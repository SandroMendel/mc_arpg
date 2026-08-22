package rpg.core.ability.effect;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.ability.Ability;
import rpg.core.ability.AbilityConfigFixture;
import rpg.core.ability.EffectType;
import rpg.core.stats.Attribute;
import rpg.core.stats.StatSnapshot;

/**
 * T102 - SC-010: <b>eine Ausnahme in einem Baustein reißt weder die übrigen noch die Sitzung mit.</b>
 *
 * <p>Das ist die Zusage, die die ganze Bauform erst tragfähig macht. Eine Fähigkeit besteht aus
 * Bausteinen, die aus einer Konfigurationsdatei zusammengesteckt werden - also aus einer Datei, die
 * ein Betreiber ändert. Ohne Barriere hieße ein Fehler in {@code abilities.yml}, dass ein Rechtsklick
 * eine Ausnahme bis in den Event-Handler wirft und Paper den Spieler dafür hinauswirft.
 */
class EffectDispatcherTest {

    private EffectDispatcher dispatcher;
    private final List<String> ran = new ArrayList<>();

    @BeforeEach
    void setUp() {
        Logger logger = Logger.getLogger(EffectDispatcherTest.class.getName());
        logger.setLevel(Level.OFF);
        dispatcher = new EffectDispatcher(logger);
    }

    @Test
    @DisplayName("SC-010: der Baustein davor hat gewirkt, der danach wirkt trotzdem")
    void oneFailingEffectDoesNotStopTheOthers() throws Exception {
        dispatcher.register(EffectType.DAMAGE, context -> ran.add("first"));
        dispatcher.register(
                EffectType.HEAL,
                context -> {
                    throw new IllegalStateException("what a bad configuration value looks like");
                });
        dispatcher.register(EffectType.MANA_RESTORE, context -> ran.add("third"));

        assertThatCode(() -> run(threeEffectAbility()))
                .as("die Ausnahme kommt nicht beim Aufrufer an - sonst nähme sie den Spieler mit")
                .doesNotThrowAnyException();

        assertThat(ran)
                .as("der davor ist gelaufen, der danach auch - nur der kaputte fehlt")
                .containsExactly("first", "third");
    }

    @Test
    @DisplayName("auch ein Fehler ohne Ausnahmetyp - ein Error - bleibt hinter der Barriere")
    void evenTheUglyOnesAreCaught() throws Exception {
        dispatcher.register(EffectType.DAMAGE, context -> ran.add("first"));
        dispatcher.register(
                EffectType.HEAL,
                context -> {
                    throw new NullPointerException("something forgot to check for null");
                });
        dispatcher.register(EffectType.MANA_RESTORE, context -> ran.add("third"));

        assertThatCode(() -> run(threeEffectAbility())).doesNotThrowAnyException();
        assertThat(ran).containsExactly("first", "third");
    }

    @Test
    @DisplayName("ein Baustein ohne registrierte Umsetzung ist still, nicht tödlich")
    void anUnregisteredEffectIsSilent() throws Exception {
        assertThatCode(() -> run(threeEffectAbility())).doesNotThrowAnyException();
        assertThat(ran).isEmpty();
    }

    // --- helpers ---

    private void run(Ability ability) {
        dispatcher.run(ability, UUID.randomUUID(), List.of(UUID.randomUUID()), 1, snapshot());
    }

    /** Eine Fähigkeit mit drei Bausteinen - der mittlere ist der, der scheitern soll. */
    private static Ability threeEffectAbility() throws Exception {
        Map<String, Object> document = AbilityConfigFixture.valid();
        Map<String, Object> ability = AbilityConfigFixture.activeAbility();
        ability.put(
                "effects",
                new ArrayList<>(
                        List.of(
                                effect("DAMAGE", "damage-type", "PHYSICAL"),
                                effect("HEAL", null, null),
                                effect("MANA_RESTORE", null, null))));
        AbilityConfigFixture.abilities(document).put("probe.three", ability);
        return AbilityConfigFixture.bind(document).require("probe.three");
    }

    private static Map<String, Object> effect(String type, String extraKey, String extraValue) {
        Map<String, Object> effect = new LinkedHashMap<>();
        effect.put("type", type);
        effect.put("amount", 1.0);
        if (extraKey != null) {
            effect.put(extraKey, extraValue);
        }
        return effect;
    }

    private static StatSnapshot snapshot() {
        return new StatSnapshot(new double[Attribute.count()], 1L);
    }
}
