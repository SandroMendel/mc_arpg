package rpg.core.zone;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

import rpg.core.message.MessageKey;

/**
 * Warns a character who walks into a region above their level - and never blocks them (FR-021 to
 * FR-025).
 *
 * <p><b>Only a warning, deliberately.</b> The mobs enforce the staggering by themselves. Pushing
 * somebody back at an invisible line would need anti-stuck logic and special cases for teleport and
 * relogin, and it feels wrong at a border you cross by accident.
 *
 * <p><b>Only the lower bound warns.</b> A level 60 character walking through the starting area is not
 * doing anything wrong, and a message saying otherwise would be noise (FR-023).
 *
 * <p><b>The repeat block is timestamp-based and lazy</b> (FR-024, Constitution II). Somebody walking
 * a border would otherwise be warned on every step. There is no scheduled cleanup: an entry is
 * overwritten on the next crossing and dropped when the character is forgotten, so the map is bounded
 * by the number of characters present, not by time.
 */
public final class LevelBandGuard {

    /** What a warning needs to reach a player - the platform supplies the sending. */
    @FunctionalInterface
    public interface Warner {

        /** Sends one message to whoever is playing this character. */
        void warn(UUID characterId, MessageKey key, Map<String, String> placeholders);
    }

    private final Supplier<Zones> zones;
    private final LevelOf levels;
    private final Warner warner;
    private final Clock clock;
    private final Supplier<Duration> cooldown;
    private final Map<Warned, Long> warnedAt = new ConcurrentHashMap<>();

    /** Which level a character is on - B06 answers it (FR-021). */
    @FunctionalInterface
    public interface LevelOf {

        OptionalInt levelOf(UUID characterId);
    }

    /** One character and one zone: the same border may warn again, a different one immediately. */
    private record Warned(UUID characterId, String zoneKey) {}

    public LevelBandGuard(
            Supplier<Zones> zones,
            LevelOf levels,
            Warner warner,
            Clock clock,
            Supplier<Duration> cooldown) {
        this.zones = Objects.requireNonNull(zones, "zones");
        this.levels = Objects.requireNonNull(levels, "levels");
        this.warner = Objects.requireNonNull(warner, "warner");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.cooldown = Objects.requireNonNull(cooldown, "cooldown");
    }

    /**
     * Reacts to a zone change. Subscribed to {@link ZoneChangedEvent}.
     *
     * <p>Leaving a region warns about nothing - only the region being entered can be too dangerous.
     */
    public void onZoneChanged(ZoneChangedEvent event) {
        if (event.to().isEmpty()) {
            return;
        }
        String zoneKey = event.to().get();
        Zone zone = zones.get().byKey(zoneKey).orElse(null);
        if (zone == null) {
            return;
        }
        OptionalInt level = levels.levelOf(event.characterId());
        if (level.isEmpty()) {
            // No level means the character is not loaded. Warning about a danger they cannot be in
            // yet would be a message with no situation behind it.
            return;
        }
        if (!zone.levelBand().isBelow(level.getAsInt())) {
            return;
        }
        if (!claimWarning(event.characterId(), zoneKey)) {
            return;
        }
        warner.warn(
                event.characterId(),
                ZoneMessageKeys.TOO_DANGEROUS,
                Map.of(
                        "min", String.valueOf(zone.levelBand().min()),
                        "max", String.valueOf(zone.levelBand().max())));
    }

    /** Forgets the repeat block for a character that left. */
    public void forget(UUID characterId) {
        warnedAt.keySet().removeIf(warned -> warned.characterId().equals(characterId));
    }

    /**
     * Whether this warning may be sent now, remembering that it was.
     *
     * <p>Evaluated lazily against the stored timestamp - no scheduled sweep, no task per character
     * (Constitution II).
     */
    private boolean claimWarning(UUID characterId, String zoneKey) {
        long now = clock.millis();
        long window = cooldown.get().toMillis();
        Warned key = new Warned(characterId, zoneKey);
        Long previous = warnedAt.get(key);
        if (previous != null && now - previous < window) {
            return false;
        }
        warnedAt.put(key, now);
        return true;
    }
}
