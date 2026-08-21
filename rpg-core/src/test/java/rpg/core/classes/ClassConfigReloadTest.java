package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;

/**
 * T113 bis T115 und T117 - Balancing ohne Codeänderung (US6).
 *
 * <p>Das Erfolgskriterium SC-007 nennt fünf Kategorien: Basiswerte, Wachstumskurven, Stufenwerte,
 * Materialien und Anzeigenamen. Jede bekommt hier eine eigene Prüfung, weil „alles ist Config" sonst
 * eine Behauptung bleibt, die für vier von fünf stimmt.
 */
class ClassConfigReloadTest {

    @Test
    @DisplayName("T114 Kategorie 1: ein geänderter Basiswert wirkt (SC-007)")
    void baseStatsAreConfiguration() throws Exception {
        Map<String, Object> classes = ClassConfigFixture.valid();
        eight(classes, CharacterClass.WARRIOR, "base-stats").put(Attribute.HEALTH.key(), 999.0);

        ClassConfig config = ClassConfigFixture.bind(classes);

        assertThat(config.definition(CharacterClass.WARRIOR).baseStats().of(Attribute.HEALTH))
                .isEqualTo(999.0);
    }

    @Test
    @DisplayName("T114 Kategorie 2: eine geänderte Wachstumskurve wirkt (SC-007)")
    void growthIsConfiguration() throws Exception {
        Map<String, Object> classes = ClassConfigFixture.valid();
        eight(classes, CharacterClass.MAGE, "growth").put(Attribute.MANA.key(), 42.0);

        ClassConfig config = ClassConfigFixture.bind(classes);

        assertThat(config.definition(CharacterClass.MAGE).growth().perLevel(Attribute.MANA))
                .isEqualTo(42.0);
    }

    @Test
    @DisplayName("T114 Kategorie 3: ein geänderter Stufenwert wirkt (SC-007)")
    void tierValuesAreConfiguration() throws Exception {
        Map<String, Object> classes = ClassConfigFixture.valid();
        Map<String, Object> top =
                ClassConfigFixture.tierAt(
                        ClassConfigFixture.armorLadderOf(classes, CharacterClass.ROGUE), 6);
        ClassConfigFixture.valuesOf(top).put(Attribute.HEALTH.key(), 1234.0);

        ClassConfig config = ClassConfigFixture.bind(classes);

        assertThat(
                        config.definition(CharacterClass.ROGUE)
                                .armorLadder()
                                .top()
                                .valueOf(Attribute.HEALTH))
                .isEqualTo(1234.0);
    }

    @Test
    @DisplayName("T114 Kategorie 4: ein geändertes Material wirkt (SC-007)")
    void materialsAreConfiguration() throws Exception {
        Map<String, Object> classes = ClassConfigFixture.valid();
        ClassConfigFixture.tierAt(
                        ClassConfigFixture.weaponLadderOf(classes, CharacterClass.WARRIOR), 6)
                .put("material", "GOLDEN_SWORD");

        ClassConfig config = ClassConfigFixture.bind(classes);

        assertThat(
                        config.definition(CharacterClass.WARRIOR)
                                .weaponLadder()
                                .top()
                                .appearance()
                                .material())
                .isEqualTo("GOLDEN_SWORD");
    }

    @Test
    @DisplayName("T115 Kategorie 5: ein geänderter Anzeigename wirkt (US6.5, SC-007)")
    void displayNamesAreConfiguration() throws Exception {
        Map<String, Object> classes = ClassConfigFixture.valid();
        ClassConfigFixture.blockOf(classes, CharacterClass.WARRIOR)
                .put("display-name-key", "class.warrior.alternative");

        ClassConfig config = ClassConfigFixture.bind(classes);

        assertThat(config.definition(CharacterClass.WARRIOR).displayNameKey().value())
                .isEqualTo("class.warrior.alternative");
    }

    @Test
    @DisplayName("T113: eine Änderung wirkt auf neue UND bestehende Charaktere (US6.1)")
    void aChangeAffectsExistingCharactersToo() throws Exception {
        UUID existing = UUID.randomUUID();
        Map<String, Object> before = ClassConfigFixture.valid();
        Map<String, Object> after = ClassConfigFixture.valid();
        eight(after, CharacterClass.WARRIOR, "base-stats").put(Attribute.HEALTH.key(), 500.0);

        // Derselbe Charakter, dieselbe gespeicherte Stufe - nur die Konfiguration ist eine andere.
        // Der Wert folgt der Konfiguration, weil er nirgends am Charakter hängt.
        double healthBefore = healthOf(ClassConfigFixture.bind(before), existing);
        double healthAfter = healthOf(ClassConfigFixture.bind(after), existing);

        assertThat(healthAfter - healthBefore).isEqualTo(500.0 - 40.0);
    }

