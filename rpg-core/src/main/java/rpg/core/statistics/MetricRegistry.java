package rpg.core.statistics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Das Metrikverzeichnis — <b>die eine Stelle, an der eine Metrik existiert</b> (FR-018).
 *
 * <p>Jeder Metrikschlüssel dieses Blocks steht hier und sonst nirgends. Das ist keine
 * Ordnungsliebe: die {@link MetricKind Art} entscheidet, ob ein Wert addiert oder maximiert wird,
 * und die {@link MetricVisibility Sichtbarkeit}, ob er ein fremdes Profil erreichen darf. Beides
 * hängt am Eintrag. Ein Schlüssel als Zeichenkette an der Aufrufstelle umgeht beide Eigenschaften
 * und sieht dabei völlig harmlos aus — {@code NoMetricLiteralsTest} verhindert deshalb mechanisch,
 * was ein Hinweis im Review nicht verhindert.
 *
 * <p><b>Was hier nicht steht:</b> Gesamtzahlen. „Alle Kills", „alle Tode", „Bosskills",
 * „aktive Gesamtzeit" sind Aggregationen über eine Familie und bekommen keinen eigenen Eintrag
 * (FR-013, FR-009) — ein zweiter Zähler über dasselbe Ereignis wäre eine zweite Wahrheit.
 */
public final class MetricRegistry {

    // --- Die dimensionierten Familien -------------------------------------------------------

    /**
     * Kills je Mob-Art (FR-006). Dimension ist der Artenschlüssel aus {@code mobs.yml}.
     *
     * <p>Bosskills liegen <b>hier mit drin</b> und bekommen keinen eigenen Schlüssel (FR-009). Die
     * Trennung der beiden Ranglisten passiert erst in der Aggregation: Mob-Kills summieren über
     * die Arten ohne Boss-Kennzeichen, Bosskills über die mit (FR-009a). Verliert eine Art ihr
     * Kennzeichen, wandern ihre Kills rückwirkend hinüber — dieselbe Zusage, die B11 fürs
     * Balancing gibt, und derselbe Preis.
     */
    public static final Metric MOB_KILLS =
            new Metric("mob_kills", MetricKind.SUM, MetricVisibility.PUBLIC, true);

    /**
     * Tode je Verursacher (FR-010). Dimension ist eine Mob-Art oder einer der drei festen
     * Ersatzschlüssel unten (FR-011).
     *
     * <p><b>Privat</b> — woran jemand immer wieder stirbt, ist eine Schwäche. Die daraus gebildete
     * Gesamtzahl ist dagegen öffentlich (FR-037): privat ist die Aufschlüsselung, nicht die Summe.
     */
    public static final Metric DEATHS =
            new Metric("deaths", MetricKind.SUM, MetricVisibility.PRIVATE, true);

    /**
     * Aktive Spielzeit je Zone in Sekunden (FR-014d). Dimension ist der Zonenschlüssel aus
     * {@code zones.yml} oder {@link #ZONE_WILDERNESS}.
     *
     * <p><b>Privat</b>, weil die Aufteilung verrät, wo sich jemand aufhält. Ihre Summe trägt die
     * öffentliche Spielzeit-Rangliste (FR-038a).
     */
    public static final Metric PLAYTIME_ACTIVE =
            new Metric("playtime_active", MetricKind.SUM, MetricVisibility.PRIVATE, true);

    // --- Die undimensionierten ---------------------------------------------------------------

    /**
     * Onlinezeit in Sekunden — die zweite Uhr (FR-014a, ADR-043).
     *
     * <p><b>Privat und nicht rankbar</b>: sie misst, wann und wie lange jemand am Rechner sitzt,
     * nicht, was er im Spiel erreicht hat. Sie ist nie kleiner als die aktive Zeit.
     */
    public static final Metric PLAYTIME_ONLINE =
            new Metric("playtime_online", MetricKind.SUM, MetricVisibility.PRIVATE, false);

    /**
     * Der höchste Einzeltreffer (FR-016) — die einzige {@link MetricKind#MAX}-Metrik.
     *
     * <p>Ganzzahlig und <b>abgerundet</b> abgelegt: 1249,7 wird zu 1249, nicht zu 1250, damit ein
     * aufgerundeter Wert keinen echten Treffer derselben Höhe einholt.
     */
    public static final Metric DAMAGE_MAX =
            new Metric("damage_max", MetricKind.MAX, MetricVisibility.PUBLIC, false);

    // --- Zustände: gelesen, nie geschrieben (ADR-041) ----------------------------------------

    /** Level des Kontos — der höchste Wert seiner Charaktere (FR-021). Aus B06 gelesen. */
    public static final Metric LEVEL =
            new Metric("level", MetricKind.STATE, MetricVisibility.PUBLIC, false);

    /** XP innerhalb des Levels — der Gleichstandsentscheid der Level-Rangliste (FR-020). */
    public static final Metric XP =
            new Metric("xp", MetricKind.STATE, MetricVisibility.PUBLIC, false);

    /** Coins des Kontos. Aus B08b gelesen. */
    public static final Metric COINS =
            new Metric("coins", MetricKind.STATE, MetricVisibility.PUBLIC, false);

    // --- Die festen Dimensionen ---------------------------------------------------------------

    /** Tod ohne Mob-Verursacher: Sturz, Feuer, Ertrinken (FR-011). */
    public static final String DEATH_ENVIRONMENT = "environment";

    /** Tod durch die Leere (FR-011). */
    public static final String DEATH_VOID = "void";

    /** Tod durch einen anderen Spieler (FR-011). */
    public static final String DEATH_PLAYER = "player";

    /** Aufenthalt außerhalb jeder Zone (FR-014e). */
    public static final String ZONE_WILDERNESS = "wilderness";

    private static final Map<String, Metric> BY_KEY = index();

    private MetricRegistry() {}

    private static Map<String, Metric> index() {
        Map<String, Metric> byKey = new LinkedHashMap<>();
        for (Metric metric :
                new Metric[] {
                    MOB_KILLS, DEATHS, PLAYTIME_ACTIVE, PLAYTIME_ONLINE, DAMAGE_MAX, LEVEL, XP, COINS
                }) {
            byKey.put(metric.key(), metric);
        }
        return Map.copyOf(byKey);
    }

    /**
     * Der Eintrag zu einem <b>gespeicherten</b> Schlüssel — mit Dimension oder ohne.
     *
     * <p>{@code mob_kills.dune-warlord} und {@code mob_kills} führen beide auf denselben Eintrag:
     * die Dimension ist ein Teil des Schlüssels, aber keine eigene Metrik.
     */
    public static Optional<Metric> byKey(String storedKey) {
        return storedKey == null
                ? Optional.empty()
                : Optional.ofNullable(BY_KEY.get(MetricKeys.familyOf(storedKey)));
    }

    /** Alle Einträge, in der Reihenfolge, in der sie oben stehen. */
    public static Map<String, Metric> all() {
        return BY_KEY;
    }
}
