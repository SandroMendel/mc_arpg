package rpg.plugin.command.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.statistics.Period;

/** T026 — jeder Argumenttyp, einschließlich seiner Grenzen. */
class ArgumentValidationTest {

    @Nested
    @DisplayName("AMOUNT")
    class Amount {

        private final ArgumentType<Long> type = Arguments.amount(1, 100);

        @Test
        @DisplayName("liest eine Zahl im Bereich")
        void readsANumber() throws Exception {
            assertThat(type.parse("42")).isEqualTo(42L);
        }

        @Test
        @DisplayName("die GRENZEN gehoeren dazu - 1 und 100 sind gueltig")
        void theBoundsAreInclusive() throws Exception {
            // Der haeufigste Fehler bei einer Bereichspruefung ist der um eins verfehlte Rand, und
            // er faellt im Spiel erst auf, wenn jemand genau den Randwert tippt.
            assertThat(type.parse("1")).isEqualTo(1L);
            assertThat(type.parse("100")).isEqualTo(100L);
        }

        @Test
        @DisplayName("ausserhalb des Bereichs nennt den BEREICH, nicht die Nutzungszeile")
        void outsideNamesTheRange() {
            ArgumentRejected rejected =
                    catchThrowableOfType(ArgumentRejected.class, () -> type.parse("101"));

            assertThat(rejected.key().value()).isEqualTo("command.error.argument-out-of-range");
            assertThat(rejected.placeholders())
                    .as("FR-004: welches Argument und welcher Bereich")
                    .containsEntry("argument", "amount")
                    .containsEntry("value", "101")
                    .containsEntry("min", "1")
                    .containsEntry("max", "100");
        }

        @Test
        @DisplayName("keine Zahl wird abgelehnt statt still zu 0 zu werden")
        void nonNumbersAreRejected() {
            assertThatThrownBy(() -> type.parse("viele")).isInstanceOf(ArgumentRejected.class);
        }

        @Test
        @DisplayName("schlaegt NICHTS vor - eine Zahl vorzuschlagen hiesse raten")
        void suggestsNothing() {
            assertThat(type.suggest("")).isEmpty();
        }

        @Test
        @DisplayName("min ueber max ist ein Programmierfehler und faellt beim Bauen auf")
        void aninvertedRangeFailsFast() {
            assertThatThrownBy(() -> Arguments.amount(10, 1))
                    .isInstanceOf(IllegalArgumentException.class);
        }
    }

    @Nested
    @DisplayName("LEVEL")
    class Level {

        @Test
        @DisplayName("die Obergrenze kommt aus B06 und wird bei JEDEM Aufruf gefragt")
        void themaximumComesFromProgression() throws Exception {
            // Der Grund, aus dem hier ein IntSupplier steht und keine 60: die Hoechststufe ist
            // Konfiguration (XpCurve). Ein /rpg reload, das die Kurve aendert, muss sofort
            // durchschlagen - sonst nimmt die Pruefung eine Stufe nicht an, die es im Spiel gibt.
            AtomicInteger max = new AtomicInteger(60);
            ArgumentType<Integer> type = Arguments.level(max::get);

            assertThat(type.parse("60")).isEqualTo(60);
            assertThatThrownBy(() -> type.parse("61")).isInstanceOf(ArgumentRejected.class);

            max.set(80);

            assertThat(type.parse("61")).as("nach dem Nachladen gilt die neue Kurve").isEqualTo(61);
            assertThat(type.expected()).isEqualTo("1-80");
        }

        @Test
        @DisplayName("Stufe 0 und negative Stufen gibt es nicht")
        void zeroAndBelowAreRejected() {
            ArgumentType<Integer> type = Arguments.level(() -> 60);

            assertThatThrownBy(() -> type.parse("0")).isInstanceOf(ArgumentRejected.class);
            assertThatThrownBy(() -> type.parse("-5")).isInstanceOf(ArgumentRejected.class);
        }
    }

    @Nested
    @DisplayName("PERIOD")
    class Periods {

        private final ArgumentType<Period> type = Arguments.period();

        @Test
        @DisplayName("ALL_TIME heisst getippt all-time")
        void theunderscoreBecomesAHyphen() throws Exception {
            // Der Unterstrich hat in B11 die Attributnamen und in B12 die Ranglistennamen
            // erwischt. Ein Spieler kann ALL_TIME nicht tippen.
            assertThat(type.parse("all-time")).isEqualTo(Period.ALL_TIME);
        }

        @Test
        @DisplayName("Grossschreibung stoert nicht")
        void caseDoesNotMatter() throws Exception {
            assertThat(type.parse("DAY")).isEqualTo(Period.DAY);
        }

        @Test
        @DisplayName("jeder Wert der Aufzaehlung ist erreichbar")
        void everyConstantIsReachable() throws Exception {
            for (Period period : Period.values()) {
                assertThat(type.parse(Arguments.label(period))).isEqualTo(period);
            }
        }

        @Test
        @DisplayName("ein unbekannter Zeitraum nennt die gueltigen")
        void anunknownPeriodNamesTheValidOnes() {
            ArgumentRejected rejected =
                    catchThrowableOfType(ArgumentRejected.class, () -> type.parse("gestern"));

            assertThat(rejected.placeholders().get("expected"))
                    .contains("day")
                    .contains("all-time");
        }
    }

    @Nested
    @DisplayName("DURATION")
    class Durations {

        private final ArgumentType<Duration> type = Arguments.duration(Duration.ofDays(30));

        @Test
        @DisplayName("Minuten, Stunden und Tage")
        void thethreeUnits() throws Exception {
            assertThat(type.parse("30m")).isEqualTo(Duration.ofMinutes(30));
            assertThat(type.parse("12h")).isEqualTo(Duration.ofHours(12));
            assertThat(type.parse("7d")).isEqualTo(Duration.ofDays(7));
        }

        @Test
        @DisplayName("ueber der Obergrenze wird abgelehnt")
        void abovetheCapIsRejected() {
            assertThatThrownBy(() -> type.parse("31d")).isInstanceOf(ArgumentRejected.class);
        }

        @Test
        @DisplayName("null, negativ und ohne Einheit sind keine Zeitspanne")
        void nonsenseIsRejected() {
            assertThatThrownBy(() -> type.parse("0h")).isInstanceOf(ArgumentRejected.class);
            assertThatThrownBy(() -> type.parse("-3h")).isInstanceOf(ArgumentRejected.class);
            assertThatThrownBy(() -> type.parse("12")).isInstanceOf(ArgumentRejected.class);
            assertThatThrownBy(() -> type.parse("12w")).isInstanceOf(ArgumentRejected.class);
            assertThatThrownBy(() -> type.parse("h")).isInstanceOf(ArgumentRejected.class);
        }
    }

    @Nested
    @DisplayName("Vorschlaege werden begrenzt und gefiltert (FR-033)")
    class Suggestions {

        @Test
        @DisplayName("am schon Getippten gefiltert")
        void filteredByWhatWasTyped() {
            assertThat(Arguments.period().suggest("al")).containsExactly("all-time");
        }

        @Test
        @DisplayName("nie mehr als die Obergrenze - auch bei leerer Eingabe")
        void neverMoreThanTheLimit() {
            java.util.List<String> many = new java.util.ArrayList<>();
            for (int i = 0; i < ArgumentType.SUGGESTION_LIMIT * 3; i++) {
                many.add("name" + i);
            }

            assertThat(Arguments.filtered(many, ""))
                    .as("ein leeres Feld darf nicht tausend Namen senden")
                    .hasSize(ArgumentType.SUGGESTION_LIMIT);
        }
    }
}
