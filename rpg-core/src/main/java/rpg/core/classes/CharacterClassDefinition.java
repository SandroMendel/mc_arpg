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
 * @param baseStats the eight base values
 * @param growth the eight per-level rates, replacing B06's class-neutral growth
 * @param armorLadder the armour ladder, carrying the defensive four
 * @param weaponLadder the weapon ladder, carrying the offensive four
 * @param abilities empty while B08 is missing, otherwise exactly six
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

    /** Four active including the unique, two passive - six in total (FR-041). */
    public static final int ACTIVE_ABILITIES = 4;

    public static final int PASSIVE_ABILITIES = 2;

    public static final int TOTAL_ABILITIES = ACTIVE_ABILITIES + PASSIVE_ABILITIES;

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
        long active = abilities.stream().filter(AbilityBinding::isActive).count();
        if (active != ACTIVE_ABILITIES) {
            throw new IllegalArgumentException(
                    id
                            + ": expected "
                            + ACTIVE_ABILITIES
                            + " active abilities including the unique, but found "
                            + active);
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
