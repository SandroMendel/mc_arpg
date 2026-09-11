package rpg.platform.statistics;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import rpg.core.message.Messages;
import rpg.core.statistics.Aggregation;
import rpg.core.statistics.Leaderboard;
import rpg.core.statistics.LeaderboardEntry;
import rpg.core.statistics.Leaderboards;
import rpg.core.statistics.Period;
import rpg.core.statistics.SeasonScore;
import rpg.core.statistics.SeasonScoreBoard;
import rpg.core.statistics.StatisticsMessageKeys;
import rpg.platform.item.ItemText;

/**
 * Das Ranglisten-Fenster (FR-043) — <b>umschaltbar nach Rangliste und Zeitraum.</b>
 *
 * <h2>Was hier nicht passiert: fragen</h2>
 *
 * <p>Der Inhalt kommt aus {@link Leaderboards} und damit aus dem Speicher. Dieses Fenster löst
 * keine Datenbankabfrage aus — weder beim Öffnen noch beim Umschalten (FR-030, SC-003). Beim
 * Umschalten ist die Versuchung größer als beim Öffnen: es sieht nach „nur dieser einen anderen
 * Liste" aus, und fünfzig Spieler, die durch vier Zeiträume blättern, wären zweihundert Abfragen.
 *
 * <h2>Die Beschriftung ist Teil der Zusage (FR-007c)</h2>
 *
 * <p>Die Kill-Ranglisten heißen <b>„Beteiligung an Kills"</b> und nicht „erledigte Kreaturen". Das
 * ist keine Wortklauberei, sondern der angenommene Preis von ADR-042: weil ein Kill <em>jedem</em>
 * Beteiligten zählt, übersteigt die Summe aller Zähler die Zahl tatsächlich gestorbener Kreaturen.
 * Ein Spieler, der das nicht liest, rechnet nach, findet die Differenz und meldet die Rangliste als
 * kaputt — zu Recht, wenn dort „erledigt" stünde.
 *
 * <h2>Und das Alter steht dabei (FR-032)</h2>
 *
 * <p>Die Zeile, die den Unterschied zwischen „veraltet" und „kaputt" erklärt. Ohne sie ist jede
 * Rangliste, die einen gerade erzielten Wert noch nicht zeigt, aus Sicht des Spielers ein Fehler.
 */
public final class LeaderboardMenu {

    /** Sechs Reihen: zehn Plätze, eine Kopfzeile, eine Umschaltleiste, die eigene Zeile. */
    private static final int SIZE = 54;

    private static final int FIRST_ENTRY_SLOT = 9;

    /** Die Zeile ganz unten links: die eigene Platzierung, auch außerhalb der ersten N (FR-033). */
    public static final int SLOT_OWN_RANK = 45;

    /** Die Kopfzeile mit Rangliste, Zeitraum und Alter. */
    public static final int SLOT_HEADER = 4;

    private final Leaderboards leaderboards;
    private final Messages messages;

    public LeaderboardMenu(Leaderboards leaderboards, Messages messages) {
        this.leaderboards = Objects.requireNonNull(leaderboards, "leaderboards");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Baut das Fenster für eine Rangliste und einen Zeitraum.
     *
     * @param viewer wessen eigene Platzierung unten steht
     */
    public Inventory build(Aggregation board, Period period, UUID viewer, Instant now) {
        if (board.visibility() != rpg.core.statistics.MetricVisibility.PUBLIC) {
            // Kein leeres Fenster und keine Meldung, sondern ein Abbruch: eine private Rangliste
            // kann hier nur durch einen Programmierfehler ankommen - jeder Aufrufer filtert sie
            // vorher weg (FR-036). Ein leeres Fenster haette den Fehler zugedeckt UND haette in
            // seiner Kopfzeile trotzdem den Namen der privaten Rangliste getragen.
            throw new IllegalArgumentException(
                    "leaderboard window for a private board: " + board.key() + " (FR-036)");
        }

        Optional<Leaderboard> standing = leaderboards.board(board, period);

        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        SIZE,
                        ItemText.of(
                                messages,
                                StatisticsMessageKeys.LEADERBOARD_TITLE,
                                Map.of(
                                        "board", boardLabel(board),
                                        "period", periodLabel(period))));

        inventory.setItem(SLOT_HEADER, header(board, period, now));

        if (standing.isEmpty()) {
            // FR-035: noch nie aufgefrischt. Eine Meldung, keine leere Liste - und ausdruecklich
            // keine Ersatzabfrage.
            inventory.setItem(
                    FIRST_ENTRY_SLOT,
                    note(Material.CLOCK, StatisticsMessageKeys.LEADERBOARD_NOT_READY));
            return inventory;
        }

        Leaderboard list = standing.get();
        if (list.isEmpty()) {
            inventory.setItem(
                    FIRST_ENTRY_SLOT, note(Material.PAPER, StatisticsMessageKeys.LEADERBOARD_EMPTY));
        }

        int slot = FIRST_ENTRY_SLOT;
        for (LeaderboardEntry entry : list.top()) {
            inventory.setItem(slot++, row(entry, entry.playerId().equals(viewer)));
        }

        inventory.setItem(SLOT_OWN_RANK, ownRank(list, viewer));
        return inventory;
    }

