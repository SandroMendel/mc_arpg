package rpg.core.statistics;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Wer gerade untätig ist — <b>aus einem Zeitstempel, nicht aus einer wiederkehrenden Aufgabe</b>
 * (FR-003, FR-014c).
 *
 * <h2>Warum kein Timer</h2>
 *
 * <p>Der naheliegende Bau wäre eine Aufgabe je Spieler, die im Sekundentakt prüft, ob jemand
 * untätig geworden ist. Bei fünfzig Spielern wären das fünfzig eingeplante Aufgaben, die
 * ununterbrochen laufen, um in aller Regel <em>nichts</em> festzustellen — und Prinzip II verbietet
 * genau das.
 *
 * <p>Stattdessen wird nur ein Zeitstempel fortgeschrieben, und die Frage „ist er untätig?" wird
 * <b>dann beantwortet, wenn sie gestellt wird</b>: beim Fortschreiben der Spielzeit, die ohnehin
 * auf einem vorhandenen Durchlauf mitreitet (R6). Der Zustand entsteht aus dem Vergleich zweier
 * Zeitpunkte und muss nirgends gepflegt werden.
 *
 * <h2>Was {@link #touch} kosten darf: nichts</h2>
 *
 * <p>Diese Methode hängt am fünften Handler auf {@code PlayerMoveEvent} — dem Ereignis, das bei
 * fünfzig Spielern tausende Male je Sekunde feuert. Sie ist deshalb eine Zuweisung in eine
 * vorbelegte Map und sonst nichts: <b>keine Allokation, keine Bedingung, kein Schreibvorgang,
 * keine Neuberechnung</b> (FR-014c2). Alles, was hier an Klugheit hinzukäme, würde tausendfach je
 * Sekunde bezahlt.
 *
 * <p>Der Zeitstempel lebt nur für die Sitzung (data-model.md §3). Nach einem Neustart ist niemand
 * untätig, weil niemand verbunden ist.
 */
public final class ActivityClock {

    private final Map<UUID, Instant> lastActivity = new ConcurrentHashMap<>();

    /**
     * Merkt sich, dass dieser Spieler gerade etwas getan hat.
     *
     * <p>Eine Zuweisung. Mehr passiert hier nicht, und mehr darf hier nie passieren.
     */
    public void touch(UUID playerId, Instant now) {
        lastActivity.put(playerId, now);
    }

    /**
     * Ob dieser Spieler seit {@code idleAfter} nichts mehr getan hat.
     *
     * <p>Ein unbekannter Spieler gilt als <b>nicht</b> untätig: er hat sich gerade angemeldet, und
     * die erste Antwort auf eine Frage, die noch nie gestellt wurde, soll nicht „schläft" lauten.
     */
    public boolean isIdle(UUID playerId, Instant now, Duration idleAfter) {
        Instant last = lastActivity.get(playerId);
        if (last == null) {
            return false;
        }
        return !Duration.between(last, now).minus(idleAfter).isNegative();
    }

    /** Wann dieser Spieler zuletzt etwas getan hat, sofern bekannt. */
    public Instant lastActivityOf(UUID playerId) {
        return lastActivity.get(playerId);
    }

    /**
     * Vergisst diesen Spieler.
     *
     * <p>Beim Sitzungsende gerufen — sonst wüchse die Map über die Laufzeit des Servers, und ein
     * Spieler, der vor Stunden gegangen ist, hätte immer noch einen Eintrag.
     */
    public void forget(UUID playerId) {
        lastActivity.remove(Objects.requireNonNull(playerId, "playerId"));
    }

    /** Wie viele Spieler gerade einen Zeitstempel tragen — für Tests und Diagnose. */
    public int tracked() {
        return lastActivity.size();
    }
}
