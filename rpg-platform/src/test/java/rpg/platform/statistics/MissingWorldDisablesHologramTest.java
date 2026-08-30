package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.LeaderboardCache;
import rpg.core.statistics.Leaderboards;
import rpg.core.statistics.Period;
import rpg.core.statistics.StatisticsConfig;
import rpg.core.statistics.StatisticsMessageKeys;

/**
 * FR-064 — <b>eine nicht ladbare Stelle schaltet die Anzeige ab. Der Server startet.</b>
 *
 * <p>Ein Server, der wegen einer falsch geschriebenen Welt in {@code statistics.yml} nicht
 * hochkommt, wäre die teuerste denkbare Antwort auf einen Tippfehler — und die Anzeige ist Zierde,
 * nicht Spielmechanik. Niemand kann heute noch spielen, weil vorgestern jemand {@code wolrd}
 * geschrieben hat.
 *
 * <p><b>Aber still darf es nicht sein.</b> Eine Anzeige, die einfach nicht da ist, sucht man im
 * Hub und nicht im Protokoll. Deshalb prüft dieser Test beides: dass nichts fliegt <em>und</em>
 * dass eine Warnung dasteht, die den Namen der Welt nennt — den einen Hinweis, mit dem ein
 * Betreiber den Tippfehler findet.
 */
class MissingWorldDisablesHologramTest {

    private static final Instant NOW = Instant.parse("2026-08-30T12:00:00Z");

    private ServerMock server;
    private Logger logger;
    private final List<LogRecord> logged = new ArrayList<>();

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        server.addSimpleWorld("world");

        logged.clear();
        logger = Logger.getLogger(MissingWorldDisablesHologramTest.class.getName() + Math.random());
        logger.setUseParentHandlers(false);
        logger.addHandler(
                new Handler() {
                    @Override
                    public void publish(LogRecord record) {
                        logged.add(record);
                    }

                    @Override
                    public void flush() {}

                    @Override
                    public void close() {}
                });
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("FR-064 - eine unbekannte Welt setzt keine Anzeige und wirft nichts")
    void anunknownWorldPlacesNothingAndThrowsNothing() {
        LeaderboardHologram hologram = hologram();

        assertThatCode(() -> hologram.place(settings("wolrd"), NOW)).doesNotThrowAnyException();
        assertThat(hologram.place(settings("wolrd"), NOW)).isEmpty();
        assertThat(hologram.isPlaced()).isFalse();
    }

    @Test
    @DisplayName("FR-064 - und sie sagt im Protokoll, WELCHE Welt fehlt")
    void anditSaysWhichWorldIsMissing() {
        hologram().place(settings("wolrd"), NOW);

        // Ohne den Namen waere die Warnung "irgendwas mit dem Hologramm" - und der Tippfehler
        // stuende weiter in der Datei.
        assertThat(logged)
                .anyMatch(
                        record ->
                                record.getLevel() == Level.WARNING
                                        && record.getMessage().contains("wolrd"));
    }

    @Test
    @DisplayName("dieselbe Konfiguration mit der RICHTIGEN Welt setzt sie sehr wohl")
    void thesameConfigWithTheRightWorldDoesPlaceIt() {
        // Der Gegenbeweis: die Abschaltung liegt an der Welt und nicht daran, dass hier ueberhaupt
        // nichts gesetzt werden koennte.
        LeaderboardHologram hologram = hologram();

        assertThat(hologram.place(settings("world"), NOW)).isPresent();
        assertThat(hologram.isPlaced()).isTrue();
    }

    @Test
    @DisplayName("und eine abgeschaltete Anzeige laesst sich gefahrlos auffrischen")
    void adisabledHologramCanBeRefreshedSafely() {
        LeaderboardHologram hologram = hologram();
        hologram.place(settings("wolrd"), NOW);

        // Der Auffrischungstakt laeuft weiter, ob die Anzeige steht oder nicht. Ein Takt, der auf
        // eine fehlende Anzeige mit einer Ausnahme antwortet, nimmt die Ranglisten mit.
        assertThatCode(() -> hologram.refresh(NOW)).doesNotThrowAnyException();
    }

    private LeaderboardHologram hologram() {
        return new LeaderboardHologram(
                Leaderboards.backedBy(LeaderboardCache.empty()), messages(), logger);
    }

    private static StatisticsConfig.Hologram settings(String world) {
        return new StatisticsConfig.Hologram(
                world, 0.5, 65.0, 0.5, Aggregation.MOB_KILLS, Period.ALL_TIME, 10);
    }

    private static Messages messages() {
        Map<String, String> texts = new HashMap<>();
        texts.put(StatisticsMessageKeys.HOLOGRAM_HEADER.value(), "{board} - {period}");
        texts.put(StatisticsMessageKeys.HOLOGRAM_LINE.value(), "#{rank} {player} - {value}");
        texts.put(StatisticsMessageKeys.LEADERBOARD_AS_OF.value(), "Updated {age} ago.");
        texts.put(StatisticsMessageKeys.boardName(Aggregation.MOB_KILLS).value(), "Kill Participation");
        texts.put(StatisticsMessageKeys.periodName(Period.ALL_TIME).value(), "All Time");
        return new MapMessages(texts);
    }
}
