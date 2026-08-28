package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.stats.Attribute;
import rpg.core.stats.ModifierSet;
import rpg.core.stats.SourceId;
import rpg.core.stats.SourceKind;

/**
 * FR-034 und SC-017 — <b>B11 führt keine eigene Buff-Verwaltung ein.</b>
 *
 * <p>Der zeitliche Beitrag eines Tranks geht über {@code SourceKind.BUFF} an B04, genau wie B08s
 * {@code BuffEffect}, und das Ablaufen wird von <em>demselben Durchlauf</em> getrieben, der auch
 * dessen {@code expire()} aufruft. Es entsteht keine zweite Taktung und keine wiederkehrende Aufgabe
 * je Trank (Prinzip II).
 *
 * <p><b>Der letzte Test ist der, der das festhält</b> — er sieht im Quelltext nach, dass dieses
 * Paket nichts einplant. Ein Scheduler-Aufruf hier wäre bei 150 Spielern mit ein paar Tränken
 * schnell tausend Aufgaben für etwas, das aus zwei Zeitstempeln folgt.
 */
class ConsumableBuffUsesTheExistingSeamTest {

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-28T12:00:00Z"));
    private final RecordingEngine stats = new RecordingEngine();
    private final ConsumableBuffs buffs = new ConsumableBuffs(stats, clock);

    private static final UUID HOLDER = UUID.randomUUID();

    @Test
    @DisplayName("der Beitrag laeuft ueber SourceKind.BUFF - B08s Naht, nicht eine zweite")
    void theContributionGoesThroughTheBuffSeam() {
        buffs.apply(HOLDER, "potion.stoneskin", stoneskin());

        assertThat(stats.applied).hasSize(1);
        assertThat(stats.applied.get(0).source().kind())
                .as("dieselbe Art Quelle, die B08 fuer Faehigkeiten benutzt")
                .isEqualTo(SourceKind.BUFF);
        assertThat(stats.applied.get(0).modifiers()).hasSize(1);
    }

    @Test
    @DisplayName("er laeuft zeitstempelbasiert aus, wenn der Durchlauf danach sieht")
    void itExpiresWhenTheSweepLooks() {
        buffs.apply(HOLDER, "potion.stoneskin", stoneskin());

        assertThat(buffs.expire()).as("noch nicht abgelaufen").isZero();
        assertThat(buffs.activeCount()).isEqualTo(1);

        clock.advance(Duration.ofSeconds(46));

        assertThat(buffs.expire()).isEqualTo(1);
        assertThat(buffs.activeCount()).isZero();
        assertThat(stats.removed).hasSize(1);
    }

    @Test
    @DisplayName("ein zweiter Trank derselben Vorlage ERSETZT, er stapelt nicht")
    void asecondPotionReplacesRatherThanStacks() {
        buffs.apply(HOLDER, "potion.stoneskin", stoneskin());
        buffs.apply(HOLDER, "potion.stoneskin", stoneskin());

        assertThat(buffs.activeCount())
                .as("stapeln waere der Weg, mit zehn Traenken jeden Kampf zu gewinnen")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("zwei Halter mit demselben Trank sind zwei Quellen")
    void twoHoldersAreTwoSources() {
        buffs.apply(HOLDER, "potion.stoneskin", stoneskin());
        buffs.apply(UUID.randomUUID(), "potion.stoneskin", stoneskin());

        assertThat(buffs.activeCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("Tod, Logout und Charakterwechsel nehmen alles mit")
    void deathAndLogoutTakeEverythingWithThem() {
        buffs.apply(HOLDER, "potion.stoneskin", stoneskin());

        buffs.forget(HOLDER);

        assertThat(buffs.activeCount()).isZero();
        assertThat(stats.removed).hasSize(1);
    }

    @Test
    @DisplayName("SC-017 - dieses Paket plant NICHTS ein")
    void thispackageSchedulesNothing() throws IOException {
        // Der eigentliche Punkt. Eine Abklingzeit und eine Buff-Laufzeit folgen aus zwei
        // Zeitstempeln; sie herunterzuzaehlen waere bei 150 Spielern schnell tausend Aufgaben
        // fuer nichts (Prinzip II). Das Ablaufen reitet auf dem Durchlauf, den B08 ohnehin hat.
        // Kommentare raus, bevor gesucht wird (SourceGuard). Der erste Anlauf schlug an einem
        // Satz an, der erklaert, warum hier KEIN Timer steht - eine richtige Erklaerung als
        // Verstoss zu werten ist die haeufigste Art, eine Quelltextpruefung wertlos zu machen.
        List<String> offenders = new ArrayList<>();
        for (Path source : productionSources()) {
            String code = SourceGuard.codeOnly(Files.readString(source));
            for (String call :
                    List.of("scheduler", "Scheduler", "runAsyncDelayed", "runSyncAtLocation", "Timer")) {
                if (code.contains(call)) {
                    offenders.add(source.getFileName() + " mentions " + call);
                }
            }
        }

        assertThat(offenders)
                .as("keine wiederkehrende Aufgabe je Spieler, je Item oder je Trank")
                .isEmpty();
    }

    private static ConsumableEffect stoneskin() {
        return ConsumableEffect.buff(
                Map.of(Attribute.DEFENSE, 12.0), Duration.ofSeconds(45), Duration.ofSeconds(120));
    }

    private static List<Path> productionSources() throws IOException {
        Path root = Path.of("src", "main", "java", "rpg", "core", "item");
        if (!Files.isDirectory(root)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(root)) {
            return files.filter(path -> path.toString().endsWith(".java")).toList();
        }
    }

    /**
     * Schreibt auf, was an B04 gegeben wird — die Verrechnung hat ihre eigenen Tests.
     *
     * <p>Zwei Methoden, weil die Naht zwei hat. Der erste Anlauf implementierte die ganze
     * {@code StatEngine} mit ihren neunzehn — und genau daran fiel auf, dass die Abhängigkeit zu
     * breit war. Ein Test, der unangenehm zu schreiben ist, sagt oft etwas über den Entwurf.
     */
    private static final class RecordingEngine implements ConsumableBuffs.BuffSink {

        private final List<ModifierSet> applied = new ArrayList<>();
        private final List<SourceId> removed = new ArrayList<>();

        @Override
        public void apply(UUID holderId, ModifierSet set) {
            applied.add(set);
        }

        @Override
        public void remove(UUID holderId, SourceId source) {
            removed.add(source);
        }
    }

    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
