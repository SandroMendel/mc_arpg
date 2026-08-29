package rpg.platform.item;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.projectiles.ProjectileSource;

import rpg.core.item.ConsumableBuffs;
import rpg.core.item.ConsumableEffect;
import rpg.core.item.ItemTemplate;
import rpg.core.item.ItemMessageKeys;
import rpg.core.item.Items;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;

/**
 * Ein geworfener Trank schlägt auf — und wirkt auf <b>jeden, den er trifft</b>.
 *
 * <p><b>Warum Heiltränke geworfen werden.</b> Ein Trank, den man nur selbst trinkt, hilft in einer
 * Gruppe genau einem. Geworfen hilft er allen, die im Radius stehen — und macht aus dem
 * Heiltrank eine Entscheidung, wohin man ihn wirft, statt einer Taste, die man drückt.
 *
 * <p><b>Vanilla wirft, dieser Block wirkt.</b> Flugbahn, Aufprall, Partikel und der Verbrauch des
 * Stapels sind Vanillas Arbeit; hier wird nur die eigene Wirkung angewandt und Vanillas eigene
 * abgeschaltet. Ein selbst gebautes Wurfgeschoss wäre eine zweite Fassung von etwas, das es schon
 * gibt — und eine schlechtere.
 *
 * <p><b>Nur Spieler.</b> B10s Kreaturen haben eigene Werte und damit auch einen Halter; ein
 * Heiltrank, der die Horde mitheilt, wäre kein Heiltrank, sondern ein Fehler. Der Vermerk am
 * getroffenen Wesen entscheidet das nicht — dessen Art tut es.
 *
 * <p><b>Volle Wirkung für jeden Getroffenen, nicht nach Entfernung abgestuft.</b> Vanilla skaliert
 * die Stärke mit dem Abstand; hier steht der Betrag in der Lore, und ein Trank, der „heilt 140" sagt
 * und 63 heilt, ist ein Trank, dem man nicht glaubt.
 */
public final class PotionSplashListener implements Listener {

    private final Items items;
    private final ConsumableBuffs buffs;
    private final ConsumableUseListener.Resources resources;
    private final Function<UUID, Optional<UUID>> characterOf;
    private final Messages messages;
    private final Logger logger;

