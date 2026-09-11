package rpg.persistence.statistics;

import java.time.Clock;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.logging.Logger;

import rpg.core.statistics.LeaderboardCache;
import rpg.core.statistics.StatisticsConfig;
import rpg.persistence.PersistenceModule;

/**
 * Die Datenbankseite von B12s Ranglisten — <b>und die Grenze, hinter der die {@code DataSource}
 * bleibt.</b>
 *
 * <h2>Warum es diese Klasse gibt, und warum erst jetzt</h2>
 *
 * <p>In Phase 2 wurde sie bewusst nicht gebaut: es gab keinen einzigen eigenen Bestand zu
 * verdrahten, und eine leere Klasse, die vorgibt etwas zu tun, ist schlechter als keine. Mit den
 * Sichten, dem Auffrischer und den beiden Quellen gibt es jetzt drei.
 *
 * <p><b>Den Ausschlag gab aber ein Test.</b> {@code NoDirectDatabaseAccessTest} hält seit B02
 * fest, dass {@code java.sql} und {@code DataSource} nur in diesem Modul vorkommen — sonst wäre
 * der getrennte Anmeldepool wirkungslos, weil jeder Block sich seine eigenen Verbindungen nehmen
 * könnte. Der erste Entwurf der Verdrahtung holte sich den Pool in {@code RpgPlugin} und reichte
 * ihn an drei Konstruktoren weiter; der Wächter hat das gefunden, bevor es jemand gewohnheitsmäßig
 * nachgemacht hätte.
 *
 * <p>Hier bleibt der Pool also, und nach außen geht ein fertiger {@link LeaderboardFill}.
 */
public final class StatisticsPersistenceModule {

    private final LeaderboardFill fill;
    private final SeasonClosingJob closing;

    /**
     * @param isBossKind ob ein Artenschlüssel ein Boss ist — B10s Verzeichnis, nicht ein eigenes
     * @param nameOf Konto → Anzeigename, aufgelöst beim Füllen und außerhalb des Ticks (FR-040)
     */
    public StatisticsPersistenceModule(
            PersistenceModule persistence,
            LeaderboardCache cache,
            Function<String, Boolean> isBossKind,
            Supplier<StatisticsConfig> config,
            Function<UUID, String> nameOf,
            Logger logger,
            Clock clock) {
        Objects.requireNonNull(persistence, "persistence");

        javax.sql.DataSource pool = persistence.pools().loginPool();

        // EINE Quelle fuer beide: der Zwischenstand und der Endstand rechnen aus denselben
        // Rohdaten. Zwei Quellen waeren zwei Wege, dieselbe Zahl zu bilden - und der Endstand
        // duerfte den Spielern niemals etwas anderes sagen als der Stand, den sie die ganze
        // Saison ueber gesehen haben.
        JdbcLeaderboardSource counters = new JdbcLeaderboardSource(pool, isBossKind);

        this.fill =
                new LeaderboardFill(
                        cache,
                        new LeaderboardRefresh(pool, logger),
                        counters,
                        new JdbcStateLeaderboardSource(pool),
                        config,
                        nameOf,
                        logger,
                        clock);

        this.closing =
                new SeasonClosingJob(
                        counters,
                        new JdbcSeasonResultRepository(pool),
                        new JdbcRewardClaimRepository(pool),
                        config,
                        logger,
                        clock);
    }

    /** Die Auffrischung, fertig verdrahtet. */
    public LeaderboardFill fill() {
        return fill;
    }

    /**
     * Der Saisonabschluss, fertig verdrahtet.
     *
     * <p>Er gehört an denselben Takt wie die Auffrischung <b>und</b> an den Start (FR-058) — der
     * Start, weil ein Quartalsende selten in eine Laufzeit fällt, der Takt, weil ein Server, der
     * durchläuft, sonst nie abschlösse.
     */
    public SeasonClosingJob closing() {
        return closing;
    }

    /**
     * Die rohe Leseseite fürs eigene Profil.
     *
     * <p>Als eigene Fabrikmethode und nicht über den Konstruktor oben: das Profil braucht keine
     * Ranglisten, keine Auffrischung und keine Konfiguration — nur den Pool. Sie hier
     * herauszureichen ist der einzige Weg, der die {@code DataSource} im Modul lässt.
     */
    public static rpg.core.statistics.RawStatisticsView rawView(
            PersistenceModule persistence, rpg.core.scheduler.Scheduler scheduler) {
        Objects.requireNonNull(persistence, "persistence");
        return new JdbcStatisticsView(persistence.pools().loginPool(), scheduler);
    }
}
