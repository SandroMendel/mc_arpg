package rpg.platform.item;

import java.util.List;
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

import rpg.core.item.ConsumableBuffs;
import rpg.core.item.ConsumableEffect;
import rpg.core.item.ItemTemplate;
import rpg.core.item.Items;

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
            Logger logger) {
        this.items = Objects.requireNonNull(items, "items");
        this.buffs = Objects.requireNonNull(buffs, "buffs");
        this.resources = Objects.requireNonNull(resources, "resources");
        this.characterOf = Objects.requireNonNull(characterOf, "characterOf");
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
            for (LivingEntity affected : hit) {
                applyTo(affected, templateKey.get(), effect);
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
    private void applyTo(LivingEntity hit, String templateKey, ConsumableEffect effect) {
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
    }
}
