package rpg.core.progression;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.event.EventBus;
import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.AuditLogRepository;
import rpg.core.session.SessionRegistry;
import rpg.core.stats.Attribute;
import rpg.core.stats.ResourcePool;
import rpg.core.stats.StatEngine;
import rpg.core.stats.StatSnapshot;

/**
 * The rules of B06, free of Bukkit and of any database call.
 *
 * <p>Two things are worth knowing before reading on.
 *
 * <p><b>A gain never touches the database.</b> It updates the in-memory state and marks the
 * character; B02's write-behind buffer does the rest (FR-054). The memory copy is authoritative
 * while the session lasts (Principle IV, FR-055).
 *
 * <p><b>The order inside a level-up is not free.</b> Set the state, recalculate, <em>then</em> refill
 * health and mana. Refilling first would fill against the old maximum - an error a few percent wide
 * on every rise, which is exactly the kind that stays unnoticed for months (FR-021b).
 */
public final class DefaultProgression implements Progression {

    private final ProgressionConfig config;
    private final StatEngine stats;
    private final EventBus events;
    private final SessionRegistry sessions;
    private final CharacterProgressRepository repository;
    private final AuditLogRepository auditLog;
    private final java.time.Clock clock;
    private final Logger logger;

    private final Map<UUID, ProgressState> states = new ConcurrentHashMap<>();
    private final Map<UUID, UUID> playerOfCharacter = new ConcurrentHashMap<>();
    private final ProgressAggregator aggregator;

    /**
     * Starts as the configuration-backed provider, so B06 works on its own until B10 exists. B10
     * replaces it and this block's table stops being consulted - correct, because from then on B10
     * owns what a creature is.
     */
    private volatile MobXpProvider mobXp;

    private volatile ProximityCheck proximity;

    public DefaultProgression(
            ProgressionConfig config,
            StatEngine stats,
            EventBus events,
            SessionRegistry sessions,
            CharacterProgressRepository repository,
            AuditLogRepository auditLog,
            java.time.Clock clock,
            Logger logger) {
        this.config = Objects.requireNonNull(config, "config");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.events = Objects.requireNonNull(events, "events");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.auditLog = Objects.requireNonNull(auditLog, "auditLog");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.mobXp = new ConfigMobXpProvider(config, logger);
        this.aggregator = new ProgressAggregator(clock, config.progressWindow());
    }

    // --- granting -------------------------------------------------------------------------------

