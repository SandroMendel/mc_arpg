package rpg.platform.ability;

import java.util.Objects;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.SoundCategory;
import org.bukkit.entity.Player;

import rpg.core.ability.Ability;
import rpg.core.ability.EffectSpec;
import rpg.core.ability.EffectType;

/**
 * The puff and the click that say "that worked".
 *
 * <p><b>Why this exists at all.</b> Several of the eighteen do something a player cannot see: a
 * shield absorbs damage that never arrives, a war cry changes numbers, a mana potion fills a bar the
 * player is not looking at. Without a burst at the moment of the click, a working ability and a
 * swallowed one look exactly alike - which is how three separate bugs stayed hidden behind "nothing
 * happened".
 *
 * <p><b>Chosen from the effect, not configured.</b> The ability already declares what it does; the
 * puff is that declaration made visible, and a second place to state it would be a second place to
 * get it wrong. When B13 owns presentation it can make this configurable, and then the mapping here
 * becomes the default rather than the rule.
 *
 * <p>Vanilla particles and vanilla sounds only (ADR-005). Nothing here needs a resource pack, and
 * nothing here is on the critical path: a failure to draw is caught and logged, never thrown at the
 * player (Constitution VI).
 */
public final class AbilityFeedback {

    private final org.bukkit.Server server;
    private final Logger logger;

    public AbilityFeedback(org.bukkit.Server server, Logger logger) {
        this.server = Objects.requireNonNull(server, "server");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Shows that this ability took hold, at the player who triggered it.
     *
     * <p>Called only on success. A refusal already has words for itself, and adding a puff to it
     * would make "on cooldown" look like it worked.
     */
    public void show(Player player, Ability ability) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(ability, "ability");
        if (primary(ability) == EffectType.DAMAGE) {
            // NICHTS beim Start, wenn die Faehigkeit Schaden macht.
            //
            // Ein Puff am Spieler sagt nur "der Klick kam an" - und genau das ist bei Wirbel, Blitz
            // und Blitzsturm die uninteressante Haelfte. Was ein Spieler sehen will, ist WIE WEIT
            // und WEN es trifft, und das weiss der Startmoment noch gar nicht: die Ziele stehen erst
            // fest, wenn der Effekt laeuft. Deshalb zeichnen diese Faehigkeiten in showImpact.
            return;
        }
        try {
            Location at = player.getLocation();
            Particle particle = particleFor(ability);
            // Around the player rather than at their feet: a 1-block offset is roughly body-sized, and
            // a puff at ankle height reads as a block break rather than as a cast.
            player.getWorld().spawnParticle(particle, at.clone().add(0.0, 1.0, 0.0), 24, 0.5, 0.7, 0.5, 0.02);
            player.playSound(at, soundFor(ability), SoundCategory.PLAYERS, 0.7f, pitchFor(ability));
        } catch (RuntimeException failure) {
            // Feedback is never worth a tick or a session.
            logger.warning(() -> "[abilities] could not draw feedback for " + ability.id() + ": " + failure);
        }
    }

