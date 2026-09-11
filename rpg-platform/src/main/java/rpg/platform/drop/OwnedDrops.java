package rpg.platform.drop;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.bukkit.Location;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Legt einen Gegenstand ab, der <b>genau einem Charakter</b> gehört.
 *
 * <p>Die Fassade über {@link OwnedDropPlatform} und {@link OwnedDropRegistry}: sie setzt den
 * Gegenstand, versteckt ihn vor allen, zeigt ihn dem Berechtigten, härtet ihn mit Vanillas Schlössern
 * und vermerkt ihn, damit die Sichtbarkeit einen Relogin überlebt.
 *
 * <p><b>Aufgeräumt wird nicht.</b> Vanillas Verfall holt weg, was niemand geholt hat — dieser
 * Mechanismus plant nichts (Constitution II).
 *
 * <p><b>Und Unsichtbarkeit ist nicht das Schloss.</b> Sie ist die ehrliche Darstellung: ein
 * sichtbarer Gegenstand, den man nicht aufheben kann, sieht aus wie ein Fehler. Das eigentliche
 * Schloss ist {@link OwnedDropPickupListener}, und es prüft den <b>Charakter</b> — Vanillas
 * Eigentumskennzeichen kennt nur Spieler (ADR-011, Constitution VI).
 */
public final class OwnedDrops {

    private final OwnedDropPlatform platform;
    private final OwnedDropRegistry registry;
    private final int spawnTicksLived;

    /**
     * @param spawnTicksLived wie alt ein Gegenstand beim Ablegen gestellt wird; es gibt keinen
     *     Setter für die Verfallszeit, also wird vorgealtert
     */
    public OwnedDrops(OwnedDropPlatform platform, OwnedDropRegistry registry, int spawnTicksLived) {
        this.platform = Objects.requireNonNull(platform, "platform");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.spawnTicksLived = spawnTicksLived;
    }

    /**
     * Legt {@code stack} an {@code where} ab, für {@code ownerCharacterId}.
     *
     * <p>Die Reihenfolge ist nicht beliebig: <b>erst verstecken, dann zeigen</b>. Andersherum wäre
     * der Gegenstand für einen Wimpernschlag für alle sichtbar, und auf einem vollen Server ist ein
     * Wimpernschlag ein Tick, in dem jemand hinsieht.
     *
     * @param owner der Spieler, dem der Charakter gerade gehört; {@code null}, wenn er offline ist —
     *     dann bleibt der Gegenstand unsichtbar und verfällt, was ein gewöhnlicher Ausgang ist
     * @return der abgelegte Gegenstand, oder leer, wenn die Welt ihn nicht angenommen hat
     */
    public Optional<Item> drop(
            ItemStack stack, Location where, UUID ownerCharacterId, Player owner) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(where, "where");
        Objects.requireNonNull(ownerCharacterId, "ownerCharacterId");

        if (where.getWorld() == null) {
            return Optional.empty();
        }
        Item drop = where.getWorld().dropItem(where, stack);

        // Erst unsichtbar fuer alle, dann sichtbar fuer den einen. Das ist eine Anforderung, keine
        // Feinheit - siehe Klassenkommentar.
        platform.hideFromEveryone(drop);
        if (owner != null && owner.isOnline()) {
            platform.showTo(drop, owner);
        }

        // Haertung obendrauf: Vanillas Eigentumskennzeichen sorgt dafuer, dass fremde Clients es
        // gar nicht erst versuchen. Es kennt nur Spieler - die Charakterpruefung bleibt davon
        // unberuehrt bestehen.
        platform.harden(drop, owner == null ? ownerCharacterId : owner.getUniqueId(), spawnTicksLived);

        registry.register(drop, ownerCharacterId);
        return Optional.of(drop);
    }

    /** Nach Relogin oder Charakterwechsel wieder sichtbar machen. */
    public void restoreVisibility(Player player, UUID characterId) {
        registry.showTo(player, characterId);
    }

    /** Wem ein liegender Gegenstand gehört. Leer für alles, was nicht von hier stammt. */
    public Optional<UUID> ownerOf(UUID entityId) {
        return registry.ownerOf(entityId);
    }

    /** Der Vermerk, damit ein Aufrufer nach dem Aufheben aufräumen kann. */
    public OwnedDropRegistry registry() {
        return registry;
    }
}
