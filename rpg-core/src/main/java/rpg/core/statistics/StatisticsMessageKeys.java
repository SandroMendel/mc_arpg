package rpg.core.statistics;

import java.util.ArrayList;
import java.util.List;

import rpg.core.message.MessageKey;

/**
 * Die Spielertexte dieses Blocks (R9, FR-047).
 *
 * <p><b>Kein Anzeigename steht im Code.</b> Wie eine Rangliste heißt, wie ein Zeitraum heißt, wie
 * eine Zeile aufgebaut ist — alles lebt in {@code messages.yml} (Prinzip V, Prinzip VIII: die
 * ausgelieferten Texte sind englisch). Der Code kennt nur Schlüssel, und die bildet er aus dem
 * {@link Aggregation}-Verzeichnis statt aus Zeichenketten.
 *
 * <p><b>{@link #all()} ist der Grund, aus dem es diese Klasse gibt.</b> Die Startprüfung im
 * Plugin-Modul geht die Liste durch und verweigert den Start, wenn ein Text fehlt. Ohne sie fiele
 * ein vergessener Schlüssel erst dem Spieler auf, der das Fenster öffnet — und zwar als roher
 * Schlüsselname mitten in der Oberfläche. Dieselbe Bauart wie {@code MobMessageKeys} und
 * {@code ItemMessageKeys}.
 */
public final class StatisticsMessageKeys {

    private StatisticsMessageKeys() {}

    // --- Das eigene und das fremde Profil -----------------------------------

    /** Titel des eigenen Profils. */
    public static final MessageKey PROFILE_TITLE = MessageKey.of("statistics.profile.title");

    /** Titel eines fremden Profils. Platzhalter: {@code player}. */
    public static final MessageKey PROFILE_TITLE_OTHER =
            MessageKey.of("statistics.profile.title-other");

    /** Eine Zeile im Profil. Platzhalter: {@code label}, {@code value}. */
    public static final MessageKey PROFILE_LINE = MessageKey.of("statistics.profile.line");

    /**
     * Der Hinweis, dass drei Werte nur im eigenen Profil stehen (FR-037, FR-038).
     *
     * <p>Er erscheint im <b>fremden</b> Profil an der Stelle, an der die Aufschlüsselung fehlt —
     * damit die Lücke als Absicht lesbar ist und nicht als fehlender Wert.
     */
    public static final MessageKey PROFILE_PRIVATE_OMITTED =
            MessageKey.of("statistics.profile.private-omitted");

    /** Ein Konto, das es nicht gibt. Platzhalter: {@code player}. */
    public static final MessageKey UNKNOWN_PLAYER = MessageKey.of("statistics.unknown-player");

    // --- Ranglisten ---------------------------------------------------------

    /** Titel einer Rangliste. Platzhalter: {@code board}, {@code period}. */
    public static final MessageKey LEADERBOARD_TITLE = MessageKey.of("statistics.leaderboard.title");

    /** Eine Zeile. Platzhalter: {@code rank}, {@code player}, {@code value}. */
    public static final MessageKey LEADERBOARD_ENTRY = MessageKey.of("statistics.leaderboard.entry");

    /** Die eigene Zeile, hervorgehoben. Platzhalter wie {@link #LEADERBOARD_ENTRY}. */
    public static final MessageKey LEADERBOARD_ENTRY_SELF =
            MessageKey.of("statistics.leaderboard.entry-self");

    /** Der eigene Rang unterhalb der Liste. Platzhalter: {@code rank}, {@code value}. */
    public static final MessageKey LEADERBOARD_YOUR_RANK =
            MessageKey.of("statistics.leaderboard.your-rank");

    /** Wer noch keinen Wert hat, steht nirgends — und soll wissen, dass das kein Fehler ist. */
    public static final MessageKey LEADERBOARD_UNRANKED =
            MessageKey.of("statistics.leaderboard.unranked");

    /** Eine Rangliste ohne einen einzigen Eintrag. */
    public static final MessageKey LEADERBOARD_EMPTY = MessageKey.of("statistics.leaderboard.empty");

    /**
     * Der Stand ist noch nicht geladen.
     *
     * <p>Kein Fehler: die Sichten werden periodisch aufgefrischt (FR-029), und direkt nach dem
     * Start ist der Speicher-Cache noch leer. Ein Fenster, das dann nichts sagt, sieht kaputt aus.
     */
    public static final MessageKey LEADERBOARD_NOT_READY =
            MessageKey.of("statistics.leaderboard.not-ready");

    /** Wann zuletzt aufgefrischt wurde. Platzhalter: {@code age}. */
    public static final MessageKey LEADERBOARD_AS_OF = MessageKey.of("statistics.leaderboard.as-of");

    // --- Saison -------------------------------------------------------------

    /** Die laufende Saison. Platzhalter: {@code season}, {@code days}. */
    public static final MessageKey SEASON_CURRENT = MessageKey.of("statistics.season.current");

    /** Zwischen zwei Saisons. */
    public static final MessageKey SEASON_NONE = MessageKey.of("statistics.season.none");

    /** Kopfzeile der Gesamtwertung. Platzhalter: {@code season}. */
    public static final MessageKey SEASON_SCORE_TITLE =
            MessageKey.of("statistics.season.score.title");

