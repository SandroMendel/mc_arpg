package rpg.persistence.statistics;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rpg.core.persistence.FlushReason;
import rpg.core.persistence.PlayerState;
import rpg.core.statistics.RewardClaim;
import rpg.core.statistics.StatisticsConfig;
import rpg.persistence.support.PersistenceHarness;
import rpg.persistence.support.PostgresContainer;

/**
 * SC-008 — <b>zwei gleichzeitige Einlösungen, genau eine Gutschrift.</b>
 *
 * <p>Der Fall ist nicht theoretisch: ein Doppelklick genügt, und bei einer Belohnung klickt man
 * gern zweimal. Ein vorheriges {@code SELECT} („ist der Anspruch noch offen?") würde beide
 * durchlassen — zwischen dem Lesen und dem Schreiben liegt der Moment, in dem die andere Einlösung
 * dazwischenkommt.
 *
 * <p>Deshalb entscheidet das <b>bedingte Update</b>: beide setzen es ab, die Datenbank führt sie
 * nacheinander aus, und die zweite findet {@code claimed_at} nicht mehr {@code NULL}. Sie bekommt
 * null betroffene Zeilen und weiß, dass sie zu spät war.
 *
 * <p><b>Nur gegen eine echte PostgreSQL prüfbar.</b> Genau das, was hier geprüft wird — die
 * Serialisierung zweier gleichzeitiger Updates — ist das, was eine Attrappe nicht hat.
 */
class ClaimIsExactlyOnceTest {

    private static final String SEASON = "2026-q2";

    private PersistenceHarness harness;
    private UUID account;

    @BeforeEach
    void setUp() throws Exception {
        PostgresContainer.resetSchema();
        harness = new PersistenceHarness();
        account = UUID.randomUUID();
        harness.playerStates.put(PlayerState.initial(account, Instant.now()));
        harness.flushCycle.flushNow(FlushReason.INTERVAL).get();

        claims().create(List.of(RewardClaim.open(SEASON, account, 1, reward())));
    }

    @AfterEach
    void tearDown() {
        if (harness != null) {
            harness.close();
        }
    }

    @Test
    @DisplayName("SC-008 - zwoelf gleichzeitige Einloesungen, genau eine gewinnt")
    void twelveConcurrentClaimsExactlyOneWins() throws Exception {
        JdbcRewardClaimRepository claims = claims();

        ExecutorService pool = Executors.newFixedThreadPool(12);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch done = new CountDownLatch(12);
        AtomicInteger winners = new AtomicInteger();

        for (int i = 0; i < 12; i++) {
            UUID character = UUID.randomUUID();
            pool.execute(
                    () -> {
                        try {
                            start.await();
                            if (claims.markClaimed(SEASON, account, character, Instant.now())) {
                                winners.incrementAndGet();
                            }
                        } catch (InterruptedException interrupted) {
                            Thread.currentThread().interrupt();
                        } finally {
                            done.countDown();
                        }
                    });
        }

        start.countDown();
        assertThat(done.await(20, TimeUnit.SECONDS)).isTrue();
        pool.shutdownNow();

        assertThat(winners.get())
                .as("genau eine Einloesung darf gutschreiben duerfen")
                .isEqualTo(1);
    }

    @Test
    @DisplayName("SC-008 - der zweite Versuch desselben Spielers gewinnt nicht")
    void thesecondAttemptOfTheSamePlayerDoesNotWin() {
        JdbcRewardClaimRepository claims = claims();
        UUID character = UUID.randomUUID();

        assertThat(claims.markClaimed(SEASON, account, character, Instant.now())).isTrue();
        assertThat(claims.markClaimed(SEASON, account, character, Instant.now()))
                .as("derselbe Spieler, zweimal geklickt")
                .isFalse();
    }

    @Test
    @DisplayName("ein Absturz zwischen Markierung und Gutschrift verliert, verdoppelt aber nie")
    void acrashBetweenMarkAndCreditLosesButNeverDoubles() {
        JdbcRewardClaimRepository claims = claims();

        // Markiert - und danach passiert nichts mehr, weil der Server hier abstuerzt.
        assertThat(claims.markClaimed(SEASON, account, UUID.randomUUID(), Instant.now())).isTrue();

        // Nach dem Neustart: der Anspruch ist weg, die Gutschrift kam nie an. Das ist der
        // Verlustfall - und er ist der guenstigere. Andersherum stuende hier eine zweite
        // Gutschrift.
        assertThat(claims.openFor(account)).isEmpty();
        assertThat(claims.find(SEASON, account)).get().satisfies(claim -> {
            assertThat(claim.isOpen()).isFalse();
            assertThat(claim.claimedByCharacter()).isPresent();
        });
    }

    @Test
    @DisplayName("der Inhalt des Anspruchs ueberlebt den Weg durch die Datenbank")
    void thecontentOfTheClaimSurvivesTheRoundTrip() {
        assertThat(claims().find(SEASON, account))
                .get()
                .satisfies(
                        claim -> {
                            assertThat(claim.coins()).isEqualTo(5_000L);
                            assertThat(claim.items())
                                    .extracting(StatisticsConfig.ItemGrant::template)
                                    .containsExactly("trim.ember");
                            assertThat(claim.items().get(0).amount()).isEqualTo(1);
                        });
    }

    @Test
    @DisplayName("ein offener Anspruch steht in der Liste beim Anmelden, ein eingeloester nicht")
    void anopenClaimIsListedAtLoginAClaimedOneIsNot() {
        JdbcRewardClaimRepository claims = claims();

        assertThat(claims.openFor(account)).hasSize(1);

        claims.markClaimed(SEASON, account, UUID.randomUUID(), Instant.now());

        assertThat(claims.openFor(account)).isEmpty();
    }

    private JdbcRewardClaimRepository claims() {
        return new JdbcRewardClaimRepository(harness.pools.loginPool());
    }

    private static StatisticsConfig.Reward reward() {
        return new StatisticsConfig.Reward(
                5_000L, List.of(new StatisticsConfig.ItemGrant("trim.ember", 1)));
    }
}
