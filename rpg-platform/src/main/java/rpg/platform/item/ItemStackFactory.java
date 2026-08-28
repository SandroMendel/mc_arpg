package rpg.platform.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

import rpg.core.item.ItemMessageKeys;
import rpg.core.item.ItemTemplate;
import rpg.core.item.Items;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;

/**
 * Macht aus einer Vorlage ein Exemplar — und aus einem Exemplar wieder die aktuelle Ansicht
 * (FR-002).
 *
 * <p><b>Der ganze Block hängt an einer Zeile:</b> gespeichert wird die Vorlagen-ID, abgeleitet wird
 * alles andere. Name, Lore, Raritätsbezeichnung und Wirkungstext entstehen bei <em>jedem</em> Laden
 * neu aus {@code items.yml}. Ändert ein Betreiber einen Heilbetrag und lädt neu, wirkt das auf
 * jedes vorhandene Exemplar in jedem Inventar — ohne dass ein Inventar angefasst wird (SC-001).
 *
 * <p><b>Deshalb gibt es hier kein Feld und keinen Zwischenspeicher.</b> Jeder Aufruf liest die
 * aktuelle Vorlage. Ein zwischengespeicherter {@code ItemStack} wäre nach dem nächsten Reload die
 * alte Fassung, und der Fehler fiele erst auf, wenn jemand fragt, warum sein Trank noch das Alte
 * tut.
 *
 * <p><b>Eine unbekannte Vorlage ist kein Fehler.</b> Das Exemplar bleibt, trägt seinen rohen
 * Schlüssel und ist inert — tragbar und vernichtbar, nicht benutzbar und nicht verkäuflich
 * (FR-007). Stilles Löschen wäre Datenverlust ohne Ansage; ein Startabbruch wäre die Strafe für den
 * Betreiber statt für den Fehler.
 */
public final class ItemStackFactory {

    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final Items items;
    private final Messages messages;

    public ItemStackFactory(Items items, Messages messages) {
        this.items = Objects.requireNonNull(items, "items");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    /**
     * Erzeugt ein Exemplar.
     *
     * @return leer, wenn die Vorlage unbekannt ist oder ihr Material kein Vanilla-Material ist —
     *     beides ist beim Start bereits abgefangen, aber ein Reload kann eine Vorlage entfernen
     */
    public Optional<ItemStack> create(String templateKey, int amount) {
        Optional<ItemTemplate> template = items.template(templateKey);
        if (template.isEmpty()) {
            return Optional.empty();
        }
        Material material = materialOf(template.get());
        if (material == null) {
            return Optional.empty();
        }
        ItemStack stack = new ItemStack(material, Math.max(1, amount));
        ItemTag.mark(stack, templateKey, ItemSchemaMigration.CURRENT);
        render(stack);
        return Optional.of(stack);
    }

    /**
     * Schreibt Name und Lore neu — aus der <b>aktuellen</b> Vorlage.
     *
     * <p>Aufgerufen beim Erzeugen und bei jedem Laden. Das ist der Weg, auf dem eine
     * Balancing-Änderung ein Exemplar erreicht, das seit Wochen in einer Enderchest liegt.
     */
    public void render(ItemStack stack) {
        Optional<String> key = ItemTag.templateOf(stack);
        if (key.isEmpty()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (meta == null) {
            return;
        }
        Optional<ItemTemplate> template = items.template(key.get());
        if (template.isEmpty()) {
            // Inert: der rohe Schluessel als Name, damit ein Spieler ueberhaupt sieht, was er
            // haelt, und ein Betreiber es wiederfindet (FR-007).
            meta.displayName(Component.text(key.get()));
            meta.lore(List.of());
            stack.setItemMeta(meta);
            return;
        }

        ItemTemplate found = template.get();
        meta.displayName(text(ItemMessageKeys.nameOf(found.key()), found.key()));

        List<Component> lore = new ArrayList<>(2);
        MessageKey loreKey = ItemMessageKeys.loreOf(found.key());
        if (messages.contains(loreKey)) {
            lore.add(text(loreKey, ""));
        }
        lore.add(text(ItemMessageKeys.rarityName(found.rarity()), found.rarity().configKey()));
        meta.lore(lore);

        // Das reservierte Feld aus ADR-005: gesetzt, sobald eine Vorlage es traegt, und im
        // Vanilla-Betrieb ungenutzt (FR-006).
        found.modelDataOrNone().ifPresent(meta::setCustomModelData);

        stack.setItemMeta(meta);
    }

    /** Die Vorlage hinter einem Exemplar. Leer fuer alles, was kein B11-Item ist. */
    public Optional<ItemTemplate> templateOf(ItemStack stack) {
        return ItemTag.templateOf(stack).flatMap(items::template);
    }

    private Component text(MessageKey key, String fallback) {
        if (!messages.contains(key)) {
            return Component.text(fallback);
        }
        return LEGACY.deserialize(messages.get(key, Map.of()));
    }

    private static Material materialOf(ItemTemplate template) {
        return Material.matchMaterial(template.material().toUpperCase(Locale.ROOT));
    }
}
