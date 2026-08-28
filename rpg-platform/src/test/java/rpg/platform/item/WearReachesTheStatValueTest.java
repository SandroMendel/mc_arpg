package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.GearConditionFactor;
import rpg.core.classes.LadderSlot;
import rpg.core.combat.DamageOrigin;
import rpg.core.combat.DamageType;
import rpg.core.combat.DamageView;
import rpg.core.combat.PipelineStage;
import rpg.core.event.DefaultEventBus;
import rpg.core.item.DefaultGearConditions;
import rpg.core.item.GearCondition;
import rpg.core.item.GearConditionRepository;
import rpg.core.item.WearCurve;
import rpg.core.stats.StatSnapshot;

/**
 * FR-049 — <b>der Weg vom Treffer bis zum Wert, an der Stelle, an der er die Plattform berührt.</b>
 *
 * <p>Was hier geprüft wird, ist die <em>Verkettung</em>: ein Schadensereignis erreicht
 * {@link WearInterceptor}, der senkt den Zustand, und der Zustand senkt den Faktor, mit dem B07 den
 * Stufenbeitrag multipliziert. Dass der Faktor dann wirklich nur den Stufenanteil trifft und
 * Grundwerte, Levelwachstum und den anderen Slot in Ruhe lässt, prüft
 * {@code ClassContributionIsUnchangedWithoutB11Test} in {@code rpg-core} — dort liegt B07s
 * Prüfstand, und dort gehört diese Hälfte hin.
 *
 * <p><b>Und die Naht ist dieselbe.</b> {@code GearConditions} erweitert {@link GearConditionFactor};
 * es gibt keinen zweiten Weg zu dieser Zahl, und dieser Test hält das fest, indem er den Faktor
 * über die B07-Schnittstelle abruft und nicht über die B11-Methode.
 */
class WearReachesTheStatValueTest {

    private static final UUID CHARACTER = UUID.randomUUID();
    private static final UUID HOLDER = UUID.randomUUID();

    private DefaultGearConditions conditions;
    private WearInterceptor interceptor;

    @BeforeEach
    void setUp() {
        conditions =
                new DefaultGearConditions(
                        WearCurve::defaults,
                        new NoRepository(),
                        new DefaultEventBus(java.util.logging.Logger.getLogger("quiet")),
                        Clock.systemUTC());
        conditions.put(GearCondition.full(CHARACTER));
        interceptor =
                new WearInterceptor(
                        conditions,
                        holderId -> HOLDER.equals(holderId) ? Optional.of(CHARACTER) : Optional.empty(),
                        holderId -> false);
    }

    @Test
    @DisplayName("der Zuhoerer haengt an MODIFIERS - vor der Abwehr, wie FR-040a verlangt")
    void theinterceptorSitsBeforeDefence() {
        assertThat(interceptor.stage())
                .as(
                        "eine Stufe spaeter waere der durchgekommene Schaden - und damit die"
                                + " Abwaertsspirale, gegen die FR-040a geschrieben ist")
                .isEqualTo(PipelineStage.MODIFIERS);
    }

