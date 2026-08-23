package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.event.DefaultEventBus;
import rpg.core.scheduler.WorldPosition;

/**
 * T129 - keine Bewegung, kein Zonenwechsel und keine Warnung erzeugt einen Datenbankzugriff
 * (FR-063, Prinzip II).
 *
 * <p>Bewegung ist das haeufigste Ereignis eines Servers - haeufiger als Schaden, haeufiger als
 * Erfahrung. Ein Lesezugriff je Schritt waere bei zweihundert Spielern nicht langsam, sondern
 * unbenutzbar. Die Zusage ist deshalb hart formuliert: die Zahl bleibt <b>null</b>, nicht "klein".
 *
 * <p>Gezaehlt werden Lesezugriffe. Schreibmarkierungen sind etwas anderes und ausdruecklich erlaubt -
 * sie landen in B02s Puffer und werden spaeter gebuendelt geschrieben. Eine Freischaltung darf also
 * markieren; ein Schritt darf gar nichts.
 */
class NoDatabaseInGameplayPathTest {

    private static final UUID HOLDER = UUID.randomUUID();
    private static final UUID CHARACTER = UUID.randomUUID();

    /** Zaehlt jeden Lesezugriff und jede Schreibmarkierung getrennt. */
    private static final class CountingRepository implements ZoneStateRepository {

        final AtomicInteger reads = new AtomicInteger();
        final AtomicInteger marks = new AtomicInteger();

        @Override
        public CompletableFuture<Optional<ZoneCharacterState>> find(UUID characterId) {
            reads.incrementAndGet();
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void markSeen(UUID characterId) {
            marks.incrementAndGet();
        }

        @Override
        public void unlock(UUID characterId, String crystalKey) {
            marks.incrementAndGet();
        }

        @Override
        public void setPendingRespawn(UUID characterId, String zoneKey) {
            marks.incrementAndGet();
        }
    }

    @Test
    @DisplayName("zehntausend Schritte quer durch alle Regionen erzeugen null Lesezugriffe")
    void tenThousandStepsReadNothing() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        CountingRepository repository = new CountingRepository();
        ZoneStateStore store = new ZoneStateStore(repository);
        store.load(CHARACTER, Optional.of(ZoneCharacterState.empty(CHARACTER)));
        ZoneTracker tracker =
                new ZoneTracker(() -> zones, new DefaultEventBus(Logger.getLogger("no-db-test")), Logger.getLogger("no-db-test"));

        for (int step = 0; step < 10_000; step++) {
            int x = step % 3500;
            tracker.place(HOLDER, CHARACTER, new WorldPosition(ZoneFixture.WORLD, x + 0.5d, 65.0d, 0.5d));
        }

        assertThat(repository.reads).hasValue(0);
        assertThat(repository.marks)
                .as("und auch keine Schreibmarkierung - ein Schritt ist kein Zustand")
                .hasValue(0);
    }

    @Test
    @DisplayName("hundert Zonenwechsel mit Warnung erzeugen null Lesezugriffe")
    void ahundredZoneChangesReadNothing() throws Exception {
        Zones zones = TravelFixture.load(ZoneFixture.document());
        CountingRepository repository = new CountingRepository();
        ZoneStateStore store = new ZoneStateStore(repository);
        store.load(CHARACTER, Optional.of(ZoneCharacterState.empty(CHARACTER)));
        DefaultEventBus bus = new DefaultEventBus(Logger.getLogger("no-db-test"));
        AtomicInteger warnings = new AtomicInteger();
        LevelBandGuard guard =
                new LevelBandGuard(
                        () -> zones,
                        characterId -> java.util.OptionalInt.of(1),
                        (characterId, key, placeholders) -> warnings.incrementAndGet(),
                        java.time.Clock.systemUTC(),
                        () -> java.time.Duration.ZERO);
        bus.subscribe(ZoneChangedEvent.class, guard::onZoneChanged);
        ZoneTracker tracker = new ZoneTracker(() -> zones, bus, Logger.getLogger("no-db-test"));

        for (int change = 0; change < 100; change++) {
            // Zwischen Greenfields (Band 1-10, keine Warnung) und Pale Wilds (51-60, Warnung).
            tracker.place(HOLDER, CHARACTER, new WorldPosition(ZoneFixture.WORLD, 0.5d, 65.0d, 0.5d));
            tracker.place(HOLDER, CHARACTER, new WorldPosition(ZoneFixture.WORLD, 3000.5d, 65.0d, 0.5d));
        }

        assertThat(warnings.get()).as("die Warnungen sind wirklich gelaufen").isPositive();
        assertThat(repository.reads).hasValue(0);
        assertThat(repository.marks).hasValue(0);
    }

    @Test
    @DisplayName("die Abfrage 'ist freigeschaltet' liest nie nach - sie antwortet aus dem Speicher")
    void theunlockCheckAnswersFromMemory() {
        CountingRepository repository = new CountingRepository();
        ZoneStateStore store = new ZoneStateStore(repository);
        store.load(CHARACTER, Optional.of(ZoneCharacterState.empty(CHARACTER)));

        for (int click = 0; click < 1_000; click++) {
            store.isUnlocked(CHARACTER, "greenfields-crystal");
        }

        assertThat(repository.reads).hasValue(0);
    }

    @Test
    @DisplayName("eine Freischaltung markiert genau einmal - und liest trotzdem nicht")
    void anunlockMarksOnceAndReadsNever() {
        CountingRepository repository = new CountingRepository();
        ZoneStateStore store = new ZoneStateStore(repository);
        store.load(CHARACTER, Optional.of(ZoneCharacterState.empty(CHARACTER)));

        for (int click = 0; click < 50; click++) {
            store.unlock(CHARACTER, "greenfields-crystal");
        }

        assertThat(repository.marks)
                .as("die erste Entdeckung schreibt, die neunundvierzig Wiederholungen nicht")
                .hasValue(1);
        assertThat(repository.reads).hasValue(0);
    }

    // Es gab hier einen vierten Test: "keine Quelle des Domaenenpakets kennt einen Datenbanktyp".
    // Er ist wieder verschwunden, weil rpg-persistence/NoDirectDatabaseAccessTest genau das schon
    // fuer das ganze Projekt prueft - und zwar strenger. Mein Test ist ihm sogar selbst aufgefallen:
    // er trug den verbotenen Typnamen als Suchtext im Quelltext und wurde dafuer zu Recht gemeldet.

    private static java.nio.file.Path repositoryRoot() {
        java.nio.file.Path at = java.nio.file.Path.of("").toAbsolutePath();
        while (at != null && !java.nio.file.Files.exists(at.resolve("settings.gradle.kts"))) {
            at = at.getParent();
        }
        if (at == null) {
            throw new IllegalStateException("repository root not found");
        }
        return at;
    }
}
