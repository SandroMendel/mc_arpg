package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * T103: je Schnittstelle genau ein Anbieter - zwei wären der Fehler, den ADR-005ff. an anderer
 * Stelle schon einmal gekostet hat ({@code NoCompetingSessionListenersTest} in B03).
 *
 * <p><b>Zwei verschiedene Bauformen, ein Risiko.</b> {@code CombatPipeline.setMobStatProvider} und
 * {@code Progression.setMobXpProvider} sind Ein-Platz-Setter - ein zweiter Aufruf gewinnt lautlos
 * und der erste verschwindet, ohne dass der Compiler etwas dazu sagt. {@code MobCoinProvider}
 * dagegen wird nicht gesetzt, sondern in genau eine {@code CoinDropPlanner}-Instanz hinein
 * konstruiert - eine zweite Instanz wäre derselbe Fehler, nur über den Konstruktor statt über einen
 * Setter.
 *
 * <p>Die Quelle wird gelesen, nicht die Klassen - das erfasst auch einen Block, der noch nicht
 * existiert, sobald er einmal geschrieben ist.
 */
class NoCompetingMobProviderTest {

    private static final Pattern SET_STAT_PROVIDER = Pattern.compile("\\.setMobStatProvider\\s*\\(");
    private static final Pattern SET_XP_PROVIDER = Pattern.compile("\\.setMobXpProvider\\s*\\(");
    private static final Pattern NEW_COIN_PLANNER =
            Pattern.compile("new\\s+(?:[\\w.]+\\.)?CoinDropPlanner\\s*\\(");

    @Test
    void exactlyOneCallSetsTheMobStatProvider() throws IOException {
        assertThat(countMatches(SET_STAT_PROVIDER))
                .as(
                        "CombatPipeline.setMobStatProvider ist ein Ein-Platz-Setter - ein zweiter"
                                + " Aufruf ueberschreibt den ersten lautlos")
                .isEqualTo(1);
    }

    @Test
    void exactlyOneCallSetsTheMobXpProvider() throws IOException {
        assertThat(countMatches(SET_XP_PROVIDER))
                .as(
                        "Progression.setMobXpProvider ist ein Ein-Platz-Setter - ein zweiter Aufruf"
                                + " ueberschreibt den ersten lautlos")
                .isEqualTo(1);
    }

    @Test
    void exactlyOneCoinDropPlannerIsConstructed() throws IOException {
        assertThat(countMatches(NEW_COIN_PLANNER))
                .as(
                        "eine zweite CoinDropPlanner-Instanz waere ein zweiter, unabhaengiger Weg,"
                                + " Coins fuer einen Kill auszuschuetten")
                .isEqualTo(1);
    }

    @Test
    void theScanActuallyReachesTheProjectSources() throws IOException {
        assertThat(javaSources()).hasSizeGreaterThan(20);
    }

    private static int countMatches(Pattern pattern) throws IOException {
        int total = 0;
        for (Path source : javaSources()) {
            Matcher matcher = pattern.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                total++;
            }
        }
        return total;
    }

    /** Jede {@code .java}-Datei im Projekt, ausser Tests - dieselben zaehlen nicht als Verdrahtung. */
    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> paths = Files.walk(repositoryRoot())) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/build/"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/test/"))
                    .toList();
        }
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("could not locate the repository root");
        }
        return candidate;
    }
}
