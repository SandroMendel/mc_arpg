package rpg.plugin.command.framework;

import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Sperrzeit je Absender und Kommando (T021, FR-032).
 *
 * <h2>Lazy, nicht getaktet</h2>
 *
 * <p>Gerechnet wird beim Aufruf, aus einem Zeitstempel — wie die Cooldowns es seit B08 tun. Es
 * läuft <b>kein</b> wiederkehrender Task, der Einträge aufräumt, und es gibt keine Sammlung über
 * alle Spieler (Constitution II.2). Ein Aufräumtask wäre die teuerste Art, ein paar Kilobyte zu
 * sparen.
 *
 * <h2>Wogegen das schützt</h2>
 *
 * <p>Nicht gegen Boshaftigkeit, sondern gegen die gehaltene Eingabetaste. Eine Rangliste fragt
 * echte Zeilen ab; zwanzig Aufrufe in zwei Sekunden sind zwanzig Abfragen, und die laufen, während
 * der Server Spieler bedienen soll.
 *
 * <h2>Die Konsole ist ausgenommen</h2>
 *
 * <p>Und zwar nicht aus Höflichkeit: ein Betreiber, der ein Skript über die Konsole laufen lässt,
 * ist der Fall, für den es eine Konsole gibt. Wer dort Schaden anrichtet, hat ohnehin jedes Recht.
 * Die Grenze schützt vor der <em>Menge</em> gewöhnlicher Spieler, nicht vor dem einen Betreiber.
 */
public final class RateLimits {

    /** Wann ein Absender ein Kommando zuletzt benutzt hat. */
    private final Map<Key, Long> lastUsedMillis = new ConcurrentHashMap<>();

    private final Clock clock;

    public RateLimits(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    /**
     * Prüft und vermerkt in einem Zug.
     *
     * <p><b>Ein Zug und nicht zwei</b>, weil zwei Methoden — „darf er?" und „merk es dir" — eine
     * Lücke dazwischen hätten und einen Aufrufer, der die zweite vergisst. Ein vergessenes Vermerken
     * fällt nie auf: die Sperre wirkt einfach nicht.
     *
     * @return leer, wenn es durchgeht; sonst die Restzeit
     */
    public Optional<Duration> check(CommandSender sender, RpgCommand command) {
        Duration window = command.rateLimit();
        if (window == null || window.isZero() || window.isNegative()) {
            return Optional.empty();
        }
        if (!(sender instanceof Player player)) {
            return Optional.empty();
        }

        Key key = new Key(player.getUniqueId(), command.name());
        long now = clock.millis();
        long windowMillis = window.toMillis();

        // computeIfPresent/putIfAbsent statt get-dann-put: zwei Kommandos desselben Spielers im
        // selben Tick sind selten, aber nichts hier darf davon abhaengen.
        long[] remaining = {0L};
        lastUsedMillis.compute(
                key,
                (unusedKey, previous) -> {
                    if (previous != null && now - previous < windowMillis) {
                        remaining[0] = windowMillis - (now - previous);
                        return previous;
                    }
                    return now;
                });

        return remaining[0] > 0 ? Optional.of(Duration.ofMillis(remaining[0])) : Optional.empty();
    }

    /**
     * Vergisst, was ein Spieler benutzt hat.
     *
     * <p>Gehört ans Sitzungsende. Ohne das wüchse die Karte über die Laufzeit des Servers um jeden
     * Spieler, der je ein gesperrtes Kommando benutzt hat — langsam, aber unbegrenzt.
     */
    public void forget(UUID playerId) {
        lastUsedMillis.keySet().removeIf(key -> key.senderId().equals(playerId));
    }

    /** Nur für Tests: wie viele Absender gerade vermerkt sind. */
    int trackedSenders() {
        return (int) lastUsedMillis.keySet().stream().map(Key::senderId).distinct().count();
    }

    private record Key(UUID senderId, String command) {}
}
