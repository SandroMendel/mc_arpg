package rpg.persistence.item;

import java.time.Clock;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.logging.Logger;

import rpg.core.event.EventBus;
import rpg.core.item.DefaultGearConditions;
import rpg.core.item.GearCondition;
import rpg.core.item.WearCurve;
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
 * Verdrahtet B11s Verschleißzustand: die Datenbankseite und die Sitzungsgrenzen.
 *
 * <p><b>Getrennt von {@code ItemModule}, das in {@code rpg-core} liegt</b>, aus demselben Grund, aus
 * dem {@code ZonePersistenceModule} von {@code ZoneModule} getrennt ist: die Verschleißregel ist
 * Arithmetik und braucht keine Datenbank. Nur dieser Teil braucht eine, und er wird dort gebaut, wo
 * das JDBC liegt.
 *
 * <p><b>Die drei Registrierungen aus ADR-015 Punkt 7 stehen absichtlich sichtbar beieinander.</b>
 * Die Konstante ist in {@link AggregateType}, die Position in {@code FlushCycle.WRITE_ORDER}, und die
 * dritte ist die Zeile in {@link #start}. Die dritte zu vergessen fällt nicht laut auf: die
 * Markierungen zählen bei jedem Durchlauf als gescheitert, geschrieben wird nie — das sieht aus wie
 * ein Datenbankfehler und ist keiner. B06 hat das auf die harte Tour gelernt.
 */
public final class GearConditionModule implements Module {

    /** Stabile Kennung, unabhängig vom Klassennamen (B01/FR-001a). */
    public static final String ID = "gear-condition";

    private final PersistenceModule persistence;
    private final SessionModule sessions;
    private final java.util.function.Supplier<WearCurve> curve;
    private final EventBus events;
    private final Logger logger;
    private final Clock clock;

    private JdbcGearConditionRepository repository;
    private DefaultGearConditions conditions;

    public GearConditionModule(
            PersistenceModule persistence,
            SessionModule sessions,
            java.util.function.Supplier<WearCurve> curve,
            EventBus events,
            Logger logger,
            Clock clock) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.sessions = Objects.requireNonNull(sessions, "sessions");
        this.curve = Objects.requireNonNull(curve, "curve");
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
        // Nur die Sitzung. Die Verschleisskurve kommt als Supplier herein und wird erst beim
        // Rechnen gefragt - damit haelt dieses Modul keine Konfiguration fest, die nachgeladen
        // werden kann.
        return List.of(SessionModule.ID);
    }

    @Override
    public void start(ModuleContext context) {
        repository =
                new JdbcGearConditionRepository(
                        persistence.pools().loginPool(),
                        context.scheduler(),
                        persistence.flushCycle(),
                        clock);
        conditions = new DefaultGearConditions(curve, repository, events, clock);
        // Woher der Stapel den aktuellen Stand liest: den Speicher, der massgeblich ist, solange
        // der Charakter da ist (Prinzip IV.2).
        repository.setLiveSource(this::snapshotOf);
        // Registrierung 3 von 3 (ADR-015 Punkt 7).
        persistence.flushCycle().register(AggregateType.CHARACTER_GEAR_CONDITION, repository);
        sessions.lifecycle().addAttachment(new GearConditionAttachment());
        logger.info("[gear-condition] phase=START state=READY - gear wears, and never breaks");
    }

    /**
     * Nimmt den gespeicherten Zustand beim Eintritt in den Speicher und gibt ihn am Ende frei.
     *
     * <p><b>Ohne das wäre der Block tot:</b> nichts würde je geladen, jeder Charakter sähe frisch
     * aus, und jeder Verschleiß wäre am Ende der Sitzung vergessen. Nach dem Muster von
     * {@code ZoneSessionAttachment} und {@code CurrencySessionAttachment}, die aus demselben Grund
     * existieren.
     *
     * <p>Der Zustand kommt aus dem Bündel, das der Login bereits gelesen hat — hier wird nicht
     * abgefragt, und das ist wichtig, weil das hier auf dem Tick läuft.
     */
    private final class GearConditionAttachment implements SessionAttachment {

        @Override
        public String id() {
            return ID;
        }

        @Override
        public void onSessionOpened(PlayerSession session, SessionBundle bundle) {
            // Eine Sitzung oeffnet ohne Charakter und bleibt es, bis die Auswahl entscheidet
            // (ADR-020). Es gibt noch nichts zu laden.
        }

        @Override
        public void onCharacterActivated(
                PlayerSession session, PlayerCharacter character, SessionBundle bundle) {
            // Keine Zeile heisst voll, nicht kaputt: wer noch nie gekaempft hat, traegt beides
            // ganz. Ein Vorgabewert unter 100 uebersetzte einen Ladefehler in eine stille
            // Schwaechung.
            conditions.put(
                    bundle.gearConditionOf(character.characterId())
                            .orElseGet(() -> GearCondition.full(character.characterId())));
        }

        @Override
        public void onSessionClosing(UUID playerId) {
            sessions.registry()
                    .find(playerId)
                    .flatMap(PlayerSession::activeCharacter)
                    .ifPresent(character -> conditions.forget(character.characterId()));
        }
    }

    /** Der lebende Zustand — die eine Fassung, die B07, B12 und B13 fragen. */
    public DefaultGearConditions conditions() {
        return conditions;
    }

    public JdbcGearConditionRepository repository() {
        return repository;
    }

    /**
     * Was der Stapel für diesen Charakter schreiben soll.
     *
     * <p>Leer, wenn der Charakter nicht geladen ist — er ist gegangen, und im Speicher steht nichts,
     * das neuer sein könnte als die Zeile.
     */
    private Optional<GearCondition> snapshotOf(UUID characterId) {
        return conditions == null ? Optional.empty() : conditions.of(characterId);
    }
}
