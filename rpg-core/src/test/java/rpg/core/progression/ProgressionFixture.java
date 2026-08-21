package rpg.core.progression;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;
import rpg.core.persistence.AuditEntry;
import rpg.core.persistence.AuditLogRepository;
import rpg.core.scheduler.EntityRef;
import rpg.core.scheduler.Scheduler;
import rpg.core.scheduler.TaskHandle;
import rpg.core.scheduler.WorldPosition;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionRegistry;
import rpg.core.stats.Attribute;
import rpg.core.stats.DefaultStatEngine;
import rpg.core.stats.ModifierSet;
import rpg.core.stats.ResourcePool;
import rpg.core.stats.SourceId;
import rpg.core.stats.SourceKind;
import rpg.core.stats.StatConfig;
import rpg.core.stats.StatModifier;

/**
 * Shared test environment for B06.
 *
 * <p><b>The clock is steered, not real.</b> Invite expiry and the bundling window are timestamp
 * based, and tests that actually waited would be slow and flaky. Same reason B05 does it.
 *
 * <p><b>The stat engine is the real one.</b> A stub would let a wrong contribution pass unnoticed -
 * and the whole point of ADR-013 is what {@code StatCalculator} does with a base contribution, which
 * only the real implementation can show.
 *
 * <p><b>The repository counts.</b> Several promises are about a number staying at zero, so counting
 * is the test, not a convenience.
 */
final class ProgressionFixture {

    static final class TestClock extends Clock {

        private Instant now = Instant.parse("2026-08-20T12:00:00Z");

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        void advanceMillis(long millis) {
            advance(Duration.ofMillis(millis));
        }
    }

    static final class CountingScheduler implements Scheduler {

        int scheduled;

        @Override
        public TaskHandle runSyncAtLocation(WorldPosition position, Runnable task) {
            return record(task);
        }

        @Override
        public TaskHandle runSyncOnEntity(EntityRef entity, Runnable task) {
            return record(task);
        }

        @Override
        public TaskHandle runAsync(Runnable task) {
            return record(task);
        }

        @Override
        public TaskHandle runAsyncDelayed(Duration delay, Runnable task) {
            return record(task);
        }

        private TaskHandle record(Runnable task) {
            scheduled++;
            task.run();
            return new TaskHandle() {
                @Override
                public void cancel() {}

                @Override
                public boolean isCancelled() {
                    return false;
                }
            };
        }
    }

    /** Which players count as ready. Nothing else of the session lifecycle is needed here. */
    static final class TestSessions implements SessionRegistry {

        private final Set<UUID> ready = new HashSet<>();

        void markReady(UUID playerId) {
            ready.add(playerId);
        }

        void markNotReady(UUID playerId) {
            ready.remove(playerId);
        }

        @Override
        public Optional<PlayerSession> find(UUID playerId) {
            return Optional.empty();
        }

        @Override
        public PlayerSession require(UUID playerId) {
            throw new UnsupportedOperationException("not needed in these tests");
        }

        @Override
        public boolean isReady(UUID playerId) {
            return ready.contains(playerId);
        }

        @Override
        public int activeSessionCount() {
            return ready.size();
        }
    }

    /**
     * Counts marks and, above all, database reads.
     *
     * <p>{@code reads} is the assertion behind SC-004: a thousand gains must not produce a single
     * one.
     */
    static final class CountingProgressRepository implements CharacterProgressRepository {

        final Map<UUID, Integer> marks = new ConcurrentHashMap<>();
        int reads;

        /** Makes the next mark blow up, so the fault barrier can be shown to hold. */
        boolean failNextMark;

        @Override
        public CompletableFuture<Optional<CharacterProgress>> find(UUID characterId) {
            reads++;
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void markDirty(UUID characterId) {
            if (failNextMark) {
                failNextMark = false;
                throw new IllegalStateException("write-behind buffer is unhappy");
            }
            marks.merge(characterId, 1, Integer::sum);
        }

        int marksFor(UUID characterId) {
            return marks.getOrDefault(characterId, 0);
        }

        int totalMarks() {
            return marks.values().stream().mapToInt(Integer::intValue).sum();
        }
    }

    static final class RecordingAuditLog implements AuditLogRepository {

        final List<AuditEntry> entries = new ArrayList<>();

        @Override
        public void append(AuditEntry entry) {
            entries.add(entry);
        }

        @Override
        public CompletableFuture<List<AuditEntry>> between(Instant from, Instant to) {
            return CompletableFuture.completedFuture(List.copyOf(entries));
        }
    }

    final TestClock clock = new TestClock();
    final CountingScheduler scheduler = new CountingScheduler();
    final TestSessions sessions = new TestSessions();
    final CountingProgressRepository repository = new CountingProgressRepository();
    final RecordingAuditLog auditLog = new RecordingAuditLog();
    final EventBus eventBus;
    final DefaultStatEngine stats;
    final DefaultProgression progression;
    final ProgressionConfig config;

