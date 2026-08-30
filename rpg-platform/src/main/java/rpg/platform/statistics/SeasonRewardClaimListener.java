package rpg.platform.statistics;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.currency.BookingReason;
import rpg.core.currency.Currency;
import rpg.core.message.Messages;
import rpg.core.statistics.ClaimOutcome;
import rpg.core.statistics.RewardClaim;
import rpg.core.statistics.StatisticsConfig;
import rpg.core.statistics.StatisticsMessageKeys;

/**
 * Löst Saisonbelohnungen ein — <b>und bietet sie an, wenn jemand wiederkommt</b> (FR-052, FR-054).
 *
 * <h2>Die Reihenfolge ist die ganze Sicherheit</h2>
 *
 * <ol>
 *   <li><b>Platz prüfen.</b> Vorhersehbar, deshalb vorher (FR-055) — sonst wäre der Anspruch
 *       verbraucht und die Belohnung nirgends.
 *   <li><b>Markieren.</b> Das bedingte Update entscheidet, wer gewinnt (R2).
 *   <li><b>Gutschreiben.</b> Erst jetzt, und nur wenn Schritt 2 gewonnen hat.
 * </ol>
 *
 * <p>Ein Absturz zwischen 2 und 3 kostet höchstens eine Belohnung und verdoppelt keine. Das ist
 * die Abwägung: eine verlorene kann ein Betreiber nachtragen, eine verdoppelte muss er
 * zurückholen — und der Spieler hat sie schon ausgegeben.
 *
 * <h2>Coins und Gegenstände nehmen die vorhandenen Wege</h2>
 *
 * <p>Coins über B08bs {@link Currency} mit einem eigenen Buchungsgrund, Gegenstände über B11s
 * Vorlagen. Keine eigene Gutschrift, keine eigene Herstellung: beides gäbe es dann zweimal, und
 * die zweite Fassung kennte die Regeln der ersten nicht — die Deckelung des Kontostands etwa, oder
 * die Bindung eines Ausrüstungsstücks.
 *
 * <p><b>Protokolliert wird über den vorhandenen Weg</b> (FR-056): der Buchungsgrund
 * {@link BookingReason#SEASON_REWARD} steht im Coin-Ledger, das B08b ohnehin führt. Ein eigenes
 * Protokoll wäre eine zweite Liste über dieselben Vorgänge.
 */
public final class SeasonRewardClaimListener {

    /** Ob im Inventar dieser Figur noch so viele Stapel Platz haben. */
    @FunctionalInterface
    public interface InventorySpace {
        boolean hasRoomFor(UUID playerId, int stacks);
    }

    /** Legt einen Gegenstand ins Inventar — B11s Weg, nicht ein eigener. */
    @FunctionalInterface
    public interface ItemDelivery {
        boolean deliver(UUID playerId, String templateKey, int amount);
    }

    private final Currency currency;
    private final InventorySpace space;
    private final ItemDelivery delivery;
    private final Messages messages;
    private final Logger logger;
    private final Clock clock;

    /** Markiert den Anspruch; {@code true} heißt „diese Einlösung hat gewonnen". */
    private final ClaimMarker marker;

    /** Was ein Konto offen hat. */
    private final Function<UUID, List<RewardClaim>> openClaims;

    /** Sagt dem Spieler etwas. */
    private final BiConsumer<UUID, String> tell;

    /** Der Schritt, der über Gewinnen und Verlieren entscheidet (R2). */
    @FunctionalInterface
    public interface ClaimMarker {
        boolean mark(String seasonKey, UUID playerId, UUID characterId, java.time.Instant at);
    }

