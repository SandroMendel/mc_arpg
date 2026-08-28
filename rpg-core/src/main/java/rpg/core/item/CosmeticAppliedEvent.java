package rpg.core.item;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ein Charakter trägt jetzt eine andere Trimfarbe (FR-070, FR-071).
 *
 * <p><b>Warum ein Ereignis.</b> Die Farbe ändert das <em>Aussehen</em> der getragenen Ausrüstung,
 * und die Ausrüstung liegt in der Plattformschicht. Über den Bus muss {@code rpg-core} nichts von
 * Bukkit wissen — und B13 kann später mithören, ohne dass hier eine Zeile dazukommt.
 *
 * <p>{@code appearance} ist leer, wenn die Farbe abgelegt wurde: dann fällt das Aussehen auf den
 * Trim der Stufe zurück, und das ist derselbe Weg wie bei einer Farbe, die aus der Konfiguration
 * verschwunden ist (FR-073).
 *
 * @param characterId wer
 * @param templateKey welche Farbe — leer, wenn abgelegt
 * @param appearance wie sie aussieht — leer, wenn abgelegt oder unbekannt
 */
public record CosmeticAppliedEvent(
        UUID characterId, Optional<String> templateKey, Optional<CosmeticAppearance> appearance) {

    public CosmeticAppliedEvent {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(templateKey, "templateKey");
        Objects.requireNonNull(appearance, "appearance");
    }

    /** Die Farbe wurde abgelegt — das Aussehen fällt auf die Stufe zurück. */
    public static CosmeticAppliedEvent cleared(UUID characterId) {
        return new CosmeticAppliedEvent(characterId, Optional.empty(), Optional.empty());
    }
}
