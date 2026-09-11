package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import rpg.core.item.ConsumableEffect;
import rpg.core.item.CosmeticAppearance;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemTemplate;
import rpg.core.item.Rarity;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.stats.Attribute;

/**
 * <b>Was ein Trank genau tut, steht auf dem Trank.</b>
 *
 * <p>„Healing Potion" sagt einem Spieler nicht, ob er 40 oder 420 heilt — und der Unterschied
 * zwischen den dreien im Händlerbestand ist genau das. Ohne die Zahl bleibt nur Ausprobieren, und
 * Ausprobieren kostet einen Trank.
 *
 * <p><b>Und die Zeilen werden abgeleitet, nicht gespeichert</b> (SC-001): wer {@code heal} in
 * {@code items.yml} ändert und neu lädt, ändert sie damit auf jedem vorhandenen Exemplar. Eine
 * eingebrannte Zahl wäre nach dem ersten Balancing eine Lüge auf dem Gegenstand — der letzte Test
 * hier prüft genau das.
 */
class EffectLoreTest {

    private static final PlainTextComponentSerializer PLAIN = PlainTextComponentSerializer.plainText();

    @Test
    @DisplayName("ein Heiltrank nennt seinen Betrag")
    void ahealingPotionNamesItsAmount() {
        List<String> lines = lore(healing(140.0), false);

        assertThat(lines).contains("Heals 140 health");
    }

    @Test
    @DisplayName("ein Manatrank ebenso")
    void amanaPotionToo() {
        List<String> lines = lore(mana(110.0), false);

        assertThat(lines).contains("Restores 110 mana");
    }

    @Test
    @DisplayName("ein Buff nennt Attribut, Betrag UND Dauer")
    void abuffNamesAttributeAmountAndDuration() {
        List<String> lines =
                lore(buff(Map.of(Attribute.DEFENSE, 12.0), Duration.ofSeconds(45)), false);

        assertThat(lines).contains("Defence +12 for 45s");
    }

    @Test
    @DisplayName("ZWEI Beitraege ergeben ZWEI Zeilen - keine Aufzaehlung")
    void twocontributionsGiveTwoLines() {
        // fury gibt Schaden UND Angriffsgeschwindigkeit. In einer Zeile waere das eine Aufzaehlung,
        // die niemand liest.
        List<String> lines =
                lore(
                        buff(
                                new LinkedHashMap<>(
                                        Map.of(
                                                Attribute.PHYSICAL_DAMAGE, 18.0,
                                                Attribute.ATTACK_SPEED, 0.15)),
                                Duration.ofSeconds(30)),
                        false);

        assertThat(lines).contains("Physical damage +18 for 30s", "Attack speed +0.15 for 30s");
    }

    @Test
    @DisplayName("Bruchteile behalten ihre Stelle - 0.15 ist nicht 0")
    void fractionsKeepTheirDigits() {
        List<String> lines =
                lore(buff(Map.of(Attribute.ATTACK_SPEED, 0.15), Duration.ofSeconds(30)), false);

        assertThat(lines)
                .as("als 0 dargestellt saehe ein echter Wert wie ein Fehler aus")
                .anyMatch(line -> line.contains("0.15"));
    }

    @Test
    @DisplayName("ganze Zahlen ohne Komma - 140 und nicht 140.0")
    void wholeNumbersWithoutADecimalPoint() {
        assertThat(lore(healing(140.0), false)).contains("Heals 140 health");
    }

    @Test
    @DisplayName("ein Wurftrank sagt, dass er geworfen wird")
    void athrownPotionSaysSo() {
        assertThat(lore(healing(40.0), true)).contains("Thrown - affects everyone it hits");
        assertThat(lore(healing(40.0), false))
                .as("ein trinkbarer sagt es nicht - sonst waere die Zeile bedeutungslos")
                .doesNotContain("Thrown - affects everyone it hits");
    }

