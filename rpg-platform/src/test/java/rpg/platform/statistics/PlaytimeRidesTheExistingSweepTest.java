package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Prinzip II — <b>dieser Block plant keine eigene wiederkehrende Aufgabe ein.</b>
 *
 * <p>Die Zeitfortschreibung reitet auf {@code RpgPlugin.startInventorySweep} mit (R6). Der läuft
 * bereits im Autosave-Takt und über {@code inventoryModule.playersInPlay()} — genau die Liste und
 * genau der Takt, den FR-015 verlangt.
 *
 * <p><b>Warum das ein Test sein muss und keine Notiz.</b> Eine eigene Aufgabe je Spieler ist die
 * naheliegende Umsetzung von „alle dreißig Sekunden fortschreiben", und sie funktioniert
 * tadellos — bei fünfzig Spielern sind es fünfzig Aufgaben, die dasselbe tun wie ein vorhandener
 * Durchlauf, nur zu einem anderen Zeitpunkt. Nichts wird davon rot, die Werte stimmen, und der
 * Verstoß fällt erst im Lasttest auf, wenn ihn niemand mehr einem Block zuordnen kann.
 *
 * <p>Nach dem Muster von {@code NoGlobalSchedulerAccessTest} aus B01, das aus demselben Grund
 * existiert: eine Regel, die nur in einem Review lebt, bricht der nächste Block.
 */
class PlaytimeRidesTheExistingSweepTest {

    /** Was eine eingeplante Aufgabe verrät — unabhängig davon, wie sie genannt wird. */
    private static final List<String> SCHEDULING_CALLS =
            List.of(
                    "runAsyncDelayed(",
                    "runSyncDelayed(",
                    "runTaskTimer(",
                    "runTaskTimerAsynchronously(",
                    "scheduleAtFixedRate(",
                    "scheduleWithFixedDelay(",
                    "new Timer(",
                    "ScheduledExecutorService");

    @Test
    @DisplayName("Prinzip II - kein Quelltext dieses Blocks plant etwas ein")
    void nosourceOfThisBlockSchedulesAnything() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : statisticsSources()) {
            String code = codeOnly(Files.readString(source));
            for (String call : SCHEDULING_CALLS) {
                if (code.contains(call)) {
                    violations.add(source.getFileName() + ": " + call);
                }
            }
        }

        assertThat(violations)
                .as(
                        "die Fortschreibung reitet auf dem vorhandenen Inventar-Sweep mit (R6) -"
                                + " eine eigene Aufgabe je Spieler waere Prinzip II verletzt")
                .isEmpty();
    }

    @Test
    @DisplayName("der Waechter greift ueberhaupt - er findet die Aufrufe, wenn es sie gibt")
    void theguardWouldActuallyCatchOne() {
        // Gegenprobe: ohne sie koennte die Suche oben ins Leere laufen - etwa weil sich die
        // Schreibweise der Scheduler-Aufrufe geaendert hat -, und ein leeres Ergebnis saehe aus
        // wie Einhaltung.
        String pretendSource = "scheduler.runAsyncDelayed(interval, this::accrue);";

        assertThat(SCHEDULING_CALLS.stream().anyMatch(pretendSource::contains)).isTrue();
    }

    @Test
    @DisplayName("es gibt ueberhaupt Quelltext zu pruefen")
    void thereIsSourceToCheckAtAll() throws IOException {
        assertThat(statisticsSources())
                .as("ohne Dateien prueft dieser Test nichts")
                .isNotEmpty();
    }

    private static List<Path> statisticsSources() throws IOException {
        Path root = repositoryRoot();
        List<Path> sources = new ArrayList<>();
        for (String module : List.of("rpg-core", "rpg-persistence", "rpg-platform")) {
            Path main = root.resolve(module + "/src/main/java");
            if (!Files.isDirectory(main)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(main)) {
                walk.filter(path -> path.toString().endsWith(".java"))
                        .filter(
                                path ->
                                        path.toString()
                                                .replace(java.io.File.separatorChar, '/')
                                                .contains("/statistics/"))
                        .forEach(sources::add);
            }
        }
        return sources;
    }

    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        while (here != null && !Files.isDirectory(here.resolve("rpg-core"))) {
            here = here.getParent();
        }
        if (here == null) {
            throw new IllegalStateException("Wurzelverzeichnis nicht gefunden");
        }
        return here;
    }
}
