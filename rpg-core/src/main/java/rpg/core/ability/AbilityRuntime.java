package rpg.core.ability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import rpg.core.ability.effect.EffectDispatcher;
import rpg.core.scheduler.TaskHandle;
import rpg.core.stats.Attribute;
import rpg.core.stats.ResourceView;
import rpg.core.stats.StatEngine;
import rpg.core.stats.StatSnapshot;

/**
 * Triggering an ability: the checks, the cost, the cooldowns and the effects (FR-024 to FR-030).
 *
 * <p><b>Nothing here counts down.</b> A cooldown is two timestamps compared when somebody asks, and
 * the global lock is one more. That is what keeps Principle II true at 150 players: no recurring task
 * per player exists, because none is needed.
 */
public final class AbilityRuntime {

    /** The hard cap on cooldown reduction from ADR-008. Not configurable, by decision. */
    public static final double MAX_COOLDOWN_REDUCTION = 0.40;

    private final AbilityRegistry registry;
    private final StatEngine stats;
    private final TargetResolver targets;
    private final EffectDispatcher effects;
    private final AbilityStateRepository repository;

    /**
     * Who charges for a rank. Free until B08b installs itself - which is what this block did for its
     * whole life before a currency existed.
     */
    private volatile RankCost rankCost = RankCost.free();
    private final Clock clock;

    /**
     * Settled before the mana check, never on a timer (FR-037).
     *
     * <p>Optional so the runtime stays testable on its own; absent means nothing accrues, which is the
     * safe direction - a missing settlement can only make an ability harder to use, never easier.
     */
    private volatile ResourceRegeneration regeneration;

    /** Installs the regeneration. At startup, not during play. */
    public void setRegeneration(ResourceRegeneration regeneration) {
        this.regeneration = regeneration;
    }

    /** At most one running ability per character - a cast or a sustained one (FR-040, FR-045b). */
    private final Map<UUID, RunningAbility> running = new ConcurrentHashMap<>();

    /** Charge pools, keyed by character and ability. */
    private final Map<Key, Charges> charges = new ConcurrentHashMap<>();

    /**
     * Where a delayed one-shot is placed.
     *
     * <p>The only thing in this block that schedules anything, and it is entity-bound and single-shot
     * (ADR-024). A character with nothing running has no task.
     */
    private volatile Scheduling scheduling = Scheduling.none();

    /** How the runtime reaches the scheduler without knowing about Paper. */
    @FunctionalInterface
    public interface Scheduling {
        /**
         * Runs {@code task} on the tick of the entity behind this holder, after {@code delay}.
         *
         * <p><b>A HOLDER id, not a character id.</b> What is scheduled is bound to an entity, and an
         * entity is addressed by holder - the character id names a row, not something standing in a
         * world. The block translates before it calls this.
         */
        TaskHandle after(UUID holderId, Duration delay, Runnable task);

        /** Schedules nothing. Then a cast never completes, which a test can rely on. */
        static Scheduling none() {
            return (holderId, delay, task) ->
                    new TaskHandle() {
                        @Override
                        public void cancel() {}

                        @Override
                        public boolean isCancelled() {
                            return true;
                        }
                    };
        }
    }

    /** Installs the scheduling. At startup, not during play. */
    public void setScheduling(Scheduling scheduling) {
        this.scheduling = Objects.requireNonNull(scheduling, "scheduling");
    }

    /**
     * The holder behind a character - the id everything outside this block speaks.
     *
     * <p><b>This block is keyed by character and that is correct</b>: unlocks, ranks, cooldowns and
     * toggles belong to the character, not the account (ADR-011). But four things outside it are keyed
     * by <em>holder</em> - the stat engine, the target resolver, the effects and the scheduler - and a
     * holder is the player's UUID for a player and the entity's for a mob, which is what lets one
     * pipeline damage both.
     *
     * <p>Until this method existed the character id was handed straight across all four boundaries.
     * Every stat call threw {@code NoSuchElementException}, the trigger listener contained it as it is
     * meant to, and the visible result was a server on which no ability did anything and no mana was
     * ever spent. Empty means the character is not in play; every caller here says so in its own way
     * rather than pretending otherwise.
     */
    private Optional<UUID> holderOf(UUID characterId) {
        return stats.holderOf(characterId);
    }

