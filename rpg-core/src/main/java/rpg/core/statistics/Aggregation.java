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
 * <h2>Was hier fehlt, und warum</h2>
 *
 * <p>Für Level, XP und Coins gibt es <b>keine</b> Aggregation. Das ist der eigentliche Riegel aus
 * ADR-046: eine Gewichtung kann nur auf eine Aggregation gelegt werden, also können Zustandswerte
 * gar nicht erst in die Saisonwertung geraten — sie trügen den Fortschritt vergangener Saisons in
 * die laufende, und eine Saison hörte auf, ein Neuanfang zu sein. Die beiden Zustandsranglisten
 * (Level, Coins) entstehen an anderer Stelle, aus dem Fortschritts- und dem Kontobestand.
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
    PLAYTIME_ONLINE(MetricRegistry.PLAYTIME_ONLINE, BossScope.IRRELEVANT, MetricVisibility.PRIVATE);

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
     * <p>Öffentlich muss sie sein — eine Platzierung, die sich aus Zahlen begründet, die niemand
     * nachsehen kann, wird als Willkür gelesen.
     */
    public boolean scoreable() {
        return visibility == MetricVisibility.PUBLIC;
    }

    /** Die Aggregation zu einem Konfigurationsschlüssel. */
    public static Optional<Aggregation> byKey(String key) {
        return Optional.ofNullable(BY_KEY.get(key));
    }

    /** Alle Aggregationen unter ihrem Schlüssel, in Deklarationsreihenfolge. */
    public static Map<String, Aggregation> all() {
        return BY_KEY;
    }
}
