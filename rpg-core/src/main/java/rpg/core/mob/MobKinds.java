package rpg.core.mob;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;

/**
 * Was eine Kreatur ist — die öffentliche Abfrage dieses Blocks (contracts/mob-api.md §2).
 *
 * <p>Für B11 (Beute je Art), B12 (Bosskills zählen) und B13 (Anzeige).
 *
 * <p><b>Keine Abfrage bricht einen Aufrufer ab.</b> Ein unbekannter Schlüssel antwortet leer und
 * wirft nicht — dieselbe Regel, die B06 für unbekannte Charaktere aufstellt, und aus demselben
 * Grund: mehrere Blöcke hängen an diesen Antworten, und einer davon fragt aus einem Spawn-Ereignis
 * heraus, wo „kenne ich nicht" ein normaler Zustand ist.
 */
public interface MobKinds {

    /** Die Art mit dieser Kennung, oder leer. */
    Optional<MobKind> find(String kindKey);

    /** Die Art, die diese Entität trägt, oder leer für alles, was dieser Block nicht gesetzt hat. */
    Optional<MobKind> ofEntity(UUID entityId);

    /** Alle Arten aus der Konfiguration, unveränderlich und nach dem Laden stabil. */
    List<MobKind> all();

    /**
     * Die Standardumsetzung: die Konfiguration für die Arten, der Bestand für die Zuordnung.
     *
     * <p>Als Fabrik statt als Klasse, weil hier nichts zu tun ist, was ein Name rechtfertigen würde —
     * zwei Nachschläge und eine Liste.
     */
    static MobKinds backedBy(java.util.function.Supplier<MobConfig> config, HordeRegistry registry) {
        java.util.Objects.requireNonNull(config, "config");
        java.util.Objects.requireNonNull(registry, "registry");
        Function<UUID, String> kindOfEntity =
                entityId -> {
                    HordeRegistry.Entry entry = registry.find(entityId);
                    return entry == null ? null : entry.kindKey();
                };
        return new MobKinds() {
            @Override
            public Optional<MobKind> find(String kindKey) {
                return config.get().kind(kindKey);
            }

            @Override
            public Optional<MobKind> ofEntity(UUID entityId) {
                if (entityId == null) {
                    return Optional.empty();
                }
                return find(kindOfEntity.apply(entityId));
            }

            @Override
            public List<MobKind> all() {
                return List.copyOf(config.get().kinds().values());
            }
        };
    }
}