    private TaskHandle scheduleCompletion(UUID characterId, Duration delay) {
        // Bound to the ENTITY, so the task runs on the thread that owns it (ADR-024) - and an entity
        // is addressed by holder id. The callback still carries the character: what it resumes is the
        // character's ability, not the player's.
        return scheduling.after(
                holderOf(characterId).orElse(characterId), delay, () -> completeWindUp(characterId));
    }

    private TaskHandle scheduleEnd(UUID characterId, Duration delay) {
        return scheduling.after(
                holderOf(characterId).orElse(characterId), delay, () -> expire(characterId));
    }

    /** Character plus ability - the key a charge pool hangs on. */
    private record Key(UUID characterId, String abilityId) {}

    /** A charge pool: how many are left, and when one was last taken. */
    private record Charges(int remaining, Instant lastUsedAt) {

        /** Whether the refill window has passed, which puts the pool back at its maximum. */
        boolean hasLapsed(Instant now, Duration window) {
            return window != null && !now.isBefore(lastUsedAt.plus(window));
        }
    }

    public AbilityRuntime(
            AbilityRegistry registry,
            StatEngine stats,
            TargetResolver targets,
            EffectDispatcher effects,
            AbilityStateRepository repository,
            Clock clock) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.stats = Objects.requireNonNull(stats, "stats");
        this.targets = Objects.requireNonNull(targets, "targets");
        this.effects = Objects.requireNonNull(effects, "effects");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Tries to trigger an ability.
     *
     * <p><b>The order of the checks is the contract</b> (contracts/ability-api.md): character active,
     * ability unlocked, global lock free, own cooldown free, mana sufficient. The first violated
     * condition decides, and <b>none of them consumes anything</b> - a refused trigger costs no mana,
     * starts no cooldown and does not take the global lock (FR-024, FR-025).
     */
    public AbilityResult trigger(UUID characterId, String abilityId) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(abilityId, "abilityId");

        Ability ability = registry.config().require(abilityId);
        Instant now = clock.instant();

        if (registry.classOf(characterId).isEmpty()) {
            // Before the class selection there is no game state at all (ADR-020, ADR-021). Checked
            // first because everything below it would be answering about a character that is not
            // playing.
            return AbilityResult.NO_CHARACTER;
        }
        if (!ability.isActive()) {
            // A passive is not triggered by the player - but it does carry a marker in the hotbar, so
            // this IS a player-facing case. It always was: the rogue's Totem of Undying is a passive
            // with an item, and this used to answer NOT_UNLOCKED - telling somebody who owns the
            // ability that they would unlock it later.
            return isUnlocked(characterId, abilityId)
                    ? AbilityResult.PASSIVE
                    : AbilityResult.NOT_UNLOCKED;
        }
        if (!isUnlocked(characterId, abilityId)) {
            return AbilityResult.NOT_UNLOCKED;
        }
        // FR-040 and FR-045b: at most one going at a time, whichever phase it is in. Checked before
        // the global lock because "you are already doing something" is the more useful thing to say.
        RunningAbility current = running.get(characterId);
        if (current != null) {
            return current.isWindingUp()
                    ? AbilityResult.ALREADY_CASTING
                    : AbilityResult.ALREADY_SUSTAINING;
        }
        if (isGloballyLocked(characterId, now)) {
            return AbilityResult.GLOBAL_LOCK;
        }

        AbilityState state = registry.stateOf(characterId, abilityId);
        if (ability.charges() > 1) {
            // A charge ability has no cooldown until the pool is empty (FR-045i), so the pool is the
            // gate and the cooldown only applies once it ran dry.
            if (chargesAvailable(characterId, abilityId) <= 0
                    && state.runningCooldown(now).isEmpty()) {
                return AbilityResult.NO_CHARGES;
            }
            if (state.runningCooldown(now).isPresent()) {
                return AbilityResult.ON_COOLDOWN;
            }
        } else if (state.runningCooldown(now).isPresent()) {
            return AbilityResult.ON_COOLDOWN;
        }

