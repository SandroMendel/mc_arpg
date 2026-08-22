package rpg.plugin;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.EnumMap;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import rpg.core.classes.CharacterClassDefinition;
import rpg.core.classes.ClassConfig;
import rpg.core.classes.ClassConfigSchema;
import rpg.core.classes.EquipmentLadder;
import rpg.core.classes.LadderSlot;
import rpg.core.config.ConfigSchema;
import rpg.core.config.SchemaValidator;
import rpg.core.session.CharacterClass;
import rpg.core.stats.Attribute;
import rpg.core.stats.AttributeDefinition;

/**
 * T066, T067, T069 - das Wertbudget der <b>ausgelieferten</b> {@code classes.yml}.
 *
 * <p><b>Warum dieser Test in rpg-plugin liegt und nicht in rpg-core</b>, wie die Aufgabenliste
 * vorsah: die ausgelieferte Datei und der YAML-Parser liegen beide ausserhalb von {@code rpg-core} -
 * SnakeYAML kommt über Paper, und {@code rpg-core} hat keine einzige Abhängigkeit. Derselbe Grund,
 * aus dem {@code ShippedProgressionConfigTest} hier liegt.
 *
 * <p>Der Test rechnet die Summen <b>selbst</b> aus der geladenen Konfiguration. Hinterlegte
 * Erwartungswerte hätten die Tabelle in der Spec geprüft statt die Datei, die der Server liest.
 */
class ShippedClassConfigTest {

    /** Die Caps aus {@code stats.yml}, hier als Vergleichsgrösse. */
    private static final Map<Attribute, Double> CAPS = caps();

    /** Aus {@code progression.yml}: Level 60 ist das Ende der Levelprogression. */
    private static final int MAX_LEVEL = 60;

    @Test
    @DisplayName("die ausgelieferte classes.yml besteht das Schema, für das sie geschrieben wurde")
    void shippedConfigurationIsValid() throws Exception {
        ClassConfig config = shipped();

        assertThat(config.definitions()).hasSize(3);
        for (CharacterClass id : CharacterClass.values()) {
            assertThat(config.definition(id)).isNotNull();
        }
    }

    @Test
    @DisplayName("die Leiterlängen sind 6/6, 6/6 und 7/7 - nicht überall gleich (FR-013)")
    void ladderLengthsAreNotUniform() throws Exception {
        ClassConfig config = shipped();

        assertThat(lengths(config, CharacterClass.WARRIOR)).containsExactly(6, 6);
        assertThat(lengths(config, CharacterClass.ROGUE)).containsExactly(6, 6);
        assertThat(lengths(config, CharacterClass.MAGE)).containsExactly(7, 7);
    }

    @Test
    @DisplayName("T069: kein Endwert überschreitet seinen Cap aus ADR-008")
    void noValueExceedsItsCap() throws Exception {
        shipped().validateAgainstCaps(ShippedClassConfigTest::definitionOf, MAX_LEVEL);
    }

    @Test
    @DisplayName("T066: jeder Endwert liegt unter seinem Cap, mit Abweichung unter 3 % (SC-004)")
    void endValuesSitJustUnderTheirCaps() throws Exception {
        ClassConfig config = shipped();

        // Je Attribut reizt genau eine Klasse den Cap aus; die anderen bleiben darunter. Geprüft wird
        // deshalb das Maximum über die drei Klassen gegen den Cap.
        for (Attribute attribute : Attribute.all()) {
            double cap = CAPS.get(attribute);
            if (cap <= 0.0) {
                continue;
            }
            double best = 0.0;
            for (CharacterClass id : CharacterClass.values()) {
                best = Math.max(best, endValue(config.definition(id), attribute));
            }
            assertThat(best)
                    .as("%s: höchster Endwert über alle Klassen gegen Cap %s", attribute.key(), cap)
                    .isLessThanOrEqualTo(cap)
                    .isGreaterThanOrEqualTo(cap * 0.97);
        }
    }

