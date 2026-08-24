package rpg.core.mob;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Was in einer Zone steht — die zweite öffentliche Abfrage (contracts/mob-api.md §3).
 *
 * <p>Für B12 (Statistik), B14 (ein Betreiber will wissen, was los ist) und B15 (Messung).
 *
 * <p><b>Zahlen und eine Id, nie die Sammlung.</b> Ein anderer Block, der den Bestand in der Hand
 * hielte, könnte eine Kreatur entfernen — und wäre damit eine zweite Stelle, an der das Budget
 * auseinanderläuft. Dieselbe Überlegung, aus der {@code CombatStatusSource} in B05 nur ein Lesen
 * herausgibt und nicht die Stat-Engine.
 *
 * <p><b>Eine unbekannte Zone antwortet mit 0 und nicht mit einer Ausnahme.</b> Dieselbe
 * Entscheidung, die B09 für {@code spawnAreasOf} getroffen hat, und aus demselben Grund: gefragt
 * wird aus einem Spawn-Ereignis heraus, und „außerhalb jeder Region" ist dort ein normaler Zustand.
 */
public interface Hordes {

    /** Wie viele Kreaturen dieses Blocks in dieser Zone stehen. 0 für eine unbekannte Zone. */
    int countIn(String zoneKey);

    /** Wie viele insgesamt — die Zahl, gegen die das serverweite Budget hält. */
    int total();

    /**
     * Der lebende Boss dieser Region, oder leer.
     *
     * <p>Leer heißt auch während des Respawn-Timers leer. Ob er <em>kommen darf</em>, ist Interna
     * dieses Blocks und steht bewusst nicht im Vertrag: es wäre eine Zusage über einen Zeitpunkt,
     * die niemand außerhalb braucht.
     */
    Optional<UUID> bossOf(String zoneKey);

    /** Die Standardumsetzung: der Bestand für die Zahlen, der Bosszustand für den Boss. */
    static Hordes backedBy(HordeRegistry registry, Map<String, BossState> bosses) {
        Objects.requireNonNull(registry, "registry");
        Objects.requireNonNull(bosses, "bosses");
        return new Hordes() {
            @Override
            public int countIn(String zoneKey) {
                return zoneKey == null ? 0 : registry.countIn(zoneKey);
            }

            @Override
            public int total() {
                return registry.total();
            }

            @Override
            public Optional<UUID> bossOf(String zoneKey) {
                if (zoneKey == null) {
                    return Optional.empty();
                }
                BossState state = bosses.get(zoneKey);
                return state == null ? Optional.empty() : state.alive();
            }
        };
    }
}
