package rpg.core.combat;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T055 - worauf gruendet die Ablehnung fuer einen Spieler ohne Charakter?
 *
 * <p>ADR-020 sagt: vor der Klassenwahl gibt es keinen Spielzustand, insbesondere keinen Schaden. Der
 * Plan von B07 nahm an, das laufe ueber {@code SESSION_NOT_READY}. Diese Pruefung zeigt, dass es
 * anders und <b>staerker</b> ist: die Ablehnung faellt an {@code NO_HOLDER}, weil ein Stat-Halter
 * ueber {@code createForCharacter} entsteht und ein Spieler ohne Charakter deshalb gar nicht Teil des
 * Kampfsystems ist - genauso wie ein gewoehnliches Tier.
 *
 * <p>Der Unterschied ist nicht kosmetisch. {@code SESSION_NOT_READY} haengt am Sitzungszustand und
 * waere nach dem Laden der Sitzung wieder {@code READY}, also wirkungslos; {@code NO_HOLDER} haengt
 * daran, dass es nichts zu treffen gibt. Deshalb traegt ADR-020 <b>ohne jede Aenderung an B05</b>.
 */
class NoCharacterNoCombatTest {

    @Test
    @DisplayName("ein Angriff auf einen Spieler ohne Charakter wird mit NO_HOLDER abgelehnt")
    void attackOnCharacterlessPlayerIsRejected() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        // Kein createForCharacter: genau der Zustand zwischen Beitritt und Klassenwahl.
        UUID withoutCharacter = UUID.randomUUID();

        DamageResult result = fixture.pipeline.meleeAttack(attacker, withoutCharacter);

        assertThat(result.applied()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectReason.NO_HOLDER);
    }

    @Test
    @DisplayName("ein Spieler ohne Charakter kann auch nicht angreifen")
    void characterlessPlayerCannotAttack() {
        CombatFixture fixture = new CombatFixture();
        UUID withoutCharacter = UUID.randomUUID();
        UUID target = fixture.mob(100.0, 0.0, 5.0);

        DamageResult result = fixture.pipeline.meleeAttack(withoutCharacter, target);

        assertThat(result.applied()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectReason.NO_HOLDER);
    }

    @Test
    @DisplayName("Umgebungsschaden trifft einen Spieler ohne Charakter ebenfalls nicht")
    void environmentalDamageIsRejectedToo() {
        CombatFixture fixture = new CombatFixture();
        UUID withoutCharacter = UUID.randomUUID();

        DamageResult result =
                fixture.pipeline.environmentDamage(withoutCharacter, EnvironmentSource.FALL);

        assertThat(result.applied()).isFalse();
        assertThat(result.reason()).isEqualTo(RejectReason.NO_HOLDER);
    }

    @Test
    @DisplayName("mit Charakter greift der Schaden normal - der Halter ist der Unterschied")
    void afterSelectionDamageWorks() {
        CombatFixture fixture = new CombatFixture();
        UUID attacker = fixture.player(50.0, 0.0, 4.0);
        // Ziel ist ein Mob, nicht ein Spieler: PvP ist kein Kernmechanik-Ziel des Projekts, und ein
        // Spieler-gegen-Spieler-Angriff wuerde aus einem anderen Grund abgelehnt als dem, um den es
        // hier geht.
        UUID target = fixture.mob(100.0, 0.0, 5.0);

        DamageResult result = fixture.pipeline.meleeAttack(attacker, target);

        assertThat(result.applied())
                .as("derselbe Angreifer, jetzt mit vorhandenem Halter am Ziel: es trifft")
                .isTrue();
    }
}
