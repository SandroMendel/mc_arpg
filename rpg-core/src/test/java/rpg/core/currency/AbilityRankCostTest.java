package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.ability.Ability;
import rpg.core.ability.TargetSpec;
import rpg.core.classes.AbilityKind;
import rpg.core.message.MessageKey;

/**
 * T108 - der Rangaufstieg kostet Coins (US5 Szenarien 1 bis 4, FR-051 bis FR-054).
 *
 * <p>Geprueft wird das Stueck, das B08b beisteuert: die Preisauskunft und die Abbuchung. Dass die
 * Pruefung <b>zuletzt</b> laeuft - nach Freischaltung und Hoechstrang - gehoert zu B08 und steht in
 * {@code AbilityRankTest}; hier zaehlt, dass ein abgelehnter Kauf nichts abbucht.
 */
class AbilityRankCostTest {

    private static final Logger QUIET = Logger.getLogger("ability-rank-cost-test");

    private final UUID character = UUID.randomUUID();
    private CurrencyFixture.Harness harness;
    private AbilityRankCost rankCost;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        harness = CurrencyFixture.loadedWith(character, 1000L);
        rankCost = new AbilityRankCost(harness.currency, QUIET);
    }

    @Test
    @DisplayName("ein Rang mit Preis wird bezahlt und der Aufstieg zugelassen")
    void apricedRankIsPaidFor() {
        assertThat(rankCost.charge(character, ability(Map.of("coins", 250)))).isTrue();

        assertThat(harness.currency.balanceOf(character)).hasValue(750L);
        assertThat(harness.ledger.forCharacter(character))
                .singleElement()
                .satisfies(
                        entry -> {
                            assertThat(entry.reason()).isEqualTo(BookingReason.ABILITY_RANK);
                            assertThat(entry.direction()).isEqualTo(LedgerEntry.Direction.DEBIT);
                            assertThat(entry.amount()).isEqualTo(250L);
                        });
    }

    @Test
    @DisplayName("zu wenig Coins verweigert den Aufstieg und bucht NICHTS ab (US5 Szenario 1)")
    void tooFewCoinsRefuseAndChargeNothing() {
        assertThat(rankCost.charge(character, ability(Map.of("coins", 5000)))).isFalse();

        assertThat(harness.currency.balanceOf(character))
                .as("ein Spieler, der fuer nichts bezahlt hat, ist das schlimmste Ergebnis hier")
                .hasValue(1000L);
        assertThat(harness.ledger.entries).isEmpty();
    }

    @Test
    @DisplayName("eine Faehigkeit ohne rank-cost steigt kostenlos auf (FR-054)")
    void anAbilityWithoutARankCostIsFree() {
        assertThat(rankCost.charge(character, ability(Map.of()))).isTrue();

        assertThat(harness.currency.balanceOf(character)).hasValue(1000L);
        assertThat(harness.ledger.entries)
                .as("kostenlos heisst auch: kein Eintrag ueber null")
                .isEmpty();
    }

    @Test
    @DisplayName("ein unlesbarer Preis verweigert lieber, als einen geratenen Betrag abzubuchen")
    void anUnreadablePriceRefuses() {
        assertThat(rankCost.charge(character, ability(Map.of("shards", 7)))).isFalse();

        assertThat(harness.currency.balanceOf(character)).hasValue(1000L);
    }

    @Test
    @DisplayName("genau der Stand reicht, einer mehr nicht")
    void exactlyTheBalanceIsEnough() {
        assertThat(rankCost.charge(character, ability(Map.of("coins", 1001)))).isFalse();
        assertThat(rankCost.charge(character, ability(Map.of("coins", 1000)))).isTrue();
        assertThat(harness.currency.balanceOf(character)).hasValue(0L);
    }

    /** Irgendein gueltiger Effekt: eine Faehigkeit ohne einen ist immer ein Fehler. */
    private static rpg.core.ability.EffectSpec anEffect() {
        return new rpg.core.ability.EffectSpec(
                rpg.core.ability.EffectType.DAMAGE,
                1.0,
                0.0,
                null,
                null,
                1,
                null,
                null,
                rpg.core.combat.DamageType.PHYSICAL,
                null,
                null,
                null,
                null,
                false);
    }

    private static Ability ability(Map<String, Object> rankCost) {
        return new Ability(
                "probe.strike",
                AbilityKind.ACTIVE,
                MessageKey.of("ability.probe-strike.name"),
                0.0,
                Duration.ZERO,
                Duration.ZERO,
                false,
                Duration.ZERO,
                1,
                // Kein Ladefenster bei einer einzigen Ladung - das bedeutete nichts.
                null,
                false,
                false,
                false,
                false,
                java.util.Set.of(),
                1.0,
                TargetSpec.self(),
                List.of(anEffect()),
                5,
                rankCost,
                // Eine aktive Faehigkeit braucht genau einen Gegenstand - er ist der Slot, den der
                // Spieler anklickt.
                List.of("STICK"));
    }
}
