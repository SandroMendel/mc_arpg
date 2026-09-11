package rpg.core.item;

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
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.combat.DamageShare;
import rpg.core.combat.DeathCause;
import rpg.core.event.DefaultEventBus;
import rpg.core.progression.PartyRegistry;
import rpg.core.progression.ProximityCheck;
import rpg.core.progression.WorldPoint;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;

/**
 * Wem ein gefallener Gegenstand gehört — der ganze Entscheidungsweg (FR-019, FR-026 bis FR-026d).
 *
 * <p><b>Der Kern ist eine dokumentierte Abweichung von ADR-029.</b> Erfahrung und Coins teilen sich
 * nach Anteil; ein Gegenstand kann das nicht. B05 hat daraus die Entscheidung gegen Kill-Stealing
 * gemacht — <em>„loot goes to the largest contributor because a sword does not"</em> — und B11
 * ergänzt nur die Party, die B05 nicht kennt.
 */
class LootPlannerTest {

    private static final Logger QUIET = Logger.getLogger(LootPlannerTest.class.getName());
    private static final String KIND = "greenfields.rotling";
    private static final String ZONE = "greenfields";

    private final UUID tank = UUID.randomUUID();
    private final UUID damage = UUID.randomUUID();
    private final UUID healer = UUID.randomUUID();
    private final Map<UUID, UUID> characters = new HashMap<>();
    private final WorldPoint where = new WorldPoint(UUID.randomUUID(), 0, 64, 0);