    @Test
    @DisplayName("die Abklingzeit steht ZULETZT - erst was er tut, dann wie man ihn benutzt")
    void thecooldownComesLast() {
        List<String> lines = lore(healing(140.0), true);

        assertThat(lines.getLast()).isEqualTo("Cooldown 8s");
        assertThat(lines.getFirst()).isEqualTo("Heals 140 health");
    }

    @Test
    @DisplayName("eine Kosmetik hat keine Wirkung - und keine Wirkungszeilen")
    void acosmeticHasNoEffectLines() {
        assertThat(lore(cosmetic(), false)).isEmpty();
        assertThat(EffectLore.of(messages(), null, false)).isEmpty();
    }

    @Test
    @DisplayName("die Zeilen sind NICHT kursiv")
    void thelinesAreNotItalic() {
        List<Component> lines = EffectLore.of(messages(), healing(140.0), false);

        assertThat(lines)
                .allSatisfy(
                        line ->
                                assertThat(line.decoration(TextDecoration.ITALIC))
                                        .isEqualTo(TextDecoration.State.FALSE));
    }

    @Test
    @DisplayName("SC-001: eine geaenderte Zahl in der Vorlage aendert die ZEILE")
    void achangedNumberInTheTemplateChangesTheLine() {
        // Der Kern der ganzen Bauweise. Waere die Zahl beim Erzeugen eingebrannt worden, stuende auf
        // jedem vorhandenen Trank nach dem naechsten Balancing eine falsche.
        assertThat(lore(healing(40.0), false)).contains("Heals 40 health");
        assertThat(lore(healing(80.0), false)).contains("Heals 80 health");
    }

    @Test
    @DisplayName("ein fehlender Text laesst die Zeile weg statt einen Schluessel zu zeigen")
    void amissingTextOmitsTheLine() {
        List<Component> lines = EffectLore.of(new MapMessages(Map.of()), healing(140.0), true);

        assertThat(lines).isEmpty();
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private static List<String> lore(ItemTemplate template, boolean thrown) {
        return EffectLore.of(messages(), template, thrown).stream().map(PLAIN::serialize).toList();
    }

    private static Messages messages() {
        Map<String, String> texts = new LinkedHashMap<>();
        texts.put("item.effect.heal", "&cHeals &f{amount}&c health");
        texts.put("item.effect.mana", "&9Restores &f{amount}&9 mana");
        texts.put("item.effect.buff", "&b{attribute} &f+{amount}&b for &f{seconds}s");
        texts.put("item.effect.splash", "&dThrown - affects everyone it hits");
        texts.put("item.effect.cooldown", "&8Cooldown {seconds}s");
        texts.put("item.attribute.defense", "Defence");
        texts.put("item.attribute.physical-damage", "Physical damage");
        texts.put("item.attribute.attack-speed", "Attack speed");
        return new MapMessages(texts);
    }

    private static ItemTemplate healing(double amount) {
        return consumable(ConsumableEffect.healing(amount, Duration.ofSeconds(8)));
    }

    private static ItemTemplate mana(double amount) {
        return consumable(ConsumableEffect.mana(amount, Duration.ofSeconds(8)));
    }

    private static ItemTemplate buff(Map<Attribute, Double> values, Duration duration) {
        return consumable(
                new ConsumableEffect(null, null, values, duration, Duration.ofSeconds(8)));
    }

    private static ItemTemplate consumable(ConsumableEffect effect) {
        return new ItemTemplate(
                "potion.test",
                ItemCategory.CONSUMABLE,
                "POTION",
                Rarity.COMMON,
                null,
                null,
                3L,
                null,
                effect,
                null);
    }

    private static ItemTemplate cosmetic() {
        return new ItemTemplate(
                "trim.test",
                ItemCategory.COSMETIC,
                "NETHERITE_UPGRADE_SMITHING_TEMPLATE",
                Rarity.LEGENDARY,
                null,
                null,
                null,
                null,
                null,
                new CosmeticAppearance("REDSTONE", "RAISER"));
    }
}
