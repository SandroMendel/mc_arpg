package rpg.core.zone;

import java.util.UUID;

import rpg.core.ability.WorldCondition;

/**
 * B09's answer to B08's open-world question (FR-052, FR-052b).
 *
 * <p><b>The answer is yes everywhere, and it is a decision rather than an unfinished
 * implementation.</b> That difference is why this class exists at all.
 * {@code WorldCondition.everywhere()} is a stand-in in B08 - a value chosen because the block that
 * knew better did not exist yet. This one is chosen because the block that knows better has looked:
 * the release ships no instances. The bosses stand in their regions, every region is part of the
 * continent world (ADR-006), and there is nowhere a rogue can be that is not the open world. The
 * wilderness between the regions included - it is open world in the plainest sense.
 *
 * <p><b>What changes when instances arrive.</b> A zone gains a flag saying it is one, this class
 * takes a {@link ZonePresence} and reads the holder's region. Nothing else moves - not the ability,
 * not B08's interface, not the wiring. That is what ADR-006 bought by refusing to make a zone a
 * world: the answer can become interesting without the model changing.
 *
 * <p><b>Why a class that returns a constant is worth writing.</b> Leaving the stand-in in place would
 * have been the same behaviour and a different statement. B08's own Javadoc calls it "the default
 * until B09 installs the real one", so a reader finding it still there in a shipped B09 would have to
 * work out whether it was overlooked. This says: looked at, decided, and here is the line that will
 * change.
 */
public final class ZoneWorldCondition implements WorldCondition {

    /**
     * Whether this holder is somewhere an open-world-only ability may take hold.
     *
     * <p><b>The argument is the holder, despite what B08's parameter is called.</b>
     * {@code PassiveDispatcher} passes the id it fires on, which is the player; this block's
     * placement is keyed the same way. An implementation that took the old name literally and looked
     * a character id up in a holder-keyed map would find nothing, answer "not the open world", and
     * silently switch Second Life off everywhere - exactly the failure ADR-025 picked the permissive
     * default to avoid.
     */
    @Override
    public boolean isOpenWorld(UUID holderId) {
        return true;
    }
}
