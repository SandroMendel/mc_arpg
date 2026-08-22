package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Stream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.ability.effect.EffectContext;
import rpg.core.ability.effect.EffectDispatcher;

/**
 * T098 - SC-001, die Zusage, um die herum dieser ganze Block gebaut wurde: <b>eine neue Fähigkeit
 * entsteht aus Konfiguration, nicht aus einer Java-Klasse.</b>
 *
 * <p>Der Test erfindet eine Fähigkeit, die es nirgends gibt - {@code probe.invented} -, gibt ihr drei
 * Wirkungen, eine Kegel-Zielwahl, eine Wirkzeit, Manakosten und einen Cooldown, und löst sie aus. Es
 * wird <b>keine einzige Klasse registriert, die nicht schon im Spiel steckt</b>. Käme man ohne Java
 * nicht aus, scheiterte hier etwas.
 *
 * <p>Die zweite Hälfte des Beweises steht in {@code NothingInJava}: die Kennung dieser Fähigkeit
 * kommt in keiner produktiven Quelldatei vor. Ein Test, der die Fähigkeit erfindet und dann eine
 * Sonderbehandlung im Code fände, hätte nichts bewiesen.
 */
class ConfigOnlyAbilityTest {

    /** Die erfundene Fähigkeit. Sie steht ausschließlich in diesem Test. */
    private static final String INVENTED = "probe.invented";

    private AbilityFixture fixture;
    private final List<String> seen = new ArrayList<>();

    @BeforeEach
    void setUp() throws Exception {
        fixture = inventedFixture();
    }

    @Nested
    @DisplayName("SC-001 - aus Konfiguration, ohne eine Zeile Java")
    class FromConfigurationAlone {

        @Test
        @DisplayName("die erfundene Fähigkeit existiert und trägt, was in der Konfiguration steht")
        void theInventedAbilityExists() {
            Ability invented = fixture.registry.config().require(INVENTED);

            assertThat(invented.kind()).isEqualTo(rpg.core.classes.AbilityKind.ACTIVE);
            assertThat(invented.manaCost()).isEqualTo(30.0);
            assertThat(invented.target().mode()).isEqualTo(TargetMode.CONE);
            assertThat(invented.target().maxTargets()).isEqualTo(5);
            assertThat(invented.effects())
                    .as("drei Bausteine, alle schon vorhanden")
                    .extracting(EffectSpec::type)
                    .containsExactly(EffectType.DAMAGE, EffectType.KNOCKBACK, EffectType.BUFF);
        }

        @Test
        @DisplayName("sie lässt sich auslösen und wirkt genau die drei Bausteine, in ihrer Reihenfolge")
        void itFiresAllThreeEffects() {
            fixture.resolvedTargets = List.of(UUID.randomUUID(), UUID.randomUUID());

            AbilityResult result = fixture.runtime.trigger(fixture.character, INVENTED);

            assertThat(result).isEqualTo(AbilityResult.CASTING);
            // 1,5 s Wirkzeit: erst danach wirkt sie.
            assertThat(seen).isEmpty();
            fixture.scheduling.runPending();

            assertThat(seen).containsExactly("DAMAGE", "KNOCKBACK", "BUFF");
        }

        @Test
        @DisplayName("Kosten und Cooldown kommen aus derselben Konfiguration")
        void costAndCooldownComeFromTheSameFile() {
            double before = fixture.stats.mana;
            fixture.resolvedTargets = List.of(UUID.randomUUID());

            fixture.runtime.trigger(fixture.character, INVENTED);
            fixture.scheduling.runPending();

            assertThat(fixture.stats.mana).isEqualTo(before - 30.0);
            // Ueber die globale Sperre hinweg, sonst antwortete die zuerst und der Cooldown bliebe
            // ungeprueft.
            fixture.clock.advance(java.time.Duration.ofSeconds(2));
            assertThat(fixture.runtime.trigger(fixture.character, INVENTED))
                    .as("12 s Cooldown, ebenfalls nur konfiguriert")
                    .isEqualTo(AbilityResult.ON_COOLDOWN);
        }

