package rpg.core.currency;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldType;

/**
 * Schema for {@code currency.yml} (FR-058).
 *
 * <p><b>Every field is required, including the ones that are zero in the shipped file.</b> Same
 * argument B05 makes for its environment sources and B06 for its growth fields: a missing field
 * should stop the start, not quietly become zero. Otherwise "characters start with nothing" is
 * indistinguishable from "somebody deleted the line".
 *
 * <p><b>Durations are integers with the unit in the key</b> - {@code despawn-seconds},
 * {@code retention-days}. The configuration layer has no duration type, and inventing a parser for
 * two fields would have added a second way to write time to a project that already has one.
 *
 * <p>{@code drops.by-type} is one map field rather than one required key per creature, the same
 * shape {@code mob-xp.by-type} uses in {@code progression.yml}, so the two files side by side read
 * alike.
 */
public final class CurrencyConfigSchema {

    public static final int SCHEMA_VERSION = 1;

    private CurrencyConfigSchema() {}

    public static ConfigSchema<CurrencyConfig> schema() {
        ConfigSchema.Builder<CurrencyConfig> builder = ConfigSchema.builder(SCHEMA_VERSION);
        // LONG rather than INTEGER for anything that is coins: a balance above two billion is
        // unlikely, but reaching it should be a refused credit (FR-010), never a silent wrap.
        builder.required("account.starting-balance", FieldType.LONG);
        builder.required("drops.default", FieldType.LONG);
        builder.required("drops.by-type", FieldType.MAP);
        builder.required("drops.despawn-seconds", FieldType.INTEGER);
        builder.required("drops.merge-radius", FieldType.DOUBLE);
        builder.required("drops.max-piles", FieldType.INTEGER);
        builder.required("ledger.retention-days", FieldType.INTEGER);
        builder.required("history.page-size", FieldType.INTEGER);
        return builder.boundTo(CurrencyConfigSchema::bind).build();
    }

    private static CurrencyConfig bind(ConfigView view) {
        return new CurrencyConfig(
                view.getLong("account.starting-balance"),
                view.getLong("drops.default"),
                readDrops(view.getMap("drops.by-type")),
                Duration.ofSeconds(view.getInt("drops.despawn-seconds")),
                view.getDouble("drops.merge-radius"),
                view.getInt("drops.max-piles"),
                Duration.ofDays(view.getInt("ledger.retention-days")),
                view.getInt("history.page-size"));
    }

    /**
     * Reads the per-type overrides.
     *
     * <p>Keys are upper-cased so a file written {@code zombie:} still matches the Bukkit type name.
     * A lower-case entry that silently never matched would look like a balancing decision that did
     * not work rather than a typo.
     *
     * <p><b>Zero is allowed here and only here.</b> An explicit {@code 0} means "this creature drops
     * nothing" and is a choice; a <em>missing</em> entry means the default (FR-023). Those two must
     * stay distinguishable, which is why the default is not simply written as zero.
     */
    private static Map<String, Long> readDrops(Map<?, ?> raw) {
        Map<String, Long> amounts = new LinkedHashMap<>();
        raw.forEach(
                (key, value) -> {
                    String type = String.valueOf(key).trim();
                    if (type.isEmpty()) {
                        throw new IllegalArgumentException(
                                "currency.drops.by-type has an entry with an empty creature type");
                    }
                    if (!(value instanceof Number number)) {
                        throw new IllegalArgumentException(
                                "currency.drops.by-type."
                                        + type
                                        + " must be a number, but was "
                                        + value);
                    }
                    long amount = number.longValue();
                    if (amount < 0L) {
                        throw new IllegalArgumentException(
                                "currency.drops.by-type."
                                        + type
                                        + " must not be negative, but was "
                                        + amount);
                    }
                    amounts.put(type.toUpperCase(Locale.ROOT), amount);
                });
        return amounts;
    }
}
