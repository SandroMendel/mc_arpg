package rpg.core.classes;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.ToIntFunction;

import rpg.core.event.EventBus;
import rpg.core.session.CharacterClass;

/**
 * Advancing one ladder by one step, and handing out the cost block unread.
 *
 * <p><b>One step, never a jump.</b> There is no method to set a tier to an arbitrary value: a jump
 * could not be told from a bug, and the only legitimate producer of a tier is this class.
 *
 * <p><b>No cost check.</b> B07 does not know what a tier costs (FR-021). By the time this is called
 * the caller - B11 - has collected whatever it decided. Checking here would have coupled this block to
 * one that does not exist yet (Workflow rule 5).
 *
 * <p><b>No recalculation call.</b> This class does not know holder ids; it publishes
 * {@link TierAdvancedEvent} and the module asks B04 to recalculate. That is why one advance costs
 * exactly one recalculation (SC-009) rather than one per attribute.
 */
public final class TierAdvance {

    private final ClassConfig config;
    private final Function<UUID, Optional<CharacterClass>> classOf;
    private final ToIntFunction<UUID> levelOf;
    private final Function<UUID, Optional<ClassProgress>> read;
    private final Consumer<ClassProgress> write;
    private final ClassProgressRepository repository;
    private final EventBus eventBus;

    /**
     * @param read the authoritative in-memory state during a session (Constitution IV)
     * @param write where the new state goes - the same cache, never the database directly
     * @param repository only to mark the aggregate dirty; the write-behind buffer decides when it
     *     reaches the database (Constitution II)
     */
    public TierAdvance(
            ClassConfig config,
            Function<UUID, Optional<CharacterClass>> classOf,
            ToIntFunction<UUID> levelOf,
            Function<UUID, Optional<ClassProgress>> read,
            Consumer<ClassProgress> write,
            ClassProgressRepository repository,
            EventBus eventBus) {
        this.config = Objects.requireNonNull(config, "config");
        this.classOf = Objects.requireNonNull(classOf, "classOf");
        this.levelOf = Objects.requireNonNull(levelOf, "levelOf");
        this.read = Objects.requireNonNull(read, "read");
        this.write = Objects.requireNonNull(write, "write");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
    }

    public TierAdvanceResult advanceArmor(UUID characterId) {
        return advance(characterId, LadderSlot.ARMOR);
    }

    public TierAdvanceResult advanceWeapon(UUID characterId) {
        return advance(characterId, LadderSlot.WEAPON);
    }

    /**
     * Moves one ladder up by one.
     *
     * <p>Order of the checks matters for the message an operator sees: an unknown character first,
     * then the top of the ladder, then the level. Asking about the level of a ladder that has no next
     * tier would name a requirement that does not exist.
     */
    public TierAdvanceResult advance(UUID characterId, LadderSlot slot) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(slot, "slot");

        Optional<CharacterClass> characterClass = classOf.apply(characterId);
        if (characterClass.isEmpty()) {
            return TierAdvanceResult.rejected(TierAdvanceRejection.UNKNOWN_CHARACTER);
        }
        EquipmentLadder ladder = config.definition(characterClass.get()).ladder(slot);
        ClassProgress current =
                read.apply(characterId).orElseGet(() -> ClassProgress.initial(characterId));
        int from = current.tierOf(slot);
        if (ladder.isTop(from)) {
            return TierAdvanceResult.rejected(TierAdvanceRejection.ALREADY_AT_TOP);
        }
        int to = from + 1;
        if (levelOf.applyAsInt(characterId) < ladder.tier(to).requiredLevel()) {
            return TierAdvanceResult.rejected(TierAdvanceRejection.BELOW_REQUIRED_LEVEL);
        }

        write.accept(current.advanced(slot));
        // Marked, not written: the write-behind buffer from B02 decides when it reaches the database.
        repository.markDirty(characterId);
        eventBus.publish(new TierAdvancedEvent(characterId, slot, from, to));
        return TierAdvanceResult.advanced(to);
    }

    /**
     * The cost block of a target tier, exactly as configured and <b>not interpreted</b> (FR-021).
     *
     * @return an empty map for a tier that has no cost, never {@code null}
     * @throws IllegalArgumentException if the class or the tier does not exist - asking for the cost of
     *     something that cannot be reached is a caller mistake, not a rejection
     */
    public Map<String, Object> costOf(CharacterClass id, LadderSlot slot, int targetTier) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slot, "slot");
        return config.definition(id).ladder(slot).tier(targetTier).cost();
    }

    /** The level a target tier requires, for a caller that wants to say so before trying. */
    public int requiredLevelFor(CharacterClass id, LadderSlot slot, int targetTier) {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(slot, "slot");
        return config.definition(id).ladder(slot).tier(targetTier).requiredLevel();
    }
}
