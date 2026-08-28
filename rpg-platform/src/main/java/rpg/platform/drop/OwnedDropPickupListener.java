package rpg.platform.drop;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerAttemptPickupItemEvent;

/**
 * <b>Das Schloss</b> — und zwar das, das unabhängig vom Sehen gilt.
 *
 * <p>Unsichtbarkeit ist Darstellung. Sie sorgt dafür, dass ein Spieler gar nicht erst versucht, was
 * ihm nicht gehört, und sie ist deshalb die <em>ehrliche</em> Anzeige: ein sichtbarer Gegenstand,
 * den man nicht aufheben kann, sieht aus wie ein Fehler. Aber <b>Darstellung ist niemals die
 * Autorität</b> (Constitution VI), und diese Klasse ist der Grund, aus dem das kein Widerspruch ist.
 *
 * <p><b>Zwei Schlösser, nicht eins.</b> Vanillas {@code setOwner} hält andere Spieler und Kreaturen
 * billig fern, sodass die meisten Versuche hier nie ankommen. Was es <em>nicht</em> kann, ist
 * Charaktere auseinanderhalten: ein Spieler hat bis zu drei und kann mitten in der Sitzung wechseln
 * (ADR-011). Vanilla filtert grob; hier wird genau geprüft.
 *
 * <p><b>Ein fremder Gegenstand wird nicht entfernt, sondern liegen gelassen.</b> Er gehört weiter
 * dem Charakter, der ihn verdient hat, und verfällt mit seiner eigenen Frist. Ihn hier wegzunehmen
 * hieße, ihn seinem rechtmäßigen Eigentümer zu nehmen.
 */
public final class OwnedDropPickupListener implements Listener {

    private final OwnedDropRegistry registry;
    private final Function<UUID, Optional<UUID>> activeCharacterOf;

    /**
     * @param activeCharacterOf welcher Charakter eines Spielers gerade im Spiel ist — B03 besitzt
     *     die Antwort, und sie kommt als Funktion herein, damit dieses Paket B03 nicht importiert
     */
    public OwnedDropPickupListener(
            OwnedDropRegistry registry, Function<UUID, Optional<UUID>> activeCharacterOf) {
        this.registry = Objects.requireNonNull(registry, "registry");
        this.activeCharacterOf = Objects.requireNonNull(activeCharacterOf, "activeCharacterOf");
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPickup(PlayerAttemptPickupItemEvent event) {
        Item drop = event.getItem();
        Optional<UUID> entitled = registry.ownerOf(drop.getUniqueId());
        if (entitled.isEmpty()) {
            // Keiner von unseren - was auf fast jeden Gegenstand zutrifft, den ein Spieler
            // beruehrt. Nichts tun ist hier die richtige Antwort und der haeufigste Pfad.
            return;
        }

        Player player = event.getPlayer();
        Optional<UUID> active = activeCharacterOf.apply(player.getUniqueId());
        if (active.isPresent() && active.get().equals(entitled.get())) {
            // Der Berechtigte. Aufheben erlaubt, und der Vermerk faellt weg - der Gegenstand ist
            // ab jetzt ein Inventarposten wie jeder andere.
            registry.forget(drop);
            return;
        }

        // Ein anderer Charakter desselben Spielers, ein fremder Spieler, oder gar kein Charakter.
        // Der Gegenstand bleibt liegen.
        event.setCancelled(true);
    }
}
