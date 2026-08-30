package rpg.core.statistics;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Ein Belohnungsanspruch ([data-model.md] §1.4, ADR-045).
 *
 * <p><b>Er verfällt nicht.</b> Es gibt keine Frist und keine Ablaufspalte — wer während des
 * Saisonendes nicht online war, hat nichts falsch gemacht.
 *
 * <p>{@link #claimedAt()} leer heißt <b>offen</b>. Das ist die ganze Zustandsmaschine: offen →
 * markiert → gutgeschrieben, und der Übergang von offen nach markiert ist ein bedingtes Update,
 * das genau einmal greift (R2).
 *
 * @param seasonKey aus welcher Saison
 * @param playerId wem er gehört — dem <b>Konto</b>, nicht einer Figur (FR-005)
 * @param rank der belohnte Platz
 * @param coins wie viele Coins
 * @param items Vorlagen mit Stückzahl
 * @param claimedAt wann eingelöst; leer heißt offen
 * @param claimedByCharacter welche Figur eingelöst hat; leer heißt offen
 */
public record RewardClaim(
        String seasonKey,
        UUID playerId,
        int rank,
        long coins,
        List<StatisticsConfig.ItemGrant> items,
        Optional<Instant> claimedAt,
        Optional<UUID> claimedByCharacter) {

    public RewardClaim {
        Objects.requireNonNull(seasonKey, "seasonKey");
        Objects.requireNonNull(playerId, "playerId");
        items = List.copyOf(Objects.requireNonNull(items, "items"));
        Objects.requireNonNull(claimedAt, "claimedAt");
        Objects.requireNonNull(claimedByCharacter, "claimedByCharacter");

        if (claimedAt.isPresent() != claimedByCharacter.isPresent()) {
            // Dieselbe Regel, die auch die Tabelle als CHECK traegt: ein Zeitstempel ohne
            // Einloeser waere eine Einloesung ohne Einloeser, ein Einloeser ohne Zeitstempel eine
            // halbe Markierung.
            throw new IllegalArgumentException(
                    "eingeloest heisst: Zeitpunkt UND Charakter, oder keins von beidem");
        }
        if (coins == 0 && items.isEmpty()) {
            throw new IllegalArgumentException(
                    "ein Anspruch ohne Inhalt waere eine Zusage, die beim Einloesen nichts tut");
        }
    }

    /** Ob er noch offen ist. */
    public boolean isOpen() {
        return claimedAt.isEmpty();
    }

    /** Ein frisch angelegter, offener Anspruch. */
    public static RewardClaim open(
            String seasonKey, UUID playerId, int rank, StatisticsConfig.Reward reward) {
        return new RewardClaim(
                seasonKey,
                playerId,
                rank,
                reward.coins(),
                reward.items(),
                Optional.empty(),
                Optional.empty());
    }
}
