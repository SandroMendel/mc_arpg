package rpg.core.zone;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T017 - the promise that no zone query walks the list of zones (FR-005, Constitution II).
 *
 * <p>A source test is the only form in which "this does <b>not</b> happen here" can be checked. It
 * turns red when somebody breaks the rule, and not when a server with two hundred players does.
 *
 * <p><b>Why a behavioural test cannot carry this.</b> With six regions, a linear scan and an index
 * are indistinguishable - both are instant. The lookup would still be correct while quietly becoming
 * O(zones), and the first thing anybody noticed would be the tick budget on a map that finally had
 * thirty regions. The one measurement that would catch it (T037) proves the current number is fast
 * enough; it cannot prove the shape of the code that produced it.
 */
class ZoneIndexNoIterationTest {

    private static final Path PACKAGE =
            repositoryRoot().resolve("rpg-core/src/main/java/rpg/core/zone");

    @Test
    @DisplayName("the index lookup does not touch the complete zone list")
    void indexLookupDoesNotTouchTheZoneList() throws IOException {
        String lookup = methodBody(source("ChunkZoneIndex.java"), "public Zone zoneAt(");

        assertThat(lookup)
                .as("the query must go through the chunk table, not the list of all zones")
                .doesNotContain("zones)")
                .doesNotContain("zones.")
                .doesNotContain("for (Zone zone : zones");
        assertThat(lookup).as("it goes through the table").contains("table.get(");
    }

    @Test
    @DisplayName("the boundary-chunk guard is a table lookup too")
    void boundaryGuardIsATableLookup() throws IOException {
        String guard = methodBody(source("ChunkZoneIndex.java"), "public boolean isBoundaryChunk(");

        assertThat(guard).contains("table.get(");
        assertThat(guard).doesNotContain("for (");
    }

    @Test
    @DisplayName("the public query delegates to the index and loops over nothing")
    void publicQueryDelegates() throws IOException {
        String text = source("DefaultZones.java");
        String lookup = methodBody(text, "private Zone lookup(");

        assertThat(lookup).as("one delegation, nothing else").contains("index.zoneAt(");
        assertThat(lookup).doesNotContain("for (");
        assertThat(methodBody(text, "public String zoneKeyAt(")).doesNotContain("for (");
        assertThat(methodBody(text, "public boolean inSafeCore(")).doesNotContain("for (");
    }

    @Test
    @DisplayName("iterating all zones happens in the load path only, never in a query")
    void iterationLivesInTheLoadPath() throws IOException {
        // build(...) and the constructor may walk everything - they run once at start and once per
        // reload. The point is that no *query* does.
        assertThat(methodBody(source("ChunkZoneIndex.java"), "public static ChunkZoneIndex build("))
                .as("the load path is where the walking belongs")
                .contains("for (Zone zone : zones)");
    }

    /**
     * The text of one method, from its signature to the matching closing brace.
     *
     * <p>Brace counting rather than a parser: this asserts about a handful of methods in one package,
     * and a dependency on a Java parser for that would be heavier than the thing it checks.
     */
    private static String methodBody(String source, String signatureStart) {
        int at = source.indexOf(signatureStart);
        assertThat(at).as("method not found: " + signatureStart).isNotNegative();
        int open = source.indexOf('{', at);
        int depth = 0;
        for (int i = open; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(open, i + 1);
                }
            }
        }
        throw new AssertionError("unbalanced braces after " + signatureStart);
    }

    private static String source(String fileName) throws IOException {
        return Files.readString(PACKAGE.resolve(fileName));
    }

    private static Path repositoryRoot() {
        Path at = Path.of("").toAbsolutePath();
        while (at != null && !Files.exists(at.resolve("settings.gradle.kts"))) {
            at = at.getParent();
        }
        if (at == null) {
            throw new IllegalStateException("repository root not found from " + Path.of("").toAbsolutePath());
        }
        return at;
    }
}
