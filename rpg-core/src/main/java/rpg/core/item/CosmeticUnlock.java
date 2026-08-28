package rpg.core.item;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Eine gekaufte Trimfarbe (data-model.md §3).
 *
 * <p><b>Besitz und Anwendung sind zwei Dinge.</b> Gekauft wird sie auf jeder Stufe, angewandt erst
 * auf der Höchststufe (FR-069) — wer sie auf Stufe 3 kauft, hat sie, und sie wartet. Wäre der Kauf
 * an die Stufe gebunden, hätte der Spieler auf Stufe 3 gar keinen Grund, in den Bestand zu sehen,
 * und der Grind aus Q2 fiele weg.
 *
 * <p><b>Je Charakter, nicht je Konto</b> (ADR-011). Zwei Figuren eines Spielers teilen keine Farbe;
 * was der Krieger gekauft hat, hat der Schurke nicht.
 *
 * @param characterId wem sie gehört
 * @param templateKey welche Farbe — der Schlüssel aus {@code items.yml}, nicht ihr Aussehen
 * @param applied ob sie gerade getragen wird; höchstens eine je Charakter (FR-071)
 * @param acquiredAt wann sie gekauft wurde
 */
public record CosmeticUnlock(
        UUID characterId, String templateKey, boolean applied, Instant acquiredAt) {

    public CosmeticUnlock {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(acquiredAt, "acquiredAt");
        if (templateKey.isBlank()) {
            throw new IllegalArgumentException("templateKey must not be blank");
        }
    }

    /** Frisch gekauft und noch nicht getragen. */
    public static CosmeticUnlock acquired(UUID characterId, String templateKey, Instant when) {
        return new CosmeticUnlock(characterId, templateKey, false, when);
    }

    public CosmeticUnlock withApplied(boolean nowApplied) {
        return new CosmeticUnlock(characterId, templateKey, nowApplied, acquiredAt);
    }
}