    @Test
    @DisplayName("T117: eine verkürzte Leiter bricht den Start ab statt herabzustufen (FR-024)")
    void aShortenedLadderRefusesToStart() throws Exception {
        UUID character = UUID.randomUUID();
        // Der Charakter steht auf Rüstungsstufe 5 - die Warrior-Leiter hat genau fünf.
        ClassProgress stored =
                new ClassProgress(character, 5, 1, ClassProgress.CURRENT_DATA_VERSION, 0L);

        Map<String, Object> shortened = ClassConfigFixture.valid();
        List<Object> armor = ClassConfigFixture.armorLadderOf(shortened, CharacterClass.WARRIOR);
        armor.remove(armor.size() - 1);
        ClassConfig config = ClassConfigFixture.bind(shortened);

        assertThatThrownBy(
                        () ->
                                config.validateAgainstStoredTiers(
                                        List.of(stored),
                                        id -> Optional.of(CharacterClass.WARRIOR)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("armor-ladder tier 5")
                .hasMessageContaining("only 4")
                .hasMessageContaining("rather than demoting");
    }

    @Test
    @DisplayName("T117: eine unveränderte Leiter lässt den Start durch")
    void anUnchangedLadderStartsFine() throws Exception {
        UUID character = UUID.randomUUID();
        ClassProgress stored =
                new ClassProgress(character, 5, 6, ClassProgress.CURRENT_DATA_VERSION, 0L);
        ClassConfig config = ClassConfigFixture.bind(ClassConfigFixture.valid());

        assertThatCode(
                        () ->
                                config.validateAgainstStoredTiers(
                                        List.of(stored),
                                        id -> Optional.of(CharacterClass.WARRIOR)))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("T117: ein gespeicherter Stand ohne Klasse ist ein Startfehler, nicht eine Annahme")
    void storedTiersWithoutAClassRefuseToStart() throws Exception {
        ClassProgress orphan =
                new ClassProgress(UUID.randomUUID(), 1, 1, ClassProgress.CURRENT_DATA_VERSION, 0L);
        ClassConfig config = ClassConfigFixture.bind(ClassConfigFixture.valid());

        assertThatThrownBy(
                        () -> config.validateAgainstStoredTiers(List.of(orphan), id -> Optional.empty()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("has stored tiers but no class");
    }

    @Test
    @DisplayName("eine verlängerte Leiter ist unproblematisch - niemand verliert etwas")
    void alongerLadderIsFine() throws Exception {
        UUID character = UUID.randomUUID();
        ClassProgress stored =
                new ClassProgress(character, 2, 1, ClassProgress.CURRENT_DATA_VERSION, 0L);
        ClassConfig config = ClassConfigFixture.bind(ClassConfigFixture.valid());

        assertThatCode(
                        () ->
                                config.validateAgainstStoredTiers(
                                        List.of(stored),
                                        id -> Optional.of(CharacterClass.WARRIOR)))
                .doesNotThrowAnyException();
    }

    // --- helpers ----------------------------------------------------------------------------

    private static double healthOf(ClassConfig config, UUID characterId) {
        ClassStatContributor contributor =
                new ClassStatContributor(
                        config,
                        id -> Optional.of(CharacterClass.WARRIOR),
                        id -> 1,
                        id -> Optional.of(ClassProgress.initial(characterId)));
        double[] total = {0.0};
        contributor.contribute(
                new rpg.core.stats.StatHolderView() {
                    @Override
                    public UUID holderId() {
                        return UUID.randomUUID();
                    }

                    @Override
                    public Optional<UUID> characterId() {
                        return Optional.of(characterId);
                    }

                    @Override
                    public Optional<rpg.core.stats.StatSnapshot> previousSnapshot() {
                        return Optional.empty();
                    }
                },
                (attribute, value) -> {
                    if (attribute == Attribute.HEALTH) {
                        total[0] += value;
                    }
                });
        return total[0];
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> eight(
            Map<String, Object> classes, CharacterClass id, String section) {
        return (Map<String, Object>) ClassConfigFixture.blockOf(classes, id).get(section);
    }
}
