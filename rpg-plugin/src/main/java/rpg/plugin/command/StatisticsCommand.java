package rpg.plugin.command;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import rpg.core.statistics.Period;
import rpg.core.statistics.ProfileSnapshot;
import rpg.platform.statistics.ProfileLoader;

/**
 * {@code /stats} — das eigene Profil (FR-041, FR-046).
 *
 * <p><b>Vorläufig, und dazu gedacht, ersetzt zu werden</b> (ADR-028), wie {@code /coins},
 * {@code /xp} und {@code /top}.
 *
 * <h2>Laden, dann öffnen — und die Reihenfolge ist der ganze Inhalt</h2>
 *
 * <p>Die Werte kommen aus der Datenbank und damit asynchron. Dieser Command wartet <b>nicht</b>
 * auf sie: er stößt das Laden an und öffnet das Fenster, wenn es fertig ist — auf dem Tick, weil
 * ein Inventar nur dort geöffnet werden darf.
 *
 * <p>Der naheliegende Fehler wäre {@code join()} im Tick. Es funktioniert auf einer leeren
 * Testdatenbank tadellos und hält bei fünfzig Spielern den Server an — und zwar an genau der
 * Stelle, an der eine Statistik keinen Schaden anrichten dürfte.
 */
public final class StatisticsCommand implements CommandExecutor, TabCompleter {

    public static final String PERMISSION = "rpg.statistics.own";

    private final ProfileLoader loader;

    /** Öffnet das fertige Fenster — auf dem Tick, vom Aufrufer bereitgestellt. */
    private final BiConsumer<Player, ProfileSnapshot> open;

    /** Name → Konto; leer für einen Namen, den es nie gab. */
    private final Function<String, Optional<UUID>> accountOf;

    /** Öffnet ein fremdes Profil — dasselbe Fenster, ein anderer Titel. */
    private final BiConsumer<Player, ProfileSnapshot> openForeignProfile;

    /** Sagt dem Betrachter, dass es diesen Spieler nicht gibt. */
    private final BiConsumer<Player, String> unknownPlayer;

    public StatisticsCommand(
            ProfileLoader loader,
            BiConsumer<Player, ProfileSnapshot> open,
            Function<String, Optional<UUID>> accountOf,
            BiConsumer<Player, ProfileSnapshot> openForeignProfile,
            BiConsumer<Player, String> unknownPlayer) {
        this.loader = Objects.requireNonNull(loader, "loader");
        this.open = Objects.requireNonNull(open, "open");
        this.accountOf = Objects.requireNonNull(accountOf, "accountOf");
        this.openForeignProfile = Objects.requireNonNull(openForeignProfile, "openForeignProfile");
        this.unknownPlayer = Objects.requireNonNull(unknownPlayer, "unknownPlayer");
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command opens a window and needs a player.");
            return true;
        }
        if (!player.hasPermission(PERMISSION)) {
            return true;
        }

        // Ein Argument, das kein Zeitraum ist, ist ein Spielername (FR-044). Die Reihenfolge ist
        // Absicht: /stats week soll den eigenen Zeitraum meinen und nicht nach einem Spieler
        // namens "week" suchen.
        if (args.length > 0 && periodOf(args[0]).isEmpty()) {
            openForeign(player, args[0], args.length > 1 ? periodOf(args[1]).orElse(Period.ALL_TIME) : Period.ALL_TIME);
            return true;
        }

        Period period = args.length > 0 ? periodOf(args[0]).orElse(Period.ALL_TIME) : Period.ALL_TIME;

        loader.own(player.getUniqueId(), period)
                .thenAccept(profile -> open.accept(player, profile))
                .exceptionally(
                        failure -> {
                            // Eine gescheiterte Abfrage darf den Spieler nicht ratlos lassen. Sie
                            // ist selten und sie ist protokolliert; hier zaehlt nur, dass etwas
                            // passiert, wenn er /stats tippt.
                            player.sendMessage("Your record could not be loaded. Try again shortly.");
                            return null;
                        });
        return true;
    }

    /**
     * Das Profil eines anderen (FR-044).
     *
     * <p><b>Kostet keine Abfrage</b>: es kommt vollständig aus dem Ranglisten-Speicherstand. Eine
     * Abfrage gäbe es hier auch gar nicht — die Aufschlüsselung, aus der die Kill-Aufteilung
     * entsteht, verlässt das Modul nur für den Betrachter selbst (FR-037).
     *
     * <p>Ein Name, den es nie gab, bekommt eine <b>Meldung</b> und kein leeres Fenster. Ein leeres
     * Fenster ist die schlechtere Antwort: es sieht aus wie „dieser Spieler hat nichts getan" und
     * beantwortet damit eine Frage, die niemand gestellt hat.
     */
    private void openForeign(Player viewer, String name, Period period) {
        Optional<UUID> account = accountOf.apply(name);
        if (account.isEmpty()) {
            unknownPlayer.accept(viewer, name);
            return;
        }
        openForeignProfile.accept(viewer, loader.foreign(account.get(), period));
    }

    @Override
    public List<String> onTabComplete(
            CommandSender sender, Command command, String label, String[] args) {
        List<String> options = new ArrayList<>();
        if (args.length == 1) {
            for (Period period : Period.values()) {
                String name = period.name().toLowerCase(Locale.ROOT).replace('_', '-');
                if (name.startsWith(args[0].toLowerCase(Locale.ROOT))) {
                    options.add(name);
                }
            }
        }
        return options;
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
