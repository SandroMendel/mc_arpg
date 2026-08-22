package rpg.persistence.currency;

import java.nio.file.Path;
import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.config.ConfigHandle;
import rpg.core.config.ConfigValidationException;
import rpg.core.currency.CharacterBalance;
import rpg.core.currency.Currency;
import rpg.core.currency.CurrencyConfig;
import rpg.core.currency.CurrencyConfigSchema;
import rpg.core.currency.DefaultCurrency;
import rpg.core.module.Module;
import rpg.core.module.ModuleContext;
import rpg.core.persistence.AggregateType;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;
import rpg.core.session.SessionAttachment;
import rpg.core.session.SessionBundle;
import rpg.persistence.PersistenceModule;
import rpg.persistence.session.SessionModule;

/**
 * Wires B08b into the plugin (ADR-012).
 *
 * <p>Lives in {@code rpg-persistence} like {@code ProgressionModule} and for the same reason: this
 * block has a database, and putting the module where its repository is makes the dependency visible
 * instead of hiding it.
 *
 * <p><b>The three registrations of ADR-015 point 7 are all here or named here.</b> The
 * {@link AggregateType} constants are registration 1, their place in {@code FlushCycle.WRITE_ORDER}
 * is registration 2, and the {@code flushCycle().register} calls below are registration 3. A type
 * missing any one of them has its marks counted as failed on every flush and is never written -
 * which looks like a database problem and is none.
 *
 * <p>{@link CurrencySessionAttachment} is the piece without which the whole block would be dead
 * code: it takes a character's balance into memory when the session opens and stashes it when the
 * session closes. Everything else could be perfect and no character would ever hold a coin -
 * exactly the failure class ADR-012 was written about.
 */
public final class CurrencyModule implements Module {

    public static final String ID = "currency";
    private static final String CONFIG_FILE = "currency.yml";

    private final PersistenceModule persistence;
    private final SessionModule sessions;
    private final Logger logger;
    private final Clock clock;

    private DefaultCurrency currency;
    private JdbcCharacterBalanceRepository balances;
    private JdbcCoinLedgerRepository ledger;
    private JdbcCurrencyAdmin admin;
    private ConfigHandle<CurrencyConfig> configHandle;

    public CurrencyModule(
            PersistenceModule persistence, SessionModule sessions, Logger logger, Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        // Session only. This block does not depend on B04, B05, B06, B07 or B08 - which is the
        // whole point of ADR-027: it sits in layer 1 and can therefore close B07 and B08 rather
        // than waiting behind them.
        return List.of(SessionModule.ID);
    }

    @Override
    public void start(ModuleContext context) throws Exception {
        configHandle = loadConfig(context);
        CurrencyConfig config = configHandle.get();

        balances =
                new JdbcCharacterBalanceRepository(
                        persistence.pools().loginPool(),
                        context.scheduler(),
                        persistence.flushCycle(),
                        clock);
        // Registration 3 of 3 (ADR-015 point 7).
        persistence.flushCycle().register(AggregateType.CHARACTER_BALANCE, balances);

        ledger =
                new JdbcCoinLedgerRepository(
                        persistence.pools().loginPool(),
                        context.scheduler(),
                        persistence.flushCycle(),
                        config.ledgerRetention(),
                        clock);
        // Registration 3 of 3 for the second aggregate (ADR-015 point 7).
        persistence.flushCycle().register(AggregateType.COIN_LEDGER, ledger);

        currency = new DefaultCurrency(config, balances, ledger, clock, logger);

        // The rules are authoritative while a character is online (Constitution IV); the flush asks
        // them rather than keeping a second copy that could disagree. Once the character is released
        // the stash answers, so the last bookings of a session are not lost to a flush that arrives
        // a moment too late (ADR-015 point 7).
        balances.setLiveSource(currency::liveOrLastKnown);

        // The operator path, and the offline-capable credit the pile cap needs (FR-030c, FR-042).
        // Through the registry, not through a field of PersistenceModule: B02 publishes the audit
        // log as a service, and reaching for another module's field would be exactly the access to
        // internals Principle III rules out.
        admin =
                new JdbcCurrencyAdmin(
                        currency,
                        persistence.pools().writePool(),
                        ledger,
                        context.registry()
                                .getService(rpg.core.persistence.AuditLogRepository.class),
                        clock,
                        logger);

        sessions.lifecycle().addAttachment(new CurrencySessionAttachment());

        context.registry().registerService(ID, Currency.class, currency);
        context.registry().registerService(ID, rpg.core.currency.CurrencyAdmin.class, admin);

        logger.info(
                "[currency] ready - starting balance "
                        + config.startingBalance()
                        + ", piles despawn after "
                        + config.pileDespawn().toSeconds()
                        + "s, at most "
                        + config.maxPiles()
                        + " lying at once, no scheduled work");
    }

