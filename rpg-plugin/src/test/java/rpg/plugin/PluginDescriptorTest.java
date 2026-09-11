package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * Die Ladeordnung, die kein anderer Test sehen kann.
 *
 * <p><b>Warum es diesen Test gibt.</b> {@code plugin.yml} trug seit dem ersten Commit
 * {@code load: STARTUP} - aus einer Zeit, in der dieses Plugin nur B01 bis B03 war und keine Welt
 * brauchte. STARTUP heisst: {@code onEnable} laeuft, <em>bevor</em> Paper die Welten laedt.
 * {@code Bukkit.getWorld("world")} gibt dann null.
 *
 * <p>B09 hat daraus einen Startfehler gemacht: {@code WorldResolver} loest jeden {@code world:}-Namen
 * aus {@code zones.yml} beim Binden der Konfiguration auf und verweigert den Start bei einer
 * unbekannten Welt (FR-002a). Unter STARTUP ist jede Welt unbekannt - der Zonenblock scheiterte auf
 * dem echten Server also immer, mit einer Meldung, die auf die Konfiguration zeigte, waehrend der
 * Fehler in der Ladeordnung stand.
 *
 * <p><b>Und warum {@code FullBootstrapTest} es nicht finden konnte.</b> Der legt die Welt an, bevor
 * er das Plugin laedt - ausdruecklich, weil B04s Regenerationssperre eine geladene Welt braucht.
 * Diese Reihenfolge gibt es auf dem echten Server unter STARTUP nicht. MockBukkit kennt die
 * Ladeordnung ueberhaupt nicht: {@code MockBukkit.load} ruft {@code onEnable} auf, ganz gleich, was
 * im Deskriptor steht. Es war kein uebersehener Fall, sondern einer, den kein Test dieses Projekts
 * ueberhaupt darstellen konnte - also wird hier der Deskriptor selbst geprueft, statt ein Verhalten,
 * das die Testumgebung nicht hat.
 *
 * <p>Der Deskriptor wird als YAML gelesen und nicht als Text durchsucht, damit ein
 * auskommentiertes {@code load:} nicht als gesetzt zaehlt.
 */
class PluginDescriptorTest {

    @Test
    @DisplayName("das Plugin laedt NACH den Welten - sonst kennt zones.yml keine")
    void theDescriptorLoadsAfterTheWorlds() {
        Object load = descriptor().get("load");

        assertThat(load)
                .as(
                        "STARTUP hiesse: onEnable ohne geladene Welten, und B09 verweigert dann jeden"
                                + " Start (FR-002a). POSTWORLD ist auch Bukkits Standard - ein"
                                + " fehlender Schluessel ist deshalb ebenfalls richtig.")
                .isIn("POSTWORLD", null);
    }

    @Test
    @DisplayName("die Bibliotheken stehen im Deskriptor und nicht im Jar (ADR-010)")
    void theThirdPartyLibrariesAreDeclaredAndNotShaded() {
        // Dieselbe Zusage, die JarContainsNoThirdPartyClassesTest von der anderen Seite prueft: dort
        // steht, dass sie NICHT im Jar sind, hier, dass sie ueberhaupt genannt werden. Fehlte der
        // Abschnitt, waere das Jar ebenso sauber - und das Plugin startbereit ohne JDBC-Treiber.
        assertThat(descriptor().get("libraries"))
                .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
                .isNotEmpty();
    }

    @Test
    @DisplayName("es gibt KEINEN commands:-Block mehr - alle sechs sind auf Brigadier umgezogen")
    void thereIsNoCommandsBlockAnyMore() {
        // T040. Die Entscheidung stammt aus dem Serverbeweis (T007-T010): bei Namensgleichheit
        // gewinnt Brigadier, und die plugin.yml-Fassung wird NIE erreicht. Ein Eintrag, der
        // beschreibt, was er nicht bedient, ist schlimmer als keiner - wer die usage-Zeile dort
        // aendert, aendert nichts und merkt es nicht.
        assertThat(descriptor())
                .as("ein zurueckgekehrter Eintrag waere still wirkungslos")
                .doesNotContainKey("commands");
    }

    @Test
    @DisplayName("jedes Recht, das ein Kommando verlangt, ist im Deskriptor definiert")
    void everyPermissionACommandDemandsIsDeclared() {
        // Dieser Test prueft bis B14 den commands:-Block. Den gibt es nicht mehr - die ZUSAGE aber
        // schon, und sie ist dieselbe: ein Recht, das nirgends definiert ist, faellt auf Bukkits
        // Standard zurueck, und der ist "jeder darf". Bei /xp und /coins waere das ein Loch.
        //
        // Geprueft wird jetzt gegen die KONSTANTEN der Kommandos statt gegen eine YAML-Liste. Das
        // ist strenger: die Liste konnte einen Eintrag verlieren, ohne dass etwas rot wurde.
        Map<String, Object> permissions = section("permissions");

        Map<String, String> demanded = new java.util.LinkedHashMap<>();
        demanded.put("/char", rpg.plugin.command.CharacterSheetCommand.PERMISSION);
        demanded.put("/trash", rpg.plugin.command.TrashCommand.PERMISSION);
        demanded.put("/stats", rpg.plugin.command.StatisticsCommand.PERMISSION);
        demanded.put("/top", rpg.plugin.command.TopCommand.PERMISSION);
        demanded.put("/coins", rpg.plugin.command.CoinsCommand.PERMISSION_BALANCE);
        demanded.put("/coins set|add|remove", rpg.plugin.command.CoinsCommand.PERMISSION_ADMIN);
        demanded.put("/xp", rpg.plugin.command.XpCommand.PERMISSION);

        demanded.forEach(
                (command, permission) ->
                        assertThat(permissions)
                                .as(
                                        command
                                                + " verlangt "
                                                + permission
                                                + ", aber die Berechtigung ist im Deskriptor nicht"
                                                + " definiert")
                                .containsKey(permission));
    }

    // --- fixtures ---

    @SuppressWarnings("unchecked")
    private static Map<String, Object> section(String key) {
        Object value = descriptor().get(key);
        assertThat(value).as("plugin.yml hat keinen Abschnitt " + key).isInstanceOf(Map.class);
        return (Map<String, Object>) value;
    }

    /**
     * Der Deskriptor, wie Paper ihn liest.
     *
     * <p>Aus dem Klassenpfad und nicht aus einem Pfad im Projekt: gelesen wird, was tatsaechlich
     * mitgeliefert wird. Der Platzhalter {@code ${version}} bleibt dabei unaufgeloest, was hier
     * niemanden stoert - keine Zusicherung dieses Tests haengt an der Version.
     */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> descriptor() {
        try (InputStream in =
                PluginDescriptorTest.class.getClassLoader().getResourceAsStream("plugin.yml")) {
            assertThat(in).as("plugin.yml liegt nicht im Klassenpfad").isNotNull();
            Object parsed = new Yaml().load(new String(in.readAllBytes(), StandardCharsets.UTF_8));
            assertThat(parsed).isInstanceOf(Map.class);
            return (Map<String, Object>) parsed;
        } catch (IOException unreadable) {
            throw new AssertionError("plugin.yml is unreadable", unreadable);
        }
    }
}
