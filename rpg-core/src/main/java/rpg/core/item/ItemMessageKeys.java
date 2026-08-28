package rpg.core.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import rpg.core.classes.LadderSlot;
import rpg.core.message.MessageKey;

/**
 * Die Spielertexte dieses Blocks.
 *
 * <p><b>Der Name einer Vorlage ist ein Schlüssel, kein Text</b> — genau wie bei B10s Arten und B09s
 * Regionen. Der sichtbare Name lebt unter {@code item.<key>.name} und nirgends sonst. Eine Vorlage
 * umzubenennen ist eine Zeile dort, und keine Beutetabelle, kein Händlerbestand und kein späterer
 * Block muss dafür angefasst werden (Prinzip V).
 *
 * <p>{@link #all(Collection)} nimmt deshalb die konfigurierten Vorlagenschlüssel entgegen — die
 * Startprüfung kann dann beweisen, dass jede Vorlage wirklich einen Namen hat, und ein Trank, der
 * als {@code item.potion.minor-healing.name} durch das Inventar liefe, verweigert stattdessen den
 * Start.
 *
 * <p><b>Auch die Raritätsbezeichnungen stehen dort</b>, nicht im Java-Quelltext. Ein Farbcode im
 * Code wäre ein hartcodierter Spielertext mit einem anderen Namen — und die acht Stufen sind das
 * einzige, was ein Spieler von der Raritätsskala je zu sehen bekommt (FR-014).
 */
public final class ItemMessageKeys {

    private ItemMessageKeys() {}

    /** Der sichtbare Name einer Vorlage, {@code item.<key>.name}. */
    public static MessageKey nameOf(String templateKey) {
        return MessageKey.of("item." + templateKey + ".name");
    }

    /**
     * Die Beschreibungszeile einer Vorlage, {@code item.<key>.lore}.
     *
     * <p>Optional: nicht jede Vorlage braucht eine. Wirkungswerte kommen ohnehin aus der Vorlage
     * und werden bei jedem Laden neu abgeleitet (FR-002) — hier steht nur, was ein Autor darüber
     * hinaus sagen will.
     */
    public static MessageKey loreOf(String templateKey) {
        return MessageKey.of("item." + templateKey + ".lore");
    }

    /** Der Name einer Raritätsstufe, {@code item.rarity.<key>.name}. */
    public static MessageKey rarityName(Rarity rarity) {
        return MessageKey.of("item.rarity." + rarity.configKey() + ".name");
    }

    // --- Die fünf Ablehnungen (FR-037) ------------------------------------------------
    //
    // Jede hat ihre eigene Meldung, und das ist die Anforderung. „Geht nicht" ist keine Antwort:
    // ein Spieler, der nicht erfaehrt, warum sein Trank nicht wirkt, probiert es weiter und haelt
    // ihn dann fuer kaputt.

    /** Das Level reicht nicht. */
    public static final MessageKey REFUSED_LEVEL = MessageKey.of("item.refused.level");

    /** Falsche Klasse. */
    public static final MessageKey REFUSED_CLASS = MessageKey.of("item.refused.class");

    /** Die Abklingzeit läuft noch. Platzhalter: {@code seconds}. */
    public static final MessageKey REFUSED_COOLDOWN = MessageKey.of("item.refused.cooldown");

    /** Es würde nichts bewirken — Heilung bei vollem Leben (FR-036). */
    public static final MessageKey REFUSED_NO_EFFECT = MessageKey.of("item.refused.no-effect");

    /** Die Vorlage ist unbekannt: ein Exemplar, dessen Vorlage verschwunden ist (FR-007). */
    public static final MessageKey REFUSED_UNKNOWN = MessageKey.of("item.refused.unknown");

    // --- Der Händler (FR-057 bis FR-065) ----------------------------------------------
    //
    // Auch hier gilt Prinzip V: kein sichtbarer Text im Java-Quelltext. Das Fenster zeichnet
    // Vanilla-Materialien und holt jede Zeile von hier - B13 tauscht spaeter die Darstellung,
    // ohne dass diese Schluessel sich aendern.

