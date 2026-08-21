package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.session.CharacterClass;

/** T075 bis T082 - der Stufenaufstieg (US3). */
class TierAdvanceTest {

    private static final Logger QUIET = quietLogger();

    @Test
    @DisplayName("T075: ein frischer Charakter steht auf Stufe 1 beider Leitern (US3.1)")
    void freshCharacterStartsOnTierOne() throws Exception {
        Fixture fixture = new Fixture();
        UUID character = fixture.character(CharacterClass.WARRIOR, 1);

        ClassProgress progress = fixture.progress.get(character);
        assertThat(progress.armorTier()).isEqualTo(1);
        assertThat(progress.weaponTier()).isEqualTo(1);
    }

    @Test
    @DisplayName("T076: Weiterschalten erhöht um eins und meldet die neue Stufe (US3.2)")
    void advanceRaisesByOne() throws Exception {
        Fixture fixture = new Fixture();
        UUID character = fixture.character(CharacterClass.WARRIOR, 60);

        TierAdvanceResult result = fixture.advance.advanceArmor(character);

        assertThat(result.advanced()).isTrue();
        assertThat(result.newTier()).isEqualTo(2);
        assertThat(fixture.progress.get(character).armorTier()).isEqualTo(2);
    }

    @Test
    @DisplayName("T077: über die Endstufe hinaus wird mit ALREADY_AT_TOP abgewiesen (US3.3, FR-020)")
    void beyondTheTopIsRejected() throws Exception {
        Fixture fixture = new Fixture();
        UUID character = fixture.character(CharacterClass.WARRIOR, 60);
        // Warrior-Rüstung hat fünf Stufen.
        for (int i = 0; i < 4; i++) {
            assertThat(fixture.advance.advanceArmor(character).advanced()).isTrue();
        }

        TierAdvanceResult result = fixture.advance.advanceArmor(character);

        assertThat(result.rejection()).hasValue(TierAdvanceRejection.ALREADY_AT_TOP);
        assertThat(fixture.progress.get(character).armorTier())
                .as("unverändert auf der Endstufe")
                .isEqualTo(5);
    }

    @Test
    @DisplayName("T078: unterhalb der Levelanforderung wird benannt abgewiesen (US3.4, FR-018)")
    void belowRequiredLevelIsRejected() throws Exception {
        Fixture fixture = new Fixture();
        // Stufe 2 der Warrior-Rüstung verlangt Level 15.
        UUID character = fixture.character(CharacterClass.WARRIOR, 14);

        TierAdvanceResult result = fixture.advance.advanceArmor(character);

        assertThat(result.rejection()).hasValue(TierAdvanceRejection.BELOW_REQUIRED_LEVEL);
        assertThat(fixture.progress.get(character).armorTier()).isEqualTo(1);
        assertThat(fixture.repository.marked).as("kein Schreibvermerk").isEmpty();
        assertThat(fixture.events).isEmpty();
    }

    @Test
    @DisplayName("genau auf der Levelanforderung gelingt es")
    void exactlyAtTheRequiredLevelSucceeds() throws Exception {
        Fixture fixture = new Fixture();
        UUID character = fixture.character(CharacterClass.WARRIOR, 15);

        assertThat(fixture.advance.advanceArmor(character).advanced()).isTrue();
    }

    @Test
    @DisplayName("T079: die beiden Leitern sind unabhängig (US3.6, FR-019)")
    void laddersAreIndependent() throws Exception {
        Fixture fixture = new Fixture();
        UUID character = fixture.character(CharacterClass.WARRIOR, 60);

        fixture.advance.advanceArmor(character);

        ClassProgress progress = fixture.progress.get(character);
        assertThat(progress.armorTier()).isEqualTo(2);
        assertThat(progress.weaponTier()).as("unverändert").isEqualTo(1);
    }

    @Test
    @DisplayName("T080: ein Aufstieg veröffentlicht genau EIN Ereignis (SC-009)")
    void oneAdvanceIsOneEvent() throws Exception {
        Fixture fixture = new Fixture();
        UUID character = fixture.character(CharacterClass.WARRIOR, 60);

        fixture.advance.advanceArmor(character);

        assertThat(fixture.events).hasSize(1);
        TierAdvancedEvent event = fixture.events.get(0);
        assertThat(event.slot()).isEqualTo(LadderSlot.ARMOR);
        assertThat(event.fromTier()).isEqualTo(1);
        assertThat(event.toTier()).isEqualTo(2);
    }

    @Test
    @DisplayName("ein Aufstieg markiert das Aggregat, schreibt aber nicht selbst (Prinzip II)")
    void advanceMarksButDoesNotWrite() throws Exception {
        Fixture fixture = new Fixture();
        UUID character = fixture.character(CharacterClass.WARRIOR, 60);

        fixture.advance.advanceArmor(character);

        assertThat(fixture.repository.marked).containsExactly(character);
        assertThat(fixture.repository.reads).as("kein Lesen aus der Datenbank").isZero();
    }

