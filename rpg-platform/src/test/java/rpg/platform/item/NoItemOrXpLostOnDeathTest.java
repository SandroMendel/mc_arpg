package rpg.platform.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.classes.LadderSlot;
import rpg.core.combat.CombatDeathEvent;
import rpg.core.combat.DamageShare;
import rpg.core.combat.DeathCause;
import rpg.core.item.DefaultGearConditions;
import rpg.core.item.GearCondition;
import rpg.core.item.GearConditionRepository;
import rpg.core.item.WearCurve;

/**
 * SC-010, FR-046 — <b>ein Tod kostet Verschleiß und sonst nichts.</b>
 *
 * <p>Nach ADR-017 verliert ein Spieler beim Tod weder Gegenstände noch Erfahrung. Das ist keine
 * Nachlässigkeit, sondern die Entscheidung, die den Verschleiß überhaupt nötig macht: <b>er ist das
 * Einzige, was ein Tod kostet</b>, und wenn er nichts kostete, gäbe es keinen Grund, den Tod zu
 * vermeiden.
 *
 * <p>Der Test hat deshalb zwei Hälften, und die zweite ist die wichtigere. Die erste zeigt, dass der
 * Tod den Zustand senkt. Die zweite zeigt, dass dieser Block <em>nichts anderes</em> anfasst — keine
 * Inventare, keine Erfahrung. Eine Zeile, die beim Tod „aufräumt", sähe vernünftig aus und wäre
 * genau der Verlust, den ADR-017 ausgeschlossen hat.
 */
class NoItemOrXpLostOnDeathTest {

    private static final Logger QUIET = Logger.getLogger("no-loss-on-death-test");
    private static final Path ITEM_PACKAGE =
            Path.of("src", "main", "java", "rpg", "platform", "item");

    private static final UUID PLAYER = UUID.randomUUID();
    private static final UUID CHARACTER = UUID.randomUUID();

    private DefaultGearConditions conditions;
    private WearListener listener;

    @BeforeEach
    void setUp() {
        conditions =
                new DefaultGearConditions(
                        WearCurve::defaults,
                        new NoRepository(),
                        new rpg.core.event.DefaultEventBus(QUIET),
                        Clock.systemUTC());
        conditions.put(GearCondition.full(CHARACTER));
        listener = new WearListener(conditions, QUIET);
    }

    @Test
    @DisplayName("der Tod senkt BEIDE Leitern - das ist die ganze Strafe")
    void adeathLowersBothLadders() {
        listener.onDeath(death(DeathCause.COMBAT, true));

        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.ARMOR)).isLessThan(WearCurve.FULL);
        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.WEAPON)).isLessThan(WearCurve.FULL);
    }

    @Test
    @DisplayName("der Tod einer KREATUR kostet niemanden etwas")
    void thedeathOfACreatureCostsNobodyAnything() {
        // playerVictim ist der Haken, den B05 fuer genau diesen Fall gesetzt hat. Eine gestorbene
        // Kreatur traegt keine Ausruestung - und haette sie einen Charakter, waere es der falsche.
        listener.onDeath(death(DeathCause.COMBAT, false));

        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.ARMOR)).isEqualTo(WearCurve.FULL);
    }

    @Test
    @DisplayName("ein /kill kostet nichts (FR-042)")
    void anadminKillCostsNothing() {
        listener.onDeath(death(DeathCause.ADMIN, true));

        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.ARMOR)).isEqualTo(WearCurve.FULL);
        assertThat(conditions.conditionOf(CHARACTER, LadderSlot.WEAPON)).isEqualTo(WearCurve.FULL);
    }

    @Test
    @DisplayName("KEINE Klasse dieses Blocks raeumt beim Tod ein Inventar oder Erfahrung ab")
    void nothingHereTakesItemsOrExperienceOnDeath() throws IOException {
        // Die Zeile, gegen die das steht, saehe vernuenftig aus: ein "clear()" oder ein
        // "setLevel(0)" in einem Todesbehandler. Und sie waere der Verlust, den ADR-017
        // ausgeschlossen hat - bemerkt erst von dem Spieler, dem es passiert.
        try (var sources = Files.walk(ITEM_PACKAGE)) {
            List<String> offenders =
                    sources.filter(path -> path.toString().endsWith(".java"))
                            .filter(
                                    path -> {
                                        try {
                                            String code = codeOnly(Files.readString(path));
                                            return code.contains("getInventory().clear()")
                                                    || code.contains("setExp(")
                                                    || code.contains("setTotalExperience(")
                                                    || code.contains("setKeepInventory(");
                                        } catch (IOException failure) {
                                            throw new IllegalStateException(failure);
                                        }
                                    })
                            .map(path -> path.getFileName().toString())
                            .toList();

            assertThat(offenders)
                    .as("beim Tod verliert man Haltbarkeit - und sonst nichts (FR-046, SC-010)")
                    .isEmpty();
        }
    }

    private static CombatDeathEvent death(DeathCause cause, boolean playerVictim) {
        return new CombatDeathEvent(
                PLAYER, playerVictim ? CHARACTER : null, null, cause, DamageShare.empty(), playerVictim);
    }

    /** Kommentare weg — eine Erklärung ist kein Aufruf. */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
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
