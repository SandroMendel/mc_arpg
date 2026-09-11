package rpg.core.mob;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Der geprüfte Inhalt von {@code mobs.yml} (FR-001).
 *
 * <p><b>Kein Schalter für die Unterdrückung.</b> Das ist eine Entscheidung vom 2026-08-24 und keine
 * Auslassung: ein Schalter wäre ein Weg, das Budget zu umgehen, und die ganze Leistungszusage dieses
 * Blocks hängt daran, dass nichts anderes Kreaturen erzeugt. Einmal auf {@code false} gestellt und
 * vergessen, läuft der Server voll und niemand sieht die Ursache.
 *
 * @param budget die vier harten Grenzen
 * @param respawnInterval wie schnell sich eine geräumte Horde wieder füllt
 * @param densityPerPlayer Dichtezuwachs je zusätzlichem Spieler, als Anteil. Berührt nie Attribute
 * @param cleanupAfter wie lange nach dem letzten Spieler in der Zone
 * @param cleanupRadius wer weiter weg ist als das, wird entfernt — nicht schlafen gelegt
 * @param retargetInterval Abstand, in dem eine eigene Zielzuweisung frühestens wieder anfasst
 * @param kinds die Arten, nach Kennung
 * @param hordes was in welcher Zone steht, nach Zonenschlüssel
 */
public record MobConfig(
        Budget budget,
        Duration respawnInterval,
        double densityPerPlayer,
        Duration cleanupAfter,
        double cleanupRadius,
        Duration retargetInterval,
        Map<String, MobKind> kinds,
        Map<String, HordeSpec> hordes) {

    public MobConfig {
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(respawnInterval, "respawnInterval");
        Objects.requireNonNull(cleanupAfter, "cleanupAfter");
        Objects.requireNonNull(retargetInterval, "retargetInterval");
        kinds = Map.copyOf(Objects.requireNonNull(kinds, "kinds"));
        hordes = Map.copyOf(Objects.requireNonNull(hordes, "hordes"));
        requirePositive(respawnInterval, "horde.respawn-interval-ms");
        requirePositive(cleanupAfter, "horde.cleanup-after-seconds");
        requirePositive(retargetInterval, "horde.retarget-interval-ms");
        if (!Double.isFinite(densityPerPlayer) || densityPerPlayer < 0.0) {
            throw new IllegalArgumentException(
                    "horde.density-per-player must be zero or more, but was " + densityPerPlayer);
        }
        if (!Double.isFinite(cleanupRadius) || cleanupRadius <= 0.0) {
            throw new IllegalArgumentException(
                    "horde.cleanup-radius must be positive, but was " + cleanupRadius);
        }
    }

    private static void requirePositive(Duration value, String where) {
        if (value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(
                    where + " must be positive, but was " + value.toMillis() + "ms");
        }
    }

    /** Die Art mit dieser Kennung, oder leer. Wirft nie — fünf Blöcke hängen an dieser Antwort. */
    public Optional<MobKind> kind(String kindKey) {
        return kindKey == null ? Optional.empty() : Optional.ofNullable(kinds.get(kindKey));
    }

    /** Was in dieser Zone steht, oder leer. Eine unbekannte Zone ist ein normaler Zustand. */
    public Optional<HordeSpec> horde(String zoneKey) {
        return zoneKey == null ? Optional.empty() : Optional.ofNullable(hordes.get(zoneKey));
    }

    /** Alle Artschlüssel — die Startprüfung der Anzeigenamen braucht sie. */
    public List<String> kindKeys() {
        return List.copyOf(kinds.keySet());
    }
}
