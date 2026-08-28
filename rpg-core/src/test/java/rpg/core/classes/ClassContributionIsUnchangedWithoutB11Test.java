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
 * <b>Die Abnahmebedingung des einen Eingriffs, den B11 in B07 macht.</b>
 *
 * <p>{@link ClassStatContributor} und {@link EquipmentLadder#contributeTo} haben mit ADR-039 einen
 * Faktor bekommen — B11s Verschleiß (research.md R1). Der Plan nennt das im Complexity Tracking, und
 * die Bedingung dafür war von Anfang an: <em>ohne B11 verhält sich B07 bitgenau wie zuvor.</em>
 *
 * <p>Der ganze B07-Testbestand prüft das indirekt, indem er unverändert grün bleibt. Dieser Test
 * prüft es <b>direkt</b> und an der Stelle, an der es zählt: derselbe Charakter, einmal über den
 * alten Konstruktor und einmal über den neuen mit {@link GearConditionFactor#NONE}, ergibt Zahl für
 * Zahl dasselbe.
 *
 * <p><b>Warum das nicht selbstverständlich ist.</b> Der Faktor multipliziert jeden Stufenwert. Eine
 * Multiplikation mit {@code 1.0} ist in Gleitkomma nicht immer folgenlos — und wichtiger: die
 * Bedingung {@code value != 0.0}, die verhindert, dass ein Nullbeitrag als {@code addBase} in die
 * Senke geht, sitzt <em>hinter</em> der Multiplikation. Bliebe sie davor, käme bei Faktor 0 ein
 * Beitrag von {@code 0.0} in die Senke, den es vorher nicht gab.
 */
class ClassContributionIsUnchangedWithoutB11Test {

    @Test
    @DisplayName("mit NONE liefert der Beitragende Zahl fuer Zahl dasselbe wie ohne Faktor")
    void withNoneTheContributionIsIdentical() throws Exception {
        UUID characterId = UUID.randomUUID();

        for (CharacterClass characterClass : CharacterClass.values()) {
            for (int level : new int[] {1, 20, 60}) {
                for (int tier : new int[] {1, 3, 6}) {
                    RecordingSink before = new RecordingSink();
                    RecordingSink after = new RecordingSink();

                    withoutFactor(characterId, characterClass, level, tier)
                            .contribute(holder(characterId), before);
                    withNone(characterId, characterClass, level, tier)
                            .contribute(holder(characterId), after);

                    assertThat(after.totals())
                            .as(
                                    "%s auf Level %d, Stufe %d - der Faktor NONE darf nichts"
                                            + " veraendern",
                                    characterClass, level, tier)
                            .isEqualTo(before.totals());
                    assertThat(after.calls())
                            .as("und auch nicht die Anzahl der Beitraege")
                            .isEqualTo(before.calls());
                }
            }
        }
    }

    @Test
    @DisplayName("ein Faktor unter 1 mindert den Stufenbeitrag - und nur ihn")
    void afactorBelowOneReducesOnlyTheTierContribution() throws Exception {
        UUID characterId = UUID.randomUUID();

        RecordingSink full = new RecordingSink();
        RecordingSink halved = new RecordingSink();

        withNone(characterId, CharacterClass.WARRIOR, 60, 6).contribute(holder(characterId), full);
        withFactor(characterId, CharacterClass.WARRIOR, 60, 6, 0.5)
                .contribute(holder(characterId), halved);

        // Basiswerte der Klasse und Levelwachstum laufen NICHT ueber contributeTier - sie bleiben
        // unberuehrt (FR-049). Nur der Stufenanteil halbiert sich, also ist die Differenz genau die
        // Haelfte des Stufenwerts und nicht die Haelfte des Gesamtwerts.
        double fullHealth = full.total(Attribute.HEALTH);
        double halvedHealth = halved.total(Attribute.HEALTH);

        assertThat(halvedHealth).isLessThan(fullHealth);
        assertThat(halvedHealth)
                .as("mehr als die Haelfte bleibt uebrig - Basis und Wachstum sind unberuehrt")
                .isGreaterThan(fullHealth / 2.0);
    }

    @Test
    @DisplayName("Ruestung und Waffe werden getrennt gemindert (FR-049)")
    void theTwoSlotsAreScaledSeparately() throws Exception {
        UUID characterId = UUID.randomUUID();

        RecordingSink armourOnly = new RecordingSink();
        RecordingSink weaponOnly = new RecordingSink();

        // Nur die Ruestung verschlissen.
        contributor(characterId, CharacterClass.WARRIOR, 60, 6, 6,
                        (id, slot) -> slot == LadderSlot.ARMOR ? 0.2 : 1.0)
                .contribute(holder(characterId), armourOnly);
        // Nur die Waffe verschlissen.
        contributor(characterId, CharacterClass.WARRIOR, 60, 6, 6,
                        (id, slot) -> slot == LadderSlot.WEAPON ? 0.2 : 1.0)
                .contribute(holder(characterId), weaponOnly);

        // Die Ruestung traegt Health, die Waffe nicht - also trifft ein verschlissener
        // Ruestungsslot die Lebensenergie und ein verschlissener Waffenslot nicht.
        assertThat(armourOnly.total(Attribute.HEALTH))
                .as("verschlissene Ruestung mindert die Lebensenergie")
                .isLessThan(weaponOnly.total(Attribute.HEALTH));
        assertThat(weaponOnly.total(Attribute.PHYSICAL_DAMAGE))
                .as("und verschlissene Waffe den Schaden")
                .isLessThan(armourOnly.total(Attribute.PHYSICAL_DAMAGE));
    }

    // ---------------------------------------------------------------------------------

    /** Der Weg, den jeder Aufrufer vor B11 genommen hat - der Konstruktor ohne Faktor. */
    private static ClassStatContributor withoutFactor(
            UUID characterId, CharacterClass id, int level, int tier) throws Exception {
        return new ClassStatContributor(
                ClassConfigFixture.bind(ClassConfigFixture.valid()),
                held -> Optional.of(id),
                held -> level,
                held -> Optional.of(progress(characterId, tier)));
    }

    private static ClassStatContributor withNone(
            UUID characterId, CharacterClass id, int level, int tier) throws Exception {
        return contributor(characterId, id, level, tier, tier, GearConditionFactor.NONE);
    }

    private static ClassStatContributor withFactor(
            UUID characterId, CharacterClass id, int level, int tier, double factor)
            throws Exception {
        return contributor(characterId, id, level, tier, tier, (held, slot) -> factor);
    }

    private static ClassStatContributor contributor(
            UUID characterId,
            CharacterClass id,
            int level,
            int armorTier,
            int weaponTier,
            GearConditionFactor factor)
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
                                        0L)),
                factor);
    }

    private static ClassProgress progress(UUID characterId, int tier) {
        return new ClassProgress(
                characterId, tier, tier, ClassProgress.CURRENT_DATA_VERSION, 0L);
    }

    private static StatHolderView holder(UUID characterId) {
        return new StatHolderView() {
            @Override
            public UUID holderId() {
                return characterId;
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

    /** Eine Senke, die mitschreibt statt zu rechnen. */
    private static final class RecordingSink implements BaseStatSink {

        private final Map<Attribute, Double> totals = new EnumMap<>(Attribute.class);
        private int calls;

        @Override
        public void addBase(Attribute attribute, double amount) {
            totals.merge(attribute, amount, Double::sum);
            calls++;
        }

        Map<Attribute, Double> totals() {
            return totals;
        }

        double total(Attribute attribute) {
            return totals.getOrDefault(attribute, 0.0);
        }

        int calls() {
            return calls;
        }
    }
}
