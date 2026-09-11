package rpg.platform.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.currency.BookingReason;
import rpg.core.currency.BookingResult;
import rpg.core.currency.Currency;
import rpg.core.message.MapMessages;
import rpg.core.message.Messages;
import rpg.core.statistics.ClaimOutcome;
import rpg.core.statistics.RewardClaim;
import rpg.core.statistics.StatisticsConfig;
import rpg.core.statistics.StatisticsMessageKeys;

/**
 * FR-055 — <b>kein Platz heißt: Ablehnung mit Begründung, Anspruch bleibt offen, und er wird gar
 * nicht erst markiert.</b>
 *
 * <p>Der letzte Teil ist der entscheidende. Die Reihenfolge „markieren, dann gutschreiben"
 * schützt gegen den <em>unvorhersehbaren</em> Fehler — einen Absturz, einen Verbindungsabbruch.
 * Ein volles Inventar ist keiner davon: es ist vorher bekannt. Es in dieselbe Reihenfolge zu
 * stecken hieße, einen Anspruch für einen Fall zu verbrauchen, den man hätte kommen sehen — und
 * der Spieler stünde mit leeren Händen und ohne Anspruch da, weil er einen Stapel zu viel
 * dabeihatte.
 *
 * <p>Deshalb steht die Platzprüfung <b>vor</b> dem bedingten Update, und dieser Test hält genau
 * das fest: bei vollem Inventar wird die Markierung nie versucht.
 */
class FullInventoryKeepsTheClaimTest {

    private static final String SEASON = "2026-q2";
    private static final UUID ACCOUNT = UUID.randomUUID();
    private static final UUID CHARACTER = UUID.randomUUID();

    @Test
    @DisplayName("FR-055 - volles Inventar: abgelehnt, und NICHT markiert")
    void afullInventoryIsRefusedAndNotMarked() {
        AtomicInteger markAttempts = new AtomicInteger();
        Recorder told = new Recorder();

        SeasonRewardClaimListener listener =
                listener(
                        (playerId, stacks) -> false,
                        (playerId, template, amount) -> true,
                        (season, playerId, character, at) -> {
                            markAttempts.incrementAndGet();
                            return true;
                        },
                        told,
                        new CoinRecorder());

        assertThat(listener.claim(ACCOUNT, CHARACTER, SEASON))
                .isEqualTo(ClaimOutcome.INVENTORY_FULL);
        assertThat(markAttempts)
                .as("die Markierung darf gar nicht erst versucht werden")
                .hasValue(0);
        assertThat(told.messages).anyMatch(text -> text.contains("stays yours"));
    }

    @Test
    @DisplayName("FR-055 - und keine Coins fliessen, obwohl Coins Platz haetten")
    void andNoCoinsFlowEvenThoughCoinsWouldFit() {
        CoinRecorder coins = new CoinRecorder();

        listener(
                        (playerId, stacks) -> false,
                        (playerId, template, amount) -> true,
                        (season, playerId, character, at) -> true,
                        new Recorder(),
                        coins)
                .claim(ACCOUNT, CHARACTER, SEASON);

        // Eine halbe Einloesung waere schlimmer als keine: der Anspruch traegt Coins UND
        // Gegenstaende, und er wird als Ganzes eingeloest oder gar nicht.
        assertThat(coins.credited).isZero();
    }

    @Test
    @DisplayName("mit Platz laeuft alles durch: markiert, Coins gutgeschrieben, Gegenstand geliefert")
    void withRoomEverythingGoesThrough() {
        CoinRecorder coins = new CoinRecorder();
        List<String> delivered = new ArrayList<>();
        Recorder told = new Recorder();

        ClaimOutcome outcome =
                listener(
                                (playerId, stacks) -> true,
                                (playerId, template, amount) -> delivered.add(template),
                                (season, playerId, character, at) -> true,
                                told,
                                coins)
                        .claim(ACCOUNT, CHARACTER, SEASON);

        assertThat(outcome).isEqualTo(ClaimOutcome.CLAIMED);
        assertThat(coins.credited).isEqualTo(5_000L);
        assertThat(coins.reason).isEqualTo(BookingReason.SEASON_REWARD);
        assertThat(delivered).containsExactly("trim.ember");
        assertThat(told.messages).anyMatch(text -> text.contains("Claimed"));
    }

