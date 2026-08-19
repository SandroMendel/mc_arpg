package rpg.platform.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * T076: only this package may handle the events that start or end a session.
 *
 * <p>The failure this prevents is not hypothetical and does not look like a mistake when it is
 * introduced. A later block wants to know when a player joins - say to hand out a starting item -
 * and adds its own {@code PlayerJoinEvent} handler that reads or writes session data. Now there are
 * two paths into the same lifecycle, and the second one has none of the guards the first one has:
 * no readiness check, no ordering against the load, no single unload. Every symptom of that shows up
 * as data loss weeks later, attributed to anything but a second listener.
 *
 * <p>Blocks that need to react to a join subscribe to B01's event bus, which fires <em>after</em>
 * the session is ready and has no way to interfere with the lifecycle itself.
 *
 * <p>The scan reads sources rather than classes, so it covers modules this one does not depend on -
 * the point is to catch a block that does not exist yet.
 */
class NoCompetingSessionListenersTest {

    /** The three events that begin or end a session. */
    private static final List<String> LIFECYCLE_EVENTS =
            List.of(
                    "AsyncPlayerPreLoginEvent",
                    "PlayerJoinEvent",
                    "PlayerQuitEvent",
                    "PlayerConnectionCloseEvent",
                    // A separate kick handler is the classic duplicate unload (FR-014).
                    "PlayerKickEvent");

    /** A handler method: an {@code @EventHandler} annotation followed by its parameter type. */
    private static final Pattern HANDLER =
            Pattern.compile(
                    "@EventHandler[^)]*\\)?\\s*(?:public\\s+)?void\\s+\\w+\\s*\\(\\s*(\\w+)",
                    Pattern.DOTALL);

    /** The one package allowed to own these handlers. */
    private static final String OWNING_PACKAGE = "/rpg/platform/session/";

    /**
     * The single documented exception, and the reason it is one.
     *
     * <p>B01's {@code PreJoinGuard} refuses connections while the bootstrap is still running. It
     * touches no session and holds no reference to the lifecycle - it only decides whether a
     * connection is allowed at all, which is why it sits at {@code LOWEST} and this block's loader
     * sits at {@code LOW} behind it.
     *
     * <p>This list is meant to stay one entry long. Adding to it is a deliberate act that has to be
     * argued for in review; that is the entire value of it being a list rather than a broad rule.
     */
    private static final List<String> DOCUMENTED_EXCEPTIONS = List.of("PreJoinGuard.java");

    /** This test names the events it guards, so it must not scan itself. */
    private static final String SELF = "NoCompetingSessionListenersTest.java";

    @Test
    void noModuleOutsideThisPackageHandlesASessionLifecycleEvent() throws IOException {
        List<String> violations = new ArrayList<>();

        for (Path source : javaSources()) {
            String name = source.getFileName().toString();
            String path = source.toString().replace('\\', '/');
            if (name.equals(SELF)
                    || path.contains(OWNING_PACKAGE)
                    || DOCUMENTED_EXCEPTIONS.contains(name)) {
                continue;
            }
            Matcher matcher = HANDLER.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                String eventType = matcher.group(1);
                if (LIFECYCLE_EVENTS.contains(eventType)) {
                    violations.add(path + " handles " + eventType);
                }
            }
        }

        assertThat(violations)
                .as(
                        "FR-007/FR-014: the session lifecycle has exactly one entry and one exit."
                                + " To react to a join or quit, subscribe to B01's event bus, which"
                                + " fires once the session is ready.")
                .isEmpty();
    }

    @Test
    void theOwningPackageDoesHandleThemSoTheScanIsNotVacuouslyGreen() throws IOException {
        // Without this, deleting every listener in the project would make the test above pass.
        List<String> handled = new ArrayList<>();

        for (Path source : javaSources()) {
            String path = source.toString().replace('\\', '/');
            if (!path.contains(OWNING_PACKAGE) || path.contains("/test/")) {
                continue;
            }
            Matcher matcher = HANDLER.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                handled.add(matcher.group(1));
            }
        }

        assertThat(handled)
                .contains(
                        "AsyncPlayerPreLoginEvent",
                        "PlayerJoinEvent",
                        "PlayerQuitEvent",
                        "PlayerConnectionCloseEvent");
    }

    @Test
    void thereIsExactlyOneHandlerPerLifecycleEvent() throws IOException {
        // Two handlers for the same event inside this package would produce the duplicate unload
        // just as reliably as one in another module.
        List<String> handled = new ArrayList<>();

        for (Path source : javaSources()) {
            String path = source.toString().replace('\\', '/');
            if (!path.contains(OWNING_PACKAGE) || path.contains("/test/")) {
                continue;
            }
            Matcher matcher = HANDLER.matcher(Files.readString(source, StandardCharsets.UTF_8));
            while (matcher.find()) {
                if (LIFECYCLE_EVENTS.contains(matcher.group(1))) {
                    handled.add(matcher.group(1));
                }
            }
        }

        assertThat(handled).doesNotHaveDuplicates();
        // And no kick handler at all: PlayerQuitEvent already fires for a kick.
        assertThat(handled).doesNotContain("PlayerKickEvent");
    }

    @Test
    void theScanWouldCatchAViolation() {
        String offending =
                """
                @EventHandler(priority = EventPriority.HIGH)
                public void onJoin(PlayerJoinEvent event) {
                    grantStartingItem(event.getPlayer());
                }
                """;

        Matcher matcher = HANDLER.matcher(offending);

        assertThat(matcher.find()).isTrue();
        assertThat(matcher.group(1)).isEqualTo("PlayerJoinEvent");
        assertThat(LIFECYCLE_EVENTS).contains(matcher.group(1));
    }

    @Test
    void theScanActuallyReachesTheProjectSources() throws IOException {
        assertThat(javaSources()).hasSizeGreaterThan(20);
    }

    @Test
    void theOnlyDocumentedExceptionIsTheBootstrapGuardAndItTouchesNoSession() throws IOException {
        // An exception that grows quietly is the same as no rule. If this ever needs a second
        // entry, that is a design conversation, not a test edit.
        assertThat(DOCUMENTED_EXCEPTIONS).containsExactly("PreJoinGuard.java");

        Path guard =
                javaSources().stream()
                        .filter(path -> path.getFileName().toString().equals("PreJoinGuard.java"))
                        .findFirst()
                        .orElseThrow();
        String code = Files.readString(guard, StandardCharsets.UTF_8);

        // The exception holds only as long as the guard stays what it claims to be.
        assertThat(code).doesNotContain("SessionLifecycle").doesNotContain("SessionRegistry");
    }

    /** Every {@code .java} file in the repository, across all five modules. */
    private static List<Path> javaSources() throws IOException {
        try (Stream<Path> paths = Files.walk(repositoryRoot())) {
            return paths.filter(Files::isRegularFile)
                    .filter(path -> path.toString().toLowerCase(Locale.ROOT).endsWith(".java"))
                    .filter(path -> !path.toString().replace('\\', '/').contains("/build/"))
                    .toList();
        }
    }

    private static Path repositoryRoot() {
        Path candidate = Path.of("").toAbsolutePath();
        while (candidate != null && !Files.exists(candidate.resolve("settings.gradle.kts"))) {
            candidate = candidate.getParent();
        }
        if (candidate == null) {
            throw new IllegalStateException("could not locate the repository root");
        }
        return candidate;
    }
}
