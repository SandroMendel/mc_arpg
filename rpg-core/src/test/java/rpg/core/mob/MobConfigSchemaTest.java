package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.config.SchemaValidator;
import rpg.core.config.ConfigView;
import rpg.core.stats.Attribute;

/**
 * Das Schema von {@code mobs.yml} — und die eine Zusage, die es einlöst.
 *
 * <p><b>Jede Meldung nennt Datei, Schlüssel und Grund.</b> Geprüft wird deshalb nicht nur, <em>dass
 * </em> ein Fehler den Start abbricht, sondern dass die Meldung den Schlüssel nennt. Eine, die nur
 * „invalid configuration" sagt, ist ein Fehler dieses Blocks und kein bestandener Prüfschritt: sie
 * lässt einen Betreiber vierzig Zeilen durchsuchen, um einen Tippfehler zu finden, den der Code
 * bereits in der Hand hatte.
 */
class MobConfigSchemaTest {

    @Test
    @DisplayName("eine gueltige Konfiguration wird gebunden")
    void aValidConfigurationBinds() {
        MobConfig config = MobConfigSchema.schema().bind(view(document()));

        assertThat(config.budget().serverWide()).isEqualTo(800);
        assertThat(config.kinds()).containsOnlyKeys("greenfields.rotling", "greenfields.warden");
        assertThat(config.kind("greenfields.rotling").orElseThrow().attributeOr(Attribute.HEALTH, 0))
                .isEqualTo(40.0);
        assertThat(config.horde("greenfields").orElseThrow().entries()).hasSize(1);
        assertThat(config.horde("greenfields").orElseThrow().boss().kindKey())
                .isEqualTo("greenfields.warden");
        assertThat(config.adminSpawnLimit()).isEqualTo(20);
    }

    @Test
    @DisplayName("die Admin-Grenze faellt ohne Feld auf 20 zurueck")
    void anOmittedAdminSpawnLimitUsesTheDefault() throws Exception {
        Map<String, Object> doc = document();
        doc.remove("admin-spawn-limit");

        ConfigView validated =
                SchemaValidator.validate(Path.of("mobs.yml"), doc, MobConfigSchema.schema());

        assertThat(MobConfigSchema.schema().bind(validated).adminSpawnLimit()).isEqualTo(20);
    }

    @Test
    @DisplayName("eine gesetzte Admin-Grenze wird uebernommen")
    void aConfiguredAdminSpawnLimitBinds() {
        Map<String, Object> doc = document();
        doc.put("admin-spawn-limit", 7);

        assertThat(MobConfigSchema.schema().bind(view(doc)).adminSpawnLimit()).isEqualTo(7);
    }

    @Test
    @DisplayName("mehrere Arten auf derselben Basis sind der Normalfall, kein Fehler")
    void severalKindsOnTheSameBaseAreNormal() {
        // 48 Arten auf sechs Regionen, und Minecraft hat nicht 48 passende Entity-Typen. Genau
        // hier lag die Verwechslung, die den Schluesselwechsel ausgeloest hat.
        Map<String, Object> doc = document();
        @SuppressWarnings("unchecked")
        Map<String, Object> kinds = (Map<String, Object>) doc.get("kinds");
        kinds.put("greenfields.brute", kind("ZOMBIE", 5, 90.0, 30L, 10L, false));

        MobConfig config = MobConfigSchema.schema().bind(view(doc));

        assertThat(config.kind("greenfields.rotling").orElseThrow().base()).isEqualTo("ZOMBIE");
        assertThat(config.kind("greenfields.brute").orElseThrow().base()).isEqualTo("ZOMBIE");
        assertThat(config.kind("greenfields.brute").orElseThrow().xp())
                .as("und sie sind trotzdem unterscheidbar")
                .isNotEqualTo(config.kind("greenfields.rotling").orElseThrow().xp());
    }

