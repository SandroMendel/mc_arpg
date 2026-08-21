package rpg.core.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.session.CharacterClass;

/**
 * T083, T101, T102, T103 - der Sollzustand und das Bindungsprädikat.
 *
 * <p>Das Prädikat liegt im Pfad <b>jedes</b> Inventarklicks. Deshalb prüft dieser Test nicht nur, was
 * es antwortet, sondern auch, dass es das ohne Datenbank und ohne vermeidbare Objekterzeugung tut.
 */
class BoundEquipmentTest {

    @Test
    @DisplayName("T083: der Sollzustand folgt eindeutig aus Klasse und Stufen (FR-023)")
    void expectedFollowsFromClassAndTiers() throws Exception {
        Fixture fixture = new Fixture();
        UUID character = fixture.character(CharacterClass.WARRIOR, 1, 1);

        Map<LadderSlot, TierAppearance> expected =
                fixture.bound.expectedFor(character).orElseThrow();

        assertThat(expected.get(LadderSlot.ARMOR).material()).isEqualTo("LEATHER");
        assertThat(expected.get(LadderSlot.WEAPON).material()).isEqualTo("WOODEN_SWORD");
    }

    @Test
    @DisplayName("eine höhere Stufe ergibt ein anderes Aussehen - die Stufe erzeugt das Item")
    void ahigherTierYieldsADifferentAppearance() throws Exception {
        Fixture fixture = new Fixture();
        UUID warrior = fixture.character(CharacterClass.WARRIOR, 5, 6);

        Map<LadderSlot, TierAppearance> expected = fixture.bound.expectedFor(warrior).orElseThrow();

        assertThat(expected.get(LadderSlot.ARMOR).material()).isEqualTo("NETHERITE");
        assertThat(expected.get(LadderSlot.WEAPON).material()).isEqualTo("NETHERITE_SWORD");
    }

    @Test
    @DisplayName("beim Mage trägt die Farbe die Stufe, nicht das Material (FR-016a)")
    void forTheMageColourCarriesTheTier() throws Exception {
        Fixture fixture = new Fixture();
        UUID mage = fixture.character(CharacterClass.MAGE, 1, 1);
        UUID topMage = fixture.character(CharacterClass.MAGE, 7, 7);

        TierAppearance first = fixture.bound.expectedFor(mage).orElseThrow().get(LadderSlot.ARMOR);
        TierAppearance last = fixture.bound.expectedFor(topMage).orElseThrow().get(LadderSlot.ARMOR);

        assertThat(first.material()).isEqualTo(last.material()).isEqualTo("LEATHER");
        assertThat(first.rgb()).isNotEqualTo(last.rgb());
        assertThat(first.looksLike(last)).isFalse();
    }

    @Test
    @DisplayName("beim Rogue trägt ab Stufe 4 der Trim die Stufe (FR-016a)")
    void forTheRogueTrimCarriesTheTier() throws Exception {
        Fixture fixture = new Fixture();
        UUID third = fixture.character(CharacterClass.ROGUE, 3, 1);
        UUID fourth = fixture.character(CharacterClass.ROGUE, 4, 1);

        TierAppearance withoutTrim =
                fixture.bound.expectedFor(third).orElseThrow().get(LadderSlot.ARMOR);
        TierAppearance withTrim =
                fixture.bound.expectedFor(fourth).orElseThrow().get(LadderSlot.ARMOR);

        assertThat(withoutTrim.material()).isEqualTo(withTrim.material()).isEqualTo("CHAINMAIL");
        assertThat(withoutTrim.hasTrim()).isFalse();
        assertThat(withTrim.hasTrim()).isTrue();
    }

    @Test
    @DisplayName("T101: das Prädikat antwortet ja für beide gebundenen Slots, nein für alles andere")
    void predicateAnswersForBothSlotsAndNothingElse() throws Exception {
        Fixture fixture = new Fixture();
        UUID character = fixture.character(CharacterClass.WARRIOR, 1, 1);

        String armorTag = fixture.bound.expectedTag(character, LadderSlot.ARMOR).orElseThrow();
        String weaponTag = fixture.bound.expectedTag(character, LadderSlot.WEAPON).orElseThrow();

        assertThat(fixture.bound.isBoundTo(armorTag, character)).isTrue();
        assertThat(fixture.bound.isBoundTo(weaponTag, character)).isTrue();
        assertThat(fixture.bound.isBoundTo(null, character)).as("Beute ohne Marke").isFalse();
        assertThat(fixture.bound.isBoundTo("etwas anderes", character)).isFalse();
    }

    @Test
    @DisplayName("T102: ein für einen ANDEREN Charakter gebundenes Item gilt hier als nicht gebunden")
    void anotherCharactersItemIsNotBoundHere() throws Exception {
        Fixture fixture = new Fixture();
        UUID mine = fixture.character(CharacterClass.WARRIOR, 1, 1);
        UUID theirs = fixture.character(CharacterClass.MAGE, 1, 1);

        String theirTag = fixture.bound.expectedTag(theirs, LadderSlot.ARMOR).orElseThrow();

        assertThat(fixture.bound.isBoundTo(theirTag, mine))
                .as("eine Kopie gehört einem anderen Charakter und ist damit wertlos")
                .isFalse();
        assertThat(fixture.bound.isBound(theirTag))
                .as("gebunden ist sie trotzdem - nur nicht an mich")
                .isTrue();
    }

