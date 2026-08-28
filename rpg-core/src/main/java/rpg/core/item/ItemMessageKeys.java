package rpg.core.item;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

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
        return keys;
    }
}
