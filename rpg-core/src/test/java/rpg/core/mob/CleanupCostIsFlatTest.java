package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T012c, FR-018, Prinzip II: die Aufraeumentscheidung kostet je Kreatur immer dasselbe.
 *
 * <p><b>Der Verstoss, den dieser Test sichtbar macht.</b> Die naheliegende Fassung von "steht ein
 * Spieler in Reichweite dieser Kreatur?" ist eine Schleife je Kreatur ueber alle Spieler. Sie ist
 * richtig, sie ist lesbar, sie faellt in keinem anderen Test auf - und bei 130 Kreaturen und 200
 * Spielern sind es 26.000 Abstandsrechnungen je Durchlauf. Das ist genau die lineare Iteration ueber
 * alle Kandidaten, die FR-018 verbietet, und ohne diesen Test bliebe sie gruen, bis sie auf einem
 * vollen Server als Ruckeln auftaucht.
 *
 * <p>{@code HordeBudgetBenchmarkTest} misst dagegen den <em>Betrag</em> eines Durchlaufs bei 130
 * Kreaturen und fuenf Spielern. Das ist eine andere Frage: sie sagt, ob es heute schnell genug ist,
 * nicht, ob es das bei doppelter Menge noch waere.
 *
 * <p><b>Drei Wege, dieselbe Zusage.</b> Zwei gemessene - je Kreatur bei 13 und bei 130 Kreaturen,
 * und je Kreatur bei einem kleinen und einem sehr grossen gestempelten Index - und einer, der nicht
 * misst, sondern nachsieht: {@link CleanupRule#shouldRemove} bekommt die Spieler gar nicht erst in
 * die Hand. Was man nicht hat, kann man nicht in einer Schleife durchgehen. Der dritte ist der
 * verlaesslichste; die beiden gemessenen fangen, was innerhalb von {@link NearbyChunks} passiert.
 *
 * <p>Gemessen wird wie in {@code HordeBudgetBenchmarkTest}: aufwaermen, dann den <b>schnellsten</b>
 * von mehreren Durchlaeufen nehmen. Das Minimum ist der stabilste Schaetzer, den man ohne JMH
 * bekommt - ein Mittelwert traegt jede Pause des Betriebssystems mit.
 */
class CleanupCostIsFlatTest {

    private static final int FEW_CREATURES = 13;
    private static final int MANY_CREATURES = 130;

    /** Beide Messungen entscheiden gleich oft - verglichen wird der Preis je Entscheidung. */
    private static final int DECISIONS_PER_ROUND = 13_000;

    private static final int WARMUP_ROUNDS = 100;
    private static final int MEASURED_ROUNDS = 50;

    /** Zehnfache Menge darf nicht dreifach teuer sein. Eine Spielerschleife waere zweistellig. */
    private static final double CREATURE_TOLERANCE = 3.0;

    /**
     * Ein vielfach groesserer Index darf den Nachschlag nicht 30-mal teurer machen. Eine lineare
     * Suche waere hier vierstellig langsamer; die Marge deckt die Cache-Fehlgriffe ab, die ein
     * grosser Hash unvermeidlich hat.
     */
    private static final double INDEX_TOLERANCE = 30.0;

    @Test
    @DisplayName("130 Kreaturen kosten je Kreatur nicht mehr als 13 - bei gleicher Spielerzahl")
    void oneHundredThirtyCreaturesCostNoMorePerCreatureThanThirteen() {
        NearbyChunks nearby = stampedFor(5);

        Measurement few = measure(creatureChunks(FEW_CREATURES), nearby);
        Measurement many = measure(creatureChunks(MANY_CREATURES), nearby);

        System.out.printf(
                "[mob] T012c: je Entscheidung %.2f ns bei %d Kreaturen, %.2f ns bei %d (Faktor"
                        + " %.2f, erlaubt %.1f)%n",
                few.nanosPerDecision(),
                FEW_CREATURES,
                many.nanosPerDecision(),
                MANY_CREATURES,
                many.nanosPerDecision() / few.nanosPerDecision(),
                CREATURE_TOLERANCE);

        assertThat(many.nanosPerDecision())
                .as(
                        "FR-018: die Entscheidung je Kreatur muss von der Zahl der Kreaturen"
                                + " unabhaengig sein - %.2f ns bei %d gegen %.2f ns bei %d",
                        many.nanosPerDecision(),
                        MANY_CREATURES,
                        few.nanosPerDecision(),
                        FEW_CREATURES)
                .isLessThan(few.nanosPerDecision() * CREATURE_TOLERANCE);
    }

    @Test
    @DisplayName("ein viel groesserer Spielerumkreis macht den Nachschlag nicht linear teurer")
    void aMuchLargerStampedIndexDoesNotMakeTheLookupLinear() {
        // Der Unterschied, um den es geht: fuenf Spieler an einer Ecke der Welt gegen zweihundert
        // ueber ein ganzes Gebiet verteilt. Der Index waechst dabei um ein Vielfaches. Waere er
        // eine Liste statt einer Menge, waere jeder Nachschlag eine Schleife ueber ihn - und genau
        // das ist die Bauart, gegen die NearbyChunks geschrieben wurde.
        List<Long> creatures = creatureChunks(MANY_CREATURES);
        NearbyChunks small = stampedFor(5);
        NearbyChunks large = stampedFor(200);

        Measurement inSmall = measure(creatures, small);
        Measurement inLarge = measure(creatures, large);

        System.out.printf(
                "[mob] T012c: je Entscheidung %.2f ns bei %d gestempelten Chunks, %.2f ns bei %d"
                        + " (Faktor %.2f, erlaubt %.1f)%n",
                inSmall.nanosPerDecision(),
                small.size(),
                inLarge.nanosPerDecision(),
                large.size(),
                inLarge.nanosPerDecision() / inSmall.nanosPerDecision(),
                INDEX_TOLERANCE);

        assertThat(large.size())
                .as("die zweite Messung muss wirklich einen viel groesseren Index vor sich haben")
                .isGreaterThan(small.size() * 10);
        assertThat(inLarge.nanosPerDecision())
                .as(
                        "FR-018: ein Mengenzugriff, keine Schleife - %.2f ns bei %d Chunks gegen"
                                + " %.2f ns bei %d",
                        inLarge.nanosPerDecision(),
                        large.size(),
                        inSmall.nanosPerDecision(),
                        small.size())
                .isLessThan(inSmall.nanosPerDecision() * INDEX_TOLERANCE);
    }

    @Test
    @DisplayName("die Entscheidung bekommt die Spieler gar nicht erst in die Hand")
    void theDecisionNeverReceivesThePlayersAtAll() {
        // Der Test, der nicht misst. Eine Messung sagt, dass es heute schnell ist; diese Zeile
        // sagt, dass es nicht anders sein KANN. Sobald jemand shouldRemove eine Spielerliste
        // gibt - und das waere die naheliegendste aller Aenderungen - faellt sie um, und zwar
        // sofort und ohne Zeitmessung. Dieselbe Bauart wie CombatStatusSource in B05.
        Method decision = shouldRemove();

        for (Class<?> parameter : decision.getParameterTypes()) {
            assertThat(Collection.class.isAssignableFrom(parameter))
                    .as(
                            "shouldRemove nimmt keine Sammlung entgegen, sondern den fertigen"
                                    + " Index - hier aber %s",
                            parameter.getName())
                    .isFalse();
            assertThat(Iterable.class.isAssignableFrom(parameter))
                    .as("auch kein Iterable: %s", parameter.getName())
                    .isFalse();
            assertThat(Map.class.isAssignableFrom(parameter))
                    .as("auch keine Karte: %s", parameter.getName())
                    .isFalse();
            assertThat(parameter.isArray())
                    .as("auch kein Feld: %s", parameter.getName())
                    .isFalse();
        }

        assertThat(decision.getParameterTypes())
                .as("und der raeumliche Index ist wirklich dabei - sonst bewacht dieser Test nichts")
                .contains(NearbyChunks.class);
    }

    // --- Messung ---

    private record Measurement(double nanosPerDecision, long sink) {}

    /**
     * Der Preis <em>einer</em> Aufraeumentscheidung, gemittelt ueber gleich viele Entscheidungen -
     * dreizehn Kreaturen werden entsprechend oefter durchgegangen als hundertdreissig.
     */
    private static Measurement measure(List<Long> creatures, NearbyChunks nearby) {
        int passes = DECISIONS_PER_ROUND / creatures.size();
        long sink = 0L;

        for (int round = 0; round < WARMUP_ROUNDS; round++) {
            sink += onePass(creatures, nearby, passes);
        }

        long best = Long.MAX_VALUE;
        for (int round = 0; round < MEASURED_ROUNDS; round++) {
            long start = System.nanoTime();
            sink += onePass(creatures, nearby, passes);
            best = Math.min(best, System.nanoTime() - start);
        }

        assertThat(sink).as("der Compiler darf die Entscheidungen nicht wegoptimieren").isPositive();
        return new Measurement((double) best / (passes * (long) creatures.size()), sink);
    }

    private static long onePass(List<Long> creatures, NearbyChunks nearby, int passes) {
        long removed = 0L;
        for (int pass = 0; pass < passes; pass++) {
            for (int i = 0; i < creatures.size(); i++) {
                if (CleanupRule.shouldRemove(false, creatures.get(i), nearby, false)) {
                    removed++;
                }
            }
        }
        return removed;
    }

    // --- Aufbauten ---

    /**
     * Kreaturen im Wechsel drinnen und draussen - eine Messung, in der jede Entscheidung gleich
     * ausfaellt, misst nur einen Zweig.
     */
    private static List<Long> creatureChunks(int count) {
        List<Long> chunks = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            if (i % 2 == 0) {
                chunks.add(NearbyChunks.pack((i * 7) % 40, (i * 11) % 40));
            } else {
                chunks.add(NearbyChunks.pack(4000 + (i * 13) % 500, 4000 + (i * 17) % 500));
            }
        }
        return chunks;
    }

    /** Spieler auf einem Raster; je mehr, desto weiter reicht der gestempelte Umkreis. */
    private static NearbyChunks stampedFor(int players) {
        NearbyChunks nearby = new NearbyChunks();
        int perSide = (int) Math.ceil(Math.sqrt(players));
        int placed = 0;
        for (int gx = 0; gx < perSide && placed < players; gx++) {
            for (int gz = 0; gz < perSide && placed < players; gz++) {
                nearby.stampAround(gx * 208, gz * 208, 96.0);
                placed++;
            }
        }
        return nearby;
    }

    private static Method shouldRemove() {
        try {
            return CleanupRule.class.getMethod(
                    "shouldRemove", boolean.class, long.class, NearbyChunks.class, boolean.class);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(
                    "CleanupRule.shouldRemove(boolean, long, NearbyChunks, boolean) gibt es nicht"
                            + " mehr - wurde die Aufraeumentscheidung umgebaut, gehoert dieser Test"
                            + " mit umgebaut und nicht geloescht",
                    e);
        }
    }
}
