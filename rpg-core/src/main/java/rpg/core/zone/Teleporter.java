package rpg.core.zone;

import java.util.UUID;

import rpg.core.scheduler.WorldPosition;

/**
 * Moves a holder to a place (FR-037c).
 *
 * <p>The domain layer hands out places and never moves anybody itself - moving is a Paper call, and
 * {@code rpg-core} may not make one (Constitution III.1). This interface is the one seam between the
 * two, and it is what lets the travel sequence be tested without a server.
 *
 * <p><b>The return value carries weight.</b> A teleport can fail: another plugin cancels the event,
 * the target world is not loaded, the player left between the click and the call. Travel has already
 * taken coins by the time this is called, so a silent {@code void} here would turn a failure into a
 * loss (FR-050f, research.md R1). Whoever calls this has to look at the answer.
 */
@FunctionalInterface
public interface Teleporter {

    /**
     * Moves this holder.
     *
     * @return {@code true} when they are now there, {@code false} when they are not - for any reason
     */
    boolean teleport(UUID holderId, WorldPosition destination);
}
