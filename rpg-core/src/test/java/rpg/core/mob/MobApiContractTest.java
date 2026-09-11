package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.message.MessageKey;
import rpg.core.stats.Attribute;

/**
 * Der Vertrag der beiden öffentlichen Abfragen (contracts/mob-api.md §2 und §3).
 *
 * <p><b>Keine Abfrage bricht einen Aufrufer ab.</b> Das ist die eine Zusage, die dieser Test
 * festhält, und sie ist nicht kosmetisch: B11, B12 und B13 hängen an diesen Antworten, und B10 selbst
 * fragt aus einem Spawn-Ereignis heraus, wo „außerhalb jeder Region" ein normaler Zustand ist.
 * Dieselbe Entscheidung hat B09 für {@code spawnAreasOf} getroffen — leere Liste statt Ausnahme.
 */
class MobApiContractTest {

    @Test
    @DisplayName("ein unbekannter Artschluessel antwortet LEER und wirft nicht")
    void anUnknownKindAnswersEmpty() {
        MobKinds kinds = MobKinds.backedBy(MobApiContractTest::config, new HordeRegistry());

        assertThatCode(() -> kinds.find("does.not.exist")).doesNotThrowAnyException();
        assertThat(kinds.find("does.not.exist")).isEmpty();
        assertThat(kinds.find(null)).as("auch null - ein Aufrufer soll nicht vorher pruefen muessen").isEmpty();
    }

    @Test
    @DisplayName("eine Entitaet, die dieser Block nicht gesetzt hat, antwortet leer")
    void anEntityThisBlockDidNotPlaceAnswersEmpty() {
        // Also fuer fast jede Entitaet der Welt. Die Abfrage liegt in Pfaden, die jede Kreatur
        // beruehren - sie muss den Normalfall billig und ohne Ausnahme beantworten.
        MobKinds kinds = MobKinds.backedBy(MobApiContractTest::config, new HordeRegistry());

        assertThat(kinds.ofEntity(UUID.randomUUID())).isEmpty();
        assertThat(kinds.ofEntity(null)).isEmpty();
    }

    @Test
    @DisplayName("eine Entitaet aus dem Bestand antwortet mit ihrer Art")
    void anEntityFromTheRegistryAnswersWithItsKind() {
        HordeRegistry registry = new HordeRegistry();
        UUID mob = UUID.randomUUID();
        registry.add(
                new HordeRegistry.Entry(
                        mob,
                        "probe.rotling",
                        "greenfields",
                        0L,
                        Instant.parse("2026-08-24T20:00:00Z"),
                        HordeRegistry.Origin.BUDGET));
        MobKinds kinds = MobKinds.backedBy(MobApiContractTest::config, registry);

        assertThat(kinds.ofEntity(mob).orElseThrow().key()).isEqualTo("probe.rotling");
    }

    @Test
    @DisplayName("ein unbekannter Zonenschluessel antwortet mit 0 und nicht mit einer Ausnahme")
    void anUnknownZoneAnswersZero() {
        Hordes hordes = Hordes.backedBy(new HordeRegistry(), new HashMap<>());

        assertThatCode(() -> hordes.countIn("does-not-exist")).doesNotThrowAnyException();
        assertThat(hordes.countIn("does-not-exist")).isZero();
        assertThat(hordes.countIn(null)).isZero();
        assertThat(hordes.bossOf("does-not-exist")).isEmpty();
    }

    @Test
    @DisplayName("bossOf antwortet auch waehrend des Respawn-Timers leer")
    void bossOfIsEmptyDuringTheRespawnTimer() {
        // Ob er kommen DARF, ist Interna dieses Blocks und steht bewusst nicht im Vertrag: es waere
        // eine Zusage ueber einen Zeitpunkt, die niemand ausserhalb braucht.
        Map<String, BossState> bosses = new HashMap<>();
        BossState state = new BossState("greenfields");
        state.placed(UUID.randomUUID());
        state.killed(Instant.parse("2026-08-24T20:00:00Z"));
        bosses.put("greenfields", state);

        Hordes hordes = Hordes.backedBy(new HordeRegistry(), bosses);

        assertThat(hordes.bossOf("greenfields")).isEmpty();
        assertThat(state.mayAppear(Instant.parse("2026-08-24T20:10:00Z"), Duration.ofMinutes(30)))
                .as("und intern weiss der Zustand sehr wohl, dass es noch nicht so weit ist")
                .isFalse();
    }

    @Test
    @DisplayName("total zaehlt ueber alle Zonen - die Zahl, gegen die das serverweite Budget haelt")
    void totalCountsAcrossAllZones() {
        HordeRegistry registry = new HordeRegistry();
        Instant when = Instant.parse("2026-08-24T20:00:00Z");
        registry.add(
                new HordeRegistry.Entry(
                        UUID.randomUUID(), "a", "greenfields", 1L, when, HordeRegistry.Origin.BUDGET));
        registry.add(
                new HordeRegistry.Entry(
                        UUID.randomUUID(), "b", "dustlands", 2L, when, HordeRegistry.Origin.BUDGET));

        Hordes hordes = Hordes.backedBy(registry, new HashMap<>());

        assertThat(hordes.total()).isEqualTo(2);
        assertThat(hordes.countIn("greenfields")).isEqualTo(1);
    }

    // --- fixtures ---

    private static MobConfig config() {
        MobKind rotling =
                new MobKind(
                        "probe.rotling",
                        "ZOMBIE",
                        3,
                        Map.of(Attribute.HEALTH, 40.0),
                        24.0,
                        MessageKey.of("mob.probe.rotling.name"),
                        12L,
                        4L,
                        false);
        return new MobConfig(
                new Budget(800, 130, 12, 25),
                Duration.ofSeconds(2),
                0.2,
                Duration.ofSeconds(60),
                96.0,
                Duration.ofMillis(500),
                Map.of(rotling.key(), rotling),
                Map.of(
                        "greenfields",
                        new HordeSpec(
                                "greenfields",
                                List.of(new HordeSpec.Entry("greenfields-east", "probe.rotling", 1)),
                                null)));
    }
}
