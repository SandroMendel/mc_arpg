package rpg.core.item;

import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;

import rpg.core.classes.LadderSlot;
import rpg.core.currency.BookingReason;
import rpg.core.currency.Currency;

/**
 * Die bezahlte Instandsetzung (FR-052 bis FR-054).
 *
 * <p><b>Dieselbe Reihenfolge wie überall, und aus demselben Grund</b> ({@link VendorTransaction},
 * {@code EquipmentPurchase}): jede Bedingung ist geprüft, bevor sich eine einzige Coin bewegt. Wer
 * bezahlt hat und nichts bekam, kann sich nicht selbst helfen.
 *
 * <p><b>Ein Slot, ein Preis.</b> Rüstung und Waffe verschleißen getrennt (FR-039), also werden sie
 * getrennt repariert — wer nur die Waffe abgenutzt hat, zahlt nicht für die Rüstung mit.
 *
 * <p><b>Eine Reparatur ohne Verschleiß wird abgelehnt</b> (FR-054), nicht als Nullbuchung
 * durchgeführt. Der Unterschied ist für den Spieler sichtbar: „da ist nichts zu reparieren" ist eine
 * Auskunft, eine wortlos gebuchte Null ist ein Rätsel.
 *
 * <p><b>Bukkit-frei.</b> Welche Stufe der Charakter erreicht hat, kommt über eine Naht — B07 besitzt
 * die Antwort, und eine zweite hier wäre eine zweite Wahrheit (FR-079).
 */
public final class GearRepair {

    /** Was aus einem Versuch geworden ist. */
    public enum Outcome {
        /** Durchgeführt und gebucht. */
        DONE,
        /** Da war nichts abgenutzt (FR-054). */
        NOT_WORN,
        /** Zu wenig Coins — und nichts wurde angefasst. */
        NOT_ENOUGH_COINS,
        /** Kein Zustand geladen: der Charakter ist nicht da. */
        UNKNOWN_CHARACTER
    }

    /** Das Ergebnis, plus der Preis, falls einer geflossen ist. */
    public record Result(Outcome outcome, long price) {

        public boolean isSuccess() {
            return outcome == Outcome.DONE;
        }

        static Result refused(Outcome outcome, long price) {
            return new Result(outcome, price);
        }
    }

    /** Welche Stufe dieser Charakter in dieser Leiter erreicht hat — B07s Antwort, nicht eine zweite. */
    @FunctionalInterface
    public interface TierLookup {
        int tierOf(UUID characterId, LadderSlot slot);
    }

    private final Supplier<ItemConfig> config;
    private final DefaultGearConditions conditions;
    private final TierLookup tiers;
    private final Currency currency;

    public GearRepair(
            Supplier<ItemConfig> config,
            DefaultGearConditions conditions,
            TierLookup tiers,
            Currency currency) {
        this.config = Objects.requireNonNull(config, "config");
        this.conditions = Objects.requireNonNull(conditions, "conditions");
        this.tiers = Objects.requireNonNull(tiers, "tiers");
        this.currency = Objects.requireNonNull(currency, "currency");
    }

    /** Was diese Reparatur kosten würde — für die Anzeige, nicht als Zusage. */
    public long priceOf(UUID characterId, LadderSlot slot) {
        return config.get()
                .repair()
                .priceFor(tiers.tierOf(characterId, slot), conditions.conditionOf(characterId, slot));
    }

    /**
     * Repariert eine Leiter.
     *
     * <p>Drei Prüfungen, und erst danach die Buchung: der Zustand ist geladen, es ist überhaupt
     * etwas abgenutzt, und der Kontostand reicht.
     */
    public Result repair(UUID characterId, LadderSlot slot) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(slot, "slot");

        if (conditions.of(characterId).isEmpty()) {
            return Result.refused(Outcome.UNKNOWN_CHARACTER, 0L);
        }
        long price = priceOf(characterId, slot);
        if (price <= 0L) {
            // Null heisst "nichts abgenutzt" - und das ist eine Auskunft, keine Gratisreparatur.
            return Result.refused(Outcome.NOT_WORN, 0L);
        }
        if (!currency.canAfford(characterId, price)) {
            return Result.refused(Outcome.NOT_ENOUGH_COINS, price);
        }

        // Erst hier. Und die Wiederherstellung DANACH - schlaegt sie fehl, waere eine Buchung ohne
        // Gegenwert entstanden, und genau davor steht diese Reihenfolge.
        currency.debit(characterId, price, BookingReason.REPAIR);
        if (!conditions.repair(characterId, slot)) {
            // Kann nur passieren, wenn zwischen Pruefung und Ausfuehrung etwas den Zustand
            // zurueckgesetzt hat. Die Coins zurueck, sonst waere bezahlt und nichts geschehen.
            currency.credit(characterId, price, BookingReason.REPAIR);
            return Result.refused(Outcome.NOT_WORN, 0L);
        }
        return new Result(Outcome.DONE, price);
    }
}
