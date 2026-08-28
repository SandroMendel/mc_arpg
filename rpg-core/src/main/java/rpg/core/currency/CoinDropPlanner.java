package rpg.core.currency;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.UUID;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.progression.ShareCalculator;
import rpg.core.progression.WorldPoint;

/**
 * Turns one death into the piles it should leave behind (FR-019 to FR-026, FR-031).
 *
 * <p><b>Bukkit-free on purpose.</b> Everything about <em>who is entitled to what</em> is decided
 * here and testable without a server; the platform layer only turns each plan into an entity.
 *
 * <p><b>The entitlement rule is not reimplemented here.</b> It comes from
 * {@link ShareCalculator}, the same object B06 uses for experience (ADR-029). Two implementations
 * would have agreed only until somebody edited one, and the divergence would have hit two players of
 * the same party differently.
 *
 * <p><b>One pile per entitled character, never one per coin</b> (FR-026). A death with three
 * entitled characters produces three plans, not three hundred entities.
 */
public final class CoinDropPlanner {

    private final ShareCalculator shares;
    private final MobCoinProvider drops;
    private final CurrencyConfig config;
    private final CharacterLookup characters;

    public CoinDropPlanner(
            ShareCalculator shares,
            MobCoinProvider drops,
            CurrencyConfig config,
            CharacterLookup characters) {
        this.shares = Objects.requireNonNull(shares, "shares");
        this.drops = Objects.requireNonNull(drops, "drops");
        this.config = Objects.requireNonNull(config, "config");
        this.characters = Objects.requireNonNull(characters, "characters");
    }

    /**
     * What this death should drop.
     *
     * @param death the death from B05, whose shares are taken as given
     * @param mobTypeKey which kind of creature it was, for the configured amount
     * @param origin where it died, read by the listener while that was still valid
     * @return one plan per entitled character; empty when nothing is owed to anyone
     */
    public List<CoinDropPlan> planFor(
            CombatDeathEvent death, String mobTypeKey, WorldPoint origin) {
        Objects.requireNonNull(death, "death");
        if (death.playerVictim()) {
            // No coins for killing a player. PvP is not a core mechanic, and turning players into a
            // coin source would make it one.
            return List.of();
        }
        Map<UUID, Double> damageShares = death.shares().shares();
        if (damageShares.isEmpty()) {
            // Burned in lava, fell, shot by another mob. An ordinary case with nobody entitled, and
            // nothing falls (FR-031).
            return List.of();
        }
        if (origin == null) {
            // Without a place there is nowhere to drop. Better nothing than a pile at the world
            // origin, which is what a fallback coordinate would produce.
            return List.of();
        }

        long amount = amountFor(mobTypeKey);
        if (amount <= 0L) {
            // An explicit zero in the configuration - this creature drops nothing, deliberately.
            return List.of();
        }

        List<CoinDropPlan> plans = new ArrayList<>();
        shares.allocate(
                damageShares,
                amount,
                origin,
                (holderId, share) -> {
                    Optional<UUID> characterId = characters.characterOf(holderId);
                    if (characterId.isEmpty()) {
                        // Logged out in the moment of the kill, or never entered play. The share
                        // lapses rather than being redistributed - the same rule B06 applies to
                        // experience, so a party with an absent member is not worth more.
                        return;
                    }
                    plans.add(new CoinDropPlan(characterId.get(), holderId, share, origin));
                });
        return List.copyOf(plans);
    }

    /**
     * What this creature is worth.
     *
     * <p>An empty answer from the provider means "no entry of its own" and yields the default -
     * never zero (FR-023).
     */
    private long amountFor(String mobTypeKey) {
        OptionalLong own = drops.coinsFor(mobTypeKey);
        return own.isPresent() ? own.getAsLong() : config.defaultDrop();
    }
}
