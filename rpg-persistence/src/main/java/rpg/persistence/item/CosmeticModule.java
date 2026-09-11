package rpg.persistence.item;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Supplier;
import java.util.logging.Logger;

import rpg.core.event.EventBus;
import rpg.core.item.CosmeticApplication;
import rpg.core.item.CosmeticUnlock;
import rpg.core.item.ItemConfig;
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
 * Verdrahtet B11s Kosmetik: die Datenbankseite und die Sitzungsgrenzen (US6).
 *
 * <p>Ein eigenes Modul neben {@link GearConditionModule}, obwohl beide zu B11 gehören — ein Aggregat,
 * ein Modul, wie überall in diesem Projekt. Zwei Aggregate unter einem Modul hieße eine gemeinsame
 * Startbedingung für zwei Dinge, die nichts miteinander zu tun haben: der Verschleiß ändert sich bei
 * jedem Treffer, eine Trimfarbe ein paar Mal im Leben eines Charakters.
 *
 * <p><b>Die drei Registrierungen aus ADR-015 Punkt 7</b> stehen sichtbar beieinander: die Konstante
 * in {@link AggregateType}, die Position in {@code FlushCycle.WRITE_ORDER}, und die Zeile in
 * {@link #start}.
 */
public final class CosmeticModule implements Module {

    /** Stabile Kennung, unabhängig vom Klassennamen (B01/FR-001a). */
    public static final String ID = "cosmetic";

    private final PersistenceModule persistence;
    private final SessionModule sessions;
    private final Supplier<ItemConfig> config;
    private final CosmeticApplication.TopTierCheck topTier;
    private final EventBus events;
    private final Logger logger;
    private final Clock clock;

    private JdbcCosmeticRepository repository;
    private CosmeticApplication cosmetics;

    public CosmeticModule(
            PersistenceModule persistence,
            SessionModule sessions,
            Supplier<ItemConfig> config,
            CosmeticApplication.TopTierCheck topTier,
            EventBus events,
            Logger logger,
            Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.config = Objects.requireNonNull(config, "config");
        this.topTier = Objects.requireNonNull(topTier, "topTier");
        this.events = Objects.requireNonNull(events, "events");
        this.logger = Objects.requireNonNull(logger, "logger");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Override
    public String id() {
        return ID;
    }

    @Override
    public List<String> dependencies() {
        return List.of(SessionModule.ID);
    }

    @Override
    public void start(ModuleContext context) {
        repository =
                new JdbcCosmeticRepository(
                        persistence.pools().loginPool(),
                        context.scheduler(),
                        persistence.flushCycle(),
                        clock);
        cosmetics = new CosmeticApplication(config, repository, topTier, events, clock);
        repository.setLiveSource(this::snapshotOf);
        // Registrierung 3 von 3 (ADR-015 Punkt 7).
        persistence.flushCycle().register(AggregateType.CHARACTER_COSMETIC, repository);
        sessions.lifecycle().addAttachment(new CosmeticAttachment());
        logger.info("[cosmetic] phase=START state=READY - level 60 is not the end any more");
    }

    /**
     * Nimmt den Besitz beim Eintritt in den Speicher und gibt ihn am Ende frei.
     *
     * <p>Der Besitz kommt aus dem Bündel, das der Login bereits gelesen hat — hier wird nicht
     * abgefragt, weil das hier auf dem Tick läuft.
     */
    private final class CosmeticAttachment implements SessionAttachment {

        @Override
        public String id() {
            return ID;
        }

        @Override
        public void onSessionOpened(PlayerSession session, SessionBundle bundle) {
            // Ohne Charakter gibt es nichts zu laden (ADR-020).
        }

        @Override
        public void onCharacterActivated(
                PlayerSession session, PlayerCharacter character, SessionBundle bundle) {
            cosmetics.put(character.characterId(), bundle.cosmeticsOf(character.characterId()));
        }

        @Override
        public void onSessionClosing(UUID playerId) {
            sessions.registry()
                    .find(playerId)
                    .flatMap(PlayerSession::activeCharacter)
                    .ifPresent(character -> cosmetics.forget(character.characterId()));
        }
    }

    /** Der lebende Besitz — die eine Fassung, die der Händler und das Aussehen fragen. */
    public CosmeticApplication cosmetics() {
        return cosmetics;
    }

    public JdbcCosmeticRepository repository() {
        return repository;
    }

    private List<CosmeticUnlock> snapshotOf(UUID characterId) {
        return cosmetics == null ? List.of() : cosmetics.ownedBy(characterId);
    }
}