    @Override
    public XpResult grant(UUID characterId, long amount, XpSource source) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(source, "source");
        try {
            return grantChecked(characterId, amount, source);
        } catch (RuntimeException failure) {
            // A fault stays with this character and leaves the ongoing combat operation alone
            // (FR-059), the same barrier B01 puts around a module.
            logger.log(
                    Level.WARNING,
                    "[progression] granting " + amount + " xp to " + characterId + " failed",
                    failure);
            return XpResult.rejected(XpRejection.NONE);
        }
    }

    private XpResult grantChecked(UUID characterId, long amount, XpSource source) {
        if (amount <= 0L) {
            // Never read as a deduction: only setProgress may lower anything (FR-015, FR-024a).
            logger.warning(
                    "[progression] refusing an experience amount of "
                            + amount
                            + " from "
                            + source
                            + " for character "
                            + characterId);
            return XpResult.rejected(XpRejection.INVALID_AMOUNT);
        }
        ProgressState state = states.get(characterId);
        if (state == null) {
            logger.fine(
                    "[progression] no progress loaded for character "
                            + characterId
                            + " - amount dropped");
            return XpResult.rejected(XpRejection.UNKNOWN_CHARACTER);
        }
        UUID playerId = playerOfCharacter.get(characterId);
        if (playerId == null || !sessions.isReady(playerId)) {
            // Logged out between the kill and the split. The share lapses silently (FR-014).
            return XpResult.rejected(XpRejection.SESSION_NOT_READY);
        }
        if (config.curve().isMaxLevel(state.level())) {
            // Nothing changes, so nothing is published and nothing is logged per call (FR-050).
            return XpResult.discarded(amount);
        }
        return apply(characterId, playerId, state, amount, false);
    }

    /**
     * The single place level and experience move. Used by a gain and by an operator's intervention
     * alike, so both trigger the same recalculation and the same events (FR-024c).
     */
    private XpResult apply(
            UUID characterId, UUID playerId, ProgressState state, long amount, boolean byAdmin) {
        int previousLevel = state.level();
        int level = previousLevel;
        long xp = state.xpInLevel() + amount;
        int max = config.maxLevel();

        while (level < max) {
            long needed = config.curve().thresholdFor(level + 1);
            if (needed <= 0L || xp < needed) {
                break;
            }
            xp -= needed;
            level++;
        }

        long discarded = 0L;
        if (level >= max) {
            // Ends exactly on the maximum; the rest is gone, without an overflow and without a
            // negative remainder in the display (FR-049).
            discarded = xp;
            xp = 0L;
        }

        states.put(characterId, new ProgressState(level, xp));
        // The only write path: a mark, never a database call (FR-054).
        repository.markDirty(characterId);

        if (level == previousLevel) {
            // Still inside the level: the gain goes into the bundle and is reported only when the
            // window closes (FR-023a).
            long bundled = aggregator.record(characterId, amount);
            if (bundled > 0L) {
                publishProgress(characterId, playerId, bundled);
            }
            return XpResult.granted(amount);
        }

        // A level-up flushes the open bundle FIRST, so the messages arrive in the order they
        // happened and the progress bar cannot jump backwards (FR-023c).
        long pending = aggregator.flush(characterId);
        if (pending > 0L) {
            publishProgressAt(characterId, playerId, pending, previousLevel, state.xpInLevel());
        }

        LevelUp levelUp = new LevelUp(previousLevel, level, xp, discarded);
        onLevelChanged(characterId, playerId, previousLevel, level, true);
        events.publish(new LevelUpEvent(characterId, playerId, previousLevel, level, byAdmin));
        return XpResult.leveled(amount, levelUp);
    }

    /**
     * Recalculates and, on a rise, refills health and mana.
     *
     * <p>Exactly once per change, even when several levels were crossed at once (FR-021, SC-019).
     * Recalculation first, refill second - the other way round would fill against the old maximum
     * (FR-021b).
     */
    private void onLevelChanged(
            UUID characterId, UUID playerId, int previousLevel, int newLevel, boolean refill) {
        StatSnapshot snapshot = stats.recalculateNow(playerId);
        if (refill && newLevel > previousLevel) {
            // A rise heals to the new maximum (FR-021a). Deliberate consequence: with no
            // level-difference scaling a player can save a pending rise for a boss fight and use it
            // as a full heal. Self-limiting - each level rises once, and at the ceiling it stops.
            stats.restoreResources(
                    playerId,
                    ResourcePool.full(
                            snapshot.get(Attribute.HEALTH), snapshot.get(Attribute.MANA)));
        } else {
            // A lowered level does not refill; a value above the new maximum is clamped to it
            // (FR-024c). Reading and writing back is not a no-op: the maximum just changed, so a
            // pool that was legal a moment ago can now exceed it.
            var view = stats.resources(playerId);
            stats.restoreResources(
                    playerId,
                    new ResourcePool(view.currentHealth(), view.currentMana())
                            .clampedTo(
                                    snapshot.get(Attribute.HEALTH),
                                    snapshot.get(Attribute.MANA)));
        }
    }

    /** Reports a closed bundle against the current state. */
    private void publishProgress(UUID characterId, UUID playerId, long gained) {
        ProgressState state = states.get(characterId);
        if (state == null) {
            return;
        }
        publishProgressAt(characterId, playerId, gained, state.level(), state.xpInLevel());
    }

    /**
     * Reports a closed bundle against an explicitly given level.
     *
     * <p>Needed for the flush before a level-up: the bundle belongs to the <b>old</b> level, and
     * stamping it with the new one is exactly the backwards jump FR-023c forbids.
     */
    private void publishProgressAt(
            UUID characterId, UUID playerId, long gained, int level, long xpInLevel) {
        boolean atMax = config.curve().isMaxLevel(level);
        long next = atMax ? 0L : config.curve().thresholdFor(level + 1);
        events.publish(
                new ProgressChangedEvent(characterId, playerId, gained, level, xpInLevel, next));
    }

    // --- queries --------------------------------------------------------------------------------

    @Override
    public boolean meetsLevel(UUID characterId, int requiredLevel) {
        Objects.requireNonNull(characterId, "characterId");
        ProgressState state = states.get(characterId);
        if (state == null) {
            // Never an exception: five blocks gate content on this answer, and a query must not
            // abort the caller (FR-027).
            logger.fine(
                    "[progression] level query for unknown character "
                            + characterId
                            + " - answering 'not met'");
            return false;
        }
        return state.level() >= requiredLevel;
    }

    @Override
    public Optional<ProgressView> progressOf(UUID characterId) {
        ProgressState state = states.get(characterId);
        if (state == null) {
            return Optional.empty();
        }
        boolean atMax = config.curve().isMaxLevel(state.level());
        long next = atMax ? 0L : config.curve().thresholdFor(state.level() + 1);
        return Optional.of(
                new ProgressView(state.level(), atMax ? 0L : state.xpInLevel(), next, atMax));
    }

    @Override
    public OptionalInt levelOf(UUID characterId) {
        ProgressState state = states.get(characterId);
        return state == null ? OptionalInt.empty() : OptionalInt.of(state.level());
    }

    /**
     * Primitive variant of {@link #levelOf}; 0 when nothing is loaded.
     *
     * <p>Public because the module wires it into {@link LevelStatContributor} from another package,
     * and primitive because it runs on every recalculation - an {@code OptionalInt} per call would
     * allocate in a path that promises not to.
     */
    public int levelOrZero(UUID characterId) {
        ProgressState state = states.get(characterId);
        return state == null ? 0 : state.level();
    }

    @Override
    public int maxLevel() {
        return config.maxLevel();
    }

    // --- administration -------------------------------------------------------------------------

    @Override
    public XpResult setProgress(UUID actorId, UUID characterId, int level, long xpInLevel) {
        Objects.requireNonNull(actorId, "actorId");
        Objects.requireNonNull(characterId, "characterId");
        if (level < 1 || level > config.maxLevel() || xpInLevel < 0L) {
            return XpResult.rejected(XpRejection.INVALID_AMOUNT);
        }
        ProgressState previous = states.get(characterId);
        if (previous == null) {
            return XpResult.rejected(XpRejection.UNKNOWN_CHARACTER);
        }
        UUID playerId = playerOfCharacter.get(characterId);
        if (playerId == null) {
            return XpResult.rejected(XpRejection.SESSION_NOT_READY);
        }

        states.put(characterId, new ProgressState(level, xpInLevel));
        repository.markDirty(characterId);

        auditLog.append(
                new AuditEntry(
                        clock.instant(),
                        actorId.toString(),
                        "progression.set",
                        Optional.of(playerId),
                        Map.of(
                                "characterId", characterId.toString(),
                                "fromLevel", previous.level(),
                                "fromXp", previous.xpInLevel(),
                                "toLevel", level,
                                "toXp", xpInLevel)));

        if (level != previous.level()) {
            boolean rise = level > previous.level();
            onLevelChanged(characterId, playerId, previous.level(), level, rise);
            events.publish(new LevelUpEvent(characterId, playerId, previous.level(), level, true));
            // A LevelUp is built only for a rise: the record refuses to go down, and a lowering is
            // not a rise however it is spelled. The audit entry above is the record of what happened.
            return rise
                    ? XpResult.leveled(0L, new LevelUp(previous.level(), level, xpInLevel, 0L))
                    : XpResult.granted(0L);
        }
        return XpResult.granted(0L);
    }

    // --- extension points -----------------------------------------------------------------------

    @Override
    public void setMobXpProvider(MobXpProvider provider) {
        this.mobXp = Objects.requireNonNull(provider, "provider");
    }

    @Override
    public void setProximityCheck(ProximityCheck check) {
        this.proximity = Objects.requireNonNull(check, "check");
    }

    /** The installed measurement, or {@code null} while nobody registered one (FR-044). */
    ProximityCheck proximityCheck() {
        return proximity;
    }

    /** Experience a creature is worth: its own entry, otherwise the configured default (FR-060). */
    long xpForMob(String mobTypeKey) {
        OptionalLong own = mobXp.xpFor(mobTypeKey);
        return own.isPresent() ? own.getAsLong() : config.mobXpDefault();
    }

    ProgressionConfig config() {
        return config;
    }

    // --- lifecycle ------------------------------------------------------------------------------

    @Override
    public void load(UUID characterId, UUID playerId, ProgressState state) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(state, "state");
        playerOfCharacter.put(characterId, playerId);
        states.put(characterId, state);

        // A curve that was LOWERED after this row was written can leave xpInLevel at or above the
        // new threshold. That is a pending rise, not a broken row - resolved here by the same code an
        // ordinary gain uses, so "up" stays the only direction (FR-024). In the normal case nothing
        // happens and, in particular, no dirty mark is written on every login.
        long threshold = config.curve().thresholdFor(state.level() + 1);
        if (!config.curve().isMaxLevel(state.level())
                && threshold > 0L
                && state.xpInLevel() >= threshold) {
            states.put(characterId, new ProgressState(state.level(), 0L));
            apply(
                    characterId,
                    playerId,
                    new ProgressState(state.level(), 0L),
                    state.xpInLevel(),
                    false);
        }
    }

    @Override
    public void release(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        states.remove(characterId);
        playerOfCharacter.remove(characterId);
        // The open bundle is discarded, not delivered: it is presentation only and the recipient is
        // already gone. The experience itself was credited long before and gets written.
        aggregator.release(characterId);
    }

    /** How many display windows are still open. For leak tests. */
    public int openProgressWindows() {
        return aggregator.openWindows();
    }

    /**
     * Which character of this player is loaded, if any.
     *
     * <p>Needed on the way out: the session closes by player, while progress is held by character.
     */
    public Optional<UUID> characterOf(UUID playerId) {
        for (Map.Entry<UUID, UUID> entry : playerOfCharacter.entrySet()) {
            if (entry.getValue().equals(playerId)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }

    /** How many characters are held. For leak tests. */
    public int loadedCount() {
        return states.size();
    }

    /** The current state, for the write-behind flush to read. */
    public Optional<ProgressState> stateOf(UUID characterId) {
        return Optional.ofNullable(states.get(characterId));
    }
}