    /** Die Überschrift des Händlerfensters. */
    public static final MessageKey VENDOR_TITLE = MessageKey.of("item.vendor.title");

    /** Der Verkaufsplatz im Fenster. */
    public static final MessageKey VENDOR_SELL = MessageKey.of("item.vendor.sell");

    /**
     * Der Reparaturplatz einer Leiter, {@code item.vendor.repair.<slot>}.
     *
     * <p>Je Leiter einer, weil B07 genau zwei kennt — Rüstung und Waffe — und weil sie getrennt
     * verschleißen (FR-040, FR-041). Ein gemeinsamer Knopf müsste raten, welche gemeint ist.
     */
    public static MessageKey vendorRepair(LadderSlot slot) {
        return MessageKey.of("item.vendor.repair." + slot.configKey());
    }

    /** Der Aufstiegsplatz einer Leiter, {@code item.vendor.upgrade.<slot>}. */
    public static MessageKey vendorUpgrade(LadderSlot slot) {
        return MessageKey.of("item.vendor.upgrade." + slot.configKey());
    }

    /** Der Preis an der Ware. Platzhalter: {@code price}. */
    public static final MessageKey VENDOR_PRICE = MessageKey.of("item.vendor.price");

    /** Derselbe Preis, wenn der Kontostand ihn nicht deckt. Platzhalter: {@code price}. */
    public static final MessageKey VENDOR_PRICE_SHORT = MessageKey.of("item.vendor.price-short");

    /** Verkauft. Platzhalter: {@code amount}. */
    public static final MessageKey VENDOR_SOLD = MessageKey.of("item.vendor.sold");

    /** Gekauft. Platzhalter: {@code amount}. */
    public static final MessageKey VENDOR_BOUGHT = MessageKey.of("item.vendor.bought");

    /** Er nimmt diesen Gegenstand nicht an (FR-016). */
    public static final MessageKey VENDOR_NOT_SELLABLE = MessageKey.of("item.vendor.not-sellable");

    /** Klassenausrüstung bleibt am Charakter (FR-063, ADR-018). */
    public static final MessageKey VENDOR_BOUND = MessageKey.of("item.vendor.bound");

    /** Diese Vorlage führt er nicht (FR-058). */
    public static final MessageKey VENDOR_NOT_IN_STOCK = MessageKey.of("item.vendor.not-in-stock");

    /** Zu wenig Coins. */
    public static final MessageKey VENDOR_NOT_ENOUGH = MessageKey.of("item.vendor.not-enough");

    /** Kein Platz im Inventar — geprüft vor der Buchung (FR-064). */
    public static final MessageKey VENDOR_NO_ROOM = MessageKey.of("item.vendor.no-room");

    // --- Verschleiss und Reparatur (FR-051 bis FR-056) --------------------------------

    /** Repariert. Platzhalter: {@code price}. */
    public static final MessageKey REPAIR_DONE = MessageKey.of("item.repair.done");

    /** Da war nichts abgenutzt — eine Auskunft, keine Gratisreparatur (FR-054). */
    public static final MessageKey REPAIR_NOT_WORN = MessageKey.of("item.repair.not-worn");

    /**
     * Die Ausrüstung ist verschlissen. Platzhalter: {@code condition}.
     *
     * <p>Höchstens einmal je Schwelle und Ruhezeit (FR-051). Eine Meldung bei jedem Treffer wäre
     * eine, die niemand mehr liest — und dann fiele die eine, auf die es ankam, auch nicht mehr auf.
     */
    public static final MessageKey WEAR_WARNING = MessageKey.of("item.wear.warning");

    // --- Kosmetik (FR-067 bis FR-073) -------------------------------------------------

    /** Die Farbe wird jetzt getragen. */
    public static final MessageKey COSMETIC_APPLIED = MessageKey.of("item.cosmetic.applied");

