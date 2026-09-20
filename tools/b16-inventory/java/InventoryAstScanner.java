import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.LiteralTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.JavacTask;
import com.sun.source.util.SourcePositions;
import com.sun.source.util.TreePathScanner;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Emits numeric Java literal spans for one source file.
 *
 * The output is intentionally a small tab-separated protocol:
 *   L <tab> start <tab> end <tab> Tree.Kind <tab> original-token
 *
 * javac's source positions are UTF-16 offsets, which is also the offset model
 * used by PowerShell/.NET strings.  No compiler output or generated files are
 * written by this helper.
 */
public final class InventoryAstScanner {
    private InventoryAstScanner() {
    }

    public static void main(String[] args) {
        try {
            if (args.length != 1) {
                fail("usage: InventoryAstScanner <Java source file>");
            }
            scan(Paths.get(args[0]).toAbsolutePath().normalize());
        } catch (Exception exception) {
            System.err.println("AST scanner unavailable: " + exception.getMessage());
            System.exit(2);
        }
    }

    private static void scan(Path sourcePath) throws IOException {
        if (!Files.isRegularFile(sourcePath)) {
            fail("Java source file does not exist: " + sourcePath);
        }

        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) {
            fail("JDK compiler API is unavailable; run the inventory with a JDK, not a JRE");
        }

        String source = new String(Files.readAllBytes(sourcePath), StandardCharsets.UTF_8);
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        List<LiteralSpan> spans = new ArrayList<>();

        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(
                diagnostics, null, StandardCharsets.UTF_8)) {
            Iterable<? extends JavaFileObject> files = fileManager.getJavaFileObjects(sourcePath.toFile());
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    Arrays.asList("-proc:none"),
                    null,
                    files);
            Iterable<? extends CompilationUnitTree> units = task.parse();
            failOnErrors(diagnostics);

            SourcePositions positions = com.sun.source.util.Trees.instance(task).getSourcePositions();
            for (CompilationUnitTree unit : units) {
                new TreePathScanner<Void, Void>() {
                    @Override
                    public Void visitLiteral(LiteralTree literal, Void unused) {
                        if (isNumeric(literal.getKind())) {
                            long start = positions.getStartPosition(unit, literal);
                            long end = positions.getEndPosition(unit, literal);
                            if (start < 0 || end < start || end > source.length()) {
                                throw new IllegalStateException("javac returned an invalid literal span");
                            }
                            spans.add(new LiteralSpan(
                                    (int) start,
                                    (int) end,
                                    literal.getKind().name(),
                                    source.substring((int) start, (int) end)));
                        }
                        return super.visitLiteral(literal, unused);
                    }
                }.scan(unit, null);
            }
        }

        failOnErrors(diagnostics);
        spans.sort(Comparator.comparingInt(span -> span.start));
        for (LiteralSpan span : spans) {
            System.out.println("L\t" + span.start + "\t" + span.end + "\t"
                    + span.kind + "\t" + span.token);
        }
    }

    private static boolean isNumeric(Tree.Kind kind) {
        return kind == Tree.Kind.INT_LITERAL
                || kind == Tree.Kind.LONG_LITERAL
                || kind == Tree.Kind.FLOAT_LITERAL
                || kind == Tree.Kind.DOUBLE_LITERAL;
    }

    private static void failOnErrors(DiagnosticCollector<JavaFileObject> diagnostics) {
        for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
            if (diagnostic.getKind() == Diagnostic.Kind.ERROR) {
                fail("Java parsing failed at line " + diagnostic.getLineNumber()
                        + ": " + diagnostic.getMessage(null));
            }
        }
    }

    private static void fail(String message) {
        throw new IllegalStateException(message);
    }

    private static final class LiteralSpan {
        private final int start;
        private final int end;
        private final String kind;
        private final String token;

        private LiteralSpan(int start, int end, String kind, String token) {
            this.start = start;
            this.end = end;
            this.kind = kind;
            this.token = token;
        }
    }
}
