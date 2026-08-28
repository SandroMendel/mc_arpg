package rpg.platform.item;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

import rpg.core.item.ConsumableBuffs;
import rpg.core.item.ConsumableEffect;
import rpg.core.item.ConsumableUse;
import rpg.core.item.ItemMessageKeys;
import rpg.core.item.ItemTemplate;
import rpg.core.item.Items;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.session.CharacterClass;

/**
 * Ein Spieler benutzt einen Trank (FR-033 bis FR-037).
 *
 * <p><b>Die Regel liegt in {@code rpg-core}</b> — {@link ConsumableUse} entscheidet, ob es geht und
 * warum nicht. Diese Klasse macht daraus einen Rechtsklick, einen verbrauchten Stapel und eine
 * Meldung.
 *
 * <p><b>Der Vanilla-Trinkvorgang wird abgebrochen.</b> Ein B11-Trank ist ein Behälter mit einer
 * Vorlagen-ID, kein Vanilla-Trank; ihn von Minecraft trinken zu lassen brächte Vanilla-Effekte
 * obendrauf und eine leere Flasche, die niemand bestellt hat. Abbrechen, prüfen, anwenden,
 * verbrauchen — in dieser Reihenfolge.
 *
 * <p><b>Verbraucht wird nur, was gewirkt hat.</b> Jede Ablehnung lässt den Stapel unangetastet
 * (FR-036) und sagt dem Spieler, woran es lag (FR-037) — „geht nicht" ist keine Antwort.
 */
public final class ConsumableUseListener implements Listener {

    private final Items items;
    private final ConsumableUse rule;
    private final ConsumableBuffs buffs;
    private final Resources resources;
    private final Function<UUID, Optional<UUID>> characterOf;
    private final Function<UUID, Optional<UUID>> holderOf;
    private final Function<UUID, Integer> levelOf;
    private final Function<UUID, Optional<CharacterClass>> classOf;
    private final Messages messages;
    private final Logger logger;

    /**
     * Die drei Fragen an B04 — als Naht, nicht als Abhängigkeit auf die ganze {@code StatEngine}.
     *
     * <p>Dieselbe Überlegung wie bei {@link ConsumableBuffs.BuffSink}: die Engine hat neunzehn
     * Methoden, gebraucht werden drei.
     */
    public interface Resources {

        double currentHealth(UUID holderId);

        double maxHealth(UUID holderId);

        double currentMana(UUID holderId);

        double maxMana(UUID holderId);

        void changeHealth(UUID holderId, double delta);

        void changeMana(UUID holderId, double delta);
    }

    public ConsumableUseListener(
            Items items,
            ConsumableUse rule,
            ConsumableBuffs buffs,
            Resources resources,
            Function<UUID, Optional<UUID>> characterOf,
            Function<UUID, Optional<UUID>> holderOf,
            Function<UUID, Integer> levelOf,
            Function<UUID, Optional<CharacterClass>> classOf,
            Messages messages,
            Logger logger) {
        this.items = Objects.requireNonNull(items, "items");
        this.rule = Objects.requireNonNull(rule, "rule");
        this.buffs = Objects.requireNonNull(buffs, "buffs");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.characterOf = Objects.requireNonNull(characterOf, "characterOf");
        this.holderOf = Objects.requireNonNull(holderOf, "holderOf");
        this.levelOf = Objects.requireNonNull(levelOf, "levelOf");
        this.classOf = Objects.requireNonNull(classOf, "classOf");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        ItemStack stack = event.getItem();
        if (stack == null || !ItemTag.isOurs(stack)) {
            // Fast jeder Rechtsklick im Spiel. Nichts tun ist hier die richtige Antwort und der
            // haeufigste Pfad.
            return;
        }
        if (!event.getAction().isRightClick()) {
            return;
        }

        // Ab hier gehoert der Vorgang uns: Vanilla soll ihn nicht auch noch anfassen.
        event.setCancelled(true);

        try {
            apply(event.getPlayer(), stack);
        } catch (RuntimeException failure) {
            // Ein Fehler hier darf den Spieler nicht in einen kaputten Zustand bringen
            // (Constitution VI).
            logger.log(Level.WARNING, "[item] could not use a consumable", failure);
        }
    }