    @Test
    @DisplayName("wer das Rennen ums Markieren verliert, bekommt BEREITS EINGELOEST")
    void whoeverLosesTheRaceGetsAlreadyClaimed() {
        CoinRecorder coins = new CoinRecorder();

        ClaimOutcome outcome =
                listener(
                                (playerId, stacks) -> true,
                                (playerId, template, amount) -> true,
                                (season, playerId, character, at) -> false,
                                new Recorder(),
                                coins)
                        .claim(ACCOUNT, CHARACTER, SEASON);

        assertThat(outcome).isEqualTo(ClaimOutcome.ALREADY_CLAIMED);
        assertThat(coins.credited).as("und schreibt nichts gut").isZero();
    }

    @Test
    @DisplayName("ohne offenen Anspruch passiert nichts")
    void withoutAnOpenClaimNothingHappens() {
        CoinRecorder coins = new CoinRecorder();

        SeasonRewardClaimListener listener =
                new SeasonRewardClaimListener(
                        coins,
                        (playerId, stacks) -> true,
                        (playerId, template, amount) -> true,
                        (season, playerId, character, at) -> true,
                        playerId -> List.of(),
                        new Recorder(),
                        (playerId, details) -> {},
                        messages(),
                        Logger.getLogger("test"),
                        Clock.fixed(Instant.now(), ZoneOffset.UTC));

        assertThat(listener.claim(ACCOUNT, CHARACTER, SEASON))
                .isEqualTo(ClaimOutcome.NOTHING_TO_CLAIM);
        assertThat(coins.credited).isZero();
    }

    @Test
    @DisplayName("FR-052 - offene Ansprueche werden beim Wiederkommen ANGEBOTEN, nicht eingeloest")
    void openClaimsAreOfferedNotClaimed() {
        AtomicInteger markAttempts = new AtomicInteger();
        Recorder told = new Recorder();

        listener(
                        (playerId, stacks) -> true,
                        (playerId, template, amount) -> true,
                        (season, playerId, character, at) -> {
                            markAttempts.incrementAndGet();
                            return true;
                        },
                        told,
                        new CoinRecorder())
                .offerOpenClaims(ACCOUNT);

        // Angeboten, nicht aufgedraengt: die Einloesung braucht eine Figur und einen freien Platz,
        // und beides entscheidet der Spieler.
        assertThat(told.messages).anyMatch(text -> text.contains("waiting"));
        assertThat(markAttempts).hasValue(0);
    }

    @Test
    @DisplayName("FR-056 - jede Einloesung hinterlaesst einen Protokolleintrag")
    void everyClaimLeavesAnAuditEntry() {
        List<Map<String, String>> audited = new ArrayList<>();

        listener(
                        (playerId, stacks) -> true,
                        (playerId, template, amount) -> true,
                        (season, playerId, character, at) -> true,
                        new Recorder(),
                        new CoinRecorder(),
                        audited)
                .claim(ACCOUNT, CHARACTER, SEASON);

        // Der Buchungsgrund im Coin-Ledger deckt nur die Coins ab. Ein Anspruch aus reinen
        // Gegenstaenden haette ohne diesen Eintrag keine Spur hinterlassen - und FR-056 verlangt
        // JEDE Einloesung.
        assertThat(audited).hasSize(1);
        assertThat(audited.get(0))
                .containsEntry("season", SEASON)
                .containsEntry("rank", "1")
                .containsEntry("character", CHARACTER.toString());
    }

