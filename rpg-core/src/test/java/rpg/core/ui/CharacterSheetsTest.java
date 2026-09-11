package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.LadderSlot;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.stats.StatSnapshot;

/**
 * Die Charakterübersicht, serverlos (T069–T072).
 *
 * <p>Sie ist das einzige wirklich <em>fehlende</em> Fenster dieses Blocks: vier Blöcke — B04, B07,
 * B08b, B11 — haben Daten, die nirgendwo zusammen zu sehen sind.
 */
class CharacterSheetsTest {

    private final UiFixtures.Identities ids = UiFixtures.identities();

    @Test
    @DisplayName("T069: die Uebersicht zeigt JEDES Attribut aus B04")
    void itShowsEveryAttribute() {
        // Nicht die, die gerade ungleich null sind. Eine Uebersicht, die eine Zeile weglaesst, weil
        // ihr Wert 0 ist, laesst den Spieler raten, ob es das Attribut gibt - und beim naechsten
        // Ausruestungsstueck taucht es unvermittelt auf.
        CharacterSheet sheet = sheets().of(ids.characterId()).orElseThrow();

        for (Attribute attribute : Attribute.values()) {
            assertThat(sheet.attributes()).as("Attribut " + attribute).containsKey(attribute);
        }
    }

    @Test
    @DisplayName("T069: die Zahl der Attribute steht nirgends im Code")
    void thecountIsNotHardcoded() {
        // Es sind heute zehn. Die Spec nannte urspruenglich acht - die Zahl stammte aus einem
        // Roadmap-Ziel und war nie eine Aufzaehlung. Der Test prueft die Quelle, nicht die
        // Erinnerung an sie.
        CharacterSheet sheet = sheets().of(ids.characterId()).orElseThrow();

        assertThat(sheet.attributes()).hasSize(Attribute.values().length);
    }

