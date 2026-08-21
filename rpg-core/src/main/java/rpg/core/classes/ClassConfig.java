package rpg.core.classes;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.stats.AttributeDefinition;

/**
 * The bound content of {@code classes.yml} - exactly three definitions for the whole server.
 *
 * <p>Not per player. Three immutable objects and a map; at 200 players that is still three objects.
 * Immutability is what makes sharing them without locks safe (Constitution I).
 */
public final class ClassConfig {

    /**
     * The one armour set every class may share. Beyond it, an armour set belongs to at most one class
     * (FR-016c) - otherwise two classes would look identical and the visual distinction would be
     * silently lost.
     */
    public static final String SHARED_ENTRY_ARMOR = "LEATHER";

    /**
     * Armour sets a colour may be applied to. Vanilla dyes leather and nothing else, so a colour on
     * gold or chainmail would silently do nothing (FR-016b). The platform confirms the material
     * exists at all; this is the rule about what a colour means.
     */
    private static final Set<String> DYEABLE_ARMOR = Set.of("LEATHER");

    private final Map<CharacterClass, CharacterClassDefinition> definitions;

    private ClassConfig(Map<CharacterClass, CharacterClassDefinition> definitions) {
        this.definitions = definitions;
    }

    /**
     * @throws IllegalArgumentException if a known class is missing, or if an armour set outside the
     *     shared entry material appears in more than one class
     */
    public static ClassConfig of(Map<CharacterClass, CharacterClassDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        Map<CharacterClass, CharacterClassDefinition> copy = new EnumMap<>(CharacterClass.class);
        copy.putAll(definitions);
        for (CharacterClass known : CharacterClass.values()) {
            if (!copy.containsKey(known)) {
                throw new IllegalArgumentException(
                        "classes: '"
                                + known
                                + "' is missing. All "
                                + CharacterClass.values().length
                                + " known classes are required - the set of classes lives in code,"
                                + " the content in configuration");
            }
        }
        validateArmorFamilies(copy);
        validateColourOnlyOnDyeable(copy);
        return new ClassConfig(Map.copyOf(copy));
    }

    /** V11 - an armour set belongs to at most one class, the shared entry material excepted. */
    private static void validateArmorFamilies(Map<CharacterClass, CharacterClassDefinition> byClass) {
        Map<String, CharacterClass> owner = new HashMap<>();
        byClass.forEach(
                (id, definition) -> {
                    for (EquipmentTier tier : definition.armorLadder().tiers()) {
                        String material = tier.appearance().material();
                        if (SHARED_ENTRY_ARMOR.equals(material)) {
                            continue;
                        }
                        CharacterClass previous = owner.putIfAbsent(material, id);
                        if (previous != null && previous != id) {
                            throw new IllegalArgumentException(
                                    "armor set '"
                                            + material
                                            + "' appears in both "
                                            + previous
                                            + " and "
                                            + id
                                            + " - outside the shared entry material '"
                                            + SHARED_ENTRY_ARMOR
                                            + "' an armour set belongs to at most one class,"
                                            + " otherwise two classes look the same");
                        }
                    }
                });
    }

    /** V9 - a colour is only meaningful on a dyeable set. */
    private static void validateColourOnlyOnDyeable(
            Map<CharacterClass, CharacterClassDefinition> byClass) {
        byClass.forEach(
                (id, definition) -> {
                    for (EquipmentTier tier : definition.armorLadder().tiers()) {
                        TierAppearance appearance = tier.appearance();
                        if (appearance.hasColor() && !DYEABLE_ARMOR.contains(appearance.material())) {
                            throw new IllegalArgumentException(
                                    id
                                            + " armor-ladder tier "
                                            + tier.index()
                                            + ": '"
                                            + appearance.material()
                                            + "' cannot be dyed, so the colour would silently do"
                                            + " nothing. Use a trim instead");
                        }
                    }
                    // A weapon is a single item, not a set - a colour there has no meaning either.
                    for (EquipmentTier tier : definition.weaponLadder().tiers()) {
                        if (tier.appearance().hasColor()) {
                            throw new IllegalArgumentException(
                                    id
                                            + " weapon-ladder tier "
                                            + tier.index()
                                            + ": a weapon cannot be dyed");
                        }
                    }
                });
    }

