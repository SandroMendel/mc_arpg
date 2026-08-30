package rpg.core.ui;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import rpg.core.message.MessageKey;
import rpg.core.scheduler.WorldPosition;

/**
 * Eine kurzlebige Zahl am Trefferort (FR-040 bis FR-045).
 *
 * <h2>Sie ist die genaue Umkehrung von B12s Hologramm</h2>
 *
 * <p>Dieselbe Technik — ein {@code TextDisplay} —, in drei Punkten entgegengesetzt:
 *
 * <table>
 *   <caption>Hologramm gegen Schadenszahl</caption>
 *   <tr><th></th><th>B12s Hologramm</th><th>B13s Schadenszahl</th></tr>
 *   <tr><td>Persistenz</td><td><b>persistent</b> — es muss einen Neustart überleben</td>
 *       <td><b>nicht persistent</b> — es darf ihn nicht überleben</td></tr>
 *   <tr><td>Sichtbarkeit</td><td>für alle</td><td>nur für den Verursacher (FR-042)</td></tr>
 *   <tr><td>Entfernung</td><td>räumt <em>vor</em> dem Setzen auf</td>
 *       <td>plant sie <em>beim</em> Setzen ein (FR-044)</td></tr>
 * </table>
 *
 * <p><b>Und die Umkehrung ist der ganze Punkt.</b> Ein Hologramm gibt es einmal je Rangliste; eine
 * Schadenszahl entsteht bei jedem Treffer. Eine, die aufräumte wie das Hologramm, wäre bei 150
 * Spielern in Minuten tausendfacher Müll, der einen Neustart überlebt — und niemand fände ihn
 * wieder, weil er keiner Rangliste gehört.
 *
 * <p><em>Was der Server nicht speichert, kann ein Absturz nicht zurücklassen.</em> Das ist der ganze
 * Inhalt von SC-009, und deshalb ist {@code setPersistent(false)} hier keine Einstellung, sondern
 * die Zusage.
 *
 * <h2>Eine Zahl je Treffer, nicht je Schlag</h2>
 *
 * <p>B05 bündelt bereits (FR-041). {@link #hitCount} ist die Zahl der gebündelten Schläge und steht
 * im Text, damit ein Spieler eine {@code 240} von vier Treffern nicht für einen einzelnen hält.
 *
 * @param viewerId wer sie sieht — <b>nur</b> der Verursacher
 * @param position wo sie steht, inklusive Versatz
 * @param amount der gebündelte Schaden
 * @param hitCount wie viele Schläge darin stecken
 * @param lethal ob dieser Treffer getötet hat — bekommt einen eigenen Schlüssel
 * @param expiresAt wann sie verschwindet
 */
public record DamageNumber(
        UUID viewerId,
        WorldPosition position,
        double amount,
        int hitCount,
        boolean lethal,
        Instant expiresAt) {

    public DamageNumber {
        Objects.requireNonNull(viewerId, "viewerId");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(expiresAt, "expiresAt");
        if (!Double.isFinite(amount)) {
            throw new IllegalArgumentException("amount ist " + amount + " - erlaubt ist endlich");
        }
        if (hitCount < 1) {
            throw new IllegalArgumentException(
                    "hitCount ist " + hitCount + " - eine Zahl ohne Treffer gibt es nicht");
        }
    }

    /**
     * Wie lange sie noch steht.
     *
     * <p>Nie negativ: eine abgelaufene Anzeige hat null Rest und keine Schuld.
     */
    public Duration remaining(Instant now) {
        Duration left = Duration.between(Objects.requireNonNull(now, "now"), expiresAt);
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** Ob sie abgelaufen ist. */
    public boolean isExpired(Instant now) {
        return !Objects.requireNonNull(now, "now").isBefore(expiresAt);
    }

    /**
     * Der Schlüssel für ihren Text.
     *
     * <p>Ein eigener für den tödlichen Treffer, damit der Betreiber ihn hervorheben kann — der
     * letzte Treffer ist die einzige Zahl, auf die ein Spieler wirklich wartet.
     */
    public MessageKey key() {
        return lethal ? UiMessageKeys.DAMAGE_NUMBER_LETHAL : UiMessageKeys.DAMAGE_NUMBER;
    }

    /**
     * Die Platzhalter für den Text.
     *
     * <p>Keine Nachkommastellen: {@code 148.7} liest sich als {@code 149}, und der Bruch ist auf
     * dieser Skala Rauschen — dieselbe Entscheidung wie auf der Actionbar und in der Übersicht.
     */
    public Map<String, String> values() {
        return Map.of(
                "amount", Long.toString(Math.round(amount)),
                "hits", Integer.toString(hitCount));
    }
}
