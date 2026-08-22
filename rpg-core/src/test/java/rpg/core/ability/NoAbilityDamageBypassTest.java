package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T137 - <b>B08 erzeugt Schaden ausschliesslich über die Kampf-Pipeline</b> (FR-068, Prinzip III).
 *
 * <p>Ein Quellentest, weil die Zusage eine über Abwesenheit ist. Ein Verhaltenstest könnte zeigen,
 * dass ein bestimmter Effekt durch die Pipeline geht; er kann nicht zeigen, dass <em>keiner</em>
 * daran vorbeigeht - und genau der eine, den jemand später hinzufügt, wäre der Fall.
 *
 * <p>Vorbei ginge es über {@code setHealth}, {@code damage(...)} oder eine eigene Rechnung mit
 * Rüstung. Jede davon hiesse: Schaden, den Ausweichen nicht abweisen, kein Schild absorbieren und
 * keine Anzeige melden kann - und niemand fände heraus warum, weil er ja ankommt.
 *
 * <p>Die Sperre für {@code rpg-platform} ist enger als die für {@code rpg-core}: dort <b>gibt</b> es
 * Bukkit, also ist die Versuchung real.
 */
class NoAbilityDamageBypassTest {

    private static final String B08_CORE = "/rpg/core/ability/";
    private static final String B08_PLATFORM = "/rpg/platform/ability/";

    /**
     * Was hier stehen darf, und warum. Jeder Eintrag ist eine Ausnahme, und eine Ausnahme ohne
     * Begruendung ist eine Stelle, die jemand hineingeschrieben und niemand geprueft hat.
     */
    private static final List<String> PERMITTED_HEALTH_ACCESS =
            List.of(
                    // Setzt die STARTgesundheit des frisch gerufenen Klons. Niemandem wird dabei
                    // Schaden zugefuegt - das Wesen entsteht mit diesem Wert.
                    "PaperSummons.java",
                    // HOERT auf Schadensereignisse, um einen Cast abzubrechen. Lesen, nicht austeilen.
                    "CastInterruptListener.java");

    @Test
    @DisplayName("FR-068: kein Quelltext dieses Blocks teilt Schaden an der Pipeline vorbei aus")
    void nothingDealsDamageDirectly() throws IOException {
        // setHealth und ein Bukkit-damage(...) sind die zwei Wege vorbei. Der dritte - eine eigene
        // Ruestungsrechnung - hat keinen Namen, den man suchen koennte, und wird von der Abwesenheit
        // der ersten beiden mit erschlagen: ohne eine dieser Methoden landet die Zahl nirgends.
        List<String> forbidden = List.of("setHealth(", "EntityDamageEvent");

        List<String> violations = new ArrayList<>();
        for (Path source : blockSources()) {
            String name = source.getFileName().toString();
            if (PERMITTED_HEALTH_ACCESS.contains(name)) {
                continue;
            }
            String content = codeOf(source);
            for (String needle : forbidden) {
                if (content.contains(needle)) {
                    violations.add(name + " uses " + needle);
                }
            }
        }

        assertThat(violations)
                .as(
                        "Schaden aus einer Faehigkeit laeuft durch CombatPipeline.abilityDamage wie"
                                + " jeder andere. Daran vorbei hiesse: kein Ausweichen, kein Schild,"
                                + " keine Anzeige - und niemand faende heraus warum")
                .isEmpty();
    }

    @Test
    @DisplayName("genau zwei Stellen sprechen die Pipeline an, und beide haben einen Grund")
    void onlyTwoPlacesCallThePipeline() throws IOException {
        List<String> callers = new ArrayList<>();
        for (Path source : blockSources()) {
            if (codeOf(source).contains("pipeline.abilityDamage(")) {
                callers.add(source.getFileName().toString());
            }
        }

        // DamageEffect ist der gewoehnliche Weg. AbilityProjectile ist der zweite, weil ein Geschoss
        // erst spaeter ankommt: der Effekt ist da laengst vorbei, und der Werfer moeglicherweise auch.
        // Ein DRITTER waere ein dritter Ort, an dem entschieden wird, wie eine Faehigkeit Schaden
        // macht - und dann sind es irgendwann drei Antworten.
        assertThat(callers)
                .containsExactlyInAnyOrder("DamageEffect.java", "AbilityProjectile.java");
    }

    @Test
    @DisplayName("der Effekt deckelt die Ziele nicht nach - die Zielwahl hat das schon getan")
    void theEffectDoesNotCapAgain() throws IOException {
        Path damageEffect =
                repositoryRoot()
                        .resolve("rpg-core/src/main/java/rpg/core/ability/effect/DamageEffect.java");
        String code = codeOf(damageEffect);

        // Zwei Stellen, die eine Regel durchsetzen, heisst: eine davon ist irgendwann falsch. Der
        // Deckel gehört dem Resolver, und dass er hier NICHT noch einmal steht, ist die Zusage.
        assertThat(code).doesNotContain("maxTargets");
        assertThat(code).doesNotContain("subList");
    }

    // --- helpers ---

    private static String codeOf(Path source) throws IOException {
        String content = Files.readString(source, StandardCharsets.UTF_8);
        return content.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    /** Beide Hälften des Blocks: die Regeln in core und die Paper-Seite in platform. */
    private static List<Path> blockSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path path : javaSources()) {
            String normalised = path.toString().replace('\\', '/');
            if (normalised.contains("/test/")) {
                continue;
            }
            if (normalised.contains(B08_CORE) || normalised.contains(B08_PLATFORM)) {
                sources.add(path);
            }
        }
        return sources;
    }

    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> paths = Files.walk(repositoryRoot())) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/build/"))
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