    /**
     * @param characterOf Halter zu Charakter — B04s Übersetzung. Für einen Spieler ist die
     *     Halter-Kennung seine Spieler-Kennung; wer hier keinen Charakter hat, spielt nicht mit und
     *     wird nicht geheilt.
     */
    public PotionSplashListener(
            Items items,
            ConsumableBuffs buffs,
            ConsumableUseListener.Resources resources,
            Function<UUID, Optional<UUID>> characterOf,
            Messages messages,
            Logger logger) {
        this.items = Objects.requireNonNull(items, "items");
        this.buffs = Objects.requireNonNull(buffs, "buffs");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.characterOf = Objects.requireNonNull(characterOf, "characterOf");
        this.messages = Objects.requireNonNull(messages, "messages");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onSplash(PotionSplashEvent event) {
        ItemStack thrown = event.getPotion().getItem();
        Optional<String> templateKey = ItemTag.templateOf(thrown);
        if (templateKey.isEmpty()) {
            // Ein gewoehnlicher Vanilla-Wurftrank. Das Spiel bleibt Minecraft.
            return;
        }
        // Die Getroffenen ZUERST festhalten, und zwar in einer eigenen Liste.
        //
        // Bukkits setIntensity(entity, 0) ENTFERNT die Entität aus der Sammlung des Ereignisses -
        // die Sammlung ist eine Sicht auf dieselbe Abbildung. Wer erst neutralisiert und dann über
        // getAffectedEntities() läuft, läuft über eine leere Liste und heilt niemanden. Genau das
        // stand hier zuerst, und es sah aus wie ein Trank, der nichts tut.
        List<LivingEntity> hit = List.copyOf(event.getAffectedEntities());

        Optional<ItemTemplate> template = items.template(templateKey.get());
        if (template.isEmpty() || template.get().effect() == null) {
            // Vorlage verschwunden (FR-007) oder keine Wirkung: der Trank zerschellt und tut
            // nichts. Kein Absturz, kein Vanilla-Effekt, keine Meldung.
            neutralise(event, hit);
            return;
        }

        neutralise(event, hit);
        try {
            ConsumableEffect effect = template.get().effect();
            Player thrower = throwerOf(event);
            for (LivingEntity affected : hit) {
                applyTo(affected, templateKey.get(), effect, thrower);
            }
        } catch (RuntimeException failure) {
            // Ein Fehler an einem Getroffenen darf die anderen nicht mitnehmen und den Server
            // nicht anhalten (Prinzip VI).
            logger.log(Level.WARNING, "[item] a thrown consumable failed on impact", failure);
        }
    }

    /**
     * Vanillas eigene Wirkung aus.
     *
     * <p>Ein B11-Trank ist ein Behälter mit einer Vorlagen-ID, kein Vanilla-Trank. Ohne diese Zeile
     * käme Vanillas Effekt obendrauf — bei einem Wassergefäß nichts, bei einem falsch
     * konfigurierten Material dagegen ein Effekt, den niemand bestellt hat.
     */
    private static void neutralise(PotionSplashEvent event, List<LivingEntity> hit) {
        for (LivingEntity affected : hit) {
            event.setIntensity(affected, 0.0);
        }
    }

    /**
     * Was ein Getroffener abbekommt.
     *
     * <p><b>Nur Spieler</b> — siehe Klassenkommentar. Und volle Wirkung, nicht nach Entfernung
     * abgestuft: in der Lore steht eine Zahl, und sie soll stimmen.
     */
    private void applyTo(
            LivingEntity hit, String templateKey, ConsumableEffect effect, Player thrower) {
        if (!(hit instanceof Player player)) {
            return;
        }
        UUID holderId = player.getUniqueId();
        if (characterOf.apply(holderId).isEmpty()) {
            // Kein Charakter, keine Werte - jemand, der gerade in der Klassenauswahl steht.
            return;
        }
        effect.healAmount().ifPresent(amount -> resources.changeHealth(holderId, amount));
        effect.manaAmount().ifPresent(amount -> resources.changeMana(holderId, amount));
        if (effect.hasBuff()) {
            buffs.apply(holderId, templateKey, effect);
        }
        tell(player, effect, thrower);
    }

    /**
     * Sagt dem Getroffenen, was er bekommen hat — und von wem.
     *
     * <p><b>Ohne diese Meldung merkt er nur, dass sich eine Zahl geändert hat.</b> Ein Wurftrank
     * wirkt auf Leute, die ihn nicht geworfen haben; woher die Heilung kam und wie viel es war,
     * steht sonst nirgends.
     *
     * <p><b>Beim eigenen Wurf ohne Namen.</b> Wer selbst geworfen hat, weiß, von wem der Trank
     * kam — der Name wäre die Antwort auf eine Frage, die niemand gestellt hat. Was bleibt, ist
     * die Wirkung, und die ist auch beim eigenen Wurf nicht selbstverständlich: die Zahl steht
     * zwar in der Lore, aber ein Trank, den man wirft, heilt einen anderen Betrag, wenn er kurz
     * vorher balanciert wurde.
     */
    private void tell(Player hit, ConsumableEffect effect, Player thrower) {
        boolean own = thrower != null && thrower.getUniqueId().equals(hit.getUniqueId());
        String name = thrower == null ? "" : thrower.getName();

        effect.healAmount()
                .ifPresent(
                        amount ->
                                send(
                                        hit,
                                        own ? ItemMessageKeys.SPLASH_SELF_HEALED : ItemMessageKeys.SPLASH_HEALED,
                                        values(name, Map.of("amount", EffectLore.number(amount)))));
        effect.manaAmount()
                .ifPresent(
                        amount ->
                                send(
                                        hit,
                                        own ? ItemMessageKeys.SPLASH_SELF_MANA : ItemMessageKeys.SPLASH_MANA,
                                        values(name, Map.of("amount", EffectLore.number(amount)))));

        // Je Attribut eine Zeile, wie in der Lore. Ein Trank mit zwei Beitraegen waere in einer
        // Zeile eine Aufzaehlung, die im Chat vorbeirauscht.
        String seconds = effect.buffDuration().map(d -> String.valueOf(d.toSeconds())).orElse("0");
        effect.buff()
                .forEach(
                        (attribute, amount) ->
                                send(
                                        hit,
                                        own ? ItemMessageKeys.SPLASH_SELF_BUFF : ItemMessageKeys.SPLASH_BUFF,
                                        values(
                                                name,
                                                Map.of(
                                                        "attribute",
                                                        EffectLore.attributeName(messages, attribute),
                                                        "amount",
                                                        EffectLore.number(amount),
                                                        "seconds",
                                                        seconds))));
    }

    /** Die Platzhalter plus den Werfernamen — der bei einem eigenen Wurf schlicht ungenutzt bleibt. */
    private static Map<String, String> values(String thrower, Map<String, String> own) {
        Map<String, String> all = new LinkedHashMap<>(own);
        all.put("player", thrower);
        return all;
    }

    private void send(Player player, MessageKey key, Map<String, String> placeholders) {
        net.kyori.adventure.text.Component text = ItemText.of(messages, key, placeholders);
        if (text != null) {
            player.sendMessage(text);
        }
    }

    /**
     * Wer geworfen hat — oder {@code null}.
     *
     * <p>Ein Wurfgeschoss muss keinen Spieler als Quelle haben: ein Spender wirft auch. Dann gibt
     * es keinen Namen zu nennen, und die Meldung fällt auf die namenlose Fassung zurück.
     */
    private static Player throwerOf(PotionSplashEvent event) {
        ProjectileSource source = event.getPotion().getShooter();
        return source instanceof Player player ? player : null;
    }
}
