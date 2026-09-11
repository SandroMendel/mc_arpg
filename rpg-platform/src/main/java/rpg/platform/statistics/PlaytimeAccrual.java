package rpg.platform.statistics;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import rpg.core.statistics.ActivityClock;
import rpg.core.statistics.MetricRegistry;
import rpg.core.statistics.Playtime;
import rpg.core.statistics.Statistics;

/**
 * Schreibt beide Uhren und die Zonenaufteilung fort — <b>auf einem Durchlauf, den es schon
 * gibt</b> (R6, FR-015).
 *
 * <h2>Warum keine eigene Aufgabe</h2>
 *
 * <p>{@code RpgPlugin.startInventorySweep} läuft bereits im Autosave-Takt und geht über
 * {@code inventoryModule.playersInPlay()} — <b>nicht</b> über die Online-Liste des Servers: wer
 * noch in der Charakterauswahl sitzt, hat nichts festzuschreiben. Das ist exakt die Liste und exakt
 * der Takt, den FR-015 verlangt.
 *
 * <p>Eine eigene wiederkehrende Aufgabe je Spieler wäre die naheliegende Umsetzung und würde gegen
 * Prinzip II verstoßen: fünfzig Spieler wären fünfzig Aufgaben, die dasselbe tun wie ein
 * vorhandener Durchlauf, nur zu einem anderen Zeitpunkt. B11 hat {@code ConsumableBuffs.expire()}
 * aus demselben Grund auf den vorhandenen Fähigkeiten-Sweep gesetzt.
 *
 * <p>Ein Absturz kostet damit höchstens ein Autosave-Intervall — dieselbe Zusage, die Prinzip IV
 * ohnehin gibt.
 *
 * <h2>Die Reihenfolge in {@link #accrue} ist nicht beliebig</h2>
 *
 * <p>Erst wird der Untätigkeitswechsel geschlossen, dann der Rest verbucht. Andersherum liefe die
 * Zeit bis zum Sweep noch als „aktiv" durch, obwohl der Spieler längst untätig war — die aktive
 * Uhr wäre systematisch um bis zu ein Sweep-Intervall zu großzügig, und zwar nur für den, der
 * wirklich stillsteht.
 */
public final class PlaytimeAccrual {

    private final Statistics statistics;
    private final Playtime playtime;
    private final ActivityClock activity;
    private final Supplier<Duration> idleAfter;
    private final Clock clock;

    public PlaytimeAccrual(
            Statistics statistics,
            Playtime playtime,
            ActivityClock activity,
            Supplier<Duration> idleAfter,
            Clock clock) {
        this.statistics = Objects.requireNonNull(statistics, "statistics");
        this.playtime = Objects.requireNonNull(playtime, "playtime");
        this.activity = Objects.requireNonNull(activity, "activity");
        this.idleAfter = Objects.requireNonNull(idleAfter, "idleAfter");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /** Beginnt die Zeitrechnung — beim Betreten der Welt mit einem Charakter. */
    public void begin(UUID account, String zoneKey) {
        Instant now = clock.instant();
        activity.touch(account, now);
        playtime.begin(account, now, zoneKey);
    }

    /** Ein Durchlauf des vorhandenen Sweeps, für einen Spieler. */
    public void accrue(UUID account) {
        Instant now = clock.instant();
        Duration threshold = idleAfter.get();
        boolean idle = activity.isIdle(account, now, threshold);

        // Der Wechsel wird auf den Zeitpunkt datiert, an dem er STATTFAND - nicht auf den, an dem
        // der Sweep ihn bemerkt. Beides zu verwechseln ist der naheliegende Fehler und macht die
        // aktive Uhr systematisch zu grosszuegig: wer um 10:05 aufhoert und um 10:10 gefegt wird,
        // haette fuenf Minuten Untaetigkeit als Spielzeit gutgeschrieben bekommen. Und zwar
        // ausgerechnet der, der stillsteht.
        book(account, playtime.activityChanged(account, idle ? idleSince(account, now, threshold) : now, !idle));
        book(account, playtime.accrue(account, now));
    }

    /**
     * Wann die Untätigkeit begann: die letzte Tat plus die Schwelle.
     *
     * <p>Fehlt der Zeitstempel — der Spieler hat seit dem Anmelden nichts getan —, zählt der
     * Zeitpunkt des Sweeps. Ein früherer wäre geraten.
     */
    private Instant idleSince(UUID account, Instant now, Duration threshold) {
        Instant last = activity.lastActivityOf(account);
        if (last == null) {
            return now;
        }
        Instant began = last.plus(threshold);
        return began.isAfter(now) ? now : began;
    }

    /** Der Spieler hat die Zone gewechselt (FR-014f). */
    public void zoneChanged(UUID account, String newZoneKey) {
        book(account, playtime.zoneChanged(account, clock.instant(), newZoneKey));
    }

    /** Sitzungsende: der letzte Abschnitt wird geschlossen und der Zeitstempel vergessen. */
    public void end(UUID account) {
        book(account, playtime.end(account, clock.instant()));
        activity.forget(account);
    }

    /**
     * Verbucht geschlossene Abschnitte.
     *
     * <p><b>Jeder Abschnitt geht auf die Onlineuhr, nur der aktive zusätzlich auf die Zonenuhr.</b>
     * Damit ist die aktive Gesamtzeit die Summe der Zonenzeiten — es gibt gar keine zweite
     * Rechnung, die davon abweichen könnte (FR-014d, SC-016).
     */
    private void book(UUID account, List<Playtime.Slice> slices) {
        for (Playtime.Slice slice : slices) {
            statistics.count(account, MetricRegistry.PLAYTIME_ONLINE, slice.seconds());
            if (slice.active()) {
                statistics.count(
                        account, MetricRegistry.PLAYTIME_ACTIVE, slice.zoneKey(), slice.seconds());
            }
        }
    }
}
