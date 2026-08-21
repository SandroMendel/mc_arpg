package rpg.core.classes;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import rpg.core.session.CharacterClass;

/**
 * What a character ought to be wearing, and whether a given tag belongs to it.
 *
 * <p><b>The direction is one-way: the tier produces the item, never the other way round.</b> Two
 * properties follow that would otherwise be laborious to enforce - a missing bound item heals itself on
 * the next load (FR-023), and there is no way to gain a tier by tampering with an item
 * (Constitution VI).
 *
 * <p>The binding tag travels as a <b>string</b>, not as a Bukkit object. The core defines what the tag
 * says; {@code rpg-platform} puts it into the item's persistent data container and reads it back. That
 * is what keeps Constitution III.1 intact even though the binding hangs off a Bukkit concept.
 *
 * <p>The predicate sits in the path of <b>every</b> inventory click, so it compares a pre-resolved
 * string and allocates nothing (Constitution II, SC-010).
 */
public final class BoundEquipment {

    /** Separator inside the tag. Not a character any class id, slot or uuid can contain. */
    private static final char SEPARATOR = '|';

    private final ClassConfig config;
    private final Function<UUID, Optional<CharacterClass>> classOf;
    private final Function<UUID, Optional<ClassProgress>> progressOf;

    public BoundEquipment(
            ClassConfig config,
            Function<UUID, Optional<CharacterClass>> classOf,
            Function<UUID, Optional<ClassProgress>> progressOf) {
        this.config = Objects.requireNonNull(config, "config");
        this.classOf = Objects.requireNonNull(classOf, "classOf");
        this.progressOf = Objects.requireNonNull(progressOf, "progressOf");
    }

    /**
     * The tag a bound item carries.
     *
     * <p>Contains the <b>character id</b>, which is what makes a copied item worthless: it belongs to
     * a different character and is replaced on load rather than accepted (Constitution VI).
     */
    public static String tagFor(UUID characterId, CharacterClass id, LadderSlot slot) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slot, "slot");
        return id.name() + SEPARATOR + slot.name() + SEPARATOR + characterId;
    }

    /**
     * Whether {@code tag} marks an item bound to <b>this</b> character.
     *
     * <p>A plain string comparison against the expected tag - no parsing, no allocation. An item bound
     * to another character answers {@code false} here, which is the whole point.
     */
    public boolean isBoundTo(String tag, UUID characterId) {
        if (tag == null || characterId == null) {
            return false;
        }
        Optional<CharacterClass> characterClass = classOf.apply(characterId);
        if (characterClass.isEmpty()) {
            return false;
        }
        return tag.equals(tagFor(characterId, characterClass.get(), LadderSlot.ARMOR))
                || tag.equals(tagFor(characterId, characterClass.get(), LadderSlot.WEAPON));
    }

    /**
     * Whether {@code tag} marks an item bound to <b>any</b> character.
     *
     * <p>Used where the owner is not at hand - the drop route, for instance. Deliberately not a
     * substitute for {@link #isBoundTo}: an item bound to someone else must not count as this
     * character's equipment.
     */
    public boolean isBound(String tag) {
        if (tag == null) {
            return false;
        }
        int first = tag.indexOf(SEPARATOR);
        if (first <= 0) {
            return false;
        }
        int second = tag.indexOf(SEPARATOR, first + 1);
        if (second <= first + 1 || second == tag.length() - 1) {
            return false;
        }
        return isKnownClass(tag.substring(0, first)) && isKnownSlot(tag.substring(first + 1, second));
    }

    /**
     * What this character ought to wear - one appearance per slot, derived from the reached tiers.
     *
     * <p>Empty for a character whose class is unknown. Never a default: a made-up class would write
     * itself into the next save (Constitution VI).
     */
    public Optional<Map<LadderSlot, TierAppearance>> expectedFor(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        Optional<CharacterClass> characterClass = classOf.apply(characterId);
        if (characterClass.isEmpty()) {
            return Optional.empty();
        }
        CharacterClassDefinition definition = config.definition(characterClass.get());
        ClassProgress progress =
                progressOf.apply(characterId).orElseGet(() -> ClassProgress.initial(characterId));
        Map<LadderSlot, TierAppearance> expected = new EnumMap<>(LadderSlot.class);
        for (LadderSlot slot : LadderSlot.values()) {
            EquipmentLadder ladder = definition.ladder(slot);
            int tier = Math.min(Math.max(progress.tierOf(slot), 1), ladder.length());
            expected.put(slot, ladder.tier(tier).appearance());
        }
        return Optional.of(expected);
    }

    /** The tag this character's item in {@code slot} should carry. */
    public Optional<String> expectedTag(UUID characterId, LadderSlot slot) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(slot, "slot");
        return classOf.apply(characterId).map(id -> tagFor(characterId, id, slot));
    }

    private static boolean isKnownClass(String name) {
        for (CharacterClass id : CharacterClass.values()) {
            if (id.name().equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isKnownSlot(String name) {
        for (LadderSlot slot : LadderSlot.values()) {
            if (slot.name().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
