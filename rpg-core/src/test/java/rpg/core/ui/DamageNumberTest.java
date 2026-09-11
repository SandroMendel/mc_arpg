package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.scheduler.WorldPosition;

/**
 * Die Schadenszahl als Wert (T102, T103) — ohne Server, weil hier nichts von Paper abhängt.
 *
 * <p>Was sie <em>ist</em>, entscheidet sich hier; wo sie landet und wann sie verschwindet, in
 * {@code DamageNumbersTest}.
 */
class DamageNumberTest {

    private static final WorldPosition SOMEWHERE =
            new WorldPosition(UUID.randomUUID(), 10.0, 65.0, 20.0);

    @Test
    @DisplayName("T102: sie traegt Empfaenger, Ort, Betrag, Trefferzahl und Ablauf")
    void itcarriesEverythingItNeeds() {
        UUID viewer = UUID.randomUUID();
        DamageNumber number =
                new DamageNumber(viewer, SOMEWHERE, 240.0, 4, false, UiFixtures.T0.plusSeconds(1));

        assertThat(number.viewerId()).isEqualTo(viewer);
        assertThat(number.position()).isEqualTo(SOMEWHERE);
        assertThat(number.amount()).isEqualTo(240.0);
        assertThat(number.hitCount()).isEqualTo(4);
        assertThat(number.lethal()).isFalse();
    }

    @Test
    @DisplayName("T102: die Lebensdauer laeuft gegen eine gestellte Uhr")
    void thelifetimeRunsAgainstAFixedClock() {
        DamageNumber number =
                new DamageNumber(
                        UUID.randomUUID(),
                        SOMEWHERE,
                        10.0,
                        1,
                        false,
                        UiFixtures.T0.plusMillis(1200));

        assertThat(number.remaining(UiFixtures.T0)).isEqualTo(Duration.ofMillis(1200));
        assertThat(number.isExpired(UiFixtures.T0)).isFalse();
        assertThat(number.isExpired(UiFixtures.T0.plusMillis(1200))).isTrue();
    }

    @Test
    @DisplayName("T102: eine abgelaufene Zahl hat null Rest, nicht negativen")
    void anexpiredNumberHasNoNegativeRemainder() {
        // Eine negative Dauer waere im Scheduler ein Sofort-Lauf oder eine Ausnahme, je nach
        // Umsetzung - und beides waere ein Fehler in einem Pfad, den jeder Treffer beruehrt.
        DamageNumber number =
                new DamageNumber(
                        UUID.randomUUID(), SOMEWHERE, 10.0, 1, false, UiFixtures.T0);

        assertThat(number.remaining(UiFixtures.T0.plusSeconds(10))).isEqualTo(Duration.ZERO);
    }

    @Test
    @DisplayName("T103: zwanzig Schlaege ergeben EINE Zahl mit der Trefferzahl darin")
    void twentyHitsBecomeOneNumber() {
        // FR-041: B05 buendelt bereits. Die Trefferzahl steht im Text, damit ein Spieler eine 240
        // aus vier Treffern nicht fuer einen einzelnen haelt.
        DamageNumber number =
                new DamageNumber(
                        UUID.randomUUID(),
                        SOMEWHERE,
                        480.0,
                        20,
                        false,
                        UiFixtures.T0.plusSeconds(1));

        assertThat(number.values())
                .containsEntry("amount", "480")
                .containsEntry("hits", "20");
    }

    @Test
    @DisplayName("T103: der Betrag wird gerundet, nicht als Bruch gezeigt")
    void theamountIsRounded() {
        // 148.7 liest sich als 149, und der Bruch ist auf dieser Skala Rauschen - dieselbe
        // Entscheidung wie auf der Actionbar und in der Uebersicht.
        DamageNumber number =
                new DamageNumber(
                        UUID.randomUUID(),
                        SOMEWHERE,
                        148.7,
                        1,
                        false,
                        UiFixtures.T0.plusSeconds(1));

        assertThat(number.values()).containsEntry("amount", "149");
    }

    @Test
    @DisplayName("ein toedlicher Treffer bekommt einen eigenen Schluessel")
    void alethalHitGetsItsOwnKey() {
        DamageNumber lethal =
                new DamageNumber(
                        UUID.randomUUID(), SOMEWHERE, 99.0, 1, true, UiFixtures.T0.plusSeconds(1));
        DamageNumber ordinary =
                new DamageNumber(
                        UUID.randomUUID(), SOMEWHERE, 99.0, 1, false, UiFixtures.T0.plusSeconds(1));

        assertThat(lethal.key()).isEqualTo(UiMessageKeys.DAMAGE_NUMBER_LETHAL);
        assertThat(ordinary.key()).isEqualTo(UiMessageKeys.DAMAGE_NUMBER);
    }

    @Test
    @DisplayName("beide Schluessel stehen in UiMessageKeys.all()")
    void bothKeysAreDeclared() {
        // Sonst faellt ein fehlender Text erst dem Spieler auf, mitten im Kampf.
        assertThat(UiMessageKeys.all())
                .contains(UiMessageKeys.DAMAGE_NUMBER, UiMessageKeys.DAMAGE_NUMBER_LETHAL);
    }

    @Test
    @DisplayName("eine Zahl ohne Treffer gibt es nicht")
    void anumberWithoutHitsIsRejected() {
        assertThatThrownBy(
                        () ->
                                new DamageNumber(
                                        UUID.randomUUID(),
                                        SOMEWHERE,
                                        10.0,
                                        0,
                                        false,
                                        UiFixtures.T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hitCount");
    }

    @Test
    @DisplayName("ein unendlicher Betrag wird abgewiesen")
    void aninfiniteAmountIsRejected() {
        assertThatThrownBy(
                        () ->
                                new DamageNumber(
                                        UUID.randomUUID(),
                                        SOMEWHERE,
                                        Double.POSITIVE_INFINITY,
                                        1,
                                        false,
                                        UiFixtures.T0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endlich");
    }

    @Test
    @DisplayName("negativer Schaden ist erlaubt - Heilung ist auch ein Treffer")
    void negativeDamageIsAllowed() {
        // Endlich ist die Regel, nicht positiv. Was B05 als Betrag meldet, zeigt dieser Block an -
        // er entscheidet nicht, was ein gueltiger Schaden ist (FR-074).
        DamageNumber number =
                new DamageNumber(
                        UUID.randomUUID(), SOMEWHERE, -12.0, 1, false, UiFixtures.T0.plusSeconds(1));

        assertThat(number.values()).containsEntry("amount", "-12");
    }
}
