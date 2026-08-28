package rpg.core.item;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.OptionalLong;

import rpg.core.session.CharacterClass;

/**
 * Was eine Art von Gegenstand <b>ist</b> - und die einzige Quelle dafuer (FR-001 bis FR-003).
 *
 * <p><b>Ein Exemplar traegt nur die Kennung dieser Vorlage.</b> Name, Lore, Werte und Wirkung
 * werden bei jedem Laden hieraus abgeleitet, nie gespeichert (ADR-004 in der Fassung von ADR-027,
 * Constitution IV). Das ist der Grund, aus dem eine Balancing-Aenderung nach dem Release noch
 * moeglich ist: der Betreiber aendert diese Vorlage, laedt neu, und <b>jedes</b> vorhandene
 * Exemplar in <b>jedem</b> Inventar wirkt anders - ohne dass ein Inventar angefasst wurde (SC-001).
 *
 * <p><b>Die Werte sind fest.</b> Kein Wuerfeln, keine Wertebereiche, keine Affixe (FR-010,
 * ADR-027). Ohne Roll ist die Vorlage die einzige Quelle, und die Zusage ist dadurch staerker
 * geworden: frueher wirkte ein geaendertes Balancing nur auf neue Exemplare.
 *
 * @param key eindeutige Kennung in der Form {@code bereich.name}, wie bei Mob-Arten
 * @param category {@code CONSUMABLE} oder {@code COSMETIC} - Ausruestung gibt es hier nicht
 * @param material Vanilla-Material; unterscheidet Gegenstaende, solange kein Resource Pack
 *     existiert (FR-009)
 * @param rarity eine der acht Stufen - reines Etikett, ohne Wertwirkung (FR-014)
 * @param minLevel Mindestlevel, oder {@code null}; darunter wird die Benutzung abgelehnt (FR-015)
 * @param boundClass Klassenbindung, oder {@code null} (FR-015)
 * @param sellPrice Verkaufserloes, oder {@code null}; ohne ihn ist der Gegenstand unverkaeuflich
 *     und wird vom Haendler abgewiesen (FR-016)
 * @param modelData reserviert fuer ein spaeteres Resource Pack (FR-006, ADR-005); im
 *     Vanilla-Betrieb ungenutzt
 * @param effect die Wirkung; gesetzt genau dann, wenn {@code category == CONSUMABLE}
 * @param appearance das Aussehen; gesetzt genau dann, wenn {@code category == COSMETIC}
 */
public record ItemTemplate(
        String key,
        ItemCategory category,
        String material,
        Rarity rarity,
        Integer minLevel,
        CharacterClass boundClass,
        Long sellPrice,
        Integer modelData,
        ConsumableEffect effect,
        CosmeticAppearance appearance) {

    public ItemTemplate {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(category, "category");
        Objects.requireNonNull(material, "material");
        Objects.requireNonNull(rarity, "rarity");

        if (key.isBlank()) {
            throw new IllegalArgumentException("key must not be blank");
        }
        if (material.isBlank()) {
            throw new IllegalArgumentException(key + ": material must not be blank");
        }
        // Die Kategorie bestimmt, welche Haelfte gesetzt sein muss. Ein Verbrauchbares mit
        // Aussehen und ohne Wirkung waere ein Gegenstand, den man nicht benutzen und nicht
        // anlegen kann - und genau so etwas soll gar nicht erst starten (FR-012).
        if ((category == ItemCategory.CONSUMABLE) != (effect != null)) {
            throw new IllegalArgumentException(
                    key
                            + ": an effect belongs to a CONSUMABLE and only to one, but category was "
                            + category
                            + " and effect was "
                            + (effect == null ? "absent" : "present"));
        }
        if ((category == ItemCategory.COSMETIC) != (appearance != null)) {
            throw new IllegalArgumentException(
                    key
                            + ": an appearance belongs to a COSMETIC and only to one, but category"
                            + " was "
                            + category
                            + " and appearance was "
                            + (appearance == null ? "absent" : "present"));
        }
        if (minLevel != null && (minLevel < 1 || minLevel > 60)) {
            throw new IllegalArgumentException(
                    key + ": min-level must be between 1 and 60, but was " + minLevel);
        }
        if (sellPrice != null && sellPrice < 0L) {
            throw new IllegalArgumentException(
                    key + ": sell-price must not be negative, but was " + sellPrice);
        }
    }

    public OptionalInt minLevelOrNone() {
        return minLevel == null ? OptionalInt.empty() : OptionalInt.of(minLevel);
    }

    public Optional<CharacterClass> boundClassOrNone() {
        return Optional.ofNullable(boundClass);
    }

    /** Was der Haendler dafuer zahlt. Leer heisst unverkaeuflich (FR-016). */
    public OptionalLong sellPriceOrNone() {
        return sellPrice == null ? OptionalLong.empty() : OptionalLong.of(sellPrice);
    }

    public OptionalInt modelDataOrNone() {
        return modelData == null ? OptionalInt.empty() : OptionalInt.of(modelData);
    }

    public Optional<ConsumableEffect> effectOrNone() {
        return Optional.ofNullable(effect);
    }

    public Optional<CosmeticAppearance> appearanceOrNone() {
        return Optional.ofNullable(appearance);
    }

    /**
     * Ob dieser Charakter die Vorlage ueberhaupt benutzen darf (FR-015).
     *
     * <p>Prueft Mindestlevel und Klassenbindung - nicht die Abklingzeit und nicht, ob die Wirkung
     * gerade etwas taete. Beides haengt am Charakter zur Laufzeit und nicht an der Vorlage.
     */
    public boolean allows(int level, CharacterClass characterClass) {
        if (minLevel != null && level < minLevel) {
            return false;
        }
        return boundClass == null || boundClass == characterClass;
    }
}