    @Test
    @DisplayName("T067: die Leiter trägt 60 bis 80 % des Zuwachses der Attribute, die sie führt (SC-005)")
    void ladderCarriesTheDominantShare() throws Exception {
        ClassConfig config = shipped();

        for (CharacterClass id : CharacterClass.values()) {
            CharacterClassDefinition definition = config.definition(id);
            for (Attribute attribute : Attribute.all()) {
                if (definition.growth().perLevel(attribute) == 0.0) {
                    // Die drei prozentualen Attribute kommen vollständig aus der Leiter und sind von
                    // dieser Spanne ausdrücklich ausgenommen (SC-005).
                    continue;
                }
                if (attribute == Attribute.HEALTH_REGEN || attribute == Attribute.MANA_REGEN) {
                    // Der Gegenfall, und ebenso ausdrücklich: die beiden Regenerationsraten kommen
                    // vollständig aus dem Levelwachstum und liegen auf keiner Leiter (ADR-023).
                    // Damit ist ihr Leiteranteil null - keine Abweichung, sondern die Entscheidung.
                    continue;
                }
                double start = startValue(definition, attribute);
                double end = endValue(definition, attribute);
                double ladderShare = ladderGain(definition, attribute) / (end - start);
                assertThat(ladderShare)
                        .as("%s %s: Leiteranteil am Zuwachs", id, attribute.key())
                        .isBetween(0.60, 0.80);
            }
        }
    }

    @Test
    @DisplayName("die drei prozentualen Attribute wachsen nicht mit dem Level")
    void percentAttributesDoNotGrowWithLevel() throws Exception {
        ClassConfig config = shipped();

        for (CharacterClass id : CharacterClass.values()) {
            CharacterClassDefinition definition = config.definition(id);
            assertThat(definition.growth().perLevel(Attribute.ATTACK_SPEED)).isZero();
            assertThat(definition.growth().perLevel(Attribute.MOVEMENT_SPEED)).isZero();
            assertThat(definition.growth().perLevel(Attribute.ABILITY_COOLDOWN)).isZero();
        }
    }

    @Test
    @DisplayName("die Rollenprofile stimmen: je Attribut reizt genau eine Klasse ihren Bereich aus")
    void roleProfilesAreDistinct() throws Exception {
        ClassConfig config = shipped();

        assertThat(strongestIn(config, Attribute.HEALTH)).isEqualTo(CharacterClass.WARRIOR);
        assertThat(strongestIn(config, Attribute.DEFENSE)).isEqualTo(CharacterClass.WARRIOR);
        assertThat(strongestIn(config, Attribute.PHYSICAL_DAMAGE)).isEqualTo(CharacterClass.WARRIOR);
        // Der Warrior führt healthRegen, weil er das grösste Gefäss füllt - alle drei Klassen sind
        // nach derselben Zeit wieder voll, und genau deshalb regeneriert er am schnellsten.
        assertThat(strongestIn(config, Attribute.HEALTH_REGEN)).isEqualTo(CharacterClass.WARRIOR);
        assertThat(strongestIn(config, Attribute.MANA)).isEqualTo(CharacterClass.MAGE);
        assertThat(strongestIn(config, Attribute.MANA_REGEN)).isEqualTo(CharacterClass.MAGE);
        assertThat(strongestIn(config, Attribute.MAGIC_DAMAGE)).isEqualTo(CharacterClass.MAGE);
        assertThat(strongestIn(config, Attribute.ABILITY_COOLDOWN)).isEqualTo(CharacterClass.MAGE);
        assertThat(strongestIn(config, Attribute.ATTACK_SPEED)).isEqualTo(CharacterClass.ROGUE);
        assertThat(strongestIn(config, Attribute.MOVEMENT_SPEED)).isEqualTo(CharacterClass.ROGUE);
    }

    @Test
    @DisplayName("jede Klasse hat sechs Fähigkeiten mit genau einer Unique (FR-041)")
    void everyLoadoutIsComplete() throws Exception {
        // Seit B08 sind alle drei gefüllt. Bis dahin stand hier "Mage und Rogue bleiben leer" - eine
        // Zusage mit Verfallsdatum, und sie ist verfallen.
        //
        // Was B07 hier prüft, ist nur die Form: sechs Einträge, eine Unique. Ob die Aufteilung 4+2
        // oder 3+3 ist und ob die IDs überhaupt existieren, gehört B08 und steht in
        // ShippedAbilityConfigTest - zwei Stellen, die dasselbe prüfen, wären eine zu viel.
        for (CharacterClass id : CharacterClass.values()) {
            CharacterClassDefinition definition = shipped().definition(id);

            assertThat(definition.abilities()).as(id + " hat sechs").hasSize(6);
            assertThat(definition.abilities().stream().filter(a -> a.unique()).count())
                    .as(id + " hat genau eine Unique")
                    .isEqualTo(1);
        }
    }

    // --- helpers ----------------------------------------------------------------------------

    private static CharacterClass strongestIn(ClassConfig config, Attribute attribute) {
        CharacterClass best = null;
        double bestValue = Double.NEGATIVE_INFINITY;
        for (CharacterClass id : CharacterClass.values()) {
            double value = endValue(config.definition(id), attribute);
            if (value > bestValue) {
                bestValue = value;
                best = id;
            }
        }
        return best;
    }

