package rpg.core.item;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.session.CharacterClass;

/**
 * Wann ein Trank wirkt — und die vier Gründe, aus denen er es nicht tut (FR-033 bis FR-037).
 *
 * <p><b>Die tragende Zusage steht im letzten Abschnitt:</b> in <em>jedem</em> abgelehnten Fall ist
 * nichts verbraucht und die Abklingzeit läuft nicht an. Andernfalls kostete ein abgelehnter Versuch
 * den Spieler die nächste Gelegenheit — und das wäre eine Bestrafung dafür, dass er etwas versucht
 * hat, was ohnehin nicht ging.
 */
class ConsumableUseTest {

    private static final UUID CHARACTER = UUID.randomUUID();

    private final MutableClock clock = new MutableClock(Instant.parse("2026-08-28T12:00:00Z"));
    private final ConsumableCooldown cooldowns = new ConsumableCooldown(clock);

    @Test
    @DisplayName("ein passender Trank wird benutzt und verbraucht")
    void amatchingPotionIsUsed() {
        ConsumableUse use = use(alwaysUseful());

        ConsumableUse.Result result =
                use.use(Optional.of(potion(null, null)), CHARACTER, 1, CharacterClass.WARRIOR);

        assertThat(result.isSuccess()).isTrue();
        assertThat(result.outcome()).isEqualTo(ConsumableUse.Outcome.USED);
    }

    @Test
    @DisplayName("zu niedriges Level - abgelehnt, nichts verbraucht")
    void tooLowALevelIsRefused() {
        ConsumableUse use = use(alwaysUseful());

        ConsumableUse.Result result =
                use.use(Optional.of(potion(20, null)), CHARACTER, 5, CharacterClass.WARRIOR);

        assertThat(result.outcome()).isEqualTo(ConsumableUse.Outcome.LEVEL_TOO_LOW);
        assertThat(cooldowns.size()).as("nichts vermerkt").isZero();
    }

    @Test
    @DisplayName("falsche Klasse - abgelehnt, nichts verbraucht")
    void thewrongClassIsRefused() {
        ConsumableUse use = use(alwaysUseful());

        ConsumableUse.Result result =
                use.use(
                        Optional.of(potion(null, CharacterClass.MAGE)),
                        CHARACTER,
                        60,
                        CharacterClass.WARRIOR);

        assertThat(result.outcome()).isEqualTo(ConsumableUse.Outcome.WRONG_CLASS);
        assertThat(cooldowns.size()).isZero();
    }

    @Test
    @DisplayName("innerhalb der Abklingzeit - abgelehnt, und die Meldung sagt WIE LANGE noch")
    void withinTheCooldownIsRefusedWithATime() {
        ConsumableUse use = use(alwaysUseful());
        ItemTemplate potion = potion(null, null);

        assertThat(use.use(Optional.of(potion), CHARACTER, 60, CharacterClass.WARRIOR).isSuccess())
                .isTrue();

        clock.advance(Duration.ofSeconds(3));
        ConsumableUse.Result second =
                use.use(Optional.of(potion), CHARACTER, 60, CharacterClass.WARRIOR);

        assertThat(second.outcome()).isEqualTo(ConsumableUse.Outcome.ON_COOLDOWN);
        assertThat(second.remaining())
                .as("'noch fuenf Sekunden' ist eine Antwort, 'geht nicht' ist keine (FR-037)")
                .isEqualTo(Duration.ofSeconds(5));
    }

    @Test
    @DisplayName("nach der Abklingzeit geht es wieder")
    void afterTheCooldownItWorksAgain() {
        ConsumableUse use = use(alwaysUseful());
        ItemTemplate potion = potion(null, null);

        use.use(Optional.of(potion), CHARACTER, 60, CharacterClass.WARRIOR);
        clock.advance(Duration.ofSeconds(8));

        assertThat(use.use(Optional.of(potion), CHARACTER, 60, CharacterClass.WARRIOR).isSuccess())
                .isTrue();
    }

