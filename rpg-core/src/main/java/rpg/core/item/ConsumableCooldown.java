package rpg.core.item;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wann ein Charakter eine Vorlage zuletzt benutzt hat (FR-035).
 *
 * <p><b>Zwei Zeitstempel, keine laufende Aufgabe</b> (Prinzip II). Eine Abklingzeit ist die
 * Musterfrage für zeitstempelbasierte Auswertung: sie wird nur dann gestellt, wenn jemand etwas
 * benutzen will, und dazwischen kostet sie nichts. Ein Timer je Charakter und Vorlage wären bei 150
 * Spielern und einer Handvoll Tränke schnell tausend Aufgaben für nichts.
 *
 * <p><b>Je Charakter und Vorlage</b>, nicht je Spieler: ein Spieler hat bis zu drei Charaktere, und
 * die Abklingzeit des einen geht den anderen nichts an (ADR-011).
 *
 * <p><b>Aufgeräumt wird beim Charakterwechsel und beim Logout</b>, nicht periodisch — dieselbe
 * Stelle, an der auch alles andere fällt, was zu einer Sitzung gehört.
 */
public final class ConsumableCooldown {

    private record Key(UUID characterId, String templateKey) {}

    private final Map<Key, Instant> lastUsed = new ConcurrentHashMap<>();
    private final Clock clock;

    public ConsumableCooldown(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Ob die Abklingzeit abgelaufen ist.
     *
     * @param cooldown die Abklingzeit der Vorlage; {@code null} heißt: keine
     */
    public boolean isReady(UUID characterId, String templateKey, Duration cooldown) {
        if (cooldown == null || cooldown.isZero()) {
            return true;
        }
        Instant last = lastUsed.get(new Key(characterId, templateKey));
        return last == null || !clock.instant().isBefore(last.plus(cooldown));
    }

    /**
     * Wie lange es noch dauert.
     *
     * <p>Für die Meldung an den Spieler: „noch drei Sekunden" ist eine Antwort, „geht nicht" ist
     * keine (FR-037).
     */
    public Duration remaining(UUID characterId, String templateKey, Duration cooldown) {
        if (cooldown == null || cooldown.isZero()) {
            return Duration.ZERO;
        }
        Instant last = lastUsed.get(new Key(characterId, templateKey));
        if (last == null) {
            return Duration.ZERO;
        }
        Duration left = Duration.between(clock.instant(), last.plus(cooldown));
        return left.isNegative() ? Duration.ZERO : left;
    }

    /** Vermerkt eine Benutzung. Nur aufrufen, wenn wirklich verbraucht wurde. */
    public void used(UUID characterId, String templateKey) {
        lastUsed.put(
                new Key(
                        Objects.requireNonNull(characterId, "characterId"),
                        Objects.requireNonNull(templateKey, "templateKey")),
                clock.instant());
    }

    /** Vergisst alles zu einem Charakter — Logout, Charakterwechsel, Löschung. */
    public void forget(UUID characterId) {
        lastUsed.keySet().removeIf(key -> key.characterId().equals(characterId));
    }

    /** Wie viele Einträge gerade stehen — für Tests und die Lecksuche. */
    public int size() {
        return lastUsed.size();
    }
}