        // FR-037. Settled here, immediately before the question is asked: "not enough mana" must
        // never be down to an outstanding settlement rather than to the player's actual mana.
        ResourceRegeneration accrual = regeneration;
        if (accrual != null) {
            accrual.settle(characterId);
        }
        // The holder, resolved once and used for everything below that is not this block's own state.
        // Absent means the character is not in play - the same answer as no class at all, and the
        // reason it is not checked earlier is that the cheaper gates above should still get to speak.
        UUID holderId = holderOf(characterId).orElse(null);
        if (holderId == null) {
            return AbilityResult.NO_CHARACTER;
        }
        ResourceView resources = stats.resources(holderId);
        if (resources.currentMana() < ability.manaCost()) {
            return AbilityResult.NOT_ENOUGH_MANA;
        }

        // Past every gate. From here on things are consumed, and in this order: the global lock takes
        // hold at the START of a trigger (FR-029), so an ability with a cast time cannot be used to
        // slip past it.
        registry.lockGlobally(characterId, now.plus(registry.config().globalCooldown()));
        if (ability.manaCost() > 0.0) {
            stats.changeMana(holderId, -ability.manaCost());
        }
        spendCharge(characterId, ability, now);

        if (ability.hasCastTime()) {
            // Winds up first. The effects follow when the cast completes, and until then an
            // interruption costs nothing (FR-039, FR-045d).
            beginWindUp(characterId, ability, now);
            return AbilityResult.CASTING;
        }
        return takeEffect(characterId, ability, now);
    }

    /**
     * Ends whatever this character has running (FR-045c to FR-045e).
     *
     * <p><b>The state decides what it costs, not the caller.</b> Winding up refunds and starts no
     * cooldown; already running keeps the cost and starts it. A caller that could choose would
     * eventually choose wrong, and the difference is exactly what stops "cancel immediately" from
     * being a free way to fish for a better moment.
     */
    public AbilityResult end(UUID characterId, EndCause cause) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(cause, "cause");

        RunningAbility running = this.running.remove(characterId);
        if (running == null) {
            return AbilityResult.TRIGGERED;
        }
        running.cancelTask();
        Ability ability = registry.config().require(running.abilityId());

        if (running.isWindingUp()) {
            // It never happened. Refund in full and leave the cooldown untouched (FR-045d).
            if (running.reservedMana() > 0.0) {
                // Only if they are still in play. A disconnect ends the wind-up too, and refunding
                // mana to a holder that is gone would throw where nothing is wrong.
                holderOf(characterId)
                        .ifPresent(holderId -> stats.changeMana(holderId, running.reservedMana()));
            }
            return AbilityResult.ENDED;
        }
        // It did happen, and stopping it early does not undo that (FR-045e).
        startCooldown(characterId, ability, clock.instant());
        return AbilityResult.ENDED;
    }

    /** What this character has going, or empty. At most one (FR-040, FR-045b). */
    public Optional<RunningAbility> running(UUID characterId) {
        return Optional.ofNullable(running.get(characterId));
    }

    /**
     * Completes a wind-up: the effects run now, and the cooldown starts here rather than at the
     * trigger (FR-030).
     *
     * <p>Called by the scheduled one-shot, on the tick.
     */
    public void completeWindUp(UUID characterId) {
        RunningAbility windingUp = running.get(characterId);
        if (windingUp == null || !windingUp.isWindingUp()) {
            // Interrupted between the schedule and now. Ordinary, not an error.
            return;
        }
        running.remove(characterId);
        Ability ability = registry.config().require(windingUp.abilityId());
        takeEffect(characterId, ability, clock.instant());
    }

    /** Ends a sustained ability whose duration ran out. Called by the scheduled one-shot. */
    public void expire(UUID characterId) {
        end(characterId, EndCause.EXPIRED);
    }

    /** Applies the effects and decides whether the ability keeps running afterwards. */
    private AbilityResult takeEffect(UUID characterId, Ability ability, Instant now) {
        apply(ability, characterId, registry.stateOf(characterId, ability.id()).rank());

        if (ability.sustained()) {
            Instant endsAt = now.plus(ability.duration());
            running.put(
                    characterId,
                    new RunningAbility(
                            characterId,
                            ability.id(),
                            RunningAbility.Phase.RUNNING,
                            now,
                            endsAt,
                            0.0,
                            scheduleEnd(characterId, ability.duration())));
            // No cooldown yet: it starts when the ability actually ends, however that happens.
            return AbilityResult.SUSTAINING;
        }

        startCooldown(characterId, ability, now);
        return AbilityResult.TRIGGERED;
    }

    private void beginWindUp(UUID characterId, Ability ability, Instant now) {
        Instant dueAt = now.plus(ability.castTime());
        running.put(
                characterId,
                new RunningAbility(
                        characterId,
                        ability.id(),
                        RunningAbility.Phase.WINDING_UP,
                        now,
                        dueAt,
                        ability.manaCost(),
                        scheduleCompletion(characterId, ability.castTime())));
    }

    /**
     * Takes a charge and, on the last one, lets the cooldown apply (FR-045i to FR-045k).
     *
     * <p>Timestamp arithmetic like everything else: the pool springs back when the window has passed
     * since the last use, so nothing has to notice that it did.
     */
    private void spendCharge(UUID characterId, Ability ability, Instant now) {
        if (ability.charges() <= 1) {
            return;
        }
        Key key = new Key(characterId, ability.id());
        Charges current = charges.get(key);
        int remaining =
                current == null || current.hasLapsed(now, ability.chargeWindow())
                        ? ability.charges()
                        : current.remaining();
        charges.put(key, new Charges(remaining - 1, now));
    }

    /** How many charges this character has left on this ability (FR-045i). */
    public int chargesAvailable(UUID characterId, String abilityId) {
        Ability ability = registry.config().require(abilityId);
        if (ability.charges() <= 1) {
            return registry.remainingCooldown(characterId, abilityId).isPresent() ? 0 : 1;
        }
        Charges current = charges.get(new Key(characterId, abilityId));
        if (current == null || current.hasLapsed(clock.instant(), ability.chargeWindow())) {
            return ability.charges();
        }
        return current.remaining();
    }

    /**
     * Sets the player's setting on a switchable passive (FR-052d).
     *
     * <p>Belongs to the character, not the account, and outlives the session - it is the one piece of
     * ability state a player edits directly, and having it reset on every logout would make the
     * setting useless.
     *
     * @throws IllegalArgumentException if the ability does not offer a setting. A silent no-op would
     *     leave a command reporting success while nothing changed
     */
    public void setToggle(UUID characterId, String abilityId, ToggleState state) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(state, "state");
        Ability ability = registry.config().require(abilityId);
        if (!ability.playerToggle()) {
            throw new IllegalArgumentException(abilityId + " has no player setting to change");
        }
        registry.put(registry.stateOf(characterId, abilityId).withToggle(state));
        repository.markDirty(characterId);
    }

    /**
     * Raises this character's rank on this ability by one (FR-062).
     *
     * <p><b>The rank belongs to the character, never to the account</b> (ADR-011). Two characters of
     * the same player advance separately, and the state key says so: it is a pair of character and
     * ability, and no player id appears in it anywhere.
     *
     * <p><b>A rank costs coins</b> since B08b exists (ADR-027). The check runs <em>last</em>, after
     * the unlock and the maximum rank, so an advance refused for any other reason leaves the balance
     * untouched (FR-052). What it costs lives in the ability configuration and is read by B08b -
     * this block still knows nothing about coins, and installs the check through {@link RankCost}.
     *
     * <p>The paragraph that used to stand here said nothing was charged because the project had no
     * currency, and that inventing one in this block would put an economy in the wrong place. That
     * was right: the price was never guessed, and when a currency arrived, this method grew by one
     * {@code if}.
     *
     * <p>Written through the buffer like every other change: the cache is authoritative for the
     * session and the write-behind cycle carries it to the database (Principle IV).
     */
    public RankResult advanceRank(UUID characterId, String abilityId) {
        Objects.requireNonNull(characterId, "characterId");
        Ability ability = registry.config().require(abilityId);
        if (!isUnlocked(characterId, abilityId)) {
            return RankResult.NOT_UNLOCKED;
        }
        AbilityState state = registry.stateOf(characterId, abilityId);
        if (state.rank() >= ability.maxRank()) {
            return RankResult.AT_MAXIMUM;
        }
        // LAST, after everything that is not about money (FR-052). A character at the maximum rank
        // must not be charged for an advance that was never going to happen.
        if (!rankCost.charge(characterId, ability)) {
            return RankResult.NOT_ENOUGH_COINS;
        }
        registry.put(state.withRank(state.rank() + 1));
        repository.markDirty(characterId);
        return RankResult.ADVANCED;
    }

    /**
     * Who takes the price of a rank, if anybody does.
     *
     * <p><b>A seam rather than a dependency.</b> This block owns what a rank <em>costs</em> only in
     * the sense that the number sits in its configuration file; what a coin <em>is</em> belongs to
     * B08b, and B08 pointing at it would be layer 1 depending on layer 1 in the wrong direction
     * (ADR-027). B08b installs the real one at wiring time.
     *
     * <p>The default charges nothing, which is exactly what happened before B08b existed.
     */
    @FunctionalInterface
    public interface RankCost {
        /**
         * @return whether the price was paid; false refuses the advance and must have taken nothing
         */
        boolean charge(java.util.UUID characterId, Ability ability);

        /** What this block did before there was a currency: nobody pays. */
        static RankCost free() {
            return (characterId, ability) -> true;
        }
    }

    /** Installs the price check. Called once at wiring time by B08b. */
    public void setRankCost(RankCost rankCost) {
        this.rankCost = java.util.Objects.requireNonNull(rankCost, "rankCost");
    }

    /** The player's setting, or {@link ToggleState#ON} when they never changed it. */
    public ToggleState toggleOf(UUID characterId, String abilityId) {
        return registry.toggleOf(characterId, abilityId);
    }

    /** Whether the character's level has reached this ability's unlock level (FR-061). */
    public boolean isUnlocked(UUID characterId, String abilityId) {
        return registry.unlockedFor(characterId).stream()
                .anyMatch(unlocked -> unlocked.id().equals(abilityId));
    }

    /** Whether the short lock after another ability is still running (FR-028). */
    public boolean isGloballyLocked(UUID characterId, Instant now) {
        return registry.remainingGlobalLock(characterId).isPresent();
    }

    /**
     * The cooldown this character would get on this ability, after their reduction.
     *
     * <p>Capped at {@link #MAX_COOLDOWN_REDUCTION} even if the attribute somehow exceeds it. The
     * attribute is already capped in {@code stats.yml}; enforcing it a second time here is cheap and
     * means a configuration mistake cannot produce a zero cooldown (FR-027, ADR-008).
     */
    public Duration effectiveCooldown(UUID characterId, Ability ability) {
        if (ability.cooldown().isZero()) {
            return Duration.ZERO;
        }
        double reduction =
                holderOf(characterId)
                        .map(
                                holderId ->
                                        Math.min(
                                                MAX_COOLDOWN_REDUCTION,
                                                Math.max(
                                                        0.0,
                                                        stats.value(
                                                                holderId,
                                                                Attribute.ABILITY_COOLDOWN))))
                        // Not in play: no reduction, full cooldown. The alternative - throwing - would
                        // turn a logout during a cast into an error.
                        .orElse(0.0);
        long millis = Math.round(ability.cooldown().toMillis() * (1.0 - reduction));
        return Duration.ofMillis(millis);
    }

    /**
     * Applies the effects with the values as of this moment.
     *
     * <p>The snapshot is drawn once here and handed to every effect (FR-018). Drawing it per effect
     * would let a buff expiring mid-ability change what the second half of it does.
     */
    private void apply(Ability ability, UUID characterId, int rank) {
        // All three of these speak holder: the snapshot is the holder's, the resolver looks up an
        // entity by it, and the effects address whatever they hit - which may be a mob, and a mob has
        // no character at all.
        UUID casterId = holderOf(characterId).orElse(null);
        if (casterId == null) {
            // Gone between the trigger and now - a wind-up outliving its player. Nothing to apply and
            // nothing wrong.
            return;
        }
        StatSnapshot snapshot = stats.snapshot(casterId);
        List<UUID> resolved = targets.resolve(casterId, ability.target());
        effects.run(ability, casterId, resolved, rank, snapshot);
    }

    /**
     * Starts the cooldown - at the moment the ability <b>takes effect</b> (FR-030).
     *
     * <p>Also the only write path: the state is marked and B02's write-behind buffer does the rest.
     * No game event reaches the database directly (FR-032).
     */
    private void startCooldown(UUID characterId, Ability ability, Instant now) {
        Duration cooldown = effectiveCooldown(characterId, ability);
        if (cooldown.isZero()) {
            return;
        }
        if (ability.charges() > 1 && chargesAvailable(characterId, ability.id()) > 0) {
            // Charges left, so no cooldown yet: it begins when the last one is spent (FR-045i).
            return;
        }
        registry.put(registry.stateOf(characterId, ability.id()).withCooldown(now.plus(cooldown)));
        repository.markDirty(characterId);
    }
}