    @Test
    @DisplayName("FR-036 - was nichts bewirken wuerde, wird abgelehnt statt wirkungslos verbraucht")
    void whatWouldDoNothingIsRefused() {
        // Ein Heiltrank bei vollem Leben. Ihn zu schlucken kostet den Spieler einen Trank und
        // bringt nichts - und das merkt er erst, wenn er ihn braucht.
        ConsumableUse use = use((characterId, effect) -> false);

        ConsumableUse.Result result =
                use.use(Optional.of(potion(null, null)), CHARACTER, 60, CharacterClass.WARRIOR);

        assertThat(result.outcome()).isEqualTo(ConsumableUse.Outcome.NO_EFFECT);
        assertThat(cooldowns.size()).isZero();
    }

    @Test
    @DisplayName("eine unbekannte Vorlage ist kein Absturz, sondern eine Ablehnung (FR-007)")
    void anUnknownTemplateIsRefused() {
        ConsumableUse use = use(alwaysUseful());

        assertThat(use.use(Optional.empty(), CHARACTER, 60, CharacterClass.WARRIOR).outcome())
                .isEqualTo(ConsumableUse.Outcome.UNKNOWN_TEMPLATE);
    }

    @Test
    @DisplayName("eine Kosmetik ist kein Trank - und ihr Trinken keine Benutzung")
    void acosmeticIsNotAPotion() {
        ConsumableUse use = use(alwaysUseful());
        ItemTemplate trim =
                new ItemTemplate(
                        "trim.test",
                        ItemCategory.COSMETIC,
                        "NETHERITE_UPGRADE_SMITHING_TEMPLATE",
                        Rarity.LEGENDARY,
                        null,
                        null,
                        null,
                        null,
                        null,
                        new CosmeticAppearance("REDSTONE", "RAISER"));

        assertThat(use.use(Optional.of(trim), CHARACTER, 60, CharacterClass.WARRIOR).outcome())
                .isEqualTo(ConsumableUse.Outcome.UNKNOWN_TEMPLATE);
    }

    @Test
    @DisplayName("KEIN abgelehnter Fall vermerkt eine Benutzung - die tragende Zusage")
    void noRefusalEverMarksAUse() {
        ConsumableUse levelBlocked = use(alwaysUseful());
        levelBlocked.use(Optional.of(potion(20, null)), CHARACTER, 5, CharacterClass.WARRIOR);
        levelBlocked.use(
                Optional.of(potion(null, CharacterClass.MAGE)), CHARACTER, 60, CharacterClass.WARRIOR);
        levelBlocked.use(Optional.empty(), CHARACTER, 60, CharacterClass.WARRIOR);
        use((characterId, effect) -> false)
                .use(Optional.of(potion(null, null)), CHARACTER, 60, CharacterClass.WARRIOR);

        assertThat(cooldowns.size())
                .as(
                        "ein abgelehnter Versuch darf den Spieler nicht die naechste Gelegenheit"
                                + " kosten")
                .isZero();
    }

    // --- Hilfsmittel ---------------------------------------------------------------

    private ConsumableUse use(ConsumableUse.WouldDoSomething wouldDoSomething) {
        return new ConsumableUse(cooldowns, wouldDoSomething);
    }

    private static ConsumableUse.WouldDoSomething alwaysUseful() {
        return (characterId, effect) -> true;
    }

    private static ItemTemplate potion(Integer minLevel, CharacterClass boundClass) {
        return new ItemTemplate(
                "potion.test",
                ItemCategory.CONSUMABLE,
                "POTION",
                Rarity.COMMON,
                minLevel,
                boundClass,
                3L,
                null,
                ConsumableEffect.healing(40.0, Duration.ofSeconds(8)),
                null);
    }

    /** Eine Uhr, die sich stellen lässt — Abklingzeiten ohne Warten. */
    private static final class MutableClock extends Clock {

        private Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void advance(Duration by) {
            now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
