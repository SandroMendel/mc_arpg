package rpg.core.item;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Der geprüfte Inhalt von {@code items.yml} (FR-011).
 *
 * <p><b>Alles, was ein Betreiber ändern können muss, steht hier</b> — und kein Bezeichner einer
 * einzelnen Vorlage steht im Code (FR-013, SC-004). Eine neue Vorlage entsteht durch Bearbeiten der
 * Datei und einen Neustart, ohne eine Zeile Java (SC-002, Prinzip V).
 *
 * <p><b>Nachladen tauscht das Ganze aus</b>, wie {@code MobConfig} es tut. Ein Exemplar in einem
 * Spielerinventar wird dabei nicht angefasst: es trägt nur die Vorlagen-ID, und die zeigt nach dem
 * Nachladen auf die neuen Werte (FR-003, SC-001). Das ist der ganze Zweck der Bauweise.
 *
 * @param templates jede Vorlage unter ihrer Kennung
 * @param wear die Verschleißkurve und die drei Raten
 * @param repair die Reparaturpreise je Stufe
 * @param loot die Beutetabellen in ihren drei Ebenen
 * @param vendors je Region ein Verkaufsbestand
 */
public record ItemConfig(
        Map<String, ItemTemplate> templates,
        WearCurve wear,
        RepairPricing repair,
        LootTables loot,
        Map<String, VendorStock> vendors,
        java.time.Duration inventoryFullCooldown) {

    public ItemConfig {
        // LinkedHashMap statt Map.copyOf: die Fassade verspricht KONFIGURATIONSREIHENFOLGE
        // (contracts/item-api.md §1). Map.copyOf verwirft sie, und der Haendlerbestand stuende
        // dann nach jedem Neustart anders da - ein Fehler, den niemand als Fehler erkennt.
        templates =
                java.util.Collections.unmodifiableMap(
                        new java.util.LinkedHashMap<>(Objects.requireNonNull(templates, "templates")));
        vendors =
                java.util.Collections.unmodifiableMap(
                        new java.util.LinkedHashMap<>(Objects.requireNonNull(vendors, "vendors")));
        Objects.requireNonNull(wear, "wear");
        Objects.requireNonNull(repair, "repair");
        Objects.requireNonNull(loot, "loot");
        Objects.requireNonNull(inventoryFullCooldown, "inventoryFullCooldown");
        if (inventoryFullCooldown.isNegative()) {
            throw new IllegalArgumentException(
                    "inventory-full-cooldown-ms must not be negative, but was "
                            + inventoryFullCooldown);
        }
    }

    /**
     * Die Vorgabe für die Ruhezeit der Warnung bei vollem Inventar (FR-076).
     *
     * <p>Fünfzehn Sekunden — dieselbe Zahl, die B07 fest im Quelltext hatte, bevor sie
     * konfigurierbar wurde. Lang genug, dass ein Haufen Beute <em>eine</em> Warnung erzeugt,
     * kurz genug, dass sie noch eine Warnung ist.
     */
    public static final java.time.Duration DEFAULT_INVENTORY_FULL_COOLDOWN =
            java.time.Duration.ofSeconds(15);

    /**
     * Die Gestalt vor US7 — mit der Vorgabe-Ruhezeit.
     *
     * <p>Dieselbe Bauart wie {@code SessionBundle}: ein neues Feld bekommt einen Konstruktor,
     * der es füllt, statt jeden vorhandenen Aufrufer zu ändern. Ein Prüfstand, der eine Ruhezeit
     * nennen müsste, um über Beutetabellen zu sprechen, prüfte danach weniger klar als vorher.
     */
    public ItemConfig(
            Map<String, ItemTemplate> templates,
            WearCurve wear,
            RepairPricing repair,
            LootTables loot,
            Map<String, VendorStock> vendors) {
        this(templates, wear, repair, loot, vendors, DEFAULT_INVENTORY_FULL_COOLDOWN);
    }

    /** Die Vorlage zu dieser Kennung. Leer bei einer unbekannten — niemals {@code null}. */
    public Optional<ItemTemplate> template(String key) {
        return key == null ? Optional.empty() : Optional.ofNullable(templates.get(key));
    }

    /** Der Bestand des Händlers dieser Region. Leer, wenn dort keiner steht. */
    public VendorStock vendorOf(String zoneKey) {
        return vendors.getOrDefault(zoneKey, VendorStock.empty());
    }
}