    /**
     * Noch nicht auf der Höchststufe — <b>und der Besitz bleibt</b> (FR-069).
     *
     * <p>Der Text muss beides sagen. „Geht nicht" allein liest sich wie ein verlorener Kauf, und
     * genau das ist es nicht: die Farbe wartet.
     */
    public static final MessageKey COSMETIC_NOT_TOP_TIER =
            MessageKey.of("item.cosmetic.not-top-tier");

    /** Sie wird bereits getragen. */
    public static final MessageKey COSMETIC_ALREADY_WORN =
            MessageKey.of("item.cosmetic.already-worn");

    /** Amboss, Zauberpult und Schleifstein sind für gebundene Ausrüstung gesperrt (FR-056). */
    public static final MessageKey REPAIR_ROUTE_LOCKED = MessageKey.of("item.repair.route-locked");

    // --- Der Mülleimer (FR-078) --------------------------------------------------------

    /** Nachgefragt: derselbe Befehl noch einmal vernichtet. */
    public static final MessageKey TRASH_CONFIRM = MessageKey.of("item.trash.confirm");

    /** Vernichtet. */
    public static final MessageKey TRASH_DONE = MessageKey.of("item.trash.done");

    /** Die Hand ist leer. */
    public static final MessageKey TRASH_NOTHING_HELD =
            MessageKey.of("item.trash.nothing-held");

    /** Klassenausrüstung bleibt am Charakter (FR-063, ADR-018). */
    public static final MessageKey TRASH_BOUND = MessageKey.of("item.trash.bound");

    /**
     * Die Schlüssel, die nicht an einer Vorlage hängen.
     *
     * <p>Sie stehen fest, also lässt sich beim Start beweisen, dass jeder einzelne aufgelöst wird.
     * Eine Ablehnung, die als roher Schlüssel im Chat erscheint, ist der Fall, in dem ein Spieler
     * gar nichts erfährt — und dieser Fall soll den Start kosten, nicht den Abend.
     */
    private static final List<MessageKey> FIXED =
            List.of(
                    REFUSED_LEVEL,
                    REFUSED_CLASS,
                    REFUSED_COOLDOWN,
                    REFUSED_NO_EFFECT,
                    REFUSED_UNKNOWN,
                    VENDOR_TITLE,
                    VENDOR_SELL,
                    VENDOR_PRICE,
                    VENDOR_PRICE_SHORT,
                    VENDOR_SOLD,
                    VENDOR_BOUGHT,
                    VENDOR_NOT_SELLABLE,
                    VENDOR_BOUND,
                    VENDOR_NOT_IN_STOCK,
                    VENDOR_NOT_ENOUGH,
                    VENDOR_NO_ROOM,
                    REPAIR_DONE,
                    REPAIR_NOT_WORN,
                    WEAR_WARNING,
                    REPAIR_ROUTE_LOCKED,
                    COSMETIC_APPLIED,
                    COSMETIC_NOT_TOP_TIER,
                    COSMETIC_ALREADY_WORN,
                    TRASH_CONFIRM,
                    TRASH_DONE,
                    TRASH_NOTHING_HELD,
                    TRASH_BOUND);

    /**
     * Jeder Schlüssel, den dieser Block ausgeben kann — für die Auflösungsprüfung beim Start.
     *
     * <p>Nimmt die Vorlagenschlüssel entgegen, weil sie erst feststehen, wenn {@code items.yml}
     * gelesen ist. Die Lore-Schlüssel sind <b>nicht</b> dabei: sie sind optional, und eine Pflicht
     * daraus zu machen hieße, jeden Trank mit einem Satz zu versehen, den niemand schreiben wollte.
     */
    public static List<MessageKey> all(Collection<String> templateKeys) {
        List<MessageKey> keys = new ArrayList<>();
        for (String templateKey : templateKeys) {
            keys.add(nameOf(templateKey));
        }
        for (Rarity rarity : Rarity.values()) {
            keys.add(rarityName(rarity));
        }
        keys.addAll(FIXED);
        for (LadderSlot slot : LadderSlot.values()) {
            keys.add(vendorRepair(slot));
            keys.add(vendorUpgrade(slot));
        }
        return keys;
    }
}
