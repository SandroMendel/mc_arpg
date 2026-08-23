package rpg.platform.currency;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;

import rpg.core.currency.BookingReason;
import rpg.core.currency.BookingResult;
import rpg.core.currency.Currency;
import rpg.core.currency.CurrencyMessageKeys;
import rpg.core.message.Messages;
import rpg.core.session.SessionRegistry;

/**
 * Turns picking a pile up into a booking (FR-021, FR-027, FR-033).
 *
 * <p><b>The event is always cancelled.</b> A coin pile must never land in an inventory: it is a
 * value, not an item, and B11 owns items. Cancel, book, remove - in that order.
 *
 * <p><b>Two locks, not one.</b> Vanilla's {@code setOwner} keeps other players and mobs away
 * cheaply, so most attempts never reach this code. What it cannot do is tell characters apart: a
 * player has up to three and can switch mid-session, so without the check below character B would
 * collect what character A earned (ADR-011). Vanilla filters coarsely; this checks exactly.
 *
 * <p><b>Every value comes from the server side.</b> The amount is read from the pile's data
 * container, never from anything the event carries - the player only says "I would like to pick this
 * up" (Constitution VI).
 */
public final class CoinPickupListener implements Listener {

    private final Currency currency;
    private final SessionRegistry sessions;
    private final CoinPileRegistry registry;
    private final Messages messages;

    public CoinPickupListener(
            Currency currency,
            SessionRegistry sessions,
            CoinPileRegistry registry,
            Messages messages) {
        this.currency = Objects.requireNonNull(currency, "currency");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.messages = Objects.requireNonNull(messages, "messages");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        Item pile = event.getItem();
        Optional<UUID> entitled = CoinPileTag.characterOf(pile.getItemStack());
        if (entitled.isEmpty()) {
            // Not one of ours - which is almost every item a player touches.
            return;
        }

        // Ours, so it never enters an inventory whatever happens next.
        event.setCancelled(true);

        Player player = event.getPlayer();
        Optional<UUID> active = activeCharacterOf(player.getUniqueId());
        if (active.isEmpty() || !active.get().equals(entitled.get())) {
            // A different character of the same player, or no character at all. The pile stays
            // where it is: it still belongs to the character who earned it, and it expires with its
            // own timer. Removing or crediting it here would take it from its rightful owner.
            return;
        }

        long amount = CoinPileTag.amountOf(pile.getItemStack()).orElse(0L);
        if (amount <= 0L) {
            // A malformed pile. Take it out of the world rather than leave something unclaimable
            // lying around, but book nothing.
            registry.forget(pile);
            pile.remove();
            return;
        }

        BookingResult result =
                currency.credit(entitled.get(), amount, BookingReason.PILE_PICKED_UP);
        if (!result.isSuccess()) {
            // Only reachable at the far edge of the number range. Leaving the pile is the right
            // answer: the coins still exist, and the player can come back to them.
            player.sendMessage(messages.get(result.messageKey()));
            return;
        }

        registry.forget(pile);
        pile.remove();
        // Der Ton, bevor die Zeile kommt: aufheben ist eine Handlung, und eine Handlung ohne Geraeusch
        // fuehlt sich an, als waere sie nicht passiert. Vanilla-Ton, kein Ressourcenpaket (ADR-005).
        player.playSound(
                player.getLocation(),
                org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP,
                org.bukkit.SoundCategory.PLAYERS,
                0.8f,
                1.4f);
        player.sendMessage(
                messages.get(
                        CurrencyMessageKeys.PILE_PICKED_UP,
                        java.util.Map.of("amount", String.valueOf(amount))));
    }

    private Optional<UUID> activeCharacterOf(UUID playerId) {
        return sessions.find(playerId)
                .flatMap(session -> session.activeCharacter())
                .map(character -> character.characterId());
    }
}
