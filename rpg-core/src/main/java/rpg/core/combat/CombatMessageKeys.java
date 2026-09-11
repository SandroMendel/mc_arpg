package rpg.core.combat;

import java.util.List;

import rpg.core.message.MessageKey;

/**
 * The player-facing text of the combat block.
 *
 * <p>B05 had none until now: it computed damage and let the vanilla animation say so. These two lines
 * put numbers on it - one about the player, one about what they are hitting.
 *
 * <p>Text, colours and layout live in {@code messages.yml} like everything else (Constitution V), so a
 * server can reword or translate them without a build. The placeholders are the contract.
 */
public final class CombatMessageKeys {

    /**
     * The player's own health and defence, on the action bar.
     *
     * <p>Placeholders: {@code health}, {@code max}, {@code percent}, {@code defense}.
     */
    public static final MessageKey STATUS_ACTION_BAR = MessageKey.of("combat.status.action-bar");

    /**
     * The same line for a holder without mana - a mob.
     *
     * <p>Its own key rather than a placeholder that stays empty: a text with a hole in it is a text
     * somebody has to remember to keep tidy, and an operator translating this file should see the two
     * shapes side by side.
     */
    public static final MessageKey STATUS_ACTION_BAR_NO_MANA =
            MessageKey.of("combat.status.action-bar-no-mana");

    /**
     * What the player just hit, in chat.
     *
     * <p>Placeholders: {@code target}, {@code health}, {@code max}, {@code percent},
     * {@code defense}, {@code damage}, {@code hits}.
     */
    /**
     * The line floating over a creature: what it is, and how much of it is left.
     *
     * <p>ONE line, because vanilla gives an entity exactly one name and this project ships no
     * resource pack and no second entity per mob (ADR-005, Constitution II). Name and health share
     * it; a floating health bar UNDER the name would cost a display entity per creature.
     */
    public static final MessageKey MOB_NAMEPLATE = MessageKey.of("combat.mob.nameplate");

    /**
     * Die Zeile eines Spielers, der zusaetzlich einen Zaehler traegt - heute nur der Warrior.
     *
     * <p>Ein eigener Text statt eines Platzhalters, der bei allen anderen leer bliebe: fuer einen
     * Magier stuende sonst ein Rest Formatierung ohne Zahl auf der Zeile, und drei Werte, die etwas
     * sagen, verloeren Platz an einen vierten, der nichts sagt. Dieselbe Entscheidung wie bei
     * {@link #STATUS_ACTION_BAR_NO_MANA}.
     *
     * <p><b>Welche Klasse den Zaehler hat, steht nicht hier und nicht im Code</b>: sie hat ihn, wenn
     * eine ihrer Faehigkeiten einen METER-Effekt traegt. Heute ist das die Raserei des Warriors.
     */
    public static final MessageKey STATUS_ACTION_BAR_WITH_METER =
            MessageKey.of("combat.status.action-bar-with-meter");

    /**
     * Der Fortschrittsteil derselben Zeile: Stufe und Erfahrung.
     *
     * <p><b>Ein eigener Text, der in die Spielerzeile eingesetzt wird</b>, statt vier weiterer
     * Vollzeilen. Mana, Zaehler und Hoechststufe sind drei voneinander unabhaengige Ja/Nein - als
     * Vollzeilen-Schluessel waeren das acht Texte, von denen ein Betreiber sieben gleich zu
     * formatieren haette und beim achten einen Tippfehler. Der Fortschritt steht deshalb einmal
     * hier und wird als {@code progress} in {@link #STATUS_ACTION_BAR} und
     * {@link #STATUS_ACTION_BAR_WITH_METER} eingesetzt. Wortlaut und Layout bleiben dabei
     * vollstaendig in {@code messages.yml} (Prinzip V) - der Code waehlt nur aus, welcher der
     * beiden Texte gilt, genau wie er es bei den Vollzeilen schon tut.
     *
     * <p>Platzhalter: {@code level}, {@code xp}, {@code xpNext}.
     */
    public static final MessageKey STATUS_PROGRESS = MessageKey.of("combat.status.progress");

    /**
     * Derselbe Teil fuer einen Charakter auf der Hoechststufe.
     *
     * <p>Dort ist die Schwelle der naechsten Stufe 0, und {@code 4120/0} saehe aus wie ein Fehler.
     * FR-051 will einen Charakter am Maximum als fertig gemeldet sehen und nicht als "0 % zur
     * naechsten Stufe"; {@code ProgressView.atMaxLevel()} ist genau dafuer ein eigenes Feld, und
     * hier steht der Text dazu.
     *
     * <p>Platzhalter: {@code level}, {@code xp}.
     */
    public static final MessageKey STATUS_PROGRESS_MAX =
            MessageKey.of("combat.status.progress-max");

    private CombatMessageKeys() {}

    /** Every key this block can emit, for the resolution test in the plugin module. */
    public static List<MessageKey> all() {
        return List.of(
                STATUS_ACTION_BAR,
                STATUS_ACTION_BAR_NO_MANA,
                MOB_NAMEPLATE,
                STATUS_ACTION_BAR_WITH_METER,
                STATUS_PROGRESS,
                STATUS_PROGRESS_MAX);
    }
}
