package rpg.core.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.random.RandomGenerator;

/**
 * Was eine Kreatur fallen laesst (FR-018, FR-021).
 *
 * <p><b>Jeder Eintrag wird einzeln gezogen</b>, nicht einer aus der Liste gewaehlt. Zwei Eintraege
 * zu je 50 % lassen in einem Viertel der Faelle beide fallen und in einem Viertel keinen - eine
 * Tabelle ist eine Liste von Chancen, keine Verteilung ueber ein Feld. Das ist die Form, die ein
 * Betreiber erwartet, wenn er eine dritte Zeile hinzufuegt: die anderen beiden werden dadurch nicht
 * seltener.
 *
 * <p><b>Der Zufallsgenerator kommt von aussen.</b> Ein Test kann damit eine feste Folge vorgeben
 * und die Ausbeute pruefen, ohne tausend Durchlaeufe zu mitteln (Prinzip VII).
 */
public record LootTable(List<LootEntry> entries) {

    private static final LootTable EMPTY = new LootTable(List.of());

    public LootTable {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
    }

    /** Eine Art ohne eigene Beute - kein Fehler, sondern eine Kreatur, die nichts hinterlaesst. */
    public static LootTable empty() {
        return EMPTY;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /**
     * Zieht einmal.
     *
     * @return je Eintrag, der getroffen hat, ein Posten mit Vorlage und Stueckzahl; leer, wenn
     *     nichts getroffen hat
     */
    public List<Roll> roll(RandomGenerator random) {
        Objects.requireNonNull(random, "random");
        if (entries.isEmpty()) {
            return List.of();
        }
        List<Roll> result = new ArrayList<>(entries.size());
        for (LootEntry entry : entries) {
            if (random.nextDouble() < entry.chance()) {
                int span = entry.maxCount() - entry.minCount() + 1;
                int count = entry.minCount() + random.nextInt(span);
                result.add(new Roll(entry.templateKey(), count));
            }
        }
        return result;
    }

    /**
     * Ein gezogener Posten.
     *
     * @param templateKey welche Vorlage
     * @param count wie viele Stueck
     */
    public record Roll(String templateKey, int count) {
        public Roll {
            Objects.requireNonNull(templateKey, "templateKey");
            if (count < 1) {
                throw new IllegalArgumentException("count must be at least 1, but was " + count);
            }
        }
    }
}
