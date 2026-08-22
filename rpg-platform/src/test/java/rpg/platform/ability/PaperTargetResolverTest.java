package rpg.platform.ability;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.ability.TargetMode;
import rpg.core.ability.TargetSpec;

/**
 * T099, T101 und T110 - die Zielwahl am echten räumlichen Index (FR-019 bis FR-023, SC-007).
 *
 * <p>Zwei Zusagen tragen hier alles andere: <b>die Obergrenze hält</b>, egal wie voll es ist, und
 * <b>bei mehr Kandidaten als erlaubt gewinnen die nächsten</b>. Die zweite ist nicht Kosmetik: ohne
 * sie liefert dieselbe Lage zweimal ein anderes Ergebnis, und dann lässt sich über Balancing nicht
 * mehr reden.
 */
class PaperTargetResolverTest {

    private ServerMock server;
    private World world;
    private PlayerMock caster;
    private PaperTargetResolver resolver;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        MockBukkit.createMockPlugin("TargetProbe");
        world = server.addSimpleWorld("world");
        caster = server.addPlayer();
        caster.teleport(new Location(world, 0.0, 64.0, 0.0));
        resolver = new PaperTargetResolver(server, (attacker, target) -> true);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Nested
    @DisplayName("SC-007 - die Obergrenze hält")
    class TheCapHolds {

        @Test
        @DisplayName("zweihundert Kandidaten, acht Ziele")
        void twoHundredCandidatesYieldEight() {
            for (int i = 0; i < 200; i++) {
                spawnAt(0.5 + i * 0.02, 0.0);
            }

            List<UUID> targets = resolver.resolve(caster.getUniqueId(), radius(20.0, 8));

            assertThat(targets).hasSize(8);
        }

        @Test
        @DisplayName("weniger Kandidaten als erlaubt heißt weniger Ziele, nicht Auffüllen")
        void fewerCandidatesMeansFewerTargets() {
            spawnAt(1.0, 0.0);
            spawnAt(2.0, 0.0);

            assertThat(resolver.resolve(caster.getUniqueId(), radius(20.0, 8))).hasSize(2);
        }

        @Test
        @DisplayName("der Auslöser ist nie sein eigenes Ziel")
        void theCasterIsNeverItsOwnTarget() {
            spawnAt(1.0, 0.0);

            assertThat(resolver.resolve(caster.getUniqueId(), radius(20.0, 8)))
                    .doesNotContain(caster.getUniqueId());
        }

        @Test
        @DisplayName("außerhalb der Reichweite zählt nicht mit, auch wenn der Suchkasten es fasst")
        void beyondTheRangeDoesNotCount() {
            // Der Kasten ist quadratisch, der Radius rund: diagonal liegt etwas im Kasten und
            // trotzdem zu weit weg. Ohne die Abstandsprüfung träfe eine Fähigkeit um die Ecke.
            spawnAt(4.0, 4.0);

            assertThat(resolver.resolve(caster.getUniqueId(), radius(5.0, 8))).isEmpty();
        }
    }

    @Nested
    @DisplayName("FR-021 - die nächsten gewinnen")
    class NearestFirst {

        @Test
        @DisplayName("bei mehr Kandidaten als erlaubt bleiben die drei nächsten übrig")
        void theThreeNearestSurvive() {
            LivingEntity near = spawnAt(1.0, 0.0);
            LivingEntity middle = spawnAt(2.0, 0.0);
            LivingEntity alsoNear = spawnAt(3.0, 0.0);
            spawnAt(9.0, 0.0);
            spawnAt(10.0, 0.0);

            List<UUID> targets = resolver.resolve(caster.getUniqueId(), radius(20.0, 3));

            assertThat(targets)
                    .containsExactly(
                            near.getUniqueId(), middle.getUniqueId(), alsoNear.getUniqueId());
        }

        @Test
        @DisplayName("dieselbe Lage liefert zweimal dasselbe Ergebnis")
        void theSameSituationGivesTheSameAnswer() {
            for (int i = 0; i < 20; i++) {
                spawnAt(1.0 + i * 0.5, 0.0);
            }
            TargetSpec spec = radius(20.0, 4);

            assertThat(resolver.resolve(caster.getUniqueId(), spec))
                    .isEqualTo(resolver.resolve(caster.getUniqueId(), spec));
        }
    }

    @Nested
    @DisplayName("FR-023 - wen der Auslöser nicht angreifen darf, nennt der Resolver nicht")
    class Permission {

        @Test
        @DisplayName("eine ablehnende Erlaubnis lässt nichts übrig")
        void aRefusingPermissionLeavesNothing() {
            spawnAt(1.0, 0.0);
            spawnAt(2.0, 0.0);
            PaperTargetResolver strict =
                    new PaperTargetResolver(server, (attacker, target) -> false);

            assertThat(strict.resolve(caster.getUniqueId(), radius(20.0, 8))).isEmpty();
        }
    }

    @Nested
    @DisplayName("SELF und die Randfälle")
    class Edges {

        @Test
        @DisplayName("SELF ist immer genau der Auslöser, auch ohne irgendwen in Reichweite")
        void selfIsAlwaysTheCaster() {
            assertThat(resolver.resolve(caster.getUniqueId(), new TargetSpec(
                            TargetMode.SELF, 0.0, null, 1, null, null)))
                    .containsExactly(caster.getUniqueId());
        }

        @Test
        @DisplayName("ein Auslöser, den es nicht mehr gibt, liefert nichts statt zu scheitern")
        void aVanishedCasterYieldsNothing() {
            assertThat(resolver.resolve(UUID.randomUUID(), radius(20.0, 8))).isEmpty();
        }
    }

    // --- helpers ---

    private LivingEntity spawnAt(double x, double z) {
        return (LivingEntity)
                world.spawnEntity(new Location(world, x, 64.0, z), EntityType.ZOMBIE);
    }

    private static TargetSpec radius(double range, int maxTargets) {
        return new TargetSpec(TargetMode.RADIUS, range, null, maxTargets, null, null);
    }
}
