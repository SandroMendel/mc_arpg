package rpg.platform.mob;

import java.util.UUID;

import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import rpg.core.zone.ZonePresence;
import rpg.core.zone.Zones;
import rpg.platform.zone.BukkitPositions;

/**
 * Beantwortet {@link ZonePresence#zoneKeyOf} ueber die live gelesene Bukkit-Position statt ueber
 * eine gestempelte Zuordnung wie das echte {@code ZoneTracker} - fuer die B10-Plattformtests reicht
 * das, weil sie ohnehin per {@code player.teleport(...)} bewegen und danach sofort pruefen.
 */
final class FakeZonePresence implements ZonePresence {

    private final ServerMock server;
    private final Zones zones;

    FakeZonePresence(ServerMock server, Zones zones) {
        this.server = server;
        this.zones = zones;
    }

    @Override
    public boolean inSafeCore(UUID holderId) {
        throw new UnsupportedOperationException("dieser Test braucht das nicht");
    }

    @Override
    public String zoneKeyOf(UUID holderId) {
        PlayerMock player = (PlayerMock) server.getPlayer(holderId);
        if (player == null) {
            return null;
        }
        return zones.zoneKeyAt(BukkitPositions.of(player.getLocation()));
    }
}
