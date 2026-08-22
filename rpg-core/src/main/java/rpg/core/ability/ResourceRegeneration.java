package rpg.core.ability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rpg.core.combat.CombatPipeline;
import rpg.core.stats.Attribute;
import rpg.core.stats.ResourceView;
import rpg.core.stats.StatEngine;

/**
 * Health and mana coming back over time (FR-033 to FR-038b).
 *
 * <p><b>This is where a wounded player heals at all.</b> ADR-013 switched off vanilla regeneration so
 * that only the engine writes the health bar, and never supplied a replacement - until ADR-023 made
 * the two rates attributes, a damaged character simply stayed damaged.
 *
 * <h2>Two timestamps, no event, no task</h2>
 *
 * <p>Per character this keeps exactly two instants: when it was last settled, and when the combat it
 * last saw ends. That is enough to split any interval into its combat and its idle part <b>exactly</b>,
 * without waiting for {@code CombatStateChangedEvent} - which matters, because that event's leaving
 * edge is not actually published in production today (research.md R3), and because no event reaches a
 * player who is offline (FR-038).
 *
 * <p>Nothing is scheduled. Regeneration does not happen at a moment; it is <em>established</em> at a
 * moment, whenever somebody asks (Principle II).
 */
public final class ResourceRegeneration {

    private final StatEngine stats;
    private final CombatPipeline combat;
    private final AbilityRegistry registry;
    private final Clock clock;

    private final Map<UUID, State> states = new ConcurrentHashMap<>();

    public ResourceRegeneration(
            StatEngine stats, CombatPipeline combat, AbilityRegistry registry, Clock clock) {
        this.stats = Objects.requireNonNull(stats, "stats");
        this.combat = Objects.requireNonNull(combat, "combat");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Settles every character that is currently in play.
     *
     * <p><b>Why this exists at all, and why the paragraph below used to say "never periodically".</b>
     * The original design settled only when somebody asked - before a mana check, before damage,
     * before a read. That is correct for <em>mana</em>, where the question "can I afford this" is the
     * only moment the number matters. It is wrong for <em>health</em>: a wounded player standing
     * still asks nothing, and expects their health to climb anyway. Until this method existed, a
     * player who never triggered an ability never regenerated at all.
     *
     * <p>It is the same distinction ADR-024 draws for the scheduler: timestamp arithmetic answers
     * questions, it does not perform actions. Regeneration turned out to be an action.
     *
     * <p><b>No new task.</b> This rides the one sweep that already drives every timed ability effect,
     * so Constitution II is untouched: still no recurring work per player, per entity or per party -
     * one pass that walks a list. Settling is arithmetic plus two map operations, and it is
     * idempotent, so an extra pass costs a subtraction that yields zero.
     */
    public void settleAll(java.util.Collection<UUID> characterIds) {
        if (characterIds == null || characterIds.isEmpty()) {
            return;
        }
        for (UUID characterId : characterIds) {
            try {
                settle(characterId);
            } catch (RuntimeException failure) {
                // One character's failure must not stop the rest of the sweep (Constitution VI).
                // No logger here on purpose: this class is pure arithmetic and has never needed one,
                // and a log line per character per pass would be the loudest thing in the file at
                // 150 players. The failure is swallowed at the level where it cannot spread.
                continue;
            }
        }
    }

    /**
     * Credits whatever has accrued since the last settlement.
     *
     * <p>Call before checking mana, before applying damage and before reading a resource (FR-037) -
     * and, since {@link #settleAll}, also from the sweep. The answer "not enough mana" must never be
     * down to an outstanding settlement, which is why the on-demand calls stay even though the sweep
     * would eventually get there.
     */
    public void settle(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        Instant now = clock.instant();
        State state = states.get(characterId);
        if (state == null) {
            // First contact. Nothing accrued before a character was being watched, and inventing a
            // start would credit the whole time since the epoch.
            states.put(characterId, new State(now, combatEnd(characterId, now)));
            return;
        }

        ResourceView resources = stats.resources(characterId);
        if (resources.currentHealth() <= 0.0) {
            // Dead. The clock moves on but nothing accrues, and settling anyway would let a corpse
            // heal itself back over the respawn screen (FR-038b).
            states.put(characterId, new State(now, combatEnd(characterId, now)));
            return;
        }

        Split split = split(state, now);
        AbilityConfig config = registry.config();

        credit(
                characterId,
                stats.value(characterId, Attribute.HEALTH_REGEN),
                split,
                config.healthCombatFactor(),
                stats::changeHealth);
        credit(
                characterId,
                stats.value(characterId, Attribute.MANA_REGEN),
                split,
                config.manaCombatFactor(),
                stats::changeMana);

        states.put(characterId, new State(now, combatEnd(characterId, now)));
    }

    /**
     * Credits time that passed while the character was away (FR-038).
     *
     * <p>Called once by the load path with the moment the session ended. Everything since then counts
     * as idle: nobody was there to fight.
     */
    public void settleAbsence(UUID characterId, Instant lastSeen) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(lastSeen, "lastSeen");
        states.put(characterId, new State(lastSeen, null));
        settle(characterId);
    }

    /** Drops a character. On logout and on a character switch. */
    public void forget(UUID characterId) {
        states.remove(characterId);
    }

    /** How many characters are tracked. For leak tests. */
    public int trackedCount() {
        return states.size();
    }

    /**
     * Splits the elapsed interval at the moment combat ended.
     *
     * <p>The whole point of keeping {@code combatEndsAt}: at settlement time the character may be long
     * out of combat, and crediting the entire interval at the reduced rate - or at the full one -
     * would be wrong in opposite directions depending on how long ago the fight was.
     */
    private static Split split(State state, Instant now) {
        long total = Math.max(0L, Duration.between(state.lastSettledAt(), now).toMillis());
        if (total == 0L) {
            return new Split(0L, 0L);
        }
        Instant combatEnds = state.combatEndsAt();
        if (combatEnds == null || !combatEnds.isAfter(state.lastSettledAt())) {
            // Combat was already over when we last looked: all of it is idle time.
            return new Split(0L, total);
        }
        if (!combatEnds.isBefore(now)) {
            // Still in combat for the whole interval.
            return new Split(total, 0L);
        }
        long inCombat = Duration.between(state.lastSettledAt(), combatEnds).toMillis();
        return new Split(inCombat, total - inCombat);
    }

    private void credit(
            UUID characterId,
            double ratePerSecond,
            Split split,
            double combatFactor,
            java.util.function.ObjDoubleConsumer<UUID> apply) {
        if (ratePerSecond <= 0.0) {
            // Base zero, no class contributor: a creature. It does not heal itself (ADR-023).
            return;
        }
        double amount =
                ratePerSecond * (split.combatMillis() * combatFactor + split.idleMillis()) / 1000.0;
        if (amount > 0.0) {
            // changeHealth and changeMana clamp, so a surplus is discarded rather than reported
            // (FR-038a).
            apply.accept(characterId, amount);
        }
    }

    /** When the combat this character is in ends, or {@code null} if they are not in one. */
    private Instant combatEnd(UUID characterId, Instant now) {
        if (!combat.isInCombat(characterId)) {
            return null;
        }
        Optional<Duration> left = combat.remainingCombatTime(characterId);
        return left.map(now::plus).orElse(null);
    }

    /** What is kept per character. Two instants, nothing else. */
    private record State(Instant lastSettledAt, Instant combatEndsAt) {}

    /** How the elapsed interval divides. */
    private record Split(long combatMillis, long idleMillis) {}
}