    @Test
    @DisplayName("ein Treffer senkt den Zustand - und damit den Faktor, mit dem B07 rechnet")
    void ahitLowersTheConditionAndWithItTheFactor() {
        GearConditionFactor seam = conditions;
        assertThat(seam.factorFor(CHARACTER, LadderSlot.ARMOR)).isEqualTo(1.0);

        // Genug Treffer, um unter die Schwelle zu kommen - darueber ist der Beitrag voll, und das
        // ist Absicht: Verschleiss faengt spaeter an (FR-047).
        for (int hit = 0; hit < 60; hit++) {
            interceptor.intercept(new FakeDamage(HOLDER, HOLDER, DamageOrigin.MELEE, 100.0));
        }

        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.ARMOR)).isLessThan(50.0);
        assertThat(seam.factorFor(CHARACTER, LadderSlot.ARMOR))
                .as("dieselbe Zahl, ueber B07s Schnittstelle geholt - es gibt keine zweite")
                .isLessThan(1.0)
                .isGreaterThanOrEqualTo(WearCurve.defaults().floor());
    }

    @Test
    @DisplayName("ein Autoattack nutzt die Waffe ab, ein Treffer die Ruestung - getrennt (SC-012)")
    void thetwoSlotsWearSeparately() {
        // Ein Angreifer, der NICHT das Ziel ist: dann trifft der eine Schlag die Waffe des einen
        // und die Ruestung des anderen - und die des anderen ist hier niemand.
        interceptor.intercept(
                new FakeDamage(HOLDER, UUID.randomUUID(), DamageOrigin.MELEE, 2_000.0));

        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.WEAPON)).isLessThan(WearCurve.FULL);
        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.ARMOR))
                .as("wer austeilt, steckt dabei nichts ein")
                .isEqualTo(WearCurve.FULL);
    }

    @Test
    @DisplayName("eine FAEHIGKEIT nutzt die Waffe nicht ab (FR-041)")
    void anabilityDoesNotWearTheWeapon() {
        interceptor.intercept(
                new FakeDamage(HOLDER, UUID.randomUUID(), DamageOrigin.ABILITY, 5_000.0));

        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.WEAPON))
                .as("sonst zahlte die Klasse mit den meisten Faehigkeiten am meisten drauf")
                .isEqualTo(WearCurve.FULL);
    }

    @Test
    @DisplayName("und ein Treffer ohne Schaden aendert gar nichts")
    void ahitForNoDamageChangesNothing() {
        interceptor.intercept(new FakeDamage(HOLDER, HOLDER, DamageOrigin.MELEE, 0.0));

        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.ARMOR)).isEqualTo(WearCurve.FULL);
    }

    @Test
    @DisplayName("ein Halter ohne Charakter verschleisst nichts - eine Kreatur traegt keine Ruestung")
    void aholderWithoutACharacterWearsNothing() {
        UUID creature = UUID.randomUUID();

        interceptor.intercept(new FakeDamage(creature, creature, DamageOrigin.MELEE, 5_000.0));

        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.ARMOR)).isEqualTo(WearCurve.FULL);
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    /**
     * Die Sicht, die der Zuhörer bekommt — nur die vier Werte, die er liest.
     *
     * <p>Alles andere wirft, und das ist Absicht: liest dieser Zuhörer eines Tages mehr, soll der
     * Test es merken und nicht schweigend eine Null liefern.
     */
    private record FakeDamage(UUID attacker, UUID target, DamageOrigin origin, double raw)
            implements DamageView {

        @Override
        public Optional<UUID> attackerId() {
            return Optional.ofNullable(attacker);
        }

        @Override
        public UUID targetId() {
            return target;
        }

        @Override
        public DamageType type() {
            return DamageType.PHYSICAL;
        }

        @Override
        public DamageOrigin origin() {
            return origin;
        }

        @Override
        public Optional<rpg.core.combat.EnvironmentSource> environmentSource() {
            return Optional.empty();
        }

        @Override
        public double factor() {
            return 1.0;
        }

        @Override
        public double rawDamage() {
            return raw;
        }

        @Override
        public double finalDamage() {
            throw new AssertionError(
                    "FR-040a: gemessen wird VOR der Abwehr - finalDamage darf hier nie gefragt"
                            + " werden");
        }

        @Override
        public Optional<StatSnapshot> attackerSnapshot() {
            return Optional.empty();
        }

        @Override
        public StatSnapshot targetSnapshot() {
            throw new UnsupportedOperationException("not read by the wear interceptor");
        }

        @Override
        public PipelineStage stage() {
            return PipelineStage.MODIFIERS;
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public void setRawDamage(double value) {
            throw new AssertionError("der Verschleiss aendert den Schaden nicht");
        }

        @Override
        public void setFinalDamage(double value) {
            throw new AssertionError("der Verschleiss aendert den Schaden nicht");
        }

        @Override
        public void cancel() {
            throw new AssertionError("der Verschleiss bricht keinen Treffer ab");
        }
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
