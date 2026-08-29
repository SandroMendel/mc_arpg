package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * FR-018 — <b>der Wächter des Verzeichnisses.</b>
 *
 * <p>Ein Metrikschlüssel als Zeichenkette an der Aufrufstelle ist kein Schönheitsfehler. Er umgeht
 * den Verzeichniseintrag, und mit ihm die {@link MetricKind Art} und die {@link MetricVisibility
 * Sichtbarkeit} — die beiden Eigenschaften, an denen hängt, ob ein Wert addiert oder maximiert wird
 * und ob er ein fremdes Profil erreichen darf. Ein solcher Aufruf sieht dabei völlig harmlos aus:
 * er übersetzt sauber, läuft sauber und schreibt eine plausible Zahl an eine falsche Stelle.
 *
 * <p><b>Deshalb liest dieser Test die Quellen und nicht das Verhalten.</b> Ein Verhaltenstest kann
 * zeigen, dass ein Aufruf richtig zählt; er kann nicht zeigen, dass es keinen zweiten gibt, der es
 * anders tut. Nach dem Muster von {@code ConfigOnlyMobTest} aus B10.
 *
 * <h2>Zwei Prüfungen, weil eine allein eine Lücke hätte</h2>
 *
 * <p><b>Die erste sucht im ganzen Projekt</b> — aber nur nach den Schlüsseln, die tatsächlich in
 * der Tagestabelle landen. {@link MetricKind#STATE}-Metriken sind ausgenommen, und zwar nicht aus
 * Bequemlichkeit: Level, XP und Coins werden nach FR-019 <b>nie</b> als Metrikschlüssel
 * gespeichert. Sie heißen so, wie sie in {@code progression.yml}, in {@code character_progress}
 * und in einem Dutzend Meldungen anderer Blöcke seit Langem heißen. Diese Wörter zu verbieten
 * hieße nicht, B12 zu sichern, sondern B06 bis B11 nach einer Regel zu bestrafen, die für sie nie
 * galt — der Wächter fing mit 34 Fehlalarmen an, und kein einziger war ein Verstoß.
 *
 * <p><b>Die zweite schließt genau die Lücke, die dadurch entsteht</b>, und zwar strenger: in den
 * Quellen dieses Blocks darf an einen Zähl- oder Leseaufruf <em>überhaupt kein</em> Literal
 * übergeben werden, auch keins, das nach nichts aussieht. Wo eine Metrik hingehört, steht ein
 * Verzeichniseintrag.
 */
class NoMetricLiteralsTest {

    /** Die eine Datei, in der die Schlüssel stehen dürfen. */
    private static final String THE_REGISTRY = "MetricRegistry.java";

    /** Ein Zähl- oder Leseaufruf, in dessen Argumenten ein Literal steht. */
    private static final Pattern LITERAL_ARGUMENT =
            Pattern.compile("\\b(count|reportMax|increment|value|breakdown)\\s*\\([^;)]*\"");

    @Test
    @DisplayName(
            "FR-018 - kein gespeicherter Metrikschluessel steht als Literal ausserhalb des"
                    + " Verzeichnisses")
    void noStoredMetricKeyAppearsAsALiteralOutsideTheRegistry() throws IOException {
        List<String> storedKeys =
                MetricRegistry.all().values().stream()
                        .filter(metric -> metric.kind() != MetricKind.STATE)
                        .map(Metric::key)
                        .toList();

        assertThat(storedKeys)
                .as("ohne gespeicherte Metriken prueft dieser Test nichts")
                .isNotEmpty();

        List<String> violations = new ArrayList<>();
        for (Path source :
                SourceGuard.productionSources(
                        "rpg-core", "rpg-persistence", "rpg-platform", "rpg-plugin")) {
            if (source.getFileName().toString().equals(THE_REGISTRY)) {
                continue;
            }
            String code = SourceGuard.codeOnly(Files.readString(source));
            for (String key : storedKeys) {
                if (code.contains('"' + key)) {
                    violations.add(source.getFileName() + ": \"" + key + "\"");
                }
            }
        }

        assertThat(violations)
                .as(
                        "Metrikschluessel gehoeren ausschliesslich in %s - ein Literal umgeht Art"
                                + " und Sichtbarkeit des Verzeichniseintrags (FR-018)",
                        THE_REGISTRY)
                .isEmpty();
    }

    @Test
    @DisplayName("FR-018 - im Block selbst nimmt kein Zaehl- oder Leseaufruf ein Literal entgegen")
    void insideTheBlockNoCallTakesALiteralWhereAMetricBelongs() throws IOException {
        List<String> violations = new ArrayList<>();
        for (Path source : SourceGuard.statisticsSources()) {
            if (source.getFileName().toString().equals(THE_REGISTRY)) {
                continue;
            }
            String code = SourceGuard.codeOnly(Files.readString(source));
            Matcher matcher = LITERAL_ARGUMENT.matcher(code);
            while (matcher.find()) {
                violations.add(source.getFileName() + ": " + matcher.group(1) + "(... \"...\")");
            }
        }

        assertThat(violations)
                .as(
                        "wo eine Metrik hingehoert, steht ein Verzeichniseintrag - auch dann, wenn"
                                + " das Literal harmlos aussieht (FR-018)")
                .isEmpty();
    }

    @Test
    @DisplayName("der Waechter greift ueberhaupt - das Verzeichnis selbst traegt die Literale")
    void theRegistryItselfIsWhereTheLiteralsLive() throws IOException {
        Path registry =
                SourceGuard.repositoryRoot()
                        .resolve("rpg-core/src/main/java/rpg/core/statistics/" + THE_REGISTRY);
        String code = SourceGuard.codeOnly(Files.readString(registry));

        // Ohne diese Gegenprobe koennte die Suche oben ins Leere laufen - etwa weil die Schluessel
        // gar nicht mehr als Literale gebildet werden -, und ein leeres Ergebnis saehe aus wie
        // Einhaltung.
        for (Metric metric : MetricRegistry.all().values()) {
            assertThat(code).contains('"' + metric.key() + '"');
        }
    }
}
