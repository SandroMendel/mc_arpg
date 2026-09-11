package rpg.core.item;

import java.util.Map;
import java.util.Objects;

/**
 * Alle Beutetabellen, und die Regel, welche gilt (FR-018, FR-023).
 *
 * <p><b>Art vor Region, Boss getrennt.</b> Drei Ebenen, und die Reihenfolge ist die Anforderung:
 *
 * <ol>
 *   <li>Hat die <b>Art</b> eine eigene Tabelle, gilt sie. Damit lassen zwei Arten auf derselben
 *       Vanilla-Basis unterschiedliche Beute fallen - nach Art, nicht nach Basistyp (FR-020,
 *       dieselbe Zusage, die B10 fuer Werte, Erfahrung und Coins gibt).
 *   <li>Sonst gilt die Tabelle ihrer <b>Region</b>.
 *   <li>Ein <b>Boss</b> erbt <em>nicht</em> von seiner Region. Seine Tabelle ist getrennt, weil
 *       sonst der seltene Gegner dieselbe Ausbeute liefe wie die Horde um ihn herum (FR-023).
 * </ol>
 *
 * <p>Eine Art ohne Tabelle in keiner der Ebenen laesst nichts fallen. Das ist kein Fehler - nicht
 * jede Kreatur muss etwas hinterlassen.
 */
public record LootTables(
        Map<String, LootTable> byKind, Map<String, LootTable> byZone, Map<String, LootTable> byBoss) {

    public LootTables {
        // Wie in ItemConfig: die Reihenfolge der Konfiguration bleibt erhalten. Hier faellt sie
        // weniger auf als beim Haendlerbestand, aber zwei Regeln fuer dieselbe Frage waeren eine
        // zu viel - und beim naechsten Lesen der Datei ist die Reihenfolge das, was ein Betreiber
        // wiedererkennt.
        byKind = unmodifiable(Objects.requireNonNull(byKind, "byKind"));
        byZone = unmodifiable(Objects.requireNonNull(byZone, "byZone"));
        byBoss = unmodifiable(Objects.requireNonNull(byBoss, "byBoss"));
    }

    private static Map<String, LootTable> unmodifiable(Map<String, LootTable> source) {
        return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(source));
    }

    public static LootTables empty() {
        return new LootTables(Map.of(), Map.of(), Map.of());
    }

    /**
     * Welche Tabelle fuer diese Kreatur gilt.
     *
     * @param kindKey ihre Art
     * @param zoneKey ihre Ursprungsregion
     * @param boss ob sie ein Boss ist
     */
    public LootTable tableFor(String kindKey, String zoneKey, boolean boss) {
        if (boss) {
            // Kein Rueckfall auf die Region: ein Boss ohne eigene Tabelle laesst nichts fallen,
            // statt die Ausbeute eines gewoehnlichen Gegners zu liefern.
            return byBoss.getOrDefault(kindKey, LootTable.empty());
        }
        LootTable own = byKind.get(kindKey);
        if (own != null) {
            return own;
        }
        return byZone.getOrDefault(zoneKey, LootTable.empty());
    }

    /** Jede Vorlagen-Kennung, die irgendeine Tabelle nennt - fuer die Startpruefung (FR-024). */
    public java.util.Set<String> referencedTemplates() {
        java.util.Set<String> keys = new java.util.LinkedHashSet<>();
        for (Map<String, LootTable> level : java.util.List.of(byKind, byZone, byBoss)) {
            for (LootTable table : level.values()) {
                for (LootEntry entry : table.entries()) {
                    keys.add(entry.templateKey());
                }
            }
        }
        return keys;
    }
}
