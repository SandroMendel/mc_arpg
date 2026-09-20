package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigLoader;
import rpg.core.config.ConfigSchema;
import rpg.core.config.ConfigValidationException;
import rpg.core.event.EventBus;
import rpg.core.message.MessageKey;
import rpg.core.message.Messages;
import rpg.core.module.ModuleContext;
import rpg.core.module.ModuleRegistry;
import rpg.core.scheduler.Scheduler;
import rpg.core.zone.SpawnArea;
import rpg.core.zone.Zones;

/**
 * T104: ein Nachladen von {@code mobs.yml} ändert Budgets und Raten, lässt eine bereits stehende
 * Kreatur aber unberührt (contracts/mob-config.md, "Neues gilt für Neues, Laufendes behält, womit
 * es gestartet ist").
 *
 * <p>Nach dem Muster von {@code ProvisionalWarningTest} aus B09: der Weg geht durch {@link
 * MobModule#start} und {@link MobModule#applyReloadedConfig}, nicht an ihnen vorbei - ein
 * {@link ConfigHandle}, dessen Wert sich zwischen beiden Aufrufen ändert, ist genau das, was ein
 * echtes Nachladen liefert.
 */
class MobModuleReloadTest {

    @Test
    @DisplayName("Budget und Nachschubrate wirken sofort, ein bereits gesetzter Bestand bleibt unberührt")
    void budgetAndRateTakeEffectARunningCreatureStays() throws Exception {
        MutableHandleLoader loader =
                new MutableHandleLoader(configWith(Duration.ofSeconds(2), 130));
        MobModule module = new MobModule(Logger.getLogger("test"), alwaysConfiguredMessages(), noAreas());
        module.start(contextFor(loader));

        // Eine Kreatur, wie HordeSweep sie eintragen würde - vor dem Nachladen gesetzt.
        UUID placed = UUID.randomUUID();
        module.registry()
                .add(
                        new HordeRegistry.Entry(
                                placed,
                                "probe.rotling",
                                "greenfields",
                                0L,
                                Instant.now(),
                                HordeRegistry.Origin.BUDGET));

        loader.set(configWith(Duration.ofSeconds(5), 300));
        module.applyReloadedConfig();

        assertThat(module.config().respawnInterval())
                .as("die Nachschubrate ist ab jetzt die neue")
                .isEqualTo(Duration.ofSeconds(5));
        assertThat(module.config().budget().perZone()).as("und das Budget auch").isEqualTo(300);
        assertThat(module.registry().find(placed))
                .as("die schon stehende Kreatur wird nicht umgerechnet oder entfernt")
                .isNotNull();
        assertThat(module.registry().total()).isEqualTo(1);
    }

    @Test
    @DisplayName("ein Nachladen benachrichtigt, wer zuhören wollte - dieselbe Bauart wie ZoneModule")
    void reloadNotifiesListeners() throws Exception {
        MutableHandleLoader loader = new MutableHandleLoader(configWith(Duration.ofSeconds(2), 130));
        MobModule module = new MobModule(Logger.getLogger("test"), alwaysConfiguredMessages(), noAreas());
        module.start(contextFor(loader));
        List<String> told = new ArrayList<>();
        module.onReload(() -> told.add("re-evaluated"));

        loader.set(configWith(Duration.ofSeconds(5), 300));
        module.applyReloadedConfig();

        assertThat(told).containsExactly("re-evaluated");
    }

    // --- fixtures ---

    private static MobConfig configWith(Duration respawnInterval, int perZone) {
        return new MobConfig(
                new Budget(800, perZone, 12, 25),
                respawnInterval,
                0.2,
                Duration.ofSeconds(60),
                96.0,
                Duration.ofMillis(500),
                Map.of(),
                Map.of());
    }

