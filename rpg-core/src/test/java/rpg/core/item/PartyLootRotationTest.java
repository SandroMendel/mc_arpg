package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>SC-006a</b> — in einer Party wandert die Beute reihum, und über die Zeit bekommt jeder gleich
 * viel.
 *
 * <p>Das ist die Antwort auf einen Widerspruch, den keiner der beiden beteiligten Blöcke für sich
 * sehen konnte: B06 behandelt eine Party als <em>einen</em> Beitragenden und teilt Erfahrung und
 * Coins gleichmäßig; B05 gibt Beute dem größten Beitragenden und kennt keine Partys. In einer festen
 * Gruppe wäre jedes Item dauerhaft an denselben Spieler gegangen — der Tank hätte nie etwas
 * bekommen (ADR-039).
 *
 * <p><b>Gezählt werden die Gegenstände, nicht die Kills</b> (FR-026b). Der zweite Test hier ist der,
 * der den Unterschied festhält.
 */
class PartyLootRotationTest {

    private final PartyLootRotation rotation = new PartyLootRotation();

    @Test
    @DisplayName("SC-006a - drei Mitglieder, neun Gegenstaende, jeder genau drei")
    void threeMembersNineItemsThreeEach() {
        UUID partyId = UUID.randomUUID();
        UUID[] members = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};

        Map<UUID, Integer> received = new HashMap<>();
        for (int item = 0; item < 9; item++) {
            received.merge(rotation.next(partyId, members, 3), 1, Integer::sum);
        }

        assertThat(received).hasSize(3);
        assertThat(received.values())
                .as("gleichmaessig - so wie B06 Erfahrung und Coins verteilt")
                .containsOnly(3);
    }

    @Test
    @DisplayName("FR-026b - gezaehlt werden die GEGENSTAENDE, nicht die Kills")
    void itemsAreCountedNotKills() {
        UUID partyId = UUID.randomUUID();
        UUID[] members = {UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID()};

        // Zwanzig Kills, aber nur vier Gegenstaende. Wuerde je Kill weitergezaehlt, verfiele die
        // Runde dessen, dessen Gegner nichts fallen laesst - und ueber einen Abend gliche sich das
        // nicht aus. Hier wandert der Zeiger nur, wenn wirklich etwas faellt.
        List<UUID> owners = new ArrayList<>();
        for (int kill = 0; kill < 20; kill++) {
            boolean dropped = kill % 5 == 0; // vier von zwanzig
            if (dropped) {
                owners.add(rotation.next(partyId, members, 3));
            }
        }

        assertThat(owners).hasSize(4);
        assertThat(owners.subList(0, 3))
                .as("die ersten drei Gegenstaende gehen an drei verschiedene Mitglieder")
                .containsExactly(members[0], members[1], members[2]);
        assertThat(owners.get(3)).isEqualTo(members[0]);
    }

    @Test
    @DisplayName("mehrere Gegenstaende aus EINEM Tod gehen an aufeinanderfolgende Mitglieder")
    void severalItemsFromOneDeathGoToConsecutiveMembers() {
        UUID partyId = UUID.randomUUID();
        UUID[] members = {UUID.randomUUID(), UUID.randomUUID()};

        UUID first = rotation.next(partyId, members, 2);
        UUID second = rotation.next(partyId, members, 2);

        assertThat(first).isNotEqualTo(second);
    }

    @Test
    @DisplayName("zwei Partys stoeren sich nicht")
    void twoPartiesDoNotInterfere() {
        UUID here = UUID.randomUUID();
        UUID there = UUID.randomUUID();
        UUID[] members = {UUID.randomUUID(), UUID.randomUUID()};

        UUID firstHere = rotation.next(here, members, 2);
        rotation.next(there, members, 2);
        UUID secondHere = rotation.next(here, members, 2);

        assertThat(secondHere)
                .as("der Zeiger der einen Party ruecken nicht durch die andere vor")
                .isNotEqualTo(firstHere);
    }

    @Test
    @DisplayName("niemand in Reichweite - der Aufrufer faellt zurueck (FR-026c)")
    void nobodyInRangeYieldsNothing() {
        assertThat(rotation.next(UUID.randomUUID(), new UUID[0], 0)).isNull();
    }

    @Test
    @DisplayName("eine aufgeloeste Party wird vergessen - reiner Laufzeitzustand (FR-026d)")
    void aDissolvedPartyIsForgotten() {
        UUID partyId = UUID.randomUUID();
        UUID[] members = {UUID.randomUUID(), UUID.randomUUID()};
        rotation.next(partyId, members, 2);

        assertThat(rotation.size()).isEqualTo(1);

        rotation.forget(partyId);

        assertThat(rotation.size())
                .as("die Party wird laut B06 nicht persistiert - der Zeiger auch nicht")
                .isZero();
    }
}
