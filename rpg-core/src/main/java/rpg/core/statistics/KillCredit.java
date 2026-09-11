package rpg.core.statistics;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import rpg.core.combat.DamageShare;

/**
 * Wem ein Kill zufällt (FR-007, ADR-042).
 *
 * <h2>Zwei Wege zur Gutschrift, und keiner davon ist der letzte Treffer</h2>
 *
 * <ol>
 *   <li><b>Beteiligung.</b> Wessen Schadensanteil die konfigurierte Schwelle erreicht, bekommt den
 *       Kill — <em>jeder</em>, der sie erreicht, nicht nur der größte Beitragende und schon gar
 *       nicht der, der zufällig zuletzt getroffen hat. Kill-Stealing ist damit keine Strategie,
 *       sondern ein Missverständnis.
 *   <li><b>Party in Reichweite.</b> Wer in der Gruppe des Beitragenden steht und nahe genug ist,
 *       bekommt ihn auch — <b>mit Anteil null</b> (FR-007a). Der Heiler, der die ganze Zeit
 *       geheilt hat, hat am Kill genauso gearbeitet wie der, der zugeschlagen hat.
 * </ol>
 *
 * <h2>Was diese Klasse NICHT tut</h2>
 *
 * <p><b>Sie rechnet nichts nach.</b> Der Schadensanteil kommt fertig aus B05s {@link DamageShare},
 * die Reichweite hat die Plattform schon angewendet, bevor sie die Mitglieder hier hereinreicht,
 * und die Schwelle steht in {@code statistics.yml}. Eine eigene Anteilsrechnung wäre die zweite
 * Wahrheit über denselben Kampf — und sie würde beim nächsten Balancing anders antworten als die
 * erste, ohne dass etwas bricht (R5).
 *
 * <p><b>Und sie kennt keine Sonderregel für Bosse</b> (FR-007e). Ein Boss ist eine Kreatur mit
 * größeren Zahlen; wer an ihm die Schwelle erreicht, hat ihn erlegt. Zehn Beteiligte ohne Party,
 * acht über der Schwelle, ergeben acht Bosskills. Eine Sonderregel wäre der naheliegende Reflex —
 * „einen Boss erlegt man nicht nebenbei" — und sie hätte die Rangliste an genau der Stelle vom
 * Rest der Statistik abgekoppelt, an der die Leute am genauesten hinsehen.
 *
 * <p><b>Die Reichweite gilt auch ohne Party</b> (ADR-042, Nachtrag): wer beiträgt, zählt, ob er
 * in einer Gruppe steht oder nicht. Die Party fügt nur die hinzu, die <em>nicht</em> beigetragen
 * haben.
 */
public final class KillCredit {

    private KillCredit() {}

    /**
     * Die Konten, denen dieser Kill gutgeschrieben wird.
     *
     * <p>Die Reihenfolge ist stabil: erst die Beitragenden, absteigend nach Anteil und bei
     * Gleichstand nach Kennung, dann die übrigen Mitglieder. Nicht, weil sie irgendwo sichtbar
     * wäre, sondern damit ein fehlgeschlagener Test zweimal dasselbe zeigt — {@link DamageShare}
     * hält seine Anteile in einer {@code Map.copyOf}, und deren Reihenfolge ist nicht die, in der
     * sie hineingelegt wurden.
     *
     * @param shares der Schadensanteil aus B05 — <b>bereits auf Spieler gefiltert</b>
     * @param threshold der Anteil, ab dem gutgeschrieben wird; erreicht zählt als erreicht
     * @param partyMembersInRange Gruppenmitglieder, auf die die Reichweite schon angewendet wurde
     */
    public static Set<UUID> recipients(
            DamageShare shares, double threshold, Collection<UUID> partyMembersInRange) {
        Set<UUID> recipients = new LinkedHashSet<>();

        // FR-008 zuerst: hat KEIN Spieler beigetragen, faellt der Kill niemandem zu - auch nicht
        // der Gruppe, die zufaellig danebenstand. Sonst zaehlte eine Kreatur, die in der Lava
        // gestorben ist, fuer jeden in Reichweite, und die Rangliste maesse Anwesenheit.
        if (shares == null || shares.isEmpty()) {
            return recipients;
        }

        shares.shares().entrySet().stream()
                // Genau AUF der Schwelle zaehlt als erreicht. Andernfalls haette der Wert 0.05 in
                // der Konfiguration die Bedeutung "mehr als 5 %", und niemand laese ihn so.
                .filter(contribution -> contribution.getValue() >= threshold)
                .sorted(
                        Map.Entry.<UUID, Double>comparingByValue()
                                .reversed()
                                .thenComparing(Map.Entry.comparingByKey()))
                .forEach(contribution -> recipients.add(contribution.getKey()));

        if (partyMembersInRange != null) {
            recipients.addAll(partyMembersInRange);
        }
        return recipients;
    }

    /**
     * Ob dieser Kill überhaupt jemandem zufällt.
     *
     * <p>Stirbt eine Kreatur ohne Zutun eines Spielers — im Feuer, im Sturz, durch eine andere
     * Kreatur —, wird <b>kein</b> Kill gezählt (FR-008). Der Unterschied ist wichtig genug für
     * einen eigenen Namen: eine leere Empfängermenge heißt hier „niemand hat ihn erlegt", nicht
     * „die Gutschrift ist verloren gegangen".
     */
    public static boolean creditedToAnyone(
            DamageShare shares, double threshold, Collection<UUID> partyMembersInRange) {
        return !recipients(shares, threshold, partyMembersInRange).isEmpty();
    }
}
