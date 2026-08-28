package rpg.core.mob;

import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Die Drosselung einer eigenen Zielzuweisung - je Kreatur, zeitstempelbasiert lazy und ohne
 * laufende Aufgabe (FR-035, Prinzip II).
 *
 * <p><b>Nicht Vanillas eigene Zielsuche.</b> Die ist ueber {@code Attribute.FOLLOW_RANGE} begrenzt
 * (FR-036, FR-037) und braucht keine eigene Drosselung - Vanilla sucht ohnehin nicht in jedem Tick
 * neu und nie ausserhalb der gesetzten Reichweite. Diese Klasse gilt fuer die EINE eigene
 * Zielzuweisung, die dieser Block kennt: den Klon aus US7, der eine Kreatur auf sich umlenkt
 * (research.md R6, R9).
 *
 * <p><b>Zwei Zeitstempel, kein Scheduler.</b> Dieselbe Bauart wie {@link BossState}: die Frage "darf
 * ich jetzt" ist eine Rechnung aus dem letzten Zeitpunkt und dem konfigurierten Abstand, nicht eine
 * wiederkehrende Aufgabe je Kreatur - bei Hunderten Kreaturen waere das genau die Kostenexplosion,
 * die dieser ganze Block vermeidet.
 *
 * <p><b>Fragt nur, wer bereits einen Kandidaten hat</b> (FR-037 sinngemaess auf die eigene Zuweisung
 * angewendet). Ohne Kandidat ruft der Aufrufer {@link #mayRetarget} erst gar nicht auf - eine
 * Kreatur ohne Klon in Reichweite kostet diese Klasse nichts, und ohne einen Aufruf von
 * {@link #retargeted} bleibt der Zustand unveraendert, egal wie oft nur gefragt wird.
 */
public final class RetargetThrottle {

    private Instant lastRetargetAt;

    /**
     * Ob jetzt neu zugewiesen werden darf.
     *
     * <p>Ja, wenn noch nie zugewiesen wurde - eine frische Kreatur soll nicht erst den Abstand
     * abwarten. Sonst erst, wenn der konfigurierte Abstand seit der letzten Zuweisung vergangen ist.
     */
    public boolean mayRetarget(Instant now, Duration interval) {
        Objects.requireNonNull(now, "now");
        Objects.requireNonNull(interval, "interval");
        if (lastRetargetAt == null) {
            return true;
        }
        return !now.isBefore(lastRetargetAt.plus(interval));
    }

    /** Merkt sich, dass gerade zugewiesen wurde - der Abstand zaehlt ab jetzt neu. */
    public void retargeted(Instant now) {
        this.lastRetargetAt = Objects.requireNonNull(now, "now");
    }

    /** Wann zuletzt zugewiesen wurde, oder leer. Fuer Tests. */
    public java.util.Optional<Instant> lastRetargetAt() {
        return java.util.Optional.ofNullable(lastRetargetAt);
    }
}
