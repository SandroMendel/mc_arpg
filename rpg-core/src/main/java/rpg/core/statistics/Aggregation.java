package rpg.core.statistics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Eine <b>Rangliste</b> — das, was ein Spieler sieht, und das, was eine Gewichtung tragen kann.
 *
 * <h2>Warum es diesen Typ neben {@link Metric} gibt</h2>
 *
 * <p>Eine Metrik ist, was <em>gespeichert</em> wird; eine Aggregation ist, was <em>gefragt</em>
 * wird. Die beiden fallen nicht zusammen, und zwar an drei Stellen:
 *
 * <ul>
 *   <li><b>Bosskills sind keine Metrik.</b> Nach FR-009 bekommen sie bewusst keinen eigenen
 *       Zähler — ein zweiter Zähler über dasselbe Ereignis wäre eine zweite Wahrheit. Sie sind
 *       eine Summe über die Arten <em>mit</em> Boss-Kennzeichen, während {@link #MOB_KILLS} über
 *       die <em>ohne</em> summiert (FR-009a). Beide zusammen ergeben alle Kills, und keine Kreatur
 *       zählt in beiden.
 *   <li><b>Eine Familie hat keine Gesamtzeile.</b> „Alle Tode", „aktive Gesamtzeit" entstehen
 *       durch Summieren über {@code deaths.*} beziehungsweise {@code playtime_active.*}, nicht
 *       durch einen zusätzlichen Schreibvorgang (FR-013).
 *   <li><b>Die Sichtbarkeit sitzt verschieden.</b> {@link MetricRegistry#DEATHS} und
 *       {@link MetricRegistry#PLAYTIME_ACTIVE} sind <b>privat</b> — aber privat ist die
 *       <em>Aufschlüsselung</em>, nicht die Summe (FR-036, FR-037, ADR-043). Die Gesamtzahl der
 *       Tode steht in jedem fremden Profil, und die aktive Gesamtzeit trägt sogar eine öffentliche
 *       Rangliste (FR-038a). Deshalb trägt eine Aggregation ihre eigene Sichtbarkeit; sie von der
 *       Metrik zu erben wäre schlicht falsch.
 * </ul>
 *
 * <h2>Die beiden Zustandsranglisten stehen hier mit drin — und tragen nie eine Gewichtung</h2>
 *
 * <p>{@link #LEVEL} und {@link #COINS} kommen nicht aus der Statistiktabelle, sondern aus dem
 * Fortschritts- und dem Kontobestand (ADR-041). Sie stehen trotzdem hier, weil FR-030 für
 * <em>alle</em> Ranglisten gilt: das Öffnen kostet keine Abfrage, und dafür müssen sie in
 * denselben Speicherstand. Eine Ausnahme davon hätte niemand im Fenster erkannt.
 *
 * <p><b>Der Riegel aus ADR-046 sitzt deshalb in {@link #scoreable()} und nicht in ihrer
 * Abwesenheit.</b> Das war der erste Entwurf — Zustandswerte gar nicht erst als Aggregation zu
 * führen —, und er war der elegantere: was es nicht gibt, kann man nicht gewichten. Er hielt der
 * Anforderung nicht stand, dass auch diese beiden Listen ohne Abfrage zu öffnen sein müssen. Also
 * steht die Regel jetzt ausdrücklich da, statt sich aus einer Lücke zu ergeben: eine Saison
 * belohnt, was <em>in ihr</em> geleistet wurde, und Level und Coins tragen den Fortschritt
 * vergangener Saisons in die laufende.
 */
public enum Aggregation {

    /**
     * Kills an Arten <b>ohne</b> Boss-Kennzeichen (FR-009a).
     *
     * <p>Der Schlüssel ist derselbe wie der der Metrik: die gewöhnliche Kill-Rangliste ist der
     * Normalfall, und ein eigener Name dafür wäre nur eine zweite Vokabel für dieselbe Sache.
     */
    MOB_KILLS(MetricRegistry.MOB_KILLS, BossScope.WITHOUT_BOSSES, MetricVisibility.PUBLIC),

    /**
     * Kills an Arten <b>mit</b> Boss-Kennzeichen (FR-009, FR-009a).
     *
     * <p>Der einzige Eintrag mit einem eigenen Schlüssel — er benennt keine gespeicherte Metrik,
     * sondern die zweite Hälfte derselben.
     */
    BOSS_KILLS("boss_kills", MetricRegistry.MOB_KILLS, BossScope.ONLY_BOSSES, MetricVisibility.PUBLIC),

    /** Alle Tode, über jeden Verursacher summiert — die <b>Summe</b> ist öffentlich (FR-037). */
    DEATHS(MetricRegistry.DEATHS, BossScope.IRRELEVANT, MetricVisibility.PUBLIC),

    /** Aktive Spielzeit über alle Zonen — die öffentliche Spielzeit-Rangliste (FR-038a). */
    PLAYTIME_ACTIVE(MetricRegistry.PLAYTIME_ACTIVE, BossScope.IRRELEVANT, MetricVisibility.PUBLIC),

    /** Der höchste Einzeltreffer (FR-016). */
    DAMAGE_MAX(MetricRegistry.DAMAGE_MAX, BossScope.IRRELEVANT, MetricVisibility.PUBLIC),

    /**
     * Die reine Onlinezeit.
     *
     * <p><b>Privat und damit nicht rankbar</b> (FR-036, ADR-043) — sie steht hier, weil das eigene
     * Profil sie zeigt, nicht weil es eine Rangliste dafür gäbe.
     */
    PLAYTIME_ONLINE(MetricRegistry.PLAYTIME_ONLINE, BossScope.IRRELEVANT, MetricVisibility.PRIVATE),

    /**
     * Das Level eines Kontos — der höchste seiner Charaktere (FR-021).
     *
     * <p>Gleichstand entscheidet die XP <b>innerhalb</b> dieses Levels (FR-020). Gelesen aus
     * {@code character_progress}, nie geschrieben (ADR-041).
     */
    LEVEL(MetricRegistry.LEVEL, BossScope.IRRELEVANT, MetricVisibility.PUBLIC),

    /**
     * Die Coins eines Kontos — die <b>Summe</b> über seine Charaktere (FR-022).
     *
     * <p>Nicht das Maximum: Geld ist teilbar und liegt verteilt. Gelesen aus
     * {@code character_balance}, nie geschrieben.
     */
    COINS(MetricRegistry.COINS, BossScope.IRRELEVANT, MetricVisibility.PUBLIC);

    /** Ob eine Aggregation Boss-Arten ein-, aus- oder nicht unterscheidet. */
    public enum BossScope {

        /** Nur Arten ohne Boss-Kennzeichen. */
        WITHOUT_BOSSES,

        /** Nur Arten mit Boss-Kennzeichen. */
        ONLY_BOSSES,

        /** Die Unterscheidung spielt für diese Aggregation keine Rolle. */
        IRRELEVANT
    }

    private static final Map<String, Aggregation> BY_KEY = index();

    private final String key;
    private final Metric source;
    private final BossScope bossScope;
    private final MetricVisibility visibility;

    Aggregation(Metric source, BossScope bossScope, MetricVisibility visibility) {
        this(source.key(), source, bossScope, visibility);
    }

    Aggregation(String key, Metric source, BossScope bossScope, MetricVisibility visibility) {
        this.key = key;
        this.source = source;
        this.bossScope = bossScope;
        this.visibility = visibility;
    }

    private static Map<String, Aggregation> index() {
        Map<String, Aggregation> byKey = new LinkedHashMap<>();
        for (Aggregation aggregation : values()) {
            byKey.put(aggregation.key, aggregation);
        }
        return Map.copyOf(byKey);
    }

    /** Der Schlüssel, unter dem diese Rangliste in {@code statistics.yml} angesprochen wird. */
    public String key() {
        return key;
    }

    /** Die Metrikfamilie, über die summiert wird. */
    public Metric source() {
        return source;
    }

    /** Wie Boss-Arten behandelt werden. */
    public BossScope bossScope() {
        return bossScope;
    }

    /** Ob diese Rangliste öffentlich ist. */
    public MetricVisibility visibility() {
        return visibility;
    }

    /**
     * Ob auf diese Aggregation eine Gewichtung gelegt werden darf (ADR-046, FR-050c).
     *
     * <p>Zwei Bedingungen, und beide haben ihren eigenen Grund:
     *
     * <ul>
     *   <li><b>Öffentlich</b> — eine Platzierung, die sich aus Zahlen begründet, die niemand
     *       nachsehen kann, wird als Willkür gelesen.
     *   <li><b>Kein Zustandswert</b> — Level und Coins tragen den Fortschritt vergangener Saisons
     *       in die laufende und setzten alte Konten dauerhaft nach oben. Eine Saison hörte damit
     *       auf, ein Neuanfang zu sein.
     * </ul>
     */
    public boolean scoreable() {
        return visibility == MetricVisibility.PUBLIC && source.kind() != MetricKind.STATE;
    }

    /** Ob diese Rangliste aus den Zustandsbeständen kommt statt aus der Statistiktabelle. */
    public boolean isState() {
        return source.kind() == MetricKind.STATE;
    }

    /**
     * Wie viele <b>Punkteinheiten</b> ein Rohwert dieser Rangliste ergibt.
     *
     * <p>Für fast alle ist eine Einheit ein Rohwert: ein Kill ist ein Kill. Die Spielzeit ist die
     * Ausnahme — sie wird in <em>Sekunden</em> gespeichert, und {@code statistics.yml} vergibt
     * ihre Punkte ausdrücklich <b>je angefangener Stunde</b>. Ohne diese Umrechnung wäre ein
     * Gewicht von 2 in Wahrheit „zwei Punkte je Sekunde" — bei einer einzigen Spielstunde 7200
     * Punkte statt 2, und die Saisonwertung wäre eine reine Anwesenheitsliste.
     *
     * <p><b>Angefangen, nicht abgerundet:</b> wer 61 Minuten spielt, bekommt zwei Einheiten. Das
     * ist die Lesart, die der Kommentar in der Konfiguration zusagt, und sie ist die freundlichere
     * — abrunden hieße, die erste Dreiviertelstunde einer Sitzung zählt gar nicht.
     */
    public long scoreUnits(long rawValue) {
        if (rawValue <= 0) {
            return 0;
        }
        return this == PLAYTIME_ACTIVE || this == PLAYTIME_ONLINE
                ? (rawValue + SECONDS_PER_HOUR - 1) / SECONDS_PER_HOUR
                : rawValue;
    }

    private static final long SECONDS_PER_HOUR = 3_600L;

    /** Die Aggregation zu einem Konfigurationsschlüssel. */
    public static Optional<Aggregation> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /** Alle Aggregationen unter ihrem Schlüssel, in Deklarationsreihenfolge. */
    public static Map<String, Aggregation> all() {
        return BY_KEY;
    }
}
