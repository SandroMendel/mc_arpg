package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import org.bukkit.entity.EntityType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.world.WorldMock;

/**
 * Der Vermerk an der Kreatur — und die eine Ableitung, die daraus einen Schlüssel macht.
 *
 * <p>{@link MobKindTag#kindKeyOf} ist die Stelle, an der die Verwechslung behoben wird, die diesen
 * ganzen Schlüsselwechsel ausgelöst hat: mit {@code getType().name()} sind vier Arten auf {@code
 * ZOMBIE} vier Mal derselbe Schlüssel — dieselben Werte, dieselbe Erfahrung, dieselben Coins. Mit
 * dem Vermerk sind sie vier verschiedene.
 *
 * <p>Und die zweite Hälfte, die genauso wichtig ist: eine Kreatur <b>ohne</b> Vermerk antwortet
 * weiterhin mit ihrem Vanilla-Typnamen. Die Übergangsregelung aus B05 hört nicht auf zu
 * funktionieren — sie wird nur noch von dem bedient, was sie immer gemeint hat (FR-009).
 */
class MobKindTagTest {

    private ServerMock server;
    private WorldMock world;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        world = server.addSimpleWorld("world");
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("Art und Ursprungszone lassen sich schreiben und wieder lesen")
    void kindAndZoneAreWrittenAndReadBack() {
        var zombie = world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);

        MobKindTag.mark(zombie, "greenfields.rotling", "greenfields");

        assertThat(MobKindTag.kindOf(zombie)).hasValue("greenfields.rotling");
        assertThat(MobKindTag.zoneOf(zombie)).hasValue("greenfields");
        assertThat(MobKindTag.isOurs(zombie)).isTrue();
    }

    @Test
    @DisplayName("zwei Arten auf DERSELBEN Basis sind unterscheidbar")
    void twoKindsOnTheSameBaseAreDistinguishable() {
        // Der ganze Grund fuer den Vermerk. Ohne ihn waeren beide "ZOMBIE".
        var first = world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);
        var second = world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);

        MobKindTag.mark(first, "greenfields.rotling", "greenfields");
        MobKindTag.mark(second, "greenfields.brute", "greenfields");

        assertThat(MobKindTag.kindKeyOf(first)).isEqualTo("greenfields.rotling");
        assertThat(MobKindTag.kindKeyOf(second)).isEqualTo("greenfields.brute");
        assertThat(first.getType()).as("und ihre Basis ist trotzdem dieselbe").isEqualTo(second.getType());
    }

    @Test
    @DisplayName("ohne Vermerk faellt der Schluessel auf den Vanilla-Typnamen zurueck")
    void withoutAMarkTheKeyFallsBackToTheVanillaTypeName() {
        // FR-009: die vorhandenen combat.yml-Eintraege und die konfigurierten Standardwerte gelten
        // fuer eine ungekennzeichnete Kreatur unveraendert weiter.
        var zombie = world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);

        assertThat(MobKindTag.isOurs(zombie)).isFalse();
        assertThat(MobKindTag.kindKeyOf(zombie)).isEqualTo("ZOMBIE");
        assertThat(MobKindTag.kindOf(zombie)).isEmpty();
    }

    @Test
    @DisplayName("null ist kein Fehler - eine Kreatur kann zwischen Tod und Auswertung weg sein")
    void nullIsNotAnError() {
        // ProgressionDeathListener fragt nach einer Entitaet, die B05 gerade getoetet hat, und die
        // kann bereits entfernt sein. Ein Wurf hier waere ein Fehler in der Todesbehandlung.
        assertThat(MobKindTag.kindKeyOf(null)).isEqualTo("UNKNOWN");
        assertThat(MobKindTag.kindOf(null)).isEmpty();
        assertThat(MobKindTag.isOurs(null)).isFalse();
    }

    @Test
    @DisplayName("der Vermerk ueberschreibt sich, statt sich zu verdoppeln")
    void markingTwiceReplacesInsteadOfDuplicating() {
        var zombie = world.spawnEntity(world.getSpawnLocation(), EntityType.ZOMBIE);

        MobKindTag.mark(zombie, "a", "greenfields");
        MobKindTag.mark(zombie, "b", "dustlands");

        assertThat(MobKindTag.kindOf(zombie)).hasValue("b");
        assertThat(MobKindTag.zoneOf(zombie)).hasValue("dustlands");
    }
}
