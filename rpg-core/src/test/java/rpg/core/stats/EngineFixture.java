package rpg.core.stats;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.session.SessionRegistry;

/** Shared setup for the engine tests: a held-back scheduler, a real event bus, no session rule. */
final class EngineFixture {

    final ControlledScheduler scheduler = new ControlledScheduler();
    final EventBus eventBus;
    final DefaultStatEngine engine;

    final List<StatsRecalculatedEvent> recalculations = new ArrayList<>();
    final List<ResourceChangedEvent> resourceChanges = new ArrayList<>();

    EngineFixture() {
        this(StatConfig.defaults(), null);
    }

    EngineFixture(StatConfig config, SessionRegistry sessions) {
        Logger logger = Logger.getLogger(EngineFixture.class.getName());
        this.eventBus = new DefaultEventBus(logger);
        this.engine = new DefaultStatEngine(config, scheduler, eventBus, sessions, logger);
        eventBus.subscribe(StatsRecalculatedEvent.class, recalculations::add);
        eventBus.subscribe(ResourceChangedEvent.class, resourceChanges::add);
    }

    /**
     * A holder for a player character, calculated once and filled, as the load path leaves it.
     *
     * <p>The restore is not decoration: production always follows {@code createForCharacter} with it,
     * because the zero pool a holder is created with is a placeholder - the maxima only exist after the
     * first calculation. A fixture that stopped before the restore left every test running against a
     * character at zero health, a state no live server ever has, and hid what that state means to the
     * vanilla mirror: {@code setHealth(0)} kills the player.
     */
    UUID character() {
        UUID playerId = UUID.randomUUID();
        engine.createForCharacter(playerId, UUID.randomUUID(), new ResourcePool(0.0, 0.0));
        StatSnapshot first = engine.recalculateNow(playerId);
        engine.restoreResources(
                playerId,
                ResourcePool.full(first.get(Attribute.HEALTH), first.get(Attribute.MANA)));
        clearRecorded();
        return playerId;
    }

    void clearRecorded() {
        recalculations.clear();
        resourceChanges.clear();
        scheduler.reset();
    }

    /** The test's equivalent of the next tick. */
    void tick() {
        scheduler.runPending();
    }

    static ModifierSet equipment(String slot, StatModifier... modifiers) {
        return ModifierSet.of(SourceId.of(SourceKind.EQUIPMENT, slot), modifiers);
    }

    static ModifierSet buff(String name, StatModifier... modifiers) {
        return ModifierSet.of(SourceId.of(SourceKind.BUFF, name), modifiers);
    }
}
