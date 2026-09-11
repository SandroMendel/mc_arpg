package rpg.plugin.command;

import java.time.Duration;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Function;

import org.bukkit.Server;
import org.bukkit.entity.Player;

import rpg.core.message.MessageKey;
import rpg.core.statistics.Period;
import rpg.core.statistics.ProfileSnapshot;
import rpg.platform.statistics.ProfileLoader;
import rpg.plugin.command.framework.Argument;
import rpg.plugin.command.framework.Arguments;
import rpg.plugin.command.framework.RpgCommand;

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
public final class StatisticsCommand {

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

    /**
     * Der Knoten für den Kommandobaum (T035).
     *
     * <p><b>{@code /stats [zeitraum|spieler] [zeitraum]}</b> — genau die Syntax von vorher (FR-005).
     * Die Mehrdeutigkeit des ersten Arguments steckt jetzt im Typ
     * {@link Arguments#periodOrPlayer} statt in einer {@code if}-Kette; die Regel ist dieselbe:
     * <em>ein Wort, das ein Zeitraum ist, ist ein Zeitraum</em> (B12-FR-044), sonst suchte
     * {@code /stats week} nach einem Spieler namens „week".
     *
     * <p><b>Die handgeschriebene {@code onTabComplete} ist weg</b> — und mit ihr zwei Mängel. Sie
     * lief über {@code Period.values()} und war damit eine <em>zweite</em> Schleife neben
     * {@code periodOf(raw)}; beide mussten übereinstimmen, ohne dass etwas sie dazu zwang (genau das
     * Paar, für das FR-002 geschrieben wurde). Und sie schlug <b>nur Zeiträume</b> vor, nie
     * Spielernamen, obwohl beide erlaubt sind: die Vervollständigung war schon vorher unvollständig,
     * es ist nur niemandem aufgefallen.
     *
     * <p><b>Sperrzeit</b> aus {@code commands.yml} (T042, FR-032) — {@code /stats} fragt die
     * Datenbank.
     */
    public RpgCommand definition(Server server, Duration rateLimit) {
        Argument<Arguments.PeriodOrPlayer> target =
                Argument.optional("target", Arguments.periodOrPlayer(server));
        Argument<Period> period = Argument.optional("period", Arguments.period());

        return RpgCommand.playerLeaf(
                        "stats",
                        MessageKey.of("command.stats.description"),
                        PERMISSION,
                        List.of(target, period),
                        context -> {
                            Player player = context.player().orElseThrow();
                            Optional<Arguments.PeriodOrPlayer> first = context.find(target);
                            Period when =
                                    first.filter(Arguments.PeriodOrPlayer::isPeriod)
                                            .map(Arguments.PeriodOrPlayer::period)
                                            .or(() -> context.find(period))
                                            .orElse(Period.ALL_TIME);

                            if (first.isPresent() && !first.get().isPeriod()) {
                                openForeign(player, first.get().player().getName(), when);
                                return;
                            }
                            openOwn(player, when);
                        })
                .throttled(rateLimit);
    }

    /** Das eigene Profil, ohne Bukkits Befehlsverpackung. */
    public void openOwn(Player player, Period period) {
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

}
