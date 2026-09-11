package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.LadderSlot;
import rpg.core.event.DefaultEventBus;

/**
 * FR-069 — <b>eine Trimfarbe ist erst auf der Höchststufe anwendbar, und der Besitz bleibt trotzdem.</b>
 *
 * <p><b>Warum die Beschränkung überhaupt existiert</b>, steht in B07s eigener Anforderung: für
 * Schurke und Krieger ist der Trim das <em>einzige</em> Unterscheidungsmerkmal ihrer oberen Stufen
 * (B07/FR-016, {@code TierAppearance}). Eine frei anwendbare Farbe würde ihn überschreiben —
 * Schurkenstufe 4 und 6 sähen gleich aus, und die Sichtbarkeit des Fortschritts, für die
 * {@code TierAppearance} überhaupt gebaut wurde, wäre weg. Auf der Höchststufe gibt es nichts mehr zu
 * verwechseln.
 *
 * <p><b>Und der zweite Teil ist der wichtigere.</b> Eine Ablehnung nimmt nichts weg. Wer auf Stufe 3
 * kauft, hat die Farbe, und sie wartet — das ist der Grind aus Q2. Verlöre der Kauf sich, hätte
 * niemand auf Stufe 3 einen Grund, in den Bestand zu sehen.
 */
class CosmeticOnlyAtTopTierTest {

    private static final UUID CHARACTER = UUID.randomUUID();
    private static final String EMBER = "trim.ember";
    private static final String FROST = "trim.frost";

    private final Map<LadderSlot, Boolean> atTop = new java.util.EnumMap<>(LadderSlot.class);
    private final RecordingRepository repository = new RecordingRepository();

    private CosmeticApplication cosmetics;

    @BeforeEach
    void setUp() {
        atTop.put(LadderSlot.ARMOR, false);
        atTop.put(LadderSlot.WEAPON, false);
        cosmetics =
                new CosmeticApplication(
                        CosmeticOnlyAtTopTierTest::config,
                        repository,
                        (characterId, slot) -> atTop.get(slot),
                        new DefaultEventBus(java.util.logging.Logger.getLogger("quiet")),
                        Clock.systemUTC());
        cosmetics.put(CHARACTER, List.of());
    }

    @Test
    @DisplayName("unterhalb der Hoechststufe abgelehnt - und der BESITZ BLEIBT")
    void belowTheTopItIsRefusedButKept() {
        cosmetics.grant(CHARACTER, EMBER);

        assertThat(cosmetics.apply(CHARACTER, EMBER))
                .isEqualTo(CosmeticApplication.Outcome.NOT_TOP_TIER);

        assertThat(cosmetics.owns(CHARACTER, EMBER))
                .as("sonst waere der Kauf verloren, und niemand kaufte auf Stufe 3 (Q2)")
                .isTrue();
        assertThat(cosmetics.appliedOf(CHARACTER)).isEmpty();
    }

    @Test
    @DisplayName("BEIDE Leitern muessen oben sein - eine reicht nicht")
    void bothLaddersHaveToBeAtTheTop() {
        cosmetics.grant(CHARACTER, EMBER);
        atTop.put(LadderSlot.ARMOR, true);

        assertThat(cosmetics.apply(CHARACTER, EMBER))
                .as("wer die Waffenleiter noch vor sich hat, ist nicht am Ende seines Fortschritts")
                .isEqualTo(CosmeticApplication.Outcome.NOT_TOP_TIER);
    }

    @Test
    @DisplayName("auf der Hoechststufe wird sie getragen")
    void atTheTopItIsWorn() {
        cosmetics.grant(CHARACTER, EMBER);
        reachTheTop();

        assertThat(cosmetics.apply(CHARACTER, EMBER)).isEqualTo(CosmeticApplication.Outcome.DONE);

        assertThat(cosmetics.appliedOf(CHARACTER).orElseThrow().templateKey()).isEqualTo(EMBER);
        assertThat(cosmetics.appearanceOf(CHARACTER))
                .contains(new CosmeticAppearance("REDSTONE", "RAISER"));
    }

    @Test
    @DisplayName("was der Charakter NICHT besitzt, kann er nicht tragen - auch nicht oben")
    void whatIsNotOwnedCannotBeWorn() {
        reachTheTop();

        assertThat(cosmetics.apply(CHARACTER, EMBER))
                .isEqualTo(CosmeticApplication.Outcome.NOT_OWNED);
    }

