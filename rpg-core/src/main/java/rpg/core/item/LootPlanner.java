package rpg.core.item;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.random.RandomGenerator;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.progression.PartyRegistry;
import rpg.core.progression.ProximityCheck;
import rpg.core.progression.WorldPoint;

/**
 * Was ein Tod fallen lässt, und wem es gehört — der ganze Entscheidungsweg an einer Stelle.
 *
 * <p><b>Bukkit-frei, nach dem Muster von {@code CoinDropPlanner}</b> (research.md R4). Alles über
 * <em>wer worauf Anspruch hat</em> wird hier entschieden und ist ohne Server testbar; die
 * Plattformschicht macht aus jedem Posten eine Entität.
 *
 * <p><b>Der Weg:</b>
 *
 * <pre>
 *   CombatDeathEvent
 *     └─ lootRecipient()  — der groesste Beitragende (B05), NICHT der letzte Treffer
 *          ├─ in keiner Party  → er selbst                                    FR-026
 *          └─ in einer Party   → die Party gilt als EIN Beitragender           FR-026a
 *                                 └─ Mitglieder in Reichweite zum Gegner       FR-026c
 *                                      ├─ mindestens eines → reihum, je Posten FR-026b
 *                                      └─ keines           → zurueck auf ihn   FR-026c
 * </pre>
 *
 * <p><b>Warum Beute sich nicht teilt wie Erfahrung und Coins.</b> B06 und B08b benutzen denselben
 * {@code ShareCalculator} und verteilen nach Anteil (ADR-029). B05 hat für Gegenstände das Gegenteil
 * entschieden, und die Begründung steht in {@code DamageShare}: <em>„XP is split by share because XP
 * divides, loot goes to the largest contributor because a sword does not."</em> Dieser Block
 * erfindet nichts, er benutzt die vorhandene Antwort — und ergänzt sie nur um die Party, die B05
 * nicht kennt.
 *
 * <p><b>Die Reichweite kommt aus {@code progression.yml}</b>, nicht aus {@code items.yml}. Zwei
 * Zahlen für dieselbe Frage wären eine zu viel, und niemand könnte erklären, warum Erfahrung und
 * Beute unterschiedlich weit reichen.
 */
public final class LootPlanner {

    private final Supplier<ItemConfig> config;
    private final PartyRegistry parties;
    private final Supplier<ProximityCheck> proximity;
    private final Supplier<Double> shareRange;
    private final CharacterOf characterOf;
    private final PartyLootRotation rotation;
    private final RandomGenerator random;

    /** Welchen Charakter ein Spieler gerade spielt — dieselbe Frage, die B08b stellt (ADR-011). */
    @FunctionalInterface
    public interface CharacterOf {
        Optional<UUID> characterOf(UUID playerId);
    }

    /** Wiederverwendete Puffer: ein Tod darf keine Felder allozieren (Prinzip II). */
    private final UUID[] members = new UUID[16];
    private final UUID[] inRange = new UUID[16];

    public LootPlanner(
            Supplier<ItemConfig> config,
            PartyRegistry parties,
            Supplier<ProximityCheck> proximity,
            Supplier<Double> shareRange,
            CharacterOf characterOf,
            PartyLootRotation rotation,
            RandomGenerator random) {
        this.config = Objects.requireNonNull(config, "config");
        this.parties = Objects.requireNonNull(parties, "parties");
        this.proximity = Objects.requireNonNull(proximity, "proximity");
        this.shareRange = Objects.requireNonNull(shareRange, "shareRange");
        this.characterOf = Objects.requireNonNull(characterOf, "characterOf");
        this.rotation = Objects.requireNonNull(rotation, "rotation");
        this.random = Objects.requireNonNull(random, "random");
    }

    /**
     * Was dieser Tod hinterlässt.
     *
     * @param death das Ereignis von B05
     * @param kindKey die Art der gestorbenen Kreatur
     * @param zoneKey ihre Ursprungsregion
     * @param boss ob sie ein Boss war
     * @param where wo sie starb — Bezugspunkt für die Reichweite, gelesen solange er gültig war
     * @return die Posten, die fallen sollen; leer, wenn nichts fällt
     */
    public List<LootClaim> plan(
            CombatDeathEvent death, String kindKey, String zoneKey, boolean boss, WorldPoint where) {
        Objects.requireNonNull(death, "death");

        if (death.playerVictim()) {
            // Ein gestorbener Spieler laesst nichts fallen - das ist die Todesstrafe aus ADR-017,
            // und sie besteht aus Verschleiss, nicht aus Verlust (FR-046).
            return List.of();
        }

        Optional<UUID> recipient = death.lootRecipient();
        if (recipient.isEmpty()) {
            // Niemand hat nennenswert beigetragen: Sturz, Sonnenlicht, eine andere Kreatur, oder
            // das Aufraeumen einer Zone. Kein Spieler, keine Beute (FR-019).
            return List.of();
        }

        LootTable table = config.get().loot().tableFor(kindKey, zoneKey, boss);
        List<LootTable.Roll> rolls = table.roll(random);
        if (rolls.isEmpty()) {
            return List.of();
        }

        int candidateCount = partyMembersInRange(recipient.get(), where);
        UUID partyId = parties.partyOf(recipient.get()).map(PartyRegistry.PartyView::partyId).orElse(null);

        List<LootClaim> claims = new ArrayList<>(rolls.size());
        for (LootTable.Roll roll : rolls) {
            // Je POSTEN weitergereicht, nicht je Kill (FR-026b).
            UUID ownerPlayerId = ownerFor(partyId, candidateCount, recipient.get());
            Optional<UUID> ownerCharacter = characterOf.characterOf(ownerPlayerId);
            if (ownerCharacter.isEmpty()) {
                // Kein Charakter im Spiel - der Posten faellt aus. Ihn dem naechsten zu geben
                // waere eine stille Umverteilung, die niemand erwartet.
                continue;
            }
            claims.add(new LootClaim(roll.templateKey(), roll.count(), ownerCharacter.get()));
        }
        return claims;
    }

    /**
     * Der Spieler, dem dieser eine Posten gehört.
     *
     * <p>Ohne Party oder ohne jemanden in Reichweite: der größte Beitragende selbst (FR-026,
     * FR-026c). Sonst reihum unter den Anwesenden.
     */
    private UUID ownerFor(UUID partyId, int candidateCount, UUID recipient) {
        if (partyId == null || candidateCount == 0) {
            return recipient;
        }
        UUID chosen = rotation.next(partyId, inRange, candidateCount);
        return chosen == null ? recipient : chosen;
    }

    /** Füllt {@link #inRange} und gibt zurück, wie viele Mitglieder in Reichweite stehen. */
    private int partyMembersInRange(UUID recipient, WorldPoint where) {
        if (where == null) {
            return 0;
        }
        int memberCount = parties.membersOf(recipient, members);
        if (memberCount == 0) {
            return 0;
        }
        // Gemessen zum GESTORBENEN GEGNER - der einzige Bezugspunkt, den alle Mitglieder
        // gemeinsam haben. Dieselbe Wahl wie in B06s ShareCalculator, Schritt 4.
        return proximity.get().inRange(where, members, memberCount, shareRange.get(), inRange);
    }
}
