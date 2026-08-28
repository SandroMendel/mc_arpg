package rpg.core.mob;

import java.time.Duration;
import java.util.Objects;

/**
 * Zieldichte aus Spielerzahl, gekappt am Budget - ohne Bukkit, testbar (FR-025, FR-026, FR-027,
 * FR-028).
 *
 * <p><b>Zieldichte und Budget sind zwei verschiedene Rollen.</b> Diese Klasse rechnet aus, wie voll
 * eine Zone bei so vielen Spielern <em>sein sollte</em> - {@link Budget#ceilingFor} sagt weiterhin,
 * wie voll sie <em>hoechstens sein darf</em>. Das Ergebnis ist immer {@code min(zieldichte,
 * budget)} (data-model.md): die Zieldichte kappt niemals das Budget, sie kappt hoechstens sich
 * selbst daran.
 *
 * <p><b>Linear ab dem ersten Spieler, nicht ab null.</b> {@link Budget#perPlayer()} ist die Dichte
 * fuer genau einen Spieler; jeder weitere legt {@code densityPerPlayer} obendrauf (research.md R7:
 * zu fuenft ungefaehr doppelt so voll wie allein, bei den Ausgangswerten). Absichtlich unabhaengig
 * von {@code playersInZone * perPlayer} - dieser Ausdruck ist bereits die harte Grenze in {@link
 * Budget#allows} und {@link Budget#ceilingFor}, eine zweite, sanftere Kurve braucht eine eigene
 * Rechnung, sonst waere sie nur ein Alias fuer das Budget und liesse sich nie kleiner beobachten.
 *
 * <p><b>Kein Weg zurueck, kein Loeschen.</b> Sinkt die Spielerzahl, faellt die Zieldichte mit ihr
 * (FR-028) - aber diese Klasse entfernt nichts. Sie liefert nur eine kleinere Zahl, gegen die ein
 * Aufrufer seine naechste Spawn-Entscheidung misst; wer schon ueber der neuen Zieldichte steht,
 * bleibt stehen, bis {@code CleanupRule} ihn aus einem anderen Grund holt (Reichweite oder
 * Zonenaufgabe). Der Ueberhang wird abgebaut, nicht geloescht.
 *
 * <p><b>Nie eine {@link MobKind} in Sicht</b> (FR-026). Attribute, Level und Staerke einer Art
 * stehen ausschliesslich in {@code mobs.yml} und werden ueber B04 gesetzt - diese Klasse kennt
 * nur Kopfzahlen und Zeitspannen, nichts davon.
 */
public final class DensityScaling {

    private DensityScaling() {}

    /**
     * Wie viele Kreaturen bei so vielen Spielern in dieser Zone stehen sollten, gedeckelt am Budget
     * (FR-025, FR-027).
     *
     * @param budget die vier Grenzen - hier nur fuer {@link Budget#perPlayer()} und {@link
     *     Budget#ceilingFor}
     * @param densityPerPlayer Dichtezuwachs je zusaetzlichem Spieler, als Anteil (FR-025, FR-026)
     * @param playersInZone wie viele Spieler in der Zone sind - null oder weniger heisst: nichts
     * @param serverTotalElsewhere wie viele Kreaturen dieser Block ausserhalb dieser Zone haelt
     * @return die gedeckelte Zieldichte
     */
    public static int targetDensity(
            Budget budget, double densityPerPlayer, int playersInZone, int serverTotalElsewhere) {
        Objects.requireNonNull(budget, "budget");
        if (playersInZone <= 0) {
            return 0;
        }
        double scaled = budget.perPlayer() * (1.0 + densityPerPlayer * (playersInZone - 1));
        int ceiling = budget.ceilingFor(playersInZone, serverTotalElsewhere);
        return Math.min((int) Math.round(scaled), ceiling);
    }

    /**
     * Die skalierte Nachschubrate: bei mehr Spielern wird derselbe Abstand kuerzer, nie laenger
     * (FR-025).
     *
     * @param baseInterval {@code horde.respawn-interval-ms}, der Abstand fuer einen Spieler
     * @param densityPerPlayer Dichtezuwachs je zusaetzlichem Spieler, als Anteil - dieselbe Zahl wie
     *     in {@link #targetDensity}, damit beide Kurven zusammengehoeren
     * @param playersInZone wie viele Spieler in der Zone sind
     * @return der skalierte Abstand - nie unter einer Millisekunde
     */
    public static Duration respawnInterval(
            Duration baseInterval, double densityPerPlayer, int playersInZone) {
        Objects.requireNonNull(baseInterval, "baseInterval");
        if (playersInZone <= 1) {
            return baseInterval;
        }
        double factor = 1.0 + densityPerPlayer * (playersInZone - 1);
        long scaledMillis = Math.max(1L, Math.round(baseInterval.toMillis() / factor));
        return Duration.ofMillis(scaledMillis);
    }
}