    @Test
    @DisplayName("FR-071: eine zweite Farbe ERSETZT die erste - und nimmt sie nicht weg")
    void asecondColourReplacesTheFirst() {
        cosmetics.grant(CHARACTER, EMBER);
        cosmetics.grant(CHARACTER, FROST);
        reachTheTop();
        cosmetics.apply(CHARACTER, EMBER);

        assertThat(cosmetics.apply(CHARACTER, FROST)).isEqualTo(CosmeticApplication.Outcome.DONE);

        assertThat(cosmetics.appliedOf(CHARACTER).orElseThrow().templateKey()).isEqualTo(FROST);
        assertThat(cosmetics.ownedBy(CHARACTER))
                .as("beide gehoeren ihm weiterhin - ein Wechsel ist keine Aufgabe")
                .hasSize(2);
    }

    @Test
    @DisplayName("dieselbe Farbe zweimal anzuziehen ist keine Aenderung")
    void wearingTheSameColourTwiceIsNoChange() {
        cosmetics.grant(CHARACTER, EMBER);
        reachTheTop();
        cosmetics.apply(CHARACTER, EMBER);

        assertThat(cosmetics.apply(CHARACTER, EMBER))
                .isEqualTo(CosmeticApplication.Outcome.ALREADY_APPLIED);
    }

    @Test
    @DisplayName("ein Trank ist keine Kosmetik - er wird abgewiesen, nicht getragen")
    void apotionIsNoCosmetic() {
        cosmetics.grant(CHARACTER, "potion.test");
        reachTheTop();

        assertThat(cosmetics.apply(CHARACTER, "potion.test"))
                .isEqualTo(CosmeticApplication.Outcome.UNKNOWN_TEMPLATE);
    }

    @Test
    @DisplayName("Ablegen faellt auf das Aussehen der Stufe zurueck")
    void clearingFallsBackToTheTier() {
        cosmetics.grant(CHARACTER, EMBER);
        reachTheTop();
        cosmetics.apply(CHARACTER, EMBER);

        assertThat(cosmetics.clear(CHARACTER)).isTrue();

        assertThat(cosmetics.appearanceOf(CHARACTER)).isEmpty();
        assertThat(cosmetics.owns(CHARACTER, EMBER))
                .as("abgelegt ist nicht verkauft")
                .isTrue();
    }

    @Test
    @DisplayName("KEINE Ablehnung merkt etwas zum Schreiben vor")
    void norefusalEverMarksAnythingDirty() {
        cosmetics.grant(CHARACTER, EMBER);
        repository.marks.clear();

        cosmetics.apply(CHARACTER, EMBER); // NOT_TOP_TIER
        cosmetics.apply(CHARACTER, "trim.nope"); // UNKNOWN_TEMPLATE
        cosmetics.apply(CHARACTER, FROST); // NOT_OWNED

        assertThat(repository.marks)
                .as("was nicht passiert ist, muss auch nicht geschrieben werden")
                .isEmpty();
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private void reachTheTop() {
        atTop.put(LadderSlot.ARMOR, true);
        atTop.put(LadderSlot.WEAPON, true);
    }

    private static ItemConfig config() {
        Map<String, ItemTemplate> templates = new LinkedHashMap<>();
        templates.put(EMBER, cosmetic(EMBER, "REDSTONE", "RAISER"));
        templates.put(FROST, cosmetic(FROST, "DIAMOND", "SILENCE"));
        templates.put(
                "potion.test",
                new ItemTemplate(
                        "potion.test",
                        ItemCategory.CONSUMABLE,
                        "POTION",
                        Rarity.COMMON,
                        null,
                        null,
                        3L,
                        null,
                        ConsumableEffect.healing(40.0, Duration.ofSeconds(8)),
                        null));
        return new ItemConfig(
                templates,
                WearCurve.defaults(),
                new RepairPricing(List.of(0L, 40L)),
                LootTables.empty(),
                Map.of());
    }

    private static ItemTemplate cosmetic(String key, String trimMaterial, String trimPattern) {
        return new ItemTemplate(
                key,
                ItemCategory.COSMETIC,
                "NETHERITE_UPGRADE_SMITHING_TEMPLATE",
                Rarity.LEGENDARY,
                null,
                null,
                null,
                null,
                null,
                new CosmeticAppearance(trimMaterial, trimPattern));
    }

    /** Schreibt mit, was vorgemerkt wurde — und ob überhaupt. */
    private static final class RecordingRepository implements CosmeticRepository {

        private final List<UUID> marks = new ArrayList<>();

        @Override
        public CompletableFuture<List<CosmeticUnlock>> find(UUID characterId) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public void markDirty(UUID characterId) {
            marks.add(characterId);
        }
    }
}
