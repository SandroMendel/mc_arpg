package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.UUID;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;

import rpg.core.classes.BoundEquipment;
import rpg.core.classes.LadderSlot;
import rpg.core.session.CharacterClass;
import rpg.platform.classes.BoundItemFactory;
import rpg.platform.classes.BoundItemTag;

/**
 * SC-016 — <b>das Bindungsprädikat sitzt im Pfad jedes Inventarklicks.</b>
 *
 * <p>Jeder Klick, den ein Spieler in irgendeinem Fenster macht, geht durch diese Frage: B07s
 * Ausrüstungssperre, das Händlerfenster, der Mülleimer, die drei gesperrten Vanilla-Wege. Bei 150
 * Spielern ist das der meistbenutzte Pfad, den dieser Block berührt — und der einzige, bei dem eine
 * Allokation je Aufruf zu Müll auf dem Tick würde.
 *
 * <p><b>Was hier gemessen wird und was nicht.</b> Nicht die Volllast — die gehört nach ADR-031 zu
 * B15. Gemessen wird die <em>Größenordnung</em> unter wiederholbaren Bedingungen: hunderttausend
 * Aufrufe, und die Frage ist, ob sie in Millisekunden oder in Sekunden beantwortet werden. Ein Test,
 * der eine Mikrosekundengrenze zieht, misst auf einem geteilten Bausystem den Zufall.
 *
 * <p><b>Der eigentliche Beweis ist die Bauart, nicht die Uhr.</b> {@code BoundItemTag.read} steigt
 * auf drei Feldzugriffen aus, bevor es irgendetwas anfasst: kein Item, keine Metadaten, ein leerer
 * Container — und das trifft auf fast jeden Gegenstand zu, den ein Spieler je anklickt.
 */
class BoundPredicateBenchmarkTest {

    /** Genug, dass eine Allokation je Aufruf sichtbar würde; wenig genug für einen Testlauf. */
    private static final int CALLS = 100_000;

    /**
     * Großzügig, und ausdrücklich so gemeint.
     *
     * <p>Hunderttausend Aufrufe in einer Sekunde sind zehn Mikrosekunden je Aufruf — das Hundertfache
     * dessen, was diese Methode braucht. Die Grenze fängt eine Größenordnung, keinen Ausreißer. Ein
     * schärferer Wert wäre auf einem Bausystem unter Last ein Test, der aus dem falschen Grund rot
     * wird, und der wäre schlimmer als keiner.
     */
    private static final Duration BUDGET = Duration.ofSeconds(1);

    private ItemStack bound;
    private ItemStack ordinary;
    private ItemStack bare;

    @BeforeEach
    void setUp() {
        MockBukkit.mock();

        bound = new ItemStack(Material.IRON_CHESTPLATE);
        ItemMeta meta = bound.getItemMeta();
        BoundItemFactory.markBound(
                meta,
                BoundEquipment.tagFor(UUID.randomUUID(), CharacterClass.WARRIOR, LadderSlot.ARMOR));
        bound.setItemMeta(meta);

        ordinary = new ItemStack(Material.DIAMOND_SWORD);
        ordinary.editMeta(edited -> edited.setUnbreakable(true));

        bare = new ItemStack(Material.COBBLESTONE);
    }

    @AfterEach
    void tearDown() {
        MockBukkit.unmock();
    }

    @Test
    @DisplayName("hunderttausend Fragen an einen gewoehnlichen Gegenstand - der haeufigste Fall")
    void ahundredThousandQuestionsAboutAnOrdinaryItem() {
        // Der Pfad, der wirklich zaehlt: fast jeder angeklickte Gegenstand ist NICHT gebunden.
        warmUp();

        long startedAt = System.nanoTime();
        int tagged = 0;
        for (int call = 0; call < CALLS; call++) {
            if (BoundItemTag.isTagged(bare)) {
                tagged++;
            }
        }
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(tagged).isZero();
        assertThat(took)
                .as("%d Aufrufe brauchten %d ms - der Klickpfad darf hier nichts kosten", CALLS, took.toMillis())
                .isLessThan(BUDGET);
    }

    @Test
    @DisplayName("und ebenso viele an einen gebundenen - der teurere Fall")
    void andjustAsManyAboutABoundOne() {
        warmUp();

        long startedAt = System.nanoTime();
        int tagged = 0;
        for (int call = 0; call < CALLS; call++) {
            if (BoundItemTag.isTagged(bound)) {
                tagged++;
            }
        }
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(tagged).isEqualTo(CALLS);
        assertThat(took).isLessThan(BUDGET);
    }

    @Test
    @DisplayName("die Antwort ist bei jedem Aufruf dieselbe - gemessen wird nichts Zufaelliges")
    void theanswerIsTheSameEveryTime() {
        // Ohne das koennte die Messung oben eine Methode messen, die manchmal etwas anderes tut.
        for (int call = 0; call < 1_000; call++) {
            assertThat(BoundItemTag.isTagged(bound)).isTrue();
            assertThat(BoundItemTag.isTagged(ordinary)).isFalse();
            assertThat(BoundItemTag.isTagged(bare)).isFalse();
            assertThat(BoundItemTag.isTagged(null)).isFalse();
        }
    }

    @Test
    @DisplayName("ein Gegenstand MIT Metadaten, aber ohne Vermerk, ist genauso guenstig")
    void anitemWithMetaButNoTagIsJustAsCheap() {
        // Der Fall, der die Ausstiegsreihenfolge prueft: hasItemMeta ist wahr, der Container ist
        // leer. Wer hier erst den Container aufbaut, zahlt fuer jede verzauberte Spitzhacke.
        warmUp();

        long startedAt = System.nanoTime();
        for (int call = 0; call < CALLS; call++) {
            BoundItemTag.isTagged(ordinary);
        }
        Duration took = Duration.ofNanos(System.nanoTime() - startedAt);

        assertThat(took).isLessThan(BUDGET);
    }

    /** Damit die Messung nicht den ersten Durchlauf des JIT misst. */
    private void warmUp() {
        for (int call = 0; call < 10_000; call++) {
            BoundItemTag.isTagged(bound);
            BoundItemTag.isTagged(ordinary);
            BoundItemTag.isTagged(bare);
        }
    }
}
