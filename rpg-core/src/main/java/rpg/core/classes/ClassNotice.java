package rpg.core.classes;

import java.util.UUID;

import rpg.core.message.MessageKey;

/**
 * Telling one player one thing - the seam B13 will take over.
 *
 * <p>Deliberately <b>not</b> called {@code HudRenderer}. Constitution III names that interface for the
 * HUD, and the HUD belongs to B13: bossbars, scoreboards, titles, the lot. This is one message to one
 * player, which is all B07 needs, and claiming the bigger name would force B13 to reconcile two
 * abstractions instead of widening one.
 *
 * <p>Takes a {@link MessageKey}, never text (Constitution V). Whether it arrives as a title, an action
 * bar or a chat line is the implementation's business - and B13's decision later.
 */
public interface ClassNotice {

    /**
     * Shows the message behind {@code key} to {@code playerId}, if that player is reachable.
     *
     * <p>Never throws for an absent player: a notice is not important enough to interrupt anything
     * (Constitution VI).
     */
    void show(UUID playerId, MessageKey key);
}
