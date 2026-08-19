package rpg.persistence.session;

import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import rpg.core.session.DefaultSessionLifecycle;
import rpg.core.session.PlayerCharacter;
import rpg.persistence.PersistenceModule;
import rpg.persistence.jdbc.JdbcCharacterRepository;

/**
 * The bridge from the session lifecycle to B02's writing.
 *
 * <p>Deliberately thin, and that is the point. Every method here delegates; none of them writes.
 * B02 already guarantees that at most one autosave interval is ever lost, and it proves that with
 * its own tests. A second write path in B03 could break that guarantee without a single B03 test
 * noticing - which is why this class exists as the one, obvious, reviewable place where the two
 * blocks meet.
 */
final class PersistenceSessionWriter implements DefaultSessionLifecycle.SessionWriter {

    private final PersistenceModule persistence;
    private final JdbcCharacterRepository characters;

    PersistenceSessionWriter(PersistenceModule persistence, JdbcCharacterRepository characters) {
        this.persistence = Objects.requireNonNull(persistence, "persistence");
        this.characters = Objects.requireNonNull(characters, "characters");
    }

    @Override
    public CompletableFuture<Void> writeAndAwait(UUID playerId) {
        // B02's existing session-end flush: immediate, independent of the autosave interval
        // (B02/FR-004). Not reimplemented here.
        return persistence.sessionHandover().onSessionEnd(playerId);
    }

    @Override
    public void markCharactersDirty(List<PlayerCharacter> migrated) {
        // A migrated record is marked so the current format reaches storage (FR-026). Marking, not
        // writing - when it is written remains B02's decision.
        for (PlayerCharacter character : migrated) {
            characters.put(character);
        }
    }
}
