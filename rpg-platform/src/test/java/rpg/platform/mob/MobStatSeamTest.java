package rpg.platform.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalLong;
import java.util.function.Supplier;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.MobStatProvider;
import rpg.core.currency.MobCoinProvider;
import rpg.core.mob.MobProviders;
import rpg.core.progression.MobXpProvider;
import rpg.core.stats.StatConfig;

/**
 * T037, SC-008: die drei uebernommenen Schnittstellen - und die Naht, an der sie haengen.
 *
 * <p>B05, B06 und B08b halten seit Monaten je eine Schnittstelle offen, jede mit demselben Satz im
 * Javadoc: <em>"until B10 exists, then B10 replaces the provider through this same interface"</em>.
 * SC-008 macht daraus eine pruefbare Zusage: <b>bedient, und keine hat eine zweite Fassung
 * bekommen</b>.
 *
 * <p><b>Warum das ueberhaupt ein Test ist.</b> Der bequeme Weg waere gewesen, neben {@code
 * statsFor(String)} ein {@code statsForKind(MobKind)} zu stellen und die alte Methode stehen zu
 * lassen. Es haette funktioniert, es waere ohne einen Fehlschlag durchgegangen - und danach haette
 * es zwei Wege gegeben, dieselbe Frage zu stellen, mit zwei Aufrufergruppen und einer Antwort, die
 * je nach Weg unterschiedlich ausfaellt. Genau davor steht dieser Test.
 *
 * <p><b>Und die positive Haelfte.</b> {@code NoRawTypeNameLeftTest} bewacht, dass niemand mehr den
 * Vanilla-Typnamen in die drei Schnittstellen fuettert - aber dieser Waechter bliebe auch dann
 * gruen, wenn jemand den Aufruf ersatzlos entfernt. Hier steht die Gegenprobe: die Stellen, an
 * denen die drei Schnittstellen gefuettert werden, uebergeben wirklich {@code kindKeyOf}.
 */
class MobStatSeamTest {

    private static final Path ROOT = repositoryRoot();

    /** Die drei Schnittstellen mit ihrer <b>unveraenderten</b> Form aus B05, B06 und B08b. */
    private static final Map<Class<?>, MethodShape> SEAM = seam();

    /**
     * Die drei Stellen, an denen der Artschluessel die Plattformschicht verlaesst - und der Aufruf,
     * dem sie ihn uebergeben (T032, T033, T033a).
     *
     * <p>Nur eine fragt unmittelbar. Die Erfahrung laeuft ueber {@code XpDistributor.distribute}
     * und die Coins ueber {@code CoinDropPlanner.planFor}, weil {@link MobXpProvider} und {@link
     * MobCoinProvider} dort liegen und nicht beim Listener. Der Schluessel ist derselbe, nur eine
     * Station weiter - und genau er ist es, den dieser Test verfolgt.
     */
    private static final Map<String, String> FEEDERS =
            Map.of(
                    "MobEquipmentListener.java", "statsFor",
                    "ProgressionDeathListener.java", "distribute",
                    "CoinDropListener.java", "planFor");

    private static final Pattern DECLARES_INTERFACE = Pattern.compile("(?m)^\\s*(?:public\\s+)?interface\\s+\\w+");
    private static final Pattern SEAM_METHOD = Pattern.compile("\\b(statsFor|xpFor|coinsFor)\\s*\\(");

    @Test
    @DisplayName("jede der drei hat genau eine Methode - und es ist noch dieselbe")
    void eachOfTheThreeHasExactlyOneMethodAndItIsStillTheSameOne() {
        for (Map.Entry<Class<?>, MethodShape> entry : SEAM.entrySet()) {
            Class<?> seam = entry.getKey();
            MethodShape expected = entry.getValue();

            List<Method> abstractMethods =
                    Stream.of(seam.getMethods())
                            .filter(method -> java.lang.reflect.Modifier.isAbstract(method.getModifiers()))
                            .toList();

            assertThat(abstractMethods)
                    .as(
                            "SC-008: %s darf genau eine Frage stellen - eine zweite Methode waere"
                                    + " die zweite Fassung, die es nicht geben soll",
                            seam.getSimpleName())
                    .hasSize(1);

            Method only = abstractMethods.get(0);
            assertThat(only.getName()).isEqualTo(expected.name());
            assertThat(only.getParameterTypes())
                    .as("%s.%s nimmt den Schluessel als String entgegen", seam.getSimpleName(), expected.name())
                    .containsExactly(String.class);
            assertThat(only.getReturnType())
                    .as(
                            "%s.%s antwortet weiterhin %s - leer heisst \"kein eigener Eintrag\","
                                    + " nie Null (FR-007)",
                            seam.getSimpleName(), expected.name(), expected.returnType().getSimpleName())
                    .isEqualTo(expected.returnType());
        }
    }

