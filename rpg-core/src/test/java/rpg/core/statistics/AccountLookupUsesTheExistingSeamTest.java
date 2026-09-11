package rpg.core.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * R3, FR-005 — <b>Halter und Charakter tragen verschiedene Kennungen, und ein vertauschtes Paar
 * fällt auf.</b>
 *
 * <p>Genau der Fehler, den 1614 Tests einmal nicht gesehen haben. Er ist unsichtbar, wenn die
 * Testdaten für beides dieselbe UUID benutzen — dann geht jede Vertauschung durch, weil beide
 * Seiten gleich aussehen. Deshalb kommen die Kennungen hier aus {@link StatisticsFixtures}, die
 * sie grundsätzlich getrennt erzeugt und ein gleiches Paar aktiv zurückweist.
 */
class AccountLookupUsesTheExistingSeamTest {

    @Test
    @DisplayName("die Charakterkennung fuehrt zum Konto")
    void thecharacterIdLeadsToTheAccount() {
        StatisticsFixtures.Identities identities = StatisticsFixtures.identities();
        AccountLookup lookup = lookupFor(identities);

        assertThat(lookup.accountOf(identities.characterId())).contains(identities.holderId());
    }

    @Test
    @DisplayName("die KONTOkennung fuehrt zu nichts - ein vertauschtes Paar faellt auf")
    void theaccountIdLeadsNowhere() {
        StatisticsFixtures.Identities identities = StatisticsFixtures.identities();
        AccountLookup lookup = lookupFor(identities);

        // Waeren beide Kennungen gleich, waere dieser Test nicht nur gruen, sondern
        // bedeutungslos - und die Vertauschung an jeder Aufrufstelle unsichtbar.
        assertThat(lookup.accountOf(identities.holderId()))
                .as("mit der Kontokennung gefragt, antwortet die Naht leer - nicht zufaellig richtig")
                .isEmpty();
    }

    @Test
    @DisplayName("ein Charakter ausserhalb des Spiels ergibt leer, nicht sich selbst")
    void acharacterOutOfPlayYieldsEmpty() {
        AccountLookup lookup = new AccountLookup(id -> Optional.empty());
        UUID characterId = StatisticsFixtures.characterId();

        assertThat(lookup.accountOf(characterId))
                .as("kein Rueckfall auf die Charakterkennung - das schriebe auf ein Konto, das es"
                        + " nicht gibt")
                .isEmpty();
    }

    @Test
    @DisplayName("die Huelle fragt die vorhandene Naht und fuehrt keine eigene Zuordnung")
    void thewrapperAsksTheSeamAndKeepsNoMappingOfItsOwn() {
        StatisticsFixtures.Identities identities = StatisticsFixtures.identities();
        AtomicInteger asked = new AtomicInteger();
        Map<UUID, UUID> seam = new HashMap<>();
        seam.put(identities.characterId(), identities.holderId());

        AccountLookup lookup =
                new AccountLookup(
                        id -> {
                            asked.incrementAndGet();
                            return Optional.ofNullable(seam.get(id));
                        });

        assertThat(lookup.accountOf(identities.characterId())).contains(identities.holderId());

        // Der Charakter wechselt das Konto - in der EINEN Wahrheit, die B04 fuehrt.
        UUID newHolder = StatisticsFixtures.holderId();
        seam.put(identities.characterId(), newHolder);

        assertThat(lookup.accountOf(identities.characterId()))
                .as("eine eigene Map hier haette die alte Antwort behalten, ohne dass etwas bricht")
                .contains(newHolder);
        assertThat(asked).as("jede Antwort kam aus der Naht, keine aus einem Zwischenspeicher")
                .hasValue(2);
    }

    private static AccountLookup lookupFor(StatisticsFixtures.Identities identities) {
        return new AccountLookup(
                id ->
                        id.equals(identities.characterId())
                                ? Optional.of(identities.holderId())
                                : Optional.empty());
    }
}