    @Test
    @DisplayName("T081: eine nachträglich erhöhte Anforderung senkt eine erreichte Stufe nicht")
    void raisingTheRequirementDoesNotDemote() throws Exception {
        Fixture fixture = new Fixture();
        UUID character = fixture.character(CharacterClass.WARRIOR, 60);
        fixture.advance.advanceArmor(character);
        assertThat(fixture.progress.get(character).armorTier()).isEqualTo(2);

        // Der Charakter fällt im Level unter die Anforderung der Stufe, die er schon trägt.
        fixture.levels.put(character, 5);

        assertThat(fixture.progress.get(character).armorTier())
                .as("die Anforderung gilt beim Weiterschalten, nicht rückwirkend")
                .isEqualTo(2);
        assertThat(fixture.advance.advanceArmor(character).rejection())
                .as("weiter geht es aber nicht")
                .hasValue(TierAdvanceRejection.BELOW_REQUIRED_LEVEL);
    }

    @Test
    @DisplayName("ein unbekannter Charakter wird benannt abgewiesen")
    void unknownCharacterIsRejected() throws Exception {
        Fixture fixture = new Fixture();

        TierAdvanceResult result = fixture.advance.advanceArmor(UUID.randomUUID());

        assertThat(result.rejection()).hasValue(TierAdvanceRejection.UNKNOWN_CHARACTER);
    }

    @Test
    @DisplayName("T082: costOf gibt den Block unausgelegt zurück (FR-021)")
    void costIsHandedOutUnread() throws Exception {
        Fixture fixture = new Fixture();

        Map<String, Object> cost =
                fixture.advance.costOf(CharacterClass.WARRIOR, LadderSlot.ARMOR, 2);

        assertThat(cost).isNotNull();
        assertThat(fixture.advance.costOf(CharacterClass.WARRIOR, LadderSlot.ARMOR, 1))
                .as("Stufe 1 ist der Start und kostet nichts")
                .isEmpty();
    }

    @Test
    @DisplayName("costOf für eine Stufe jenseits der Leiter ist ein Aufruferfehler, keine Ablehnung")
    void costForANonexistentTierThrows() throws Exception {
        Fixture fixture = new Fixture();

        assertThatThrownBy(() -> fixture.advance.costOf(CharacterClass.WARRIOR, LadderSlot.ARMOR, 99))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has tiers 1..5");
    }

    @Test
    @DisplayName("die Levelanforderung ist vorab abfragbar, damit ein Aufrufer sie nennen kann")
    void requiredLevelCanBeAskedInAdvance() throws Exception {
        Fixture fixture = new Fixture();

        assertThat(fixture.advance.requiredLevelFor(CharacterClass.WARRIOR, LadderSlot.ARMOR, 2))
                .isEqualTo(15);
        assertThat(fixture.advance.requiredLevelFor(CharacterClass.MAGE, LadderSlot.WEAPON, 7))
                .isEqualTo(55);
    }

    @Test
    @DisplayName("ein Sprung über mehrere Stufen ist nicht vorgesehen - das Ereignis verbietet ihn")
    void aJumpIsNotRepresentable() {
        assertThatThrownBy(
                        () ->
                                new TierAdvancedEvent(
                                        UUID.randomUUID(), LadderSlot.ARMOR, 1, 3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("exactly one step");
    }

    // --- fixture ----------------------------------------------------------------------------

    private static final class Fixture {
        final Map<UUID, CharacterClass> classes = new HashMap<>();
        final Map<UUID, Integer> levels = new HashMap<>();
        final Map<UUID, ClassProgress> progress = new HashMap<>();
        final List<TierAdvancedEvent> events = new ArrayList<>();
        final RecordingRepository repository = new RecordingRepository();
        final TierAdvance advance;

        Fixture() throws Exception {
            EventBus eventBus = new DefaultEventBus(QUIET);
            eventBus.subscribe(TierAdvancedEvent.class, events::add);
            advance =
                    new TierAdvance(
                            ClassConfigFixture.bind(ClassConfigFixture.valid()),
                            id -> Optional.ofNullable(classes.get(id)),
                            id -> levels.getOrDefault(id, 1),
                            id -> Optional.ofNullable(progress.get(id)),
                            updated -> progress.put(updated.characterId(), updated),
                            repository,
                            eventBus);
        }

        UUID character(CharacterClass id, int level) {
            UUID characterId = UUID.randomUUID();
            classes.put(characterId, id);
            levels.put(characterId, level);
            progress.put(characterId, ClassProgress.initial(characterId));
            return characterId;
        }
    }

    private static final class RecordingRepository implements ClassProgressRepository {
        final List<UUID> marked = new ArrayList<>();
        int reads;

        @Override
        public CompletableFuture<Optional<ClassProgress>> find(UUID characterId) {
            reads++;
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void markDirty(UUID characterId) {
            marked.add(characterId);
        }
    }

    private static Logger quietLogger() {
        Logger logger = Logger.getLogger(TierAdvanceTest.class.getName());
        logger.setLevel(Level.OFF);
        return logger;
    }
}