    private static java.util.function.Supplier<Zones> noAreas() {
        return () ->
                new Zones() {
                    @Override
                    public java.util.Optional<rpg.core.zone.Zone> zoneAt(
                            rpg.core.scheduler.WorldPosition position) {
                        throw new UnsupportedOperationException("dieser Test braucht das nicht");
                    }

                    @Override
                    public String zoneKeyAt(rpg.core.scheduler.WorldPosition position) {
                        throw new UnsupportedOperationException("dieser Test braucht das nicht");
                    }

                    @Override
                    public boolean inSafeCore(rpg.core.scheduler.WorldPosition position) {
                        throw new UnsupportedOperationException("dieser Test braucht das nicht");
                    }

                    @Override
                    public boolean isBoundaryChunk(UUID worldId, int blockX, int blockZ) {
                        throw new UnsupportedOperationException("dieser Test braucht das nicht");
                    }

                    @Override
                    public java.util.Optional<rpg.core.zone.CrystalPlacement> crystalAt(
                            rpg.core.scheduler.WorldPosition position) {
                        throw new UnsupportedOperationException("dieser Test braucht das nicht");
                    }

                    @Override
                    public java.util.Optional<rpg.core.zone.CrystalPlacement> crystalByKey(
                            String crystalKey) {
                        throw new UnsupportedOperationException("dieser Test braucht das nicht");
                    }

                    @Override
                    public List<rpg.core.zone.CrystalPlacement> crystals() {
                        throw new UnsupportedOperationException("dieser Test braucht das nicht");
                    }

                    @Override
                    public java.util.Optional<rpg.core.zone.Zone> byKey(String zoneKey) {
                        return java.util.Optional.empty();
                    }

                    @Override
                    public List<rpg.core.zone.Zone> all() {
                        return List.of();
                    }

                    @Override
                    public rpg.core.scheduler.WorldPosition startPoint() {
                        throw new UnsupportedOperationException("dieser Test braucht das nicht");
                    }

                    @Override
                    public java.util.Optional<rpg.core.scheduler.WorldPosition> respawnPointOf(
                            String zoneKey) {
                        throw new UnsupportedOperationException("dieser Test braucht das nicht");
                    }

                    @Override
                    public rpg.core.scheduler.WorldPosition fallbackPoint() {
                        throw new UnsupportedOperationException("dieser Test braucht das nicht");
                    }

                    @Override
                    public List<SpawnArea> spawnAreasOf(String zoneKey) {
                        return List.of();
                    }
                };
    }

    /** Jeder Schluessel ist konfiguriert - mit leeren Arten in diesem Test nur NAMEPLATE noetig. */
    private static Messages alwaysConfiguredMessages() {
        return new Messages() {
            @Override
            public String get(MessageKey key) {
                return "x";
            }

            @Override
            public String get(MessageKey key, Map<String, String> placeholders) {
                return "x";
            }

            @Override
            public boolean contains(MessageKey key) {
                return true;
            }
        };
    }

    private static ModuleContext contextFor(ConfigLoader loader) {
        return new ModuleContext() {
            @Override
            public String moduleId() {
                return MobModule.ID;
            }

            @Override
            public ModuleRegistry registry() {
                throw new UnsupportedOperationException("dieser Block braucht keine Registry");
            }

            @Override
            public EventBus eventBus() {
                throw new UnsupportedOperationException("dieser Block braucht keinen EventBus");
            }

            @Override
            public Scheduler scheduler() {
                throw new UnsupportedOperationException(
                        "dieser Block registriert keine Aufgabe - Prinzip II");
            }

            @Override
            public ConfigLoader configLoader() {
                return loader;
            }
        };
    }

    /** Ein Handle, dessen Wert sich zwischen {@code start} und {@code applyReloadedConfig} ändert. */
    private static final class MutableHandleLoader implements ConfigLoader {

        private final AtomicReference<MobConfig> current;

        MutableHandleLoader(MobConfig initial) {
            this.current = new AtomicReference<>(initial);
        }

        void set(MobConfig next) {
            current.set(next);
        }

        @Override
        public <T> T loadAndValidate(Path source, ConfigSchema<T> schema) {
            throw new UnsupportedOperationException("dieser Test laedt nicht direkt, nur ueber register");
        }

        @Override
        @SuppressWarnings("unchecked")
        public <T> ConfigHandle<T> register(Path source, ConfigSchema<T> schema)
                throws ConfigValidationException {
            return (ConfigHandle<T>)
                    new ConfigHandle<MobConfig>() {
                        @Override
                        public MobConfig get() {
                            return current.get();
                        }

                        @Override
                        public Path source() {
                            return source;
                        }
                    };
        }

        @Override
        public void reloadAll() {
            // In diesem Test aendert sich der Wert direkt ueber set(...), nicht ueber diesen Weg.
        }
    }
}
