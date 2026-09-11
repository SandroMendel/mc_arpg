package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Der Sprachsatz und seine Datei (T130).
 *
 * <p>B13 baut hier <b>nichts Neues</b>: die Vollständigkeitsprüfung existiert seit B01
 * ({@code MessageKeyValidator}), und dieser Block ändert nur, welche Datei gelesen wird. Was hier
 * geprüft wird, ist genau diese Zuordnung — und dass ein Kürzel kein Pfad sein kann.
 */
class LanguageSetTest {

    @Test
    @DisplayName("T130: englisch bleibt bei messages.yml")
    void englishStaysWithTheShippedFile() {
        // Die Ausnahme ist Absicht: die Datei heisst seit B01 so, und jeder Betreiber, der sie
        // bearbeitet hat, haette sie nach einer Umbenennung ploetzlich nicht mehr in Gebrauch -
        // ohne Fehler, ohne Hinweis, nur mit englischen Vorgabetexten.
        assertThat(LanguageSet.defaultSet().file()).isEqualTo("messages.yml");
        assertThat(LanguageSet.defaultSet().isDefault()).isTrue();
    }

    @Test
    @DisplayName("T130: jede andere Sprache bekommt ihre eigene Datei")
    void everyOtherLanguageGetsItsOwnFile() {
        assertThat(new LanguageSet("de").file()).isEqualTo("messages_de.yml");
        assertThat(new LanguageSet("fr").file()).isEqualTo("messages_fr.yml");
        assertThat(new LanguageSet("pt-br").file()).isEqualTo("messages_pt-br.yml");
    }

    @Test
    @DisplayName("T130: das Kuerzel wird normalisiert, nicht abgewiesen")
    void thecodeIsNormalised() {
        // Ein Betreiber, der 'DE' schreibt, meint 'de'. Ihn dafuer nicht starten zu lassen waere
        // Strenge ohne Gewinn.
        assertThat(new LanguageSet("DE").code()).isEqualTo("de");
        assertThat(new LanguageSet("  de  ").code()).isEqualTo("de");
    }

    @Test
    @DisplayName("T130: ein Kuerzel kann kein Pfad sein")
    void acodeCannotBeAPath() {
        // Aus dem Kuerzel wird ein DATEINAME. Ein '../' darin waere ein Weg, Dateien ausserhalb des
        // Datenordners zu lesen - und die Konfiguration ist die eine Stelle, an der ein Betreiber
        // ohne Codezugriff etwas hineinschreibt.
        assertThatThrownBy(() -> new LanguageSet("../../etc/passwd"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Dateiname");
        assertThatThrownBy(() -> new LanguageSet("de/../en"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new LanguageSet("a b"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("T130: ein leeres Kuerzel wird abgewiesen")
    void anemptyCodeIsRejected() {
        assertThatThrownBy(() -> new LanguageSet("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ui.yml");
    }

    @Test
    @DisplayName("T130: ein einzelner Buchstabe ist kein Sprachkuerzel")
    void asingleLetterIsNotALanguage() {
        assertThatThrownBy(() -> new LanguageSet("d"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("die Meldung nennt Datei, Schluessel und erlaubte Form")
    void themessageNamesEverything() {
        // Prinzip V: jede Meldung nennt Datei, Schluessel und Grund. Ein Betreiber, der acht YAMLs
        // pflegt, soll nicht suchen muessen.
        assertThatThrownBy(() -> new LanguageSet("DEUTSCH!"))
                .hasMessageContaining("ui.yml")
                .hasMessageContaining("language")
                .hasMessageContaining("en, de, pt-br");
    }
}
