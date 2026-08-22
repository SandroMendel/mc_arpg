package rpg.core.currency;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.logging.Logger;

/**
 * Test harness for the currency block.
 *
 * <p>A controlled clock, a repository that records what was marked, and a ledger that keeps what was
 * queued. Nothing here reaches a database or a server - Constitution VII asks for exactly that of
 * every rule in the domain layer.
 */
final class CurrencyFixture {

    private CurrencyFixture() {}

    /** A configuration that is valid and boring, with the shipped starting balance of zero. */
    static CurrencyConfig config() {
        return config(0L);
    }

    /** The same, with a chosen starting balance. */
    static CurrencyConfig config(long startingBalance) {
        return new CurrencyConfig(
                startingBalance,
                4L,
                Map.of("ZOMBIE", 5L, "CREEPER", 8L),
                Duration.ofSeconds(120),
                3.0d,
                400,
                Duration.ofDays(30),
                45);
    }

    /** A currency with a character already loaded and holding {@code balance}. */
    static Harness loadedWith(UUID characterId, long balance) {
        Harness harness = new Harness(config());
        harness.currency.onCharacterLoaded(
                characterId, Optional.of(new CharacterBalance(characterId, balance, 1, 1L)));
        return harness;
    }

    /** A currency with nothing loaded. */
    static Harness empty() {
        return new Harness(config());
    }

    /** A currency whose configured starting balance is {@code startingBalance}. */
    static Harness startingWith(long startingBalance) {
        return new Harness(config(startingBalance));
    }

    /** The pieces a test needs to reach. */
    static final class Harness {
        final DefaultCurrency currency;
        final RecordingRepository repository;
        final RecordingLedger ledger;

        Harness(CurrencyConfig config) {
            this.repository = new RecordingRepository();
            this.ledger = new RecordingLedger();
            this.currency =
                    new DefaultCurrency(
                            config,
                            repository,
                            ledger,
                            Clock.fixed(Instant.parse("2026-08-22T12:00:00Z"), ZoneOffset.UTC),
                            Logger.getLogger("test"));
        }
    }

    /** Records what was marked; a test can assert that a booking queued a write. */
    static final class RecordingRepository implements CharacterBalanceRepository {

        final List<UUID> dirtied = new CopyOnWriteArrayList<>();
        private final Map<UUID, CharacterBalance> stored = new java.util.concurrent.ConcurrentHashMap<>();

        void store(CharacterBalance balance) {
            stored.put(balance.characterId(), balance);
        }

        @Override
        public CompletableFuture<Optional<CharacterBalance>> find(UUID characterId) {
            return CompletableFuture.completedFuture(Optional.ofNullable(stored.get(characterId)));
        }

        @Override
        public void markDirty(UUID characterId) {
            dirtied.add(characterId);
        }

        boolean marked(UUID characterId) {
            return dirtied.contains(characterId);
        }
    }

    /** Keeps queued entries so a test can read the history without a database. */
    static final class RecordingLedger implements LedgerWriter {

        final List<LedgerEntry> entries = new CopyOnWriteArrayList<>();

        @Override
        public void append(LedgerEntry entry) {
            entries.add(entry);
        }

        List<LedgerEntry> forCharacter(UUID characterId) {
            List<LedgerEntry> mine = new ArrayList<>();
            for (LedgerEntry entry : entries) {
                if (entry.characterId().equals(characterId)) {
                    mine.add(entry);
                }
            }
            return mine;
        }
    }
}
