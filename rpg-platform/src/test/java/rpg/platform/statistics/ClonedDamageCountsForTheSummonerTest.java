package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.DamageDealtEvent;
import rpg.core.combat.DamageType;
import rpg.core.statistics.Metric;
import rpg.core.statistics.Statistics;

/**
 * FR-016a, ADR-047 — <b>der Schaden eines beschworenen Klons steht beim Beschwörer.</b>
 *
 * <h2>Die bewusste Asymmetrie zu B11s FR-041a</h2>
 *
 * <p><b>B11</b> hat für denselben Klon entschieden, dass er <em>keinen</em> Verschleiß verursacht:
 * der Schaden, den er austeilt, nutzt die Waffe des Beschwörers nicht ab. <b>B12</b> entscheidet
 * hier das Gegenteil — sein Schaden zählt für den Beschwörer.
 *
 * <p>Das sieht wie ein Widerspruch aus und ist keiner, und der Unterschied lohnt das Nachlesen,
 * damit ihn niemand später „geradezieht":
 *
 * <ul>
 *   <li>Ein Klon trägt <b>keine eigene Ausrüstung</b>. Verschleiß hätte an fremdem Gerät angesetzt
 *       — am Werkzeug des Spielers, das der Klon gar nicht führt.
 *   <li>Sein <b>Schaden</b> dagegen entsteht unmittelbar aus einer Fähigkeit, die der Spieler
 *       gewirkt und mit Mana bezahlt hat.
 * </ul>
 *
 * <p>Kurz: was der Klon <em>kostet</em>, kostet ihn; was er <em>leistet</em>, leistet der Spieler.
 *
 * <p><b>Warum es überhaupt darauf ankommt:</b> für einige Klassen ist die Beschwörung kein
 * Nebenweg, sondern der Hauptweg, Schaden auszuteilen. Zählte er nicht, wäre die
 * Schadensrangliste für sie strukturell verschlossen — nicht weil sie schwächer wären, sondern
 * weil die Statistik an der falschen Stelle nachsieht.
 *
 * <p><b>Der Blockbezug steht hier ausdrücklich davor</b> („B11s FR-041a"), weil B12 selbst ein
 * FR-041 hat und der Bezeichnerraum blockübergreifend nicht eindeutig ist.
 */
class ClonedDamageCountsForTheSummonerTest {

    @Test
    @DisplayName("ADR-047 - der Schaden des Klons steht beim Beschwoerer, nicht beim Klon")
    void theclonesDamageStandsWithTheSummoner() {
        UUID summoner = UUID.randomUUID();
        UUID clone = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder, Map.of(clone, summoner)).onDamage(damage(clone, 800.0));

        assertThat(recorder.reported).containsExactly(summoner + "|800");
    }

    @Test
    @DisplayName("der eigene Schaden des Beschwoerers bleibt seiner")
    void thesummonersOwnDamageStaysHis() {
        UUID summoner = UUID.randomUUID();
        UUID clone = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder, Map.of(clone, summoner)).onDamage(damage(summoner, 500.0));

        assertThat(recorder.reported).containsExactly(summoner + "|500");
    }

    @Test
    @DisplayName("was kein Klon ist, bleibt bei sich - und das ist fast alles")
    void whatIsNoCloneStaysWithItself() {
        UUID attacker = UUID.randomUUID();
        Recorder recorder = new Recorder();

        listener(recorder, Map.of()).onDamage(damage(attacker, 300.0));

        assertThat(recorder.reported).containsExactly(attacker + "|300");
    }

    @Test
    @DisplayName("zwei Klone desselben Beschwoerers melden beide auf sein Konto")
    void twoClonesOfTheSameSummonerBothReportToHisAccount() {
        UUID summoner = UUID.randomUUID();
        UUID firstClone = UUID.randomUUID();
        UUID secondClone = UUID.randomUUID();
        Recorder recorder = new Recorder();

        DamageStatListener listener =
                listener(recorder, Map.of(firstClone, summoner, secondClone, summoner));
        listener.onDamage(damage(firstClone, 400.0));
        listener.onDamage(damage(secondClone, 900.0));

        // Das Maximieren selbst macht die Datenbank; hier zaehlt, dass beide beim selben Konto
        // ankommen und nicht bei zwei erfundenen.
        assertThat(recorder.reported).containsExactly(summoner + "|400", summoner + "|900");
    }

    private static DamageStatListener listener(Recorder recorder, Map<UUID, UUID> summonerByClone) {
        return new DamageStatListener(
                recorder, id -> Optional.ofNullable(summonerByClone.get(id)));
    }

    private static DamageDealtEvent damage(UUID attacker, double total) {
        return new DamageDealtEvent(attacker, UUID.randomUUID(), DamageType.MAGIC, total, 1, false);
    }

    private static final class Recorder implements Statistics {

        private final List<String> reported = new ArrayList<>();

        @Override
        public void count(UUID playerId, Metric metric, long delta) {
            throw new AssertionError("nicht erwartet");
        }

        @Override
        public void count(UUID playerId, Metric family, String dimension, long delta) {
            throw new AssertionError("nicht erwartet");
        }

        @Override
        public void reportMax(UUID playerId, Metric metric, long value) {
            reported.add(playerId + "|" + value);
        }
    }
}