    @Test
    @DisplayName("T069: eine unvollstaendige Attributliste wird abgewiesen")
    void anincompleteAttributeMapIsRejected() {
        Map<Attribute, Double> incomplete = new EnumMap<>(Attribute.class);
        incomplete.put(Attribute.HEALTH, 100.0);

        assertThatThrownBy(
                        () ->
                                new CharacterSheet(
                                        ids.characterId(),
                                        CharacterClass.WARRIOR,
                                        1,
                                        0,
                                        incomplete,
                                        1L,
                                        Map.of(),
                                        Map.of(),
                                        Map.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("jedes Attribut");
    }

    @Test
    @DisplayName("T070: bei drei Charakteren erscheint der aktive, keine Summe")
    void withThreeCharactersOnlyTheActiveOneShows() {
        // FR-053. Eine Summe waere eine Zahl, die keinem der drei gehoert - und sie saehe aus wie
        // ein Fehler in dem Moment, in dem er den Charakter wechselt und alles kleiner wird.
        Sources sources = new Sources();
        UUID active = ids.characterId();
        UUID second = UUID.randomUUID();
        UUID third = UUID.randomUUID();
        sources.give(active, 100.0, 1L, CharacterClass.WARRIOR, 12, 250L);
        sources.give(second, 999.0, 1L, CharacterClass.MAGE, 40, 9999L);
        sources.give(third, 555.0, 1L, CharacterClass.ROGUE, 30, 5555L);

        CharacterSheet sheet = new CharacterSheets(sources, sources, sources::classOf, sources::levelOf, sources::coinsOf)
                .of(active)
                .orElseThrow();

        assertThat(sheet.characterId()).isEqualTo(active);
        assertThat(sheet.valueOf(Attribute.HEALTH)).isEqualTo(100.0);
        assertThat(sheet.coins()).isEqualTo(250L);
        assertThat(sheet.level()).isEqualTo(12);
    }

    @Test
    @DisplayName("T071: bei gleicher Revision gilt der Stand weiter")
    void thesameRevisionMeansTheSheetStillHolds() {
        // FR-054: zwischenspeichern und nur bei Aenderung neu aufbauen. Die Marke ist
        // StatSnapshot.revision() - die einzige Antwort auf "hat sich etwas geaendert", die B13
        // nicht selbst erfinden muss.
        CharacterSheet sheet = sheets().of(ids.characterId()).orElseThrow();

        assertThat(sheet.isCurrent(sheet.revision())).isTrue();
    }

    @Test
    @DisplayName("T071: bei neuer Revision gilt er nicht mehr")
    void anewRevisionInvalidatesIt() {
        CharacterSheet sheet = sheets().of(ids.characterId()).orElseThrow();

        assertThat(sheet.isCurrent(sheet.revision() + 1)).isFalse();
    }

    @Test
    @DisplayName("T071: die Revision kommt aus B04 und wird nicht selbst gezaehlt")
    void therevisionComesFromB04() {
        // Ein eigener Zaehler waere eine zweite Wahrheit darueber, wann sich etwas geaendert hat -
        // und er ginge auseinander, sobald B04 einmal ohne Ereignis neu rechnet.
        Sources sources = new Sources();
        sources.give(ids.characterId(), 100.0, 4711L, CharacterClass.WARRIOR, 1, 0L);

        CharacterSheet sheet =
                new CharacterSheets(sources, sources, sources::classOf, sources::levelOf, sources::coinsOf)
                        .of(ids.characterId())
                        .orElseThrow();

        assertThat(sheet.revision()).isEqualTo(4711L);
    }

    @Test
    @DisplayName("T072: ABILITY_COOLDOWN ist das prozentual gelesene Attribut")
    void abilityCooldownIsTheProportionalOne() {
        // Nicht jedes Attribut ist eine Stueckzahl. Ohne eigene Zeile stuende bei einer
        // Cooldown-Reduktion eine nackte 0.15, und der Spieler rechnete selbst.
        //
        // Der Test steht in rpg-core, weil die ENTSCHEIDUNG hierher gehoert: welches Attribut
        // prozentual gelesen wird, ist eine Eigenschaft des Attributs und keine des Fensters.
        assertThat(UiMessageKeys.attributeLabel(Attribute.ABILITY_COOLDOWN).value())
                .isEqualTo("ui.attribute.ability-cooldown");
        assertThat(UiMessageKeys.all()).contains(UiMessageKeys.SHEET_ATTRIBUTE_PERCENT);
    }

    @Test
    @DisplayName("ohne Charakter in B04 gibt es keine Uebersicht")
    void withoutASnapshotThereIsNoSheet() {
        assertThat(sheets().of(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("ohne Klasse in B07 gibt es keine Uebersicht")
    void withoutAClassThereIsNoSheet() {
        // Zwischen Anmeldung und Klassenwahl. Ein normaler Zustand, kein Fehler - und Nullen, die
        // wie echte Werte aussehen, sind in einem Fenster noch schlimmer als auf einer Flaeche.
        Sources sources = new Sources();
        sources.giveSnapshotOnly(ids.characterId(), 100.0, 1L);

        assertThat(
                        new CharacterSheets(
                                        sources,
                                        sources,
                                        sources::classOf,
                                        sources::levelOf,
                                        sources::coinsOf)
                                .of(ids.characterId()))
                .isEmpty();
    }

    @Test
    @DisplayName("die Uebersicht traegt B07s AUSSEHEN, keinen Materialnamen")
    void thesheetCarriesTheAppearanceNotAMaterialName() {
        // DER TEST, DER GEFEHLT HAT (Serverabnahme, Schritt 11).
        //
        // Die Ruestungsleiter nennt FAMILIEN - LEATHER, COPPER, IRON, DIAMOND, NETHERITE -, keine
        // Bukkit-Materialien. Ein erster Entwurf reichte appearance.material() als String durch;
        // aus "IRON" wurde in der Paper-Schicht Material.matchMaterial("IRON") und damit null, und
        // der Platz blieb leer.
        //
        // Warum das kein Test gefunden hat: die alten Tests reichten "IRON_CHESTPLATE" durch - ein
        // Name, den es GIBT. Sie haben also einen Wert geprueft, den die Wirklichkeit nie liefert.
        // Und im Spiel ging es bei einer von drei Klassen, weil die Leiter des Magiers durchgehend
        // LEATHER ist und das zufaellig auch ein Material.
        //
        // Der Typ ist die Abhilfe: TierAppearance KANN keine Familie mit einem Material
        // verwechseln, weil es keinen String mehr durchreicht - und es traegt Farbe und Trim mit,
        // die der Name ohnehin verloren haette.
        Sources sources = new Sources();
        sources.give(ids.characterId(), 100.0, 1L, CharacterClass.WARRIOR, 12, 250L);
        sources.wear(ids.characterId(), LadderSlot.ARMOR, "IRON");

        CharacterSheet sheet =
                new CharacterSheets(
                                sources,
                                sources,
                                sources::classOf,
                                sources::levelOf,
                                sources::coinsOf)
                        .of(ids.characterId())
                        .orElseThrow();

        assertThat(sheet.equipmentOn(LadderSlot.ARMOR))
                .as("der Platz ist belegt - IRON ist eine Familie und trotzdem gueltig")
                .isPresent();
        assertThat(sheet.equipmentOn(LadderSlot.ARMOR).orElseThrow().material())
                .as("die FAMILIE kommt durch, nicht ein zusammengebauter Materialname")
                .isEqualTo("IRON");
        assertThat(sheet.tagOn(LadderSlot.ARMOR))
                .as("und der Bindungsvermerk, den BoundItemFactory zum Bauen braucht")
                .isPresent();
    }

    @Test
    @DisplayName("ein leerer Ausruestungsplatz gilt als voll erhalten")
    void anemptySlotCountsAsPristine() {
        // Ein leeres Optional zwaenge jede Aufrufstelle zu derselben Fallunterscheidung, und
        // "nichts abgenutzt" ist fuer "da ist nichts" die richtige Antwort.
        CharacterSheet sheet = sheets().of(ids.characterId()).orElseThrow();

        assertThat(sheet.equipmentOn(LadderSlot.WEAPON)).isEmpty();
        assertThat(sheet.conditionOn(LadderSlot.WEAPON)).isEqualTo(1.0);
    }

    @Test
    @DisplayName("ein Zustand ausserhalb von [0,100] wird abgewiesen")
    void anoutOfRangeConditionIsRejected() {
        // PROZENT, nicht Anteil: WearCurve.FULL ist 100.0. Ein erster Entwurf nahm hier [0,1] an -
        // damit waere jedes getragene Stueck abgewiesen worden, sobald sein Zustand ueber 1 liegt,
        // also praktisch immer. Aufgefallen ist es erst beim Test gegen B11s echte Bauteile.
        assertThatThrownBy(
                        () ->
                                new CharacterSheet(
                                        ids.characterId(),
                                        CharacterClass.WARRIOR,
                                        1,
                                        0,
                                        CharacterSheet.emptyAttributes(),
                                        1L,
                                        Map.of(),
                                        Map.of(),
                                        Map.of(LadderSlot.ARMOR, 150.0)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ARMOR");
    }

    @Test
    @DisplayName("ein Zustand von 40 ist gueltig - das ist ein abgenutztes Stueck, kein Fehler")
    void awornConditionIsValid() {
        // Die Gegenprobe zum Test darueber, und die eigentlich wichtige: mit der falschen Skala
        // waere sie rot, und zwar fuer jeden Gegenstand, den ein Spieler jemals benutzt hat.
        assertThatCode(
                        () ->
                                new CharacterSheet(
                                        ids.characterId(),
                                        CharacterClass.WARRIOR,
                                        1,
                                        0,
                                        CharacterSheet.emptyAttributes(),
                                        1L,
                                        Map.of(),
                                        Map.of(),
                                        Map.of(LadderSlot.ARMOR, 40.0)))
                .doesNotThrowAnyException();
    }

    // --- Aufbau -------------------------------------------------------------

    private CharacterSheets sheets() {
        Sources sources = new Sources();
        sources.give(ids.characterId(), 100.0, 1L, CharacterClass.WARRIOR, 12, 250L);
        return new CharacterSheets(
                sources, sources, sources::classOf, sources::levelOf, sources::coinsOf);
    }

    /** Alle fünf Quellen in einer Klasse — so klein wie die Fragen, die sie beantworten. */
    private static final class Sources
            implements CharacterSheets.StatSource, CharacterSheets.EquipmentSource {

        private final Map<UUID, StatSnapshot> snapshots = new HashMap<>();
        private final Map<UUID, CharacterClass> classes = new HashMap<>();
        private final Map<UUID, Integer> levels = new HashMap<>();
        private final Map<UUID, Long> coins = new HashMap<>();
        private final Map<UUID, Map<LadderSlot, rpg.core.classes.TierAppearance>> worn =
                new HashMap<>();

        void give(
                UUID characterId,
                double health,
                long revision,
                CharacterClass characterClass,
                int level,
                long balance) {
            giveSnapshotOnly(characterId, health, revision);
            classes.put(characterId, characterClass);
            levels.put(characterId, level);
            coins.put(characterId, balance);
        }

        void giveSnapshotOnly(UUID characterId, double health, long revision) {
            double[] values = new double[Attribute.values().length];
            values[Attribute.HEALTH.ordinal()] = health;
            snapshots.put(characterId, new StatSnapshot(values, revision));
        }

        @Override
        public Optional<StatSnapshot> snapshotOf(UUID characterId) {
            return Optional.ofNullable(snapshots.get(characterId));
        }

        /** Was der Charakter traegt — B07s Aussehen, keine Vorlagenkennung. */
        void wear(UUID characterId, LadderSlot slot, String materialFamily) {
            worn.computeIfAbsent(characterId, id -> new HashMap<>())
                    .put(slot, rpg.core.classes.TierAppearance.ofMaterial(materialFamily));
        }

        @Override
        public Map<LadderSlot, rpg.core.classes.TierAppearance> equipmentOf(UUID characterId) {
            return worn.getOrDefault(characterId, Map.of());
        }

        @Override
        public Optional<String> tagOf(UUID characterId, LadderSlot slot) {
            return Optional.of("test-tag");
        }

        @Override
        public double conditionOf(UUID characterId, LadderSlot slot) {
            return 1.0;
        }

        Optional<CharacterClass> classOf(UUID characterId) {
            return Optional.ofNullable(classes.get(characterId));
        }

        int levelOf(UUID characterId) {
            return levels.getOrDefault(characterId, 1);
        }

        long coinsOf(UUID characterId) {
            return coins.getOrDefault(characterId, 0L);
        }
    }
}