    /**
     * Das Fenster der Saison-Gesamtwertung — <b>der Zwischenstand, nicht erst der Endstand</b>
     * (FR-050e).
     *
     * <p>Eigenes Fenster und kein Zeitraum der Ranglisten: die Gesamtwertung ist keine
     * {@link Aggregation}, ihre Einheit sind Punkte, und ihr Titel nennt die Saison statt einer
     * Rangliste. Sie in die Umschaltleiste zu hängen hätte bedeutet, sie so zu beschriften, als
     * wäre sie eine Metrik unter anderen.
     *
     * <p>Unten steht <b>die eigene Rechnung</b>, nicht nur der eigene Platz: ADR-046 verlangt die
     * Aufschlüsselung ausdrücklich, weil eine Wertung, deren Zustandekommen man nicht sieht, als
     * Willkür gelesen wird — bei einer Belohnung lauter als anderswo. Sie liegt fertig im
     * Speicherstand; das Öffnen kostet auch hier keine Abfrage.
     */
    public Inventory buildSeasonScore(UUID viewer, Instant now) {
        Optional<SeasonScoreBoard> standing = leaderboards.seasonScore();

        Inventory inventory =
                Bukkit.createInventory(
                        null,
                        SIZE,
                        ItemText.of(
                                messages,
                                StatisticsMessageKeys.SEASON_SCORE_TITLE,
                                Map.of(
                                        "season",
                                        standing.map(SeasonScoreBoard::seasonKey).orElse("-"))));

        if (standing.isEmpty()) {
            // Zwei Gruende, ein Bild: es laeuft keine Saison, oder es wurde noch nie
            // aufgefrischt. Der erste ist der haeufigere und der einzige, den ein Spieler
            // ueberhaupt bemerken wuerde - deshalb steht er da.
            inventory.setItem(SLOT_HEADER, note(Material.CLOCK, StatisticsMessageKeys.SEASON_NONE));
            return inventory;
        }

        SeasonScoreBoard board = standing.get();
        inventory.setItem(SLOT_HEADER, seasonHeader(board, now));

        if (board.isEmpty()) {
            inventory.setItem(
                    FIRST_ENTRY_SLOT, note(Material.PAPER, StatisticsMessageKeys.LEADERBOARD_EMPTY));
        }

        int slot = FIRST_ENTRY_SLOT;
        for (LeaderboardEntry entry : board.top()) {
            inventory.setItem(slot++, row(entry, entry.playerId().equals(viewer)));
        }

        inventory.setItem(SLOT_OWN_RANK, ownScore(board, viewer));
        return inventory;
    }

    /** Die Kopfzeile der Gesamtwertung: welche Saison — und wie alt der Stand ist. */
    private ItemStack seasonHeader(SeasonScoreBoard board, Instant now) {
        ItemStack head = new ItemStack(Material.BOOK);
        ItemMeta meta = head.getItemMeta();
        meta.displayName(
                ItemText.onItem(
                        ItemText.of(
                                messages,
                                StatisticsMessageKeys.SEASON_SCORE_TITLE,
                                Map.of("season", board.seasonKey()))));
        meta.lore(
                List.of(
                        ItemText.onItem(
                                ItemText.of(
                                        messages,
                                        StatisticsMessageKeys.LEADERBOARD_AS_OF,
                                        Map.of("age", age(board.refreshedAt(), now))))));
        head.setItemMeta(meta);
        return head;
    }

    /**
     * Die eigene Punktzahl — und darunter, Zeile für Zeile, wie sie zustande kommt.
     *
     * <p><b>In der Zeile steht die Einheit, die multipliziert wird</b>, nicht der Rohwert: bei der
     * Spielzeit sind das angefangene Stunden. Stünde dort die Sekundenzahl, ginge die angezeigte
     * Rechnung nicht auf — und eine Rechnung, die nicht aufgeht, erklärt weniger als gar keine.
     */
    private ItemStack ownScore(SeasonScoreBoard board, UUID viewer) {
        ItemStack own = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = own.getItemMeta();

        Optional<SeasonScore> mine = board.scoreFor(viewer);
        if (mine.isEmpty()) {
            meta.displayName(
                    ItemText.onItem(
                            ItemText.of(
                                    messages, StatisticsMessageKeys.LEADERBOARD_UNRANKED, Map.of())));
            own.setItemMeta(meta);
            return own;
        }

        SeasonScore score = mine.get();
        meta.displayName(
                ItemText.onItem(
                        ItemText.of(
                                messages,
                                StatisticsMessageKeys.SEASON_SCORE_TOTAL,
                                Map.of(
                                        "points", String.valueOf(score.total()),
                                        "rank", String.valueOf(board.rankFor(viewer).orElse(0))))));

        List<Component> lore = new ArrayList<>();
        for (SeasonScore.Part part : score.parts()) {
            lore.add(
                    ItemText.onItem(
                            ItemText.of(
                                    messages,
                                    StatisticsMessageKeys.SEASON_SCORE_LINE,
                                    Map.of(
                                            "label", boardLabel(part.board()),
                                            "value", String.valueOf(part.units()),
                                            "weight", weightLabel(part.weight()),
                                            "points", String.valueOf(part.points())))));
        }
        meta.lore(lore);
        own.setItemMeta(meta);
        return own;
    }

