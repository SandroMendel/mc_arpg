package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.random.RandomGenerator;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Was eine Beutetabelle zieht — und was sie ausdrücklich <b>nicht</b> würfelt.
 *
 * <p><b>Die Stückzahl ist der einzige Zufall in diesem Block</b> (FR-021). Ob ein Eintrag trifft und
 * wie viele Stück es sind, wird gewürfelt; die <em>Werte</em> des gefallenen Gegenstands sind fest,
 * weil sie aus der Vorlage kommen. Das ist der Unterschied zwischen einer Beutetabelle und dem
 * Roll-Mechanismus, den ADR-027 abgeschafft hat.
 *
 * <p><b>Jeder Eintrag wird einzeln gezogen</b>, nicht einer aus der Liste gewählt. Zwei Einträge zu
 * je 50 % lassen in einem Viertel der Fälle beide fallen — das ist die Form, die ein Betreiber
 * erwartet, wenn er eine dritte Zeile hinzufügt: die anderen beiden werden dadurch nicht seltener.
 */
class LootTableTest {

    @Test
    @DisplayName("ein Eintrag, der trifft, liefert seine Vorlage")
    void anEntryThatHitsYieldsItsTemplate() {
        LootTable table = new LootTable(List.of(LootEntry.single("potion.test", 0.5)));

        List<LootTable.Roll> rolls = table.roll(fixed(0.1));

        assertThat(rolls).hasSize(1);
        assertThat(rolls.get(0).templateKey()).isEqualTo("potion.test");
        assertThat(rolls.get(0).count()).isEqualTo(1);
    }

    @Test
    @DisplayName("ein Eintrag, der nicht trifft, liefert nichts")
    void anEntryThatMissesYieldsNothing() {
        LootTable table = new LootTable(List.of(LootEntry.single("potion.test", 0.5)));

        assertThat(table.roll(fixed(0.9))).isEmpty();
    }

    @Test
    @DisplayName("jeder Eintrag wird EINZELN gezogen - zwei Treffer sind moeglich")
    void everyEntryIsRolledOnItsOwn() {
        LootTable table =
                new LootTable(
                        List.of(
                                LootEntry.single("potion.a", 0.5),
                                LootEntry.single("potion.b", 0.5)));

        List<LootTable.Roll> rolls = table.roll(fixed(0.1));

        assertThat(rolls)
                .as("eine dritte Zeile macht die ersten beiden nicht seltener")
                .hasSize(2)
                .extracting(LootTable.Roll::templateKey)
                .containsExactly("potion.a", "potion.b");
    }

    @Test
    @DisplayName("die Stueckzahl bleibt in der konfigurierten Spanne")
    void theCountStaysInTheConfiguredSpan() {
        LootTable table = new LootTable(List.of(new LootEntry("potion.test", 1.0, 2, 4)));

        for (int seed = 0; seed < 50; seed++) {
            List<LootTable.Roll> rolls = table.roll(java.util.random.RandomGenerator.getDefault());
            assertThat(rolls).hasSize(1);
            assertThat(rolls.get(0).count()).isBetween(2, 4);
        }
    }

    @Test
    @DisplayName("ueber viele Ziehungen trifft die Haeufigkeit die Wahrscheinlichkeit")
    void overManyRollsTheFrequencyMatchesTheChance() {
        LootTable table = new LootTable(List.of(LootEntry.single("potion.test", 0.25)));
        RandomGenerator random = new java.util.Random(20260828L);

        int hits = 0;
        for (int attempt = 0; attempt < 20_000; attempt++) {
            hits += table.roll(random).size();
        }

        // 25 % von 20 000 sind 5 000. Die Spanne ist weit genug, dass der Test nicht flackert, und
        // eng genug, dass eine vertauschte Vergleichsrichtung auffiele.
        assertThat(hits).isBetween(4_600, 5_400);
    }

    @Test
    @DisplayName("eine leere Tabelle ist kein Fehler - nicht jede Kreatur hinterlaesst etwas")
    void anEmptyTableIsNotAnError() {
        assertThat(LootTable.empty().roll(fixed(0.0))).isEmpty();
        assertThat(LootTable.empty().isEmpty()).isTrue();
    }

    @Test
    @DisplayName("eine Chance ausserhalb (0,1] wird abgelehnt")
    void aChanceOutsideTheRangeIsRefused() {
        assertThatThrownBy(() -> LootEntry.single("potion.test", 0.0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("chance");
        assertThatThrownBy(() -> LootEntry.single("potion.test", 1.5))
                .hasMessageContaining("chance");
    }

    @Test
    @DisplayName("max unter min wird abgelehnt")
    void maxBelowMinIsRefused() {
        assertThatThrownBy(() -> new LootEntry("potion.test", 0.5, 3, 1))
                .hasMessageContaining("max");
    }

    /** Ein Generator, der immer denselben Wert liefert - damit ein Treffer kein Zufall ist. */
    private static RandomGenerator fixed(double value) {
        return new RandomGenerator() {
            @Override
            public long nextLong() {
                return 0L;
            }

            @Override
            public double nextDouble() {
                return value;
            }

            @Override
            public int nextInt(int bound) {
                return 0;
            }
        };
    }
}
