package rpg.core.ability;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

/**
 * T074 bis T077 - US3: Gesundheit und Mana kommen über die Zeit zurück (FR-033 bis FR-038b).
 *
 * <p><b>Hier heilt ein verletzter Spieler zum ersten Mal überhaupt.</b> ADR-013 hatte die
 * Vanilla-Regeneration abgeschaltet, damit ausschließlich die Engine die Herzleiste schreibt, und nie
 * einen Ersatz nachgeliefert.
 *
 * <p>Die schärfste Zusage ist nicht, <em>dass</em> regeneriert wird, sondern <b>wie</b>: aus zwei
 * Zeitstempeln, ohne Ereignis und ohne eine einzige geplante Aufgabe.
 */
class ResourceRegenerationTest {

    /** healthRegen 10/s, manaRegen 4/s - runde Zahlen, damit die Erwartung ablesbar bleibt. */
    private static final double HEALTH_RATE = 10.0;
    private static final double MANA_RATE = 4.0;

    private AbilityFixture fixture;
    private FakeCombat combat;
    private ResourceRegeneration regeneration;

    @BeforeEach
    void setUp() throws Exception {
        fixture = AbilityFixture.withStrike();
        fixture.stats.values.put(rpg.core.stats.Attribute.HEALTH_REGEN, HEALTH_RATE);
        fixture.stats.values.put(rpg.core.stats.Attribute.MANA_REGEN, MANA_RATE);
        fixture.stats.health = 500.0;
        fixture.stats.mana = 50.0;
        combat = new FakeCombat();
        regeneration =
                new ResourceRegeneration(fixture.stats, combat, fixture.registry, fixture.clock);
    }

    @Nested
    @DisplayName("US3.3 und FR-034 - die Abrechnung selbst")
    class Settling {

        @Test
        @DisplayName("die erste Abrechnung schreibt nichts gut - vorher wurde nicht hingesehen")
        void theFirstSettlementCreditsNothing() {
            regeneration.settle(fixture.character);

            assertThat(fixture.stats.health).isEqualTo(500.0);
            assertThat(fixture.stats.mana).isEqualTo(50.0);
        }

        @Test
        @DisplayName("außerhalb des Kampfes gilt die volle Rate")
        void outOfCombatTheFullRateApplies() {
            regeneration.settle(fixture.character);
            fixture.clock.advance(Duration.ofSeconds(10));

            regeneration.settle(fixture.character);

            assertThat(fixture.stats.health).isEqualTo(500.0 + 10 * HEALTH_RATE);
            assertThat(fixture.stats.mana).isEqualTo(50.0 + 10 * MANA_RATE);
        }

        @Test
        @DisplayName("FR-033a: im Kampf gilt die Rate mal dem Faktor")
        void inCombatTheFactorApplies() {
            combat.inCombat = true;
            combat.remaining = Duration.ofSeconds(60);
            regeneration.settle(fixture.character);
            fixture.clock.advance(Duration.ofSeconds(10));

            regeneration.settle(fixture.character);

            // 0.20 für Gesundheit, 0.35 für Mana - die Werte aus abilities.yml.
            assertThat(fixture.stats.health).isEqualTo(500.0 + 10 * HEALTH_RATE * 0.20);
            assertThat(fixture.stats.mana).isEqualTo(50.0 + 10 * MANA_RATE * 0.35);
        }

        @Test
        @DisplayName("zwei Abrechnungen ohne Zeitfortschritt schreiben nichts doppelt gut")
        void settlingTwiceCreditsOnce() {
            regeneration.settle(fixture.character);
            fixture.clock.advance(Duration.ofSeconds(5));

            regeneration.settle(fixture.character);
            double afterFirst = fixture.stats.health;
            regeneration.settle(fixture.character);

            assertThat(fixture.stats.health).isEqualTo(afterFirst);
        }
    }

    @Nested
    @DisplayName("T075 - die exakte Zerlegung, ganz ohne Ereignis")
    class ExactSplit {

        @Test
        @DisplayName("endet der Kampf mitten im Intervall, wird genau dort getrennt")
        void anIntervalIsSplitWhereCombatEnded() {
            // Kampf läuft noch 4 s, dann 6 s Ruhe - und dazwischen trifft KEIN Ereignis ein.
            combat.inCombat = true;
            combat.remaining = Duration.ofSeconds(4);
            regeneration.settle(fixture.character);

            combat.inCombat = false;
            combat.remaining = null;
            fixture.clock.advance(Duration.ofSeconds(10));

            regeneration.settle(fixture.character);

            double expected = 500.0 + (4 * HEALTH_RATE * 0.20) + (6 * HEALTH_RATE);
            assertThat(fixture.stats.health)
                    .as("4 s zum Kampffaktor, 6 s voll - nicht zehn zum einen oder anderen")
                    .isCloseTo(expected, within(0.001));
        }

        @Test
        @DisplayName("das Ereignis der verlassenden Flanke wird nie gebraucht (research.md R3)")
        void theLeavingEdgeEventIsNeverNeeded() {
            // In der Produktion wird CombatStateChangedEvent mit inCombat=false gar nicht
            // veröffentlicht. Diese Klasse abonniert nichts und kommt trotzdem auf das Richtige.
            combat.inCombat = true;
            combat.remaining = Duration.ofSeconds(8);
            regeneration.settle(fixture.character);

            combat.inCombat = false;
            fixture.clock.advance(Duration.ofSeconds(8));
            regeneration.settle(fixture.character);

            assertThat(fixture.stats.health)
                    .as("acht Sekunden Kampf, keine Ruhe - allein aus dem Zeitstempel")
                    .isCloseTo(500.0 + 8 * HEALTH_RATE * 0.20, within(0.001));
        }