    @Test
    @DisplayName("es gibt keine vierte Schnittstelle, die dieselbe Frage stellt")
    void thereIsNoFourthInterfaceAskingTheSameQuestion() throws IOException {
        // Gesucht wird in der Quelle und nicht ueber Reflection: eine neue Schnittstelle, die
        // niemand implementiert, faende Reflection nicht - geschrieben waere sie trotzdem.
        List<String> declaring = new ArrayList<>();

        for (Path source : productionSources()) {
            String code = codeOnly(Files.readString(source, StandardCharsets.UTF_8));
            if (DECLARES_INTERFACE.matcher(code).find() && SEAM_METHOD.matcher(code).find()) {
                declaring.add(source.getFileName().toString());
            }
        }

        assertThat(declaring)
                .as(
                        "SC-008: statsFor, xpFor und coinsFor werden von genau drei Schnittstellen"
                                + " erklaert - jede weitere waere ein zweiter Weg zu derselben"
                                + " Antwort")
                .containsExactlyInAnyOrder(
                        "MobStatProvider.java", "MobXpProvider.java", "MobCoinProvider.java");
    }

    @Test
    @DisplayName("die drei Fuetterstellen uebergeben wirklich die Art")
    void theThreeFeedingPlacesActuallyPassTheKind() throws IOException {
        // Die Gegenprobe zu NoRawTypeNameLeftTest: dort faellt auf, wer den falschen Schluessel
        // nimmt - hier faellt auf, wer gar keinen mehr nimmt. Ein geloeschter Aufruf ist die
        // stillste Art, diesen Block rueckgaengig zu machen.
        Map<String, String> found = new LinkedHashMap<>();

        for (Path source : productionSources()) {
            String name = source.getFileName().toString();
            if (!FEEDERS.containsKey(name)) {
                continue;
            }
            String code = codeOnly(Files.readString(source, StandardCharsets.UTF_8));
            found.put(name, code);
        }

        assertThat(found.keySet())
                .as("die drei Fuetterstellen muessen es alle noch geben")
                .containsExactlyInAnyOrderElementsOf(FEEDERS.keySet());

        for (Map.Entry<String, String> entry : found.entrySet()) {
            String name = entry.getKey();
            String code = entry.getValue();

            assertThat(code)
                    .as("%s reicht den Schluessel weiterhin an %s", name, FEEDERS.get(name))
                    .containsPattern("\\b" + FEEDERS.get(name) + "\\s*\\(");
            assertThat(code)
                    .as(
                            "%s muss den Schluessel ueber MobKindTag.kindKeyOf bilden - sonst"
                                    + " bekommen vier Arten auf ZOMBIE wieder dieselbe Antwort"
                                    + " (FR-006, FR-007)",
                            name)
                    .contains("kindKeyOf(");
        }
    }

    @Test
    @DisplayName("B10 liefert genau diese drei Typen - und keinen eigenen daneben")
    void blockTenDeliversExactlyTheseThreeTypesAndNoOwnOneBesideThem() throws NoSuchMethodException {
        // Der Beweis, dass die Naht haelt, steht in den Rueckgabetypen von MobProviders: was
        // rpg.core.mob herausgibt, ist der Typ, den B05, B06 und B08b seit Monaten erwarten.
        // Waere daneben eine eigene Fassung entstanden, stuende hier ein anderer Name.
        Class<?> providers = MobProviders.class;

        assertThat(providers.getMethod("stats", Supplier.class, StatConfig.class).getReturnType())
                .isEqualTo(MobStatProvider.class);
        assertThat(providers.getMethod("xp", Supplier.class).getReturnType())
                .isEqualTo(MobXpProvider.class);
        assertThat(providers.getMethod("coins", Supplier.class).getReturnType())
                .isEqualTo(MobCoinProvider.class);
    }

    // --- fixtures ---

    private record MethodShape(String name, Class<?> returnType) {}

    private static Map<Class<?>, MethodShape> seam() {
        Map<Class<?>, MethodShape> shapes = new LinkedHashMap<>();
        shapes.put(MobStatProvider.class, new MethodShape("statsFor", Optional.class));
        shapes.put(MobXpProvider.class, new MethodShape("xpFor", OptionalLong.class));
        shapes.put(MobCoinProvider.class, new MethodShape("coinsFor", OptionalLong.class));
        return Map.copyOf(shapes);
    }

    private static List<Path> productionSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (String module : List.of("rpg-core", "rpg-platform", "rpg-plugin", "rpg-persistence")) {
            Path main = ROOT.resolve(module + "/src/main/java");
            if (!Files.isDirectory(main)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(main)) {
                walk.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
            }
        }
        return sources;
    }

    /** Kommentare weg: eine Schnittstelle zu erklaeren ist erlaubt, sie zu verdoppeln nicht. */
    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    private static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        while (here != null && !Files.isDirectory(here.resolve("rpg-platform"))) {
            here = here.getParent();
        }
        if (here == null) {
            throw new IllegalStateException(
                    "repository root not found from " + Path.of("").toAbsolutePath());
        }
        return here;
    }
}
