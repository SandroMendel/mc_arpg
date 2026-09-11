package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.MobStatProvider;
import rpg.core.message.MessageKey;
import rpg.core.stats.Attribute;
import rpg.core.stats.ModifierSet;
import rpg.core.stats.StatConfig;
import rpg.core.stats.StatModifier;

/**
 * T022, FR-004: mehrere Arten auf demselben Vanilla-Entity - und sie bleiben unterscheidbar.
 *
 * <p><b>Warum das der Kern dieses Blocks ist.</b> Acht Arten je Region auf sechs Regionen sind 48,
 * und Minecraft hat keine 48 passenden Entity-Typen. Mehrere Arten teilen sich also eine Basis.
 * Genau daran ist der bisherige Schluessel gescheitert: {@code creature.getType().name()} antwortet
 * fuer vier verschiedene Kreaturen vier Mal {@code ZOMBIE} - dieselben Werte, dieselbe Erfahrung,
 * dieselben Coins. Das sieht im Spiel nach Balancing aus und nicht nach einem Fehler, und deshalb
 * faellt es ohne einen Test wie diesen niemandem auf.
 *
 * <p><b>Was hier geprueft wird und was woanders steht.</b> {@code MobKeyFallbackTest} zeigt, dass
 * zwei Arten auf derselben Basis verschiedene <em>Betraege</em> geben, und dass ein Vanilla-Typname
 * leer antwortet. Dieser Test geht den Weg davor: die <b>Abfrage</b> {@link MobKinds} - von der
 * Kennung und von der Entitaet aus - und die <b>Attributwerte</b>, also das, was eine Kreatur im
 * Kampf ist. Zwei Zombies, zwei Antworten, an jeder der Stellen.
 */
class MobKindLookupTest {

    private static final String ROTLING = "probe.rotling";
    private static final String BRUTE = "probe.brute";
    private static final String BASE = "ZOMBIE";

    @Test
    @DisplayName("zwei Arten auf derselben Basis sind zwei verschiedene Eintraege")
    void twoKindsOnTheSameBaseAreTwoDifferentEntries() {
        MobKinds kinds = MobKinds.backedBy(MobKindLookupTest::config, new HordeRegistry());

        MobKind rotling = kinds.find(ROTLING).orElseThrow();
        MobKind brute = kinds.find(BRUTE).orElseThrow();

        assertThat(rotling.base())
                .as("die Voraussetzung des ganzen Tests: es ist wirklich dieselbe Basis")
                .isEqualTo(BASE)
                .isEqualTo(brute.base());
        assertThat(rotling).isNotEqualTo(brute);
        assertThat(rotling.level()).isNotEqualTo(brute.level());
        assertThat(rotling.displayNameKey()).isNotEqualTo(brute.displayNameKey());
        assertThat(rotling.attributeOr(Attribute.HEALTH, 0.0))
                .isNotEqualTo(brute.attributeOr(Attribute.HEALTH, 0.0));
    }

    @Test
    @DisplayName("zwei Entitaeten auf derselben Basis werden auseinandergehalten")
    void twoEntitiesOnTheSameBaseAreToldApart() {
        // Das ist FR-004 im laufenden Betrieb: nicht zwei Zeilen in einer Konfiguration, sondern
        // zwei Kreaturen, die im Spiel nebeneinander stehen. Ohne den Vermerk je Entitaet waere
        // hier fuer beide dasselbe herausgekommen.
        HordeRegistry registry = new HordeRegistry();
        UUID one = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        Instant when = Instant.parse("2026-08-24T20:00:00Z");
        registry.add(new HordeRegistry.Entry(one, ROTLING, "greenfields", 0L, when));
        registry.add(new HordeRegistry.Entry(other, BRUTE, "greenfields", 0L, when));

        MobKinds kinds = MobKinds.backedBy(MobKindLookupTest::config, registry);

        assertThat(kinds.ofEntity(one).orElseThrow().key()).isEqualTo(ROTLING);
        assertThat(kinds.ofEntity(other).orElseThrow().key()).isEqualTo(BRUTE);
        assertThat(kinds.ofEntity(one)).isNotEqualTo(kinds.ofEntity(other));
    }

    @Test
    @DisplayName("die Kampfwerte folgen der Art und nicht der Basis")
    void theCombatValuesFollowTheKindAndNotTheBase() {
        // Der Weg, auf dem der Unterschied im Spiel ankommt: B04 setzt, was hier herauskommt.
        // Waere der Schluessel weiterhin der Vanilla-Typ, stuenden hier zwei gleiche Saetze.
        StatConfig stats = StatConfig.defaults();
        MobStatProvider provider = MobProviders.stats(MobKindLookupTest::config, stats);
        double base = stats.definition(Attribute.HEALTH).base();

        ModifierSet rotling = provider.statsFor(ROTLING).orElseThrow();
        ModifierSet brute = provider.statsFor(BRUTE).orElseThrow();

        assertThat(healthOf(rotling)).isEqualTo(40.0 - base);
        assertThat(healthOf(brute)).isEqualTo(90.0 - base);
        assertThat(rotling.source())
                .as("und die beiden Saetze kommen aus zwei unterscheidbaren Quellen")
                .isNotEqualTo(brute.source());
    }

    @Test
    @DisplayName("all() nennt beide Arten, obwohl sie sich eine Basis teilen")
    void allNamesBothKindsEvenThoughTheyShareABase() {
        MobKinds kinds = MobKinds.backedBy(MobKindLookupTest::config, new HordeRegistry());

        assertThat(kinds.all()).extracting(MobKind::key).contains(ROTLING, BRUTE);
        assertThat(kinds.all()).extracting(MobKind::base).containsOnly(BASE);
    }

    // --- fixtures ---

    private static double healthOf(ModifierSet set) {
        return set.modifiers().stream()
                .filter(modifier -> modifier.attribute() == Attribute.HEALTH)
                .mapToDouble(StatModifier::value)
                .findFirst()
                .orElseThrow();
    }

    /** Zwei Arten, eine Basis - alles andere unterscheidet sich. */
    private static MobConfig config() {
        MobKind rotling = kind(ROTLING, 3, 40.0, 12L, 4L);
        MobKind brute = kind(BRUTE, 9, 90.0, 30L, 10L);
        return new MobConfig(
                new Budget(800, 130, 12, 25),
                Duration.ofSeconds(2),
                0.2,
                Duration.ofSeconds(60),
                96.0,
                Duration.ofMillis(500),
                Map.of(rotling.key(), rotling, brute.key(), brute),
                Map.of(
                        "greenfields",
                        new HordeSpec(
                                "greenfields",
                                List.of(
                                        new HordeSpec.Entry("greenfields-east", ROTLING, 4),
                                        new HordeSpec.Entry("greenfields-east", BRUTE, 1)),
                                null)));
    }

    private static MobKind kind(String key, int level, double health, long xp, long coins) {
        return new MobKind(
                key,
                BASE,
                level,
                Map.of(Attribute.HEALTH, health, Attribute.DEFENSE, 2.0),
                24.0,
                MessageKey.of("mob." + key + ".name"),
                xp,
                coins,
                false);
    }
}
