package rpg.core.ui;

import java.util.List;

import rpg.core.message.MessageKey;
import rpg.core.stats.Attribute;

/**
 * Die Spielertexte dieses Blocks (FR-014, FR-015).
 *
 * <p><b>Kein Anzeigename steht im Code.</b> Wie eine Sidebar-Zeile aussieht, wie der Zonenhinweis
 * lautet, wie eine Attributzeile aufgebaut ist — alles lebt in der Sprachdatei (Prinzip V; Prinzip
 * VIII: der ausgelieferte Satz ist englisch). Der Code kennt nur Schlüssel.
 *
 * <p><b>{@link #all()} ist der Grund, aus dem es diese Klasse gibt.</b> {@code RpgPlugin
 * .loadMessages} geht die Liste durch und verweigert den Start, wenn ein Text fehlt — und meldet
 * <em>alle</em> Lücken auf einmal, nicht die erste. Ohne sie fiele ein vergessener Schlüssel erst
 * dem Spieler auf, und zwar als roher Schlüsselname mitten auf dem Bildschirm.
 *
 * <p>Ab B13 ist diese Prüfung zugleich die Prüfung des <b>Sprachsatzes</b> (FR-018): wer eine
 * zweite Sprache anlegt, bekommt beim Start die vollständige Liste dessen, was ihm noch fehlt.
 *
 * <h2>Die Attributnamen entstehen aus der Aufzählung, nicht aus einer zweiten Liste</h2>
 *
 * <p>{@link #attributeLabel(Attribute)} bildet den Schlüssel aus {@link Attribute}. Kommt ein
 * Attribut dazu, wächst die Prüfung mit — eine von Hand gepflegte Liste hier würde beim
 * übernächsten Mal vergessen. <b>Der Unterstrich wird dabei zum Bindestrich</b>, weil
 * {@link MessageKey} nur kleingeschriebene, mit Bindestrich getrennte Segmente zulässt; derselbe
 * Stolper hat in B11 die Attributnamen und in B12 die Ranglistennamen erwischt.
 */
public final class UiMessageKeys {

    private UiMessageKeys() {}

    // --- Die Sidebar --------------------------------------------------------

    /** Der Titel über der Sidebar. */
    public static final MessageKey SIDEBAR_TITLE = MessageKey.of("ui.sidebar.title");

    /** Die Level-Zeile. Platzhalter: {@code level}. */
    public static final MessageKey SIDEBAR_LEVEL = MessageKey.of("ui.sidebar.level");

    /**
     * Die Erfahrungszeile. Platzhalter: {@code xp}, {@code xpNext}.
     *
     * <p>Seit B13 steht sie hier und nicht mehr auf der Actionbar (FR-002a).
     */
    public static final MessageKey SIDEBAR_XP = MessageKey.of("ui.sidebar.xp");

    /**
     * Die Erfahrungszeile am Höchstlevel. Platzhalter: {@code xp}.
     *
     * <p><b>Ein eigener Schlüssel und keine abgeleitete Regel.</b> Am Maximum ist die Schwelle 0,
     * und {@code 4120/0} sähe aus wie ein Fehler. {@code ProgressView.atMaxLevel()} beantwortet das
     * als eigenes Feld; {@code StatusActionBar} hat es bereits so benutzt, und die Sidebar folgt
     * derselben Unterscheidung, statt sie ein zweites Mal leicht anders zu erfinden.
     */
    public static final MessageKey SIDEBAR_XP_MAX = MessageKey.of("ui.sidebar.xp-max");

    /** Die Coin-Zeile. Platzhalter: {@code coins}. */
    public static final MessageKey SIDEBAR_COINS = MessageKey.of("ui.sidebar.coins");

    /** Die Zonenzeile. Platzhalter: {@code zone}. */
    public static final MessageKey SIDEBAR_ZONE = MessageKey.of("ui.sidebar.zone");

    /** Was in der Zonenzeile steht, wenn der Spieler in keiner Zone ist. */
    public static final MessageKey SIDEBAR_ZONE_WILDERNESS =
            MessageKey.of("ui.sidebar.zone-wilderness");

    // --- Die Bossbar --------------------------------------------------------

    /** Der Zonenname beim Betreten. Platzhalter: {@code zone}. */
    public static final MessageKey BOSSBAR_ZONE_NOTICE = MessageKey.of("ui.bossbar.zone-notice");

    /** Ein Bosskampf. Platzhalter: {@code name}. */
    public static final MessageKey BOSSBAR_BOSS_FIGHT = MessageKey.of("ui.bossbar.boss-fight");

    /** Eine kanalisierte Fähigkeit. Platzhalter: {@code ability}. */
    public static final MessageKey BOSSBAR_CHANNELLING = MessageKey.of("ui.bossbar.channelling");

    // --- Die Schadenszahlen -------------------------------------------------