    final List<LevelUpEvent> levelUps = new ArrayList<>();

    /**
     * Default curve: levels 2 to 10, thresholds 100, 120, 140 and so on.
     *
     * <p>Deliberately <b>not</b> {@link CurveFixture#twoLevels()}, even though the arithmetic
     * examples only mention 100 and 120. With a maximum at level 3 a gain of 250 correctly ends on
     * the ceiling and throws the remainder away, which is the max-level rule rather than the
     * remainder rule - two different promises, and mixing them hides both.
     */
    ProgressionFixture() {
        this(config(CurveFixture.valid()));
    }

    ProgressionFixture(ProgressionConfig config) {
        Logger logger = Logger.getLogger(ProgressionFixture.class.getName());
        logger.setLevel(Level.OFF);
        this.config = config;
        this.eventBus = new DefaultEventBus(logger);
        this.stats = new DefaultStatEngine(StatConfig.defaults(), scheduler, eventBus, null, logger);
        this.progression =
                new DefaultProgression(
                        config,
                        stats,
                        eventBus,
                        sessions,
                        repository,
                        auditLog,
                        clock,
                        logger);
        this.stats.registerBaseStatContributor(
                new LevelStatContributor(config.growth(), progression::levelOrZero));
        eventBus.subscribe(LevelUpEvent.class, levelUps::add);
    }

    /** A configuration around a given curve, with the growth used by the arithmetic examples. */
    static ProgressionConfig config(Map<Integer, Long> curve) {
        return config(curve, growth(8.0, 2.0, 4.0, 1.5, 1.5));
    }

    static ProgressionConfig config(Map<Integer, Long> curve, LevelGrowth growth) {
        return new ProgressionConfig(
                XpCurve.of(curve),
                growth,
                10L,
                Map.of("ZOMBIE", 12L, "CREEPER", 18L),
                5,
                50.0,
                0.10,
                0.40,
                Duration.ofSeconds(60),
                Duration.ofMillis(500));
    }

    /** Growth for the five attributes that grow; the other three stay at zero (FR-022b). */
    static LevelGrowth growth(
            double health,
            double defense,
            double mana,
            double physicalDamage,
            double magicDamage) {
        double[] values = new double[Attribute.count()];
        values[Attribute.HEALTH.ordinal()] = health;
        values[Attribute.DEFENSE.ordinal()] = defense;
        values[Attribute.MANA.ordinal()] = mana;
        values[Attribute.PHYSICAL_DAMAGE.ordinal()] = physicalDamage;
        values[Attribute.MAGIC_DAMAGE.ordinal()] = magicDamage;
        return LevelGrowth.of(values);
    }

    /** A ready character on level 1, its holder created and filled to its maxima. */
    UUID character() {
        return character(ProgressState.INITIAL);
    }

    /** A ready character in a given state. Returns the character id; the player id mirrors it. */
    UUID character(ProgressState state) {
        UUID playerId = UUID.randomUUID();
        UUID characterId = UUID.randomUUID();
        sessions.markReady(playerId);
        stats.createForCharacter(playerId, characterId, new ResourcePool(0.0, 0.0));
        progression.load(characterId, playerId, state);
        players.put(characterId, playerId);
        fillToMax(playerId);
        clearRecorded();
        return characterId;
    }

    final Map<UUID, UUID> players = new ConcurrentHashMap<>();

    UUID playerOf(UUID characterId) {
        return players.get(characterId);
    }

    /** Equipment-style contribution, so a test can show the band moving with the level. */
    void equip(UUID characterId, Attribute attribute, double flat) {
        UUID playerId = playerOf(characterId);
        stats.apply(
                playerId,
                ModifierSet.of(
                        SourceId.of(SourceKind.EQUIPMENT, "test-gear"),
                        StatModifier.flat(attribute, flat)));
        stats.recalculateNow(playerId);
    }

    void fillToMax(UUID playerId) {
        var snapshot = stats.snapshot(playerId);
        stats.restoreResources(
                playerId,
                ResourcePool.full(snapshot.get(Attribute.HEALTH), snapshot.get(Attribute.MANA)));
    }

    double attribute(UUID characterId, Attribute attribute) {
        return stats.value(playerOf(characterId), attribute);
    }

    double health(UUID characterId) {
        return stats.resources(playerOf(characterId)).currentHealth();
    }

    double maxHealth(UUID characterId) {
        return stats.resources(playerOf(characterId)).maxHealth();
    }

    double mana(UUID characterId) {
        return stats.resources(playerOf(characterId)).currentMana();
    }

    void setHealth(UUID characterId, double value) {
        UUID playerId = playerOf(characterId);
        var view = stats.resources(playerId);
        stats.restoreResources(playerId, new ResourcePool(value, view.currentMana()));
    }

    void clearRecorded() {
        levelUps.clear();
        repository.marks.clear();
        repository.reads = 0;
        scheduler.scheduled = 0;
        auditLog.entries.clear();
    }
}
