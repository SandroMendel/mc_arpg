package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class B16MigrationRuntimeBoundaryTest {

    private static final Path RUNTIME_ENTRY_POINT =
            Path.of("src", "main", "java", "rpg", "plugin", "RpgPlugin.java");

    @Test
    @DisplayName("normal bootstrap does not silently migrate or overwrite B16 input")
    void normalBootstrapHasNoMigrationWritePath() throws Exception {
        String source = Files.readString(RUNTIME_ENTRY_POINT, StandardCharsets.UTF_8);

        assertThat(source)
                .doesNotContain("b16-migration")
                .doesNotContain("migrate-b16")
                .doesNotContain("Files.write")
                .doesNotContain("Files.copy");
        assertThat(source).contains("saveResource(file, false)");
    }

    @Test
    @DisplayName("runtime entry point does not import spreadsheet configuration")
    void runtimeEntryPointHasNoSpreadsheetConfigurationPath() throws Exception {
        String source = Files.readString(RUNTIME_ENTRY_POINT, StandardCharsets.UTF_8);
        String normalizedSource = source.toLowerCase(Locale.ROOT);

        assertThat(normalizedSource)
                .doesNotContain(
                        ".csv",
                        "import-csv",
                        "convertfrom-csv",
                        "import-excel",
                        "spreadsheet",
                        "workbook",
                        "worksheet",
                        ".xls",
                        ".xlsx");
    }
}
