package rpg.platform.classes;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

import rpg.core.classes.BoundEquipment;
import rpg.core.classes.LadderSlot;
import rpg.core.classes.TierAppearance;
import rpg.core.session.CharacterClass;

/**
 * T118 bis T122 - FR-046 bis FR-048: der Waffentyp darf die Werte nicht beeinflussen.
 *
 * <h2>Was hier NICHT geprüft werden kann, und warum das so dasteht</h2>
 *
 * <p>Der Bukkit-Vertrag ist eindeutig: „To clear all custom attribute modifiers, use {@code null}. To
 * set no modifiers (<b>which will override the default modifiers</b>), use an empty map." Genau das tut
 * {@code BoundItemFactory}.
 *
 * <p><b>MockBukkit unterscheidet die beiden Zustände jedoch nicht.</b> Nachgemessen: nach
 * {@code setAttributeModifiers(ImmutableMultimap.of())} liefert {@code hasAttributeModifiers()} false,
 * {@code getAttributeModifiers()} null, die Serialisierung ist identisch mit der eines rohen Items, und
 * die beiden Metas sind {@code equals}. MockBukkit behandelt den Aufruf als Nulloperation.
 *
 * <p>Ein Test auf „der Getter liefert leer" wäre deshalb <b>wertlos</b>: er wäre auch grün, wenn der
 * Aufruf ganz fehlte. Dieselbe Sorte Scheintest wie ein Zähler, der nie erhöht wird.
 *
 * <p>Geprüft wird deshalb, was tatsächlich prüfbar ist: dass der Aufbau den <b>richtigen Aufruf</b>
 * macht und nicht die beiden dokumentierten Fallen benutzt. Der entscheidende Nachweis ist
 * <b>T143</b> auf einem echten Server - dort und nur dort zeigt sich, ob die Schlagrate vom Waffentyp
 * unabhängig ist (SC-011).
 */
class AttributeNeutralizationTest {

    private static final Path FACTORY =
            Path.of("src/main/java/rpg/platform/classes/BoundItemFactory.java");

    private ServerMock server;
    private BoundItemFactory factory;

    @BeforeEach
    void setUp() {
        server = MockBukkit.mock();
        factory = new BoundItemFactory(PlatformClassFixture.messages());
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("T118: der Aufbau setzt einen leeren, NICHT-null Modifikatorsatz (FR-046)")
    void theFactorySetsAnEmptyNonNullModifierSet() throws IOException {
        String code = codeOf(FACTORY);

        assertThat(code)
                .as("der dokumentierte Weg, die Materialvorgaben zu überschreiben")
                .contains("setAttributeModifiers(ImmutableMultimap.of())");
    }

    @Test
    @DisplayName("T119 Falle 1: setAttributeModifiers(null) kommt nirgends vor")
    void nullIsNeverPassed() throws IOException {
        String code = codeOf(FACTORY);

        assertThat(code)
                .as(
                        "null entfernt die Überschreibung und stellt die Vorgaben WIEDER HER - das"
                                + " Gegenteil von neutral, und derselbe Fehlertyp wie der"
                                + " Double.NaN-Sentinel aus ADR-016")
                .doesNotContain("setAttributeModifiers(null)");
    }

    @Test
    @DisplayName("T119 Falle 2: HIDE_ATTRIBUTES steht NACH dem echten Aufruf, nicht statt seiner")
    void theFlagIsNotUsedAsTheMechanism() throws IOException {
        String code = codeOf(FACTORY);

        int neutralise = code.indexOf("setAttributeModifiers(ImmutableMultimap.of())");
        int flag = code.indexOf("HIDE_ATTRIBUTES");
        assertThat(neutralise).as("der Aufruf existiert").isNotNegative();
        assertThat(flag).as("die Flagge existiert").isNotNegative();
        assertThat(neutralise)
                .as(
                        "die Flagge versteckt nur den Tooltip. Wer sie für Neutralisierung hält, baut"
                                + " einen Fehler, der 'sieht richtig aus' besteht und falsch rechnet")
                .isLessThan(flag);
    }

    @Test
    @DisplayName("T120: jedes gebaute Item durchläuft die Neutralisierung - eine Stelle, kein Zweig")
    void everyBuiltItemGoesThroughTheSamePath() throws IOException {
        String code = codeOf(FACTORY);

        // Genau ein Aufruf, aufgerufen von beiden Bauwegen. Zwei Stellen wären zwei Gelegenheiten,
        // eine zu vergessen.
        assertThat(count(code, "neutraliseVanillaModifiers(meta)"))
                .as("einmal für Rüstung, einmal für Waffe")
                .isEqualTo(2);
        assertThat(count(code, "setAttributeModifiers(ImmutableMultimap.of())"))
                .as("und genau eine Stelle, die es tut")
                .isEqualTo(1);
    }

    @ParameterizedTest(name = "{0}")
    @ValueSource(
            strings = {
                "WOODEN_SWORD",
                "STONE_SWORD",
                "COPPER_SWORD",
                "IRON_SWORD",
                "DIAMOND_SWORD",
                "NETHERITE_SWORD",
                "WOODEN_SPEAR",
                "COPPER_SPEAR",
                "GOLDEN_SPEAR",
                "NETHERITE_SPEAR"
            })
    @DisplayName("T120: jeder Waffentyp lässt sich bauen und trägt die Anzeige-Flagge")
    void everyWeaponTypeBuilds(String material) {
        ItemStack weapon = factory.weapon(TierAppearance.ofMaterial(material), tag());

        assertThat(weapon.getType().name()).isEqualTo(material);
        assertThat(weapon.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)).isTrue();
    }

