package rpg.core.classes;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

/**
 * How a tier looks. The reason this type exists at all is that the material alone cannot carry every
 * ladder (FR-016).
 *
 * <p>The warrior changes material on every step, so material alone would do. The mage stays on
 * leather the whole way and is distinguished by <b>colour</b>; the rogue stays on chainmail from tier
 * four and is distinguished by <b>trim</b>, because neither gold nor chainmail is dyeable in vanilla.
 * Visibility is therefore a requirement, not decoration: without it the progression of two of the
 * three classes would be a bare number.
 *
 * <p>Equality compares the <b>triple</b> of material, colour and trim. Two tiers may agree in two of
 * the three but never in all three - that is what {@code FR-016} forbids and what
 * {@link #looksLike(TierAppearance)} answers.
 *
 * @param material vanilla material name, required
 * @param color RGB value for dyeable materials, or {@code null}
 * @param trimMaterial trim material name, set together with {@code trimPattern} or not at all
 * @param trimPattern trim pattern name, set together with {@code trimMaterial} or not at all
 * @param modelData reserved for a later resource pack (ADR-005); unused in vanilla operation
 */
public record TierAppearance(
        String material, Integer color, String trimMaterial, String trimPattern, Integer modelData) {

    public TierAppearance {
        Objects.requireNonNull(material, "material");
        if (material.isBlank()) {
            throw new IllegalArgumentException("material must not be blank");
        }
        // Both or neither. One half of a trim is a configuration mistake, not a partial trim.
        if ((trimMaterial == null) != (trimPattern == null)) {
            throw new IllegalArgumentException(
                    "trim-material and trim-pattern must be set together or not at all, but were "
                            + trimMaterial
                            + " / "
                            + trimPattern);
        }
    }

    /** A tier that relies on its material alone - the warrior case. */
    public static TierAppearance ofMaterial(String material) {
        return new TierAppearance(material, null, null, null, null);
    }

    /** A dyed tier - the mage case. */
    public static TierAppearance dyed(String material, int color) {
        return new TierAppearance(material, color, null, null, null);
    }

    /** A trimmed tier - the rogue case from tier four. */
    public static TierAppearance trimmed(String material, String trimMaterial, String trimPattern) {
        return new TierAppearance(material, null, trimMaterial, trimPattern, null);
    }

    public Optional<String> trimMaterialName() {
        return Optional.ofNullable(trimMaterial);
    }

    public Optional<String> trimPatternName() {
        return Optional.ofNullable(trimPattern);
    }

    public OptionalInt rgb() {
        return color == null ? OptionalInt.empty() : OptionalInt.of(color);
    }

    public boolean hasColor() {
        return color != null;
    }

    public boolean hasTrim() {
        return trimMaterial != null;
    }

    /**
     * Whether this tier is indistinguishable from {@code other} - identical material, colour and
     * trim. Used by the schema to reject a ladder whose progression would be invisible (FR-016).
     *
     * <p>{@code modelData} is deliberately not compared: it renders nothing without a resource pack,
     * so it cannot make a tier visible (ADR-005).
     */
    public boolean looksLike(TierAppearance other) {
        return material.equals(other.material)
                && Objects.equals(color, other.color)
                && Objects.equals(trimMaterial, other.trimMaterial)
                && Objects.equals(trimPattern, other.trimPattern);
    }
}
