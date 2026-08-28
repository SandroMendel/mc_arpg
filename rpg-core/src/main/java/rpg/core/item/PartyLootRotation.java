package rpg.core.item;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wer in einer Party den <b>nächsten</b> Gegenstand bekommt (FR-026a bis FR-026d).
 *
 * <p><b>Warum es das überhaupt gibt.</b> B06 behandelt eine Party als <em>einen</em> Beitragenden
 * und verteilt Erfahrung und Coins gleichmäßig auf die Mitglieder in Reichweite — ausdrücklich,
 * <em>„damit gemeinsames Spielen nicht schlechter ist als allein zu spielen"</em>. Für Beute ging
 * das nicht: {@code DamageShare.topContributor} stammt aus B05, und B05 kennt keine Partys. In einer
 * festen Gruppe wäre jeder Gegenstand dauerhaft an denselben Spieler gegangen, während sich
 * Erfahrung und Coins teilen — der Tank hätte nie etwas bekommen (ADR-039).
 *
 * <p><b>Gezählt werden die Gegenstände, nicht die Kills.</b> Beute ist wahrscheinlichkeitsbehaftet;
 * eine Runde je Kill ließe die Runde dessen verfallen, dessen Gegner nichts fallen lässt, und über
 * einen Abend gliche sich das nicht aus. Je Gegenstand gezählt ist die Verteilung exakt gleichmäßig,
 * und fallen bei einem Tod drei Gegenstände, gehen sie an drei aufeinanderfolgende Mitglieder.
 *
 * <p><b>Wer außer Reichweite steht, wird übersprungen und behält seine Position.</b> Sonst würde ein
 * Mitglied, das einmal zurückfällt, dauerhaft nach hinten rutschen.
 *
 * <p><b>Reiner Laufzeitzustand.</b> Die Party wird laut B06 nicht persistiert; ein persistierter
 * Zeiger wäre der einzige Teil von ihr, der einen Neustart überlebte. Er vergeht mit ihr.
 */
public final class PartyLootRotation {

    /** {@code partyId -> Index des Mitglieds, das als Nächstes an der Reihe ist.} */
    private final Map<UUID, Integer> cursors = new ConcurrentHashMap<>();

    /**
     * Wählt das nächste Mitglied aus und rückt den Zeiger vor.
     *
     * <p>Aufgerufen <b>je gefallenem Gegenstand</b>, nicht je Kill.
     *
     * @param partyId welche Party
     * @param inRange die Mitglieder in Reichweite, in stabiler Reihenfolge
     * @param count wie viele Einträge von {@code inRange} gelten
     * @return das gewählte Mitglied, oder {@code null}, wenn niemand in Reichweite ist — der
     *     Aufrufer fällt dann auf den größten Beitragenden zurück (FR-026c)
     */
    public UUID next(UUID partyId, UUID[] inRange, int count) {
        Objects.requireNonNull(partyId, "partyId");
        Objects.requireNonNull(inRange, "inRange");
        if (count <= 0) {
            return null;
        }
        // Der Zeiger laeuft ueber die ANWESENDEN, nicht ueber die Mitgliederliste. Wer fehlt, wird
        // damit uebersprungen, ohne dass jemand anders dauerhaft nach hinten rutscht: die Position
        // ist eine Zahl modulo der Anwesenden, kein Platz in einer festen Liste.
        int cursor = cursors.merge(partyId, 1, Integer::sum) - 1;
        return inRange[Math.floorMod(cursor, count)];
    }

    /** Vergisst eine Party — aufgerufen, wenn sie sich auflöst oder sich ihre Mitglieder ändern. */
    public void forget(UUID partyId) {
        if (partyId != null) {
            cursors.remove(partyId);
        }
    }

    /** Wie viele Partys gerade einen Zeiger haben — für Tests und Auswertung. */
    public int size() {
        return cursors.size();
    }
}
