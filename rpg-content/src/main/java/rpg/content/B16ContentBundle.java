package rpg.content;

import java.util.Objects;

import rpg.core.ability.AbilityConfig;
import rpg.core.classes.ClassConfig;
import rpg.core.combat.CombatConfig;
import rpg.core.currency.CurrencyConfig;
import rpg.core.item.ItemConfig;
import rpg.core.mob.MobConfig;
import rpg.core.progression.ProgressionConfig;
import rpg.core.stats.StatConfig;
import rpg.core.zone.ZoneConfig;

/**
 * The fully bound B16 configuration generation produced by {@link B16ContentLoader}.
 *
 * <p>This is a staging value, not a published runtime snapshot. Every field is present only after
 * all nine source documents have been parsed, schema-validated, bound and cross-checked. T016 owns
 * connecting this complete generation to the existing handles and {@link ContentSnapshot} lifecycle.
 */
public record B16ContentBundle(
        ClassConfig classes,
        AbilityConfig abilities,
        ProgressionConfig progression,
        CombatConfig combat,
        ZoneConfig zones,
        MobConfig mobs,
        ItemConfig items,
        CurrencyConfig currency,
        StatConfig stats) {

    public B16ContentBundle {
        Objects.requireNonNull(classes, "classes");
        Objects.requireNonNull(abilities, "abilities");
        Objects.requireNonNull(progression, "progression");
        Objects.requireNonNull(combat, "combat");
        Objects.requireNonNull(zones, "zones");
        Objects.requireNonNull(mobs, "mobs");
        Objects.requireNonNull(items, "items");
        Objects.requireNonNull(currency, "currency");
        Objects.requireNonNull(stats, "stats");
    }
}
