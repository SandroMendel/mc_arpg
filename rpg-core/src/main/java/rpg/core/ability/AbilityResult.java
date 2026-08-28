package rpg.core.ability;

import rpg.core.message.MessageKey;

/**
 * The outcome of trying to trigger an ability - a result, not a throw (FR-024, FR-025).
 *
 * <p>Being refused is an ordinary outcome: a player presses a key while the cooldown runs, and that
 * is not exceptional. An exception is reserved for what is actually a programming error - an unknown
 * ability id, a null character.
 *
 * <p>Each outcome carries the message key the player is told with. The sentence itself is chosen
 * where players are addressed, never here (Constitution V).
 */
public enum AbilityResult {

    /** It went off. Cost paid, cooldown running, effects applied. */
    TRIGGERED(null),

    /** It has a cast time and is now running. The effects follow when it completes. */
    CASTING(null),

    /** It is sustained and now running. A second right-click on the slot ends it (FR-045c). */
    SUSTAINING(null),

    /** A running sustained ability was ended by the player. */
    ENDED(null),

    ON_COOLDOWN(AbilityMessageKeys.ON_COOLDOWN),

    GLOBAL_LOCK(AbilityMessageKeys.GLOBAL_LOCK),

    NOT_ENOUGH_MANA(AbilityMessageKeys.NOT_ENOUGH_MANA),

    NOT_UNLOCKED(AbilityMessageKeys.NOT_UNLOCKED),

    /**
     * The ability is passive: the player has it, and it is not triggered by clicking.
     *
     * <p><b>Its own value rather than NOT_UNLOCKED</b>, which is what a passive marker used to answer.
     * That was reachable long before anybody noticed - the rogue's Totem of Undying is a passive with
     * an item - and it told a player who owns the ability that they would unlock it later.
     *
     * <p>Now that every passive carries a marker, saying "this works on its own" is the point of the
     * slot rather than an edge case.
     */
    PASSIVE(AbilityMessageKeys.PASSIVE),

    ALREADY_CASTING(AbilityMessageKeys.ALREADY_CASTING),

    ALREADY_SUSTAINING(AbilityMessageKeys.ALREADY_SUSTAINING),

    NO_CHARGES(AbilityMessageKeys.NO_CHARGES),

    /** No character is active - before the class selection there is no game state (ADR-020). */
    NO_CHARACTER(AbilityMessageKeys.NO_CHARACTER);

    private final MessageKey messageKey;

    AbilityResult(MessageKey messageKey) {
        this.messageKey = messageKey;
    }

    /** Whether the ability took hold in any form. */
    public boolean isSuccess() {
        return messageKey == null;
    }

    /**
     * What the player is told, or {@code null} on success.
     *
     * <p>A rejection always has one. That is the point of pairing them here rather than letting each
     * call site pick: the same refusal reads the same way wherever it comes from.
     */
    public MessageKey messageKey() {
        return messageKey;
    }
}