    @Test
    @DisplayName("eine Horde, die auf eine unbekannte Art zeigt, bricht den Start ab")
    void anUnknownKindInAHordeRefusesTheStart() {
        Map<String, Object> doc = document();
        horde(doc).put("areas", Map.of("greenfields-east", List.of(entry("does.not.exist", 3))));

        assertThatThrownBy(() -> MobConfigSchema.schema().bind(view(doc)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does.not.exist")
                .hasMessageContaining("greenfields-east");
    }

    @Test
    @DisplayName("ein Gewicht unter eins wird abgelehnt")
    void aWeightBelowOneIsRefused() {
        Map<String, Object> doc = document();
        horde(doc).put("areas", Map.of("greenfields-east", List.of(entry("greenfields.rotling", 0))));

        assertThatThrownBy(() -> MobConfigSchema.schema().bind(view(doc)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("weight");
    }

    @Test
    @DisplayName("eine Bossart ohne 'boss: true' wird abgelehnt - ein Etikett muss etwas bedeuten")
    void aBossKindWithoutTheLabelIsRefused() {
        Map<String, Object> doc = document();
        @SuppressWarnings("unchecked")
        Map<String, Object> kinds = (Map<String, Object>) doc.get("kinds");
        kinds.put("greenfields.warden", kind("ZOMBIE", 10, 600.0, 400L, 250L, false));

        assertThatThrownBy(() -> MobConfigSchema.schema().bind(view(doc)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("boss: true");
    }

    @Test
    @DisplayName("ein unbekannter Attributname bricht ab statt still ignoriert zu werden")
    void anUnknownAttributeNameRefusesTheStart() {
        // Ein Tippfehler waere sonst eine Kreatur ohne Leben - und das faellt erst im Kampf auf.
        Map<String, Object> doc = document();
        @SuppressWarnings("unchecked")
        Map<String, Object> kinds = (Map<String, Object>) doc.get("kinds");
        @SuppressWarnings("unchecked")
        Map<String, Object> rotling = (Map<String, Object>) kinds.get("greenfields.rotling");
        rotling.put("attributes", Map.of("helth", 40.0));

        assertThatThrownBy(() -> MobConfigSchema.schema().bind(view(doc)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("helth")
                .hasMessageContaining("health");
    }

    @Test
    @DisplayName("ein Chunk-Budget ueber dem Zonenbudget bricht ab")
    void aChunkBudgetAboveTheZoneBudgetRefusesTheStart() {
        Map<String, Object> doc = document();
        doc.put("budget", budget(800, 100, 200, 25));

        assertThatThrownBy(() -> MobConfigSchema.schema().bind(view(doc)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("per-chunk");
    }

    @Test
    @DisplayName("die Summe der Zonenbudgets DARF ueber dem serverweiten liegen")
    void theSumOfZoneBudgetsMayExceedTheServerWideOne() {
        // FR-013a: sechs Zonen zu je 200 sind eine legitime Verteilung, solange nie 800
        // gleichzeitig stehen. Ein Fehler beim Start waere hier zu streng - die Grenze greift zur
        // Laufzeit, und genau dafuer ist sie da.
        Map<String, Object> doc = document();
        doc.put("budget", budget(800, 200, 12, 25));

        assertThat(MobConfigSchema.schema().bind(view(doc)).budget().perZone()).isEqualTo(200);
    }

    @Test
    @DisplayName("eine Art ohne Attribute wird abgelehnt")
    void aKindWithoutAttributesIsRefused() {
        Map<String, Object> doc = document();
        @SuppressWarnings("unchecked")
        Map<String, Object> kinds = (Map<String, Object>) doc.get("kinds");
        @SuppressWarnings("unchecked")
        Map<String, Object> rotling = (Map<String, Object>) kinds.get("greenfields.rotling");
        rotling.put("attributes", Map.of());

        assertThatThrownBy(() -> MobConfigSchema.schema().bind(view(doc)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attributes");
    }

    @Test
    @DisplayName("ein fehlendes Pflichtfeld nennt seinen Pfad")
    void aMissingRequiredFieldNamesItsPath() {
        Map<String, Object> doc = document();
        @SuppressWarnings("unchecked")
        Map<String, Object> kinds = (Map<String, Object>) doc.get("kinds");
        @SuppressWarnings("unchecked")
        Map<String, Object> rotling = (Map<String, Object>) kinds.get("greenfields.rotling");
        rotling.remove("follow-range");

        assertThatThrownBy(() -> MobConfigSchema.schema().bind(view(doc)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("kinds.greenfields.rotling.follow-range");
    }

    // --- fixtures ---

    private static Map<String, Object> document() {
        Map<String, Object> kinds = new LinkedHashMap<>();
        kinds.put("greenfields.rotling", kind("ZOMBIE", 3, 40.0, 12L, 4L, false));
        kinds.put("greenfields.warden", kind("ZOMBIE", 10, 600.0, 400L, 250L, true));

        Map<String, Object> horde = new LinkedHashMap<>();
        horde.put("areas", Map.of("greenfields-east", List.of(entry("greenfields.rotling", 3))));
        horde.put(
                "boss",
                Map.of(
                        "kind", "greenfields.warden",
                        "area", "greenfields-east",
                        "respawn-minutes", 30));

        Map<String, Object> doc = new LinkedHashMap<>();
        doc.put("budget", budget(800, 130, 12, 25));
        doc.put("admin-spawn-limit", 20);
        doc.put(
                "horde",
                Map.of(
                        "respawn-interval-ms", 2000,
                        "density-per-player", 0.2,
                        "cleanup-after-seconds", 60,
                        "cleanup-radius", 96.0,
                        "retarget-interval-ms", 500));
        doc.put("kinds", kinds);
        doc.put("hordes", new LinkedHashMap<>(Map.of("greenfields", horde)));
        return doc;
    }

    private static Map<String, Object> budget(int server, int zone, int chunk, int player) {
        return new LinkedHashMap<>(
                Map.of(
                        "server-wide", server,
                        "per-zone", zone,
                        "per-chunk", chunk,
                        "per-player", player));
    }

    private static Map<String, Object> kind(
            String base, int level, double health, long xp, long coins, boolean boss) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("base", base);
        body.put("level", level);
        body.put("attributes", new LinkedHashMap<>(Map.of("health", health, "defense", 2.0)));
        body.put("follow-range", 24.0);
        body.put("xp", xp);
        body.put("coins", coins);
        if (boss) {
            body.put("boss", true);
        }
        return body;
    }

    private static Map<String, Object> entry(String kindKey, int weight) {
        return new LinkedHashMap<>(Map.of("kind", kindKey, "weight", weight));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> horde(Map<String, Object> doc) {
        return (Map<String, Object>) ((Map<String, Object>) doc.get("hordes")).get("greenfields");
    }

    /** Ein Blick auf ein bereits geprüftes Dokument — genau das, was der Loader dem Binder gibt. */
    private static ConfigView view(Map<String, Object> document) {
        return new ConfigView() {
            @Override
            public int schemaVersion() {
                return MobConfigSchema.SCHEMA_VERSION;
            }

            @Override
            public String getString(String path) {
                return String.valueOf(document.get(path));
            }

            @Override
            public boolean getBoolean(String path) {
                return Boolean.TRUE.equals(document.get(path));
            }

            @Override
            public int getInt(String path) {
                return ((Number) document.get(path)).intValue();
            }

            @Override
            public long getLong(String path) {
                return ((Number) document.get(path)).longValue();
            }

            @Override
            public double getDouble(String path) {
                return ((Number) document.get(path)).doubleValue();
            }

            @Override
            public List<?> getList(String path) {
                return (List<?>) document.get(path);
            }

            @Override
            public Map<?, ?> getMap(String path) {
                return (Map<?, ?>) document.get(path);
            }
        };
    }
}