    private PartyRegistry parties;
    private PartyLootRotation rotation;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        for (UUID player : List.of(tank, damage, healer)) {
            characters.put(player, UUID.randomUUID());
        }
        parties =
                new PartyRegistry(
                        new NoSessions(), new DefaultEventBus(QUIET), Clock.systemUTC(), 5,
                        Duration.ofSeconds(60));
        rotation = new PartyLootRotation();
    }

    @Nested
    @DisplayName("ohne Party")
    class Solo {

        @Test
        @DisplayName("SC-006 - die Beute geht an den GROESSTEN Beitragenden, nicht an den letzten Treffer")
        void lootGoesToTheLargestContributor() {
            // damage hat 70 % gemacht, tank hat den letzten Treffer gelandet. Ohne diese Regel
            // waere Kill-Stealing die guenstigste Spielweise (B05, DamageShare).
            Map<UUID, Double> shares = new LinkedHashMap<>();
            shares.put(damage, 0.7);
            shares.put(tank, 0.3);
            CombatDeathEvent death = death(shares, damage, tank);

            List<LootClaim> claims = planner(alwaysDrops()).plan(death, KIND, ZONE, false, where);

            assertThat(claims).hasSize(1);
            assertThat(claims.get(0).ownerCharacterId()).isEqualTo(characters.get(damage));
        }

        @Test
        @DisplayName("und die Beute teilt sich NICHT wie Erfahrung und Coins")
        void lootIsNotSplitLikeXp() {
            // Zwei Beitragende zu je 50 %. B06 und B08b machten daraus ZWEI Empfaenger; hier
            // bleibt es EINER. Das ist Absicht und keine vergessene Aufteilung - ein Gegenstand
            // teilt sich nicht (ADR-029, ADR-039).
            Map<UUID, Double> shares = new LinkedHashMap<>();
            shares.put(damage, 0.5);
            shares.put(tank, 0.5);

            List<LootClaim> claims =
                    planner(alwaysDrops()).plan(death(shares, damage, tank), KIND, ZONE, false, where);

            assertThat(claims)
                    .as("ein Posten, ein Eigentuemer - nicht zwei halbe Traenke")
                    .hasSize(1);
        }
    }

    @Nested
    @DisplayName("ohne Spielertoetung")
    class NoPlayerKill {

        @Test
        @DisplayName("FR-019 - ein leerer Schadensanteil laesst nichts fallen")
        void anEmptyShareDropsNothing() {
            // Sturz, Sonnenlicht, eine andere Kreatur, oder das Aufraeumen einer Zone. B10s
            // RemovalIsNotADeathTest haelt die letzte Haelfte davon fest.
            CombatDeathEvent death =
                    new CombatDeathEvent(
                            UUID.randomUUID(), null, null, DeathCause.ENVIRONMENT,
                            DamageShare.empty(), false);

            assertThat(planner(alwaysDrops()).plan(death, KIND, ZONE, false, where)).isEmpty();
        }

        @Test
        @DisplayName("der Tod eines SPIELERS laesst nichts fallen")
        void aPlayerDeathDropsNothing() {
            // Die Todesstrafe aus ADR-017 besteht aus Verschleiss, nicht aus Verlust (FR-046).
            CombatDeathEvent death =
                    new CombatDeathEvent(
                            tank,
                            characters.get(tank),
                            damage,
                            DeathCause.COMBAT,
                            new DamageShare(Map.of(damage, 1.0), damage, 10.0),
                            true);

            assertThat(planner(alwaysDrops()).plan(death, KIND, ZONE, false, where)).isEmpty();
        }

        @Test
        @DisplayName("ein Beitragender ohne Charakter bekommt nichts, statt umverteilt zu werden")
        void aContributorWithoutACharacterGetsNothing() {
            UUID stranger = UUID.randomUUID(); // kein Charakter hinterlegt

            List<LootClaim> claims =
                    planner(alwaysDrops())
                            .plan(
                                    death(Map.of(stranger, 1.0), stranger, stranger),
                                    KIND, ZONE, false, where);

            assertThat(claims)
                    .as("ihn dem naechsten zu geben waere eine stille Umverteilung")
                    .isEmpty();
        }
    }

    @Nested
    @DisplayName("in einer Party")
    class InAParty {

        @Test
        @DisplayName("FR-026c - wer ausser Reichweite steht, wird uebersprungen")
        void whoeverIsOutOfRangeIsSkipped() {
            formParty();
            // Nur tank und healer sind nah genug; damage ist weggelaufen.
            LootPlanner planner = planner(alwaysDrops(), inRangeOnly(tank, healer));

            List<LootClaim> claims =
                    planner.plan(death(Map.of(damage, 1.0), damage, damage), KIND, ZONE, false, where);

            assertThat(claims).hasSize(1);
            assertThat(claims.get(0).ownerCharacterId())
                    .as("der Abwesende bekommt nichts - gemessen zum gestorbenen Gegner, wie in B06")
                    .isIn(characters.get(tank), characters.get(healer));
        }

        @Test
        @DisplayName("FR-026c - ist NIEMAND in Reichweite, faellt es auf den groessten Beitragenden zurueck")
        void nobodyInRangeFallsBackToTheContributor() {
            formParty();
            LootPlanner planner = planner(alwaysDrops(), inRangeOnly());

            List<LootClaim> claims =
                    planner.plan(death(Map.of(damage, 1.0), damage, damage), KIND, ZONE, false, where);

            assertThat(claims).hasSize(1);
            assertThat(claims.get(0).ownerCharacterId())
                    .as("besser der Beitragende als gar niemand")
                    .isEqualTo(characters.get(damage));
        }

        @Test
        @DisplayName("drei Mitglieder in Reichweite bekommen ueber drei Posten je einen")
        void threeMembersInRangeGetOneEach() {
            formParty();
            LootPlanner planner = planner(alwaysDrops(), inRangeOnly(tank, damage, healer));

            List<UUID> owners = new java.util.ArrayList<>();
            for (int item = 0; item < 3; item++) {
                owners.add(
                        planner.plan(death(Map.of(damage, 1.0), damage, damage), KIND, ZONE, false, where)
                                .get(0)
                                .ownerCharacterId());
            }

            assertThat(owners)
                    .as("reihum - der Tank geht nicht leer aus (SC-006a)")
                    .containsExactlyInAnyOrder(
                            characters.get(tank), characters.get(damage), characters.get(healer));
        }
    }

    // --- Hilfsmittel -----------------------------------------------------------------

    private void formParty() {
        parties.create(damage);
        for (UUID member : List.of(tank, healer)) {
            parties.invite(damage, member);
            parties.accept(member);
        }
    }

    private LootPlanner planner(LootTables tables) {
        return planner(tables, inRangeOnly());
    }

    private LootPlanner planner(LootTables tables, ProximityCheck proximity) {
        ItemConfig config =
                new ItemConfig(
                        Map.of("potion.test", template()),
                        WearCurve.defaults(),
                        new RepairPricing(List.of(0L, 40L, 120L, 400L, 1200L, 3000L)),
                        tables,
                        Map.of());
        return new LootPlanner(
                () -> config,
                parties,
                () -> proximity,
                () -> 32.0,
                playerId -> Optional.ofNullable(characters.get(playerId)),
                rotation,
                always());
    }

    private static LootTables alwaysDrops() {
        return new LootTables(
                Map.of(KIND, new LootTable(List.of(LootEntry.single("potion.test", 1.0)))),
                Map.of(),
                Map.of());
    }

    /** Eine Reichweitenprüfung, die genau die genannten Spieler durchlässt. */
    private static ProximityCheck inRangeOnly(UUID... allowed) {
        List<UUID> permitted = List.of(allowed);
        return (origin, candidates, candidateCount, range, out) -> {
            int written = 0;
            for (int i = 0; i < candidateCount; i++) {
                if (permitted.contains(candidates[i])) {
                    out[written++] = candidates[i];
                }
            }
            return written;
        };
    }

    private CombatDeathEvent death(Map<UUID, Double> shares, UUID top, UUID lastHit) {
        return new CombatDeathEvent(
                UUID.randomUUID(), null, lastHit, DeathCause.COMBAT,
                new DamageShare(shares, top, 100.0), false);
    }

    private static ItemTemplate template() {
        return new ItemTemplate(
                "potion.test",
                ItemCategory.CONSUMABLE,
                "POTION",
                Rarity.COMMON,
                null,
                null,
                3L,
                null,
                ConsumableEffect.healing(40.0, Duration.ofSeconds(8)),
                null);
    }

    /** Ein Generator, bei dem jeder Eintrag trifft - die Auswahl soll hier nicht wackeln. */
    private static RandomGenerator always() {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public double nextDouble() {
                return 0.0;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }

    /** Eine Sitzungsverwaltung, die niemanden kennt - die Party braucht sie nur zum Nachschlagen. */
    private static final class NoSessions implements SessionRegistry {
        @Override
        public Optional<PlayerSession> find(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public PlayerSession require(UUID playerId) {
            throw new IllegalStateException("no session for " + playerId);
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
