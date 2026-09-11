package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.progression.ProgressView;

/**
 * Die vier Sidebar-Zeilen (FR-005).
 *
 * <p><b>Jede Zeile ist ein Schlüssel mit Platzhaltern und nie ein Text</b> (FR-014). Ein Test, der
 * fertige Zeichenketten prüfte, würde den Wortlaut im Code festschreiben — und genau das sammelt
 * der Wächter aus FR-015 später wieder ein.
 */
class SidebarLinesTest {

    private static final ProgressView MID_LEVEL = new ProgressView(12, 340, 1000, false);
    private static final ProgressView TOP_LEVEL = new ProgressView(60, 4120, 0, true);

    @Test
    @DisplayName("vier Zeilen, in der Reihenfolge Level, Erfahrung, Coins, Zone")
    void fourLinesInOrder() {
        List<SidebarLines.Line> lines = SidebarLines.of(MID_LEVEL, 250, Optional.of("Ashen Reach"));

        assertThat(lines)
                .extracting(line -> line.key().value())
                .containsExactly(
                        "ui.sidebar.level", "ui.sidebar.xp", "ui.sidebar.coins", "ui.sidebar.zone");
    }

    @Test
    @DisplayName("jede Zeile ist ein MessageKey und nie ein Text")
    void everyLineIsAKey() {
        List<SidebarLines.Line> lines = SidebarLines.of(MID_LEVEL, 250, Optional.of("Ashen Reach"));

        assertThat(lines).allSatisfy(line -> assertThat(line.key().value()).startsWith("ui."));
    }

    @Test
    @DisplayName("die Werte stehen als Platzhalter da, nicht eingesetzt")
    void theValuesTravelAsPlaceholders() {
        List<SidebarLines.Line> lines = SidebarLines.of(MID_LEVEL, 250, Optional.of("Ashen Reach"));

        assertThat(lines.get(0).values()).containsExactly(java.util.Map.entry("level", "12"));
        assertThat(lines.get(1).values()).containsOnlyKeys("xp", "xpNext");
        assertThat(lines.get(2).values()).containsExactly(java.util.Map.entry("coins", "250"));
        assertThat(lines.get(3).values()).containsExactly(java.util.Map.entry("zone", "Ashen Reach"));
    }

    @Test
    @DisplayName("am Hoechstlevel traegt die Erfahrungszeile ihren eigenen Schluessel")
    void atMaxLevelTheXpLineHasItsOwnKey() {
        // '4120/0' saehe aus wie ein Fehler. ProgressView.atMaxLevel() beantwortet das als eigenes
        // Feld, und StatusActionBar hat es bereits so benutzt - die Sidebar folgt derselben
        // Unterscheidung, statt sie ein zweites Mal leicht anders zu erfinden.
        List<SidebarLines.Line> lines = SidebarLines.of(TOP_LEVEL, 0, Optional.empty());

        assertThat(lines.get(1).key().value()).isEqualTo("ui.sidebar.xp-max");
        assertThat(lines.get(1).values()).doesNotContainKey("xpNext");
    }

    @Test
    @DisplayName("ohne Zone steht die Wildnis als eigener Schluessel")
    void withoutAZoneTheWildernessKeyIsUsed() {
        // Eine Zeile, die 'Zone: ' ohne Wert zeigt, sieht aus wie ein fehlender Wert.
        List<SidebarLines.Line> lines = SidebarLines.of(MID_LEVEL, 0, Optional.empty());

        assertThat(lines.get(3).key().value()).isEqualTo("ui.sidebar.zone-wilderness");
        assertThat(lines.get(3).values()).isEmpty();
    }

    @Test
    @DisplayName("jeder benutzte Schluessel steht in UiMessageKeys.all()")
    void everyUsedKeyIsDeclared() {
        // Sonst faellt er der Startpruefung nicht auf - und dann dem Spieler, als roher
        // Schluesselname mitten auf dem Bildschirm.
        List<SidebarLines.Line> normal = SidebarLines.of(MID_LEVEL, 1, Optional.of("Ashen Reach"));
        List<SidebarLines.Line> edge = SidebarLines.of(TOP_LEVEL, 0, Optional.empty());

        assertThat(UiMessageKeys.all())
                .containsAll(normal.stream().map(SidebarLines.Line::key).toList())
                .containsAll(edge.stream().map(SidebarLines.Line::key).toList());
    }

    @Test
    @DisplayName("die Werte sind unveraenderlich - ein Aufrufer kann die Zeile nicht umschreiben")
    void theValuesAreImmutable() {
        SidebarLines.Line line = SidebarLines.of(MID_LEVEL, 1, Optional.empty()).get(0);

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> line.values().put("level", "999"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
