package rpg.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * T051 — <b>ein im Code benutztes Recht ohne {@code plugin.yml}-Eintrag macht diesen Test rot</b>
 * (FR-010, FR-034).
 *
 * <h2>Warum das die wichtigste Zusage des Rechtebaums ist</h2>
 *
 * <p>Ein Recht, das nirgends deklariert ist, existiert für Bukkit nicht — und {@code hasPermission}
 * auf ein unbekanntes Recht fällt auf die Voreinstellung zurück. Die ist für einen Operator
 * {@code true} und für alle anderen {@code false}, <b>je nach Server unterschiedlich</b>. Es wirkt
 * also: manchmal wie gedacht, manchmal wie ein offenes Tor, und nie mit einer Fehlermeldung.
 *
 * <p>Das ist die schlimmste Sorte Fehler, die dieser Block haben kann: er sieht auf dem Testserver
 * (wo man Operator ist) richtig aus und ist auf dem echten Server falsch.
 *
 * <h2>Der Test sucht die Rechte selbst</h2>
 *
 * <p>Er führt <b>keine Liste</b>. Er liest alle Zeichenkettenliterale der Form {@code rpg.…} aus
 * dem Produktivcode und verlangt für jedes einen Eintrag. Ein neues Recht ist damit ab der ersten
 * Zeile mitgeprüft, ohne dass jemand daran denken muss — und genau daran hätte jemand nicht
 * gedacht.
 */
class DeclaredPermissionsGuardTest {

    /** Der ganze Produktivbaum, nicht nur das Kommandopaket: Rechte werden überall geprüft. */
    private static final Path SOURCES = Path.of("src", "main", "java");

    /**
     * Was wie ein Rechteknoten aussieht.
     *
     * <p>{@code rpg.} gefolgt von mindestens zwei Segmenten aus Kleinbuchstaben, Ziffern und
     * Bindestrichen. Das trifft {@code rpg.admin.reload} und trifft <em>nicht</em>
     * {@code rpg.core.item} — Paketnamen stehen nicht in Zeichenkettenliteralen.
     */
    private static final Pattern LOOKS_LIKE_A_PERMISSION =
            Pattern.compile("\"(rpg\\.[a-z0-9-]+(?:\\.[a-z0-9-]+)+)\"");

    @Test
    @DisplayName("FR-010: jedes im Code benutzte Recht steht in plugin.yml")
    void everyPermissionUsedInCodeIsDeclared() throws IOException {
        Set<String> declared = declaredPermissions();
        Set<String> used = permissionsUsedInCode();

        assertThat(used)
                .as("ohne einen einzigen Fund prueft dieser Test nichts")
                .isNotEmpty();

        assertThat(declared)
                .as(
                        "diese Rechte werden im Code benutzt, sind aber nicht deklariert - sie"
                            + " wirken je nach Server unterschiedlich und melden das nie")
                .containsAll(used);
    }

    @Test
    @DisplayName("FR-010: jeder deklarierte Knoten hat Beschreibung UND ausdrueckliches default")
    void everyDeclaredNodeIsComplete() {
        Map<String, Object> permissions = permissionsSection();

        permissions.forEach(
                (node, body) -> {
                    assertThat(body).as(node + " hat keinen Rumpf").isInstanceOf(Map.class);
                    Map<?, ?> fields = (Map<?, ?>) body;
                    assertThat(fields.get("description"))
                            .as(node + " hat keine Beschreibung - wer soll entscheiden, ob er es vergibt?")
                            .isNotNull();
                    assertThat(fields.get("default"))
                            .as(
                                    node
                                            + " hat kein ausdrueckliches default - dann entscheidet"
                                            + " Bukkit, und zwar anders als jemand erwartet")
                            .isNotNull();
                    assertThat(String.valueOf(fields.get("default")))
                            .as(node + ": nur true oder op sind gemeint")
                            .isIn("true", "false", "op", "not op");
                });
    }

    @Test
    @DisplayName("umgekehrt: kein deklarierter Knoten ist tot")
    void nodeclaredNodeIsUnused() throws IOException {
        // Die Gegenrichtung, und sie ist nicht bloss Kosmetik: ein Knoten, den niemand prueft,
        // sieht in der Rechteverwaltung aus wie eine Zusage. Ein Betreiber vergibt ihn, glaubt
        // etwas freigeschaltet zu haben, und nichts aendert sich.
        //
        // AUSNAHMEN: Knoten, deren Kommandos erst in US3 bis US8 gebaut werden. Sie stehen schon
        // in plugin.yml, weil der Baum als Ganzes eingetragen wurde (T047) - jeder gestrichene
        // Eintrag hier ist eine Aufgabe, die noch aussteht.
        Set<String> notYetBuilt =
                Set.of(
                        "rpg.admin.inspect.sheet",
                        "rpg.admin.inspect.statistics",
                        "rpg.admin.inspect.inventory",
                        "rpg.admin.inspect.session",
                        "rpg.admin.item.give",
                        "rpg.admin.mob.spawn",
                        "rpg.admin.set.class",
                        "rpg.admin.reload",
                        "rpg.admin.audit");

        Set<String> used = permissionsUsedInCode();
        Set<String> orphans = new LinkedHashSet<>(declaredPermissions());
        orphans.removeAll(used);
        orphans.removeAll(notYetBuilt);

        assertThat(orphans)
                .as("entweder benutzt sie jemand, oder sie gehoeren in die Ausnahmeliste oben")
                .isEmpty();
    }

    // --- Aufbau ---------------------------------------------------------------

    private static Set<String> permissionsUsedInCode() throws IOException {
        Set<String> found = new LinkedHashSet<>();
        try (var sources = Files.walk(SOURCES)) {
            for (Path path : sources.filter(p -> p.toString().endsWith(".java")).toList()) {
                Matcher matcher =
                        LOOKS_LIKE_A_PERMISSION.matcher(
                                Files.readString(path, StandardCharsets.UTF_8));
                while (matcher.find()) {
                    found.add(matcher.group(1));
                }
            }
        }
        return found;
    }

    private static Set<String> declaredPermissions() {
        return new LinkedHashSet<>(permissionsSection().keySet());
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> permissionsSection() {
        try (InputStream stream =
                DeclaredPermissionsGuardTest.class.getResourceAsStream("/plugin.yml")) {
            if (stream == null) {
                throw new IllegalStateException("plugin.yml ist nicht im Klassenpfad");
            }
            Map<String, Object> descriptor =
                    new Yaml().load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            Object permissions = descriptor.get("permissions");
            assertThat(permissions).as("plugin.yml hat keinen permissions:-Abschnitt").isNotNull();
            return (Map<String, Object>) permissions;
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }
}
