package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.event.DefaultEventBus;

/**
 * FR-073 — <b>eine Farbe, die die Konfiguration nicht mehr kennt, fällt auf die Stufe zurück — ohne
 * den Besitzvermerk zu verlieren.</b>
 *
 * <p>Der Fall ist gewöhnlicher, als er klingt: ein Betreiber baut die Beutetabellen um, benennt eine
 * Trimfarbe anders, lädt neu. Jede Figur, die sie trug, sitzt jetzt auf einem Schlüssel, den niemand
 * mehr auflöst.
 *
 * <p><b>Drei Antworten wären möglich, und zwei davon sind falsch.</b> Den Start abbrechen hieße, den
 * Betreiber für einen Tippfehler zu bestrafen und alle anderen mit. Die Zeile zu löschen wäre
 * Datenverlust für dieselbe Ursache — und der Spieler hätte eine Farbe bezahlt, die niemand mehr
 * findet. Bleibt die dritte: das Aussehen fällt auf die Stufe zurück, der Vermerk bleibt stehen, und
 * kommt der Schlüssel zurück, ist auch die Farbe wieder da.
 *
 * <p>Dieselbe Entscheidung, die FR-007 für einen Gegenstand mit unbekannter Vorlage getroffen hat:
 * inert statt gelöscht.
 */
class UnknownCosmeticFallsBackTest {

    private static final UUID CHARACTER = UUID.randomUUID();
    private static final String EMBER = "trim.ember";

    private final AtomicReference<ItemConfig> config = new AtomicReference<>(withEmber());

    private CosmeticApplication cosmetics;

    @BeforeEach
    void setUp() {
        cosmetics =
                new CosmeticApplication(
                        config::get,
                        new NoRepository(),
                        (characterId, slot) -> true,
                        new DefaultEventBus(java.util.logging.Logger.getLogger("quiet")),
                        Clock.systemUTC());
        cosmetics.put(CHARACTER, List.of());
        cosmetics.grant(CHARACTER, EMBER);
        cosmetics.apply(CHARACTER, EMBER);
    }

    @Test
    @DisplayName("solange die Vorlage da ist, sieht man die Farbe")
    void whileTheTemplateExistsTheColourIsVisible() {
        assertThat(cosmetics.appearanceOf(CHARACTER))
                .contains(new CosmeticAppearance("REDSTONE", "RAISER"));
    }

    @Test
    @DisplayName("faellt sie aus der Konfiguration, faellt das AUSSEHEN auf die Stufe zurueck")
    void whenItLeavesTheConfigurationTheLookFallsBack() {
        config.set(withoutEmber());

        assertThat(cosmetics.appearanceOf(CHARACTER))
                .as("leer heisst: die Stufe entscheidet, wie es aussieht")
                .isEmpty();
    }

    @Test
    @DisplayName("aber der BESITZ bleibt - der Spieler hat sie bezahlt")
    void buttheOwnershipRemains() {
        config.set(withoutEmber());

        assertThat(cosmetics.owns(CHARACTER, EMBER))
                .as("die Zeile zu loeschen waere Datenverlust fuer einen Betreiberfehler")
                .isTrue();
        assertThat(cosmetics.appliedOf(CHARACTER).orElseThrow().templateKey()).isEqualTo(EMBER);
    }

    @Test
    @DisplayName("und kommt sie zurueck, ist auch die Farbe wieder da")
    void andWhenItComesBackSoDoesTheColour() {
        config.set(withoutEmber());
        config.set(withEmber());

        assertThat(cosmetics.appearanceOf(CHARACTER))
                .as("genau das ist der Grund, den Vermerk stehen zu lassen")
                .contains(new CosmeticAppearance("REDSTONE", "RAISER"));
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private static ItemConfig withEmber() {
        Map<String, ItemTemplate> templates = new LinkedHashMap<>();
        templates.put(
                EMBER,
                new ItemTemplate(
                        EMBER,
                        ItemCategory.COSMETIC,
                        "NETHERITE_UPGRADE_SMITHING_TEMPLATE",
                        Rarity.LEGENDARY,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new CosmeticAppearance("REDSTONE", "RAISER")));
        return config(templates);
    }

    private static ItemConfig withoutEmber() {
        return config(new LinkedHashMap<>());
    }

    private static ItemConfig config(Map<String, ItemTemplate> templates) {
        return new ItemConfig(
                templates,
                WearCurve.defaults(),
                new RepairPricing(List.of(0L, 40L)),
                LootTables.empty(),
                Map.of());
    }

    private static final class NoRepository implements CosmeticRepository {

        @Override
        public CompletableFuture<List<CosmeticUnlock>> find(UUID characterId) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public void markDirty(UUID characterId) {}
    }
}