    @Override
    public void stop() throws Exception {
        currency = null;
        ledger = null;
        admin = null;
        balances = null;
    }

    public DefaultCurrency currency() {
        return currency;
    }

    public JdbcCurrencyAdmin admin() {
        return admin;
    }

    public JdbcCoinLedgerRepository ledger() {
        return ledger;
    }

    public JdbcCharacterBalanceRepository balances() {
        return balances;
    }

    /** The validated configuration, for the platform layer that drops and picks up piles. */
    public CurrencyConfig config() {
        return configHandle.get();
    }

    private ConfigHandle<CurrencyConfig> loadConfig(ModuleContext context) {
        try {
            return context.configLoader()
                    .register(Path.of(CONFIG_FILE), CurrencyConfigSchema.schema());
        } catch (ConfigValidationException invalid) {
            logger.log(Level.SEVERE, "[currency] configuration rejected", invalid);
            throw new IllegalStateException(
                    "currency configuration is invalid: " + invalid.getMessage(), invalid);
        }
    }

    /**
     * Takes a balance into memory on session open and stashes it on close (FR-016, FR-017).
     *
     * <p>Modelled on {@code ProgressSessionAttachment} in B06. Without this class nothing would ever
     * be loaded or written.
     */
    private final class CurrencySessionAttachment implements SessionAttachment {

        @Override
        public String id() {
            return ID;
        }

        @Override
        public void onSessionOpened(PlayerSession session, SessionBundle bundle) {
            session.activeCharacter().ifPresent(character -> load(character, bundle));
        }

        /**
         * A character entered play, so its balance comes into memory.
         *
         * <p>Out of the bundle, not out of a query: the row was already read at login (FR-017).
         * Asking the database here would be a query on the player's tick for a row already in hand.
         */
        @Override
        public void onCharacterActivated(
                PlayerSession session, PlayerCharacter character, SessionBundle bundle) {
            load(character, bundle);
        }

        private void load(PlayerCharacter character, SessionBundle bundle) {
            UUID characterId = character.characterId();
            Optional<CharacterBalance> stored = bundle.balanceOf(characterId);
            currency.onCharacterLoaded(characterId, stored);
            if (stored.isEmpty()) {
                // Never written before. Credits the configured starting balance, once, as an
                // ordinary booking - and books nothing at all when that number is zero (FR-011a,
                // FR-011c). Doing it here rather than on read is what keeps a later change to the
                // number from enriching everyone retroactively (FR-011b).
                currency.onCharacterCreated(characterId);
            }
        }

        @Override
        public void onSessionClosing(UUID playerId) {
            sessions.registry()
                    .find(playerId)
                    .flatMap(PlayerSession::activeCharacter)
                    .ifPresent(
                            character ->
                                    // Stash, then mark, then release. Every step of that order
                                    // matters: the flush reads through the live source, and after
                                    // the release there is nothing live left to read.
                                    currency.onSessionClosing(character.characterId()));
        }
    }
}