        @Test
        @DisplayName("T111 - ohne ein einziges Ziel kosten Mana und Cooldown trotzdem")
        void anEmptyAreaStillCosts() {
            double before = fixture.stats.mana;
            fixture.resolvedTargets = List.of();

            fixture.runtime.trigger(fixture.character, INVENTED);
            fixture.scheduling.runPending();

            // Der Kegel traf nichts - die Fähigkeit ist trotzdem passiert. Nur eine ABGEWIESENE
            // Auslösung ist kostenlos; eine, die ins Leere geht, ist ein Fehlschlag des Spielers.
            assertThat(fixture.stats.mana).isEqualTo(before - 30.0);
            // Ueber die globale Sperre hinweg, sonst antwortete die zuerst und der Cooldown bliebe
            // ungeprueft.
            fixture.clock.advance(java.time.Duration.ofSeconds(2));
            assertThat(fixture.runtime.trigger(fixture.character, INVENTED))
                    .isEqualTo(AbilityResult.ON_COOLDOWN);
        }
    }

    @Nested
    @DisplayName("Die andere Hälfte des Beweises")
    class NothingInJava {

        @Test
        @DisplayName("die Kennung kommt in keiner produktiven Quelldatei vor")
        void theIdAppearsInNoProductionSource() throws IOException {
            List<Path> offenders = new ArrayList<>();
            for (Path source : productionSources()) {
                if (Files.readString(source, StandardCharsets.UTF_8).contains(INVENTED)) {
                    offenders.add(source);
                }
            }

            assertThat(offenders)
                    .as(
                            "Wenn hier etwas steht, kennt der Code diese Fähigkeit - und SC-001 wäre"
                                    + " nicht belegt, sondern umgangen")
                    .isEmpty();
        }
    }

    // --- helpers ---

    /** Baut eine Konfiguration mit einer Fähigkeit, die es nirgends sonst gibt. */
    private AbilityFixture inventedFixture() throws Exception {
        Logger logger = Logger.getLogger(ConfigOnlyAbilityTest.class.getName());
        logger.setLevel(Level.OFF);

        Map<String, Object> document = AbilityConfigFixture.valid();
        Map<String, Object> ability = new LinkedHashMap<>();
        ability.put("kind", "ACTIVE");
        ability.put("display-name-key", "ability.probe.invented.name");
        ability.put("item", "BLAZE_ROD");
        ability.put("mana-cost", 30.0);
        ability.put("cooldown-ms", 12000);
        ability.put("cast-time-ms", 1500);
        ability.put("max-rank", 3);

        Map<String, Object> target = new LinkedHashMap<>();
        target.put("mode", "CONE");
        target.put("range", 6.0);
        target.put("angle", 45.0);
        target.put("max-targets", 5);
        ability.put("target", target);

        Map<String, Object> damage = new LinkedHashMap<>();
        damage.put("type", "DAMAGE");
        damage.put("damage-type", "MAGIC");
        damage.put("amount", 2.0);
        damage.put("per-rank", 0.25);

        Map<String, Object> knockback = new LinkedHashMap<>();
        knockback.put("type", "KNOCKBACK");
        knockback.put("amount", 1.2);

        Map<String, Object> buff = new LinkedHashMap<>();
        buff.put("type", "BUFF");
        buff.put("attribute", "movementSpeed");
        buff.put("amount", 0.15);
        buff.put("duration-ms", 4000);

        ability.put("effects", new ArrayList<>(List.of(damage, knockback, buff)));
        AbilityConfigFixture.abilities(document).put(INVENTED, ability);

        AbilityFixture built = AbilityFixture.of(AbilityConfigFixture.bind(document), logger, INVENTED);

        // Die drei Bausteine sind die, die das Spiel ohnehin mitbringt. Aufgezeichnet wird nur, DASS
        // sie liefen - was sie tun, pruefen ihre eigenen Tests.
        EffectDispatcher dispatcher = built.dispatcher;
        dispatcher.register(EffectType.DAMAGE, record("DAMAGE"));
        dispatcher.register(EffectType.KNOCKBACK, record("KNOCKBACK"));
        dispatcher.register(EffectType.BUFF, record("BUFF"));
        return built;
    }

    private rpg.core.ability.effect.AbilityEffect record(String name) {
        return (EffectContext context) -> seen.add(name);
    }

    private static List<Path> productionSources() throws IOException {
        try (Stream<Path> paths = Files.walk(repositoryRoot())) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> path.toString().replace('\\', '/'))
                    .filter(path -> path.toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(path -> path.contains("/src/main/java/"))
                    .filter(path -> !path.contains("/build/"))
                    .map(Path::of)
                    .toList();
        }
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("could not find the repository root");
        }
        return candidate;
    }
}
