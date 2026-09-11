package rpg.platform.item;

import java.util.Objects;
import java.util.Optional;
import java.util.OptionalInt;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

/**
 * Was ein Gegenstand dieses Blocks mit sich trägt — <b>zwei Werte, und keinen dritten</b> (FR-001,
 * FR-004).
 *
 * <ul>
 *   <li><b>template</b> — die Vorlagen-ID. Der Schlüssel, über den Name, Lore, Werte und Wirkung
 *       bei jedem Laden neu abgeleitet werden.
 *   <li><b>schema</b> — die Schema-Version, damit eine ältere Fassung beim Laden angehoben werden
 *       kann (FR-005). Der Migrationspfad existiert ab Tag eins, weil er sich nachträglich nicht
 *       erfinden lässt.
 * </ul>
 *
 * <p><b>Was hier NICHT steht, ist die eigentliche Zusage.</b> Kein Heilbetrag, kein Verkaufserlös,
 * kein gerendertes Lore, keine Raritätsfarbe. Stünde eines davon hier, wäre nach dem Release kein
 * Rebalancing mehr möglich, ohne jedes Item in jedem Spielerinventar anzufassen (ADR-004 in der
 * Fassung von ADR-027, Constitution IV). {@code ItemStoresOnlyTheTemplateIdTest} zählt die
 * Schlüssel nach.
 *
 * <p><b>Seit ADR-027 gibt es auch nichts mehr zu würfeln.</b> Die ursprüngliche Fassung von ADR-004
 * sprach von „Vorlagen-ID <em>und gewürfelten Roll-Werten</em>"; der Roll-Mechanismus ist
 * abgeschafft, und damit ist die Vorlage die einzige Quelle. Die Zusage wird dadurch stärker: ein
 * geändertes Balancing wirkt auf jedes vorhandene Exemplar statt nur auf neue.
 *
 * <p><b>Im Persistent Data Container, nicht im Namen oder in der Lore.</b> Dieselbe Wahl, die
 * {@code MobKindTag} und {@code CoinPileTag} getroffen haben: ein Name ist Anzeige, und Anzeige ist
 * etwas, worüber man einen Client belügen kann (Constitution VI). Lore-Parsing als Datenquelle ist
 * ausdrücklich unzulässig.
 */
public final class ItemTag {

    /** Fest, denn sie überleben einen Neustart. */
    static final NamespacedKey TEMPLATE =
            Objects.requireNonNull(NamespacedKey.fromString("rpg:item_template"));

    static final NamespacedKey SCHEMA =
            Objects.requireNonNull(NamespacedKey.fromString("rpg:item_schema"));

    private ItemTag() {}

    /**
     * Heftet Vorlage und Schema-Version an.
     *
     * <p>Ein zweites Schreiben <b>ersetzt</b> statt zu verdoppeln — ein Container kennt jeden
     * Schlüssel einmal.
     */
    public static void mark(ItemStack stack, String templateKey, int schemaVersion) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(templateKey, "templateKey");
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            // Ein Stapel ohne Meta gibt es fuer die Materialien dieses Blocks nicht; wenn doch,
            // ist die Kennzeichnung unmoeglich und ein stiller Verzicht waere schlechter als eine
            // klare Meldung.
            throw new IllegalStateException(
                    "cannot mark " + stack.getType() + " - it carries no item meta");
        }
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(TEMPLATE, PersistentDataType.STRING, templateKey);
        container.set(SCHEMA, PersistentDataType.INTEGER, schemaVersion);
        stack.setItemMeta(meta);
    }

    /** Die Vorlage dieses Exemplars, falls es eines dieses Blocks ist. */
    public static Optional<String> templateOf(ItemStack stack) {
        return read(stack).map(container -> container.get(TEMPLATE, PersistentDataType.STRING));
    }

    /** Die Schema-Version dieses Exemplars. */
    public static OptionalInt schemaOf(ItemStack stack) {
        Optional<Integer> value =
                read(stack).map(container -> container.get(SCHEMA, PersistentDataType.INTEGER));
        return value.map(OptionalInt::of).orElseGet(OptionalInt::empty);
    }

    /** Ob dieser Block dieses Exemplar erzeugt hat. */
    public static boolean isOurs(ItemStack stack) {
        return templateOf(stack).isPresent();
    }

    /**
     * Der Container, falls es einen gibt.
     *
     * <p>{@code null} ist kein Fehler: ein Stapel kann leer sein, und ein Slot ohne Gegenstand ist
     * der Normalfall im Inventarklick-Pfad, nicht die Ausnahme.
     */
    private static Optional<PersistentDataContainer> read(ItemStack stack) {
        if (stack == null || stack.getType().isAir()) {
            return Optional.empty();
        }
        ItemMeta meta = stack.getItemMeta();
        return meta == null ? Optional.empty() : Optional.of(meta.getPersistentDataContainer());
    }
}
