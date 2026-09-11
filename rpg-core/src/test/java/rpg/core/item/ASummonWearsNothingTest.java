package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.LadderSlot;
import rpg.core.combat.DamageOrigin;
import rpg.core.event.DefaultEventBus;

/**
 * FR-041a — <b>ein Klon nutzt nichts ab.</b>
 *
 * <p>Auf die Frage, ob der Klon aus B08 die Ausrüstung seines Beschwörers verschleißt, war die
 * Antwort: <em>„Nein, weder Waffe noch Rüstung."</em> Und das ist die richtige: der Klon trägt sie
 * nicht. Sie über ihn zu verschleißen hieße, eine Fähigkeit mit einer Rechnung zu belegen, die ihr
 * Wortlaut nicht nennt — wer sie oft benutzt, zahlte am meisten dafür, und niemand hätte das als
 * Balancing-Entscheidung getroffen.
 *
 * <p>Der Fall ist besonders leicht zu übersehen, weil der Klon in B05s Kampfpfad wie ein
 * gewöhnlicher Angreifer aussieht. Genau deshalb steht er als eigener Test da und nicht als Zeile in
 * {@link WearRulesTest}.
 */
class ASummonWearsNothingTest {

    private final UUID summoner = UUID.randomUUID();

    private DefaultGearConditions conditions;

    @BeforeEach
    void setUp() {
        conditions =
                new DefaultGearConditions(
                        WearCurve::defaults,
                        new NoRepository(),
                        new DefaultEventBus(java.util.logging.Logger.getLogger("quiet")),
                        Clock.systemUTC());
        conditions.put(GearCondition.full(summoner));
    }

    @Test
    @DisplayName("der Klon schlaegt tausendmal zu - die Waffe bleibt unberuehrt")
    void athousandBlowsByTheCloneWearNothing() {
        for (int blow = 0; blow < 1_000; blow++) {
            conditions.onDamageDealt(summoner, DamageOrigin.MELEE, true, 100.0);
        }

        assertThat(conditions.conditionOf(summoner, LadderSlot.WEAPON)).isEqualTo(WearCurve.FULL);
        assertThat(conditions.conditionOf(summoner, LadderSlot.ARMOR)).isEqualTo(WearCurve.FULL);
    }

    @Test
    @DisplayName("und er steckt tausendmal ein - die Ruestung ebenso")
    void athousandHitsOnTheCloneWearNothing() {
        for (int hit = 0; hit < 1_000; hit++) {
            conditions.onDamageTaken(summoner, DamageOrigin.MELEE, true, 200.0);
        }

        assertThat(conditions.conditionOf(summoner, LadderSlot.ARMOR)).isEqualTo(WearCurve.FULL);
        assertThat(conditions.conditionOf(summoner, LadderSlot.WEAPON)).isEqualTo(WearCurve.FULL);
    }

    @Test
    @DisplayName("derselbe Schlag durch den Beschwoerer SELBST nutzt sehr wohl ab")
    void thesameBlowByTheSummonerHimselfDoesWear() {
        // Sonst pruefte der Test oben nur, dass ueberhaupt nichts passiert.
        conditions.onDamageDealt(summoner, DamageOrigin.MELEE, false, 100.0);

        assertThat(conditions.conditionOf(summoner, LadderSlot.WEAPON)).isLessThan(WearCurve.FULL);
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
