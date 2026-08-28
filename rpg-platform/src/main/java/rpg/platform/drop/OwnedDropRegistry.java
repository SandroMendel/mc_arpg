package rpg.platform.drop;

import java.util.Iterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.entity.Item;
import org.bukkit.entity.Player;

/**
 * Wem ein liegender Gegenstand gehört — und wie er nach einem Relogin wieder sichtbar wird.
 *
 * <p><b>Die Falle, für die es diese Klasse gibt</b>, steht so im {@code package-info} von
 * {@code rpg.platform.currency}, wo sie zuerst auffiel: {@code showEntity} ist Zustand der
 * <em>Verbindung</em>, nicht der Entität. Ein Gegenstand ist {@code setVisibleByDefault(false)},
 * also ist er nach einer Neuanmeldung wieder unsichtbar — während das Vanilla-Eigentumskennzeichen
 * und die Charakterprüfung beide weiter passen. <b>Unsichtbar aber aufsammelbar ist das Schlechteste
 * von beidem.</b>
 *
 * <p>Deshalb wird jeder abgelegte Gegenstand hier vermerkt, und {@link #showTo} holt die Sichtbarkeit
 * nach, sobald ein Charakter ins Spiel kommt.
 *
 * <p><b>Was hier NICHT passiert: Aufräumen.</b> Vanillas Verfall holt weg, was niemand geholt hat.
 * Diese Klasse plant nichts und hält keine wiederkehrende Aufgabe (Constitution II); sie vergisst
 * Einträge nur nebenbei, wenn sie ohnehin über die Tabelle läuft. Ein eigener Sweep wäre genau die
 * Art Aufgabe, die B08b sich seinerzeit ausdrücklich verkniffen hat.
 *
 * <p><b>Der Anspruch hängt am Charakter, nicht am Spieler</b> (ADR-011). Vanillas {@code setOwner}
 * kennt nur Spieler, und ein Spieler hat bis zu drei Charaktere und kann mitten in der Sitzung
 * wechseln. Ohne diese Tabelle sammelte Charakter B ein, was Charakter A verdient hat.
 */
public final class OwnedDropRegistry {

    private record Entry(Item drop, UUID characterId) {}

    private final Map<UUID, Entry> drops = new ConcurrentHashMap<>();
    private final OwnedDropPlatform platform;

    public OwnedDropRegistry(OwnedDropPlatform platform) {
        this.platform = Objects.requireNonNull(platform, "platform");
    }

    /** Vermerkt einen Gegenstand, der jetzt existiert. */
    public void register(Item drop, UUID characterId) {
        Objects.requireNonNull(drop, "drop");
        Objects.requireNonNull(characterId, "characterId");
        drops.put(drop.getUniqueId(), new Entry(drop, characterId));
    }

    /** Vergisst ihn — nach dem Aufheben, oder wenn er entfernt wurde. */
    public void forget(Item drop) {
        if (drop != null) {
            drops.remove(drop.getUniqueId());
        }
    }

    /**
     * Wem dieser Gegenstand gehört.
     *
     * @return leer, wenn er nicht aus diesem Mechanismus stammt — was auf fast jeden Gegenstand
     *     zutrifft, den ein Spieler berührt
     */
    public Optional<UUID> ownerOf(UUID entityId) {
        Entry entry = entityId == null ? null : drops.get(entityId);
        return entry == null ? Optional.empty() : Optional.of(entry.characterId());
    }

    /**
     * Zeigt diesem Spieler wieder, was seinem Charakter gehört.
     *
     * <p>Aufgerufen, wenn ein Charakter ins Spiel kommt — und das ist auch der einzige Moment, in
     * dem es nötig sein kann: nichts anderes nimmt eine gezeigte Entität für den Rest einer Sitzung
     * wieder weg.
     *
     * <p>Für Gegenstände anderer Charaktere geschieht stillschweigend nichts — auch für die anderen
     * Charaktere desselben Spielers. Ein Gegenstand gehört dem Charakter, der ihn verdient hat.
     */
    public void showTo(Player player, UUID characterId) {
        Objects.requireNonNull(player, "player");
        Objects.requireNonNull(characterId, "characterId");
        forgetWhatIsGone();
        for (Entry entry : drops.values()) {
            if (entry.characterId().equals(characterId) && entry.drop().isValid()) {
                platform.showTo(entry.drop(), player);
            }
        }
    }

    /** Wie viele Gegenstände gerade vermerkt sind — für Tests und Auswertung. */
    public int size() {
        forgetWhatIsGone();
        return drops.size();
    }

    /**
     * Wirft Einträge weg, deren Entität es nicht mehr gibt.
     *
     * <p>Nebenbei, nicht als eigener Durchlauf: aufgerufen wird das nur, wenn ohnehin jemand über
     * die Tabelle läuft. Ein Eintrag zu einem verfallenen Gegenstand ist bis dahin harmlos — er
     * zeigt auf eine ungültige Entität, und jede Abfrage prüft das.
     */
    private void forgetWhatIsGone() {
        Iterator<Map.Entry<UUID, Entry>> iterator = drops.entrySet().iterator();
        while (iterator.hasNext()) {
            if (!iterator.next().getValue().drop().isValid()) {
                iterator.remove();
            }
        }
    }
}