    /**
     * Hebt den Schild - die Haltung, die eine gehaltene Faehigkeit sichtbar macht.
     *
     * <p><b>Nur wenn der Gegenstand einer ist, den man hochhalten kann.</b> Der Block des Warriors
     * traegt einen SHIELD, und Minecraft kennt dafuer eine Pose; der Magieschild traegt einen
     * Amethystsplitter, und den haelt niemand hoch. Die Unterscheidung steht damit in
     * {@code abilities.yml} und nicht hier: was die Pose kann, entscheidet das Material.
     *
     * <p>Das ist auch der Grund, warum der Magier waehrenddessen weiterzaubern kann und der Warrior
     * nicht - der eine haelt etwas, der andere nicht.
     */
    public void holdPose(Player player, Ability ability) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(ability, "ability");
        if (!canBeHeld(ability)) {
            return;
        }
        try {
            // Die Pose folgt dem, was in der Hand ist. Der Slot der Faehigkeit ist die Haupthand,
            // weil der Klick von dort kam.
            player.startUsingItem(org.bukkit.inventory.EquipmentSlot.HAND);
        } catch (RuntimeException failure) {
            logger.warning(() -> "[abilities] could not raise the guard for " + ability.id() + ": " + failure);
        }
    }

    /** Nimmt sie wieder herunter - nach Ablauf der Dauer oder beim zweiten Rechtsklick. */
    public void releasePose(Player player, Ability ability) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(ability, "ability");
        if (!canBeHeld(ability)) {
            return;
        }
        try {
            player.clearActiveItem();
        } catch (RuntimeException failure) {
            logger.warning(() -> "[abilities] could not lower the guard for " + ability.id() + ": " + failure);
        }
    }

    /**
     * Ob dieser Gegenstand eine Haltung kennt.
     *
     * <p>Ein Schild - mehr kennt Minecraft in diesem Zusammenhang nicht, und alles andere waere eine
     * Trinkanimation an einer Faehigkeit, die niemand trinkt.
     */
    private static boolean canBeHeld(Ability ability) {
        if (!ability.isActive() || ability.item() == null) {
            return false;
        }
        org.bukkit.Material material = org.bukkit.Material.matchMaterial(ability.item());
        return material == org.bukkit.Material.SHIELD;
    }

    /**
     * Zeichnet, was ein Effekt gerade getroffen hat - und wie weit er reichte.
     *
     * <p><b>Der Partikel IST der Schaden.</b> Aufgerufen nach jeder Anwendung, auch nach jedem Tick
     * eines periodischen Effekts, also einmal je Umdrehung des Wirbels statt einmal am Anfang. Wer
     * eine Fähigkeit auslöst, sieht damit ihre Reichweite und ihre Treffer statt einer Wolke um die
     * eigenen Füße.
     *
     * <p>Zwei Teile, und der zweite ist der, nach dem gefragt wurde: ein Stoß an jedem Ziel, und für
     * flächige Fähigkeiten ein Ring in der Größe, die sie wirklich hat - aus derselben Zahl, die die
     * Zielsuche benutzt, damit Anzeige und Wirkung nicht auseinanderlaufen können.
     */
    public void showImpact(Ability ability, UUID casterId, java.util.List<UUID> targets) {
        Objects.requireNonNull(ability, "ability");
        if (targets == null || targets.isEmpty()) {
            return;
        }
        try {
            Particle particle = particleFor(ability);
            for (UUID targetId : targets) {
                org.bukkit.entity.Entity target = server.getEntity(targetId);
                if (target == null) {
                    continue;
                }
                Location at = target.getLocation().add(0.0, 1.0, 0.0);
                target.getWorld().spawnParticle(particle, at, 12, 0.3, 0.5, 0.3, 0.01);
            }
            outline(ability, casterId, particle);
        } catch (RuntimeException failure) {
            logger.warning(() -> "[abilities] could not draw impact for " + ability.id() + ": " + failure);
        }
    }

    /**
     * Der Umriss der Fläche, in der die Fähigkeit sucht.
     *
     * <p>Nur für die beiden flächigen Modi - für einen Strahl oder ein Einzelziel wäre ein Ring eine
     * Behauptung über eine Reichweite, die es so nicht gibt.
     *
     * <p>Sechzehn Punkte, nicht sechzig: der Ring soll ablesbar sein, nicht die Sicht nehmen, und ein
     * Kreis aus sechzehn Partikeln ist bei jedem Radius als Kreis erkennbar.
     */
    private void outline(Ability ability, UUID casterId, Particle particle) {
        if (ability.target() == null) {
            return;
        }
        rpg.core.ability.TargetMode mode = ability.target().mode();
        Double radius =
                switch (mode) {
                    case RADIUS -> ability.target().range();
                    case GROUND_AREA -> ability.target().areaRadius();
                    default -> null;
                };
        if (radius == null || radius <= 0.0) {
            return;
        }
        org.bukkit.entity.Entity caster = server.getEntity(casterId);
        if (caster == null) {
            return;
        }
        Location centre = caster.getLocation();
        for (int step = 0; step < 16; step++) {
            double angle = 2.0 * Math.PI * step / 16.0;
            centre.getWorld()
                    .spawnParticle(
                            particle,
                            centre.clone()
                                    .add(Math.cos(angle) * radius, 0.2, Math.sin(angle) * radius),
                            1,
                            0.0,
                            0.0,
                            0.0,
                            0.0);
        }
    }

    /**
     * The particle that fits what the ability does.
     *
     * <p>Read from the FIRST effect: an ability's effects are written in the order they matter, so the
     * first one is the one the player would name if asked what the ability is.
     */
    private static Particle particleFor(Ability ability) {
        return switch (primary(ability)) {
            case DAMAGE -> Particle.CRIT;
            case HEAL, LIFESTEAL -> Particle.HEART;
            case MANA_RESTORE -> Particle.SOUL_FIRE_FLAME;
            case SHIELD, EVADE, MITIGATE -> Particle.ENCHANTED_HIT;
            case DASH, TELEPORT, DOUBLE_JUMP -> Particle.CLOUD;
            case KNOCKBACK -> Particle.SWEEP_ATTACK;
            case SUMMON -> Particle.WITCH;
            case INVISIBILITY -> Particle.SMOKE;
            case STATUS_EFFECT -> Particle.EFFECT;
            case BUFF, METER -> Particle.ANGRY_VILLAGER;
            case DEBUFF -> Particle.SMOKE;
            case PROJECTILE -> Particle.FLAME;
            default -> Particle.ENCHANT;
        };
    }

    /** Defensive rather than offensive, so the ear can tell the two apart without looking. */
    private static Sound soundFor(Ability ability) {
        return switch (primary(ability)) {
            case HEAL, LIFESTEAL, MANA_RESTORE -> Sound.ENTITY_PLAYER_LEVELUP;
            case SHIELD, EVADE, MITIGATE -> Sound.ITEM_SHIELD_BLOCK;
            case DASH, TELEPORT, DOUBLE_JUMP -> Sound.ENTITY_ENDERMAN_TELEPORT;
            case SUMMON -> Sound.ENTITY_EVOKER_PREPARE_SUMMON;
            default -> Sound.ENTITY_ILLUSIONER_PREPARE_BLINDNESS;
        };
    }

    /** A little higher for what heals, a little lower for what hits. Two states, not a scale. */
    private static float pitchFor(Ability ability) {
        return switch (primary(ability)) {
            case HEAL, LIFESTEAL, MANA_RESTORE, SHIELD, EVADE, MITIGATE -> 1.2f;
            default -> 0.9f;
        };
    }

    private static EffectType primary(Ability ability) {
        for (EffectSpec spec : ability.effects()) {
            return spec.type();
        }
        return null;
    }
}
