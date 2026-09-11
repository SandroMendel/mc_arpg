package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.CombatDeathEvent;
import rpg.core.combat.DeathCause;
import rpg.core.event.DefaultEventBus;
import rpg.core.event.EventBus;

/**
 * T079 to T082 - logging out in combat is a death (ADR-030, FR-038 to FR-043, SC-011).
 */
class CombatLogoutDeathTest {

    private static final UUID HOLDER = UUID.randomUUID();
    private static final UUID CHARACTER = UUID.randomUUID();

    /** Records what the store was told, and what the repository would have been told. */
    private static final class RecordingRepository implements ZoneStateRepository {

        private final Map<UUID, String> pending = new HashMap<>();
        private int pendingWrites;

        @Override
        public CompletableFuture<Optional<ZoneCharacterState>> find(UUID characterId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void markSeen(UUID characterId) {}

        @Override
        public void unlock(UUID characterId, String crystalKey) {}

        @Override
        public void setPendingRespawn(UUID characterId, String zoneKey) {
            pendingWrites++;
            if (zoneKey == null) {
                pending.remove(characterId);
            } else {
                pending.put(characterId, zoneKey);
            }
        }
    }

    private static final class Placement implements ZonePresence {

        private String zoneKey = "dustlands";

        @Override
        public boolean inSafeCore(UUID holderId) {
            return false;
        }

        @Override
        public String zoneKeyOf(UUID holderId) {
            return zoneKey;
        }
    }

    private final List<CombatDeathEvent> deaths = new ArrayList<>();
    private final RecordingRepository repository = new RecordingRepository();
    private final Placement placement = new Placement();
    private ZoneStateStore store;
    private boolean inCombat = true;
    private boolean enabled = true;
    private CombatLogoutRule rule;

    @BeforeEach
    void setUp() {
        EventBus bus = new DefaultEventBus(Logger.getLogger("combat-logout-test"));
        bus.subscribe(CombatDeathEvent.class, deaths::add);
        store = new ZoneStateStore(repository);
        store.load(CHARACTER, Optional.of(ZoneCharacterState.empty(CHARACTER)));
        rule =
                new CombatLogoutRule(
                        () -> enabled, holder -> inCombat, placement, store, bus);
    }

    @Test
    @DisplayName("in combat: the character dies, with LOGOUT as the cause (FR-038, FR-040)")
    void inCombatTheCharacterDies() {
        boolean died = rule.onSessionEnding(HOLDER, CHARACTER);

        assertThat(died).isTrue();
        assertThat(deaths).hasSize(1);
        assertThat(deaths.get(0).cause()).isEqualTo(DeathCause.LOGOUT);
        assertThat(deaths.get(0).killer()).as("nobody killed them").isEmpty();
        assertThat(deaths.get(0).victimCharacterId()).isEqualTo(CHARACTER);
        assertThat(store.pendingRespawnOf(CHARACTER)).contains("dustlands");
    }

    @Test
    @DisplayName("past the grace window: nothing happens (FR-039, SC-011)")
    void outsideCombatNothingHappens() {
        inCombat = false;

        assertThat(rule.onSessionEnding(HOLDER, CHARACTER)).isFalse();
        assertThat(deaths).isEmpty();
        assertThat(store.pendingRespawnOf(CHARACTER)).isEmpty();
    }

    @Test
    @DisplayName("combat-logout: none switches the rule off without a code change (FR-042)")
    void theSwitchTurnsItOff() {
        enabled = false;

        assertThat(rule.onSessionEnding(HOLDER, CHARACTER)).isFalse();
        assertThat(deaths).isEmpty();
        assertThat(store.pendingRespawnOf(CHARACTER)).isEmpty();
    }

    @Test
    @DisplayName("a dropped connection is the same as a deliberate quit (FR-043)")
    void aDroppedConnectionIsTheSame() {
        // There is no second entry point and no flag to distinguish the two - the rule sees one
        // call either way. A test can only state that, and it is worth stating: a rule anybody can
        // dodge by pulling a cable is worse than no rule.
        assertThat(rule.onSessionEnding(HOLDER, CHARACTER)).isTrue();
        assertThat(deaths).hasSize(1);
    }

    @Test
    @DisplayName("logging out in the wilderness still dies, and ends at the fallback point")
    void inTheWildernessTheMarkerIsEmpty() {
        placement.zoneKey = null;

        assertThat(rule.onSessionEnding(HOLDER, CHARACTER)).isTrue();
        assertThat(deaths).hasSize(1);
        assertThat(store.pendingRespawnOf(CHARACTER))
                .as("no region to return to - the join falls back (FR-037)")
                .isEmpty();
    }

    @Test
    @DisplayName("the marker is written even when the memory copy is already gone")
    void theMarkerReachesTheRepositoryRegardless() {
        // A session can be half torn down by the time this runs, and this is the one write that
        // must not be lost: it is the whole consequence of the rule.
        store.unload(CHARACTER);

        assertThat(rule.onSessionEnding(HOLDER, CHARACTER)).isTrue();
        assertThat(repository.pending).containsEntry(CHARACTER, "dustlands");
    }

    @Test
    @DisplayName("applying the marker on the next login clears it (FR-041)")
    void applyingClearsTheMarker() {
        rule.onSessionEnding(HOLDER, CHARACTER);
        assertThat(store.pendingRespawnOf(CHARACTER)).contains("dustlands");

        store.clearPendingRespawn(CHARACTER);

        assertThat(store.pendingRespawnOf(CHARACTER)).isEmpty();
        assertThat(repository.pending).doesNotContainKey(CHARACTER);
    }

    @Test
    @DisplayName("T080: there is no second time window - the eight seconds stay B05's (FR-039)")
    void theGraceWindowIsReadNeverCopied() throws java.io.IOException {
        // The rule asks a question and owns no clock. A Duration or a Clock field here would be a
        // second place where "how long is combat" is decided, and the two would drift the first time
        // somebody tuned one of them.
        java.nio.file.Path source =
                repositoryRoot()
                        .resolve("rpg-core/src/main/java/rpg/core/zone/CombatLogoutRule.java");
        String text = java.nio.file.Files.readString(source);

        assertThat(text).doesNotContain("Duration");
        assertThat(text).doesNotContain("Clock");
        assertThat(text)
                .as("it takes the answer, not the ingredients")
                .contains("Predicate<UUID> inCombat");
    }

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

    @Test
    @DisplayName("a marker survives being reloaded from the row")
    void aMarkerSurvivesAReload() {
        ZoneStateStore fresh = new ZoneStateStore(repository);
        fresh.load(
                CHARACTER,
                Optional.of(
                        new ZoneCharacterState(CHARACTER, Set.of(), Optional.of("pale-wilds"))));

        assertThat(fresh.pendingRespawnOf(CHARACTER)).contains("pale-wilds");
    }
}