    public CharacterClassDefinition definition(CharacterClass id) {
        CharacterClassDefinition definition = definitions.get(id);
        if (definition == null) {
            throw new IllegalArgumentException("no definition for class " + id);
        }
        return definition;
    }

    public Map<CharacterClass, CharacterClassDefinition> definitions() {
        return definitions;
    }

    /**
     * V13 and V14 - the effective value at maximum level on the top tier stays inside {@code min} and
     * {@code max} of its attribute.
     *
     * <p>Deliberately <b>not</b> a check against the modifier band: the band bounds modifiers around
     * the effective base, and the class moves the base itself. {@code AttributeDefinition} says so by
     * taking the base as a parameter so the band moves with it.
     *
     * <p>Separate from binding because it needs the stat configuration, which is loaded by another
     * block. Keeping it out of the schema keeps the schema testable on its own.
     *
     * @param definitions lookup of the attribute definitions from B04
     * @param maxLevel maximum level from B06
     * @throws IllegalArgumentException naming class, attribute and the offending value
     */
    public void validateAgainstCaps(
            Function<Attribute, AttributeDefinition> definitions, int maxLevel) {
        Objects.requireNonNull(definitions, "definitions");
        this.definitions.forEach(
                (id, definition) -> {
                    for (Attribute attribute : Attribute.all()) {
                        AttributeDefinition attributeDefinition = definitions.apply(attribute);
                        if (attributeDefinition == null) {
                            continue;
                        }
                        double effective = effectiveMaximum(definition, attribute, maxLevel);
                        if (effective > attributeDefinition.max()) {
                            throw new IllegalArgumentException(
                                    id
                                            + ": "
                                            + attribute.key()
                                            + " reaches "
                                            + effective
                                            + " at level "
                                            + maxLevel
                                            + " on the top tier, above the cap "
                                            + attributeDefinition.max()
                                            + " - further tiers would have no effect");
                        }
                    }
                });
    }

    /**
     * V19 - no stored tier may exceed the configured ladder length (FR-024).
     *
     * <p>Runs at startup, after the migration and <b>before the first player joins</b>. The order is
     * part of the promise: later, a character would already be loaded when the mistake surfaces.
     *
     * <p>Refusing to start is the point. Silently demoting a character to the new top tier would take
     * away something they paid for, and it would do so on the quietest possible path - a balancing edit
     * that shortened a ladder.
     *
     * @param storedTiers every persisted tier state, from the repository
     * @param classOf which class a character has
     * @throws IllegalArgumentException naming character, slot, stored tier and configured length
     */
    public void validateAgainstStoredTiers(
            Iterable<ClassProgress> storedTiers,
            Function<java.util.UUID, java.util.Optional<CharacterClass>> classOf) {
        Objects.requireNonNull(storedTiers, "storedTiers");
        Objects.requireNonNull(classOf, "classOf");
        for (ClassProgress stored : storedTiers) {
            CharacterClass id =
                    classOf.apply(stored.characterId())
                            .orElseThrow(
                                    () ->
                                            new IllegalArgumentException(
                                                    "character "
                                                            + stored.characterId()
                                                            + " has stored tiers but no class"));
            CharacterClassDefinition definition = definition(id);
            for (LadderSlot slot : LadderSlot.values()) {
                int tier = stored.tierOf(slot);
                int length = definition.ladder(slot).length();
                if (tier > length) {
                    throw new IllegalArgumentException(
                            "character "
                                    + stored.characterId()
                                    + " ("
                                    + id
                                    + ") stands on "
                                    + slot.configKey()
                                    + " tier "
                                    + tier
                                    + ", but the configured ladder has only "
                                    + length
                                    + ". Refusing to start rather than demoting the character");
                }
            }
        }
    }

    /** Base + level growth + the top tier of the ladder that carries this attribute. */
    private static double effectiveMaximum(
            CharacterClassDefinition definition, Attribute attribute, int maxLevel) {
        double value = definition.baseStats().of(attribute);
        value += definition.growth().perLevel(attribute) * Math.max(0, maxLevel - 1);
        for (LadderSlot slot : LadderSlot.values()) {
            if (slot.carried().contains(attribute)) {
                value += definition.ladder(slot).top().valueOf(attribute);
            }
        }
        return value;
    }
}
