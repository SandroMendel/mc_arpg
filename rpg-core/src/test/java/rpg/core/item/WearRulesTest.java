package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import rpg.core.classes.LadderSlot;
import rpg.core.combat.DamageOrigin;
import rpg.core.combat.DeathCause;

/**
 * SC-012 — <b>getrennter Verschleiß, und jede Zeile der Tabelle einzeln nachgewiesen.</b>
 *
 * <p>Die Anforderung des Auftraggebers war wörtlich: <em>„Erlittener Schaden = Ausrüstung verliert
 * Haltbarkeit, ausgeteilter Schaden mit der Waffe (Autoattacks) = Haltbarkeit der Waffe verringert
 * sich. Tod = beides."</em> Getrennt zu verschleißen ist der Punkt: wer viel einsteckt, repariert
 * Rüstung; wer viel austeilt, repariert die Waffe.
 *
 * <p><b>Warum eine Fähigkeit die Waffe nicht abnutzt</b> (FR-041): sonst wäre die Klasse, die am
 * meisten mit Fähigkeiten arbeitet, die mit den höchsten Reparaturkosten — eine Strafe auf einen
 * Spielstil, die niemand als Balancing-Entscheidung getroffen hat.
 */
class WearRulesTest {

    @Nested
    @DisplayName("Erlittener Schaden (FR-040)")
    class DamageTaken {

        @ParameterizedTest(name = "{0} trifft die Ruestung")
        @EnumSource(
                value = DamageOrigin.class,
                names = {"MELEE", "PROJECTILE", "ABILITY", "ENVIRONMENT"})
        void everyOriginButAdminWearsTheArmor(DamageOrigin origin) {
            assertThat(WearRules.onDamageTaken(origin, false))
                    .containsExactly(LadderSlot.ARMOR);
        }

        @Test
        @DisplayName("und NIEMALS die Waffe - wer einsteckt, benutzt sie nicht")
        void neverTheWeapon() {
            for (DamageOrigin origin : DamageOrigin.values()) {
                assertThat(WearRules.onDamageTaken(origin, false))
                        .doesNotContain(LadderSlot.WEAPON);
            }
        }

        @Test
        @DisplayName("ADMIN nutzt gar nichts ab - /kill ist kein Kampf (FR-042)")
        void adminWearsNothing() {
            assertThat(WearRules.onDamageTaken(DamageOrigin.ADMIN, false)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Ausgeteilter Schaden (FR-041)")
    class DamageDealt {

        @Test
        @DisplayName("ein Autoattack nutzt die Waffe ab - nah wie fern")
        void anautoattackWearsTheWeapon() {
            assertThat(WearRules.onDamageDealt(DamageOrigin.MELEE, false))
                    .containsExactly(LadderSlot.WEAPON);
            assertThat(WearRules.onDamageDealt(DamageOrigin.PROJECTILE, false))
                    .containsExactly(LadderSlot.WEAPON);
        }

        @Test
        @DisplayName("eine FAEHIGKEIT nutzt NICHTS ab - sonst zahlte ein Spielstil drauf")
        void anabilityWearsNothing() {
            assertThat(WearRules.onDamageDealt(DamageOrigin.ABILITY, false)).isEmpty();
        }

        @Test
        @DisplayName("und niemals die Ruestung - wer austeilt, steckt dabei nichts ein")
        void neverTheArmor() {
            for (DamageOrigin origin : DamageOrigin.values()) {
                assertThat(WearRules.onDamageDealt(origin, false)).doesNotContain(LadderSlot.ARMOR);
            }
        }

        @Test
        @DisplayName("Umgebung und ADMIN teilen nichts aus, das eine Waffe waere")
        void environmentAndAdminWearNothing() {
            assertThat(WearRules.onDamageDealt(DamageOrigin.ENVIRONMENT, false)).isEmpty();
            assertThat(WearRules.onDamageDealt(DamageOrigin.ADMIN, false)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Der Klon (FR-041a)")
    class TheSummon {

        @ParameterizedTest(name = "ein Klon nutzt bei {0} nichts ab, weder austeilend...")
        @EnumSource(DamageOrigin.class)
        void asummonWearsNothingWhenDealing(DamageOrigin origin) {
            assertThat(WearRules.onDamageDealt(origin, true)).isEmpty();
        }

        @ParameterizedTest(name = "...noch bei {0} erleidend")
        @EnumSource(DamageOrigin.class)
        void asummonWearsNothingWhenTaking(DamageOrigin origin) {
            assertThat(WearRules.onDamageTaken(origin, true)).isEmpty();
        }
    }

    @Nested
    @DisplayName("Der Tod (FR-042)")
    class Death {

        @ParameterizedTest(name = "{0} nutzt BEIDE Leitern ab")
        @EnumSource(value = DeathCause.class, names = {"COMBAT", "ENVIRONMENT", "VOID"})
        void everyCauseButAdminWearsBoth(DeathCause cause) {
            assertThat(WearRules.onDeath(cause))
                    .containsExactlyInAnyOrder(LadderSlot.ARMOR, LadderSlot.WEAPON);
        }

        @Test
        @DisplayName("ADMIN nutzt nichts ab - ein /kill ist eine Betreiberhandlung, keine Niederlage")
        void adminWearsNothing() {
            assertThat(WearRules.onDeath(DeathCause.ADMIN)).isEmpty();
        }
    }
}
