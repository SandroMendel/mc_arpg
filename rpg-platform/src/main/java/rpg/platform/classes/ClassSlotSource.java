package rpg.platform.classes;

import java.util.List;

import rpg.core.classes.ClassSlot;
import rpg.core.session.PlayerSession;

/**
 * Where the menu's contents come from.
 *
 * <p>A seam rather than a direct call, because a slot needs three blocks to describe it: the character
 * is B03's, the level is B06's and the tiers are B07's. The listener may know none of them - it lives
 * in {@code rpg-platform} and the modules live in {@code rpg-persistence}, which may not see each other
 * (Constitution III.2). The plugin introduces them.
 *
 * <p>Called on the tick, every time the menu is built - which includes every reopen. Implementations
 * answer from what the login already read; a query here would be one per escape keypress.
 */
public interface ClassSlotSource {

    /** One entry per class, played or free, in no particular order - the menu sorts them. */
    List<ClassSlot> slotsFor(PlayerSession session);
}
