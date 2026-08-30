package rpg.core.statistics;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.OptionalInt;
import java.util.UUID;

/**
 * Alles, was ein Profilfenster zeigt — <b>fertig geladen, bevor es gebaut wird.</b>
 *
 * <h2>Warum ein Schnappschuss und keine Fassade im Fenster</h2>
 *
 * <p>Die Werte kommen aus der Datenbank und damit als {@code CompletableFuture}. Ein Fenster, das
 * sie selbst abfragt, müsste entweder warten — im Tick, also gar nicht — oder sich nachträglich
 * füllen, während der Spieler schon hineinsieht. Beides ist schlechter als der dritte Weg: erst
 * alles laden, dann in einem Zug bauen und öffnen.
 *
 * <p>Damit ist auch klar, warum dieser Typ <em>keine</em> Methoden hat, die etwas nachladen. Was
 * hier fehlt, fehlt im Fenster; ein Nachladen wäre eine zweite Abfrage zu einem Zeitpunkt, an dem
 * niemand mehr damit rechnet.
 *
 * <h2>Die drei privaten Werte stehen nur in einem eigenen Schnappschuss</h2>
 *
 * <p>{@link #ownProfile()} sagt, welcher Fall vorliegt. Ein fremdes Profil trägt {@link #deathsByCause()}
 * und {@link #playtimeByZone()} <b>leer</b> und {@link #onlineSeconds()} als {@code -1} — nicht,
 * weil das hübsch wäre, sondern weil es dann keinen Weg gibt, sie versehentlich anzuzeigen: es ist
 * nichts da (FR-037).
 *
 * @param account wessen Zahlen
 * @param period welcher Zeitraum
 * @param values je öffentlicher Rangliste der eigene Wert
 * @param ranks je öffentlicher Rangliste die eigene Platzierung, sofern vorhanden
 * @param deathsByCause Tode je Verursacher — <b>nur im eigenen Profil</b> (FR-038)
 * @param playtimeByZone aktive Zeit je Zone — <b>nur im eigenen Profil</b>
 * @param onlineSeconds gesamte Onlinezeit — <b>nur im eigenen Profil</b>; sonst {@code -1}
 * @param ownProfile ob der Betrachter das Konto selbst ist
 */
public record ProfileSnapshot(
        UUID account,
        Period period,
        Map<Aggregation, Long> values,
        Map<Aggregation, Integer> ranks,
        Map<String, Long> deathsByCause,
        Map<String, Long> playtimeByZone,
        long onlineSeconds,
        boolean ownProfile) {

    public ProfileSnapshot {
        Objects.requireNonNull(account, "account");
        Objects.requireNonNull(period, "period");
        values = Map.copyOf(Objects.requireNonNull(values, "values"));
        ranks = Map.copyOf(Objects.requireNonNull(ranks, "ranks"));
        deathsByCause = Map.copyOf(Objects.requireNonNull(deathsByCause, "deathsByCause"));
        playtimeByZone = Map.copyOf(Objects.requireNonNull(playtimeByZone, "playtimeByZone"));

        if (!ownProfile && (!deathsByCause.isEmpty() || !playtimeByZone.isEmpty() || onlineSeconds >= 0)) {
            // Der Riegel sitzt im Konstruktor und nicht in der Ansicht: ein fremdes Profil, das
            // die privaten Werte TRAEGT, waere schon ein Verstoss - unabhaengig davon, ob eine
            // Ansicht sie zeigt. Ein Schloss an der Anzeige ist kein Schloss (FR-037).
            throw new IllegalArgumentException(
                    "ein fremdes Profil darf die drei privaten Werte gar nicht erst tragen (FR-037)");
        }
    }

    /** Der eigene Wert einer Rangliste; null heißt „noch nichts". */
    public long valueOf(Aggregation board) {
        return values.getOrDefault(board, 0L);
    }

    /** Die eigene Platzierung, sofern es eine gibt. */
    public OptionalInt rankOf(Aggregation board) {
        Integer rank = ranks.get(board);
        return rank == null ? OptionalInt.empty() : OptionalInt.of(rank);
    }

    /** Ein leeres fremdes Profil — der Fall „dieses Konto hat noch nichts getan". */
    public static ProfileSnapshot foreign(UUID account, Period period) {
        return new ProfileSnapshot(
                account, period, Map.of(), Map.of(), Map.of(), Map.of(), -1L, false);
    }

    /** Ein Bauhelfer für den eigenen Schnappschuss. */
    public static final class Builder {

        private final Map<Aggregation, Long> values = new LinkedHashMap<>();
        private final Map<Aggregation, Integer> ranks = new LinkedHashMap<>();
        private Map<String, Long> deathsByCause = Map.of();
        private Map<String, Long> playtimeByZone = Map.of();
        private long onlineSeconds = -1L;

        public Builder value(Aggregation board, long value) {
            values.put(board, value);
            return this;
        }

        public Builder rank(Aggregation board, int rank) {
            ranks.put(board, rank);
            return this;
        }

        public Builder deathsByCause(Map<String, Long> byCause) {
            this.deathsByCause = byCause;
            return this;
        }

        public Builder playtimeByZone(Map<String, Long> byZone) {
            this.playtimeByZone = byZone;
            return this;
        }

        public Builder onlineSeconds(long seconds) {
            this.onlineSeconds = seconds;
            return this;
        }

        public ProfileSnapshot own(UUID account, Period period) {
            return new ProfileSnapshot(
                    account, period, values, ranks, deathsByCause, playtimeByZone, onlineSeconds, true);
        }

        public ProfileSnapshot foreign(UUID account, Period period) {
            return new ProfileSnapshot(account, period, values, ranks, Map.of(), Map.of(), -1L, false);
        }
    }
}
