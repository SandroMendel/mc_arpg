package rpg.plugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Period;
import rpg.platform.statistics.StatisticsMenuListener;

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
public final class TopCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "rpg.statistics.top";

    /** Das erste Argument, das keine Rangliste meint, sondern die Saison-Gesamtwertung. */
    public static final String SEASON_SCORE = "score";

    private final StatisticsMenuListener menus;

    public TopCommand(StatisticsMenuListener menus) {
        this.menus = Objects.requireNonNull(menus, "menus");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            // Ein Fenster braucht jemanden, dem man es zeigen kann. Die Konsole bekommt eine
            // Antwort statt einer Ausnahme.
            sender.sendMessage("This command opens a window and needs a player.");
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            return true;
        }

        // "/top score" meint keine Rangliste, sondern die Saison-Gesamtwertung: ein eigenes
        // Fenster mit einer eigenen Einheit (Punkte). Sie unter die Ranglisten zu mischen hiesse,
        // sie zu beschriften, als waere sie eine Metrik unter anderen (FR-050e).
        if (args.length > 0 && args[0].equalsIgnoreCase(SEASON_SCORE)) {
            menus.openSeasonScore(player);
            return true;
        }

        Aggregation board =
                args.length > 0
                        ? boardOf(args[0]).orElse(Aggregation.MOB_KILLS)
                        : Aggregation.MOB_KILLS;
        Period period = args.length > 1 ? periodOf(args[1]).orElse(Period.ALL_TIME) : Period.ALL_TIME;

        menus.openLeaderboard(player, board, period);
        return true;
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            // Nur die oeffentlichen: eine private Rangliste vorzuschlagen hiesse, sie anzubieten
            // und dann abzulehnen (FR-036).
            Aggregation.all().values().stream()
                    .filter(board -> board.visibility() == rpg.core.statistics.MetricVisibility.PUBLIC)
                    .map(Aggregation::key)
                    .filter(key -> key.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .forEach(options::add);
            if (SEASON_SCORE.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                options.add(SEASON_SCORE);
            }
        } else if (args.length == 2) {
            for (Period period : Period.values()) {
                String name = period.name().toLowerCase(Locale.ROOT).replace('_', '-');
                if (name.startsWith(args[1].toLowerCase(Locale.ROOT))) {
                    options.add(name);
                }
            }
        }
        return options;
    }

    private static Optional<Aggregation> boardOf(String raw) {
        return Aggregation.byKey(raw.toLowerCase(Locale.ROOT))
                .filter(board -> board.visibility() == rpg.core.statistics.MetricVisibility.PUBLIC);
    }

    private static Optional<Period> periodOf(String raw) {
        String wanted = raw.toUpperCase(Locale.ROOT).replace('-', '_');
        for (Period period : Period.values()) {
            if (period.name().equals(wanted)) {
                return Optional.of(period);
            }
        }
        return Optional.empty();
    }
}