    /**
     * Eine Zeile der Aufschlüsselung. Platzhalter: {@code label}, {@code value}, {@code weight},
     * {@code points}.
     *
     * <p><b>ADR-046 verlangt sie ausdrücklich:</b> eine Wertung, deren Zustandekommen man nicht
     * sieht, wird als Willkür gelesen — bei einer Belohnung lauter als anderswo.
     */
    public static final MessageKey SEASON_SCORE_LINE = MessageKey.of("statistics.season.score.line");

    /** Die Summe. Platzhalter: {@code points}, {@code rank}. */
    public static final MessageKey SEASON_SCORE_TOTAL =
            MessageKey.of("statistics.season.score.total");

    /** Der eingefrorene Endstand einer abgeschlossenen Saison. Platzhalter: {@code season}. */
    public static final MessageKey SEASON_FINAL = MessageKey.of("statistics.season.final");

    // --- Belohnungen --------------------------------------------------------

    /** Ein offener Anspruch. Platzhalter: {@code season}, {@code rank}. */
    public static final MessageKey REWARD_AVAILABLE = MessageKey.of("statistics.reward.available");

    /** Erfolgreich eingelöst. Platzhalter: {@code season}, {@code reward}. */
    public static final MessageKey REWARD_CLAIMED = MessageKey.of("statistics.reward.claimed");

    /**
     * Bereits eingelöst.
     *
     * <p>Der Riegel gegen doppelte Einlösung ist ein bedingtes Update (R2); dieser Text ist, was
     * der zweite Versuch zu sehen bekommt.
     */
    public static final MessageKey REWARD_ALREADY_CLAIMED =
            MessageKey.of("statistics.reward.already-claimed");

    /** Kein Anspruch offen. */
    public static final MessageKey REWARD_NONE = MessageKey.of("statistics.reward.none");

    /**
     * Das Inventar ist voll.
     *
     * <p>Der Anspruch <b>verfällt dabei nicht</b> (FR-053a, ADR-045) — der Text sagt das, weil
     * sonst jeder Betroffene annimmt, seine Belohnung sei weg.
     */
    public static final MessageKey REWARD_INVENTORY_FULL =
            MessageKey.of("statistics.reward.inventory-full");

    // --- Hologramm ----------------------------------------------------------

    /** Kopfzeile des Hologramms. Platzhalter: {@code board}, {@code period}. */
    public static final MessageKey HOLOGRAM_HEADER = MessageKey.of("statistics.hologram.header");

    /** Eine Zeile. Platzhalter: {@code rank}, {@code player}, {@code value}. */
    public static final MessageKey HOLOGRAM_LINE = MessageKey.of("statistics.hologram.line");

    // --- Aus dem Verzeichnis gebildet ---------------------------------------

    /**
     * Der Anzeigename einer Rangliste, {@code statistics.board.<key>.name}.
     *
     * <p><b>Der Unterstrich wird zum Bindestrich.</b> {@code MessageKey} lässt nur
     * kleingeschriebene, mit Bindestrich getrennte Segmente zu — {@code mob_kills} wäre kein
     * gültiger Schlüssel. Die Umschreibung steht hier an einer Stelle statt in jedem Aufrufer; sie
     * ist eindeutig, weil ein Aggregationsschlüssel nie einen Bindestrich trägt. Derselbe Stolper
     * hat in B11 die Attributnamen erwischt.
     */
    public static MessageKey boardName(Aggregation aggregation) {
        return MessageKey.of("statistics.board." + aggregation.key().replace('_', '-') + ".name");
    }

    /** Der Anzeigename eines Zeitraums, {@code statistics.period.<name>}. */
    public static MessageKey periodName(Period period) {
        return MessageKey.of("statistics.period." + period.name().toLowerCase().replace('_', '-'));
    }

    /**
     * Jeder Schlüssel, den dieser Block ausgeben kann — für die Auflösungsprüfung beim Start.
     *
     * <p>Die Namen der Ranglisten und Zeiträume entstehen aus ihren Verzeichnissen, nicht aus einer
     * zweiten, von Hand gepflegten Liste. Kommt eine Rangliste dazu, wächst die Prüfung mit; eine
     * Liste hier würde beim übernächsten Mal vergessen.
     */
    public static List<MessageKey> all() {
        List<MessageKey> keys =
                new ArrayList<>(
                        List.of(
                                PROFILE_TITLE,
                                PROFILE_TITLE_OTHER,
                                PROFILE_LINE,
                                PROFILE_PRIVATE_OMITTED,
                                UNKNOWN_PLAYER,
                                LEADERBOARD_TITLE,
                                LEADERBOARD_ENTRY,
                                LEADERBOARD_ENTRY_SELF,
                                LEADERBOARD_YOUR_RANK,
                                LEADERBOARD_UNRANKED,
                                LEADERBOARD_EMPTY,
                                LEADERBOARD_NOT_READY,
                                LEADERBOARD_AS_OF,
                                SEASON_CURRENT,
                                SEASON_NONE,
                                SEASON_SCORE_TITLE,
                                SEASON_SCORE_LINE,
                                SEASON_SCORE_TOTAL,
                                SEASON_FINAL,
                                REWARD_AVAILABLE,
                                REWARD_CLAIMED,
                                REWARD_ALREADY_CLAIMED,
                                REWARD_NONE,
                                REWARD_INVENTORY_FULL,
                                HOLOGRAM_HEADER,
                                HOLOGRAM_LINE));

        for (Aggregation aggregation : Aggregation.values()) {
            keys.add(boardName(aggregation));
        }
        for (Period period : Period.values()) {
            keys.add(periodName(period));
        }
        return List.copyOf(keys);
    }
}
