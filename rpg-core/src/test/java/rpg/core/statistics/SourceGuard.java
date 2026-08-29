package rpg.core.statistics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Hilfe für die Quelltextprüfungen dieses Blocks — dieselbe Aufgabe, die {@code SourceGuard} in
 * B11 hat, und aus demselben Grund noch einmal: die Klasse ist paketprivat, und ein Paket kann
 * sie nicht aus einem anderen leihen.
 *
 * <p><b>Die Falle, gegen die sie existiert.</b> Mehrere Tests hier prüfen keine Wirkung, sondern
 * eine <em>Abwesenheit</em>: kein Metrikschlüssel als Literal, keine zweite Ablage, kein
 * Zustandswert im Schreibpfad. Solche Prüfungen haben alle dieselbe Schwachstelle — ein Kommentar,
 * der erklärt, <em>warum</em> etwas nicht dasteht, enthält das gesuchte Wort. Der Test schlägt an,
 * der Autor trägt eine Ausnahme ein, und ab da prüft er weniger, als sein Name behauptet. In B11
 * ist das dreimal hintereinander passiert.
 */
final class SourceGuard {

    private SourceGuard() {}

    /** Quelltext ohne Block- und Zeilenkommentare. */
    static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }

    /** Das Wurzelverzeichnis des Arbeitsbaums, egal aus welchem Modul der Lauf startet. */
    static Path repositoryRoot() {
        Path here = Path.of("").toAbsolutePath();
        while (here != null && !Files.isDirectory(here.resolve("rpg-core"))) {
            here = here.getParent();
        }
        if (here == null) {
            throw new IllegalStateException("Wurzelverzeichnis nicht gefunden");
        }
        return here;
    }

    /** Alle Produktivquellen der genannten Module. */
    static List<Path> productionSources(String... modules) throws IOException {
        Path root = repositoryRoot();
        List<Path> sources = new ArrayList<>();
        for (String module : modules) {
            Path main = root.resolve(module + "/src/main/java");
            if (!Files.isDirectory(main)) {
                continue;
            }
            try (Stream<Path> walk = Files.walk(main)) {
                walk.filter(path -> path.toString().endsWith(".java")).forEach(sources::add);
            }
        }
        return sources;
    }

    /** Die Produktivquellen der drei B12-Pakete. */
    static List<Path> statisticsSources() throws IOException {
        List<Path> sources = new ArrayList<>();
        for (Path source :
                productionSources("rpg-core", "rpg-persistence", "rpg-platform", "rpg-plugin")) {
            String path = source.toString().replace(java.io.File.separatorChar, '/');
            if (path.contains("/statistics/")) {
                sources.add(source);
            }
        }
        return sources;
    }
}
