package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.bukkit.Material;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import rpg.core.item.ConsumableEffect;
import rpg.core.item.ItemCategory;
import rpg.core.item.ItemTemplate;
import rpg.core.item.Rarity;

/**
 * <b>Das Material entscheidet, ob ein Trank geworfen wird — und zwar allein.</b>
 *
 * <p>Ein zusätzlicher Schlüssel {@code splash: true} in {@code items.yml} wäre eine zweite Wahrheit
 * über dieselbe Sache, und die beiden könnten sich widersprechen: ein {@code POTION} mit
 * {@code splash: true} wäre ein Trank, den niemand werfen kann, und der Fehler fiele erst im Spiel
 * auf. Vanilla wirft, was ein Wurftrank ist; dieser Block <em>liest</em> das nur.
 *
 * <p>Und die Antwort hängt an drei Stellen: ob der Rechtsklick abgebrochen wird, ob die
 * Wirksamkeitsprüfung aus FR-036 überhaupt gilt, und ob die Lore den Wurfhinweis trägt. Eine falsche
 * Antwort wäre an allen dreien gleichzeitig falsch.
 */
class ThrownConsumablesTest {

    @BeforeEach
    void setUp() {
        MockBukkit.mock();
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("ein SPLASH_POTION wird geworfen")
    void asplashPotionIsThrown() {
        assertThat(ThrownConsumables.isThrown(Material.SPLASH_POTION)).isTrue();
        assertThat(ThrownConsumables.isThrown(potion("SPLASH_POTION"))).isTrue();
    }

    @Test
    @DisplayName("ein LINGERING_POTION ebenso - auch wenn dieser Block heute keines ausliefert")
    void alingeringPotionToo() {
        // Er verhaelt sich beim Werfen genauso, und ein Betreiber, der ihn eintraegt, soll keinen
        // Trank bekommen, der beim Rechtsklick verschwindet.
        assertThat(ThrownConsumables.isThrown(Material.LINGERING_POTION)).isTrue();
    }

    @Test
    @DisplayName("ein gewoehnliches POTION wird GETRUNKEN")
    void anordinaryPotionIsDrunk() {
        assertThat(ThrownConsumables.isThrown(Material.POTION)).isFalse();
        assertThat(ThrownConsumables.isThrown(potion("POTION"))).isFalse();
    }

    @Test
    @DisplayName("und alles andere auch nicht")
    void andnothingElseIsThrown() {
        assertThat(ThrownConsumables.isThrown(Material.DIAMOND_SWORD)).isFalse();
        assertThat(ThrownConsumables.isThrown(Material.NETHERITE_UPGRADE_SMITHING_TEMPLATE)).isFalse();
    }

    @Test
    @DisplayName("ein unbekanntes Material ist kein Wurftrank - und kein Absturz")
    void anunknownMaterialIsNoThrownPotion() {
        // Kann nach einem Reload vorkommen, wenn jemand sich vertippt. Der Start faengt das ab
        // (FR-012), aber diese Frage darf trotzdem nicht werfen.
        assertThat(ThrownConsumables.isThrown(potion("NICHT_VORHANDEN"))).isFalse();
        assertThat(ThrownConsumables.isThrown((Material) null)).isFalse();
        assertThat(ThrownConsumables.isThrown((ItemTemplate) null)).isFalse();
    }

    @Test
    @DisplayName("die AUSGELIEFERTEN Heiltraenke werden geworfen, die Manatraenke nicht")
    void theshippedHealingPotionsAreThrownAndTheManaOnesAreNot() throws Exception {
        // Die Gegenprobe an der echten Datei. Ohne sie prueft der Test nur seine eigenen Beispiele,
        // und die sagen nichts darueber, was ein Spieler in die Hand bekommt.
        String yaml =
                java.nio.file.Files.readString(
                        java.nio.file.Path.of(
                                "..", "rpg-plugin", "src", "main", "resources", "items.yml"));

        for (String healing :
                java.util.List.of(
                        "potion.minor-healing", "potion.healing", "potion.greater-healing")) {
            assertThat(materialOf(yaml, healing))
                    .as("%s soll geworfen werden - alle im Radius werden geheilt", healing)
                    .isEqualTo("SPLASH_POTION");
        }
        for (String mana : java.util.List.of("potion.lesser-mana", "potion.mana")) {
            assertThat(materialOf(yaml, mana))
                    .as("%s wirkt auf den, der es braucht - und das ist der Traeger", mana)
                    .isEqualTo("POTION");
        }
    }

    private static String materialOf(String yaml, String templateKey) {
        int at = yaml.indexOf("  " + templateKey + ":");
        if (at < 0) {
            return "<nicht gefunden>";
        }
        String block = yaml.substring(at, Math.min(yaml.length(), at + 400));
        java.util.regex.Matcher matcher =
                java.util.regex.Pattern.compile("material:\\s*'([A-Z_]+)'").matcher(block);
        return matcher.find() ? matcher.group(1) : "<kein Material>";
    }

    private static ItemTemplate potion(String material) {
        return new ItemTemplate(
                "potion.test",
                ItemCategory.CONSUMABLE,
                material,
                Rarity.COMMON,
                null,
                null,
                3L,
                null,
                ConsumableEffect.healing(40.0, Duration.ofSeconds(8)),
                null);
    }
}
