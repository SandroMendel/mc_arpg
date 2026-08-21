package rpg.persistence.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.ClassConfig;
import rpg.core.classes.ClassProgress;
import rpg.core.classes.LadderSlot;
import rpg.core.persistence.AggregateType;
import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.core.session.CharacterClass;
import rpg.core.session.PlayerCharacter;
import rpg.persistence.support.PersistenceHarness;

/**
 * T132 / V19: eine Leiter, die kürzer ist als der Stand eines Charakters, bricht den Start ab.
 *
 * <p>Die einzige der neunzehn Zusagen aus dem Vertragsdokument, die eine Datenbank braucht - alle
 * anderen prüfen die Konfiguration gegen sich selbst, diese prüft sie gegen die Wirklichkeit.
 *
 * <p>Der Sinn ist das Abbrechen. Einen Charakter still auf die neue Höchststufe herabzusetzen nähme ihm
 * etwas weg, das er erspielt hat, und zwar auf dem leisesten denkbaren Weg: einer Balancing-Änderung.
 */
class TierLengthGuardTest {

    private static final Logger QUIET = Logger.getLogger("tier-length-guard-test");

    private PersistenceHarness harness;
    private JdbcClassProgressRepository repository;

    @BeforeEach
    void setUp() {
        QUIET.setLevel(Level.OFF);
        harness = new PersistenceHarness();
        repository =
                new JdbcClassProgressRepository(
                        harness.pools.loginPool(),
                        harness.scheduler,
                        harness.flushCycle,
                        Clock.systemUTC());
        harness.flushCycle.register(AggregateType.CHARACTER_CLASS_PROGRESS, repository);
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("die gespeicherten Stufen werden mit ihrer Klasse gelesen")
    void storedTiersAreReadWithTheirClass() {
        UUID characterId = storeTiers(CharacterClass.WARRIOR, 3, 4);

        JdbcClassProgressRepository.StoredTiers stored =
                repository.readAll(harness.pools.loginPool());

        assertThat(stored.tiers())
                .anySatisfy(
                        progress -> {
                            assertThat(progress.characterId()).isEqualTo(characterId);
                            assertThat(progress.armorTier()).isEqualTo(3);
                            assertThat(progress.weaponTier()).isEqualTo(4);
                        });
        assertThat(stored.classOf().apply(characterId))
                .as("die Klasse kommt aus rpg.character, nicht aus dieser Tabelle (ADR-019)")
                .hasValue(CharacterClass.WARRIOR);
    }

    @Test
    @DisplayName("eine gekürzte Rüstungsleiter bricht ab und nennt Charakter, Slot und beide Zahlen")
    void ashortenedArmorLadderRefusesTheStart() {
        UUID characterId = storeTiers(CharacterClass.WARRIOR, 3, 1);
        // Zwei Stufen, der Charakter steht auf drei.
        ClassConfig shortened = configWithLadderLength(2);

        assertThatThrownBy(() -> validate(shortened, characterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(characterId.toString())
                .hasMessageContaining(LadderSlot.ARMOR.configKey())
                .hasMessageContaining("tier 3")
                .hasMessageContaining("only 2");
    }

    @Test
    @DisplayName("eine gekürzte Waffenleiter bricht genauso ab - beide Slots werden geprüft")
    void ashortenedWeaponLadderRefusesTheStart() {
        UUID characterId = storeTiers(CharacterClass.ROGUE, 1, 5);

        assertThatThrownBy(() -> validate(configWithLadderLength(2), characterId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining(LadderSlot.WEAPON.configKey());
    }

    @Test
    @DisplayName("eine Leiter, die lang genug ist, lässt den Start durch")
    void aLongEnoughLadderPasses() {
        UUID characterId = storeTiers(CharacterClass.WARRIOR, 2, 2);

        assertThatCode(() -> validate(configWithLadderLength(2), characterId))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("ein Charakter auf Stufe 1 kommt mit der kürzest möglichen Leiter durch")
    void tierOnePassesTheShortestLadder() {
        UUID characterId = storeTiers(CharacterClass.MAGE, 1, 1);

        assertThatCode(() -> validate(configWithLadderLength(2), characterId))
                .doesNotThrowAnyException();
    }

    // --- fixtures ---

    /**
     * Prüft nur die Zeile dieses einen Charakters.
     *
     * <p>Die Testdatenbank ist zwischen den Testklassen geteilt, also stehen dort auch Zeilen anderer
     * Tests. Über alle zu prüfen würde diesen Test von deren Werten und ihrer Reihenfolge abhängig
     * machen - und ein Test, der von fremden Daten abhängt, meldet irgendwann einen Fehler, den es
     * nicht gibt.
     */
    private void validate(ClassConfig config, UUID characterId) {
        JdbcClassProgressRepository.StoredTiers stored =
                repository.readAll(harness.pools.loginPool());
        config.validateAgainstStoredTiers(
                stored.tiers().stream()
                        .filter(progress -> progress.characterId().equals(characterId))
                        .toList(),
                stored.classOf());
    }

    /**
     * Eine gültige Konfiguration, in der jede Leiter genau {@code length} Stufen hat.
     *
     * <p>Hier statt aus den Kerntests: {@code ClassConfigFixture} liegt in {@code rpg-core}s
     * Testquellen und ist von hier nicht sichtbar. Sie nachzubauen ist ehrlicher, als eine
     * Test-Fixture über eine Modulgrenze zu veröffentlichen, die es aus gutem Grund gibt.
     */
    private static ClassConfig configWithLadderLength(int length) {
        java.util.Map<CharacterClass, rpg.core.classes.CharacterClassDefinition> definitions =
                new java.util.EnumMap<>(CharacterClass.class);
        for (CharacterClass id : CharacterClass.values()) {
            definitions.put(
                    id,
                    new rpg.core.classes.CharacterClassDefinition(
                            id,
                            displayNameKeyOf(id),
                            "STONE",
                            rpg.core.classes.ClassBaseStats.of(
                                    new double[rpg.core.stats.Attribute.count()]),
                            rpg.core.classes.ClassGrowth.of(
                                    new double[rpg.core.stats.Attribute.count()]),
                            ladder(LadderSlot.ARMOR, length, id),
                            ladder(LadderSlot.WEAPON, length, id),
                            java.util.List.of()));
        }
        return ClassConfig.of(definitions);
    }

    /** Je Klasse eine eigene Familie, das gemeinsame LEATHER voran (V11). */
    private static String[] armorFamilyOf(CharacterClass id) {
        return switch (id) {
            case WARRIOR -> new String[] {"LEATHER", "COPPER", "IRON", "DIAMOND", "NETHERITE"};
            case ROGUE -> new String[] {"LEATHER", "GOLDEN", "CHAINMAIL"};
            case MAGE -> new String[] {"LEATHER", "TURTLE"};
        };
    }

    private static rpg.core.message.MessageKey displayNameKeyOf(CharacterClass id) {
        return switch (id) {
            case WARRIOR -> rpg.core.classes.ClassMessageKeys.WARRIOR_NAME;
            case MAGE -> rpg.core.classes.ClassMessageKeys.MAGE_NAME;
            case ROGUE -> rpg.core.classes.ClassMessageKeys.ROGUE_NAME;
        };
    }

    /**
     * Streng steigende Werte, Stufe 1 auf Level 1, je Stufe ein anderes Material (V6, V7).
     *
     * <p>Die Rüstungsmaterialien sind je Klasse verschieden, weil V11 das verlangt: außer dem
     * gemeinsamen Einstiegsmaterial gehört ein Rüstungssatz höchstens einer Klasse, sonst sehen zwei
     * Klassen gleich aus. Für Waffen gilt das nicht - Warrior und Rogue teilen sich die Schwerter.
     */
    private static rpg.core.classes.EquipmentLadder ladder(
            LadderSlot slot, int length, CharacterClass owner) {
        String[] materials =
                slot == LadderSlot.WEAPON
                        ? new String[] {
                            "WOODEN_SWORD", "STONE_SWORD", "IRON_SWORD", "DIAMOND_SWORD",
                            "NETHERITE_SWORD", "GOLDEN_SWORD", "WOODEN_AXE"
                        }
                        : armorFamilyOf(owner);
        java.util.List<rpg.core.classes.EquipmentTier> tiers = new java.util.ArrayList<>(length);
        for (int index = 1; index <= length; index++) {
            java.util.Map<rpg.core.stats.Attribute, Double> values =
                    new java.util.EnumMap<>(rpg.core.stats.Attribute.class);
            for (rpg.core.stats.Attribute carried : slot.carried()) {
                values.put(carried, index * 10.0);
            }
            tiers.add(
                    rpg.core.classes.EquipmentTier.of(
                            index,
                            slot,
                            values,
                            rpg.core.classes.TierAppearance.ofMaterial(
                                    materials[(index - 1) % materials.length]),
                            index == 1 ? 1 : index * 5,
                            java.util.Map.of()));
        }
        return rpg.core.classes.EquipmentLadder.of(slot, tiers);
    }

    /** Legt einen Charakter samt gespeicherter Stufen an und schreibt beides wirklich. */
    private UUID storeTiers(CharacterClass characterClass, int armorTier, int weaponTier) {
        UUID playerId = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(playerId, Clock.systemUTC().instant()));
        harness.flushCycle.flushNow(FlushReason.SESSION_END).join();
        PlayerCharacter character = harness.characters.create(playerId, characterClass).join();
        UUID characterId = character.characterId();

        repository.setLiveSource(
                id ->
                        Optional.of(
                                new ClassProgress(
                                        id,
                                        armorTier,
                                        weaponTier,
                                        ClassProgress.CURRENT_DATA_VERSION,
                                        0L)));
        repository.markDirty(characterId);
        harness.flushCycle.flushNow(FlushReason.SESSION_END).join();
        return characterId;
    }
}