        @Test
        @DisplayName("FR-038: die Abwesenheit zählt voll, obwohl niemand da war")
        void anAbsenceCountsAsIdle() {
            // 30 s bei 10/s sind 300 - bewusst unter dem Maximum, sonst prüfte der Test das Klemmen
            // statt die Anrechnung.
            regeneration.settleAbsence(
                    fixture.character, fixture.clock.instant().minus(Duration.ofSeconds(30)));

            assertThat(fixture.stats.health).isEqualTo(500.0 + 30 * HEALTH_RATE);
        }
    }

    @Nested
    @DisplayName("FR-038a und FR-038b - die Grenzen")
    class Limits {

        @Test
        @DisplayName("beide Ressourcen klemmen am Maximum, der Überschuss verfällt still")
        void bothClampAtTheMaximum() {
            regeneration.settle(fixture.character);
            fixture.clock.advance(Duration.ofHours(1));

            regeneration.settle(fixture.character);

            assertThat(fixture.stats.health).isEqualTo(fixture.stats.maxHealth);
            assertThat(fixture.stats.mana).isEqualTo(fixture.stats.maxMana);
        }

        @Test
        @DisplayName("ein toter Charakter regeneriert nicht")
        void aDeadCharacterDoesNotRegenerate() {
            regeneration.settle(fixture.character);
            fixture.stats.health = 0.0;
            fixture.clock.advance(Duration.ofSeconds(30));

            regeneration.settle(fixture.character);

            assertThat(fixture.stats.health)
                    .as("sonst heilte sich eine Leiche über den Todesbildschirm zurück")
                    .isZero();
        }

        @Test
        @DisplayName("ohne Rate regeneriert nichts - ein Monster heilt sich nicht selbst")
        void withoutARateNothingHappens() {
            fixture.stats.values.put(rpg.core.stats.Attribute.HEALTH_REGEN, 0.0);
            fixture.stats.values.put(rpg.core.stats.Attribute.MANA_REGEN, 0.0);
            regeneration.settle(fixture.character);
            fixture.clock.advance(Duration.ofSeconds(30));

            regeneration.settle(fixture.character);

            assertThat(fixture.stats.health).isEqualTo(500.0);
            assertThat(fixture.stats.mana).isEqualTo(50.0);
        }
    }

    @Nested
    @DisplayName("SC-005 - keine wiederkehrende Aufgabe je Spieler")
    class NoTasks {

        @Test
        @DisplayName("über tausend Abrechnungen wird nichts geplant")
        void nothingIsEverScheduled() {
            // Die Zusage, deren Bruch am teuersten wäre. Diese Klasse bekommt den Scheduler gar nicht
            // gereicht - sie KANN nichts planen, und das ist der Beweis, nicht eine Zählung.
            for (int i = 0; i < 1000; i++) {
                fixture.clock.advance(Duration.ofMillis(50));
                regeneration.settle(fixture.character);
            }

            assertThat(regeneration.trackedCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("ein vergessener Charakter hinterlässt nichts")
        void forgettingReleasesEverything() {
            regeneration.settle(fixture.character);
            assertThat(regeneration.trackedCount()).isEqualTo(1);

            regeneration.forget(fixture.character);

            assertThat(regeneration.trackedCount()).isZero();
        }
    }

    /** Nur der Kampfzustand, den diese Klasse liest - alles andere wirft. */
    private static final class FakeCombat implements rpg.core.combat.CombatPipeline {
        boolean inCombat;
        Duration remaining;

        @Override
        public boolean isInCombat(UUID holderId) {
            return inCombat;
        }

        @Override
        public Optional<Duration> remainingCombatTime(UUID holderId) {
            return Optional.ofNullable(remaining);
        }

        @Override
        public rpg.core.combat.DamageResult meleeAttack(UUID attackerId, UUID targetId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public rpg.core.combat.DamageResult abilityDamage(
                UUID attackerId, UUID targetId, rpg.core.combat.DamageType type, double factor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public rpg.core.combat.DamageResult projectileDamage(
                UUID shooterId, UUID targetId, double rawDamage) {
            throw new UnsupportedOperationException();
        }

        @Override
        public rpg.core.combat.DamageResult environmentDamage(
                UUID targetId, rpg.core.combat.EnvironmentSource source) {
            throw new UnsupportedOperationException();
        }

        @Override
        public rpg.core.combat.DamageResult fallDamage(UUID targetId, double fallenBlocks) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void kill(UUID targetId, rpg.core.combat.DeathCause cause) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerInterceptor(rpg.core.combat.DamageInterceptor interceptor) {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean canAttackNow(UUID attackerId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Optional<rpg.core.combat.DamageShare> currentShares(UUID targetId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setMobStatProvider(rpg.core.combat.MobStatProvider provider) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void registerFeedback(rpg.core.combat.DamageFeedback feedback) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void setPermission(rpg.core.combat.DamagePermission permission) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void forget(UUID holderId) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void clearDeathMark(UUID targetId) {
            throw new UnsupportedOperationException();
        }
    }
}