    @Test
    @DisplayName("isBound erkennt eine fremde oder kaputte Marke nicht als Bindung")
    void malformedTagsAreNotBindings() throws Exception {
        Fixture fixture = new Fixture();

        assertThat(fixture.bound.isBound(null)).isFalse();
        assertThat(fixture.bound.isBound("")).isFalse();
        assertThat(fixture.bound.isBound("WARRIOR")).isFalse();
        assertThat(fixture.bound.isBound("WARRIOR|ARMOR")).isFalse();
        assertThat(fixture.bound.isBound("WARRIOR|ARMOR|")).isFalse();
        assertThat(fixture.bound.isBound("PALADIN|ARMOR|" + UUID.randomUUID())).isFalse();
        assertThat(fixture.bound.isBound("WARRIOR|BOOTS|" + UUID.randomUUID())).isFalse();
        assertThat(fixture.bound.isBound("WARRIOR|ARMOR|" + UUID.randomUUID())).isTrue();
    }

    @Test
    @DisplayName("T103: 10 000 Abfragen werden vollständig aus dem Speicher bedient (SC-010)")
    void tenThousandQueriesAreServedFromMemory() throws Exception {
        Fixture fixture = new Fixture();
        UUID character = fixture.character(CharacterClass.WARRIOR, 1, 1);
        String tag = fixture.bound.expectedTag(character, LadderSlot.ARMOR).orElseThrow();
        fixture.lookups = 0;

        for (int i = 0; i < 10_000; i++) {
            assertThat(fixture.bound.isBoundTo(tag, character)).isTrue();
        }

        // Jede Abfrage schlägt genau einmal in der Klassenzuordnung nach - dem Sitzungs-Cache, der
        // während der Sitzung autoritativ ist (Prinzip IV). Kein Repository, kein Fortschrittszugriff.
        assertThat(fixture.lookups).isEqualTo(10_000);
        assertThat(fixture.progressLookups)
                .as("der Fortschritt wird für das Prädikat gar nicht gebraucht")
                .isZero();
    }

    @Test
    @DisplayName("T103: das Prädikat hat kein Repository - das ist eine Übersetzungszeit-Zusage")
    void thePredicateHasNoRepositoryAtAll() {
        // Der Konstruktor nimmt die Konfiguration und zwei Lesefunktionen. Es gibt keinen Weg, von
        // hier aus die Datenbank zu erreichen, und deshalb kann kein Inventarklick es tun.
        assertThat(BoundEquipment.class.getConstructors()).hasSize(1);
        assertThat(BoundEquipment.class.getConstructors()[0].getParameterCount()).isEqualTo(3);
        for (Class<?> parameter : BoundEquipment.class.getConstructors()[0].getParameterTypes()) {
            assertThat(parameter.getName()).doesNotContain("Repository");
        }
    }

    @Test
    @DisplayName("ein unbekannter Charakter hat keinen Sollzustand statt einen erfundenen")
    void unknownCharacterHasNoExpectedState() throws Exception {
        Fixture fixture = new Fixture();

        assertThat(fixture.bound.expectedFor(UUID.randomUUID())).isEmpty();
        assertThat(fixture.bound.expectedTag(UUID.randomUUID(), LadderSlot.ARMOR)).isEmpty();
    }

    @Test
    @DisplayName("eine Stufe jenseits der Leiter klemmt statt zu werfen (FR-024 ist die Vorderseite)")
    void tierBeyondTheLadderIsClamped() throws Exception {
        Fixture fixture = new Fixture();
        UUID character = fixture.character(CharacterClass.WARRIOR, 99, 99);

        Map<LadderSlot, TierAppearance> expected =
                fixture.bound.expectedFor(character).orElseThrow();

        assertThat(expected.get(LadderSlot.ARMOR).material()).isEqualTo("NETHERITE");
    }

    // --- fixture ----------------------------------------------------------------------------

    private static final class Fixture {
        final Map<UUID, CharacterClass> classes = new HashMap<>();
        final Map<UUID, ClassProgress> progress = new HashMap<>();
        final BoundEquipment bound;
        int lookups;
        int progressLookups;

        Fixture() throws Exception {
            bound =
                    new BoundEquipment(
                            ClassConfigFixture.bind(ClassConfigFixture.valid()),
                            id -> {
                                lookups++;
                                return Optional.ofNullable(classes.get(id));
                            },
                            id -> {
                                progressLookups++;
                                return Optional.ofNullable(progress.get(id));
                            });
        }

        UUID character(CharacterClass id, int armorTier, int weaponTier) {
            UUID characterId = UUID.randomUUID();
            classes.put(characterId, id);
            progress.put(
                    characterId,
                    new ClassProgress(
                            characterId,
                            armorTier,
                            weaponTier,
                            ClassProgress.CURRENT_DATA_VERSION,
                            0L));
            return characterId;
        }
    }
}
