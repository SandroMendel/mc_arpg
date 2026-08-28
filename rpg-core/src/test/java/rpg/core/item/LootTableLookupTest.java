package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Welche Tabelle für welche Kreatur gilt (FR-018, FR-020, FR-023).
 *
 * <p><b>Der wichtigste Fall ist der dritte:</b> zwei Arten auf derselben Vanilla-Basis lassen
 * unterschiedliche Beute fallen. Genau daran ist die frühere Lösung dieses Projekts gescheitert —
 * B10 hat den Schlüsselwechsel von {@code getType().name()} auf die Art gerade deshalb gemacht, weil
 * vier Arten auf {@code ZOMBIE} sonst vier Mal derselbe Schlüssel wären. Die Beute erbt diese Zusage
 * und darf sie nicht wieder verlieren.
 */
class LootTableLookupTest {

    private static final LootTable ROTLING = table("potion.rotling");
    private static final LootTable GREENFIELDS = table("potion.zone");
    private static final LootTable WARDEN = table("potion.boss");

    private final LootTables tables =
            new LootTables(
                    Map.of("greenfields.rotling", ROTLING),
                    Map.of("greenfields", GREENFIELDS),
                    Map.of("greenfields.warden-of-the-field", WARDEN));

    @Test
    @DisplayName("eine Art mit eigener Tabelle bekommt ihre eigene")
    void aKindWithItsOwnTableGetsIt() {
        assertThat(tables.tableFor("greenfields.rotling", "greenfields", false)).isEqualTo(ROTLING);
    }

    @Test
    @DisplayName("eine Art ohne eigene erbt die ihrer Region")
    void aKindWithoutOneInheritsTheZoneTable() {
        assertThat(tables.tableFor("greenfields.thornling", "greenfields", false))
                .isEqualTo(GREENFIELDS);
    }

    @Test
    @DisplayName("zwei Arten auf DERSELBEN Basis lassen verschiedene Beute fallen (FR-020)")
    void twoKindsOnTheSameBaseDropDifferently() {
        // greenfields.rotling und greenfields.field-shade sind beide ZOMBIE (mobs.yml). Die
        // Tabelle haengt an der ART, nicht am Basistyp - dieselbe Zusage, die B10 fuer Werte,
        // Erfahrung und Coins gibt, und der Grund fuer den ganzen Schluesselwechsel.
        LootTable rotling = tables.tableFor("greenfields.rotling", "greenfields", false);
        LootTable fieldShade = tables.tableFor("greenfields.field-shade", "greenfields", false);

        assertThat(rotling).isNotEqualTo(fieldShade);
        assertThat(rotling.entries().get(0).templateKey()).isEqualTo("potion.rotling");
        assertThat(fieldShade.entries().get(0).templateKey()).isEqualTo("potion.zone");
    }

    @Test
    @DisplayName("ein Boss bekommt seine eigene Tabelle")
    void aBossGetsItsOwnTable() {
        assertThat(tables.tableFor("greenfields.warden-of-the-field", "greenfields", true))
                .isEqualTo(WARDEN);
    }

    @Test
    @DisplayName("und ein Boss erbt NICHT von seiner Region (FR-023)")
    void aBossDoesNotInheritFromItsZone() {
        // Ohne eigene Tabelle laesst er nichts fallen, statt die Ausbeute eines gewoehnlichen
        // Gegners zu liefern. Ein Boss, der wie die Horde um ihn herum abwirft, waere kein Boss.
        assertThat(tables.tableFor("dustlands.dune-warlord", "dustlands", true))
                .isEqualTo(LootTable.empty());
    }

    @Test
    @DisplayName("eine Art ohne jede Tabelle laesst nichts fallen - kein Fehler")
    void aKindWithoutAnyTableDropsNothing() {
        assertThat(tables.tableFor("pale-wilds.something", "pale-wilds", false))
                .isEqualTo(LootTable.empty());
    }

    @Test
    @DisplayName("jede genannte Vorlage laesst sich einsammeln - fuer die Startpruefung")
    void everyReferencedTemplateIsCollectable() {
        assertThat(tables.referencedTemplates())
                .containsExactlyInAnyOrder("potion.rotling", "potion.zone", "potion.boss");
    }

    private static LootTable table(String templateKey) {
        return new LootTable(List.of(LootEntry.single(templateKey, 0.5)));
    }
}