    private void apply(Player player, ItemStack stack) {
        Optional<UUID> characterId = characterOf.apply(player.getUniqueId());
        Optional<UUID> holderId = holderOf.apply(player.getUniqueId());
        if (characterId.isEmpty() || holderId.isEmpty()) {
            return;
        }

        String templateKey = ItemTag.templateOf(stack).orElseThrow();
        Optional<ItemTemplate> template = items.template(templateKey);

        ConsumableUse.Result result =
                rule.use(
                        template,
                        characterId.get(),
                        levelOf.apply(characterId.get()),
                        classOf.apply(characterId.get()).orElse(null));

        if (!result.isSuccess()) {
            tell(player, result);
            return;
        }

        ConsumableEffect effect = template.orElseThrow().effect();
        effect.healAmount()
                .ifPresent(amount -> resources.changeHealth(holderId.get(), amount));
        effect.manaAmount().ifPresent(amount -> resources.changeMana(holderId.get(), amount));
        if (effect.hasBuff()) {
            buffs.apply(holderId.get(), templateKey, effect);
        }

        // Genau EIN Exemplar (FR-033) - und erst jetzt, nachdem gewirkt wurde.
        stack.setAmount(stack.getAmount() - 1);
    }

    /** Sagt, woran es lag. „Geht nicht" ist keine Antwort (FR-037). */
    private void tell(Player player, ConsumableUse.Result result) {
        MessageKey key =
                switch (result.outcome()) {
                    case LEVEL_TOO_LOW -> ItemMessageKeys.REFUSED_LEVEL;
                    case WRONG_CLASS -> ItemMessageKeys.REFUSED_CLASS;
                    case ON_COOLDOWN -> ItemMessageKeys.REFUSED_COOLDOWN;
                    case NO_EFFECT -> ItemMessageKeys.REFUSED_NO_EFFECT;
                    case UNKNOWN_TEMPLATE -> ItemMessageKeys.REFUSED_UNKNOWN;
                    case USED -> null;
                };
        if (key == null || !messages.contains(key)) {
            return;
        }
        String text =
                result.remaining() == null
                        ? messages.get(key, java.util.Map.of())
                        : messages.get(
                                key,
                                java.util.Map.of(
                                        "seconds", String.valueOf(result.remaining().toSeconds())));
        player.sendMessage(
                net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection()
                        .deserialize(text));
    }

    /**
     * Ob dieser Trank überhaupt etwas bewirken würde — die Naht, die {@link ConsumableUse} fragt.
     *
     * <p><b>Statisch, und das ist kein Stilentscheid.</b> Die Regel braucht diese Antwort, und der
     * Zuhörer braucht die Regel; als Instanzmethode wäre das ein Ring, den die Verdrahtung nur mit
     * einem halb gebauten Objekt aufbrechen könnte. So hängt beides an denselben zwei Nähten und an
     * keinem Objekt.
     */
    public static ConsumableUse.WouldDoSomething wouldDoSomething(
            Resources resources, Function<UUID, Optional<UUID>> holderOf) {
        return (characterId, effect) -> {
            Optional<UUID> holder = holderOf.apply(characterId);
            if (holder.isEmpty()) {
                return false;
            }
            UUID id = holder.get();
            if (effect.hasBuff()) {
                // Ein Buff wirkt immer - er ersetzt hoechstens sich selbst.
                return true;
            }
            boolean healUseful =
                    effect.healAmount()
                            .map(amount -> resources.currentHealth(id) < resources.maxHealth(id))
                            .orElse(false);
            boolean manaUseful =
                    effect.manaAmount()
                            .map(amount -> resources.currentMana(id) < resources.maxMana(id))
                            .orElse(false);
            return healUseful || manaUseful;
        };
    }
}
