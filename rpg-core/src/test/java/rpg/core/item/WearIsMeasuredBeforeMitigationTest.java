package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.LadderSlot;
import rpg.core.combat.DamageOrigin;
import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;

/**
 * FR-040a — <b>der Test gegen die Abwärtsspirale.</b>
 *
 * <p>Gemessen wird am <em>ankommenden</em> Schaden, vor der Abwehr. Am durchgekommenen gemessen
 * entstünde eine Rückkopplung, die niemand entworfen hat: verschlissene Rüstung lässt mehr durch,
 * mehr Durchgekommenes nutzt sie schneller ab, und ab einem gewissen Punkt fällt sie von allein
 * auseinander. Gute Rüstung wäre umgekehrt doppelt belohnt — sie hielte mehr aus <em>und</em> hielte
 * länger.
 *
 * <p>Deshalb dieser Test: <b>zwei Spieler mit unterschiedlich guter Rüstung verschleißen beim selben
 * Treffer gleich stark.</b> Genau das wäre nicht der Fall, wenn irgendwo der Schaden nach Abwehr
 * eingesetzt würde.
 */
class WearIsMeasuredBeforeMitigationTest {

    private static final Path ITEM_PACKAGE = Path.of("src", "main", "java", "rpg", "core", "item");

    private final UUID wellArmored = UUID.randomUUID();
    private final UUID barelyArmored = UUID.randomUUID();

    private DefaultGearConditions conditions;

    @BeforeEach
    void setUp() {
        EventBus events = new DefaultEventBus(java.util.logging.Logger.getLogger("quiet"));
        conditions =
                new DefaultGearConditions(
                        WearCurve::defaults, new NoRepository(), events, Clock.systemUTC());
        conditions.put(GearCondition.full(wellArmored));
        conditions.put(GearCondition.full(barelyArmored));
    }

    @Test
    @DisplayName("derselbe ANKOMMENDE Treffer verschleisst beide gleich stark")
    void thesameIncomingHitWearsBothTheSame() {
        // Der Treffer ist derselbe: 400 Punkte, bevor irgendeine Ruestung ihn anfasst. Was danach
        // durchkommt, ist verschieden - und darf hier keine Rolle spielen.
        conditions.onDamageTaken(wellArmored, DamageOrigin.MELEE, false, 400.0);
        conditions.onDamageTaken(barelyArmored, DamageOrigin.MELEE, false, 400.0);

        assertThat(conditions.conditionOf(wellArmored, LadderSlot.ARMOR))
                .as("gute Ruestung haelt mehr aus - sie soll deswegen nicht auch laenger halten")
                .isEqualTo(conditions.conditionOf(barelyArmored, LadderSlot.ARMOR));
    }

    @Test
    @DisplayName("und schon verschlissene Ruestung verschleisst nicht SCHNELLER - keine Spirale")
    void alreadyWornArmorDoesNotWearFaster() {
        // Der Kern der Anforderung. Beide bekommen denselben Treffer; einer ist bereits halb
        // verschlissen. Waere der Verschleiss vom durchgekommenen Schaden abhaengig, verloere der
        // Verschlissene mehr - und von da an immer mehr.
        for (int hit = 0; hit < 50; hit++) {
            conditions.onDamageTaken(barelyArmored, DamageOrigin.MELEE, false, 100.0);
        }
        double wornBefore = conditions.conditionOf(barelyArmored, LadderSlot.ARMOR);
        double freshBefore = conditions.conditionOf(wellArmored, LadderSlot.ARMOR);

        conditions.onDamageTaken(barelyArmored, DamageOrigin.MELEE, false, 100.0);
        conditions.onDamageTaken(wellArmored, DamageOrigin.MELEE, false, 100.0);

        double wornLost = wornBefore - conditions.conditionOf(barelyArmored, LadderSlot.ARMOR);
        double freshLost = freshBefore - conditions.conditionOf(wellArmored, LadderSlot.ARMOR);

        assertThat(wornLost)
                .as("derselbe Treffer, derselbe Abtrag - egal wie es um die Ruestung steht")
                .isCloseTo(freshLost, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("die Kurve kennt den Zustand des Traegers gar nicht - sie kann ihn nicht einrechnen")
    void thecurveCannotSeeTheWearerAtAll() {
        // Der strukturelle Beleg. afterDamageTaken bekommt den Zustand und den Schaden, sonst
        // nichts: keine Ruestungswerte, keinen Charakter, keine Momentaufnahme. Eine Spirale liesse
        // sich hier gar nicht hineinschreiben, ohne die Signatur zu aendern.
        WearCurve curve = WearCurve.defaults();

        assertThat(curve.afterDamageTaken(100.0, 300.0) - 100.0)
                .isCloseTo(curve.afterDamageTaken(20.0, 300.0) - 20.0, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    @DisplayName("und NIRGENDS im Block wird der Schaden nach Abwehr fuer Verschleiss benutzt")
    void nothingHereUsesTheDamageAfterDefence() throws IOException {
        // Die Zeile, die diesen Test faellig gemacht hat, waere ein `finalDamage()` statt eines
        // `rawDamage()` - unauffaellig, plausibel, und der Anfang der Spirale.
        try (var sources = Files.walk(ITEM_PACKAGE)) {
            var offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(
                                    path -> {
                                        try {
                                            return SourceGuard.codeOnly(Files.readString(path))
                                                    .contains("finalDamage");
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as("gemessen wird vor der Abwehr - das ist die Anforderung, keine Feinheit")
                    .isEmpty();
        }
    }

    /** Merkt sich nichts vor: hier zählt der Zustand, nicht das Schreiben. */
    private static final class NoRepository implements GearConditionRepository {

        @Override
        public java.util.concurrent.CompletableFuture<java.util.Optional<GearCondition>> find(
                UUID characterId) {
            return java.util.concurrent.CompletableFuture.completedFuture(java.util.Optional.empty());
        }

        @Override
        public void markDirty(UUID characterId) {}
    }
}
