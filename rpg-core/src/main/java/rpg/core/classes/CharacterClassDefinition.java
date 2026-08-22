package rpg.core.classes;

import java.util.List;
import java.util.Objects;

import rpg.core.message.MessageKey;
import rpg.core.session.CharacterClass;

/**
 * Everything a class is - immutable, loaded once, shared by every player (FR-006).
 *
 * <p>Three of these exist for the whole server. That is why immutability is not a style choice:
 * shared without locks is only safe if nothing can change it (Constitution I).
 *
 * <p><b>No special case per class anywhere.</b> Every difference between warrior, mage and rogue is a
 * value in this object. If logic ever has to ask "which class is this", the difference belongs in the
 * configuration instead.
 *
 * @param id the class, mapped onto the enum from B03 - the set of classes lives in code, the content
 *     here (ADR-019)
 * @param displayNameKey message key, never text. "Berserker" is a value in the message file
 * @param menuMaterial vanilla material for the selection menu (ADR-005)
 * @param baseStats one base value per attribute
 * @param growth one per-level rate per attribute, replacing B06's class-neutral growth
 * @param armorLadder the armour ladder, carrying the defensive four
 * @param weaponLadder the weapon ladder, carrying the offensive four
 * @param abilities empty while B08 has not filled them, otherwise exactly six
 */
public record CharacterClassDefinition(
        CharacterClass id,
        MessageKey displayNameKey,
        String menuMaterial,
        ClassBaseStats baseStats,
        ClassGrowth growth,
        EquipmentLadder armorLadder,
        EquipmentLadder weaponLadder,
        List<AbilityBinding> abilities) {

    /**
     * Six abilities per class, at most one of them unique (FR-041, ADR-025).
     *
     * <p><b>How they split between active and passive is not checked here, and that is deliberate.</b>
     * The rule used to be "four active, two passive"; the worked-out rogue is three and three - poison,
     * position and a second life are all states rather than button presses. Bending a thought-through
     * loadout to save a number that was only ever an early estimate would have been the wrong trade,
     * so the split became content and this class stopped having an opinion about it.
     */
    public static final int TOTAL_ABILITIES = 6;

    public CharacterClassDefinition {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayNameKey, "displayNameKey");
        Objects.requireNonNull(menuMaterial, "menuMaterial");
        Objects.requireNonNull(baseStats, "baseStats");
        Objects.requireNonNull(growth, "growth");
        Objects.requireNonNull(armorLadder, "armorLadder");
        Objects.requireNonNull(weaponLadder, "weaponLadder");
        Objects.requireNonNull(abilities, "abilities");
        if (armorLadder.slot() != LadderSlot.ARMOR) {
            throw new IllegalArgumentException(id + ": armorLadder carries slot " + armorLadder.slot());
        }
        if (weaponLadder.slot() != LadderSlot.WEAPON) {
            throw new IllegalArgumentException(
                    id + ": weaponLadder carries slot " + weaponLadder.slot());
        }
        abilities = List.copyOf(abilities);
        validateAbilities(id, abilities);
    }

    /**
     * Empty is allowed while B08 does not exist; a <b>partially</b> filled loadout is not (FR-045).
     * Otherwise a forgotten line would be indistinguishable from a deliberate omission.
     */
    private static void validateAbilities(CharacterClass id, List<AbilityBinding> abilities) {
        if (abilities.isEmpty()) {
            return;
        }
        if (abilities.size() != TOTAL_ABILITIES) {
            throw new IllegalArgumentException(
                    id
                            + ": abilities must be empty or exactly "
                            + TOTAL_ABILITIES
                            + " entries, but had "
                            + abilities.size()
                            + " - a partially filled loadout cannot be told from a forgotten line");
        }
        long unique = abilities.stream().filter(AbilityBinding::unique).count();
        if (unique > 1) {
            throw new IllegalArgumentException(
                    id + ": at most one unique class ability, but found " + unique);
        }
        long distinct = abilities.stream().map(AbilityBinding::abilityId).distinct().count();
        if (distinct != abilities.size()) {
            throw new IllegalArgumentException(id + ": duplicate ability id in the loadout");
        }
    }

    public EquipmentLadder ladder(LadderSlot slot) {
        return slot == LadderSlot.ARMOR ? armorLadder : weaponLadder;
    }

    /** The abilities available at {@code level} - derived, never stored (FR-043). */
    public List<AbilityBinding> unlockedAt(int level) {
        return abilities.stream().filter(binding -> binding.unlockLevel() <= level).toList();
    }
}
