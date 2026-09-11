package rpg.core.ui;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.UUID;

/**
 * Testdaten für B13 — zwei Vorkehrungen, die dieser Block beide braucht.
 *
 * <h2>1 · Halter und Charakter tragen hier nie dieselbe Kennung</h2>
 *
 * <p>Dieses Projekt kennt zwei Kennungen, die beide {@code UUID} heißen und beide einen Spieler
 * meinen — die <b>Halterkennung</b> (das Konto, die Spieler-UUID) und die <b>Charakterkennung</b>
 * (die gerade gespielte Figur). Welche wohin gehört, entscheidet nicht der Typ, sondern die Absicht
 * des Aufrufers. Wer sie vertauscht, bekommt keinen Übersetzungsfehler, sondern ein leeres
 * {@code Optional} — und das sieht aus wie „nicht im Spiel".
 *
 * <p>Genau das ist zweimal passiert, in B08 und in B11, und <b>keinem von 1614 Tests</b> ist es
 * aufgefallen: die Testdaten benutzten für beides dieselbe UUID, also war die Verwechslung
 * systematisch unsichtbar. Diese Fixture erzeugt sie deshalb grundsätzlich getrennt.
 *
 * <p><b>B13 ist dafür besonders anfällig.</b> Er liest zehn fremde Blöcke, und die nehmen
 * unterschiedliche Kennungen: {@code StatSnapshot.get} und {@code Currency} wollen die
 * <em>Charakter</em>kennung, {@code HudRenderer} und der {@code Scheduler} den <em>Spieler</em>.
 * Eine Anzeige, die deshalb leer bleibt, sieht aus wie „noch kein Wert" — der Fehlermodus dieses
 * Blocks ist ausgerechnet einer, der nicht auffällt.
 *
 * <h2>2 · Die Uhr wird gestellt, nicht gelesen</h2>
 *
 * <p>Jede Füllstandsrechnung dieses Blocks geht gegen die Uhr: der Kanalisierungsbalken aus
 * {@code startedAt}/{@code dueAt}, die Cooldown-Restzeit, die Lebensdauer einer Schadenszahl, die
 * Standzeit des Zonennamens. Das ist Absicht (Constitution II.2: zeitstempelbasiert lazy statt
 * einer Aufgabe je Spieler) — und es heißt, dass ein Test mit {@code Instant.now()} entweder
 * flackert oder nichts beweist.
 */
final class UiFixtures {

    private UiFixtures() {}

    /**
     * Ein zusammengehöriges Paar aus Konto und Figur.
     *
     * <p>Die beiden Felder heißen absichtlich nicht beide {@code id}: an einer Aufrufstelle soll
     * dastehen, <em>welche</em> der beiden gemeint ist.
     */
    record Identities(UUID holderId, UUID characterId) {

        Identities {
            if (holderId.equals(characterId)) {
                throw new IllegalArgumentException(
                        "Halter- und Charakterkennung sind gleich - genau die Verwechslung, "
                                + "die diese Fixture verhindern soll");
            }
        }
    }

    /** Ein frisches Paar; die beiden Kennungen sind garantiert verschieden. */
    static Identities identities() {
        return new Identities(UUID.randomUUID(), UUID.randomUUID());
    }

    /** Nur die Halterkennung — für Aufrufe, die kein Gegenstück brauchen. */
    static UUID holderId() {
        return identities().holderId();
    }

    /** Nur die Charakterkennung — für Aufrufe, die kein Gegenstück brauchen. */
    static UUID characterId() {
        return identities().characterId();
    }

    /**
     * Ein fester Zeitpunkt, gegen den gerechnet wird.
     *
     * <p>Kein runder Wert und keine Epoche: ein Fehler, der nur bei {@code 0} verschwindet — eine
     * vergessene Subtraktion etwa —, bliebe an einer runden Zahl unsichtbar.
     */
    static final Instant T0 = Instant.parse("2026-08-30T17:04:31.375Z");

    /** Eine Uhr, die genau {@link #T0} zeigt und nicht weiterläuft. */
    static Clock fixedClock() {
        return Clock.fixed(T0, ZoneOffset.UTC);
    }

    /** Eine Uhr, die {@code T0 + offset} zeigt — zum Weiterstellen in einem Test. */
    static Clock clockAt(Duration offset) {
        return Clock.fixed(T0.plus(offset), ZoneOffset.UTC);
    }

    /**
     * Eine Uhr, die sich stellen lässt, ohne neu erzeugt zu werden.
     *
     * <p>Für Prüflinge, die ihre {@link Clock} einmal im Konstruktor entgegennehmen und danach
     * festhalten — dort reicht {@link #clockAt(Duration)} nicht, weil der Prüfling die neue Uhr nie
     * zu sehen bekäme.
     */
    static final class MovableClock extends Clock {

        private Instant now = T0;

        /** Stellt die Uhr auf {@code T0 + offset}. */
        void moveTo(Duration offset) {
            this.now = T0.plus(offset);
        }

        /** Rückt die Uhr um {@code step} weiter. */
        void advance(Duration step) {
            this.now = this.now.plus(step);
        }

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