    /** Basis + Stufe 1 - der Wert auf Level 1. */
    private static double startValue(CharacterClassDefinition definition, Attribute attribute) {
        double value = definition.baseStats().of(attribute);
        for (LadderSlot slot : LadderSlot.values()) {
            if (slot.carried().contains(attribute)) {
                value += definition.ladder(slot).tier(1).valueOf(attribute);
            }
        }
        return value;
    }

    /** Basis + Levelwachstum bis 60 + Endstufe. */
    private static double endValue(CharacterClassDefinition definition, Attribute attribute) {
        double value = definition.baseStats().of(attribute);
        value += definition.growth().perLevel(attribute) * (MAX_LEVEL - 1);
        for (LadderSlot slot : LadderSlot.values()) {
            if (slot.carried().contains(attribute)) {
                value += definition.ladder(slot).top().valueOf(attribute);
            }
        }
        return value;
    }

    /** Was die Leiter von Stufe 1 bis zur Endstufe beisteuert. */
    private static double ladderGain(CharacterClassDefinition definition, Attribute attribute) {
        double gain = 0.0;
        for (LadderSlot slot : LadderSlot.values()) {
            if (slot.carried().contains(attribute)) {
                EquipmentLadder ladder = definition.ladder(slot);
                gain += ladder.top().valueOf(attribute) - ladder.tier(1).valueOf(attribute);
            }
        }
        return gain;
    }

    private static int[] lengths(ClassConfig config, CharacterClass id) {
        CharacterClassDefinition definition = config.definition(id);
        return new int[] {definition.armorLadder().length(), definition.weaponLadder().length()};
    }

    private static ClassConfig shipped() throws Exception {
        Map<String, Object> document = load("/classes.yml");
        ConfigSchema<ClassConfig> schema = ClassConfigSchema.schema();
        return schema.bind(SchemaValidator.validate(Path.of("classes.yml"), document, schema));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> load(String resource) throws Exception {
        try (InputStream stream = ShippedClassConfigTest.class.getResourceAsStream(resource)) {
            if (stream == null) {
                throw new IllegalStateException(resource + " is not on the classpath");
            }
            return new Yaml()
                    .load(new String(stream.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private static Map<Attribute, Double> caps() {
        Map<Attribute, Double> caps = new EnumMap<>(Attribute.class);
        caps.put(Attribute.HEALTH, 2000.0);
        caps.put(Attribute.HEALTH_REGEN, 40.0);
        caps.put(Attribute.DEFENSE, 300.0);
        caps.put(Attribute.MANA, 500.0);
        caps.put(Attribute.MANA_REGEN, 20.0);
        caps.put(Attribute.PHYSICAL_DAMAGE, 150.0);
        caps.put(Attribute.MAGIC_DAMAGE, 150.0);
        caps.put(Attribute.ABILITY_COOLDOWN, 0.40);
        // attackSpeed und movementSpeed haben in stats.yml einen technischen Höchstwert (1024 bzw.
        // 1.0), der keine Balancing-Grenze ist - sie werden hier nicht gegen einen Cap geprüft.
        caps.put(Attribute.ATTACK_SPEED, 0.0);
        caps.put(Attribute.MOVEMENT_SPEED, 0.0);
        return caps;
    }

    private static AttributeDefinition definitionOf(Attribute attribute) {
        return switch (attribute) {
            case HEALTH -> new AttributeDefinition(attribute, 100.0, 1.0, 2000.0, 0.0);
            case HEALTH_REGEN -> new AttributeDefinition(attribute, 0.0, 0.0, 40.0, 0.0);
            case DEFENSE -> new AttributeDefinition(attribute, 0.0, 0.0, 300.0, 0.0);
            case MANA -> new AttributeDefinition(attribute, 50.0, 0.0, 500.0, 0.0);
            case MANA_REGEN -> new AttributeDefinition(attribute, 0.0, 0.0, 20.0, 0.0);
            case PHYSICAL_DAMAGE -> new AttributeDefinition(attribute, 5.0, 0.0, 150.0, 0.0);
            case MAGIC_DAMAGE -> new AttributeDefinition(attribute, 5.0, 0.0, 150.0, 0.0);
            case ATTACK_SPEED -> new AttributeDefinition(attribute, 4.0, 0.0, 1024.0, 0.50);
            case MOVEMENT_SPEED -> new AttributeDefinition(attribute, 0.1, 0.0, 1.0, 0.30);
            case ABILITY_COOLDOWN -> new AttributeDefinition(attribute, 0.0, 0.0, 0.40, 0.0);
        };
    }
}
