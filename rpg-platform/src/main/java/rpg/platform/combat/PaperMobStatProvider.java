package rpg.platform.combat;

import java.util.Optional;

import rpg.core.combat.CombatConfig;
import rpg.core.combat.MobStatProvider;
import rpg.core.stats.Attribute;
import rpg.core.stats.ModifierSet;
import rpg.core.stats.SourceId;
import rpg.core.stats.SourceKind;
import rpg.core.stats.StatModifier;

/**
 * Mob values from {@code combat.yml}, until B10 supplies real definitions (FR-019b).
 *
 * <p>The contributions use the source {@code (CLASS, "mob:<TYPE>")} - the same key B10 will later
 * replace rather than a second one alongside it. Swapping the provider then swaps the numbers and
 * nothing else.
 *
 * <p>The values are stated as the difference from the configured base, because B04's formula starts
 * from the attribute base and adds contributions. A zombie configured at 80 health therefore
 * contributes 80 minus the base, which is what makes it end up at 80 rather than at 180.
 */
public final class PaperMobStatProvider implements MobStatProvider {

    private final CombatConfig config;
    private final double baseHealth;
    private final double baseDefense;
    private final double basePhysicalDamage;

    public PaperMobStatProvider(CombatConfig config, rpg.core.stats.StatConfig stats) {
        this.config = config;
        this.baseHealth = stats.definition(Attribute.HEALTH).base();
        this.baseDefense = stats.definition(Attribute.DEFENSE).base();
        this.basePhysicalDamage = stats.definition(Attribute.PHYSICAL_DAMAGE).base();
    }

    @Override
    public Optional<ModifierSet> statsFor(String mobTypeKey) {
        CombatConfig.MobStats values = config.mobStatsOf(mobTypeKey);
        return Optional.of(
                ModifierSet.of(
                        SourceId.of(SourceKind.CLASS, "mob:" + mobTypeKey),
                        StatModifier.flat(Attribute.HEALTH, values.health() - baseHealth),
                        StatModifier.flat(Attribute.DEFENSE, values.defense() - baseDefense),
                        StatModifier.flat(
                                Attribute.PHYSICAL_DAMAGE,
                                values.physicalDamage() - basePhysicalDamage)));
    }
}
