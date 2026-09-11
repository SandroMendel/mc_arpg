package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T093 - Freischaltungen haengen am Charakter, wachsen und werden nie entzogen (FR-051a, FR-051b2).
 *
 * <p>Die dritte Regel ist die, die man nicht testen, sondern nur bewachen kann: es gibt keinen Weg,
 * eine Freischaltung aus dem Code zu entfernen. Der letzte Test hier prueft genau das - nicht ein
 * Verhalten, sondern die Abwesenheit einer Methode.
 */
class WaypointUnlockTest {

    private static final UUID WARRIOR = UUID.randomUUID();
    private static final UUID MAGE = UUID.randomUUID();

    /** Records what the store passed on, so a test can tell a write from a no-op. */
    private static final class CountingRepository implements ZoneStateRepository {

        private final List<String> unlocks = new ArrayList<>();

        @Override
        public CompletableFuture<Optional<ZoneCharacterState>> find(UUID characterId) {
            return CompletableFuture.completedFuture(Optional.empty());
        }

        @Override
        public void markSeen(UUID characterId) {}

        @Override
        public void unlock(UUID characterId, String crystalKey) {
            unlocks.add(characterId + "/" + crystalKey);
        }

        @Override
        public void setPendingRespawn(UUID characterId, String zoneKey) {}
    }

    private final CountingRepository repository = new CountingRepository();

    private ZoneStateStore loadedStore() {
        ZoneStateStore store = new ZoneStateStore(repository);
        store.load(WARRIOR, Optional.of(ZoneCharacterState.empty(WARRIOR)));
        store.load(MAGE, Optional.of(ZoneCharacterState.empty(MAGE)));
        return store;
    }

    @Test
    @DisplayName("je Charakter, nie je Account (FR-051a, ADR-011)")
    void unlocksBelongToTheCharacter() {
        ZoneStateStore store = loadedStore();

        store.unlock(WARRIOR, "darkforest-crystal");

        assertThat(store.isUnlocked(WARRIOR, "darkforest-crystal")).isTrue();
        assertThat(store.isUnlocked(MAGE, "darkforest-crystal"))
                .as("wer mit dem Krieger gelaufen ist, faengt mit dem Magier neu an")
                .isFalse();
        assertThat(store.unlockedBy(MAGE)).isEmpty();
    }

    @Test
    @DisplayName("ein zweiter Rechtsklick aendert nichts und schreibt nichts")
    void unlockingTwiceIsIdempotent() {
        ZoneStateStore store = loadedStore();

        assertThat(store.unlock(WARRIOR, "dustlands-crystal"))
                .as("das erste Mal ist eine Entdeckung und wird gemeldet")
                .isTrue();
        assertThat(store.unlock(WARRIOR, "dustlands-crystal"))
                .as("das zweite Mal ist keine - sonst jubelt der Kristall bei jedem Klick")
                .isFalse();

        assertThat(store.unlockedBy(WARRIOR)).containsExactly("dustlands-crystal");
        assertThat(repository.unlocks)
                .as("und es entsteht auch keine zweite Schreibmarkierung")
                .hasSize(1);
    }

    @Test
    @DisplayName("Freischaltungen aus der Datenbank sind sofort da")
    void storedUnlocksSurviveTheLoad() {
        ZoneStateStore store = new ZoneStateStore(repository);
        store.load(
                WARRIOR,
                Optional.of(
                        new ZoneCharacterState(
                                WARRIOR,
                                Set.of("greenfields-crystal", "pale-wilds-crystal"),
                                Optional.empty())));

        assertThat(store.unlockedBy(WARRIOR))
                .containsExactlyInAnyOrder("greenfields-crystal", "pale-wilds-crystal");
    }

    @Test
    @DisplayName("ein nicht geladener Charakter antwortet 'nichts' statt zu blockieren")
    void anUnloadedCharacterAnswersNothing() {
        ZoneStateStore store = new ZoneStateStore(repository);

        assertThat(store.isUnlocked(WARRIOR, "greenfields-crystal")).isFalse();
        assertThat(store.unlockedBy(WARRIOR)).isEmpty();
        assertThat(store.unlock(WARRIOR, "greenfields-crystal"))
                .as("ein Klick eine Tick vor dem Laden tut nichts; der naechste wirkt")
                .isFalse();
        assertThat(repository.unlocks).isEmpty();
    }

    @Test
    @DisplayName("die Liste, die das Fenster bekommt, ist eine Kopie")
    void theWindowGetsACopy() {
        ZoneStateStore store = loadedStore();
        store.unlock(WARRIOR, "greenfields-crystal");

        Set<String> handedOut = store.unlockedBy(WARRIOR);
        store.unlock(WARRIOR, "dustlands-crystal");

        assertThat(handedOut)
                .as("sonst waechst dem Fenster der Inhalt unter der Hand")
                .containsExactly("greenfields-crystal");
    }

    @Test
    @DisplayName("es gibt keinen Weg, eine Freischaltung zu entziehen (FR-051b2)")
    void thereIsNoWayToRevokeOne() throws Exception {
        Path zonePackage = repositoryRoot().resolve("rpg-core/src/main/java/rpg/core/zone");
        // Ohne Kommentare: beide Dateien erklaeren in ihrem Javadoc, dass es das Entziehen nicht
        // gibt. Gesucht wird ausfuehrbarer Bezug, nicht die Begruendung.
        String waypoints =
                TravelFixture.codeOnly(Files.readString(zonePackage.resolve("Waypoints.java")));
        String store =
                TravelFixture.codeOnly(
                        Files.readString(zonePackage.resolve("ZoneStateStore.java")));

        // Nicht "es wird nicht aufgerufen", sondern "es existiert nicht". Das Einzige, was eine
        // Freischaltung beendet, ist der Charakter selbst - und das passiert per ON DELETE CASCADE
        // in der Datenbank, nicht hier.
        assertThat(waypoints).doesNotContain("revoke").doesNotContain("relock");
        assertThat(store).doesNotContain("revoke").doesNotContain("relock");
        assertThat(store)
                .as("und auch nicht ueber die Hintertuer")
                .doesNotContain("unlocked.remove")
                .doesNotContain("unlocked.clear");
    }

    private static Path repositoryRoot() {
        Path at = Path.of("").toAbsolutePath();
        while (at != null && !Files.exists(at.resolve("settings.gradle.kts"))) {
            at = at.getParent();
        }
        if (at == null) {
            throw new IllegalStateException("repository root not found");
        }
        return at;
    }
}
