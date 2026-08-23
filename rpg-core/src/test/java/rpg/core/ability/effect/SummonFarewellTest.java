package rpg.core.ability.effect;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.ability.Ability;
import rpg.core.ability.EffectPhase;
import rpg.core.ability.EffectSpec;
import rpg.core.ability.EffectType;
import rpg.core.ability.TargetMode;
import rpg.core.ability.TargetSpec;
import rpg.core.classes.AbilityKind;
import rpg.core.combat.DamageType;
import rpg.core.message.MessageKey;
import rpg.core.stats.Attribute;
import rpg.core.stats.StatSnapshot;

/**
 * Was der Klon hinterlaesst, wenn er geht (FR-016c).
 *
 * <p><b>Die Faehigkeit hat es zehn Monate lang versprochen und nie getan.</b> Im Text stand „geht am
 * Ende hoch", im Javadoc von {@code SummonEffect} stand „feuert einen Effekt, wenn er auslaeuft" - und
 * in der Konfiguration stand kein einziger Effekt, der das haette sein koennen. Es gab auch kein Feld
 * dafuer: alles, was eine Faehigkeit tut, tat sie beim Wirken.
 *
 * <p>Der Zeitpunkt ist deshalb jetzt eine eigene Angabe am Effekt, und dieser Test haelt die drei
 * Dinge fest, die daran haengen: der Knall kommt nicht beim Wirken, er kommt beim Ende, und er kommt
 * dort, wo der Klon steht - nicht dort, wo der Rogue inzwischen ist.
 */
class SummonFarewellTest {

    private static final UUID ROGUE = UUID.randomUUID();
    private static final UUID CLONE = UUID.randomUUID();
    private static final UUID NEAR_THE_CLONE = UUID.randomUUID();

    private final List<String> applied = new ArrayList<>();
    private EffectDispatcher dispatcher;
    private SummonEffect summon;
    private Runnable farewellTrigger;
    private Ability clone;

    @BeforeEach
    void setUp() {
        dispatcher = new EffectDispatcher(Logger.getLogger("summon-farewell-test"));
        dispatcher.register(
                EffectType.SUMMON, context -> applied.add("summon:" + context.targets()));
        dispatcher.register(
                EffectType.DAMAGE, context -> applied.add("damage:" + context.targets()));

        summon =
                new SummonEffect(
                        (summonerId, snapshot, health, lifetime, farewell) -> {
                            // Der Plattform-Teil im Kleinen: er merkt sich, was beim Ende zu tun ist,
                            // und der Test loest es aus, wenn er so weit ist.
                            farewellTrigger = farewell;
                            return Optional.of(CLONE);
                        });
        summon.setFarewell(
                (ability, summonerId, creatureId, rank, snapshot) ->
                        dispatcher.runAt(
                                ability,
                                EffectPhase.SUMMON_END,
                                summonerId,
                                // Was um den KLON herum steht - hier von Hand, in Wirklichkeit vom
                                // Resolver an dessen letzter Position.
                                List.of(NEAR_THE_CLONE),
                                rank,
                                snapshot));
        dispatcher.register(EffectType.SUMMON, summon);
        clone = cloneAbility();
    }

    @Test
    @DisplayName("beim Wirken steht der Klon, und sonst passiert nichts")
    void thecastOnlyPlacesTheClone() {
        dispatcher.run(clone, ROGUE, List.of(ROGUE), 1, snapshot());

        assertThat(applied)
                .as("der Knall wartet - genau das konnte die Maschine vorher nicht ausdruecken")
                .isEmpty();
        assertThat(farewellTrigger).as("und der Klon steht").isNotNull();
    }

    @Test
    @DisplayName("wenn der Klon geht, trifft es die, die bei IHM stehen")
    void theexplosionLandsWhereTheCloneStood() {
        dispatcher.run(clone, ROGUE, List.of(ROGUE), 1, snapshot());

        farewellTrigger.run();

        assertThat(applied).containsExactly("damage:[" + NEAR_THE_CLONE + "]");
    }

    @Test
    @DisplayName("der Klon wird beim Ende nicht ein zweites Mal gerufen")
    void thesummonItselfDoesNotRunAgain() {
        dispatcher.run(clone, ROGUE, List.of(ROGUE), 1, snapshot());

        farewellTrigger.run();

        // runAt nimmt NUR die Effekte der genannten Phase. Naehme es alle, stuende nach jedem Klon
        // ein zweiter, und nach dem ein dritter.
        assertThat(applied).noneMatch(entry -> entry.startsWith("summon:"));
    }

    @Test
    @DisplayName("ein Effekt ohne Zeitangabe wirkt weiter beim Wirken")
    void aneffectWithoutAPhaseStillFiresAtTheCast() {
        Ability plain =
                ability(
                        List.of(
                                new EffectSpec(
                                        EffectType.DAMAGE,
                                        1.0,
                                        0.0,
                                        null,
                                        null,
                                        1,
                                        null,
                                        null,
                                        DamageType.PHYSICAL,
                                        null,
                                        null,
                                        null,
                                        null,
                                        null,
                                        false,
                                        null)));

        dispatcher.run(plain, ROGUE, List.of(NEAR_THE_CLONE), 1, snapshot());

        assertThat(applied)
                .as("fehlende Angabe heisst CAST - jede vorher geschriebene Definition bleibt gleich")
                .containsExactly("damage:[" + NEAR_THE_CLONE + "]");
    }

    private static Ability cloneAbility() {
        return ability(
                List.of(
                        new EffectSpec(
                                EffectType.SUMMON,
                                60.0,
                                20.0,
                                Duration.ofSeconds(10),
                                null,
                                1,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                null,
                                false,
                                EffectPhase.CAST),
                        new EffectSpec(
                                EffectType.DAMAGE,
                                1.6,
                                0.25,
                                null,
                                null,
                                1,
                                null,
                                null,
                                DamageType.MAGIC,
                                null,
                                null,
                                null,
                                null,
                                null,
                                false,
                                EffectPhase.SUMMON_END)));
    }

    private static Ability ability(List<EffectSpec> effects) {
        return new Ability(
                "probe.clone",
                AbilityKind.ACTIVE,
                MessageKey.of("ability.probe.clone.name"),
                null,
                0.0,
                Duration.ZERO,
                Duration.ZERO,
                false,
                true,
                Duration.ZERO,
                1,
                null,
                false,
                false,
                false,
                false,
                java.util.Set.of(),
                1.0,
                new TargetSpec(TargetMode.RADIUS, 4.0, null, 8, null, null, null),
                effects,
                1,
                Map.of(),
                List.of("ARMOR_STAND"));
    }

    private static StatSnapshot snapshot() {
        return new StatSnapshot(new double[Attribute.count()], 1L);
    }
}
