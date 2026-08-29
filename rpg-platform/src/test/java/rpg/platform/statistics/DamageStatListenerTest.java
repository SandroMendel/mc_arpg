package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.DamageDealtEvent;
import rpg.core.combat.DamageType;
import rpg.core.statistics.Metric;
import rpg.core.statistics.MetricRegistry;
import rpg.core.statistics.Statistics;

/**
 * FR-016 — der höchste Wert gewinnt, und <b>1249,7 wird zu 1249</b>.
 *
 * <p>Das Abrunden ist der eigentliche Inhalt dieses Tests. Beim Aufrunden holte ein Treffer von
 * 1249,7 einen echten Treffer von 1250 ein — zwei verschiedene Leistungen stünden mit derselben
 * Zahl da, und ausgerechnet beim Maximum fällt so etwas auf, weil dieser eine Wert die ganze
 * Rangliste trägt.
 *
 * <p>Das Maximieren selbst prüft dieser Test nicht: das tut die Datenbank mit {@code GREATEST},
 * und {@code MaxWriteDoesNotReadFirstTest} hält es gegen eine echte PostgreSQL fest. Hier geht es
 * nur darum, welche Zahl überhaupt gemeldet wird.
 */
class DamageStatListenerTest {

    @Test
    @DisplayName("FR-016 - 1249,7 wird zu 1249, nicht zu 1250")
    void afractionIsRoundedDown() {
        UUID attacker = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder).onDamage(damage(attacker, 1249.7));

        assertThat(recorder.reported).containsExactly(attacker + "|1249");
    }

    @Test
    @DisplayName("ein ganzzahliger Wert bleibt, wie er ist")
    void awholeNumberStays() {
        UUID attacker = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder).onDamage(damage(attacker, 1250.0));

        assertThat(recorder.reported).containsExactly(attacker + "|1250");
    }

    @Test
    @DisplayName("die Fenstersumme wird gemeldet, nicht der einzelne Treffer")
    void thewindowSumIsReported() {
        UUID attacker = UUID.randomUUID();
        Recorder recorder = new Recorder();

        // B05 fasst zusammengehoerige Treffer bereits zusammen; hitCount sagt, wie viele es waren.
        // Gemeldet wird trotzdem die Summe - das ist der Schaden, den der Getroffene abbekommen hat.
        listener(recorder)
                .onDamage(
                        new DamageDealtEvent(
                                attacker, UUID.randomUUID(), DamageType.PHYSICAL, 900.0, 3, false));

        assertThat(recorder.reported).containsExactly(attacker + "|900");
    }

    @Test
    @DisplayName("Umgebungsschaden hat keinen Urheber und wird nicht gemeldet")
    void environmentalDamageIsNotReported() {
        Recorder recorder = new Recorder();

        listener(recorder)
                .onDamage(
                        new DamageDealtEvent(
                                null, UUID.randomUUID(), DamageType.PHYSICAL, 40.0, 1, false));

        assertThat(recorder.reported).isEmpty();
    }

    @Test
    @DisplayName("ein Schaden unter einer ganzen Einheit wird nicht gemeldet")
    void damageBelowOneWholeUnitIsNotReported() {
        Recorder recorder = new Recorder();

        // 0,4 abgerundet ist null - und eine gemeldete Null waere ein Maximum, das nichts
        // bedeutet, aber eine Zeile in der Tagestabelle erzeugt.
        listener(recorder).onDamage(damage(UUID.randomUUID(), 0.4));

        assertThat(recorder.reported).isEmpty();
    }

    private static DamageStatListener listener(Recorder recorder) {
        return new DamageStatListener(recorder, id -> Optional.empty());
    }

    private static DamageDealtEvent damage(UUID attacker, double total) {
        return new DamageDealtEvent(attacker, UUID.randomUUID(), DamageType.PHYSICAL, total, 1, false);
    }

    private static final class Recorder implements Statistics {

        private final List<String> reported = new ArrayList<>();

        @Override
        public void count(UUID playerId, Metric metric, long delta) {
            throw new AssertionError("dieser Zuhoerer zaehlt nicht, er meldet ein Maximum");
        }

        @Override
        public void count(UUID playerId, Metric family, String dimension, long delta) {
            throw new AssertionError("dieser Zuhoerer zaehlt nicht, er meldet ein Maximum");
        }

        @Override
        public void reportMax(UUID playerId, Metric metric, long value) {
            assertThat(metric).isEqualTo(MetricRegistry.DAMAGE_MAX);
            reported.add(playerId + "|" + value);
        }
    }
}