    public SeasonRewardClaimListener(
            Currency currency,
            InventorySpace space,
            ItemDelivery delivery,
            ClaimMarker marker,
            Function<UUID, List<RewardClaim>> openClaims,
            BiConsumer<UUID, String> tell,
            Messages messages,
            Logger logger,
            Clock clock) {
        this.currency = Objects.requireNonNull(currency, "currency");
        this.space = Objects.requireNonNull(space, "space");
        this.delivery = Objects.requireNonNull(delivery, "delivery");
        this.marker = Objects.requireNonNull(marker, "marker");
        this.openClaims = Objects.requireNonNull(openClaims, "openClaims");
        this.tell = Objects.requireNonNull(tell, "tell");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Bietet offene Ansprüche an, wenn jemand wieder spielt (FR-052).
     *
     * <p>Angeboten, nicht aufgedrängt: die Einlösung braucht eine Figur und einen freien Platz,
     * und beides entscheidet der Spieler. Wer gerade keinen Platz hat, soll nicht beim Anmelden
     * eine Ablehnung lesen.
     */
    public void offerOpenClaims(UUID playerId) {
        for (RewardClaim claim : openClaims.apply(playerId)) {
            tell.accept(
                    playerId,
                    messages.get(
                            StatisticsMessageKeys.REWARD_AVAILABLE,
                            Map.of(
                                    "season", claim.seasonKey(),
                                    "rank", String.valueOf(claim.rank()))));
        }
    }

    /** Löst einen Anspruch ein. Siehe Klassenkommentar für die Reihenfolge. */
    public ClaimOutcome claim(UUID playerId, UUID characterId, String seasonKey) {
        RewardClaim claim =
                openClaims.apply(playerId).stream()
                        .filter(open -> open.seasonKey().equals(seasonKey))
                        .findFirst()
                        .orElse(null);

        if (claim == null) {
            // Offen ist er nicht - entweder gab es nie einen oder er ist schon weg. Welches von
            // beidem, weiss diese Stelle nicht; der Aufrufer trennt es ueber find(...).
            return ClaimOutcome.NOTHING_TO_CLAIM;
        }

        // SCHRITT 1: Platz pruefen, VOR dem Markieren (FR-055).
        if (!claim.items().isEmpty() && !space.hasRoomFor(playerId, claim.items().size())) {
            tell.accept(
                    playerId,
                    messages.get(StatisticsMessageKeys.REWARD_INVENTORY_FULL, Map.of()));
            return ClaimOutcome.INVENTORY_FULL;
        }

        // SCHRITT 2: markieren. Nur wer hier gewinnt, schreibt gut.
        if (!marker.mark(seasonKey, playerId, characterId, clock.instant())) {
            tell.accept(
                    playerId,
                    messages.get(StatisticsMessageKeys.REWARD_ALREADY_CLAIMED, Map.of()));
            return ClaimOutcome.ALREADY_CLAIMED;
        }

        // SCHRITT 3: gutschreiben.
        credit(playerId, characterId, claim);
        return ClaimOutcome.CLAIMED;
    }

    /**
     * Coins und Gegenstände.
     *
     * <p>Ein Fehler hier verliert die Belohnung — der Anspruch ist bereits markiert. Er wird
     * deshalb <b>laut</b> protokolliert: das ist der eine Fall, in dem ein Betreiber von Hand
     * nachtragen muss, und er soll ihn im Protokoll finden und nicht in einer Beschwerde.
     */
    private void credit(UUID playerId, UUID characterId, RewardClaim claim) {
        StringBuilder what = new StringBuilder();

        if (claim.coins() > 0) {
            currency.credit(characterId, claim.coins(), BookingReason.SEASON_REWARD);
            what.append(claim.coins()).append(" coins");
        }

        for (StatisticsConfig.ItemGrant item : claim.items()) {
            if (!delivery.deliver(playerId, item.template(), item.amount())) {
                logger.log(
                        Level.SEVERE,
                        "[statistics] season reward partially lost: "
                                + claim.seasonKey()
                                + " / "
                                + playerId
                                + " - the claim is already marked, "
                                + item.template()
                                + " x"
                                + item.amount()
                                + " was not delivered");
                continue;
            }
            if (what.length() > 0) {
                what.append(", ");
            }
            what.append(item.template()).append(" x").append(item.amount());
        }

        tell.accept(
                playerId,
                messages.get(
                        StatisticsMessageKeys.REWARD_CLAIMED,
                        Map.of("season", claim.seasonKey(), "reward", what.toString())));
    }
}
