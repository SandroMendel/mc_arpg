package rpg.core.ability;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;

/**
 * Builds the raw nested map that {@code abilities.yml} produces, so a test can break exactly one
 * thing in it and assert that the loader refuses it.
 *
 * <p>Deliberately a mutable map of plain values rather than a builder of typed objects: the promises
 * under test are precisely the ones standing between untyped configuration and typed definition.
 * Same shape and same reasoning as {@code ClassConfigFixture} in B07.
 */
final class AbilityConfigFixture {

    private static final Path SOURCE = Path.of("abilities.yml");

    private AbilityConfigFixture() {}

    /** Goes through {@code SchemaValidator} and then the binder - exactly the loader's route. */
    static AbilityConfig bind(Map<String, Object> document) throws Exception {
        ConfigSchema<AbilityConfig> schema = AbilityConfigSchema.schema();
        return schema.bind(SchemaValidator.validate(SOURCE, document, schema));
    }

    /** A document that must load: the runtime block plus one active and one passive ability. */
    static Map<String, Object> valid() {
        Map<String, Object> document = new LinkedHashMap<>();
        document.put("runtime", runtime());
        Map<String, Object> abilities = new LinkedHashMap<>();
        abilities.put("probe.strike", activeAbility());
        abilities.put("probe.lifesteal", passiveAbility());
        document.put("abilities", abilities);
        return document;
    }

    static Map<String, Object> runtime() {
        Map<String, Object> regeneration = new LinkedHashMap<>();
        regeneration.put("health-combat-factor", 0.20);
        regeneration.put("mana-combat-factor", 0.35);
        Map<String, Object> runtime = new LinkedHashMap<>();
        runtime.put("global-cooldown-ms", 750);
        runtime.put("regeneration", regeneration);
        return runtime;
    }

    /** An active ability with one damage effect on the caster's surroundings. */
    static Map<String, Object> activeAbility() {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("kind", "ACTIVE");
        block.put("display-name-key", "ability.probe.strike.name");
        block.put("item", "IRON_AXE");
        block.put("mana-cost", 25.0);
        block.put("cooldown-ms", 9000);
        block.put("cast-time-ms", 0);
        block.put("max-rank", 5);
        block.put("target", radiusTarget(4.5, 8));
        block.put("effects", new ArrayList<>(List.of(damageEffect())));
        return block;
    }

    /** A passive ability triggered by dealing damage. */
    static Map<String, Object> passiveAbility() {
        Map<String, Object> block = new LinkedHashMap<>();
        block.put("kind", "PASSIVE");
        block.put("display-name-key", "ability.probe.lifesteal.name");
        block.put("trigger", "ON_DAMAGE_DEALT");
        block.put("cooldown-ms", 0);
        block.put("max-rank", 5);
        block.put("target", selfTarget());
        Map<String, Object> effect = new LinkedHashMap<>();
        effect.put("type", "LIFESTEAL");
        effect.put("amount", 0.08);
        effect.put("per-rank", 0.02);
        block.put("effects", new ArrayList<>(List.of(effect)));
        return block;
    }

    static Map<String, Object> damageEffect() {
        Map<String, Object> effect = new LinkedHashMap<>();
        effect.put("type", "DAMAGE");
        effect.put("damage-type", "PHYSICAL");
        effect.put("amount", 1.4);
        effect.put("per-rank", 0.2);
        return effect;
    }

    static Map<String, Object> selfTarget() {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("mode", "SELF");
        return target;
    }

    static Map<String, Object> radiusTarget(double range, int maxTargets) {
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("mode", "RADIUS");
        target.put("range", range);
        target.put("max-targets", maxTargets);
        return target;
    }

    // ---- navigation into the built document -------------------------------------------------------

    @SuppressWarnings("unchecked")
    static Map<String, Object> abilityOf(Map<String, Object> document, String id) {
        Map<String, Object> abilities = (Map<String, Object>) document.get("abilities");
        return (Map<String, Object>) abilities.get(id);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> abilities(Map<String, Object> document) {
        return (Map<String, Object>) document.get("abilities");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> targetOf(Map<String, Object> document, String id) {
        return (Map<String, Object>) abilityOf(document, id).get("target");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> effectOf(Map<String, Object> document, String id, int index) {
        List<Object> effects = (List<Object>) abilityOf(document, id).get("effects");
        return (Map<String, Object>) effects.get(index);
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> regenerationOf(Map<String, Object> document) {
        Map<String, Object> runtime = (Map<String, Object>) document.get("runtime");
        return (Map<String, Object>) runtime.get("regeneration");
    }

    @SuppressWarnings("unchecked")
    static Map<String, Object> runtimeOf(Map<String, Object> document) {
        return (Map<String, Object>) document.get("runtime");
    }
}
