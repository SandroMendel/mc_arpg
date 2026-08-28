package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.LadderSlot;
import rpg.core.combat.DeathCause;
import rpg.core.event.DefaultEventBus;

/**
 * FR-055 — <b>ein Stufenaufstieg repariert nicht.</b>
 *
 * <p>Die Versuchung ist offensichtlich und die Folge auch: wäre der Zustand nach einem Aufstieg
 * wieder voll, wäre der Aufstieg der <em>billigere Weg zur Instandsetzung</em>, sobald er weniger
 * kostet als die Reparatur. Die Coin-Senke aus ADR-017 hätte dann eine Umgehung, und niemand hätte
 * sie beabsichtigt.
 *
 * <p><b>Warum das hier steht und nicht bei B07.</b> B07 kennt den Zustand gar nicht — er wurde mit
 * B11 eingeführt. Die Zusage lautet deshalb nicht „B07 repariert nicht", sondern „<b>niemand</b>
 * setzt den Zustand zurück außer der bezahlten Reparatur", und das ist eine Aussage über diesen
 * Block.
 */
class TierAdvanceDoesNotRepairTest {

    private static final Path ITEM_PACKAGE = Path.of("src", "main", "java", "rpg", "core", "item");

    private final UUID character = UUID.randomUUID();

    private DefaultGearConditions conditions;

    @BeforeEach
    void setUp() {
        conditions =
                new DefaultGearConditions(
                        WearCurve::defaults,
                        new NoRepository(),
                        new DefaultEventBus(java.util.logging.Logger.getLogger("quiet")),
                        Clock.systemUTC());
        conditions.put(GearCondition.full(character));
    }

    @Test
    @DisplayName("der Zustand ueberlebt einen Aufstieg unveraendert")
    void theconditionSurvivesAnAdvanceUnchanged() {
        // B07s Aufstieg schreibt ClassProgress und beruehrt GearCondition nicht - es gibt gar
        // keinen Weg von dort hierher. Genau das wird hier festgehalten: der Zustand aendert sich
        // nur durch Verschleiss und durch Reparatur.
        conditions.onDeath(character, DeathCause.COMBAT);
        double afterDeath = conditions.conditionOf(character, LadderSlot.ARMOR);

        // Was ein Aufstieg tut: nichts hier. Es gibt keinen Aufruf, der ihn ausdruecken koennte.
        assertThat(conditions.conditionOf(character, LadderSlot.ARMOR))
                .as("waere der Aufstieg die billigere Reparatur, ginge niemand mehr zum Haendler")
                .isEqualTo(afterDeath)
                .isLessThan(WearCurve.FULL);
    }

    @Test
    @DisplayName("und nur die bezahlte Reparatur setzt ihn zurueck")
    void onlyThePaidRepairResetsIt() {
        conditions.onDeath(character, DeathCause.COMBAT);

        assertThat(conditions.repair(character, LadderSlot.ARMOR)).isTrue();

        assertThat(conditions.conditionOf(character, LadderSlot.ARMOR)).isEqualTo(WearCurve.FULL);
        assertThat(conditions.conditionOf(character, LadderSlot.WEAPON))
                .as("und nur der bezahlte Slot - die andere Leiter bleibt verschlissen (SC-012)")
                .isLessThan(WearCurve.FULL);
    }

    @Test
    @DisplayName("eine Reparatur ohne Verschleiss wird abgelehnt statt als Null durchgefuehrt")
    void arepairWithoutWearIsRefused() {
        assertThat(conditions.repair(character, LadderSlot.ARMOR))
                .as("FR-054: 'da ist nichts' ist eine Auskunft, eine wortlose Nullbuchung ein Raetsel")
                .isFalse();
    }

    @Test
    @DisplayName("KEINE Klasse dieses Blocks setzt den Zustand ausserhalb der Reparatur zurueck")
    void nothingElseResetsTheCondition() throws IOException {
        // Die Zeile, gegen die das steht, saehe harmlos aus: ein `with(slot, WearCurve.FULL)` an
        // einer Stelle, die "aufraeumt". Genau das waere der zweite Weg zur Instandsetzung.
        //
        // Geprueft wird die SCHREIBENDE Form, nicht das Wort. Der erste Anlauf suchte nach
        // "WearCurve.FULL)" und schlug an RepairPricing an, wo die Zahl in einer Preisformel steht -
        // eine Ausnahme dafuer einzutragen haette den Test um genau das gebracht, was er prueft.
        java.util.regex.Pattern reset =
                java.util.regex.Pattern.compile("\\.with\\([^)]*WearCurve\\.FULL\\)");
        try (var sources = Files.walk(ITEM_PACKAGE)) {
            var offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            // Der eine erlaubte Ort: die bezahlte Reparatur selbst.
                            .filter(
                                    path ->
                                            !path.getFileName()
                                                    .toString()
                                                    .equals("DefaultGearConditions.java"))
                            .filter(
                                    path -> {
                                        try {
                                            return reset.matcher(
                                                            SourceGuard.codeOnly(
                                                                    Files.readString(path)))
                                                    .find();
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as("es gibt genau einen Weg zurueck auf voll, und der kostet Coins (FR-052)")
                    .isEmpty();
        }
    }

    private static final class NoRepository implements GearConditionRepository {

        @Override
        public CompletableFuture<Optional<GearCondition>> find(UUID characterId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void markDirty(UUID characterId) {}
    }
}
