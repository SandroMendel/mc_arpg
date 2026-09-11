package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.LadderSlot;
import rpg.core.combat.DamageOrigin;
import rpg.core.combat.DeathCause;
import rpg.core.event.DefaultEventBus;

/**
 * SC-013, FR-043, FR-044 — <b>der Tod kostet ein Vielfaches eines Kampfes.</b>
 *
 * <p>Die Ansage war ausdrücklich: <em>„Der Tod soll aber signifikant mehr Haltbarkeit verringern als
 * erlittener und ausgeteilter Schaden."</em> Das ist die Todesstrafe aus ADR-017 — ohne Itemverlust
 * und ohne Erfahrungsverlust bleibt der Verschleiß das Einzige, was ein Tod kostet.
 *
 * <p><b>Und es ist eine Regel, keine Zahlenwahl.</b> {@code WearCurve.validate} weist eine
 * Konfiguration zurück, in der die Ordnung nicht mehr stimmt (FR-044). Ohne diese Prüfung könnte ein
 * späteres Balancing die Strafe stillschweigend aushebeln: alles liefe weiter, nur der Tod täte
 * nicht mehr weh — und niemand hätte einen Anlass, danach zu suchen.
 */
class DeathCostsMoreThanAFightTest {

    private final UUID character = UUID.randomUUID();

    @Test
    @DisplayName("SC-013: ein Tod kostet mehr als ein ganzer gewoehnlicher Kampf")
    void adeathCostsMoreThanAWholeOrdinaryFight() {
        // Ein gewoehnlicher Kampf, grosszuegig gerechnet: zwanzig Treffer eingesteckt und zwanzig
        // ausgeteilt, jeder zu 40 Schadenspunkten. Das ist mehr, als die meisten Kaempfe hergeben.
        DefaultGearConditions fighting = conditions();
        for (int hit = 0; hit < 20; hit++) {
            fighting.onDamageTaken(character, DamageOrigin.MELEE, false, 40.0);
            fighting.onDamageDealt(character, DamageOrigin.MELEE, false, 40.0);
        }
        double armorLostFighting = WearCurve.FULL - fighting.conditionOf(character, LadderSlot.ARMOR);

        DefaultGearConditions dying = conditions();
        dying.onDeath(character, DeathCause.COMBAT);
        double armorLostDying = WearCurve.FULL - dying.conditionOf(character, LadderSlot.ARMOR);

        assertThat(armorLostDying)
                .as("ein einziger Tod gegen einen ganzen Kampf - und der Tod muss deutlich mehr sein")
                .isGreaterThan(armorLostFighting);
    }

    @Test
    @DisplayName("und er trifft BEIDE Leitern, nicht nur die, die gerade benutzt wurde")
    void adeathWearsBothLadders() {
        DefaultGearConditions conditions = conditions();

        conditions.onDeath(character, DeathCause.COMBAT);

        assertThat(conditions.conditionOf(character, LadderSlot.ARMOR)).isLessThan(WearCurve.FULL);
        assertThat(conditions.conditionOf(character, LadderSlot.WEAPON)).isLessThan(WearCurve.FULL);
    }

    @Test
    @DisplayName("ein /kill kostet gar nichts - eine Betreiberhandlung ist keine Niederlage")
    void anadminKillCostsNothing() {
        DefaultGearConditions conditions = conditions();

        conditions.onDeath(character, DeathCause.ADMIN);

        assertThat(conditions.conditionOf(character, LadderSlot.ARMOR)).isEqualTo(WearCurve.FULL);
        assertThat(conditions.conditionOf(character, LadderSlot.WEAPON)).isEqualTo(WearCurve.FULL);
    }

    @Test
    @DisplayName("FR-044: eine Konfiguration, in der der Tod NICHT schwerer wiegt, bricht den Start ab")
    void aconfigurationThatUndoesThePenaltyAbortsTheStart() {
        // Genau der Fall, den ein Balancing-Durchgang aus Versehen erzeugt: die Schadensraten
        // hochgedreht, den Todesbetrag vergessen. Von aussen unsichtbar - alles laeuft weiter.
        WearCurve toothless =
                new WearCurve(
                        50.0,
                        0.20,
                        0.5,
                        0.5,
                        1.0,
                        100.0,
                        List.of(50.0, 25.0, 10.0),
                        Duration.ofSeconds(60));

        assertThatThrownBy(() -> toothless.validate("items.yml wear"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-death")
                .hasMessageContaining("death-factor-min");
    }

    @Test
    @DisplayName("und die ausgelieferten Vorgabewerte halten die Ordnung ein")
    void theshippedDefaultsKeepTheOrder() {
        WearCurve defaults = WearCurve.defaults();

        assertThat(defaults.perDeath())
                .as("sonst waere die Vorgabe selbst der erste Verstoss")
                .isGreaterThanOrEqualTo(
                        Math.max(defaults.perDamageTaken(), defaults.perDamageDealt())
                                * defaults.deathFactorMin());
        defaults.validate("items.yml wear");
    }

    private DefaultGearConditions conditions() {
        DefaultGearConditions conditions =
                new DefaultGearConditions(
                        WearCurve::defaults,
                        new NoRepository(),
                        new DefaultEventBus(java.util.logging.Logger.getLogger("quiet")),
                        Clock.systemUTC());
        conditions.put(GearCondition.full(character));
        return conditions;
    }

    private static final class NoRepository implements GearConditionRepository {

        @Override
        public CompletableFuture<Optional<GearCondition>> find(UUID characterId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void markDirty(UUID characterId) {}
    }
}
