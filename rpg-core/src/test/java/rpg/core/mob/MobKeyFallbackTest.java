package rpg.core.mob;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.combat.MobStatProvider;
import rpg.core.currency.MobCoinProvider;
import rpg.core.message.MessageKey;
import rpg.core.progression.MobXpProvider;
import rpg.core.stats.Attribute;
import rpg.core.stats.StatConfig;

/**
 * Die drei übernommenen Schnittstellen — und die Zusage, die dabei gehalten wird.
 *
 * <p>B05, B06 und B08b halten sie seit Monaten offen, jede mit demselben Satz im Javadoc: <em>„until
 * B10 exists, then B10 replaces the provider through this same interface"</em>. Dieser Test hält
 * fest, dass genau das passiert ist: <b>dieselbe Form, keine zweite Schnittstelle</b>.
 *
 * <p>Und die zweite Hälfte, die genauso wichtig ist: eine Art, die es nicht gibt, antwortet
 * <b>leer</b> und nicht mit Null. Ein leeres Ergebnis heißt „kein eigener Eintrag", und der Aufrufer
 * fällt auf seinen konfigurierten Standardwert zurück — ein Mob, den Mojang letzte Woche
 * hinzugefügt hat, soll nicht stillschweigend wertlos sein. Ein ausdrückliches {@code 0} in der
 * Konfiguration heißt dagegen Null, und die beiden müssen unterscheidbar bleiben.
 */
class MobKeyFallbackTest {

    @Test
    @DisplayName("eine bekannte Art liefert ihre Erfahrung und ihre Coins")
    void aKnownKindAnswersWithItsOwnNumbers() {
        MobXpProvider xp = MobProviders.xp(MobKeyFallbackTest::config);
        MobCoinProvider coins = MobProviders.coins(MobKeyFallbackTest::config);

        assertThat(xp.xpFor("probe.rotling")).isEqualTo(OptionalLong.of(12L));
        assertThat(coins.coinsFor("probe.rotling")).isEqualTo(OptionalLong.of(4L));
    }

    @Test
    @DisplayName("zwei Arten auf DERSELBEN Basis geben verschiedene Betraege")
    void twoKindsOnTheSameBaseGiveDifferentAmounts() {
        // Das ist die Verwechslung, wegen der es diesen Schluesselwechsel gibt: mit
        // getType().name() waeren beide "ZOMBIE" gewesen - dieselbe Erfahrung, dieselben Coins.
        MobXpProvider xp = MobProviders.xp(MobKeyFallbackTest::config);

        assertThat(config().kind("probe.rotling").orElseThrow().base())
                .isEqualTo(config().kind("probe.brute").orElseThrow().base());
        assertThat(xp.xpFor("probe.rotling")).isNotEqualTo(xp.xpFor("probe.brute"));
    }

    @Test
    @DisplayName("ein Vanilla-Typname antwortet LEER - nicht mit Null")
    void aVanillaTypeNameAnswersEmpty() {
        // Eine Kreatur ohne Vermerk kommt mit "ZOMBIE" hier an. B10 kennt sie nicht, antwortet leer,
        // und B06 beziehungsweise B08b fallen auf ihren eigenen Standardwert zurueck - genau so,
        // wie sie es taten, bevor es diesen Block gab (FR-009).
        MobXpProvider xp = MobProviders.xp(MobKeyFallbackTest::config);
        MobCoinProvider coins = MobProviders.coins(MobKeyFallbackTest::config);

        assertThat(xp.xpFor("ZOMBIE")).isEmpty();
        assertThat(coins.coinsFor("ZOMBIE")).isEmpty();
    }

    @Test
    @DisplayName("eine Art mit ausdruecklich null Coins antwortet mit null, nicht mit leer")
    void anExplicitZeroIsZeroAndNotEmpty() {
        // Der Unterschied, den FR-007 ausdruecklich verlangt: leer heisst "kein eigener Eintrag",
        // null heisst "diese Kreatur ist nichts wert". Ein Betreiber muss beides sagen koennen.
        MobCoinProvider coins = MobProviders.coins(MobKeyFallbackTest::config);

        assertThat(coins.coinsFor("probe.worthless")).isEqualTo(OptionalLong.of(0L));
    }

    @Test
    @DisplayName("die Werte kommen als Differenz gegen den Basiswert, nicht absolut")
    void statsComeAsADifferenceAgainstTheBase() {
        // Die Stat-Engine addiert Modifikatoren auf ihre Basis. Ein Absolutwert waere dieselbe Zahl
        // zweimal - genauso, wie B05s Uebergangsanbieter es rechnet.
        StatConfig stats = StatConfig.defaults();
        MobStatProvider provider = MobProviders.stats(MobKeyFallbackTest::config, stats);
        double base = stats.definition(Attribute.HEALTH).base();

        var set = provider.statsFor("probe.rotling").orElseThrow();

        assertThat(set.modifiers())
                .anySatisfy(
                        modifier -> {
                            if (modifier.attribute() == Attribute.HEALTH) {
                                assertThat(modifier.value()).isEqualTo(40.0 - base);
                            }
                        });
    }

    @Test
    @DisplayName("eine unbekannte Art bekommt keinen Werte-Satz - sie bleibt draussen")
    void anUnknownKindGetsNoStatSet() {
        MobStatProvider provider = MobProviders.stats(MobKeyFallbackTest::config, StatConfig.defaults());

        assertThat(provider.statsFor("ZOMBIE"))
                .as("B05s Uebergangsanbieter beantwortet sie, nicht dieser")
                .isEmpty();
    }

    // --- fixtures ---

    private static MobConfig config() {
        MobKind rotling = kind("probe.rotling", "ZOMBIE", 40.0, 12L, 4L);
        MobKind brute = kind("probe.brute", "ZOMBIE", 90.0, 30L, 10L);
        MobKind worthless = kind("probe.worthless", "SLIME", 10.0, 0L, 0L);
        return new MobConfig(
                new Budget(800, 130, 12, 25),
                Duration.ofSeconds(2),
                0.2,
                Duration.ofSeconds(60),
                96.0,
                Duration.ofMillis(500),
                Map.of(
                        rotling.key(), rotling,
                        brute.key(), brute,
                        worthless.key(), worthless),
                Map.of(
                        "greenfields",
                        new HordeSpec(
                                "greenfields",
                                List.of(new HordeSpec.Entry("greenfields-east", "probe.rotling", 1)),
                                null)));
    }

    private static MobKind kind(String key, String base, double health, long xp, long coins) {
        return new MobKind(
                key,
                base,
                3,
                Map.of(Attribute.HEALTH, health, Attribute.DEFENSE, 2.0),
                24.0,
                MessageKey.of("mob." + key + ".name"),
                xp,
                coins,
                false);
    }
}
