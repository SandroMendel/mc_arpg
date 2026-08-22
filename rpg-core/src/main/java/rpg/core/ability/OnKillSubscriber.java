package rpg.core.ability;

import java.util.Objects;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.event.EventBus;

/**
 * The {@code ON_KILL} trigger, hung on the death event B05 publishes (FR-046).
 *
 * <p>The only trigger that is not an interceptor. Killing is not a stage of the damage pipeline - it
 * is what the pipeline concludes - and B05 announces it rather than letting anyone hook into the
 * moment. This is the mage's Arcane Gathering.
 *
 * <p><b>The killer, not the top contributor.</b> Loot goes to whoever did the most damage (FR-034),
 * but a passive that fires on a kill belongs to whoever landed it. Two different questions with two
 * different answers, and the event carries both.
 */
public final class OnKillSubscriber {

    private final PassiveDispatcher passives;

    public OnKillSubscriber(PassiveDispatcher passives) {
        this.passives = Objects.requireNonNull(passives, "passives");
    }

    /** Called at startup, not per event. */
    public void subscribeTo(EventBus eventBus) {
        Objects.requireNonNull(eventBus, "eventBus");
        eventBus.subscribe(CombatDeathEvent.class, this::onDeath);
    }

    private void onDeath(CombatDeathEvent event) {
        event.killer()
                .ifPresent(
                        killer ->
                                // No damage type and no trigger data: a kill is not a damage event,
                                // and an ability that wanted the amount would be hanging on the wrong
                                // trigger.
                                passives.fire(killer, AbilityTrigger.ON_KILL, null, null));
    }
}
