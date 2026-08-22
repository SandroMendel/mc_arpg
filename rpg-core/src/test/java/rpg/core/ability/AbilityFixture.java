package rpg.core.ability;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.ability.effect.EffectDispatcher;
import rpg.core.classes.AbilityBinding;
import rpg.core.classes.AbilityKind;
import rpg.core.combat.DamageType;
import rpg.core.message.MessageKey;
import rpg.core.session.CharacterClass;

/**
 * A runtime that can be driven without a server: a controllable clock, a stat engine reduced to what
 * this block reads, a resolver that answers from a list, and a dispatcher that records.
 *
 * <p>Everything is a plain field a test can set. The point is that a rejection is provable - "no mana
 * was spent" is only meaningful if the test can see the mana.
 */
final class AbilityFixture {

    final UUID character = UUID.randomUUID();

    /** Moves only when a test moves it - a cooldown test that slept would take as long as a cooldown. */
    final MovableClock clock = new MovableClock(Instant.parse("2026-08-22T12:00:00Z"));

    final EffectDispatcher dispatcher;

    /** Was der Damage-Effekt gesehen hat. Aufgezeichnet im ECHTEN Dispatcher, damit dessen
     * Fehlerbarriere im Test mitlaeuft statt umgangen zu werden. */
    final List<Applied> applications = new ArrayList<>();

    /** Setzt ein Test, um eine Ausnahme im Effekt zu erzwingen (FR-017). */
    RuntimeException failWith;
    final AbilityRegistry registry;
    final AbilityRuntime runtime;
    final FakeStats stats = new FakeStats();
    final RecordingRepository repository = new RecordingRepository();

    /** What the resolver answers. A test that cares about targeting sets it. */
    List<UUID> resolvedTargets = List.of();

    /** The class the character has, or null to model "no character yet" (ADR-020). */
    CharacterClass characterClass = CharacterClass.WARRIOR;

    /** The abilities the level has unlocked. */
    List<String> unlocked = new ArrayList<>();

    private AbilityFixture(AbilityConfig config, Logger logger) {
        this.dispatcher = new EffectDispatcher(logger);
        this.dispatcher.register(
                rpg.core.ability.EffectType.DAMAGE,
                context -> {
                    if (failWith != null) {
                        throw failWith;
                    }
                    applications.add(
                            new Applied(
                                    context.ability().id(),
                                    List.copyOf(context.targets()),
                                    context.rank(),
                                    context.value()));
                });
        this.registry =
                new AbilityRegistry(
                        config,
                        id -> characterClass,
                        this::bindingsOf,
                        id -> bindingsFor(unlocked),
                        clock);
        this.runtime =
                new AbilityRuntime(
                        registry,
                        stats,
                        (caster, spec) -> resolvedTargets,
                        dispatcher,
                        repository,
                        clock);
    }

    /** A fixture with one active damage ability, unlocked, and a character with full mana. */
    static AbilityFixture withStrike() throws Exception {
        Logger logger = Logger.getLogger(AbilityFixture.class.getName());
        logger.setLevel(Level.OFF);
        Map<String, Object> document = AbilityConfigFixture.valid();
        // Eine ZWEITE aktive Faehigkeit. Ohne sie liesse sich die globale Sperre nicht pruefen: bei
        // derselben Faehigkeit greift der Einzel-Cooldown zuerst, und eine passive wird schon an der
        // Art abgewiesen.
        Map<String, Object> second = AbilityConfigFixture.activeAbility();
        second.put("display-name-key", "ability.probe.dash.name");
        second.put("item", "FEATHER");
        second.put("mana-cost", 5.0);
        AbilityConfigFixture.abilities(document).put("probe.dash", second);

        AbilityFixture fixture = new AbilityFixture(AbilityConfigFixture.bind(document), logger);
        fixture.unlocked.add("probe.strike");
        fixture.unlocked.add("probe.dash");
        fixture.unlocked.add("probe.lifesteal");
        return fixture;
    }

    /** The ability every test in US1 uses: active, 25 mana, 9 s cooldown, one damage effect. */
    Ability strike() {
        return registry.config().require("probe.strike");
    }

    private List<AbilityBinding> bindingsOf(CharacterClass id) {
        return bindingsFor(List.of("probe.strike", "probe.dash", "probe.lifesteal"));
    }

