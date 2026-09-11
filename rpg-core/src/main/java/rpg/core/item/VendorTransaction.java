package rpg.core.item;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

import rpg.core.currency.BookingReason;
import rpg.core.currency.Currency;

/**
 * Kaufen und Verkaufen beim NPC (FR-060 bis FR-064).
 *
 * <p><b>Die Reihenfolge ist die Anforderung, nicht ein Nebeneffekt.</b> Jede Bedingung ist geprüft,
 * <em>bevor</em> sich eine einzige Coin bewegt. Ein Spieler, der bezahlt hat und nichts bekam, ist
 * der schlechteste Ausgang, den dieser Block erzeugen kann — und der einzige, den ein Spieler nicht
 * selbst wieder in Ordnung bringen kann.
 *
 * <p>Dieselbe Bauart wie {@code EquipmentPurchase} in B08b, und dort steht die Begründung schon:
 * <em>„a player charged for something that did not happen is the worst outcome this block can
 * produce."</em>
 *
 * <p><b>Zwei Buchungsgründe, und sie sind aus Sicht des Spielers benannt</b> (B08bs
 * {@code BookingReason}): {@code VENDOR_SALE} ist eine <em>Gutschrift</em> — der Spieler verkauft;
 * {@code VENDOR_PURCHASE} ist eine <em>Belastung</em> — er kauft. Die Spec hatte beide vertauscht,
 * bis beim Verdrahten in der Aufzählung nachgesehen wurde.
 *
 * <p><b>Bukkit-frei.</b> Ob im Inventar Platz ist und ob ein Gegenstand gebunden ist, fragt diese
 * Klasse über Nähte; wie ein Stapel entfernt oder gegeben wird, entscheidet die Plattformschicht.
 */
public final class VendorTransaction {

    /** Was aus einem Versuch geworden ist. */
    public enum Outcome {
        /** Durchgeführt und gebucht. */
        DONE,
        /** Der Gegenstand ist unverkäuflich — keine {@code sell-price} an der Vorlage (FR-016). */
        NOT_SELLABLE,
        /** Klassenausrüstung. Sie bleibt am Charakter (FR-063, ADR-018). */
        BOUND_EQUIPMENT,
        /** Dieser Händler führt die Vorlage nicht (FR-058). */
        NOT_IN_STOCK,
        /** Zu wenig Coins. */
        NOT_ENOUGH_COINS,
        /** Kein Platz im Inventar (FR-064). */
        NO_ROOM,
        /** Die Vorlage ist unbekannt (FR-007). */
        UNKNOWN_TEMPLATE
    }

    /** Das Ergebnis, plus der Betrag, falls einer geflossen ist. */
    public record Result(Outcome outcome, long amount) {

        public boolean isSuccess() {
            return outcome == Outcome.DONE;
        }

        static Result refused(Outcome outcome) {
            return new Result(outcome, 0L);
        }
    }

    /** Ob im Inventar dieses Charakters noch Platz für so viele Stück ist. */
    @FunctionalInterface
    public interface RoomCheck {
        boolean hasRoomFor(UUID characterId, String templateKey, int amount);
    }

    /** Ob dieser Gegenstand Klassenausrüstung ist — B07s Prädikat, nicht ein zweites. */
    @FunctionalInterface
    public interface BoundCheck {
        boolean isBoundEquipment(UUID characterId, String boundTag);
    }

    private final Supplier<ItemConfig> config;
    private final Currency currency;
    private final RoomCheck room;

    public VendorTransaction(Supplier<ItemConfig> config, Currency currency, RoomCheck room) {
        this.config = Objects.requireNonNull(config, "config");
        this.currency = Objects.requireNonNull(currency, "currency");
        this.room = Objects.requireNonNull(room, "room");
    }

    /**
     * Der Charakter verkauft.
     *
     * <p>Geprüft wird: die Vorlage existiert, sie ist nicht gebunden, sie hat einen Erlös. Erst dann
     * wird gutgeschrieben — und der Aufrufer entfernt den Stapel, nachdem dies {@code DONE} sagt.
     *
     * @param bound ob der zu verkaufende Gegenstand Klassenausrüstung ist; die Antwort kommt von
     *     B07s {@code BoundEquipment}, damit es keine zweite Wahrheit darüber gibt (FR-063, FR-079)
     */
    public Result sell(UUID characterId, String templateKey, int amount, boolean bound) {
        Objects.requireNonNull(characterId, "characterId");

        if (bound) {
            // ADR-018: Klassenausruestung ist Teil des Charakters. Keine der drei
            // Entsorgungsrouten nimmt sie an, und der Haendler ist eine davon.
            return Result.refused(Outcome.BOUND_EQUIPMENT);
        }
        Optional<ItemTemplate> template = config.get().template(templateKey);
        if (template.isEmpty()) {
            return Result.refused(Outcome.UNKNOWN_TEMPLATE);
        }
        java.util.OptionalLong price = template.get().sellPriceOrNone();
        if (price.isEmpty()) {
            // Leer heisst "er nimmt es nicht an" - nicht "er zahlt nichts dafuer". Aus Sicht des
            // Spielers sind das zwei verschiedene Meldungen (FR-016).
            return Result.refused(Outcome.NOT_SELLABLE);
        }

        long total = price.getAsLong() * Math.max(1, amount);
        currency.credit(characterId, total, BookingReason.VENDOR_SALE);
        return new Result(Outcome.DONE, total);
    }

    /**
     * Der Charakter kauft.
     *
     * <p><b>Alle vier Bedingungen vor der Buchung</b> (FR-064): die Vorlage existiert, der Händler
     * führt sie, der Kontostand reicht, und im Inventar ist Platz. Ein bezahltes Item, das nirgends
     * hinpasst, ist genau der Fall, für den die letzte Prüfung da ist.
     */
    public Result buy(UUID characterId, String zoneKey, String templateKey, int amount) {
        Objects.requireNonNull(characterId, "characterId");

        ItemConfig current = config.get();
        if (current.template(templateKey).isEmpty()) {
            return Result.refused(Outcome.UNKNOWN_TEMPLATE);
        }
        java.util.OptionalLong price = current.vendorOf(zoneKey).priceOf(templateKey);
        if (price.isEmpty()) {
            return Result.refused(Outcome.NOT_IN_STOCK);
        }

        int count = Math.max(1, amount);
        long total = price.getAsLong() * count;
        if (!currency.canAfford(characterId, total)) {
            return Result.refused(Outcome.NOT_ENOUGH_COINS);
        }
        if (!room.hasRoomFor(characterId, templateKey, count)) {
            return Result.refused(Outcome.NO_ROOM);
        }

        // Erst hier. Alles davor konnte noch abbrechen, ohne dass etwas passiert ist.
        currency.debit(characterId, total, BookingReason.VENDOR_PURCHASE);
        return new Result(Outcome.DONE, total);
    }
}
