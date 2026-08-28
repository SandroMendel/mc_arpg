package rpg.platform.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import net.kyori.adventure.text.Component;

import rpg.core.item.ConsumableEffect;
import rpg.core.item.ItemMessageKeys;
import rpg.core.item.ItemTemplate;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.stats.Attribute;

/**
 * Was ein Verbrauchbares <b>genau</b> tut — als Lore-Zeilen (FR-002).
 *
 * <p><b>Die Zahlen stehen im Item, weil sie sonst nirgends stehen.</b> Ein Trank namens „Healing
 * Potion" sagt einem Spieler nicht, ob er 40 oder 420 heilt — und der Unterschied zwischen den
 * dreien im Bestand ist genau das. Ohne die Zahl bleibt nur Ausprobieren, und Ausprobieren kostet
 * einen Trank.
 *
 * <p><b>Abgeleitet bei jedem Zeichnen, nie gespeichert.</b> Dieselbe Regel wie für Name und
 * Raritätsbezeichnung, und derselbe Grund: wer {@code heal} in {@code items.yml} ändert und neu
 * lädt, ändert damit auch die Zeile auf jedem vorhandenen Exemplar (SC-001). Eine eingebrannte Zahl
 * wäre nach dem ersten Balancing eine Lüge auf dem Gegenstand.
 *
 * <p><b>Und die Texte selbst stehen in {@code messages.yml}</b> (Prinzip V) — hier wird nur
 * entschieden, welche Zeile für welche Wirkung gilt und mit welchen Zahlen sie gefüllt wird.
 */
final class EffectLore {

    private EffectLore() {}

    /**
     * Die Zeilen für diese Vorlage, in fester Reihenfolge.
     *
     * <p>Erst was sie tut, dann wie sie benutzt wird — Wirkung, Dauer, Wurf, Abklingzeit. Ein
     * Spieler, der den Bestand durchsieht, vergleicht die erste Zeile; alles Weitere liest er nur
     * bei dem, den er nimmt.
     */
    static List<Component> of(Messages messages, ItemTemplate template, boolean thrown) {
        List<Component> lines = new ArrayList<>();
        ConsumableEffect effect = template == null ? null : template.effect();
        if (effect == null) {
            return lines;
        }

        effect.healAmount()
                .ifPresent(
                        amount ->
                                add(
                                        lines,
                                        messages,
                                        ItemMessageKeys.EFFECT_HEAL,
                                        Map.of("amount", number(amount))));
        effect.manaAmount()
                .ifPresent(
                        amount ->
                                add(
                                        lines,
                                        messages,
                                        ItemMessageKeys.EFFECT_MANA,
                                        Map.of("amount", number(amount))));

        // Je Attribut eine Zeile. Ein Trank mit zwei Beitraegen - fury gibt Schaden UND
        // Angriffsgeschwindigkeit - waere in einer Zeile eine Aufzaehlung, die niemand liest.
        effect.buff()
                .forEach(
                        (attribute, value) ->
                                add(
                                        lines,
                                        messages,
                                        ItemMessageKeys.EFFECT_BUFF,
                                        Map.of(
                                                "attribute", attributeName(messages, attribute),
                                                "amount", number(value),
                                                "seconds",
                                                effect.buffDuration()
                                                        .map(d -> String.valueOf(d.toSeconds()))
                                                        .orElse("0"))));

        if (thrown) {
            add(lines, messages, ItemMessageKeys.EFFECT_SPLASH, Map.of());
        }
        effect.cooldownOrNone()
                .ifPresent(
                        cooldown ->
                                add(
                                        lines,
                                        messages,
                                        ItemMessageKeys.EFFECT_COOLDOWN,
                                        Map.of("seconds", String.valueOf(cooldown.toSeconds()))));
        return lines;
    }

    private static void add(
            List<Component> lines, Messages messages, MessageKey key, Map<String, String> values) {
        Component line = ItemText.of(messages, key, values);
        if (line != null) {
            lines.add(ItemText.onItem(line));
        }
    }

    /**
     * Der sichtbare Name eines Attributs.
     *
     * <p>Über einen Message-Schlüssel, mit dem Konfigurationsschlüssel als Rückfall. {@code defense}
     * ist als Notlösung lesbar genug, und ein fehlender Text soll eine Zeile nicht verschwinden
     * lassen — sie ist die einzige Stelle, an der ein Spieler erfährt, was der Trank ihm gibt.
     */
    private static String attributeName(Messages messages, Attribute attribute) {
        MessageKey key = ItemMessageKeys.attributeName(attribute);
        return messages.contains(key) ? messages.get(key, Map.of()) : attribute.key();
    }

    /**
     * Eine Zahl, wie ein Spieler sie lesen will.
     *
     * <p>Ganze Zahlen ohne Komma — {@code 140} und nicht {@code 140.0}. Bruchteile behalten eine
     * Stelle, weil {@code attack-speed: 0.15} sonst als {@code 0} dastünde und wie ein Fehler
     * aussähe.
     */
    private static String number(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.valueOf(Math.round(value * 100.0) / 100.0);
    }
}
