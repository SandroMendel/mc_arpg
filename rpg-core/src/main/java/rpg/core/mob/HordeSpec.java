package rpg.core.mob;

import java.util.List;
import java.util.Objects;

/**
 * Was in einer Zone steht: welche Arten in welchem Bereich, und wer ihr Boss ist (FR-012).
 *
 * <p><b>Der Bereich wird genannt, nicht beschrieben.</b> {@code areaKey} verweist auf einen
 * {@code SpawnArea}-Schluessel aus B09s {@code zones.yml}. Die Geometrie steht dort und nur dort;
 * verschiebt jemand einen Bereich, zieht die Horde mit. Zwei Orte, die dieselbe Geometrie
 * beschreiben, waeren einer zu viel - und der zweite wird beim naechsten Verschieben vergessen.
 *
 * @param zoneKey eine Zone aus {@code zones.yml}
 * @param entries welche Art in welchem Bereich, mit welchem Gewicht
 * @param boss der Boss dieser Region, oder {@code null}
 */
public record HordeSpec(String zoneKey, List<Entry> entries, BossSpec boss) {

    public HordeSpec {
        Objects.requireNonNull(zoneKey, "zoneKey");
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (zoneKey.isBlank()) {
            throw new IllegalArgumentException("a horde zone key must not be blank");
        }
        if (entries.isEmpty()) {
            throw new IllegalArgumentException(
                    zoneKey
                            + ": a horde needs at least one entry - a zone with none would be"
                            + " silently empty instead of visibly wrong");
        }
    }

    /**
     * Eine Art in einem Bereich.
     *
     * @param areaKey ein Bereichsschluessel dieser Zone
     * @param kindKey eine Art aus {@code kinds}
     * @param weight relativer Anteil an der Dichte dieses Bereichs
     */
    public record Entry(String areaKey, String kindKey, int weight) {

        public Entry {
            Objects.requireNonNull(areaKey, "areaKey");
            Objects.requireNonNull(kindKey, "kindKey");
            if (areaKey.isBlank() || kindKey.isBlank()) {
                throw new IllegalArgumentException("area and kind must not be blank");
            }
            if (weight < 1) {
                throw new IllegalArgumentException(
                        kindKey
                                + ": weight must be >= 1, but was "
                                + weight
                                + " - a weight of zero means 'never' and is more honest left out");
            }
        }
    }

    /** Die Summe aller Gewichte - der Nenner der Auswahl. */
    public int totalWeight() {
        int sum = 0;
        for (Entry entry : entries) {
            sum += entry.weight();
        }
        return sum;
    }
}