    /** {@code 2} statt {@code 2.0} — ganze Gewichte lesen sich als ganze Zahlen besser. */
    static String weightLabel(double weight) {
        return weight == Math.rint(weight) ? String.valueOf((long) weight) : String.valueOf(weight);
    }

    /** Die Kopfzeile: was gezeigt wird, über welchen Zeitraum — und wie alt es ist. */
    private ItemStack header(Aggregation board, Period period, Instant now) {
        ItemStack head = new ItemStack(Material.BOOK);
        ItemMeta meta = head.getItemMeta();
        meta.displayName(
                ItemText.onItem(
                        ItemText.of(
                                messages,
                                StatisticsMessageKeys.LEADERBOARD_TITLE,
                                Map.of(
                                        "board", boardLabel(board),
                                        "period", periodLabel(period)))));

        List<Component> lore = new ArrayList<>();
        leaderboards
                .refreshedAt()
                .ifPresent(
                        at ->
                                lore.add(
                                        ItemText.onItem(
                                                ItemText.of(
                                                        messages,
                                                        StatisticsMessageKeys.LEADERBOARD_AS_OF,
                                                        Map.of("age", age(at, now))))));
        meta.lore(lore);
        head.setItemMeta(meta);
        return head;
    }

    private ItemStack row(LeaderboardEntry entry, boolean self) {
        ItemStack row = new ItemStack(self ? Material.GOLDEN_APPLE : Material.PLAYER_HEAD);
        ItemMeta meta = row.getItemMeta();
        meta.displayName(
                ItemText.onItem(
                        ItemText.of(
                                messages,
                                self
                                        ? StatisticsMessageKeys.LEADERBOARD_ENTRY_SELF
                                        : StatisticsMessageKeys.LEADERBOARD_ENTRY,
                                Map.of(
                                        "rank", String.valueOf(entry.rank()),
                                        "player", entry.displayName(),
                                        "value", String.valueOf(entry.value())))));
        row.setItemMeta(meta);
        return row;
    }

    /**
     * Die eigene Platzierung.
     *
     * <p>Steht auch dann da, wenn der Betrachter weit außerhalb der ersten zehn liegt (FR-033) —
     * die vollständige Rangzuordnung liegt im Speicherstand, es kostet also keine Abfrage. Wer
     * überhaupt keinen Wert hat, liest, dass er noch nicht dabei ist; eine leere Zeile sähe wie
     * ein Fehler aus.
     */
    private ItemStack ownRank(Leaderboard list, UUID viewer) {
        OptionalInt rank = list.rankFor(viewer);
        ItemStack own = new ItemStack(Material.NAME_TAG);
        ItemMeta meta = own.getItemMeta();

        if (rank.isEmpty()) {
            meta.displayName(
                    ItemText.onItem(
                            ItemText.of(
                                    messages, StatisticsMessageKeys.LEADERBOARD_UNRANKED, Map.of())));
        } else {
            long value =
                    list.entryFor(viewer).map(LeaderboardEntry::value).orElse(0L);
            meta.displayName(
                    ItemText.onItem(
                            ItemText.of(
                                    messages,
                                    StatisticsMessageKeys.LEADERBOARD_YOUR_RANK,
                                    Map.of(
                                            "rank", String.valueOf(rank.getAsInt()),
                                            "value", String.valueOf(value)))));
        }
        own.setItemMeta(meta);
        return own;
    }

    private ItemStack note(Material material, rpg.core.message.MessageKey key) {
        ItemStack note = new ItemStack(material);
        ItemMeta meta = note.getItemMeta();
        meta.displayName(ItemText.onItem(ItemText.of(messages, key, Map.of())));
        note.setItemMeta(meta);
        return note;
    }

    /** Der Anzeigename einer Rangliste — aus {@code messages.yml}, nie aus dem Code. */
    public String boardLabel(Aggregation board) {
        return messages.get(StatisticsMessageKeys.boardName(board));
    }

    /** Der Anzeigename eines Zeitraums. */
    public String periodLabel(Period period) {
        return messages.get(StatisticsMessageKeys.periodName(period));
    }

    /** „vor 3 Minuten", grob — es geht um die Größenordnung, nicht um die Sekunde. */
    static String age(Instant refreshedAt, Instant now) {
        Duration since = Duration.between(refreshedAt, now);
        if (since.isNegative()) {
            return "0s";
        }
        long minutes = since.toMinutes();
        return minutes < 1 ? since.toSeconds() + "s" : minutes + "m";
    }
}
