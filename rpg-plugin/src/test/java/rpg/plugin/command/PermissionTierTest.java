package rpg.plugin.command;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

/**
 * T053 — <b>ein frisch aufgesetzter Server ist brauchbar und sicher zugleich</b> (FR-012, FR-013,
 * SC-007).
 *
 * <p>Kein Rechte-Plugin, niemand hat etwas vergeben: die fünf Spielerkommandos gehen sofort, kein
 * einziges Admin-Kommando geht. <b>Beides zusammen ist die Zusage</b> — jede Hälfte allein ist
 * leicht: alles verbieten ist sicher und unbrauchbar, alles erlauben ist brauchbar und offen.
 *
 * <h2>Geprüft wird der Deskriptor, nicht ein laufender Server</h2>
 *
 * <p>Weil genau er es entscheidet. Ohne Rechte-Plugin gilt für jeden Knoten sein {@code default},
 * und das steht hier. Ein Test gegen einen laufenden Server prüfte dieselbe Zeile über drei
 * Umwege — und wäre still grün, wenn die Testumgebung ihre eigenen Voreinstellungen mitbrächte.
 */
class PermissionTierTest {

    /** Was ein Spieler auf einem frischen Server können muss. */
    private static final List<String> PLAYER_TIER =
            List.of(
                    "rpg.ui.character",
                    "rpg.statistics.own",
                    "rpg.statistics.top",
                    "rpg.currency.balance",
                    "rpg.item.trash");

    /** Was er auf keinen Fall können darf — lesend wie schreibend. */
    private static final List<String> OPERATOR_TIER =
            List.of(
                    "rpg.currency.admin",
                    "rpg.progression.admin",
                    "rpg.admin.no-class",
                    "rpg.admin.inspect.sheet",
                    "rpg.admin.inspect.statistics",
                    "rpg.admin.inspect.inventory",
                    "rpg.admin.inspect.session",
                    "rpg.admin.item.give",
                    "rpg.admin.mob.spawn",
                    "rpg.admin.set.class",
                    "rpg.admin.reload",
                    "rpg.admin.audit");

    @Test
    @DisplayName("SC-007: die fuenf Spielerkommandos gehen ohne dass jemand etwas vergibt")
    void thefivePlayerCommandsWorkOutOfTheBox() {
        Map<String, Object> permissions = permissions();

        for (String node : PLAYER_TIER) {
            assertThat(permissions).as(node + " fehlt im Deskriptor").containsKey(node);
            assertThat(defaultOf(permissions, node))
                    .as(
                            node
                                    + " steht nicht auf true - dann waere es auf jedem frisch"
                                    + " aufgesetzten Server eine stumme Funktion")
                    .isEqualTo("true");
        }
    }

    @Test
    @DisplayName("SC-007: und KEIN Admin- oder Moderatorrecht geht dabei mit")
    void nooperatorPermissionComesAlong() {
        Map<String, Object> permissions = permissions();

        for (String node : OPERATOR_TIER) {
            assertThat(permissions).as(node + " fehlt im Deskriptor").containsKey(node);
            assertThat(defaultOf(permissions, node))
                    .as(node + " steht auf " + defaultOf(permissions, node) + " statt op")
                    .isEqualTo("op");
        }
    }

    @Test
    @DisplayName("die zwei Stufen ueberschneiden sich nicht - und decken alles ab")
    void thetwoTiersAreCompleteAndDisjoint() {
        // Ohne diese Pruefung koennte ein neuer Knoten in KEINER der beiden Listen stehen, und
        // die zwei Tests oben waeren gruen, ohne ihn je angesehen zu haben.
        assertThat(PLAYER_TIER).doesNotContainAnyElementsOf(OPERATOR_TIER);

        assertThat(permissions().keySet())
                .as("ein neuer Knoten gehoert in PLAYER_TIER oder OPERATOR_TIER")
                .containsExactlyInAnyOrderElementsOf(
                        java.util.stream.Stream.concat(PLAYER_TIER.stream(), OPERATOR_TIER.stream())
                                .toList());
    }

    @Test
    @DisplayName("rpg.item.trash bleibt bei true - sonst ist ein volles Inventar eine Sackgasse")
    void trashStaysOpenToEveryone() {
        // Steht eigens da, weil es aussieht wie ein Versehen: ein Kommando, das etwas VERNICHTET,
        // und trotzdem default: true. ADR-018 schaltet das Fallenlassen ab, also ist /trash der
        // einzige Weg, etwas Unverkaeufliches loszuwerden.
        assertThat(defaultOf(permissions(), "rpg.item.trash")).isEqualTo("true");
    }

    // --- Aufbau ---------------------------------------------------------------

    private static String defaultOf(Map<String, Object> permissions, String node) {
        return String.valueOf(((Map<?, ?>) permissions.get(node)).get("default"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> permissions() {
        try (InputStream stream = PermissionTierTest.class.getResourceAsStream("/plugin.yml")) {
            if (stream == null) {
                throw new IllegalStateException("plugin.yml ist nicht im Klassenpfad");
            }
            Map<String, Object> descriptor =
                    new Yaml().load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
            return (Map<String, Object>) descriptor.get("permissions");
        } catch (IOException unreadable) {
            throw new IllegalStateException(unreadable);
        }
    }
}
