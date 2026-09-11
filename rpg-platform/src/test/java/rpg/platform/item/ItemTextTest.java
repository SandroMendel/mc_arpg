package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.item.ItemMessageKeys;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;

/**
 * <b>Der Test, den es beim Ausliefern nicht gab.</b>
 *
 * <p>Auf dem Server stand wörtlich {@code &cSo viele Coins hast du nicht.} im Chat. Die Ursache:
 * alle sieben Klassen dieses Blocks hielten je einen eigenen Serialisierer, und alle sieben nahmen
 * {@code legacySection()} — der liest <b>§</b>, nicht <b>&</b>. B11 ist der erste Block mit Farben in
 * {@code messages.yml}; alle dreißig Codes dort gehören ihm, und keiner davon wurde je übersetzt.
 *
 * <p>Ein Fehler an sieben Stellen ist kein Fehler an sieben Stellen, sondern eine fehlende Stelle —
 * und eine fehlende Stelle hat auch keinen Test. Beides ist jetzt behoben.
 */
class ItemTextTest {

    private static final MessageKey KEY = MessageKey.of("item.test.line");
    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    @DisplayName("das &-Zeichen wird zu FARBE - und steht nicht mehr im Text")
    void theampersandBecomesColour() {
        Component text = ItemText.of(messages("&cYou do not have that many coins."), KEY, Map.of());

        assertThat(PLAIN.serialize(text))
                .as("genau das stand auf dem Server: der rohe Code im Chat")
                .isEqualTo("You do not have that many coins.")
                .doesNotContain("&");
        assertThat(text.color()).isEqualTo(NamedTextColor.RED);
    }

    @Test
    @DisplayName("mehrere Farben in einer Zeile bleiben erhalten")
    void severalColoursInOneLineSurvive() {
        Component text = ItemText.of(messages("&7Condition &f68%&7 · stats at &f87%"), KEY, Map.of());

        assertThat(PLAIN.serialize(text)).isEqualTo("Condition 68% · stats at 87%");
    }

    @Test
    @DisplayName("Platzhalter werden gefuellt")
    void placeholdersAreFilled() {
        Component text =
                ItemText.of(messages("&aHealed for &f{amount} HP&a."), KEY, Map.of("amount", "140"));

        assertThat(PLAIN.serialize(text)).isEqualTo("Healed for 140 HP.");
    }

    @Test
    @DisplayName("ein unbekannter Schluessel gibt NULL - keinen rohen Schluessel im Chat")
    void anunknownKeyGivesNull() {
        // Der Aufrufer entscheidet, ob er schweigt oder einen Ersatz nimmt. Den Schluessel selbst
        // auszugeben waere die schlechteste Antwort: der Spieler saehe "item.test.line".
        assertThat(ItemText.of(new MapMessages(Map.of()), KEY, Map.of())).isNull();
    }

    @Test
    @DisplayName("orElse nimmt den Ersatztext, wenn niemand den Schluessel kennt")
    void orElseTakesTheFallback() {
        Component text = ItemText.orElse(new MapMessages(Map.of()), KEY, "Vendor");

        assertThat(PLAIN.serialize(text)).isEqualTo("Vendor");
    }

    @Test
    @DisplayName("auf einem Item wird Vanillas Kursivsatz ABGESCHALTET")
    void onanItemTheItalicsAreSwitchedOff() {
        // Minecraft setzt jeden eigenen Item-Namen und jede Lore-Zeile kursiv - was einen
        // sorgfaeltig gesetzten Namen wie einen Fehler aussehen laesst.
        Component text = ItemText.onItem(ItemText.of(messages("&eSell"), KEY, Map.of()));

        assertThat(text.decoration(TextDecoration.ITALIC)).isEqualTo(TextDecoration.State.FALSE);
    }

    @Test
    @DisplayName("onItem vertraegt null - ein fehlender Text bleibt ein fehlender Text")
    void onitemToleratesNull() {
        assertThat(ItemText.onItem(null)).isNull();
    }

    @Test
    @DisplayName("und die AUSGELIEFERTEN Farbcodes werden alle uebersetzt")
    void andtheshippedColourCodesAllTranslate() throws Exception {
        // Die Gegenprobe an der echten Datei. Ein Test mit erfundenen Texten haette den Fehler nicht
        // gefunden, weil er das Format der echten Datei gar nicht angesehen haette.
        //
        // Gesucht wird ein FARBCODE, nicht das Zeichen: in messages.yml steht unter anderem ein
        // Regionsname "Rise & Fall", und das Und-Zeichen darin ist keins. Genau so soll es sein -
        // legacyAmpersand verbraucht nur ein & vor einem gueltigen Code und laesst jedes andere
        // stehen. Der erste Anlauf dieses Tests verbot jedes & und haette damit richtiges Verhalten
        // als Fehler gemeldet.
        java.util.regex.Pattern colourCode = java.util.regex.Pattern.compile("&[0-9a-fk-orA-FK-OR]");
        java.nio.file.Path shipped =
                java.nio.file.Path.of(
                        "..", "rpg-plugin", "src", "main", "resources", "messages.yml");

        int checked = 0;
        for (String line : java.nio.file.Files.readAllLines(shipped)) {
            String trimmed = line.trim();
            if (trimmed.startsWith("#") || trimmed.indexOf(':') < 0) {
                continue;
            }
            String raw =
                    trimmed.substring(trimmed.indexOf(':') + 1).trim().replaceAll("^['\"]|['\"]$", "");
            if (!colourCode.matcher(raw).find()) {
                continue;
            }
            Component text = ItemText.of(messages(raw), KEY, Map.of());
            checked++;

            assertThat(colourCode.matcher(PLAIN.serialize(text)).find())
                    .as("aus messages.yml: %s", trimmed)
                    .isFalse();
        }

        assertThat(checked)
                .as("haette hier nichts gestanden, pruefte der Test die Datei gar nicht")
                .isGreaterThan(20);
    }

    @Test
    @DisplayName("und ItemMessageKeys nennt jeden Schluessel, den dieser Block ausgeben kann")
    void anditemMessageKeysNamesEveryKey() {
        // Die Startpruefung haengt daran: was hier nicht drinsteht, faellt beim Start nicht auf und
        // erscheint spaeter als roher Schluessel im Chat.
        assertThat(ItemMessageKeys.all(java.util.List.of("potion.test")))
                .contains(
                        ItemMessageKeys.SPLASH_HEALED,
                        ItemMessageKeys.SPLASH_SELF_HEALED,
                        ItemMessageKeys.EFFECT_HEAL,
                        ItemMessageKeys.GEAR_CONDITION_LORE);
    }

    private static Messages messages(String text) {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put(KEY.value(), text);
        return new MapMessages(texts);
    }
}
