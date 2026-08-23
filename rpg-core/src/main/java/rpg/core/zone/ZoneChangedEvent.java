package rpg.core.zone;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.event.Event;

/**
 * A character crossed a region border (FR-015).
 *
 * <p><b>Exactly one of these per actual change</b> - when walking, when teleporting, on joining, on
 * switching character, and after a reload that moved the borders (FR-017). Never twice for an
 * unchanged assignment (FR-018).
 *
 * <p><b>Either side may be empty.</b> A hand-built continent leaves gaps, so entering out of the
 * wilderness has no {@code from} and walking into it has no {@code to}. That is a valid state, not an
 * error (FR-007).
 *
 * <p><b>This is not fired for the safe core.</b> Crossing the core boundary does not change the zone,
 * so it has its own event - see {@link SafeAreaCrossedEvent}. A zone event carrying "zone unchanged"
 * would be an untruth in the type, and every consumer listening for a zone change would have to learn
 * to ignore some of them (FR-016).
 *
 * @param characterId the character that moved
 * @param from the zone key left behind, or empty when coming out of the wilderness
 * @param to the zone key entered, or empty when walking into the wilderness
 */
public record ZoneChangedEvent(UUID characterId, Optional<String> from, Optional<String> to)
        implements Event {

    public ZoneChangedEvent {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (from.equals(to)) {
            throw new IllegalArgumentException(
                    "a zone change needs two different sides, but both were " + from);
        }
    }

    static ZoneChangedEvent of(UUID characterId, String from, String to) {
        return new ZoneChangedEvent(
                characterId, Optional.ofNullable(from), Optional.ofNullable(to));
    }

    @Override
    public String publishedByModuleId() {
        return ZoneModule.ID;
    }
}
