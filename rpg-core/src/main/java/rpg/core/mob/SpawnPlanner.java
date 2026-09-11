package rpg.core.mob;

import java.util.Objects;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * WAS wo gesetzt werden soll - ohne Bukkit, testbar (FR-011, FR-012, FR-013, FR-014, FR-015,
 * FR-016).
 *
 * <p><b>Hoechstens eine Entscheidung je Aufruf.</b> Das ist die Obergrenze aus FR-014: die
 * Spawn-Berechnung wird ueber mehrere Ticks verteilt statt in einem gebuendelt. Ein Zonendurchlauf,
 * der 130 Kreaturen fehlen, plant trotzdem nur eine - die naechste kommt beim naechsten Durchlauf.
 * Bei einem Nachschub von einer Kreatur je zwei Sekunden je Zone fuellt sich eine geraeumte Horde
 * damit in gut vier Minuten (research.md R7) - kein gebuendelter Sprung, sondern ein stetiges
 * Auffuellen.
 *
 * <p><b>Die Zieldichte dieser Geschichte ist die Zonengrenze selbst.</b> US4 fuehrt spaeter eine
 * kleinere, spielerabhaengige Zieldichte ein ({@code DensityScaling}); bis dahin ist die Obergrenze
 * aus {@link Budget#ceilingFor} die einzige Zahl, gegen die geplant wird.
 *
 * <p><b>Das Chunk-Budget entscheidet hier noch nicht mit.</b> Welcher Chunk am Ende getroffen wird,
 * steht erst fest, wenn die Plattformschicht einen Ort im gewaehlten Bereich gewuerfelt hat - das
 * ist Bukkit-Boden und liegt ausserhalb dieser Klasse. Die schaerfste Grenze entscheidet trotzdem:
 * der letzte Schritt vor dem tatsaechlichen Setzen prueft den Chunk noch einmal, und ein volles
 * Chunk-Budget verwirft die Entscheidung, ohne dass der Bestand etwas verbucht.
 */
public final class SpawnPlanner {

    private SpawnPlanner() {}

    /** Bereich und Art - was ein Durchlauf mit dieser Entscheidung setzen soll. */
    public record Decision(String areaKey, String kindKey) {

        public Decision {
            Objects.requireNonNull(areaKey, "areaKey");
            Objects.requireNonNull(kindKey, "kindKey");
        }
    }

    /**
     * Ob und was in dieser Zone jetzt geplant werden soll.
     *
     * @param horde was in der Zone steht, oder leer - ein unbekannter Zonenschluessel und eine
     *     leere Bereichsliste sind ein normaler Zustand (FR-016)
     * @param budget die vier Grenzen
     * @param serverTotalElsewhere wie viele Kreaturen dieser Block ausserhalb dieser Zone haelt -
     *     genau das Mass, gegen das {@link Budget#ceilingFor} das serverweite Budget rechnet
     * @param zoneTotal wie viele in dieser Zone stehen
     * @param playersInZone wie viele Spieler da sind - null oder weniger heisst: nichts (FR-015)
     * @param random Quelle fuer die Gewichtsauswahl
     * @return eine Entscheidung, oder leer, wenn diese Zone jetzt nichts bekommen soll
     */
    public static Optional<Decision> plan(
            Optional<HordeSpec> horde,
            Budget budget,
            int serverTotalElsewhere,
            int zoneTotal,
            int playersInZone,
            RandomGenerator random) {
        Objects.requireNonNull(horde, "horde");
        Objects.requireNonNull(budget, "budget");
        Objects.requireNonNull(random, "random");
        if (playersInZone <= 0) {
            // FR-015, geprueft als Erstes: eine Zone ohne Spieler bekommt nichts, und es lohnt
            // sich nicht, vorher irgendetwas ueber die Horde oder das Budget nachzusehen.
            return Optional.empty();
        }
        if (horde.isEmpty()) {
            // FR-016: kein Fehler, keine Ausnahme - nur nichts zu tun.
            return Optional.empty();
        }
        int ceiling = budget.ceilingFor(playersInZone, serverTotalElsewhere);
        if (zoneTotal >= ceiling) {
            return Optional.empty();
        }
        return Optional.of(pickEntry(horde.get(), random));
    }

    private static Decision pickEntry(HordeSpec horde, RandomGenerator random) {
        int roll = random.nextInt(horde.totalWeight());
        int cumulative = 0;
        for (HordeSpec.Entry entry : horde.entries()) {
            cumulative += entry.weight();
            if (roll < cumulative) {
                return new Decision(entry.areaKey(), entry.kindKey());
            }
        }
        // Unerreichbar, wenn totalWeight() korrekt summiert - der letzte Eintrag als Netz statt
        // einer Ausnahme im Spawn-Pfad (Prinzip VI).
        HordeSpec.Entry last = horde.entries().get(horde.entries().size() - 1);
        return new Decision(last.areaKey(), last.kindKey());
    }
}
