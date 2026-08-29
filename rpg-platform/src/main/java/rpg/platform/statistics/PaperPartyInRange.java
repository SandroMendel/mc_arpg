package rpg.platform.statistics;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.DoubleSupplier;
import java.util.function.IntSupplier;

import org.bukkit.Server;
import org.bukkit.entity.Entity;

import rpg.core.progression.PartyRegistry;
import rpg.core.progression.ProximityCheck;
import rpg.core.progression.WorldPoint;

/**
 * Wer beim Kill in Reichweite in der Gruppe stand (FR-007a, FR-007b).
 *
 * <h2>Dieselben Bauteile, dieselbe Zahl — kein zweiter Begriff von „dabei gewesen"</h2>
 *
 * <p>Diese Klasse rechnet nichts eigenes: sie benutzt <b>B06s {@link PartyRegistry}</b>, <b>B06s
 * {@link ProximityCheck}</b> und <b>B06s {@code party.range-blocks}</b>. Genau die drei, die auch
 * über Erfahrung und Coins entscheiden.
 *
 * <p>Der naheliegende Weg wäre eine eigene Reichweite in {@code statistics.yml} gewesen — „damit
 * der Betreiber sie getrennt einstellen kann". Das Ergebnis wäre ein Spiel, in dem ein Mitglied
 * Erfahrung bekommt, aber keinen Kill, oder umgekehrt. Niemand würde das für eine Einstellung
 * halten; alle würden es für einen Fehler halten, und sie hätten recht.
 *
 * <h2>Der Ort wird JETZT gelesen</h2>
 *
 * <p>B05 veröffentlicht den Tod, während Bukkits eigene Todesbehandlung noch läuft — die Entität
 * existiert also noch, und ihr Ort ist ablesbar. Einen Tick später wäre sie fort, und die
 * Reichweitenprüfung hätte keinen Bezugspunkt mehr. Ohne Ort fällt die Gruppe aus, und es zählen
 * nur die Beitragenden; das ist dieselbe Antwort, die B06 in dieser Lage gibt (FR-044 dort) — und
 * sie ist die vorsichtige: lieber jemandem einen Kill vorenthalten, den er knapp verdient hätte,
 * als ihn jemandem geben, der am anderen Ende der Welt stand.
 */
public final class PaperPartyInRange implements KillStatListener.PartyInRange {

    private final Server server;
    private final PartyRegistry parties;
    private final ProximityCheck proximity;
    private final DoubleSupplier range;
    private final IntSupplier partyMaxSize;

    public PaperPartyInRange(
            Server server,
            PartyRegistry parties,
            ProximityCheck proximity,
            DoubleSupplier range,
            IntSupplier partyMaxSize) {
        this.server = Objects.requireNonNull(server, "server");
        this.parties = Objects.requireNonNull(parties, "parties");
        this.proximity = Objects.requireNonNull(proximity, "proximity");
        this.range = Objects.requireNonNull(range, "range");
        this.partyMaxSize = Objects.requireNonNull(partyMaxSize, "partyMaxSize");
    }

    @Override
    public Set<UUID> around(UUID victimId, Collection<UUID> contributors) {
        if (contributors.isEmpty()) {
            return Set.of();
        }
        WorldPoint origin = locationOf(victimId);
        if (origin == null) {
            return Set.of();
        }

        int maxSize = partyMaxSize.getAsInt();
        UUID[] members = new UUID[maxSize];
        UUID[] inRange = new UUID[maxSize];
        Set<UUID> found = new LinkedHashSet<>();

        for (UUID contributor : contributors) {
            int memberCount = parties.membersOf(contributor, members);
            if (memberCount <= 1) {
                // Keine Gruppe, oder eine Gruppe aus einer Person - beides verhaelt sich gleich.
                continue;
            }
            int hits = proximity.inRange(origin, members, memberCount, range.getAsDouble(), inRange);
            for (int i = 0; i < hits; i++) {
                found.add(inRange[i]);
            }
        }
        return found;
    }

    private WorldPoint locationOf(UUID entityId) {
        Entity entity = server.getEntity(entityId);
        if (entity == null) {
            return null;
        }
        var location = entity.getLocation();
        var world = location.getWorld();
        if (world == null) {
            return null;
        }
        return new WorldPoint(world.getUID(), location.getX(), location.getY(), location.getZ());
    }
}
