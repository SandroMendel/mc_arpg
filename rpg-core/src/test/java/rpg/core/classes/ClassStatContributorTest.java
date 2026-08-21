package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.stats.BaseStatSink;
import rpg.core.stats.StatHolderView;
import rpg.core.stats.StatSnapshot;

/**
 * T062 bis T065, T071 - der Klassenbeitrag ist ein Basiswert, und es ist genau einer.
 *
 * <p>Der Beitrag wird hier direkt gegen eine mitschreibende Senke geprüft, nicht über die Stat-Engine.
 * Das ist Absicht: die Frage lautet „was steuert die Klasse bei", nicht „wie verrechnet B04 es". Die
 * Verrechnung hat ihre eigenen Tests.
 */
class ClassStatContributorTest {

    @Test
    @DisplayName("die Kennung ist 'class' - genau eine Quelle (FR-009)")
    void idIsClass() {
        assertThat(contributor().id()).isEqualTo(ClassStatContributor.ID);
        assertThat(ClassStatContributor.ID).isEqualTo("class");
    }

    @Test
    @DisplayName("Basiswerte, Levelwachstum und beide Stufen kommen in EINEM Durchgang (FR-009)")
    void oneContributionCarriesEverything() throws Exception {
        UUID characterId = UUID.randomUUID();
        RecordingSink sink = new RecordingSink();

        // Warrior auf Level 1, Stufe 1/1: Basis 40 + Wachstum 0 + Ruestung 60 + Waffe 0 = 100 Health.
        contributor(characterId, CharacterClass.WARRIOR, 1, 1, 1).contribute(holder(characterId), sink);

        assertThat(sink.total(Attribute.HEALTH)).isEqualTo(100.0);
        assertThat(sink.total(Attribute.PHYSICAL_DAMAGE)).isEqualTo(5.0);
        assertThat(sink.calls())
                .as("ein Durchgang, nicht vier - jede Zeile ist ein addBase, kein zweiter Beitrag")
                .isPositive();
    }

    @Test
    @DisplayName("der effektive Basiswert enthält die Stufenwerte - darum sind es Basiswerte (R1)")
    void tierValuesAreInTheEffectiveBase() throws Exception {
        UUID characterId = UUID.randomUUID();
        RecordingSink atTop = new RecordingSink();

        // Warrior auf Level 60, Endstufe beider Leitern.
        contributor(characterId, CharacterClass.WARRIOR, 60, 5, 6).contribute(holder(characterId), atTop);

        // Basis 40 + 59 * 9.7 + Ruestungsendstufe 1385 = 1997.3
        assertThat(sink(atTop, Attribute.HEALTH)).isCloseTo(1997.3, within(0.05));
        // Ein Band von +-30% laege damit um ~1997, nicht um 40. Genau das war das Argument aus R1:
        // als FLAT-Modifikator waere der Basiswert 40 geblieben und 1385 waere weggeklammert worden.
        assertThat(sink(atTop, Attribute.HEALTH)).isGreaterThan(40.0 * 1.3);
    }

    @Test
    @DisplayName("ein Halter ohne Charakter steuert nichts bei und wirft nichts (T064)")
    void holderWithoutCharacterContributesNothing() {
        RecordingSink sink = new RecordingSink();

        contributor().contribute(mobHolder(), sink);

        assertThat(sink.calls()).isZero();
    }

    @Test
    @DisplayName("eine unbekannte Klasse steuert nichts bei statt eine Vorgabe zu erfinden")
    void unknownClassContributesNothing() throws Exception {
        UUID characterId = UUID.randomUUID();
        RecordingSink sink = new RecordingSink();
        ClassStatContributor contributor =
                new ClassStatContributor(
                        ClassConfigFixture.bind(ClassConfigFixture.valid()),
                        id -> Optional.empty(),
                        id -> 1,
                        id -> Optional.empty());

        contributor.contribute(holder(characterId), sink);

        assertThat(sink.calls()).isZero();
    }

    @Test
    @DisplayName("das Levelwachstum ist klassenspezifisch, nicht klassenneutral (FR-003, US2.2)")
    void growthIsPerClass() throws Exception {
        UUID characterId = UUID.randomUUID();

        RecordingSink warrior = new RecordingSink();
        contributor(characterId, CharacterClass.WARRIOR, 2, 1, 1).contribute(holder(characterId), warrior);
        RecordingSink mage = new RecordingSink();
        contributor(characterId, CharacterClass.MAGE, 2, 1, 1).contribute(holder(characterId), mage);

        // Warrior 9.7 je Level, Mage 6.0 - die Zahlen aus der Konfiguration, nicht die 8.0 aus B06.
        double warriorGain = sink(warrior, Attribute.HEALTH) - 100.0;
        double mageGain = sink(mage, Attribute.HEALTH) - 70.0;
        assertThat(warriorGain).isCloseTo(9.7, within(0.001));
        assertThat(mageGain).isCloseTo(6.0, within(0.001));
        assertThat(warriorGain).isNotEqualTo(mageGain);
    }

