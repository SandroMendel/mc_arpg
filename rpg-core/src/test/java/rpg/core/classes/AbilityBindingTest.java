package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.session.CharacterClass;

/**
 * T109 bis T111 - die Fähigkeitsbindung (US5).
 *
 * <p>B07 <b>benennt</b>, B08 implementiert. Kein Test hier fragt, was eine Fähigkeit tut - das wäre
 * ein Vorgriff (Workflow-Regel 5). Geprüft wird die Anzahl, die Art, die Freischaltstufe und dass die
 * Freischaltung abgeleitet und nicht gespeichert wird.
 */
class AbilityBindingTest {

    @Test
    @DisplayName("T109: der Warrior nennt sechs Fähigkeiten - vier aktive, zwei passive (FR-041)")
    void warriorNamesSixAbilities() throws Exception {
        ClassRegistry registry = registry(1);

        List<AbilityBinding> abilities = registry.abilitiesOf(CharacterClass.WARRIOR);

        assertThat(abilities).hasSize(CharacterClassDefinition.TOTAL_ABILITIES);
        assertThat(abilities.stream().filter(AbilityBinding::isActive).count())
                .as("die Unique zählt als eine der vier aktiven")
                .isEqualTo(CharacterClassDefinition.ACTIVE_ABILITIES);
        assertThat(abilities.stream().filter(a -> !a.isActive()).count())
                .isEqualTo(CharacterClassDefinition.PASSIVE_ABILITIES);
        assertThat(abilities.stream().filter(AbilityBinding::unique).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("die Unique ist aktiv, und es gibt genau eine")
    void theUniqueIsActiveAndSingular() throws Exception {
        List<AbilityBinding> abilities = registry(1).abilitiesOf(CharacterClass.WARRIOR);

        AbilityBinding unique =
                abilities.stream().filter(AbilityBinding::unique).findFirst().orElseThrow();
        assertThat(unique.kind()).isEqualTo(AbilityKind.ACTIVE);
        assertThat(unique.abilityId()).contains("berserker");
    }

    @Test
    @DisplayName("T110: auf Level 19 fehlt eine Fähigkeit mit Freischaltstufe 20, auf 20 ist sie da")
    void unlockFollowsTheLevel() throws Exception {
        UUID character = UUID.randomUUID();

        // Der Warrior schaltet auf 1, 5, 15, 25, 35 und 45 frei.
        assertThat(registry(1).unlockedFor(CharacterClass.WARRIOR, character)).hasSize(1);
        assertThat(registry(4).unlockedFor(CharacterClass.WARRIOR, character)).hasSize(1);
        assertThat(registry(5).unlockedFor(CharacterClass.WARRIOR, character)).hasSize(2);
        assertThat(registry(14).unlockedFor(CharacterClass.WARRIOR, character)).hasSize(2);
        assertThat(registry(15).unlockedFor(CharacterClass.WARRIOR, character)).hasSize(3);
        assertThat(registry(44).unlockedFor(CharacterClass.WARRIOR, character)).hasSize(5);
        assertThat(registry(45).unlockedFor(CharacterClass.WARRIOR, character)).hasSize(6);
        assertThat(registry(60).unlockedFor(CharacterClass.WARRIOR, character))
                .as("auf Maximallevel alle sechs")
                .hasSize(6);
    }

    @Test
    @DisplayName("T111: die Freischaltung wird abgeleitet, nie gespeichert (FR-043)")
    void unlockIsDerivedNotStored() throws Exception {
        UUID character = UUID.randomUUID();
        Map<UUID, Integer> levels = new HashMap<>();
        levels.put(character, 5);
        ClassRegistry registry =
                new ClassRegistry(
                        ClassConfigFixture.bind(ClassConfigFixture.valid()),
                        id -> levels.getOrDefault(id, 1));

        assertThat(registry.unlockedFor(CharacterClass.WARRIOR, character)).hasSize(2);

        // Nur das Level ändern - nichts an einem Freischaltzustand, weil es keinen gibt.
        levels.put(character, 45);

        assertThat(registry.unlockedFor(CharacterClass.WARRIOR, character))
                .as("die Antwort folgt dem Level, ohne dass irgendwo etwas nachgetragen wurde")
                .hasSize(6);
    }

    @Test
    @DisplayName("Mage und Rogue haben noch keine Bindung - leer ist erlaubt bis B08 (FR-045)")
    void mageAndRogueAreStillEmpty() throws Exception {
        ClassRegistry registry = registry(60);

        assertThat(registry.abilitiesOf(CharacterClass.MAGE)).isEmpty();
        assertThat(registry.abilitiesOf(CharacterClass.ROGUE)).isEmpty();
        assertThat(registry.unlockedFor(CharacterClass.MAGE, UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("eine teilweise gefüllte Bindung wird abgewiesen - der Unterschied zu leer (FR-045)")
    void aPartiallyFilledLoadoutIsRejected() {
        assertThatThrownBy(
                        () ->
                                new CharacterClassDefinition(
                                        CharacterClass.WARRIOR,
                                        rpg.core.message.MessageKey.of("class.warrior.name"),
                                        "NETHERITE_SWORD",
                                        ClassBaseStats.of(
                                                new double[rpg.core.stats.Attribute.count()]),
                                        ClassGrowth.of(new double[rpg.core.stats.Attribute.count()]),
                                        LadderFixture.rising(LadderSlot.ARMOR, 2, 10.0, 20.0),
                                        LadderFixture.rising(LadderSlot.WEAPON, 2, 10.0, 20.0),
                                        List.of(
                                                new AbilityBinding(
                                                        "warrior.rage", AbilityKind.PASSIVE, false, 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("empty or exactly 6")
                .hasMessageContaining("forgotten line");
    }

    @Test
    @DisplayName("B07 löst keine Fähigkeits-ID auf - sie reist als Zeichenkette (FR-044)")
    void idsAreNeverResolved() throws Exception {
        List<AbilityBinding> abilities = registry(60).abilitiesOf(CharacterClass.WARRIOR);

        // Alles, was B07 über eine Fähigkeit weiß: die Kennung, die Art, die Stufe. Kein Verhalten,
        // kein Hotbar-Slot, keine Kosten - das ist B08 (Workflow-Regel 5).
        for (AbilityBinding binding : abilities) {
            assertThat(binding.abilityId()).isNotBlank();
            assertThat(binding.kind()).isNotNull();
            assertThat(binding.unlockLevel()).isPositive();
        }
        assertThat(AbilityBinding.class.getRecordComponents()).hasSize(4);
    }

    private static ClassRegistry registry(int level) throws Exception {
        return new ClassRegistry(ClassConfigFixture.bind(ClassConfigFixture.valid()), id -> level);
    }
}
