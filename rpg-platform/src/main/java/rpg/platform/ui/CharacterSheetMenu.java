package rpg.platform.ui;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Material;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import rpg.core.classes.LadderSlot;
import rpg.core.stats.Attribute;
import rpg.core.ui.CharacterSheet;
import rpg.core.ui.UiMessageKeys;

/**
 * Die Charakterübersicht — <b>das einzige wirklich fehlende Fenster</b> dieses Blocks (FR-050 bis
 * FR-057).
 *
 * <p>Vier Blöcke haben Daten, die nirgendwo zusammen zu sehen sind: B04 die Attribute, B07 die
 * Klasse, B08b die Coins, B11 die Ausrüstung und ihren Zustand.
 *
 * <h2>Sie ist zum Lesen da</h2>
 *
 * <p>Kein Klick tut etwas. Das ist keine fehlende Funktion, sondern die Abgrenzung: was ein Spieler
 * mit seiner Ausrüstung <em>macht</em>, entscheiden B07 und B11, und ein Fenster, das beides könnte,
 * wäre die Stelle, an der B13 anfängt, Spiellogik zu haben (FR-074).
 *
 * <h2>Der Inhalt wird zwischengespeichert</h2>
 *
 * <p>FR-054: nur bei Änderung neu aufbauen. Die Marke ist {@code StatSnapshot.revision()} aus B04
 * — sie steckt in der {@link CharacterSheet} und wird beim Öffnen mit dem aktuellen Stand
 * verglichen. Ein Fenster neu zu bauen kostet zehn {@code ItemStack}s und zwei Nachfragen an fremde
 * Blöcke; das zweimal für denselben Stand zu tun ist Arbeit ohne Anlass.
 *
 * <h2>Reine Vanilla-Materialien</h2>
 *
 * <p>ADR-005, FR-056: kein Resource Pack vorausgesetzt. Jedes Attribut bekommt ein Material, das
 * seine Bedeutung trägt — ein Herz für Leben, ein Schwert für Schaden. <b>Die Zuordnung steht im
 * Code und nicht in der Konfiguration</b>, weil sie zur Bedeutung gehört: ein Betreiber, der Leben
 * auf einen Stock legt, hat nichts falsch konfiguriert, sondern etwas unverständlich gemacht.
 */
public final class CharacterSheetMenu {

    /** Sechs Zeilen: zehn Attribute, zwei Ausrüstungsplätze und drei Kopfzeilen passen hinein. */
    private static final int ROWS = 6;

    /** Wo die Attribute stehen — zwei Spalten im Inneren, von oben nach unten. */
    private static final int[] ATTRIBUTE_SLOTS = {
        10, 11, 12, 19, 20, 21, 28, 29, 30, 37
    };

    private static final int CLASS_SLOT = 4;
    private static final int LEVEL_SLOT = 14;
    private static final int COINS_SLOT = 23;
    private static final int ARMOR_SLOT = 15;
    private static final int WEAPON_SLOT = 16;

    private final MenuFrame frame;
    private final ItemRenderer items;

    /** Der zuletzt gebaute Stand je Spieler — die Grundlage von FR-054. */
    private final Map<UUID, Cached> cache = new ConcurrentHashMap<>();

    private record Cached(UUID characterId, long revision, Inventory inventory) {}

    public CharacterSheetMenu(MenuFrame frame, ItemRenderer items) {
        this.frame = Objects.requireNonNull(frame, "frame");
        this.items = Objects.requireNonNull(items, "items");
    }

    /**
     * Das Fenster dieses Spielers — aus dem Zwischenspeicher, wenn es noch gilt.
     *
     * <p>Neu gebaut wird bei einer neuen Revision <b>oder</b> bei einem anderen Charakter. Der
     * zweite Fall ist der, der ohne Prüfung ein fremdes Fenster zeigte: derselbe Spieler, andere
     * Figur, gecachte Zahlen.
     */
    public Inventory open(UUID playerId, CharacterSheet sheet) {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(sheet, "sheet");

        Cached cached = cache.get(playerId);
        if (cached != null
                && cached.characterId().equals(sheet.characterId())
                && cached.revision() == sheet.revision()) {
            return cached.inventory();
        }
        Inventory built = build(sheet);
        cache.put(playerId, new Cached(sheet.characterId(), sheet.revision(), built));
        return built;
    }

    /** Vergisst den Zwischenspeicher dieses Spielers — beim Charakterwechsel und beim Abmelden. */
    public void forget(UUID playerId) {
        cache.remove(playerId);
    }

    /** Wie viele Fenster gerade zwischengespeichert sind — für den Test gegen ein Leck. */
    int cached() {
        return cache.size();
    }

