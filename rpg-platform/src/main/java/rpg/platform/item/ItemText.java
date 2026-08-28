package rpg.platform.item;

import java.util.Map;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import rpg.core.message.MessageKey;
import rpg.core.message.Messages;

/**
 * Aus einem Message-Schlüssel wird sichtbarer Text (ADR-005, ADR-028).
 *
 * <p><b>Warum es diese Klasse gibt, und warum erst jetzt.</b> B11 ist der erste Block mit
 * Farbcodes in {@code messages.yml} — alle dreißig {@code &}-Codes dort gehören ihm. Die sieben
 * Klassen dieses Blocks hielten je einen eigenen Serialisierer, und alle sieben nahmen
 * {@code legacySection()}. Der liest <b>§</b>, nicht <b>&</b>. Auf dem Server stand deshalb
 * wörtlich {@code &cSo viele Coins hast du nicht.} im Chat.
 *
 * <p>Ein Fehler an sieben Stellen ist kein Fehler an sieben Stellen, sondern eine fehlende Stelle.
 * Deshalb steht die Umwandlung hier und nur hier: {@code legacyAmpersand()}, ein Mal.
 *
 * <p><b>Und der Kursivsatz wird abgeschaltet.</b> Vanilla setzt jeden eigenen Item-Namen und jede
 * Lore-Zeile kursiv — was einen sorgfältig gesetzten Namen wie einen Fehler aussehen lässt. Das
 * gehört zu „Text sichtbar machen" und nicht in jede aufrufende Klasse.
 *
 * <p><b>Vorläufig bis B13</b> (ADR-028). Wenn die Anzeige einen eigenen Block bekommt, zieht diese
 * Klasse um; die Message-Schlüssel bleiben, wo sie sind.
 */
public final class ItemText {

    /** {@code &} und nicht {@code §} — siehe Klassenkommentar. */
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacyAmpersand();

    private ItemText() {}

    /** Der Text hinter dem Schlüssel, oder {@code null}, wenn ihn niemand kennt. */
    public static Component of(Messages messages, MessageKey key, Map<String, String> placeholders) {
        if (key == null || !messages.contains(key)) {
            return null;
        }
        return LEGACY.deserialize(messages.get(key, placeholders));
    }

    /** Wie {@link #of}, aber mit einem Ersatztext statt {@code null}. */
    public static Component orElse(Messages messages, MessageKey key, String fallback) {
        Component text = of(messages, key, Map.of());
        return text != null ? text : Component.text(fallback);
    }

    /**
     * Für einen Item-Namen oder eine Lore-Zeile: ohne Vanillas Kursivsatz.
     *
     * <p>Nur dort abschalten, wo es stört. Eine Chat-Zeile ist ohnehin nicht kursiv, und ihr die
     * Entscheidung aufzuzwingen hieße, eine Darstellungsfrage an einer Stelle zu beantworten, an der
     * sie nicht gestellt wurde.
     */
    public static Component onItem(Component text) {
        return text == null ? null : text.decoration(TextDecoration.ITALIC, false);
    }
}
