package rpg.core.statistics;

import java.util.UUID;

/**
 * Testdaten für B12 — und vor allem: <b>Halter und Charakter tragen hier nie dieselbe Kennung.</b>
 *
 * <h2>Warum das eine eigene Klasse rechtfertigt</h2>
 *
 * <p>Dieses Projekt kennt zwei Kennungen, die beide {@code UUID} heißen und beide einen Spieler
 * meinen — die <b>Halterkennung</b> (das Konto, in B04 die Spieler-UUID) und die
 * <b>Charakterkennung</b> (die gerade gespielte Figur). Welche wohin gehört, entscheidet nicht der
 * Typ, sondern die Absicht des Aufrufers: {@code StatEngine.holderOf} nimmt eine
 * <em>Charakter</em>kennung entgegen und antwortet mit einer <em>Halter</em>kennung. Wer sie
 * vertauscht, bekommt keinen Übersetzungsfehler, sondern ein leeres {@code Optional} — und ein
 * leeres {@code Optional} sieht aus wie „nicht im Spiel".
 *
 * <p>Genau das ist zweimal passiert. B08 gab die Charakterkennung dort hinein, wo eine
 * Halterkennung erwartet wurde: jeder Aufruf warf, jeder Wurf wurde von einem Zuhörer sauber
 * gefangen, und das Ergebnis war ein Server, auf dem keine Fähigkeit mehr wirkte. B11 trat in
 * dieselbe Grube. Beide Male ist es <b>keinem Test aufgefallen</b>, weil die Testdaten für Halter
 * und Charakter dieselbe UUID benutzten — 1614 Tests lang war die Verwechslung systematisch
 * unsichtbar.
 *
 * <p>Deshalb erzeugt diese Fixture beide <b>grundsätzlich getrennt</b>. Ein Test, der sie
 * vertauscht, fällt auf; ein Test, der sich seine UUIDs selbst besorgt, verzichtet auf diesen
 * Schutz — und sollte einen Grund dafür nennen.
 *
 * @see rpg.core.stats.StatEngine#holderOf(UUID)
 */
final class StatisticsFixtures {

    private StatisticsFixtures() {}

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
}
