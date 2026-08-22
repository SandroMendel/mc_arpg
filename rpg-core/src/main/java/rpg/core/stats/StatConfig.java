package rpg.core.stats;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigView;
import rpg.core.config.FieldType;

/**
 * The validated attribute configuration (FR-003).
 *
 * <p>Holds one {@link AttributeDefinition} per attribute. Completeness is checked here rather than
 * left to whoever reads it: a missing attribute would otherwise surface as a null somewhere deep in
 * a calculation, at which point the operator learns that something is wrong but not what.
 */
public record StatConfig(Map<Attribute, AttributeDefinition> definitions) {

    public StatConfig {
        Objects.requireNonNull(definitions, "definitions");

        EnumMap<Attribute, AttributeDefinition> copy = new EnumMap<>(Attribute.class);
        definitions.forEach(
                (attribute, definition) -> {
                    Objects.requireNonNull(attribute, "attribute");
                    Objects.requireNonNull(definition, "definition for " + attribute.key());
                    if (definition.attribute() != attribute) {
                        throw new IllegalArgumentException(
                                "definition filed under '"
                                        + attribute.key()
                                        + "' describes '"
                                        + definition.attribute().key()
                                        + "'");
                    }
                    copy.put(attribute, definition);
                });

        for (Attribute attribute : Attribute.all()) {
            if (!copy.containsKey(attribute)) {
                throw new IllegalArgumentException(
                        "attribute '"
                                + attribute.key()
                                + "' is missing from the configuration - all "
                                + Attribute.count()
                                + " attributes must be defined");
            }
        }
        definitions = Map.copyOf(copy);
    }

    /** The definition of one attribute; never {@code null}, because completeness is enforced. */
    public AttributeDefinition definition(Attribute attribute) {
        return definitions.get(attribute);
    }

    /**
     * The schema for {@code stats.yml} (FR-003, FR-014a).
     *
     * <p>Every field is declared explicitly rather than read as a free-form map. That is the whole
     * reason an operator gets "attribute 'health': field 'min' is required" instead of a null
     * somewhere in a calculation: B01's loader can only name a field it was told about.
     *
     * <p>{@code kind} is deliberately not configurable. Whether cooldown reduction is a fraction is
     * a property of the attribute, not a balancing choice, and letting a file change it would let a
     * typo turn a 40% cap into a 4000% one.
     */
    public static ConfigSchema<StatConfig> schema() {
        ConfigSchema.Builder<StatConfig> builder = ConfigSchema.builder(SCHEMA_VERSION);
        for (Attribute attribute : Attribute.all()) {
            String prefix = "attributes." + attribute.key() + ".";
            builder.required(prefix + "base", FieldType.DOUBLE);
            builder.required(prefix + "min", FieldType.DOUBLE);
            builder.required(prefix + "max", FieldType.DOUBLE);
            if (AttributeDefinition.bandRequired(attribute)) {
                builder.required(prefix + "modifier-band", FieldType.DOUBLE);
            }
        }
        return builder.boundTo(StatConfig::bind).build();
    }

    /** Schema version of {@code stats.yml}; raised when its shape changes incompatibly. */
    public static final int SCHEMA_VERSION = 1;

    private static StatConfig bind(ConfigView view) {
        EnumMap<Attribute, AttributeDefinition> map = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.all()) {
            String prefix = "attributes." + attribute.key() + ".";
            double band =
                    AttributeDefinition.bandRequired(attribute)
                            ? view.getDouble(prefix + "modifier-band")
                            : 0.0;
            // The record's constructor carries every remaining rule, so validation lives in one
            // place rather than half here and half there.
            map.put(
                    attribute,
                    new AttributeDefinition(
                            attribute,
                            view.getDouble(prefix + "base"),
                            view.getDouble(prefix + "min"),
                            view.getDouble(prefix + "max"),
                            band));
        }
        return new StatConfig(map);
    }

    /**
     * The shipped balancing values from ADR-008 and the B04 block brief.
     *
     * <p>These are a starting point, not a decision - the same numbers live in {@code stats.yml} and
     * an operator changes them there without touching code (Principle V). This method exists so that
     * every rule of this block can be tested without a configuration file, and so a server that has
     * not yet written its defaults still has something coherent to start from.
     */
    public static StatConfig defaults() {
        EnumMap<Attribute, AttributeDefinition> map = new EnumMap<>(Attribute.class);
        map.put(Attribute.HEALTH, new AttributeDefinition(Attribute.HEALTH, 100.0, 1.0, 2000.0, 0.0));
        // Base zero on both regeneration rates (ADR-023): a holder without a class contributor is a
        // creature, and a non-zero base here would make every mob in the world heal itself.
        map.put(
                Attribute.HEALTH_REGEN,
                new AttributeDefinition(Attribute.HEALTH_REGEN, 0.0, 0.0, 40.0, 0.0));
        // 300 defense is exactly 75% mitigation through 100/(100+def) - see DamageMitigation.
        map.put(Attribute.DEFENSE, new AttributeDefinition(Attribute.DEFENSE, 0.0, 0.0, 300.0, 0.0));
        map.put(Attribute.MANA, new AttributeDefinition(Attribute.MANA, 50.0, 0.0, 500.0, 0.0));
        map.put(
                Attribute.MANA_REGEN,
                new AttributeDefinition(Attribute.MANA_REGEN, 0.0, 0.0, 20.0, 0.0));
        map.put(
                Attribute.PHYSICAL_DAMAGE,
                new AttributeDefinition(Attribute.PHYSICAL_DAMAGE, 5.0, 0.0, 150.0, 0.0));
        map.put(
                Attribute.MAGIC_DAMAGE,
                new AttributeDefinition(Attribute.MAGIC_DAMAGE, 5.0, 0.0, 150.0, 0.0));
        // Vanilla base 4.0; the band is the effective limit, max only guards against values the
        // server would refuse.
        map.put(
                Attribute.ATTACK_SPEED,
                new AttributeDefinition(Attribute.ATTACK_SPEED, 4.0, 0.0, 1024.0, 0.50));
        // Vanilla base 0.1, same reasoning.
        map.put(
                Attribute.MOVEMENT_SPEED,
                new AttributeDefinition(Attribute.MOVEMENT_SPEED, 0.1, 0.0, 1.0, 0.30));
        // max is the hard cap from FR-013.
        map.put(
                Attribute.ABILITY_COOLDOWN,
                new AttributeDefinition(Attribute.ABILITY_COOLDOWN, 0.0, 0.0, 0.40, 0.0));
        return new StatConfig(map);
    }
}
