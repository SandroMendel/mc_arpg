package rpg.core.stats;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.NoSuchElementException;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Halter und Charakter sind zwei Ids - und der Engine kennt nur die eine.
 *
 * <p><b>Warum es diesen Test gibt.</b> Jede Methode dieser Schnittstelle nimmt eine <em>Halter</em>-Id,
 * und die Notiz an {@code createForCharacter} nennt eine zweite Kennung „eine Gelegenheit, die falsche
 * zu übergeben". B08 hat diese Gelegenheit genutzt: der Block ist durchgehend nach Charakter
 * verschlüsselt - Freischaltungen, Ränge, Cooldowns gehören zum Charakter (ADR-011) - und hat diese
 * Charakter-Ids ungeprüft an den Engine weitergereicht. Jeder Aufruf warf, der Trigger-Listener fing
 * die Ausnahme ab wie vorgesehen, und das Ergebnis war ein Server, auf dem keine Fähigkeit etwas tat
 * und niemand regenerierte - bei grünen Tests, weil kein Double die beiden Ids je unterschied.
 *
 * <p>Hier steht deshalb beides schwarz auf weiß: dass eine Charakter-Id <b>nicht</b> funktioniert, und
 * dass es einen benannten Rückweg gibt, statt dass jeder Aufrufer sich einen baut.
 */
class HolderAndCharacterIdTest {

    @Test
    @DisplayName("mit der Charakter-Id kennt der Engine niemanden - genau das ist passiert")
    void aCharacterIdIsNotAHolderId() {
        EngineFixture fixture = new EngineFixture();
        UUID playerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        create(fixture, playerId, characterId);

        assertThat(fixture.engine.resources(playerId).currentMana())
                .as("mit der Halter-Id geht alles")
                .isGreaterThan(0.0);

        assertThatThrownBy(() -> fixture.engine.resources(characterId))
                .isInstanceOf(NoSuchElementException.class);
        assertThatThrownBy(() -> fixture.engine.changeMana(characterId, -20.0))
                .as("und genau hier verpuffte jeder Fähigkeitsklick")
                .isInstanceOf(NoSuchElementException.class);
    }

    @Test
    @DisplayName("der Rückweg gehört dem Engine, nicht jedem Aufrufer einzeln")
    void theTranslationGoesBothWays() {
        EngineFixture fixture = new EngineFixture();
        UUID playerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        create(fixture, playerId, characterId);

        assertThat(fixture.engine.characterIdOf(playerId)).contains(characterId);
        assertThat(fixture.engine.holderOf(characterId)).contains(playerId);
    }

    @Test
    @DisplayName("ein Mob hat keinen Charakter, und kein Charakter zeigt auf ihn")
    void anEntityHasNoCharacter() {
        EngineFixture fixture = new EngineFixture();
        UUID mobId = UUID.randomUUID();
        fixture.engine.createForEntity(mobId);

        assertThat(fixture.engine.characterIdOf(mobId)).isEmpty();
        assertThat(fixture.engine.holderOf(mobId)).isEmpty();
    }

    @Test
    @DisplayName("wer nicht im Spiel ist, hat keinen Halter - leer statt eines toten Verweises")
    void removingAHolderRemovesTheReverseEntryToo() {
        EngineFixture fixture = new EngineFixture();
        UUID playerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        create(fixture, playerId, characterId);

        fixture.engine.remove(playerId);

        // Bliebe der Rückweg stehen, bekäme der Aufrufer einen Halter, den es nicht mehr gibt, und
        // fiele einen Aufruf später auf die Ausnahme statt auf das leere Optional, das „nicht im
        // Spiel" bedeutet.
        assertThat(fixture.engine.holderOf(characterId)).isEmpty();
    }

    @Test
    @DisplayName("ein zweiter Charakter desselben Spielers ersetzt den Eintrag, statt ihn zu doppeln")
    void switchingCharacterRepointsTheHolder() {
        EngineFixture fixture = new EngineFixture();
        UUID playerId = UUID.randomUUID();
        UUID first = UUID.randomUUID();
        UUID second = UUID.randomUUID();
        create(fixture, playerId, first);
        create(fixture, playerId, second);

        assertThat(fixture.engine.holderOf(second)).contains(playerId);
        assertThat(fixture.engine.characterIdOf(playerId)).contains(second);
        assertThat(fixture.engine.holderOf(first))
                .as("der abgelegte Charakter zeigt auf nichts mehr")
                .isEmpty();
    }

    /** Wie der Ladepfad: anlegen, einmal rechnen, füllen. */
    private static void create(EngineFixture fixture, UUID playerId, UUID characterId) {
        fixture.engine.createForCharacter(playerId, characterId, new ResourcePool(0.0, 0.0));
        StatSnapshot first = fixture.engine.recalculateNow(playerId);
        fixture.engine.restoreResources(
                playerId, ResourcePool.full(first.get(Attribute.HEALTH), first.get(Attribute.MANA)));
    }
}