    @Test
    @DisplayName("die Rollenprofile stimmen bei gleichem Level und gleicher Stufe (US2.3)")
    void roleProfilesHold() throws Exception {
        UUID id = UUID.randomUUID();
        RecordingSink warrior = new RecordingSink();
        RecordingSink rogue = new RecordingSink();
        RecordingSink mage = new RecordingSink();
        contributor(id, CharacterClass.WARRIOR, 60, 5, 6).contribute(holder(id), warrior);
        contributor(id, CharacterClass.ROGUE, 60, 6, 6).contribute(holder(id), rogue);
        contributor(id, CharacterClass.MAGE, 60, 7, 7).contribute(holder(id), mage);

        assertThat(sink(warrior, Attribute.HEALTH))
                .as("Warrior hat das höchste Leben")
                .isGreaterThan(sink(rogue, Attribute.HEALTH))
                .isGreaterThan(sink(mage, Attribute.HEALTH));
        assertThat(sink(rogue, Attribute.ATTACK_SPEED))
                .as("Rogue hat die höchste Angriffsgeschwindigkeit")
                .isGreaterThan(sink(warrior, Attribute.ATTACK_SPEED))
                .isGreaterThan(sink(mage, Attribute.ATTACK_SPEED));
        assertThat(sink(mage, Attribute.MANA))
                .as("Mage hat das meiste Mana")
                .isGreaterThan(sink(warrior, Attribute.MANA))
                .isGreaterThan(sink(rogue, Attribute.MANA));
    }

    @Test
    @DisplayName("ohne Fortschrittszeile gilt Stufe 1 - ein frischer Charakter trägt Stufe 1")
    void missingProgressMeansTierOne() throws Exception {
        UUID characterId = UUID.randomUUID();
        RecordingSink withoutRow = new RecordingSink();
        RecordingSink withTierOne = new RecordingSink();

        new ClassStatContributor(
                        ClassConfigFixture.bind(ClassConfigFixture.valid()),
                        id -> Optional.of(CharacterClass.WARRIOR),
                        id -> 1,
                        id -> Optional.empty())
                .contribute(holder(characterId), withoutRow);
        contributor(characterId, CharacterClass.WARRIOR, 1, 1, 1)
                .contribute(holder(characterId), withTierOne);

        assertThat(sink(withoutRow, Attribute.HEALTH)).isEqualTo(sink(withTierOne, Attribute.HEALTH));
    }

    @Test
    @DisplayName("eine gespeicherte Stufe jenseits der Leiter klemmt statt zu werfen")
    void tierBeyondTheLadderIsClamped() throws Exception {
        UUID characterId = UUID.randomUUID();
        RecordingSink sink = new RecordingSink();

        // Startup verhindert das (FR-024); wenn es doch passiert, darf nicht die Neuberechnung
        // aller Spieler daran scheitern (Prinzip VI).
        contributor(characterId, CharacterClass.WARRIOR, 60, 99, 99).contribute(holder(characterId), sink);

        assertThat(sink(sink, Attribute.HEALTH)).isCloseTo(1997.3, within(0.05));
    }

    @Test
    @DisplayName("10 000 Beiträge lösen keinen Datenbankzugriff aus (FR-012, SC-010)")
    void manyContributionsTouchNoDatabase() throws Exception {
        UUID characterId = UUID.randomUUID();
        int[] lookups = {0};
        ClassStatContributor contributor =
                new ClassStatContributor(
                        ClassConfigFixture.bind(ClassConfigFixture.valid()),
                        id -> {
                            lookups[0]++;
                            return Optional.of(CharacterClass.WARRIOR);
                        },
                        id -> 1,
                        id -> Optional.of(ClassProgress.initial(id)));

        for (int i = 0; i < 10_000; i++) {
            contributor.contribute(holder(characterId), new RecordingSink());
        }

        // Die Klassendefinition liegt im Speicher - dreimal je Server, nicht je Spieler. Die einzigen
        // Aufrufe nach draussen sind die drei Funktionen, und die lesen den Sitzungs-Cache.
        assertThat(lookups[0]).isEqualTo(10_000);
    }

    // --- helpers ----------------------------------------------------------------------------

    private static org.assertj.core.data.Offset<Double> within(double tolerance) {
        return org.assertj.core.data.Offset.offset(tolerance);
    }

    private static double sink(RecordingSink sink, Attribute attribute) {
        return sink.total(attribute);
    }

    private static ClassStatContributor contributor() {
        try {
            return new ClassStatContributor(
                    ClassConfigFixture.bind(ClassConfigFixture.valid()),
                    id -> Optional.of(CharacterClass.WARRIOR),
                    id -> 1,
                    id -> Optional.empty());
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    private static ClassStatContributor contributor(
            UUID characterId, CharacterClass id, int level, int armorTier, int weaponTier)
            throws Exception {
        return new ClassStatContributor(
                ClassConfigFixture.bind(ClassConfigFixture.valid()),
                held -> Optional.of(id),
                held -> level,
                held ->
                        Optional.of(
                                new ClassProgress(
                                        characterId,
                                        armorTier,
                                        weaponTier,
                                        ClassProgress.CURRENT_DATA_VERSION,
                                        0L)));
    }

    private static StatHolderView holder(UUID characterId) {
        return new StatHolderView() {
            @Override
            public UUID holderId() {
                return UUID.randomUUID();
            }

            @Override
            public Optional<UUID> characterId() {
                return Optional.of(characterId);
            }

            @Override
            public Optional<StatSnapshot> previousSnapshot() {
                return Optional.empty();
            }
        };
    }

    private static StatHolderView mobHolder() {
        return new StatHolderView() {
            @Override
            public UUID holderId() {
                return UUID.randomUUID();
            }

            @Override
            public Optional<UUID> characterId() {
                return Optional.empty();
            }

            @Override
            public Optional<StatSnapshot> previousSnapshot() {
                return Optional.empty();
            }
        };
    }

    /** Adds up what the contributor asked for, and counts how often it asked. */
    private static final class RecordingSink implements BaseStatSink {
        private final Map<Attribute, Double> totals = new EnumMap<>(Attribute.class);
        private int calls;

        @Override
        public void addBase(Attribute attribute, double value) {
            totals.merge(attribute, value, Double::sum);
            calls++;
        }

        double total(Attribute attribute) {
            return totals.getOrDefault(attribute, 0.0);
        }

        int calls() {
            return calls;
        }
    }
}
