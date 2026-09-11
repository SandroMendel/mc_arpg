package rpg.core.statistics;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Zwei Uhren, offene Abschnitte, und die Teilung an Tagesgrenze und Zonenwechsel
 * ([data-model.md] §3).
 *
 * <h2>Zwei Uhren, weil eine Zahl zwei Fragen nicht beantworten kann (ADR-043)</h2>
 *
 * <ul>
 *   <li><b>Onlinezeit</b> — wie lange jemand verbunden war. Läuft immer.
 *   <li><b>Aktive Zeit</b> — wie lange er gespielt hat. Steht, sobald er untätig ist (FR-014b).
 * </ul>
 *
 * <p>Sie sind nicht ineinander umrechenbar, und deshalb ist die zweite kein Luxus: ohne sie
 * gewönne die Spielzeit-Rangliste, wer sein Konto nachts angemeldet lässt. Die Onlinezeit ist
 * dafür <b>privat</b> — sie sagt aus, wann jemand am Rechner sitzt, und das gehört ihm.
 *
 * <h2>Der Kunstgriff: jeder Abschnitt ist ganz aktiv oder ganz untätig</h2>
 *
 * <p>Ein offener Abschnitt wird nicht nur beim Zonenwechsel und um Mitternacht geschlossen,
 * sondern auch dann, wenn jemand untätig wird oder wieder anfängt. Dadurch trägt jeder Abschnitt
 * ein einziges {@code active}-Kennzeichen, und zwei Zusagen gelten <b>von selbst</b> statt durch
 * Nachrechnen:
 *
 * <ul>
 *   <li>Die aktive Zeit ist nie größer als die Onlinezeit (FR-014a) — sie ist eine Teilmenge
 *       derselben Abschnitte.
 *   <li>Die Summe der Zonenzeiten eines Tages ist auf die Sekunde die aktive Gesamtzeit (FR-014d,
 *       SC-016) — jeder aktive Abschnitt trägt genau eine Zone.
 * </ul>
 *
 * <p>Die Alternative wäre gewesen, aktive Zeit und Zonenzeit getrennt zu führen und am Ende zu
 * hoffen, dass sie zusammenpassen. Sie hätten es nicht getan, und die Abweichung wäre erst
 * jemandem aufgefallen, der zwei Zahlen im eigenen Profil vergleicht.
 *
 * <h2>Außerhalb jeder Zone</h2>
 *
 * <p>Wer in der Wildnis steht, sammelt seine Zeit unter {@link MetricRegistry#ZONE_WILDERNESS}
 * (FR-014e). Ohne diesen festen Ersatzschlüssel stimmte die Summe für jeden nicht, der die Zonen
 * je verlässt — und das tut jeder.
 */
public final class Playtime {

    /**
     * Ein geschlossener Zeitabschnitt, fertig zum Verbuchen.
     *
     * @param day der Kalendertag in UTC, zu dem er zählt
     * @param zoneKey die Zone oder {@link MetricRegistry#ZONE_WILDERNESS}
     * @param seconds die Dauer
     * @param active ob er auf die aktive Uhr geht; auf die Onlineuhr geht er immer
     */
    public record Slice(LocalDate day, String zoneKey, long seconds, boolean active) {}

    /** Der laufende Abschnitt eines Spielers. */
    private record Open(Instant since, String zoneKey, boolean active) {}

    private final Map<UUID, Open> open = new ConcurrentHashMap<>();

    /** Beginnt die Zeitrechnung für einen Spieler — beim Anmelden. */
    public void begin(UUID playerId, Instant now, String zoneKey) {
        open.put(playerId, new Open(now, zoneOrWilderness(zoneKey), true));
    }

    /** Ob für diesen Spieler gerade gerechnet wird. */
    public boolean isTracking(UUID playerId) {
        return open.containsKey(playerId);
    }

    /**
     * Schließt den laufenden Abschnitt und öffnet einen neuen in derselben Zone und demselben
     * Zustand.
     *
     * <p>Der übliche Fall: das Fortschreiben reitet auf einem vorhandenen Durchlauf mit (R6) und
     * bucht dabei, was seit dem letzten Mal aufgelaufen ist.
     */
    public List<Slice> accrue(UUID playerId, Instant now) {
        Open current = open.get(playerId);
        if (current == null) {
            return List.of();
        }
        open.put(playerId, new Open(now, current.zoneKey(), current.active()));
        return slice(current.since(), now, current.zoneKey(), current.active());
    }

    /**
     * Der Spieler hat die Zone gewechselt (FR-014f).
     *
     * <p>Ausgelöst vom vorhandenen Zonenwechsel-Ereignis aus B09 — <b>nicht</b> von einem eigenen
     * periodischen Nachsehen, in welcher Zone jemand steht.
     */
    public List<Slice> zoneChanged(UUID playerId, Instant now, String newZoneKey) {
        Open current = open.get(playerId);
        if (current == null) {
            return List.of();
        }
        open.put(playerId, new Open(now, zoneOrWilderness(newZoneKey), current.active()));
        return slice(current.since(), now, current.zoneKey(), current.active());
    }

    /**
     * Der Spieler ist untätig geworden oder hat wieder angefangen.
     *
     * <p>Ändert sich nichts, wird auch nichts geschlossen: sonst zerfiele der Tag in tausend
     * Ein-Sekunden-Abschnitte, ohne dass sich an der Summe etwas änderte.
     */
    public List<Slice> activityChanged(UUID playerId, Instant now, boolean nowActive) {
        Open current = open.get(playerId);
        if (current == null || current.active() == nowActive) {
            return List.of();
        }
        open.put(playerId, new Open(now, current.zoneKey(), nowActive));
        return slice(current.since(), now, current.zoneKey(), current.active());
    }

    /**
     * Schließt den letzten Abschnitt endgültig — beim Sitzungsende.
     *
     * <p>Angebunden an den vorhandenen {@code onSessionEnded}-Rückruf und nicht an einen eigenen
     * Austrittspfad: B11 hat für genau diese zweite Tür eine architektonische Zusicherung
     * eingeführt, weil zwei Ausstiege bedeuten, dass einer von beiden irgendwann vergessen wird.
     */
    public List<Slice> end(UUID playerId, Instant now) {
        Open current = open.remove(playerId);
        if (current == null) {
            return List.of();
        }
        return slice(current.since(), now, current.zoneKey(), current.active());
    }

    /**
     * Teilt einen Abschnitt an den UTC-Tagesgrenzen auf (FR-015, SC-013).
     *
     * <p>23:40 bis 00:30 sind zwanzig Minuten am einen und dreißig am anderen Tag — nicht fünfzig
     * an einem von beiden. Ohne die Teilung wanderte die Zeit vor Mitternacht auf den Folgetag
     * oder umgekehrt, je nachdem, wann der Abschnitt zufällig geschlossen wird, und keine
     * Tagesrangliste stimmte je wieder.
     */
    public static List<Slice> slice(Instant from, Instant to, String zoneKey, boolean active) {
        Objects.requireNonNull(from, "from");
        Objects.requireNonNull(to, "to");
        if (!to.isAfter(from)) {
            return List.of();
        }

        List<Slice> slices = new ArrayList<>();
        Instant cursor = from;
        while (cursor.isBefore(to)) {
            LocalDate day = LocalDate.ofInstant(cursor, ZoneOffset.UTC);
            Instant nextMidnight = day.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
            Instant sliceEnd = nextMidnight.isBefore(to) ? nextMidnight : to;

            long seconds = Duration.between(cursor, sliceEnd).toSeconds();
            if (seconds > 0) {
                slices.add(new Slice(day, zoneOrWilderness(zoneKey), seconds, active));
            }
            cursor = sliceEnd;
        }
        return List.copyOf(slices);
    }

    private static String zoneOrWilderness(String zoneKey) {
        return zoneKey == null || zoneKey.isBlank() ? MetricRegistry.ZONE_WILDERNESS : zoneKey;
    }
}