    private List<AbilityBinding> bindingsFor(List<String> ids) {
        List<AbilityBinding> bindings = new ArrayList<>(ids.size());
        for (String id : ids) {
            Ability ability = registry.config().require(id);
            bindings.add(new AbilityBinding(id, ability.kind(), false, 1));
        }
        return bindings;
    }

    // ---- doubles ----------------------------------------------------------------------------------

    /** A clock a test advances by hand. */
    static final class MovableClock extends Clock {
        private Instant now;

        MovableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public Instant instant() {
            return now;
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }
    }

    /** Was ein Effekt beim Anwenden gesehen hat. */
    record Applied(String abilityId, List<UUID> targets, int rank, double value) {}

    /** Counts what was marked, so "no database access per game event" is provable. */
    static final class RecordingRepository implements AbilityStateRepository {
        int marks;

        @Override
        public CompletableFuture<List<AbilityState>> findAll(UUID characterId) {
            return CompletableFuture.completedFuture(List.of());
        }

        @Override
        public void markDirty(UUID characterId) {
            marks++;
        }
    }

    /** Only what B08 actually reads: mana, one attribute and a snapshot. */
    static final class FakeStats implements rpg.core.stats.StatEngine {
        double mana = 100.0;
        double maxMana = 100.0;
        double cooldownReduction;

        private final Map<rpg.core.stats.Attribute, Double> values = new HashMap<>();

        @Override
        public rpg.core.stats.StatSnapshot snapshot(UUID holderId) {
            return new rpg.core.stats.StatSnapshot(
                    new double[rpg.core.stats.Attribute.count()], 1L);
        }

        @Override
        public java.util.Optional<rpg.core.stats.StatSnapshot> findSnapshot(UUID holderId) {
            return java.util.Optional.of(snapshot(holderId));
        }

        @Override
        public double value(UUID holderId, rpg.core.stats.Attribute attribute) {
            if (attribute == rpg.core.stats.Attribute.ABILITY_COOLDOWN) {
                return cooldownReduction;
            }
            return values.getOrDefault(attribute, 0.0);
        }

        @Override
        public rpg.core.stats.ResourceView resources(UUID holderId) {
            return new rpg.core.stats.ResourceView(100.0, 100.0, mana, maxMana);
        }

        @Override
        public double changeMana(UUID holderId, double delta) {
            mana = Math.max(0.0, Math.min(maxMana, mana + delta));
            return mana;
        }

        // --- everything below is unused by B08 and deliberately inert ---

        @Override
        public List<rpg.core.stats.AttributeContribution> contributions(
                UUID holderId, rpg.core.stats.Attribute attribute) {
            return List.of();
        }

        @Override
        public void apply(UUID holderId, rpg.core.stats.ModifierSet set) {}

        @Override
        public void applyAll(UUID holderId, java.util.Collection<rpg.core.stats.ModifierSet> sets) {}

        @Override
        public void remove(UUID holderId, rpg.core.stats.SourceId source) {}

        @Override
        public void removeKind(UUID holderId, rpg.core.stats.SourceKind kind) {}

        @Override
        public UUID createForCharacter(
                UUID playerId, UUID characterId, rpg.core.stats.ResourcePool initial) {
            return playerId;
        }

        @Override
        public UUID createForEntity(UUID entityId) {
            return entityId;
        }

        @Override
        public java.util.Optional<UUID> characterIdOf(UUID holderId) {
            return java.util.Optional.of(holderId);
        }

        @Override
        public void remove(UUID holderId) {}

        @Override
        public rpg.core.stats.StatSnapshot recalculateNow(UUID holderId) {
            return snapshot(holderId);
        }

        @Override
        public double changeHealth(UUID holderId, double delta) {
            return 100.0;
        }

        @Override
        public void restoreResources(UUID holderId, rpg.core.stats.ResourcePool pool) {}

        @Override
        public void registerBaseStatContributor(rpg.core.stats.BaseStatContributor contributor) {}

        @Override
        public boolean unregisterBaseStatContributor(String id) {
            return false;
        }

        @Override
        public void registerVanillaBridge(rpg.core.stats.VanillaAttributeBridge bridge) {}

        @Override
        public int holderCount() {
            return 1;
        }
    }
}