    private Inventory build(CharacterSheet sheet) {
        Inventory inventory =
                frame.build(
                        UiMessageKeys.SHEET_TITLE,
                        Map.of("character", sheet.characterClass().name()),
                        ROWS);

        frame.put(
                inventory,
                CLASS_SLOT,
                materialOfClass(sheet),
                UiMessageKeys.SHEET_CLASS,
                Map.of("class", sheet.characterClass().name()),
                List.of());
        frame.put(
                inventory,
                LEVEL_SLOT,
                Material.EXPERIENCE_BOTTLE,
                UiMessageKeys.SHEET_LEVEL,
                Map.of("level", Integer.toString(sheet.level())),
                List.of());
        frame.put(
                inventory,
                COINS_SLOT,
                Material.GOLD_INGOT,
                UiMessageKeys.SHEET_COINS,
                Map.of("coins", Long.toString(sheet.coins())),
                List.of());

        // JEDES Attribut, aus der Aufzaehlung. Kommt eines dazu, waechst das Fenster mit - solange
        // Plaetze da sind, und der Test haelt fest, dass sie reichen.
        Attribute[] attributes = Attribute.values();
        for (int i = 0; i < attributes.length && i < ATTRIBUTE_SLOTS.length; i++) {
            Attribute attribute = attributes[i];
            frame.put(
                    inventory,
                    ATTRIBUTE_SLOTS[i],
                    materialOf(attribute),
                    keyFor(attribute),
                    Map.of(
                            "label",
                            labelTextOf(attribute),
                            "value",
                            valueTextOf(sheet, attribute)),
                    List.of());
        }

        putEquipment(inventory, ARMOR_SLOT, sheet, LadderSlot.ARMOR);
        putEquipment(inventory, WEAPON_SLOT, sheet, LadderSlot.WEAPON);
        return inventory;
    }

    private void putEquipment(
            Inventory inventory, int slot, CharacterSheet sheet, LadderSlot ladderSlot) {
        Optional<String> templateKey = sheet.equipmentOn(ladderSlot);
        if (templateKey.isEmpty()) {
            frame.put(
                    inventory,
                    slot,
                    Material.BARRIER,
                    UiMessageKeys.SHEET_EQUIPMENT_EMPTY,
                    Map.of("slot", ladderSlot.name()),
                    List.of());
            return;
        }
        double condition = sheet.conditionOn(ladderSlot);
        // renderGear und NICHT render(templateKey, ...): getragene Ausruestung hat keine
        // items.yml-Vorlage - ItemCategory kennt nur CONSUMABLE und COSMETIC. Was hier im
        // Ausruestungsplatz steht, ist das Material aus B07s TierAppearance; den Zustand darunter
        // schreibt B11 (FR-051, FR-021a).
        Optional<ItemStack> rendered = items.renderGear(ladderSlot, templateKey.get(), condition);
        if (rendered.isEmpty()) {
            // Unbekannte Vorlage: den Platz frei lassen statt den Aufrufer mitzureissen. Ein
            // Tippfehler in items.yml darf kein kaputtes Fenster ergeben.
            frame.put(
                    inventory,
                    slot,
                    Material.BARRIER,
                    UiMessageKeys.SHEET_EQUIPMENT_EMPTY,
                    Map.of("slot", ladderSlot.name()),
                    List.of());
            return;
        }
        // Die Zustandszeile schreibt die NAHT, nicht dieses Fenster (FR-021a): PaperItemRenderer
        // reicht an GearConditionDisplay.paint durch, und das fuehrt sie an fester Stelle und
        // ersetzt sie beim naechsten Mal. Sie hier ein zweites Mal anzuhaengen ergaebe zwei Zeilen
        // mit demselben Wert - und beim naechsten Aufruf drei.
        inventory.setItem(slot, rendered.get());
    }

    /**
     * Welcher Schlüssel für dieses Attribut gilt.
     *
     * <p><b>{@link Attribute#ABILITY_COOLDOWN} ist das einzige prozentual gelesene.</b> Ohne eigene
     * Zeile stünde dort eine nackte {@code 0.15}, und der Spieler rechnete selbst.
     */
    private static rpg.core.message.MessageKey keyFor(Attribute attribute) {
        return attribute == Attribute.ABILITY_COOLDOWN
                ? UiMessageKeys.SHEET_ATTRIBUTE_PERCENT
                : UiMessageKeys.SHEET_ATTRIBUTE;
    }

    private String labelTextOf(Attribute attribute) {
        return net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer.plainText()
                .serialize(frame.render(UiMessageKeys.attributeLabel(attribute), Map.of()));
    }

    private static String valueTextOf(CharacterSheet sheet, Attribute attribute) {
        double value = sheet.valueOf(attribute);
        if (attribute == Attribute.ABILITY_COOLDOWN) {
            return Long.toString(Math.round(value * 100.0));
        }
        // Keine Nachkommastellen: 148.7 Verteidigung liest sich als 149, und der Bruch ist auf
        // dieser Skala Rauschen. Dieselbe Entscheidung wie auf der Actionbar.
        return Long.toString(Math.round(value));
    }

    /**
     * Das Material zu einem Attribut.
     *
     * <p>Im Code, weil es zur Bedeutung gehört und nicht zum Geschmack — siehe Klassenkommentar.
     */
    private static Material materialOf(Attribute attribute) {
        return switch (attribute) {
            case HEALTH -> Material.RED_DYE;
            case HEALTH_REGEN -> Material.GOLDEN_APPLE;
            case DEFENSE -> Material.SHIELD;
            case MANA -> Material.LAPIS_LAZULI;
            case MANA_REGEN -> Material.GLOWSTONE_DUST;
            case PHYSICAL_DAMAGE -> Material.IRON_SWORD;
            case MAGIC_DAMAGE -> Material.BLAZE_ROD;
            case ATTACK_SPEED -> Material.FEATHER;
            case MOVEMENT_SPEED -> Material.SUGAR;
            case ABILITY_COOLDOWN -> Material.CLOCK;
        };
    }

    private static Material materialOfClass(CharacterSheet sheet) {
        return switch (sheet.characterClass()) {
            case WARRIOR -> Material.IRON_CHESTPLATE;
            case MAGE -> Material.ENCHANTED_BOOK;
            case ROGUE -> Material.LEATHER_BOOTS;
        };
    }
}
