package rpg.core.zone;

import java.util.ArrayList;
import java.util.List;

import rpg.core.message.MessageKey;

/**
 * Every string a player might see because of this block (Constitution V, FR-061).
 *
 * <p>Keys only. Nothing here formats a message or decides wording.
 *
 * <p><b>Zone names are keys too</b>, and that is the point of {@link #nameOf(String)}: a region's
 * visible name lives in the message file under {@code zone.<key>.name} and nowhere else (FR-003a).
 * Renaming a region is one line there, and no crystal, spawn area or later block has to be touched
 * for it (SC-017). {@link #all(List)} therefore takes the configured zone keys - the startup
 * validator can then prove every region actually has a name behind it, and a zone whose name would
 * show up in game as a key string refuses the start instead (FR-003c).
 */
public final class ZoneMessageKeys {

    private ZoneMessageKeys() {}

    /** Entering a region below its level band (FR-021). Warns; never blocks. */
    public static final MessageKey TOO_DANGEROUS = MessageKey.of("zone.too-dangerous");

    /** An ordinary death (FR-035). */
    public static final MessageKey DIED = MessageKey.of("zone.died");

    /** First login after a combat logout (ADR-030, FR-041). */
    public static final MessageKey DIED_LOGOUT = MessageKey.of("zone.died-logout");

    /** The waypoint window. Temporary here; belongs to B13 (ADR-032). */
    public static final MessageKey MENU_TITLE = MessageKey.of("zone.waypoint.menu-title");

    public static final MessageKey WAYPOINT_UNLOCKED = MessageKey.of("zone.waypoint.unlocked");

    public static final MessageKey WAYPOINT_LOCKED = MessageKey.of("zone.waypoint.locked");

    public static final MessageKey NOT_ENOUGH_COINS =
            MessageKey.of("zone.waypoint.not-enough-coins");

    public static final MessageKey IN_COMBAT = MessageKey.of("zone.waypoint.in-combat");

    public static final MessageKey WAYPOINT_GONE = MessageKey.of("zone.waypoint.gone");

    public static final MessageKey TRAVELLED = MessageKey.of("zone.waypoint.travelled");

    /**
     * The journey failed after the debit went through and the coins came back (FR-050f).
     *
     * <p>Its own key, not a variant of {@link #TRAVELLED}: the player did not travel, and the
     * message has to say so, or the ledger's two entries would look like a mistake.
     */
    public static final MessageKey REFUNDED = MessageKey.of("zone.waypoint.refunded");

    public static final MessageKey ENTRY_UNLOCKED = MessageKey.of("zone.waypoint.entry-unlocked");

    public static final MessageKey ENTRY_LOCKED = MessageKey.of("zone.waypoint.entry-locked");

    public static final MessageKey ENTRY_HERE = MessageKey.of("zone.waypoint.entry-here");

    /** The visible name of a region (FR-003a). */
    public static MessageKey nameOf(String zoneKey) {
        return MessageKey.of("zone." + zoneKey + ".name");
    }

    /** Every key this block uses, including one name per configured zone. */
    public static List<MessageKey> all(List<String> zoneKeys) {
        List<MessageKey> keys =
                new ArrayList<>(
                        List.of(
                                TOO_DANGEROUS,
                                DIED,
                                DIED_LOGOUT,
                                MENU_TITLE,
                                WAYPOINT_UNLOCKED,
                                WAYPOINT_LOCKED,
                                NOT_ENOUGH_COINS,
                                IN_COMBAT,
                                WAYPOINT_GONE,
                                TRAVELLED,
                                REFUNDED,
                                ENTRY_UNLOCKED,
                                ENTRY_LOCKED,
                                ENTRY_HERE));
        for (String zoneKey : zoneKeys) {
            keys.add(nameOf(zoneKey));
        }
        return List.copyOf(keys);
    }
}
