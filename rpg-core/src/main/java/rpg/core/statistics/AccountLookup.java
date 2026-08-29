package rpg.core.statistics;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

import rpg.core.stats.StatEngine;

/**
 * Vom Charakter zum Konto — <b>eine dünne Hülle um {@link StatEngine#holderOf(UUID)}</b> (R3).
 *
 * <h2>Warum das keine eigene Zuordnung ist</h2>
 *
 * <p>B12 zählt auf das <b>Konto</b>, nicht auf die Figur (FR-005): wer drei Charaktere spielt, hat
 * eine Kill-Zahl. Die Ereignisse, aus denen gezählt wird, kommen aber charakterbezogen an. Also
 * braucht dieser Block eine Übersetzung — und genau an dieser Stelle entsteht sonst eine zweite
 * Wahrheit.
 *
 * <p>Die Beziehung gehört B04, und dort steht sie an einer Stelle, weil sie einen Rückwärtsindex
 * hat und sich bei jedem Charakterwechsel pflegt. Eine eigene Map hier wäre schneller
 * hingeschrieben und ginge beim ersten Charakterwechsel auseinander, den B12 nicht mitbekommt —
 * ohne Fehler, ohne Ausnahme, nur mit Zahlen, die auf dem falschen Konto landen.
 *
 * <p><b>Diese Klasse ordnet deshalb nichts selbst zu.</b> Sie fragt, und sie macht aus einem
 * fehlenden Ergebnis kein geratenes.
 *
 * <h2>Die Falle, die sie einrahmt</h2>
 *
 * <p>{@code holderOf} nimmt eine <em>Charakter</em>kennung und gibt eine <em>Halter</em>kennung
 * zurück. Beide sind {@code UUID}. Wer sie vertauscht, bekommt keinen Übersetzungsfehler, sondern
 * ein leeres {@code Optional} — das aussieht wie „nicht im Spiel". B08 hat das einmal getan, und
 * das Ergebnis war ein Server, auf dem keine Fähigkeit mehr wirkte; B11 ist in dieselbe Grube
 * getreten. Der benannte Rückgabetyp und der Methodenname hier machen die Richtung an der
 * Aufrufstelle lesbar: {@code accountOf(characterId)} liest sich falsch, wenn man ihm eine
 * Kontokennung gibt.
 */
public final class AccountLookup {

    private final Function<UUID, Optional<UUID>> holderOf;

    /**
     * @param holderOf die Übersetzung Charakter → Konto, üblicherweise {@code engine::holderOf}
     */
    public AccountLookup(Function<UUID, Optional<UUID>> holderOf) {
        this.holderOf = Objects.requireNonNull(holderOf, "holderOf");
    }

    /** Die übliche Verdrahtung: gegen B04s Engine. */
    public static AccountLookup backedBy(StatEngine engine) {
        Objects.requireNonNull(engine, "engine");
        return new AccountLookup(engine::holderOf);
    }

    /**
     * Das Konto, zu dem dieser Charakter gehört — leer, wenn er nicht im Spiel ist.
     *
     * <p><b>Leer heißt leer</b>, nicht „nimm die Kennung selbst". Ein Rückfall auf die
     * Charakterkennung wäre die bequemste Zeile dieses Blocks und würde Zähler auf ein Konto
     * schreiben, das es nicht gibt — sichtbar erst, wenn jemand seine Statistik sucht und sie
     * nirgends steht.
     *
     * @param characterId die Kennung der <b>Figur</b>, nicht die des Kontos
     */
    public Optional<UUID> accountOf(UUID characterId) {
        Objects.requireNonNull(characterId, "characterId");
        return holderOf.apply(characterId);
    }
}
