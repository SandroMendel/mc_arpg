package rpg.core.mob;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ob der Boss einer Region lebt, und wann der letzte gefallen ist.
 *
 * <p><b>Der Timer ist keine Aufgabe</b> (FR-032). Ob der Boss wieder darf, ist eine Frage an zwei
 * Zeitstempel — dieselbe Bauart, die B08 für Cooldowns und B04 für Regeneration benutzt. Eine
 * laufende Aufgabe je Region wäre wiederkehrende Arbeit für ein Ereignis, das alle dreißig Minuten
 * einmal eintritt.
 *
 * <p><b>Aufgeräumt ist nicht gefallen</b> (FR-034). Wird der Boss entfernt, weil niemand mehr in der
 * Region steht, ist das kein Tod: {@code lastKilledAt} bleibt, wie es war, der Timer läuft davon
 * unberührt weiter, und er steht wieder da, sobald jemand zurückkommt. Ohne diese Unterscheidung
 * könnte man den Timer umgehen, indem man die Region kurz verlässt.
 */
public final class BossState {

    private final String zoneKey;
    private UUID aliveEntityId;
    private Instant lastKilledAt;

    public BossState(String zoneKey) {
        this.zoneKey = Objects.requireNonNull(zoneKey, "zoneKey");
    }

    public String zoneKey() {
        return zoneKey;
    }

    /** Der lebende Boss, oder leer. */
    public Optional<UUID> alive() {
        return Optional.ofNullable(aliveEntityId);
    }

    /** Merkt sich den gesetzten Boss. */
    public void placed(UUID entityId) {
        this.aliveEntityId = Objects.requireNonNull(entityId, "entityId");
    }

    /** Er ist gefallen: der Timer beginnt. */
    public void killed(Instant when) {
        this.aliveEntityId = null;
        this.lastKilledAt = Objects.requireNonNull(when, "when");
    }

    /**
     * Er wurde aufgeräumt: er ist weg, aber er ist nicht gefallen.
     *
     * <p>Der Unterschied zu {@link #killed(Instant)} ist der ganze Punkt dieser Klasse.
     */
    public void cleanedUp() {
        this.aliveEntityId = null;
    }

    /**
     * Ob jetzt einer erscheinen darf.
     *
     * <p>Nein, solange einer lebt (FR-029). Ja, wenn noch nie einer gefallen ist — ein frischer
     * Server soll seinen Boss nicht erst nach dreißig Minuten zeigen.
     */
    public boolean mayAppear(Instant now, Duration respawn) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(respawn, "respawn");
        if (aliveEntityId != null) {
            return false;
        }
        if (lastKilledAt == null) {
            return true;
        }
        return !now.isBefore(lastKilledAt.plus(respawn));
    }

    /** Wann er zuletzt gefallen ist, oder leer. Für eine Anzeige und für Tests. */
    public Optional<Instant> lastKilledAt() {
        return Optional.ofNullable(lastKilledAt);
    }
}
