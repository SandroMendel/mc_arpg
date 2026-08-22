package rpg.core.currency;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.combat.DamageShare;
import rpg.core.combat.DeathCause;
import rpg.core.event.DefaultEventBus;
import rpg.core.progression.PartyRegistry;
import rpg.core.progression.ShareCalculator;
import rpg.core.progression.WorldPoint;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;

/**
 * T049 - aus einem Tod werden Haufen (US2 Szenarien 1, 3, 4; FR-019 bis FR-026, FR-031).
 *
 * <p>Der Test bleibt bukkitfrei: geprueft wird, <b>wer worauf Anspruch hat</b>, nicht was daraus in
 * der Welt wird. Genau diese Trennung macht die Anspruchsregel ohne Server pruefbar.
 *
 * <p>Die Doubles stehen hier statt in einem geliehenen Hilfsmittel aus B06. Ein fremdes
 * Test-Hilfsmittel oeffentlich zu machen, damit ein anderer Block es benutzen kann, waere derselbe
 * Griff in fremde Interna, den Prinzip III fuer Produktionscode verbietet.
 */
class CoinDropPlannerTest {

    private static final String ZOMBIE = "ZOMBIE";
    private static final String UNKNOWN = "WAS_MOJANG_LETZTE_WOCHE_ERGAENZTE";

    private final UUID alicePlayer = UUID.randomUUID();
    private final UUID aliceCharacter = UUID.randomUUID();
    private final UUID bobPlayer = UUID.randomUUID();
    private final UUID bobCharacter = UUID.randomUUID();
    private final UUID mob = UUID.randomUUID();

    private final Map<UUID, UUID> characters = new HashMap<>();
    private CoinDropPlanner planner;
    private WorldPoint origin;

    @BeforeEach
    void setUp() {
        characters.put(alicePlayer, aliceCharacter);
        characters.put(bobPlayer, bobCharacter);
        origin = new WorldPoint(UUID.randomUUID(), 10, 64, 10);
        planner = plannerWith(CurrencyFixture.config());
    }

    @Test
    @DisplayName("ein Alleinkill ergibt genau EINEN Plan ueber den konfigurierten Betrag")
    void singleKillYieldsOnePlan() {
        List<CoinDropPlan> plans = planner.planFor(death(Map.of(alicePlayer, 1.0)), ZOMBIE, origin);

        assertThat(plans)
                .singleElement()
                .satisfies(
                        plan -> {
                            assertThat(plan.characterId()).isEqualTo(aliceCharacter);
                            assertThat(plan.amount()).isEqualTo(5L);
                            assertThat(plan.origin()).isEqualTo(origin);
                        });
    }

    @Test
    @DisplayName("eine Kreatur ohne eigenen Eintrag laesst den Standardbetrag fallen, nicht null")
    void unknownCreatureUsesTheDefault() {
        List<CoinDropPlan> plans = planner.planFor(death(Map.of(alicePlayer, 1.0)), UNKNOWN, origin);

        assertThat(plans)
                .as("nicht stillschweigend wertlos (FR-023)")
                .singleElement()
                .extracting(CoinDropPlan::amount)
                .isEqualTo(4L);
    }

    @Test
    @DisplayName("zwei Beitragende ergeben je EINEN Haufen ueber ihren Anteil (FR-026)")
    void twoContributorsTwoPiles() {
        Map<UUID, Double> shares = new LinkedHashMap<>();
        shares.put(alicePlayer, 0.6);
        shares.put(bobPlayer, 0.4);

        List<CoinDropPlan> plans = planner.planFor(death(shares), "CREEPER", origin);

        assertThat(plans)
                .as("ein Haufen je Berechtigtem - niemals einer je Coin")
                .hasSize(2)
                .extracting(CoinDropPlan::characterId)
                .containsExactlyInAnyOrder(aliceCharacter, bobCharacter);
        assertThat(plans)
                .extracting(CoinDropPlan::amount)
                .as("8 Coins, 60/40 anteilig, abgerundet")
                .containsExactlyInAnyOrder(4L, 3L);
    }

    @Test
    @DisplayName("20 % Beteiligung ergeben einen Haufen, nicht nichts (FR-024a)")
    void twentyPercentStillDrops() {
        Map<UUID, Double> shares = new LinkedHashMap<>();
        shares.put(alicePlayer, 0.8);
        shares.put(bobPlayer, 0.2);

        List<CoinDropPlan> plans = planner.planFor(death(shares), "CREEPER", origin);

        assertThat(plans)
                .as("eine Mindestbeteiligung gibt es nicht")
                .extracting(CoinDropPlan::characterId)
                .contains(bobCharacter);
    }

