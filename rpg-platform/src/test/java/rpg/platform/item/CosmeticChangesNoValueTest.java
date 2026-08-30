package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.RecordComponent;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.LadderSlot;
import rpg.core.classes.TierAppearance;
import rpg.core.event.DefaultEventBus;
import rpg.core.item.CosmeticAppearance;
import rpg.core.item.CosmeticApplication;
import rpg.core.item.CosmeticRepository;
import rpg.core.item.CosmeticUnlock;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemConfig;
import rpg.core.item.ItemTemplate;
import rpg.core.item.LootTables;
import rpg.core.item.Rarity;
import rpg.core.item.RepairPricing;
import rpg.core.item.WearCurve;

/**
 * SC-014, FR-070 — <b>nach dem Anwenden ist kein einziger Wert anders.</b>
 *
 * <p>Das ist die Zusage, die Kosmetik überhaupt erst erlaubt. Eine Trimfarbe, die auch nur ein
 * Prozent Schaden brächte, wäre keine Kosmetik mehr, sondern eine Stufe — und der ganze Grund für
 * FR-069, den Stufentrim nicht zu überschreiben, hinge in der Luft.
 *
 * <p><b>Geprüft in zwei Richtungen.</b> Einmal am Ergebnis: von den fünf Feldern einer
 * {@link TierAppearance} ändern sich genau die zwei Trim-Felder. Und einmal an der Bauart:
 * {@code TierAppearance} trägt überhaupt keinen Wert — die Attribute liegen in
 * {@code EquipmentTier}, und dorthin führt von hier kein Weg. Die zweite Prüfung ist die
 * haltbarere: sie bleibt richtig, auch wenn jemand der Erscheinung ein Feld hinzufügt.
 */
class CosmeticChangesNoValueTest {

    private static final UUID CHARACTER = UUID.randomUUID();
    private static final String EMBER = "trim.ember";

    private CosmeticOverride override;

    @BeforeEach
    void setUp() {
        CosmeticApplication cosmetics =
                new CosmeticApplication(
                        CosmeticChangesNoValueTest::config,
                        new NoRepository(),
                        (characterId, slot) -> true,
                        new DefaultEventBus(java.util.logging.Logger.getLogger("quiet")),
                        Clock.systemUTC());
        cosmetics.put(CHARACTER, List.of());
        cosmetics.grant(CHARACTER, EMBER);
        cosmetics.apply(CHARACTER, EMBER);
        override = new CosmeticOverride(cosmetics);
    }

    @Test
    @DisplayName("die WAFFE bekommt keinen Trim - ein Schwert hat keine ArmorMeta")
    void theweaponNeverGetsATrim() {
        // GEFUNDEN BEIM TESTSPIEL (2026-08-30), und es war ein Ausruestungsverlust:
        //
        //   IllegalStateException: NETHERITE_SWORD cannot carry a trim, but one was configured
        //     at BoundItemFactory.applyTrim
        //     at BoundItemFactory.weapon
        //     at ClassEquipmentApplier.applyWeapon
        //
        // Ein Trim ist in Vanilla eine RUESTUNGSverzierung. CosmeticOverride hat ihn aber auf jede
        // Ausruestung geschrieben, die es bekam - auch auf den Waffenplatz. Die Ausnahme flog
        // heraus, NACHDEM die Ruestung gesetzt war und BEVOR die Waffe gesetzt wurde: fuer den
        // Spieler war das Schwert weg.
        //
        // Warum kein Test das fand: dieser hier rief bis dahin AUSSCHLIESSLICH mit
        // LadderSlot.ARMOR. Der slot-Parameter existiert genau fuer diese Unterscheidung und wurde
        // von niemandem geprueft - und im Code auch von niemandem gelesen.
        TierAppearance ladderWeapon = new TierAppearance("NETHERITE_SWORD", null, null, null, 0);

        TierAppearance worn = override.apply(CHARACTER, LadderSlot.WEAPON, ladderWeapon);

        assertThat(worn.hasTrim())
                .as("der gekaufte Trim gehoert auf die Ruestung, nicht auf die Klinge")
                .isFalse();
        assertThat(worn).isEqualTo(ladderWeapon);
    }

    @Test
    @DisplayName("und die Ruestung bekommt ihn weiterhin - die Gegenprobe")
    void thearmourStillGetsIt() {
        // Ohne diesen Test waere "gar keinen Trim mehr" auch eine gruene Loesung gewesen.
        TierAppearance ladder =
                new TierAppearance("LEATHER_CHESTPLATE", 0x1f3a93, null, null, 7);

        assertThat(override.apply(CHARACTER, LadderSlot.ARMOR, ladder).hasTrim()).isTrue();
    }

