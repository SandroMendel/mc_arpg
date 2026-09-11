package rpg.platform.drop;

import java.util.Objects;
import java.util.UUID;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

/**
 * Alles an einem liegenden Gegenstand, was nur ein echter Server wirklich kann.
 *
 * <p><b>Aus {@code CoinPile.PilePlatform} hierher gehoben, nicht kopiert</b> (research.md R5,
 * ADR-039). B08b hat diese Mechanik zuerst gebraucht, B11 braucht sie genauso — und eine zweite
 * Fassung wäre identisch geblieben, bis jemand eine davon anfasst. Dasselbe Vorgehen, mit dem
 * ADR-029 den {@code ShareCalculator} aus dem {@code XpDistributor} gezogen hat.
 *
 * <p><b>Warum das überhaupt eine Naht ist.</b> MockBukkit implementiert weder {@code Item.setOwner}
 * noch {@code Entity.setVisibleByDefault}, und es meldet einen nicht implementierten Aufruf als
 * <em>übersprungenen</em> Test statt als Fehler. Ohne diese Naht meldeten sich die Tests über
 * Sichtbarkeit und Anspruch stillschweigend als übersprungen, und der Build sagte trotzdem
 * SUCCESSFUL — der schlechteste verfügbare Ausgang: ein grüner Lauf, der nichts bewiesen hat.
 *
 * <p>Die plattformabhängigen Aufrufe sind deshalb benannt, und ein Test setzt einen Mitschreiber
 * ein. Was dieser Test dann beweist, ist <b>was wir verlangen und über wen</b>; dass Paper es
 * einhält, beweist der echte Server (quickstart.md Abschnitt 3).
 *
 * <p><b>Die beiden Hälften wiegen nicht gleich.</b> Sichtbarkeit ist eine Anforderung. Das
 * Eigentumskennzeichen ist <em>Härtung</em>: es sorgt dafür, dass fremde Clients es gar nicht erst
 * versuchen, was billig ist — aber es kennt <b>Spieler, keine Charaktere</b>, und ein Spieler hat
 * bis zu drei. Die Prüfung des Charakters bleibt deshalb unabhängig davon bestehen (ADR-011).
 * Darstellung ist niemals die Autorität (Constitution VI).
 */
public interface OwnedDropPlatform {

    /** Versteckt den Gegenstand vor allen. */
    void hideFromEveryone(Item drop);

    /** Zeigt ihn dem einen Spieler, der Anspruch darauf hat — falls er online ist. */
    void showTo(Item drop, Player player);

    /**
     * Die Vanilla-Schlösser und die Voralterung, die einen fehlenden Verfalls-Setter ersetzt.
     *
     * @param spawnTicksLived wie alt der Gegenstand beim Ablegen gestellt wird; es gibt keinen
     *     Setter für die Verfallszeit, also wird stattdessen vorgealtert
     */
    void harden(Item drop, UUID ownerId, int spawnTicksLived);

    /** Was ein echter Server tut. */
    static OwnedDropPlatform vanilla(Plugin plugin) {
        Objects.requireNonNull(plugin, "plugin");
        return new OwnedDropPlatform() {
            @Override
            public void hideFromEveryone(Item drop) {
                drop.setVisibleByDefault(false);
            }

            @Override
            public void showTo(Item drop, Player player) {
                player.showEntity(plugin, drop);
            }

            @Override
            public void harden(Item drop, UUID ownerId, int spawnTicksLived) {
                drop.setOwner(ownerId);
                drop.setCanMobPickup(false);
                drop.setWillAge(true);
                drop.setTicksLived(spawnTicksLived);
            }
        };
    }
}
