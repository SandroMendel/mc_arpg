package rpg.core.zone;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.event.EventBus;
import rpg.core.scheduler.WorldPosition;

/**
 * Remembers where each character last was, and publishes what actually changed.
 *
 * <p><b>The state hangs on the character, never globally</b> (Constitution I). One entry per present
 * character, dropped when they leave.
 *
 * <p><b>No recurring task, ever.</b> Evaluation is driven by movement, joining and reload - the block
 * registers nothing with the {@code Scheduler}, and the absence of any registration is the proof
 * Constitution II asks for. The cheap guard that keeps the movement path affordable lives in the
 * platform listener; by the time anything gets here, something worth looking at has happened.
 *
 * <p><b>The zones are fetched through a supplier, not held.</b> A reload swaps in a whole new
 * {@link Zones}, and a tracker holding the old one would keep answering with the old borders.
 */
public final class ZoneTracker implements ZonePresence {

    /** Where a character was the last time anybody looked. */
    private record Placement(String zoneKey, boolean inSafeCore) {}

    /** A character and where they are right now - what the caller has to supply on a reload. */
    public record Presence(UUID characterId, WorldPosition position) {

        public Presence {
            Objects.requireNonNull(characterId, "characterId");
            Objects.requireNonNull(position, "position");
        }
    }

    private final Supplier<Zones> zones;
    private final EventBus eventBus;
    private final Logger logger;
    private final Map<UUID, Placement> lastKnown = new ConcurrentHashMap<>();

    /**
     * Which character a holder is playing, so a session that ends can be forgotten.
     *
     * <p>The events key on the <em>character</em>, because that is what a zone happens to (ADR-011).
     * The end of a session, though, is announced with the <em>player</em> id and at a moment when the
     * character may already be gone from the registry. Keeping the last translation here is what makes
     * {@link #forgetHolder} possible without a second copy of it somewhere else - B08b's
     * {@code CharacterLookup} exists for the same reason.
     */
    private final Map<UUID, UUID> characterByHolder = new ConcurrentHashMap<>();

    public ZoneTracker(Supplier<Zones> zones, EventBus eventBus, Logger logger) {
        this.zones = Objects.requireNonNull(zones, "zones");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Works out where this character is now and publishes what changed.
     *
     * <p>Zone and core are compared independently, so a step out of one region's core straight into
     * another region fires both events - and both are true. Nothing is published when nothing moved
     * (FR-018).
     *
     * <p><b>Wrapped in an exception barrier</b> (FR-064, Constitution VI): a fault in here must not
     * leave a player in an unclear state, so it is logged and contained. The last known placement is
     * left untouched in that case, so the next evaluation tries again rather than silently accepting
     * a position nobody managed to resolve.
     */
    public void evaluate(UUID characterId, WorldPosition position) {
        try {
            Zones current = zones.get();
            String zoneKey = current.zoneKeyAt(position);
            boolean inCore = zoneKey != null && current.inSafeCore(position);
            Placement now = new Placement(zoneKey, inCore);
            Placement before = lastKnown.put(characterId, now);

            if (before == null) {
                // First sighting - joining, switching character, or the first movement after a
                // reload dropped the state. Entering a zone is a change; entering the wilderness
                // from nowhere is not something to announce.
                if (zoneKey != null) {
                    eventBus.publish(ZoneChangedEvent.of(characterId, null, zoneKey));
                    if (inCore) {
                        eventBus.publish(new SafeAreaCrossedEvent(characterId, zoneKey, true));
                    }
                }
                return;
            }

            if (!Objects.equals(before.zoneKey(), zoneKey)) {
                eventBus.publish(ZoneChangedEvent.of(characterId, before.zoneKey(), zoneKey));
            }
            if (before.inSafeCore() != inCore) {
                // The core that was crossed: the one being left, or the one being entered.
                String coreZone = inCore ? zoneKey : before.zoneKey();
                if (coreZone != null) {
                    eventBus.publish(new SafeAreaCrossedEvent(characterId, coreZone, inCore));
                }
            }
        } catch (RuntimeException failure) {
            logger.log(
                    Level.SEVERE,
                    "[zone] evaluating the position of character " + characterId + " failed",
                    failure);
        }
    }

    /**
     * Re-evaluates everybody present after a reload, in <b>one pass</b> (FR-014).
     *
     * <p>A single pass is not a recurring task. Events fire only for assignments that actually
     * changed, so a player standing in a region whose borders did not move notices nothing
     * (FR-018).
     */
    public void reevaluateAll(Collection<Presence> present) {
        for (Presence presence : present) {
            evaluate(presence.characterId(), presence.position());
        }
        logger.info("[zone] phase=RELOAD state=REEVALUATED count=" + present.size());
    }

    /**
     * Notes which character a holder is playing, and evaluates where they are.
     *
     * <p>Called when a session becomes ready and when a character is put into play - the two moments
     * a holder's character can change.
     */
    public void place(UUID holderId, UUID characterId, WorldPosition position) {
        characterByHolder.put(holderId, characterId);
        evaluate(characterId, position);
    }

    /** Drops the state of a character that left. */
    public void forget(UUID characterId) {
        lastKnown.remove(characterId);
    }

    /**
     * Drops the state belonging to a holder whose session ended, and says whose it was.
     *
     * <p>The session end arrives with the player id, not the character - see
     * {@link #characterByHolder}. The forgotten character is <b>returned</b> rather than made
     * available through a second accessor, because everyone else who has to clean up after that
     * character - the level-band guard's repeat block, later the travel rate limit - needs exactly
     * this one answer at exactly this one moment. An accessor would invite asking at some other time,
     * when it is already gone.
     *
     * @return the character that holder was playing, or empty when there was none
     */
    public Optional<UUID> forgetHolder(UUID holderId) {
        UUID characterId = characterByHolder.remove(holderId);
        if (characterId == null) {
            return Optional.empty();
        }
        lastKnown.remove(characterId);
        return Optional.of(characterId);
    }

    /**
     * Whether the character this holder is playing stands in a safe core (FR-028a).
     *
     * <p>Two map lookups and nothing else - see {@link ZonePresence} for why the damage rule cannot
     * afford anything more.
     */
    @Override
    public boolean inSafeCore(UUID holderId) {
        UUID characterId = characterByHolder.get(holderId);
        if (characterId == null) {
            return false;
        }
        Placement placement = lastKnown.get(characterId);
        return placement != null && placement.inSafeCore();
    }

    /** The zone the character this holder is playing was last seen in, or {@code null}. */
    @Override
    public String zoneKeyOf(UUID holderId) {
        UUID characterId = characterByHolder.get(holderId);
        if (characterId == null) {
            return null;
        }
        Placement placement = lastKnown.get(characterId);
        return placement == null ? null : placement.zoneKey();
    }

    /** The zone this character was last seen in, or {@code null}. For tests and diagnostics. */
    public String lastKnownZoneOf(UUID characterId) {
        Placement placement = lastKnown.get(characterId);
        return placement == null ? null : placement.zoneKey();
    }

    /** How many characters are being tracked. For tests. */
    public int tracked() {
        return lastKnown.size();
    }
}