    @Test
    @DisplayName("FR-056 - eine abgelehnte Einloesung hinterlaesst KEINEN Eintrag")
    void arefusedClaimLeavesNoEntry() {
        List<Map<String, String>> audited = new ArrayList<>();

        listener(
                        (playerId, stacks) -> false,
                        (playerId, template, amount) -> true,
                        (season, playerId, character, at) -> true,
                        new Recorder(),
                        new CoinRecorder(),
                        audited)
                .claim(ACCOUNT, CHARACTER, SEASON);

        // Protokolliert wird, was GESCHEHEN ist. Ein Eintrag fuer eine Ablehnung machte das
        // Protokoll zu einer Liste von Versuchen, und die eigentliche Frage - wer hat wann was
        // bekommen - waere darin nicht mehr zu finden.
        assertThat(audited).isEmpty();
    }

    // ------------------------------------------------------------------ Gerüst

    private static SeasonRewardClaimListener listener(
            SeasonRewardClaimListener.InventorySpace space,
            SeasonRewardClaimListener.ItemDelivery delivery,
            SeasonRewardClaimListener.ClaimMarker marker,
            Recorder told,
            CoinRecorder coins) {
        return listener(space, delivery, marker, told, coins, new ArrayList<>());
    }

    private static SeasonRewardClaimListener listener(
            SeasonRewardClaimListener.InventorySpace space,
            SeasonRewardClaimListener.ItemDelivery delivery,
            SeasonRewardClaimListener.ClaimMarker marker,
            Recorder told,
            CoinRecorder coins,
            List<Map<String, String>> audited) {
        return new SeasonRewardClaimListener(
                coins,
                space,
                delivery,
                marker,
                playerId -> List.of(openClaim()),
                told,
                (playerId, details) -> audited.add(details),
                messages(),
                Logger.getLogger("test"),
                Clock.fixed(Instant.now(), ZoneOffset.UTC));
    }

    private static RewardClaim openClaim() {
        return RewardClaim.open(
                SEASON,
                ACCOUNT,
                1,
                new StatisticsConfig.Reward(
                        5_000L, List.of(new StatisticsConfig.ItemGrant("trim.ember", 1))));
    }

    private static Messages messages() {
        Map<String, String> texts = new HashMap<>();
        texts.put(
                StatisticsMessageKeys.REWARD_AVAILABLE.value(),
                "You placed #{rank} in season {season}. Your reward is waiting.");
        texts.put(
                StatisticsMessageKeys.REWARD_CLAIMED.value(), "Claimed for {season}: {reward}");
        texts.put(
                StatisticsMessageKeys.REWARD_ALREADY_CLAIMED.value(),
                "You have already claimed that reward.");
        texts.put(
                StatisticsMessageKeys.REWARD_INVENTORY_FULL.value(),
                "Your inventory is full. The reward stays yours - make room and try again.");
        return new MapMessages(texts);
    }

    /** Nimmt entgegen, was dem Spieler gesagt wurde. */
    private static final class Recorder implements java.util.function.BiConsumer<UUID, String> {

        private final List<String> messages = new ArrayList<>();

        @Override
        public void accept(UUID playerId, String text) {
            messages.add(text);
        }
    }

    /** Ein Konto, das nur mitschreibt. */
    private static final class CoinRecorder implements Currency {

        private long credited;
        private BookingReason reason;

        @Override
        public OptionalLong balanceOf(UUID characterId) {
            return OptionalLong.of(0L);
        }

        @Override
        public long balanceOrZero(UUID characterId) {
            return 0L;
        }

        @Override
        public boolean canAfford(UUID characterId, long amount) {
            return true;
        }

        @Override
        public BookingResult credit(UUID characterId, long amount, BookingReason reason) {
            this.credited += amount;
            this.reason = reason;
            return BookingResult.OK;
        }

        @Override
        public BookingResult debit(UUID characterId, long amount, BookingReason reason) {
            throw new AssertionError("eine Belohnung bucht nicht ab");
        }
    }

}
