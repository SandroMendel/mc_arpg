package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.stats.StatConfig;

/**
 * T069 und T070 - der Cap greift, und eine Änderung kostet genau eine Neuberechnung.
 *
 * <p>Gegen die echte {@code DefaultStatEngine}, nicht gegen eine Attrappe: beide Zusagen sind
 * Eigenschaften der Engine im Zusammenspiel mit diesem Block.
 */
class ClassRecalculationTest {

    @Test
    @DisplayName("T070: ein Levelaufstieg löst genau EINE Neuberechnung aus (FR-011, SC-009)")
    void oneLevelUpIsOneRecalculation() throws Exception {
        ClassEngineFixture fixture = new ClassEngineFixture();
        UUID character = fixture.character(CharacterClass.WARRIOR);
        fixture.clearRecorded();

        fixture.levelTo(character, 2);

        assertThat(fixture.recalculations)
                .as("über alle acht Attribute hinweg genau ein Ereignis")
                .hasSize(1);
    }

    @Test
    @DisplayName("T070: ein Stufenaufstieg löst genau EINE Neuberechnung aus (US3.7, SC-009)")
    void oneTierAdvanceIsOneRecalculation() throws Exception {
        ClassEngineFixture fixture = new ClassEngineFixture();
        UUID character = fixture.character(CharacterClass.WARRIOR);
        fixture.clearRecorded();

        fixture.advance(character, LadderSlot.ARMOR);

        assertThat(fixture.recalculations).hasSize(1);
    }

    @Test
    @DisplayName("zwei Aufstiege sind zwei Neuberechnungen - nicht mehr und nicht weniger")
    void twoAdvancesAreTwoRecalculations() throws Exception {
        ClassEngineFixture fixture = new ClassEngineFixture();
        UUID character = fixture.character(CharacterClass.WARRIOR);
        fixture.clearRecorded();

        fixture.advance(character, LadderSlot.ARMOR);
        fixture.advance(character, LadderSlot.WEAPON);

        assertThat(fixture.recalculations).hasSize(2);
    }

    @Test
    @DisplayName("ein Stufenaufstieg hebt die Werte um genau den Unterschied (US3.2)")
    void advanceRaisesByTheDifference() throws Exception {
        ClassEngineFixture fixture = new ClassEngineFixture();
        UUID character = fixture.character(CharacterClass.WARRIOR);
        double before = fixture.attribute(character, Attribute.HEALTH);

        fixture.advance(character, LadderSlot.ARMOR);

        EquipmentLadder armor =
                fixture.config().definition(CharacterClass.WARRIOR).armorLadder();
        double expectedGain =
                armor.tier(2).valueOf(Attribute.HEALTH) - armor.tier(1).valueOf(Attribute.HEALTH);
        assertThat(fixture.attribute(character, Attribute.HEALTH) - before)
                .isCloseTo(expectedGain, org.assertj.core.data.Offset.offset(0.001));
    }

    @Test
    @DisplayName("die beiden Leitern sind unabhängig (US3.6, FR-019)")
    void laddersAreIndependent() throws Exception {
        ClassEngineFixture fixture = new ClassEngineFixture();
        UUID character = fixture.character(CharacterClass.WARRIOR);
        double physicalBefore = fixture.attribute(character, Attribute.PHYSICAL_DAMAGE);

        fixture.advance(character, LadderSlot.ARMOR);

        assertThat(fixture.attribute(character, Attribute.PHYSICAL_DAMAGE))
                .as("die Rüstung trägt keinen physischen Schaden")
                .isEqualTo(physicalBefore);
    }

    @Test
    @DisplayName("T069: überschreiten die Werte einen Cap, greift der Cap - kein Startfehler (US2.6)")
    void capAppliesInsteadOfFailing() throws Exception {
        ClassEngineFixture fixture = new ClassEngineFixture();
        UUID character = fixture.character(CharacterClass.WARRIOR);

        // Level 60 auf Endstufe: der Warrior liegt bei Health knapp unter dem Cap von 2000.
        fixture.levelTo(character, 60);
        fixture.setTier(character, 5, 6);

        double cap = StatConfig.defaults().definition(Attribute.HEALTH).max();
        assertThat(fixture.attribute(character, Attribute.HEALTH))
                .as("unter dem Cap, weil die Konfiguration es so vorsieht")
                .isLessThanOrEqualTo(cap)
                .isGreaterThan(cap * 0.97);
    }

    @Test
    @DisplayName("T069: ein Wert jenseits des Caps wird geklammert, nicht abgelehnt")
    void beyondTheCapTheValueIsClamped() throws Exception {
        ClassEngineFixture fixture = new ClassEngineFixture();
        UUID character = fixture.character(CharacterClass.MAGE);

        // Der Mage erreicht bei Fähigkeiten-Cooldown genau den harten Cap von 0.40 aus ADR-008.
        fixture.levelTo(character, 60);
        fixture.setTier(character, 7, 7);

        double cap = StatConfig.defaults().definition(Attribute.ABILITY_COOLDOWN).max();
        assertThat(fixture.attribute(character, Attribute.ABILITY_COOLDOWN))
                .as("der harte Cap aus ADR-008 - erreicht, nicht überschritten")
                .isEqualTo(cap);
    }

    @Test
    @DisplayName("die Rollenprofile sind über die echte Engine sichtbar (US2.3)")
    void roleProfilesAreVisibleThroughTheEngine() throws Exception {
        ClassEngineFixture fixture = new ClassEngineFixture();
        UUID warrior = fixture.character(CharacterClass.WARRIOR);
        UUID rogue = fixture.character(CharacterClass.ROGUE);
        UUID mage = fixture.character(CharacterClass.MAGE);
        fixture.levelTo(warrior, 60);
        fixture.setTier(warrior, 5, 6);
        fixture.levelTo(rogue, 60);
        fixture.setTier(rogue, 6, 6);
        fixture.levelTo(mage, 60);
        fixture.setTier(mage, 7, 7);

        assertThat(fixture.attribute(warrior, Attribute.HEALTH))
                .isGreaterThan(fixture.attribute(rogue, Attribute.HEALTH))
                .isGreaterThan(fixture.attribute(mage, Attribute.HEALTH));
        assertThat(fixture.attribute(mage, Attribute.MANA))
                .isGreaterThan(fixture.attribute(warrior, Attribute.MANA));
        assertThat(fixture.attribute(rogue, Attribute.ATTACK_SPEED))
                .isGreaterThan(fixture.attribute(warrior, Attribute.ATTACK_SPEED));
    }
}
