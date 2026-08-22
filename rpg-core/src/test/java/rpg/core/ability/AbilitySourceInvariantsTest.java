package rpg.core.ability;

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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T133 - Zusagen über den <b>Quelltext</b> dieses Blocks (FR-008, Prinzip V).
 *
 * <p>Nur so prüfbar. Ein Verhaltenstest kann zeigen, dass ein bestimmter Cooldown aus der Datei
 * kommt; er kann nicht zeigen, dass <em>nirgends</em> einer im Code steht. Genau das ist aber die
 * Zusage: eine Zahl, die im Code steht, lässt sich nicht durch Neustart ändern - und dann ist
 * Balancing wieder eine Codeänderung.
 *
 * <p>Gebaut wie {@code ClassSourceInvariantsTest} in B07, mit derselben Kommentarentfernung: eine
 * Zahl in einem erklärenden Kommentar ist eine Erklärung, kein Wert.
 */
class AbilitySourceInvariantsTest {

    private static final String B08_CORE = "/rpg/core/ability/";

    /**
     * Zahlen, die in diesem Block stehen dürfen, mit dem Grund.
     *
     * <p>Kurz zu halten ist der Sinn der Liste. Jeder Eintrag ist eine Ausnahme, und eine Ausnahme
     * ohne Begründung ist eine Zahl, die jemand hineingeschrieben und niemand geprüft hat.
     */
    private static final List<String> PERMITTED =
            List.of(
                    // Der Deckel auf die Cooldown-Reduktion. Kein Balancing-Wert, sondern eine Grenze
                    // aus ADR-008: er ist bewusst NICHT konfigurierbar, weil eine Konfiguration ihn
                    // aufheben könnte und dann wäre ein Cooldown von null erreichbar.
                    "MAX_COOLDOWN_REDUCTION",
                    // Die Obergrenze des Zählers. Sie ist die Definition der Skala, nicht ihre
                    // Einstellung - ein Meter von 0 bis 100 ist, was "Meter" bedeutet.
                    "METER_MAXIMUM");

    @Test
    @DisplayName("T133: keine Kosten-, Cooldown-, Reichweiten- oder Wirkungszahl steht im Code")
    void noBalancingNumberLivesInCode() throws IOException {
        // Eine Konstante mit einem verdächtigen Namen UND einem Zahlenwert. Nach dem Namen zu suchen
        // statt nach der Zahl ist der Punkt: 0.35 kann alles sein, MANA_COST = 0.35 nicht.
        Pattern suspicious =
                Pattern.compile(
                        "(?i)(static\\s+final\\s+(?:double|int|long)\\s+"
                                + "\\w*(COST|COOLDOWN|RANGE|DAMAGE|HEAL|RADIUS|DURATION|CHANCE|MANA)"
                                + "\\w*)\\s*=\\s*[0-9]");

        List<String> violations = new ArrayList<>();
        for (Path source : productionSources()) {
            Matcher matcher = suspicious.matcher(codeOf(source));
            while (matcher.find()) {
                String declaration = matcher.group(1);
                if (PERMITTED.stream().noneMatch(declaration::contains)) {
                    violations.add(source.getFileName() + ": " + declaration.trim());
                }
            }
        }

        assertThat(violations)
                .as(
                        "FR-008: jede Zahl dieses Blocks steht in abilities.yml. Eine im Code kann"
                                + " kein Betreiber ändern, und dann ist Balancing wieder eine"
                                + " Codeänderung")
                .isEmpty();
    }

    @Test
    @DisplayName("keine Fähigkeits-ID steht im Code - sonst wäre die neunzehnte ein Sonderfall")
    void noAbilityIdLivesInCode() throws IOException {
        // Die eigentliche Zusage hinter SC-001. Eine ID im Code hiesse: DIESE Fähigkeit wird anders
        // behandelt, und jede weitere braucht wieder eine Zeile Java.
        Pattern id = Pattern.compile("\"(warrior|rogue|mage)\\.[a-z-]+\"");

        List<String> violations = new ArrayList<>();
        for (Path source : productionSources()) {
            Matcher matcher = id.matcher(codeOf(source));
            while (matcher.find()) {
                violations.add(source.getFileName() + ": " + matcher.group());
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("Prinzip III: kein Quelltext dieses Blocks erwähnt Bukkit, Paper oder NMS")
    void coreStaysBukkitFree() throws IOException {
        List<String> forbidden = List.of("org.bukkit", "io.papermc", "net.minecraft", "org.spigotmc");

        List<String> violations = new ArrayList<>();
        for (Path source : productionSources()) {
            String content = codeOf(source);
            for (String needle : forbidden) {
                if (content.contains(needle)) {
                    violations.add(source.getFileName() + " mentions " + needle);
                }
            }
        }

        assertThat(violations).isEmpty();
    }

    @Test
    @DisplayName("kein Spielertext steht im Code - nur Schlüssel (Prinzip V)")
    void noPlayerFacingTextLivesInCode() throws IOException {
        // Jede Meldung dieses Blocks geht über einen MessageKey. Der Beweis ist, dass
        // AbilityMessageKeys ausschliesslich Schlüssel enthält und die aussehen wie Schlüssel.
        Path keys =
                repositoryRoot()
                        .resolve("rpg-core/src/main/java/rpg/core/ability/AbilityMessageKeys.java");
        Matcher matcher = Pattern.compile("MessageKey\\.of\\(\"([^\"]+)\"\\)").matcher(codeOf(keys));

        List<String> notAKey = new ArrayList<>();
        while (matcher.find()) {
            String value = matcher.group(1);
            // Ein Schlüssel ist punktgetrennt und kleingeschrieben. Ein Satz ist es nicht.
            if (value.contains(" ") || !value.startsWith("ability.")) {
                notAKey.add(value);
            }
        }

        assertThat(notAKey).isEmpty();
    }

    // --- helpers ---

    private static String codeOf(Path source) throws IOException {
        String content = Files.readString(source, StandardCharsets.UTF_8);
        return content.replaceAll("(?s)/\\*.*?\\*/", " ").replaceAll("(?m)//.*$", " ");
    }

    private static List<Path> productionSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path path : javaSources()) {
            String normalised = path.toString().replace('\\', '/');
            if (normalised.contains(B08_CORE) && !normalised.contains("/test/")) {
                sources.add(path);
            }
        }
        return sources;
    }

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
            throw new IllegalStateException("could not find the repository root");
        }
        return candidate;
    }
}
