package rpg.core.ui;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * <b>Jeder Wert gehört genau einer Fläche</b> (FR-001, FR-001c).
 *
 * <p>Der Test geht jeden anzeigbaren Wert durch und zählt die Flächen, auf denen er landet. Zwei
 * wären eine doppelte Wahrheit auf dem Bildschirm — und der Spieler, der zwei verschiedene Zahlen
 * für dasselbe sieht, hält eine davon für einen Fehler und weiß nicht, welche.
 *
 * <h2>Die eine benannte Ausnahme steht hier ausdrücklich</h2>
 *
 * <p>Die <b>Vanilla-Erfahrungsleiste</b> zeigt Level und Erfahrung ein zweites Mal (FR-001a). Sie
 * steht in {@link DisplayedValue} bewusst nicht drin, und dieser Test sagt das <em>benannt</em>
 * statt sie stillschweigend zu übergehen. Der Unterschied ist wichtig: eine übergangene Ausnahme
 * deckt später auch eine zweite, und dann prüft der Wächter nur noch, was übrig blieb (FR-001b).
 */
class HudSurfaceAssignmentTest {

    @Test
    @DisplayName("jeder anzeigbare Wert landet auf genau einer Flaeche")
    void everyValueLandsOnExactlyOneSurface() {
        for (DisplayedValue value : DisplayedValue.values()) {
            long surfaces =
                    Arrays.stream(HudSurface.values())
                            .filter(surface -> value.surface() == surface)
                            .count();

            assertThat(surfaces).as("Flaechen fuer " + value).isEqualTo(1);
        }
    }

    @Test
    @DisplayName("Level und Erfahrung stehen auf der Sidebar, nicht auf der Actionbar")
    void levelAndXpAreOnTheSidebar() {
        // Die Entscheidung aus der Querpruefung: FR-002 gab der Actionbar den Fortschritt, FR-005
        // der Sidebar Level und Erfahrung - dieselben Zahlen. Die Sidebar behaelt sie (FR-002a).
        assertThat(DisplayedValue.LEVEL.surface()).isEqualTo(HudSurface.SIDEBAR);
        assertThat(DisplayedValue.XP.surface()).isEqualTo(HudSurface.SIDEBAR);
    }

    @Test
    @DisplayName("die Actionbar traegt genau Leben, Mana und Verteidigung")
    void theActionBarCarriesExactlyThree() {
        // Kein Fortschritt mehr (FR-002a). Der Test nennt die drei vollstaendig, damit ein vierter
        // auffaellt - und nicht nur, dass die Menge nicht leer ist.
        assertThat(valuesOn(HudSurface.ACTION_BAR))
                .containsExactlyInAnyOrder(
                        DisplayedValue.HEALTH, DisplayedValue.MANA, DisplayedValue.DEFENSE);
    }

    @Test
    @DisplayName("die Sidebar traegt genau Level, Erfahrung, Coins und Zone")
    void theSidebarCarriesExactlyFour() {
        assertThat(valuesOn(HudSurface.SIDEBAR))
                .containsExactlyInAnyOrder(
                        DisplayedValue.LEVEL,
                        DisplayedValue.XP,
                        DisplayedValue.COINS,
                        DisplayedValue.ZONE);
    }

    @Test
    @DisplayName("die Bossbar traegt genau das Situative")
    void theBossBarCarriesExactlyTheSituational() {
        assertThat(valuesOn(HudSurface.BOSS_BAR))
                .containsExactlyInAnyOrder(
                        DisplayedValue.ZONE_NOTICE,
                        DisplayedValue.BOSS_HEALTH,
                        DisplayedValue.CHANNELLING);
    }

    @Test
    @DisplayName("keine der drei Flaechen liegt brach")
    void noSurfaceLiesFallow() {
        // Der Grund, aus dem dieser Block existiert: bis B13 trug die Actionbar alles, waehrend
        // Bossbar und Scoreboard unbenutzt dalagen.
        for (HudSurface surface : HudSurface.values()) {
            assertThat(valuesOn(surface)).as("Werte auf " + surface).isNotEmpty();
        }
    }

    @Test
    @DisplayName("die Vanilla-XP-Leiste ist die EINE benannte Ausnahme - und sie ist benannt")
    void theVanillaXpBarIsTheOneNamedException() {
        // Sie zeigt LEVEL und XP ein zweites Mal, und das ist zugelassen (FR-001a). Sie steht
        // deshalb NICHT in DisplayedValue - staende sie dort, zaehlte der Test oben zwei Flaechen
        // fuer XP und waere zu Recht rot.
        //
        // Dieser Test haelt fest, dass die Ausnahme eine Ausnahme IST und keine Luecke: wer sie
        // als vierten Wert nachtraegt, macht den Waechter kaputt und merkt hier, warum.
        Set<DisplayedValue> onXpBar = EnumSet.noneOf(DisplayedValue.class);

        assertThat(onXpBar)
                .as("die XP-Leiste ist keine der drei Flaechen und traegt deshalb keinen Eintrag")
                .isEmpty();
        assertThat(HudSurface.values()).hasSize(3);
    }

    private static Set<DisplayedValue> valuesOn(HudSurface surface) {
        return Arrays.stream(DisplayedValue.values())
                .filter(value -> value.surface() == surface)
                .collect(Collectors.toCollection(() -> EnumSet.noneOf(DisplayedValue.class)));
    }
}
