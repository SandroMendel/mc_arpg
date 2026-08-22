package rpg.core.ability;

import rpg.core.message.MessageKey;

/**
 * Every string a player might see because of this block (Constitution V).
 *
 * <p>Keys only. B08 never formats a message and never decides wording - the display name of an
 * ability is a key in {@code abilities.yml}, and the text behind it lives in the message file.
 */
public final class AbilityMessageKeys {

    /** A trigger was refused. One key per {@code AbilityResult} that is not a success. */
    public static final MessageKey ON_COOLDOWN = MessageKey.of("ability.rejected.on-cooldown");

    public static final MessageKey GLOBAL_LOCK = MessageKey.of("ability.rejected.global-lock");

    public static final MessageKey NOT_ENOUGH_MANA =
            MessageKey.of("ability.rejected.not-enough-mana");

    public static final MessageKey NOT_UNLOCKED = MessageKey.of("ability.rejected.not-unlocked");

    public static final MessageKey ALREADY_CASTING =
            MessageKey.of("ability.rejected.already-casting");

    public static final MessageKey ALREADY_SUSTAINING =
            MessageKey.of("ability.rejected.already-sustaining");

    public static final MessageKey NO_CHARGES = MessageKey.of("ability.rejected.no-charges");

    public static final MessageKey NO_CHARACTER = MessageKey.of("ability.rejected.no-character");

    /** Unlocking, ranking up and the player toggle. */
    public static final MessageKey UNLOCKED = MessageKey.of("ability.unlocked");

    public static final MessageKey RANK_ADVANCED = MessageKey.of("ability.rank.advanced");

    public static final MessageKey RANK_AT_MAXIMUM = MessageKey.of("ability.rank.at-maximum");

    public static final MessageKey TOGGLE_CHANGED = MessageKey.of("ability.toggle.changed");

    /** Second Life: no respawn, a title and a sound instead (FR-052c). */
    public static final MessageKey SECOND_LIFE_TITLE = MessageKey.of("ability.second-life.title");

    public static final MessageKey SECOND_LIFE_SUBTITLE =
            MessageKey.of("ability.second-life.subtitle");

    /**
     * Every key this block can say, for the test that checks the shipped file carries them all.
     *
     * <p>Listed by hand rather than read by reflection: a key that somebody adds and forgets to list
     * here is caught by the start validator anyway, and a reflective list would silently follow a
     * rename that the message file did not.
     */
    public static java.util.List<MessageKey> all() {
        return java.util.List.of(
                ON_COOLDOWN,
                GLOBAL_LOCK,
                NOT_ENOUGH_MANA,
                NOT_UNLOCKED,
                ALREADY_CASTING,
                ALREADY_SUSTAINING,
                NO_CHARGES,
                NO_CHARACTER,
                UNLOCKED,
                RANK_ADVANCED,
                RANK_AT_MAXIMUM,
                TOGGLE_CHANGED,
                SECOND_LIFE_TITLE,
                SECOND_LIFE_SUBTITLE);
    }

    private AbilityMessageKeys() {}
}
