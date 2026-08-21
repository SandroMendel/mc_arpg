package rpg.core.classes;

import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Level;
import java.util.logging.Logger;

import rpg.core.event.EventBus;
import rpg.core.session.CharacterClass;
import rpg.core.session.CharacterClassTakenException;
import rpg.core.session.CharacterRepository;
import rpg.core.session.PlayerCharacter;
import rpg.core.session.PlayerSession;

/**
 * The first-join selection flow (ADR-020) - and the <b>only</b> way a character comes into existence.
 *
 * <p>That exclusivity is the point. If some other path could create a character, the promise "no game
 * state before the choice" would hold only where someone remembered to check.
 *
 * <p>What this class does <b>not</b> do: open a menu, cancel a movement, or draw anything. Those are
 * Bukkit concerns and live in {@code rpg-platform}. Here is the rule, there is the enforcement -
 * the same split B05 used for {@code VanillaDamageListener}.
 */
public final class ClassSelection {

    private final CharacterRepository characters;
    private final EventBus eventBus;
    private final Logger logger;

    public ClassSelection(CharacterRepository characters, EventBus eventBus, Logger logger) {
        this.characters = Objects.requireNonNull(characters, "characters");
        this.eventBus = Objects.requireNonNull(eventBus, "eventBus");
        this.logger = Objects.requireNonNull(logger, "logger");
    }

    /**
     * Whether this session has to choose before it can play.
     *
     * <p>Asked of the session, not of the database: the session is authoritative while a player is
     * online (Constitution IV), and this is called on every join.
     */
    public boolean needsSelection(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        return session.activeCharacter().isEmpty();
    }

    /**
     * The classes still open to this account (FR-035).
     *
     * <p>Shown <b>before</b> the attempt, so a player never picks something that then fails. The
     * database still has the last word through the unique index from B03 - this is the courtesy, not
     * the guarantee.
     */
    public Set<CharacterClass> available(PlayerSession session) {
        Objects.requireNonNull(session, "session");
        Set<CharacterClass> taken = EnumSet.noneOf(CharacterClass.class);
        for (PlayerCharacter existing : session.availableCharacters()) {
            taken.add(existing.characterClass());
        }
        Set<CharacterClass> open = EnumSet.allOf(CharacterClass.class);
        open.removeAll(taken);
        return open;
    }

    /**
     * Creates the character. The only path that does.
     *
     * <p>The concurrent case is settled by the unique index {@code (player_id, character_class)} from
     * B03, not by a check here: two joins of one account choosing the same class at the same moment
     * would both pass any application-side check. Exactly one insert wins, and the loser gets
     * {@link ClassSelectionRejection#CLASS_ALREADY_TAKEN} rather than an exception (FR-036).
     *
     * <p>Never completes exceptionally for an ordinary refusal. A gameplay path must not put a player
     * into an inconsistent state (Constitution VI).
     */
    public CompletableFuture<ClassSelectionResult> choose(
            PlayerSession session, CharacterClass id) {
        Objects.requireNonNull(session, "session");
        if (id == null) {
            return CompletableFuture.completedFuture(
                    ClassSelectionResult.rejected(ClassSelectionRejection.UNKNOWN_CLASS));
        }
        if (session.activeCharacter().isPresent()) {
            return CompletableFuture.completedFuture(
                    ClassSelectionResult.rejected(ClassSelectionRejection.ALREADY_HAS_CHARACTER));
        }
        if (!available(session).contains(id)) {
            return CompletableFuture.completedFuture(
                    ClassSelectionResult.rejected(ClassSelectionRejection.CLASS_ALREADY_TAKEN));
        }
        return characters
                .create(session.playerId(), id)
                .handle(
                        (character, failure) -> {
                            if (failure != null) {
                                return fromFailure(session, id, failure);
                            }
                            eventBus.publish(
                                    new ClassChangedEvent(
                                            session.playerId(),
                                            character.characterId(),
                                            character.characterClass()));
                            return ClassSelectionResult.accepted(character);
                        });
    }

    /**
     * Turns a failed insert into a named rejection.
     *
     * <p>Only the "class taken" case is a rejection; anything else is a real fault and is rethrown, so
     * that a broken database does not masquerade as a full account.
     */
    private ClassSelectionResult fromFailure(
            PlayerSession session, CharacterClass id, Throwable failure) {
        Throwable cause = failure instanceof java.util.concurrent.CompletionException
                ? failure.getCause()
                : failure;
        if (cause instanceof CharacterClassTakenException) {
            logger.log(
                    Level.FINE,
                    () ->
                            "class "
                                    + id
                                    + " was taken for player "
                                    + session.playerId()
                                    + " between the menu and the insert");
            return ClassSelectionResult.rejected(ClassSelectionRejection.CLASS_ALREADY_TAKEN);
        }
        if (cause instanceof RuntimeException runtime) {
            throw runtime;
        }
        throw new IllegalStateException("creating a character failed", cause);
    }
}
