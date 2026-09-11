package rpg.core.item;

import java.util.Objects;

/**
 * Ein Eintrag einer Beutetabelle (FR-021).
 *
 * <p><b>Die Stueckzahl ist der einzige Zufall in diesem Block.</b> Ob etwas faellt und wie viel
 * davon - mehr wird nicht gewuerfelt. Die <em>Werte</em> des gefallenen Gegenstands sind fest, weil
 * sie aus der Vorlage kommen und nicht aus einem Wurf (ADR-027). Das ist der Unterschied zwischen
 * einer Beutetabelle und dem Roll-Mechanismus, den ADR-027 abgeschafft hat.
 *
 * @param templateKey welche Vorlage; muss existieren, sonst bricht der Start ab (FR-024)
 * @param chance Wahrscheinlichkeit in {@code (0, 1]}
 * @param minCount kleinste Stueckzahl, mindestens 1
 * @param maxCount groesste Stueckzahl, mindestens {@code minCount}
 */
public record LootEntry(String templateKey, double chance, int minCount, int maxCount) {

    public LootEntry {
        Objects.requireNonNull(templateKey, "templateKey");
        if (templateKey.isBlank()) {
            throw new IllegalArgumentException("templateKey must not be blank");
        }
        if (!(chance > 0.0) || chance > 1.0) {
            throw new IllegalArgumentException(
                    templateKey + ": chance must be in (0, 1], but was " + chance);
        }
        if (minCount < 1) {
            throw new IllegalArgumentException(
                    templateKey + ": min must be at least 1, but was " + minCount);
        }
        if (maxCount < minCount) {
            throw new IllegalArgumentException(
                    templateKey + ": max must not be below min, but was " + maxCount + " < " + minCount);
        }
    }

    /** Ein Eintrag, der immer genau ein Stueck liefert. */
    public static LootEntry single(String templateKey, double chance) {
        return new LootEntry(templateKey, chance, 1, 1);
    }
}
