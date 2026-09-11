package rpg.plugin.command.framework;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.message.MessageKey;
import rpg.plugin.command.admin.RpgRootCommand;

/**
 * T014 — <b>ein Knoten ist entweder Verzweigung oder Blatt, nie beides und nie keins.</b>
 *
 * <p>Die Aufgabe verlangt die Regel „in {@code CommandTree}". Sie steht stattdessen im Konstruktor
 * von {@link RpgCommand}, und diese Tests sind der Grund, warum das besser ist: eine Prüfung beim
 * Bauen des Brigadier-Baums fängt einen falschen Knoten erst, wenn jemand ihn zu registrieren
 * versucht — hier lässt er sich <b>gar nicht erst herstellen</b>. Der Zustand „halb ausgeführt" ist
 * damit nicht unwahrscheinlich, sondern unmöglich.
 */
class CommandShapeTest {

    private static final MessageKey ANY = MessageKey.of("command.rpg.description");

    @Test
    @DisplayName("eine Verzweigung DARF eigene Formen haben - der Fall /coins")
    void abranchMayHaveItsOwnForms() {
        // Diese Regel lautete zuerst „eine Verzweigung hat KEINE eigene Ausfuehrung". Dann kam
        // /coins: Unterkommandos (set|add|remove) UND eigene Formen (/coins, /coins <spieler>),
        // und FR-005 laesst alle drei unveraendert. Die Regel war strenger als der Vertrag und
        // strenger als Brigadier, das die Form beherrscht.
        RpgCommand child = RpgCommand.leaf("set", ANY, null, List.of(), context -> {});
        Argument<Long> amount = Argument.optional("amount", Arguments.amount(1, 10));

        assertThatCode(
                        () ->
                                RpgCommand.branchWithOwnForms(
                                        "parent",
                                        ANY,
                                        null,
                                        List.of(child),
                                        List.of(amount),
                                        false,
                                        context -> {}))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("Argumente OHNE Ausfuehrung, die sie liest, sind nicht erreichbar")
    void argumentsWithoutAnActionAreRefused() {
        // Was von der alten Regel uebrig ist: kein Knoten, der weder ausfuehren noch
        // weiterverzweigen kann - und keine Argumente, die niemand liest.
        RpgCommand child = RpgCommand.leaf("set", ANY, null, List.of(), context -> {});
        Argument<Long> amount = Argument.required("amount", Arguments.amount(1, 10));

        assertThatThrownBy(
                        () ->
                                new RpgCommand(
                                        "parent",
                                        ANY,
                                        null,
                                        List.of(child),
                                        List.of(amount),
                                        false,
                                        null,
                                        null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("parent");
    }

    @Test
    @DisplayName("ein Blatt ohne Ausfuehrung laesst sich nicht bauen - es koennte nur schweigen")
    void aleafWithoutAnActionIsRefused() {
        assertThatThrownBy(
                        () ->
                                new RpgCommand(
                                        "leaf", ANY, null, List.of(), List.of(), false, null, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("ein Pflichtargument hinter einem optionalen ist nicht erreichbar")
    void arequiredArgumentBehindAnOptionalIsRefused() {
        // Wer das optionale weglaesst, hat das Pflichtargument an dessen Stelle getippt - und
        // niemand kann die zwei Faelle auseinanderhalten.
        Argument<Long> optional = Argument.optional("first", Arguments.amount(1, 10));
        Argument<Long> required = Argument.required("second", Arguments.amount(1, 10));

        assertThatThrownBy(
                        () ->
                                RpgCommand.leaf(
                                        "leaf",
                                        ANY,
                                        null,
                                        List.of(optional, required),
                                        context -> {}))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("second");
    }

    @Test
    @DisplayName("die gueltigen Formen gehen")
    void thevalidShapesAreAccepted() {
        Argument<Long> required = Argument.required("first", Arguments.amount(1, 10));
        Argument<Long> optional = Argument.optional("second", Arguments.amount(1, 10));

        assertThatCode(
                        () -> {
                            RpgCommand leaf =
                                    RpgCommand.leaf(
                                            "leaf",
                                            ANY,
                                            null,
                                            List.of(required, optional),
                                            context -> {});
                            RpgCommand.branch("branch", ANY, null, List.of(leaf));
                        })
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ein Argument ohne Namen kann keine Meldung nennen")
    void anargumentNeedsAName() {
        assertThatThrownBy(() -> Argument.required("  ", Arguments.amount(1, 10)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("/rpg ohne Unterkommandos wird GAR NICHT registriert")
    void therootWithoutChildrenIsNotRegistered() {
        // Ein /rpg, unter dem nichts haengt, wuerde auf jede Eingabe "unvollstaendig" antworten:
        // es stuende in der Vervollstaendigung, naehme Platz und taete nie etwas.
        assertThat(RpgRootCommand.of(List.of())).isEmpty();
        assertThat(RpgRootCommand.of(null)).isEmpty();
    }

    @Test
    @DisplayName("/rpg mit einem Unterkommando ist eine Verzweigung ohne eigenes Recht")
    void therootIsABranchWithoutItsOwnPermission() {
        RpgCommand child =
                RpgCommand.leaf("reload", ANY, "rpg.admin.reload", List.of(), context -> {});

        Optional<RpgCommand> root = RpgRootCommand.of(List.of(child));

        assertThat(root).isPresent();
        assertThat(root.get().isBranch()).isTrue();
        assertThat(root.get().permissionOrNone())
                .as("ein Recht an der Wurzel waere eine zweite Huerde vor derselben Tuer")
                .isEmpty();
    }
}
