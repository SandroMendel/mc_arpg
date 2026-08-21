package rpg.core.classes;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import rpg.core.session.CharacterClass;
import rpg.core.stats.BaseStatContributor;
import rpg.core.stats.BaseStatSink;
import rpg.core.stats.StatHolderView;

/**
 * The single class contribution: base values, per-class level growth, and the values of both reached
 * tiers - in one pass (FR-009).
 *
 * <p><b>Base values, not modifiers.</b> The modifier band from B04 is laid around the <b>effective</b>
 * base value; {@code AttributeDefinition.bandFloor} takes the base as a parameter precisely so the band
 * moves with it. Were the tier values FLAT modifiers, the band would stay pinned at the level-1 base:
 * a band of +-30% around 40 health would never admit the 1385 a top-tier warrior carries, and the value
 * would be clamped unnoticed. B06 made the same choice for level growth, where it affected a third of
 * the end power; here it is roughly 70% (ADR-017).
 *
 * <p>Consequently {@link rpg.core.stats.SourceKind#CLASS} stays <b>unused</b> by this block - the same
 * way B06 left {@code SourceKind.LEVEL} unused (ADR-015). There is nothing to sort when there is one
 * base contribution per attribute.
 *
 * <p><b>This replaces B06's class-neutral level growth, it does not add to it.</b> Every character has
 * a class - the column is {@code NOT NULL} since B03 - so once this contributor is registered, B06's
 * has no remaining subject. The wiring registers one or the other, never both; adding them would
 * double the growth (FR-003).
 *
 * <p>A holder without a character contributes nothing and throws nothing: B04 recalculates mobs through
 * the same path. Same shape as {@code LevelStatContributor}.
 */
public final class ClassStatContributor implements BaseStatContributor {

    public static final String ID = "class";

    private final ClassConfig config;
    private final Function<UUID, Optional<CharacterClass>> classOf;
    private final ToIntFunction<UUID> levelOf;
    private final Function<UUID, Optional<ClassProgress>> progressOf;

    /**
     * @param classOf which class a character has - B03 owns that column
     * @param levelOf the character's level - B06 owns it
     * @param progressOf the reached tiers; empty for a character whose row has not been written yet,
     *     in which case tier 1 applies
     */
    public ClassStatContributor(
            ClassConfig config,
            Function<UUID, Optional<CharacterClass>> classOf,
            ToIntFunction<UUID> levelOf,
            Function<UUID, Optional<ClassProgress>> progressOf) {
        this.config = Objects.requireNonNull(config, "config");
        this.classOf = Objects.requireNonNull(classOf, "classOf");
        this.levelOf = Objects.requireNonNull(levelOf, "levelOf");
        this.progressOf = Objects.requireNonNull(progressOf, "progressOf");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public void contribute(StatHolderView holder, BaseStatSink sink) {
        Optional<UUID> characterId = holder.characterId();
        if (characterId.isEmpty()) {
            // A creature has no character, so nothing to contribute - and no exception either.
            return;
        }
        UUID id = characterId.get();
        Optional<CharacterClass> characterClass = classOf.apply(id);
        if (characterClass.isEmpty()) {
            // Should not happen: the class column is NOT NULL. Contributing nothing is still the
            // right answer - a default class would write itself into the next save (Constitution VI).
            return;
        }
        CharacterClassDefinition definition = config.definition(characterClass.get());

        definition.baseStats().contributeTo(sink);
        definition.growth().contributeTo(levelOf.applyAsInt(id), sink);

        ClassProgress progress = progressOf.apply(id).orElseGet(() -> ClassProgress.initial(id));
        contributeTier(definition, LadderSlot.ARMOR, progress.armorTier(), sink);
        contributeTier(definition, LadderSlot.WEAPON, progress.weaponTier(), sink);
    }

    /**
     * Adds one tier's values, clamping a stored tier that is beyond the configured ladder.
     *
     * <p>Startup already refuses a configuration shorter than a stored tier (FR-024), so this cannot
     * happen through the ordinary path. It is guarded anyway because the alternative is an exception in
     * the recalculation path, and Constitution VI forbids letting one character's bad data take the
     * others with it.
     */
    private void contributeTier(
            CharacterClassDefinition definition, LadderSlot slot, int tier, BaseStatSink sink) {
        EquipmentLadder ladder = definition.ladder(slot);
        int effective = Math.min(Math.max(tier, 1), ladder.length());
        ladder.contributeTo(effective, sink);
    }
}
