package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Ein Spieler <b>ohne gewählten Charakter</b> bekommt auf keiner der drei Flächen einen Wert
 * (FR-008).
 *
 * <p><em>Nullen, die wie echte Werte aussehen, sind schlimmer als nichts.</em> Ein Spieler, der
 * zwischen Anmeldung und Charakterwahl {@code Level: 0} und {@code Coins: 0} sieht, hält das für
 * seinen Stand — und meldet einen Datenverlust, den es nicht gibt.
 */
class HudVisibilityTest {

    private static final UiConfig ALL_ON = config(true, true, true);

    @Test
    @DisplayName("ohne Charakter zeigt keine der drei Flaechen etwas")
    void withoutACharacterNothingShows() {
        for (HudSurface surface : HudSurface.values()) {
            assertThat(HudVisibility.shows(ALL_ON, surface, false))
                    .as("Flaeche " + surface + " ohne Charakter")
                    .isFalse();
        }
    }

    @Test
    @DisplayName("mit Charakter zeigen alle drei etwas")
    void withACharacterAllThreeShow() {
        for (HudSurface surface : HudSurface.values()) {
            assertThat(HudVisibility.shows(ALL_ON, surface, true)).isTrue();
        }
    }

    @Test
    @DisplayName("eine abgeschaltete Flaeche zeigt auch mit Charakter nichts")
    void aDisabledSurfaceShowsNothing() {
        UiConfig sidebarOff = config(true, true, false);

        assertThat(HudVisibility.shows(sidebarOff, HudSurface.SIDEBAR, true)).isFalse();
        assertThat(HudVisibility.shows(sidebarOff, HudSurface.ACTION_BAR, true)).isTrue();
    }

    @Test
    @DisplayName("ohne Charakter wird geraeumt, nicht leer beschrieben")
    void withoutACharacterTheSurfaceIsCleared() {
        // Geraeumt ist nicht dasselbe wie leer beschrieben (HudRenderer, Zusage 3). Ein Spieler,
        // der gerade den Charakter verloren hat, soll keine leere Sidebar sehen, sondern gar keine.
        assertThat(HudVisibility.needsClearing(ALL_ON, HudSurface.SIDEBAR, false)).isTrue();
    }

    @Test
    @DisplayName("eine abgeschaltete Flaeche wird NICHT geraeumt - abgeschaltet heisst kostenlos")
    void aDisabledSurfaceIsNotEvenCleared() {
        // Der Unterschied, an dem SC-010 haengt. Wer sie raeumte, haette je Spieler und Sekunde
        // genau die Arbeit erzeugt, die FR-013a verbietet - und ein Betreiber, der die Sidebar
        // abschaltet, will nicht, dass der Server sie jede Sekunde neu wegraeumt.
        UiConfig sidebarOff = config(true, true, false);

        assertThat(HudVisibility.needsClearing(sidebarOff, HudSurface.SIDEBAR, false)).isFalse();
        assertThat(HudVisibility.needsClearing(sidebarOff, HudSurface.SIDEBAR, true)).isFalse();
    }

    @Test
    @DisplayName("zeigen und raeumen schliessen einander aus")
    void showingAndClearingAreExclusive() {
        for (HudSurface surface : HudSurface.values()) {
            for (boolean enabled : new boolean[] {true, false}) {
                for (boolean hasCharacter : new boolean[] {true, false}) {
                    UiConfig config = config(enabled, enabled, enabled);

                    boolean shows = HudVisibility.shows(config, surface, hasCharacter);
                    boolean clears = HudVisibility.needsClearing(config, surface, hasCharacter);

                    assertThat(shows && clears)
                            .as(surface + " enabled=" + enabled + " character=" + hasCharacter)
                            .isFalse();
                }
            }
        }
    }

    private static UiConfig config(boolean actionBar, boolean bossBar, boolean sidebar) {
        return new UiConfig(
                "en",
                Duration.ofSeconds(1),
                new SurfaceSetting(actionBar),
                new SurfaceSetting(bossBar),
                new SurfaceSetting(sidebar),
                Duration.ofSeconds(4),
                new DamageNumberSetting(true, Duration.ofMillis(1200), 1.4));
    }
}
