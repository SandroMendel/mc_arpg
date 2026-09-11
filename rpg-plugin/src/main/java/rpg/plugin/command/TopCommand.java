package rpg.plugin.command;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import org.bukkit.entity.Player;

import rpg.core.message.MessageKey;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Period;
import rpg.platform.statistics.StatisticsMenuListener;
import rpg.plugin.command.framework.Argument;
import rpg.plugin.command.framework.Arguments;
import rpg.plugin.command.framework.RpgCommand;

/**
 * {@code /top} — die Rangliste öffnen (FR-046).
 *
 * <p><b>Vorläufig, und dazu gedacht, ersetzt zu werden</b> (ADR-028), genau wie {@code /coins} und
 * {@code /xp}: Commands, der Berechtigungsbaum und die Vervollständigung gehören B14. Dies
 * existiert, weil eine Rangliste ohne einen Weg, sie zu öffnen, vorhanden und unbenutzbar ist —
 * und B14 mehrere Blöcke entfernt liegt.
 *
 * <p><b>Der Maßstab dieser Klasse ist, wie wenig sie enthält.</b> Sie liest zwei Argumente,
 * prüft eine Berechtigung und öffnet ein Fenster. Was eine Rangliste ist, in welcher Reihenfolge
 * sie steht und ob sie überhaupt schon einen Stand hat, entscheidet {@code rpg-core} — und wird
 * dort ohne Server geprüft.
 *
 * <p>Die Berechtigung steht in {@code plugin.yml} auf {@code default: true}: eine Rangliste ist
 * für alle da, und ein Recht, das erst vergeben werden muss, wäre eine stumme Funktion auf jedem
 * frisch aufgesetzten Server (Muster {@code rpg.currency.balance}).
 */
public final class TopCommand {

    public static final String PERMISSION = "rpg.statistics.top";

    /**
     * Das erste Argument, das keine Rangliste meint, sondern die Saison-Gesamtwertung.
     *
     * <p>Wohnt seit dem Umzug (T036) in {@link Arguments#SEASON_SCORE} - dort, wo geprueft und
     * vorgeschlagen wird. Zwei Konstanten desselben Wortes waeren zwei Wahrheiten; hier steht nur
     * noch der Verweis, damit wer sie hier sucht, sie findet.
     */
    public static final String SEASON_SCORE = Arguments.SEASON_SCORE;

    private final StatisticsMenuListener menus;

    public TopCommand(StatisticsMenuListener menus) {
        this.menus = Objects.requireNonNull(menus, "menus");
    }

    /**
     * Der Knoten für den Kommandobaum (T036).
     *
     * <p><b>{@code /top [tafel|score] [zeitraum]}</b> — dieselbe Syntax wie vorher (FR-005), und
     * {@code score} bleibt daneben statt darin: es ist ein eigenes Fenster mit einer eigenen
     * Einheit, keine Metrik unter anderen (B12-FR-050e).
     *
     * <p><b>Eine bewusste Verhaltensänderung.</b> Bisher fiel {@code boardOf} sowohl für einen
     * <em>unbekannten</em> als auch für einen <em>privaten</em> Schlüssel still auf
     * {@code MOB_KILLS} zurück. Wer {@code /top playtme_active} vertippte oder
     * {@code /top playtime_online} versuchte, bekam die Kill-Tafel und erfuhr nie warum — er hätte
     * ihre Zahlen für die angefragten gehalten. Jetzt wird der Schlüssel genannt und abgelehnt
     * (FR-004). Der <em>argumentlose</em> Aufruf öffnet weiterhin {@code MOB_KILLS}: das ist eine
     * Voreinstellung und keine stille Ersetzung.
     *
     * <p><b>Sperrzeit</b> aus {@code commands.yml} (T042) — {@code /top} fragt die Datenbank.
     */
    public RpgCommand definition(Duration rateLimit) {
        Argument<Arguments.BoardChoice> which =
                Argument.optional("board", Arguments.leaderboard());
        Argument<Period> when = Argument.optional("period", Arguments.period());

        return RpgCommand.playerLeaf(
                        "top",
                        MessageKey.of("command.top.description"),
                        PERMISSION,
                        List.of(which, when),
                        context -> {
                            Player player = context.player().orElseThrow();
                            Period period = context.find(when).orElse(Period.ALL_TIME);
                            Arguments.BoardChoice choice =
                                    context.find(which)
                                            .orElse(
                                                    new Arguments.BoardChoice(
                                                            Aggregation.MOB_KILLS, false));
                            if (choice.seasonScore()) {
                                menus.openSeasonScore(player);
                                return;
                            }
                            menus.openLeaderboard(player, choice.board(), period);
                        })
                .throttled(rateLimit);
    }
}
