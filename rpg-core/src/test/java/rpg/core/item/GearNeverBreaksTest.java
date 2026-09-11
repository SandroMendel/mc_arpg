package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.LadderSlot;

/**
 * FR-038, SC-011 — <b>kein Maß an Verschleiß zerstört etwas.</b>
 *
 * <p>Die Ansage war eindeutig: <em>„Bei Haltbarkeit 0/100 wird die Ausrüstung nicht zerstört,
 * sondern sie verliert ihre Stats um bis zu 80 %."</em> Ausrüstung <em>ist</em> in diesem Spiel der
 * Fortschritt (ADR-017) — ein zerbrochener Brustpanzer wäre eine gelöschte Stufe, und die kann
 * niemand zurückgeben.
 *
 * <p>B07 hält die Gegenstände bereits unzerstörbar
 * ({@code BoundItemFactory.makeIndestructible}) und schrieb dazu den Einwand auf, den dieser Block
 * auflöst: <em>„a damaged item would quietly weaken a character in a way no attribute reflects."</em>
 * Hier ist die Schwächung keine stille — sie geht durch die Werteberechnung.
 *
 * <p>Deshalb prüft dieser Test zwei Dinge: dass bei null Zustand noch genau der Restanteil bleibt,
 * und dass diesem Block nirgends ein Weg wächst, ein Item wegen Verschleiß zu entfernen.
 */
class GearNeverBreaksTest {

    private static final Path ITEM_PACKAGE = Path.of("src", "main", "java", "rpg", "core", "item");

    private final WearCurve curve = WearCurve.defaults();

    @Test
    @DisplayName("bei Zustand 0 bleiben genau 20 Prozent Beitrag - nicht null")
    void atZeroConditionExactlyTheFloorRemains() {
        assertThat(curve.factorFor(0.0))
                .as("80 Prozent Verlust, wie zugesagt - und 20 Prozent, die bleiben")
                .isEqualTo(0.20);
    }

    @Test
    @DisplayName("auch nach beliebig vielen Toden bleibt der Restanteil")
    void nomatterHowOftenTheCharacterDies() {
        double condition = WearCurve.FULL;
        for (int death = 0; death < 1_000; death++) {
            condition = curve.afterDeath(condition);
        }

        assertThat(condition).isEqualTo(0.0);
        assertThat(curve.factorFor(condition))
                .as("tausend Tode kosten 80 Prozent - nicht mehr, und niemals das Item")
                .isEqualTo(0.20);
    }

    @Test
    @DisplayName("der Zustandsspeicher haelt den Wert bei null fest, statt negativ zu werden")
    void thestoredConditionStopsAtZero() {
        GearCondition worn = GearCondition.full(UUID.randomUUID()).with(LadderSlot.ARMOR, -50.0);

        assertThat(worn.of(LadderSlot.ARMOR)).isEqualTo(0.0);
        assertThat(worn.of(LadderSlot.WEAPON))
                .as("und die andere Leiter bleibt unberuehrt")
                .isEqualTo(WearCurve.FULL);
    }

    @Test
    @DisplayName("KEINE Klasse dieses Blocks entfernt etwas wegen Verschleiss")
    void nothingHereRemovesAnItemBecauseOfWear() throws IOException {
        // Die Versuchung ist klein, aber die Folge waere unumkehrbar. Ein spaeterer Umbau, der
        // "kaputte" Ausruestung aufraeumt, waere aus Sicht des Spielers ein Datenverlust - und aus
        // Sicht des Codes eine Zeile, die vernuenftig aussieht.
        try (var sources = Files.walk(ITEM_PACKAGE)) {
            var offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(
                                    path -> {
                                        try {
                                            String code = SourceGuard.codeOnly(Files.readString(path));
                                            return code.contains("setAmount(0)")
                                                    && code.contains("condition");
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as("Ausruestung wird schwaecher, nicht weniger (FR-038, ADR-017)")
                    .isEmpty();
        }
    }
}
