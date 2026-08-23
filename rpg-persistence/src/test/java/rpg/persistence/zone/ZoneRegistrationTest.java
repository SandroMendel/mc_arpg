package rpg.persistence.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.AggregateType;

/**
 * T078 - the third registration of ADR-015 point 7, which is the one nothing else guards.
 *
 * <p><b>The first two are already covered, and by tests that are not B09's.</b>
 * {@code NoDatabaseAccessPerGameEventTest} asserts that {@code FlushCycle.WRITE_ORDER} lists every
 * {@link AggregateType} and that a child follows its parent - so forgetting the constant or its place
 * would have turned that test red without anybody writing a line here. It did not, which is the proof
 * those two are done.
 *
 * <p><b>The third has no such net.</b> A repository that is never handed to the flush cycle leaves
 * its marks counted as failed on every flush and never written - which looks exactly like a database
 * problem and is none. B06 lost an afternoon to that shape; the comment in {@code FlushCycle} says
 * so.
 *
 * <p>A source check rather than a started module: starting one needs a database, a scheduler and a
 * session, and the thing worth guarding is a single line that is easy to forget and silent when
 * missing.
 */
class ZoneRegistrationTest {

    @Test
    @DisplayName("registration 1 of 3: the aggregate type exists")
    void theConstantExists() {
        assertThat(AggregateType.valueOf("CHARACTER_ZONE_STATE"))
                .isEqualTo(AggregateType.CHARACTER_ZONE_STATE);
    }

    @Test
    @DisplayName("registration 3 of 3: the module hands the repository to the flush cycle")
    void theRepositoryIsWired() throws IOException {
        String module = source("ZonePersistenceModule.java");

        assertThat(module)
                .as("without this line nothing B09 stores is ever written, and nothing says so")
                .contains("flushCycle().register(AggregateType.CHARACTER_ZONE_STATE");
    }

    @Test
    @DisplayName("the flush reads the live copy, so a stale row cannot overwrite a fresh change")
    void theFlushReadsTheLiveCopy() throws IOException {
        String module = source("ZonePersistenceModule.java");

        assertThat(module).contains("setLiveSource");
    }

    private static String source(String fileName) throws IOException {
        return Files.readString(
                repositoryRoot()
                        .resolve("rpg-persistence/src/main/java/rpg/persistence/zone")
                        .resolve(fileName));
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