    @Test
    @DisplayName("T120: jedes Rüstungsteil lässt sich bauen und trägt die Anzeige-Flagge")
    void everyArmorPieceBuilds() {
        for (BoundItemFactory.ArmorPiece piece : BoundItemFactory.ArmorPiece.values()) {
            ItemStack armor =
                    factory.armorPiece(TierAppearance.ofMaterial("NETHERITE"), piece, tag());

            assertThat(armor.getType().name()).isEqualTo("NETHERITE_" + piece.name());
            assertThat(armor.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES)).isTrue();
        }
    }

    @Test
    @DisplayName("T122: die Werte eines Items kommen nirgends aus dem Material (FR-048)")
    void noValueIsReadFromTheMaterial() throws IOException {
        String code = codeOf(FACTORY);

        // Der Aufbau liest kein Vanilla-Attribut und keine Materialstärke. Eine Netherite-Stufe ist
        // stark, weil die Konfiguration es sagt.
        assertThat(code).doesNotContain("getAttributeModifiers");
        assertThat(code).doesNotContain("getMaxDurability");
        assertThat(code).doesNotContain("Attribute.");
    }

    @Test
    @DisplayName("T121: der Waffentyp ist thematisch - nachweisbar nur im Spiel (SC-011, T143)")
    void theWeaponTypeIsCosmeticAndThatIsProvenOnAServer() {
        ItemStack sword = factory.weapon(TierAppearance.ofMaterial("NETHERITE_SWORD"), tag());
        ItemStack spear = factory.weapon(TierAppearance.ofMaterial("NETHERITE_SPEAR"), tag());

        // Was hier prüfbar ist: beide entstehen auf demselben Weg und unterscheiden sich nur im Typ.
        assertThat(sword.getType()).isNotEqualTo(spear.getType());
        assertThat(sword.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES))
                .isEqualTo(spear.getItemMeta().hasItemFlag(ItemFlag.HIDE_ATTRIBUTES));

        // Was hier NICHT prüfbar ist: dass die Schlagrate gleich ist. MockBukkit spiegelt keine
        // Attributmodifikatoren von Items. Der Nachweis ist Abschnitt 11 Punkt 14 (T143), und dass er
        // aussteht, steht in der Aufgabenliste - nicht hinter einer grünen Zusicherung versteckt.
        assertThat(BoundItemTag.isTagged(sword)).isTrue();
        assertThat(BoundItemTag.isTagged(spear)).isTrue();
    }

    @Test
    @DisplayName("ein rohes Vanilla-Item und ein gebautes sind über MockBukkit nicht zu unterscheiden")
    void mockBukkitCannotTellThemApart() {
        // Dieser Test hält den Grund fest, aus dem die Prüfungen oben am Quelltext hängen. Fällt er
        // eines Tages um, weil MockBukkit die Semantik nachrüstet, kann und soll dieser Test durch
        // eine echte Verhaltensprüfung ersetzt werden.
        ItemStack plain = new ItemStack(Material.NETHERITE_SWORD);
        ItemStack built = factory.weapon(TierAppearance.ofMaterial("NETHERITE_SWORD"), tag());

        assertThat(plain.getItemMeta().getAttributeModifiers()).isNull();
        assertThat(built.getItemMeta().getAttributeModifiers())
                .as("MockBukkit behandelt setAttributeModifiers(leer) als Nulloperation")
                .isNull();
    }

    // --- helpers ----------------------------------------------------------------------------

    /**
     * The source with comments removed.
     *
     * <p>Necessary, not tidy: the javadoc of {@code neutraliseVanillaModifiers} names both traps in
     * order to warn about them. A scan over raw text fails on that explanation, and deleting the
     * explanation would be the wrong fix - the same lesson {@code ClassSourceInvariantsTest} learned.
     */
    private static String codeOf(Path relative) throws IOException {
        String content = Files.readString(relative, StandardCharsets.UTF_8);
        return content.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static int count(String haystack, String needle) {
        int count = 0;
        int at = haystack.indexOf(needle);
        while (at >= 0) {
            count++;
            at = haystack.indexOf(needle, at + needle.length());
        }
        return count;
    }

    private static String tag() {
        return BoundEquipment.tagFor(UUID.randomUUID(), CharacterClass.WARRIOR, LadderSlot.WEAPON);
    }
}
