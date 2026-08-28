package rpg.core.item;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Lesen und Vormerken des Ausrüstungszustands.
 *
 * <p>Dieselbe Bauart wie {@code ClassProgressRepository} in B07 und
 * {@code CharacterProgressRepository} in B06, und aus demselben Grund: Lesen ist asynchron,
 * <b>Schreiben ist hier gar keine Methode</b>. Eine Änderung wird <em>vorgemerkt</em>, und B02s
 * Write-Behind-Puffer entscheidet, wann sie in der Datenbank ankommt.
 *
 * <p>Das ist bei diesem Aggregat wichtiger als bei den anderen: der Zustand ändert sich bei
 * <em>jedem Treffer</em>. Ein Schreibvorgang je Treffer wäre Datenbankverkehr im Kampfpfad, und
 * genau das verbietet Prinzip II.
 */
public interface GearConditionRepository {

    /** Leer für einen Charakter ohne Zeile — der Aufrufer setzt {@link GearCondition#full} ein. */
    CompletableFuture<Optional<GearCondition>> find(UUID characterId);

    /** Merkt das Aggregat zum Schreiben vor. Schreibt selbst nie. */
    void markDirty(UUID characterId);
}