    @Test
    @DisplayName("ein Kill ohne Beitragende laesst nichts fallen (FR-031)")
    void environmentalDeathDropsNothing() {
        assertThat(planner.planFor(death(Map.of()), ZOMBIE, origin))
                .as("in Lava verbrannt ist ein Normalfall, kein Fehler")
                .isEmpty();
    }

    @Test
    @DisplayName("der Tod eines Spielers laesst nichts fallen")
    void playerDeathDropsNothing() {
        CombatDeathEvent playerDeath =
                new CombatDeathEvent(
                        alicePlayer,
                        aliceCharacter,
                        bobPlayer,
                        DeathCause.COMBAT,
                        new DamageShare(Map.of(bobPlayer, 1.0), bobPlayer, 10.0),
                        true);

        assertThat(planner.planFor(playerDeath, ZOMBIE, origin)).isEmpty();
    }

    @Test
    @DisplayName("ohne Ort faellt nichts - besser nichts als ein Haufen im Weltursprung")
    void withoutAPlaceNothingFalls() {
        assertThat(planner.planFor(death(Map.of(alicePlayer, 1.0)), ZOMBIE, null)).isEmpty();
    }

    @Test
    @DisplayName("ein Beitragender ohne Charakter verfaellt, statt umverteilt zu werden")
    void aShareWithoutACharacterLapses() {
        UUID strangerPlayer = UUID.randomUUID();
        Map<UUID, Double> shares = new LinkedHashMap<>();
        shares.put(alicePlayer, 0.5);
        shares.put(strangerPlayer, 0.5);

        List<CoinDropPlan> plans = planner.planFor(death(shares), "CREEPER", origin);

        assertThat(plans)
                .as("umverteilen machte eine Gruppe mit abgemeldetem Mitglied staerker")
                .singleElement()
                .extracting(CoinDropPlan::characterId)
                .isEqualTo(aliceCharacter);
        assertThat(plans.get(0).amount()).isEqualTo(4L);
    }

    @Test
    @DisplayName("eine ausdrueckliche Null im Ertrag laesst nichts fallen - das ist gewaehlt")
    void anExplicitZeroDropsNothing() {
        CurrencyConfig zeroed =
                new CurrencyConfig(
                        0L,
                        4L,
                        Map.of(ZOMBIE, 0L),
                        Duration.ofSeconds(120),
                        3.0d,
                        400,
                        Duration.ofDays(30),
                        45);

        assertThat(plannerWith(zeroed).planFor(death(Map.of(alicePlayer, 1.0)), ZOMBIE, origin))
                .isEmpty();
    }

    // --- Hilfsmittel -----------------------------------------------------

    private CoinDropPlanner plannerWith(CurrencyConfig config) {
        Logger quiet = Logger.getLogger(CoinDropPlannerTest.class.getName());
        quiet.setLevel(Level.OFF);
        PartyRegistry parties =
                new PartyRegistry(
                        new NoSessions(),
                        new DefaultEventBus(quiet),
                        Clock.systemUTC(),
                        5,
                        Duration.ofSeconds(60));
        ShareCalculator shares =
                new ShareCalculator(parties, ProgressionConfigStub.forParties(), () -> null);
        return new CoinDropPlanner(
                shares,
                new ConfigMobCoinProvider(config, quiet),
                config,
                holderId -> Optional.ofNullable(characters.get(holderId)));
    }

    private CombatDeathEvent death(Map<UUID, Double> shares) {
        UUID top = shares.isEmpty() ? null : shares.keySet().iterator().next();
        return new CombatDeathEvent(
                mob,
                null,
                top,
                shares.isEmpty() ? DeathCause.ENVIRONMENT : DeathCause.COMBAT,
                new DamageShare(shares, top, 100.0),
                false);
    }

    /** Niemand ist in einer Party, also kommt der Rechner nie ueber den Einzelfall hinaus. */
    private static final class NoSessions implements SessionRegistry {

        @Override
        public Optional<PlayerSession> find(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public PlayerSession require(UUID playerId) {
            throw new UnsupportedOperationException("not needed in these tests");
        }

        @Override
        public boolean isReady(UUID playerId) {
            return true;
        }

        @Override
        public int activeSessionCount() {
            return 0;
        }
    }
}
