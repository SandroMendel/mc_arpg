package rpg.core.statistics;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Was B12 nach außen zur <b>Saison</b> anbietet (contracts/stats-api.md §4).
 *
 * <h2>Der Zwischenstand ist sichtbar (FR-050e)</h2>
 *
 * <p>Nicht erst am Saisonende. Eine Wertung, deren Stand man erst erfährt, wenn sie vorbei ist,
 * ist kein Wettbewerb, sondern eine Bekanntgabe — und niemand richtet sein Spielen danach aus, was
 * er nicht sehen kann. Deshalb liefert {@link #scoreOf} die laufende Punktzahl samt
 * Aufschlüsselung, und zwar aus dem Speicherstand: sie kostet beim Öffnen nichts.
 *
 * <h2>Und die Einlösung hat vier Ausgänge</h2>
 *
 * <p>Siehe {@link ClaimOutcome}. Drei davon heißen für den Spieler zunächst „ich habe nichts
 * bekommen" und bedeuten Verschiedenes; sie zusammenzufassen nähme ihm die einzige Information,
 * die ihm weiterhilft.
 */
public interface Seasons {

    /** Die laufende Saison, falls eine läuft. */
    Optional<SeasonCalendar.Season> current(Instant now);

    /**
     * Der Zwischenstand der Gesamtwertung, aufgeschlüsselt nach Rangliste (FR-050e, FR-050f).
     *
     * <p>Leer, solange keine Saison läuft oder der Speicherstand noch nicht gefüllt ist — nicht
     * eine Punktzahl von null. Der Unterschied ist derselbe wie bei einer Rangliste vor der ersten
     * Auffrischung: „noch nicht da" ist keine Aussage über die Leistung des Spielers.
     */
    Optional<SeasonScore> scoreOf(UUID playerId);

    /**
     * Offene Ansprüche eines Kontos. Verfallen nie (FR-053a).
     *
     * <p>Asynchron, weil sie aus der Datenbank kommen — sie liegen in keinem Cache. Ein Anspruch
     * ist selten und wichtig; ihn aus einem Speicherstand zu beantworten hieße, einen Zustand zu
     * spiegeln, der sich genau einmal ändert und dann für immer gilt.
     */
    CompletableFuture<List<RewardClaim>> openClaims(UUID playerId);

    /**
     * Löst einen Anspruch ein.
     *
     * <p><b>Markiert zuerst, schreibt dann gut</b> — ein Absturz dazwischen verliert höchstens,
     * verdoppelt nie (FR-053). Die einzige Ausnahme von dieser Reihenfolge ist das volle Inventar:
     * das wird <em>vorher</em> geprüft, weil es vorhersehbar ist und der Anspruch sonst verbraucht
     * wäre, ohne dass etwas ankommt (FR-055).
     *
     * @param playerId das Konto, dem der Anspruch gehört
     * @param characterId die Figur, die ihn einlöst — sie bekommt die Coins und die Gegenstände
     */
    CompletableFuture<ClaimOutcome> claim(UUID playerId, UUID characterId, String seasonKey);
}