    @Test
    @DisplayName("genau die zwei Trim-Felder aendern sich - Material, Farbe und Modell bleiben")
    void exactlyTheTwoTrimFieldsChange() {
        TierAppearance ladder =
                new TierAppearance("LEATHER_CHESTPLATE", 0x1f3a93, "COPPER", "RIB", 7);

        TierAppearance worn = override.apply(CHARACTER, LadderSlot.ARMOR, ladder);

        assertThat(worn.material()).isEqualTo(ladder.material());
        assertThat(worn.color())
                .as("der Magier bleibt in seinem gefaerbten Leder")
                .isEqualTo(ladder.color());
        assertThat(worn.modelData()).isEqualTo(ladder.modelData());
        assertThat(worn.trimMaterial()).isEqualTo("REDSTONE");
        assertThat(worn.trimPattern()).isEqualTo("RAISER");
    }

    @Test
    @DisplayName("eine Stufe ganz ohne Trim bekommt einen - und behaelt alles andere")
    void atierWithoutATrimGetsOne() {
        TierAppearance ladder = TierAppearance.ofMaterial("NETHERITE_CHESTPLATE");

        TierAppearance worn = override.apply(CHARACTER, LadderSlot.ARMOR, ladder);

        assertThat(worn.material()).isEqualTo("NETHERITE_CHESTPLATE");
        assertThat(worn.hasTrim()).isTrue();
        assertThat(worn.hasColor()).isFalse();
    }

    @Test
    @DisplayName("ohne getragene Farbe bleibt die Erscheinung BITGENAU dieselbe")
    void withoutAWornColourTheAppearanceIsIdentical() {
        CosmeticApplication nothing =
                new CosmeticApplication(
                        CosmeticChangesNoValueTest::config,
                        new NoRepository(),
                        (characterId, slot) -> true,
                        new DefaultEventBus(java.util.logging.Logger.getLogger("quiet")),
                        Clock.systemUTC());
        nothing.put(CHARACTER, List.of());
        TierAppearance ladder = TierAppearance.trimmed("CHAINMAIL_CHESTPLATE", "AMETHYST", "SILENCE");

        assertThat(new CosmeticOverride(nothing).apply(CHARACTER, LadderSlot.ARMOR, ladder))
                .as("wer nichts gekauft hat, sieht aus wie seine Stufe - und zwar genau so")
                .isSameAs(ladder);
    }

    @Test
    @DisplayName("TierAppearance traegt ueberhaupt keinen Wert - hier ist nichts zu aendern")
    void thetierAppearanceCarriesNoValueAtAll() {
        // Die haltbarere Haelfte des Beweises. Waere hier je ein Zahlenfeld, das ein Attribut
        // bedeutet, koennte eine Trimfarbe es mitnehmen - und SC-014 waere eine Behauptung statt
        // einer Eigenschaft.
        List<String> fields =
                java.util.Arrays.stream(TierAppearance.class.getRecordComponents())
                        .map(RecordComponent::getName)
                        .toList();

        assertThat(fields)
                .as("Aussehen und Werte sind getrennt - die Werte liegen in EquipmentTier")
                .containsExactlyInAnyOrder(
                        "material", "color", "trimMaterial", "trimPattern", "modelData");
    }

    @Test
    @DisplayName("und die Kosmetik kennt weder StatEngine noch Attribute")
    void thecosmeticKnowsNeitherEngineNorAttributes() throws IOException {
        // Der strukturelle Beweis fuer SC-014: gaebe es hier eine Zeile, die Werte anfasst, muesste
        // sie einen dieser Namen nennen.
        for (Path source :
                List.of(
                        Path.of("src", "main", "java", "rpg", "platform", "item", "CosmeticOverride.java"),
                        Path.of(
                                "..",
                                "rpg-core",
                                "src",
                                "main",
                                "java",
                                "rpg",
                                "core",
                                "item",
                                "CosmeticApplication.java"))) {
            String code = codeOnly(Files.readString(source));

            assertThat(code)
                    .as("%s darf keinen Wert anfassen (SC-014)", source.getFileName())
                    .doesNotContain("StatEngine")
                    .doesNotContain("Attribute.")
                    .doesNotContain("addBase")
                    .doesNotContain("EquipmentTier");
        }
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private static ItemConfig config() {
        Map<String, ItemTemplate> templates = new LinkedHashMap<>();
        templates.put(
                EMBER,
                new ItemTemplate(
                        EMBER,
                        ItemCategory.COSMETIC,
                        "NETHERITE_UPGRADE_SMITHING_TEMPLATE",
                        Rarity.LEGENDARY,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new CosmeticAppearance("REDSTONE", "RAISER")));
        return new ItemConfig(
                templates,
                WearCurve.defaults(),
                new RepairPricing(List.of(0L, 40L)),
                LootTables.empty(),
                Map.of());
    }

    /** Kommentare weg — eine Erklärung ist kein Aufruf. */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static final class NoRepository implements CosmeticRepository {

        @Override
        public CompletableFuture<List<CosmeticUnlock>> find(UUID characterId) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public void markDirty(UUID characterId) {}
    }
}
