package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.DamageShare;

/**
 * FR-007a, FR-007b, SC-014 — <b>der Heiler bekommt den Kill.</b>
 *
 * <p>Wer in der Gruppe steht und nahe genug ist, bekommt die Gutschrift auch mit einem
 * Schadensanteil von <b>null</b>. Das ist keine Großzügigkeit, sondern die einzige Lesart, die zu
 * B07 passt: eine Klasse, die heilt und schützt, teilt keinen Schaden aus. Zählte nur Schaden,
 * wäre jede Kill-Rangliste eine Rangliste der Schadensklassen — und die Gruppenrolle, die das
 * Spiel anbietet, wäre in der Statistik ein Nachteil.
 *
 * <p><b>Die Reichweite ist dieselbe wie bei Erfahrung und Coins</b> (FR-007b): {@code party
 * .range-blocks} aus {@code progression.yml}. Eine eigene Zahl für die Statistik wäre ein zweiter
 * Begriff von „dabei gewesen" — und Spieler würden ihn finden, sobald XP und Kill einmal
 * auseinanderliefen.
 *
 * <p><b>Diese Klasse wendet die Reichweite nicht selbst an.</b> Sie bekommt die Mitglieder
 * gereicht, auf die sie schon angewendet wurde; Positionen sind Plattformwissen. Was hier geprüft
 * wird, ist die Regel — nicht die Geometrie.
 */
class PartyMemberInRangeCountsTest {

    private static final double THRESHOLD = 0.05;

    @Test
    @DisplayName("FR-007a - ein Mitglied in Reichweite mit Anteil null bekommt den Kill")
    void amemberInRangeWithZeroShareGetsTheKill() {
        UUID fighter = StatisticsFixtures.holderId();
        UUID healer = StatisticsFixtures.holderId();

        DamageShare shares = shares(Map.of(fighter, 1.0));

        assertThat(KillCredit.recipients(shares, THRESHOLD, List.of(healer)))
                .containsExactlyInAnyOrder(fighter, healer);
    }

    @Test
    @DisplayName("FR-007b - das Mitglied ausserhalb der Reichweite bekommt ihn nicht")
    void themember0utOfRangeDoesNot() {
        UUID fighter = StatisticsFixtures.holderId();
        UUID healerNearby = StatisticsFixtures.holderId();
        UUID afkAtTheBase = StatisticsFixtures.holderId();

        DamageShare shares = shares(Map.of(fighter, 1.0));

        // Die Plattform hat die Reichweite bereits angewendet: wer zu weit weg stand, steht gar
        // nicht in der Liste. Genau deshalb kann hier nichts "vergessen" werden.
        assertThat(KillCredit.recipients(shares, THRESHOLD, List.of(healerNearby)))
                .containsExactlyInAnyOrder(fighter, healerNearby)
                .doesNotContain(afkAtTheBase);
    }

    @Test
    @DisplayName("ein Mitglied, das ohnehin beigetragen hat, wird nicht doppelt gezaehlt")
    void amemberWhoAlsoContributedIsNotCountedTwice() {
        UUID fighter = StatisticsFixtures.holderId();
        UUID secondFighter = StatisticsFixtures.holderId();

        DamageShare shares = shares(Map.of(fighter, 0.6, secondFighter, 0.4));

        assertThat(KillCredit.recipients(shares, THRESHOLD, List.of(fighter, secondFighter)))
                .as("eine Menge, kein Sack - zwei Wege zur selben Gutschrift bleiben eine")
                .hasSize(2);
    }

    @Test
    @DisplayName("ein Mitglied unter der Schwelle bekommt ihn ueber die Party trotzdem")
    void amemberBelowTheThresholdStillGetsItThroughTheParty() {
        UUID fighter = StatisticsFixtures.holderId();
        UUID dabbler = StatisticsFixtures.holderId();

        // Ein Anteil von 2 % reicht allein nicht (FR-007). In der Gruppe und in Reichweite zaehlt
        // er trotzdem - die beiden Wege sind ein Oder, kein Und.
        DamageShare shares = shares(Map.of(fighter, 0.98, dabbler, 0.02));

        assertThat(KillCredit.recipients(shares, THRESHOLD, List.of(dabbler)))
                .containsExactlyInAnyOrder(fighter, dabbler);
    }

    @Test
    @DisplayName("FR-008 - eine Party allein erlegt nichts")
    void apartyAloneKillsNothing() {
        UUID member = StatisticsFixtures.holderId();

        // Die Kreatur ist ohne Zutun eines Spielers gestorben. Dass jemand in der Naehe stand und
        // in einer Gruppe war, macht daraus keinen Kill - sonst zaehlte Herumstehen.
        //
        // Genau hier lag beim Schreiben ein Fehler: die Mitglieder wurden bedingungslos
        // hinzugefuegt, und eine in der Lava verbrannte Kreatur haette der ganzen Gruppe einen
        // Kill gutgeschrieben. FR-007a setzt einen Beitragenden voraus, ueber den ueberhaupt
        // etwas zu verteilen ist.
        assertThat(KillCredit.recipients(DamageShare.empty(), THRESHOLD, List.of(member)))
                .as("ohne Beitragenden gibt es nichts zu verteilen (FR-008)")
                .isEmpty();
    }

    private static DamageShare shares(Map<UUID, Double> byAttacker) {
        Map<UUID, Double> copy = new LinkedHashMap<>(byAttacker);
        UUID top =
                copy.entrySet().stream()
                        .max(Map.Entry.comparingByValue())
                        .map(Map.Entry::getKey)
                        .orElse(null);
        return new DamageShare(copy, top, 1000.0);
    }
}
