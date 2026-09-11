package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.message.MapMessages;
import rpg.core.message.MessageKeyValidator;
import rpg.core.message.Messages;
import rpg.core.stats.Attribute;
import rpg.core.ui.HudSurface;
import rpg.core.ui.UiConfig;
import rpg.core.ui.UiConfigSchema;
import rpg.core.ui.UiMessageKeys;

/**
 * T012 — die <b>ausgelieferte</b> {@code ui.yml}.
 *
 * <p>Gegen die echte Datei, nicht gegen eine Fixtur. {@code UiConfigSchemaTest} belegt, dass das
 * Schema tut, was es soll; dieser Test belegt, dass die Datei, die der Server tatsächlich liest,
 * durch dieses Schema kommt und genau die Werte aus {@code contracts/ui-config.md} §1 ergibt.
 * Beides ist nötig, und nur das zweite fände einen Tippfehler in der ausgelieferten Datei.
 *
 * <p><b>Warum in {@code rpg-plugin} und nicht in {@code rpg-core}:</b> die ausgelieferte Datei und
 * SnakeYAML liegen beide außerhalb von {@code rpg-core} — dieselbe Aufteilung und derselbe Grund,
 * aus dem {@code ShippedAbilityConfigTest} und {@code ShippedClassConfigTest} hier liegen.
 * {@code tasks.md} nannte für T012 einen Pfad unter {@code rpg-core}; der wäre nicht baubar, weil
 * {@code rpg-core} die Ressourcen des Plugins nicht sieht.
 */
class ShippedUiConfigTest {

    @Test
    @DisplayName("die ausgelieferte ui.yml besteht das Schema")
    void theShippedFilePassesTheSchema() throws Exception {
        assertThat(shippedUi()).isNotNull();
    }

    @Test
    @DisplayName("sie ergibt genau die Werte aus contracts/ui-config.md §1")
    void itYieldsExactlyTheContractValues() throws Exception {
        UiConfig config = shippedUi();

        assertThat(config.language()).isEqualTo("en");
        assertThat(config.tick().toMillis()).isEqualTo(1000);
        assertThat(config.zoneNoticeDuration().toSeconds()).isEqualTo(4);
        assertThat(config.damageNumbers().lifetime().toMillis()).isEqualTo(1200);
        assertThat(config.damageNumbers().offset()).isEqualTo(1.4);
    }

    @Test
    @DisplayName("alle drei Flaechen und die Schadenszahlen sind ausgeliefert eingeschaltet")
    void everythingShipsEnabled() throws Exception {
        UiConfig config = shippedUi();

        // Der Block existiert, damit zwei von vier Vanilla-Flaechen aufhoeren brachzuliegen. Sie
        // abgeschaltet auszuliefern waere genau der Zustand, den er beendet.
        assertThat(config.isEnabled(HudSurface.ACTION_BAR)).isTrue();
        assertThat(config.isEnabled(HudSurface.BOSS_BAR)).isTrue();
        assertThat(config.isEnabled(HudSurface.SIDEBAR)).isTrue();
        assertThat(config.damageNumbers().enabled()).isTrue();
    }

    @Test
    @DisplayName("der Takt liegt unter den zwei Sekunden, nach denen die Actionbar ausblendet")
    void theTickStaysUnderTheFadeOut() throws Exception {
        // Der einzige Wert dieser Datei, der eine Mechanik traegt statt eines Geschmacks: laenger
        // als der Ausblendezeitraum, und die Actionbar blinkt bei jedem Spieler dauerhaft.
        assertThat(shippedUi().tick().toMillis()).isLessThan(2000);
    }

    @Test
    @DisplayName("jeder Textschluessel dieses Blocks loest in messages.yml auf")
    void everyKeyResolves() throws Exception {
        // Dieselbe Pruefung, die RpgPlugin.loadMessages beim Start macht - hier aber ohne Server,
        // also faellt eine Luecke schon im Build auf und nicht erst beim Hochfahren.
        //
        // Ab B13 ist das zugleich die Pruefung des SPRACHSATZES (FR-018): sie meldet ALLE
        // fehlenden Schluessel auf einmal. Wer eine zweite Sprache anlegt, bekommt die Liste -
        // bei einer Meldung je Startversuch gaebe man nach dem zwanzigsten auf.
        Messages messages = MapMessages.fromNested(load("/messages.yml"));

        MessageKeyValidator.verifyAllPresent(messages, UiMessageKeys.all());
    }

    @Test
    @DisplayName("jedes der zehn Attribute hat einen Anzeigenamen")
    void everyAttributeHasALabel() throws Exception {
        // Die Namen entstehen aus der Aufzaehlung Attribute, nicht aus einer Liste im Code. Ein
        // umbenanntes Attribut faellt hier auf und nicht beim Spieler, der /char oeffnet.
        Messages messages = MapMessages.fromNested(load("/messages.yml"));

        for (Attribute attribute : Attribute.values()) {
            assertThat(messages.get(UiMessageKeys.attributeLabel(attribute), Map.of()))
                    .as("Anzeigename fuer " + attribute)
                    .isNotBlank();
        }
    }

    private static UiConfig shippedUi() throws Exception {
        Map<String, Object> document = load("/ui.yml");
        ConfigSchema<UiConfig> schema = UiConfigSchema.schema();
        return schema.bind(SchemaValidator.validate(Path.of("ui.yml"), document, schema));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String resource) throws Exception {
        try (InputStream stream = ShippedUiConfigTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            return new Yaml().load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }
}
