package rpg.core.ui;

import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.classes.LadderSlot;
import rpg.core.classes.TierAppearance;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.stats.StatSnapshot;

/**
 * Baut eine {@link CharacterSheet} aus den Nähten der Blöcke, die die Daten führen.
 *
 * <h2>Sie liest fünf Blöcke und entscheidet nichts</h2>
 *
 * <p>B04 (Attribute und die Revision), B06 (Level), B07 (Klasse), B08b (Coins), B11 (Ausrüstung und
 * Zustand). <b>Keiner davon wird angefasst</b> — die Übersicht ist die Stelle, an der vier Blöcke
 * zum ersten Mal nebeneinander zu sehen sind, und mehr soll sie nicht sein (FR-074).
 *
 * <h2>Die Quellen kommen als Funktionen herein, nicht als Module</h2>
 *
 * <p>Damit {@code rpg.core.ui} nicht von fünf anderen Paketen abhängt, um vier Zahlen zu lesen. Es
 * ist dieselbe Bauart, mit der {@code MobModule} seine Zonen und {@code StatisticsModule} seine
 * Vorlagen bekommt — und sie hält die Startreihenfolge frei.
 *
 * <h2>Leer heißt „kein Charakter", nicht „keine Werte"</h2>
 *
 * <p>Ein Spieler ohne gewählten Charakter bekommt keine Übersicht (FR-008). Nullen, die wie echte
 * Werte aussehen, sind schlimmer als nichts — und in einem Fenster, das man ausdrücklich öffnet,
 * noch mehr als auf einer Fläche, die man nur streift.
 */
public final class CharacterSheets {

    /** Was B04 über einen Charakter weiß. */
    public interface StatSource {
        Optional<StatSnapshot> snapshotOf(UUID characterId);
    }

    /** Was auf welchem Platz steckt und in welchem Zustand — B07 und B11. */
    public interface EquipmentSource {
        Map<LadderSlot, TierAppearance> equipmentOf(UUID characterId);

        /** Der Bindungsvermerk, den B07 fuer diesen Charakter und Platz erwartet. */
        Optional<String> tagOf(UUID characterId, LadderSlot slot);

        double conditionOf(UUID characterId, LadderSlot slot);
    }

    private final StatSource stats;
    private final EquipmentSource equipment;
    private final java.util.function.Function<UUID, Optional<CharacterClass>> classOf;
    private final java.util.function.ToIntFunction<UUID> levelOf;
    private final java.util.function.ToLongFunction<UUID> coinsOf;

    public CharacterSheets(
            StatSource stats,
            EquipmentSource equipment,
            java.util.function.Function<UUID, Optional<CharacterClass>> classOf,
            java.util.function.ToIntFunction<UUID> levelOf,
            java.util.function.ToLongFunction<UUID> coinsOf) {
        this.stats = Objects.requireNonNull(stats, "stats");
        this.equipment = Objects.requireNonNull(equipment, "equipment");
        this.classOf = Objects.requireNonNull(classOf, "classOf");
        this.levelOf = Objects.requireNonNull(levelOf, "levelOf");
        this.coinsOf = Objects.requireNonNull(coinsOf, "coinsOf");
    }

    /**
     * Die Übersicht dieses Charakters, oder leer.
     *
     * <p>Leer, wenn B04 ihn nicht kennt oder B07 keine Klasse führt — beides heißt „noch kein
     * spielbarer Charakter", und beides ist ein normaler Zustand zwischen Anmeldung und Wahl.
     */
    public Optional<CharacterSheet> of(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");

        Optional<StatSnapshot> snapshot = stats.snapshotOf(characterId);
        if (snapshot.isEmpty()) {
            return Optional.empty();
        }
        Optional<CharacterClass> characterClass = classOf.apply(characterId);
        if (characterClass.isEmpty()) {
            return Optional.empty();
        }

        // JEDES Attribut, aus der Aufzaehlung und nicht aus einer Liste. Kommt eines dazu, waechst
        // die Uebersicht mit - eine Liste hier waere die naechste Divergenz, und die Spec hat sich
        // bei der ZAHL der Attribute schon einmal geirrt (acht statt zehn).
        Map<Attribute, Double> values = new EnumMap<>(Attribute.class);
        for (Attribute attribute : Attribute.values()) {
            values.put(attribute, snapshot.get().get(attribute));
        }

        Map<LadderSlot, TierAppearance> worn = equipment.equipmentOf(characterId);
        Map<LadderSlot, Double> conditions = new EnumMap<>(LadderSlot.class);
        Map<LadderSlot, String> tags = new EnumMap<>(LadderSlot.class);
        for (LadderSlot slot : LadderSlot.values()) {
            conditions.put(slot, equipment.conditionOf(characterId, slot));
            equipment.tagOf(characterId, slot).ifPresent(tag -> tags.put(slot, tag));
        }

        return Optional.of(
                new CharacterSheet(
                        characterId,
                        characterClass.get(),
                        levelOf.applyAsInt(characterId),
                        coinsOf.applyAsLong(characterId),
                        values,
                        snapshot.get().revision(),
                        worn,
                        tags,
                        conditions));
    }
}