    /** Eine gebündelte Schadenszahl. Platzhalter: {@code amount}, {@code hits}. */
    public static final MessageKey DAMAGE_NUMBER = MessageKey.of("ui.damage.number");

    /**
     * Der tödliche Treffer. Platzhalter wie {@link #DAMAGE_NUMBER}.
     *
     * <p>Ein eigener Schlüssel, damit der Betreiber ihn hervorheben kann — der letzte Treffer ist
     * die einzige Zahl, auf die ein Spieler wirklich wartet.
     */
    public static final MessageKey DAMAGE_NUMBER_LETHAL = MessageKey.of("ui.damage.number-lethal");

    // --- Die Charakteruebersicht --------------------------------------------

    /** Der Fenstertitel. Platzhalter: {@code character}. */
    public static final MessageKey SHEET_TITLE = MessageKey.of("ui.sheet.title");

    /** Eine Attributzeile. Platzhalter: {@code label}, {@code value}. */
    public static final MessageKey SHEET_ATTRIBUTE = MessageKey.of("ui.sheet.attribute");

    /**
     * Eine prozentual gelesene Attributzeile. Platzhalter: {@code label}, {@code value}.
     *
     * <p>Nicht jedes Attribut ist eine Stückzahl. Ohne eigene Zeile stünde bei einer
     * Cooldown-Reduktion eine nackte {@code 0.15}, und der Spieler rechnete selbst.
     */
    public static final MessageKey SHEET_ATTRIBUTE_PERCENT =
            MessageKey.of("ui.sheet.attribute-percent");

    /** Die Klasse. Platzhalter: {@code class}. */
    public static final MessageKey SHEET_CLASS = MessageKey.of("ui.sheet.class");

    /** Level und Fortschritt in der Übersicht. Platzhalter: {@code level}. */
    public static final MessageKey SHEET_LEVEL = MessageKey.of("ui.sheet.level");

    /** Der Coin-Stand in der Übersicht. Platzhalter: {@code coins}. */
    public static final MessageKey SHEET_COINS = MessageKey.of("ui.sheet.coins");

    /** Ein Ausrüstungsplatz. Platzhalter: {@code slot}, {@code item}. */
    public static final MessageKey SHEET_EQUIPMENT = MessageKey.of("ui.sheet.equipment");

    /** Der Zustand eines Ausrüstungsstücks. Platzhalter: {@code percent}. */
    public static final MessageKey SHEET_CONDITION = MessageKey.of("ui.sheet.condition");

    /** Ein leerer Ausrüstungsplatz. Platzhalter: {@code slot}. */
    public static final MessageKey SHEET_EQUIPMENT_EMPTY =
            MessageKey.of("ui.sheet.equipment-empty");

    /** Ein Spieler ohne gewählten Charakter, der {@code /char} eingibt. */
    public static final MessageKey SHEET_NO_CHARACTER = MessageKey.of("ui.sheet.no-character");

    // --- Aus der Aufzaehlung gebildet ---------------------------------------

    /**
     * Der Anzeigename eines Attributs, {@code ui.attribute.<name>}.
     *
     * <p>Der Unterstrich wird zum Bindestrich — siehe Klassenkommentar.
     */
    public static MessageKey attributeLabel(Attribute attribute) {
        return MessageKey.of("ui.attribute." + attribute.name().toLowerCase().replace('_', '-'));
    }

    /**
     * Jeder Schlüssel, den dieser Block ausgeben kann — für die Startprüfung.
     *
     * <p>Die Attributnamen entstehen aus {@link Attribute}, nicht aus einer zweiten Liste. Es sind
     * heute <b>zehn</b>; die Zahl steht hier absichtlich nirgends, weil sie sich mit B04 ändert und
     * eine Zahl im Code die nächste Divergenz wäre.
     */
    public static List<MessageKey> all() {
        List<MessageKey> keys =
                new java.util.ArrayList<>(
                        List.of(
                                SIDEBAR_TITLE,
                                SIDEBAR_LEVEL,
                                SIDEBAR_XP,
                                SIDEBAR_XP_MAX,
                                SIDEBAR_COINS,
                                SIDEBAR_ZONE,
                                SIDEBAR_ZONE_WILDERNESS,
                                BOSSBAR_ZONE_NOTICE,
                                BOSSBAR_BOSS_FIGHT,
                                BOSSBAR_CHANNELLING,
                                DAMAGE_NUMBER,
                                DAMAGE_NUMBER_LETHAL,
                                SHEET_TITLE,
                                SHEET_ATTRIBUTE,
                                SHEET_ATTRIBUTE_PERCENT,
                                SHEET_CLASS,
                                SHEET_LEVEL,
                                SHEET_COINS,
                                SHEET_EQUIPMENT,
                                SHEET_CONDITION,
                                SHEET_EQUIPMENT_EMPTY,
                                SHEET_NO_CHARACTER));

        for (Attribute attribute : Attribute.values()) {
            keys.add(attributeLabel(attribute));
        }
        return List.copyOf(keys);
    }
}
