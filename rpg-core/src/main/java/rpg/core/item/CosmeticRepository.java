package rpg.core.item;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Lesen und Vormerken der gekauften Trimfarben.
 *
 * <p>Wie {@link GearConditionRepository} und {@code ClassProgressRepository}: Lesen ist asynchron,
 * <b>Schreiben ist hier keine Methode</b>. Eine Änderung wird vorgemerkt, B02s Write-Behind
 * entscheidet, wann sie ankommt.
 *
 * <p><b>Mehrere Zeilen je Charakter</b>, anders als beim Verschleiß: wer drei Farben gekauft hat,
 * hat drei Zeilen, und höchstens eine davon trägt {@code applied}. Der Stapel schreibt deshalb eine
 * <em>Menge</em> und nicht einen Wert — dieselbe Form wie B08s Fähigkeitszeilen.
 */
public interface CosmeticRepository {

    /** Alles, was dieser Charakter besitzt. Leer ist der Normalfall, kein Fehlschlag. */
    CompletableFuture<List<CosmeticUnlock>> find(UUID characterId);

    /** Merkt den Charakter zum Schreiben vor. Schreibt selbst nie. */
    void markDirty(UUID characterId);
}
