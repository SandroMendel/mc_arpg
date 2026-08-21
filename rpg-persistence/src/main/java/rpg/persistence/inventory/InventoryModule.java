package rpg.persistence.inventory;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

import rpg.core.inventory.CharacterInventory;
import rpg.core.inventory.CharacterInventoryRepository;
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
 * Keeps what a character was carrying - groundwork for B11, needed by B07.
 *
 * <p>The Minecraft inventory belongs to the <em>player</em>, and B07's selection lets one player move
 * between three characters. Without a place to put the contents, entering a character had to empty the
 * inventory, and everything farmed was lost. This is that place.
 *
 * <p><b>This module never reads a Bukkit inventory.</b> It cannot: the flush runs off the tick and that
 * call is tick-only. What it holds is the last snapshot the platform captured on the tick and handed
 * over through {@link #store}. The plugin decides when that happens - on the way out of a session, and
 * periodically so a crash costs at most one interval rather than the whole session.
 *
 * <p>B11 will very likely replace the storage format with something it can reason about. The seam that
 * matters - capture on the tick, store here, flush from here - should survive that.
 */
public final class InventoryModule implements Module {

    public static final String ID = "inventory";

    private final PersistenceModule persistence;
    private final SessionModule sessions;
    private final Logger logger;
    private final Clock clock;

    /** The last snapshot per character while it is in play. */
    private final Map<UUID, CharacterInventory> live = new ConcurrentHashMap<>();

    /** The contents of a character whose session just ended, for the final write. */
    private final Map<UUID, CharacterInventory> lastKnown = new ConcurrentHashMap<>();

    /** Which character a player is playing, so the quit path can find it. */
    private final Map<UUID, UUID> characterByPlayer = new ConcurrentHashMap<>();

    private JdbcCharacterInventoryRepository repository;

    public InventoryModule(
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
        // Only the session: what is stored is opaque, so neither stats nor classes are involved.
        return List.of(SessionModule.ID);
    }

    @Override
    public void start(ModuleContext context) throws Exception {
        repository =
                new JdbcCharacterInventoryRepository(
                        persistence.pools().loginPool(),
                        context.scheduler(),
                        persistence.flushCycle(),
                        clock);
        // Registration 3 of 3 (ADR-015).
        persistence.flushCycle().register(AggregateType.CHARACTER_INVENTORY, repository);
        repository.setLiveSource(this::contentsForFlush);

        sessions.lifecycle().addAttachment(new InventorySessionAttachment());
        context.registry().registerService(ID, CharacterInventoryRepository.class, repository);

        logger.info("[inventory] ready - contents are kept per character, class equipment is not");
    }

    @Override
    public void stop() throws Exception {
        live.clear();
        lastKnown.clear();
        characterByPlayer.clear();
        repository = null;
    }

    public JdbcCharacterInventoryRepository repository() {
        return repository;
    }

    /**
     * What the character being played was last seen carrying.
     *
     * <p>For the entry path, which restores it before the class equipment goes on.
     */
    public Optional<CharacterInventory> contentsOf(UUID characterId) {
        return Optional.ofNullable(live.get(characterId));
    }

    /** The character a player is playing, if any. */
    public Optional<UUID> characterOf(UUID playerId) {
        return Optional.ofNullable(characterByPlayer.get(playerId));
    }

    /**
     * Everyone currently playing a character.
     *
     * <p>For the periodic snapshot, which needs the list from off the tick. Asking the server for its
     * online players there is not safe; this map is, and it is also the more precise question - someone
     * still sitting in the selection has no character to capture for.
     */
    public List<UUID> playersInPlay() {
        return List.copyOf(characterByPlayer.keySet());
    }

    /**
     * Takes a snapshot captured on the tick and marks it for the next flush.
     *
     * <p>Called by the plugin, which is the only layer that may read an inventory. Marking here rather
     * than at capture time keeps the decision in one place: a snapshot that was taken is a snapshot
     * worth writing.
     */
    public void store(UUID characterId, byte[] contents, byte[] enderChest) {
        Objects.requireNonNull(characterId, "characterId");
        Objects.requireNonNull(contents, "contents");
        Objects.requireNonNull(enderChest, "enderChest");
        live.put(characterId, CharacterInventory.of(characterId, contents, enderChest));
        repository.markDirty(characterId);
    }

    /**
     * What the flush should write: the live snapshot, or the one stashed when the session closed.
     *
     * <p>Consumed on read, like every other stash here: it exists for exactly one final write.
     */
    Optional<CharacterInventory> contentsForFlush(UUID characterId) {
        CharacterInventory current = live.get(characterId);
        if (current != null) {
            return Optional.of(current);
        }
        return Optional.ofNullable(lastKnown.remove(characterId));
    }

    /**
     * Loads and releases the stored contents of the played character.
     *
     * <p>Nothing is applied to the player here - that is Bukkit's business and happens in the plugin
     * when the character enters play. This only makes the bytes available.
     */
    private final class InventorySessionAttachment implements SessionAttachment {

        @Override
        public String id() {
            return ID;
        }

        @Override
        public void onSessionOpened(PlayerSession session, SessionBundle bundle) {
            // A session opens without a character since ADR-021; the work happens on activation.
        }

        @Override
        public void onCharacterActivated(
                PlayerSession session, PlayerCharacter character, SessionBundle bundle) {
            UUID characterId = character.characterId();
            characterByPlayer.put(session.playerId(), characterId);
            live.put(
                    characterId,
                    bundle.inventoryOf(characterId)
                            // Never stored anything, so it is carrying nothing - which is also what a
                            // character created a moment ago carries.
                            .orElseGet(() -> CharacterInventory.empty(characterId)));
        }

        @Override
        public void onSessionClosing(UUID playerId) {
            UUID characterId = characterByPlayer.remove(playerId);
            if (characterId == null) {
                return;
            }
            // Stash, mark, release - the order every aggregate here uses, because the flush reads
            // through the live source and there is nothing live left after the release.
            //
            // What is stashed is the last snapshot the plugin captured. It captures on the way out,
            // before this runs, so the stash holds what the player was actually carrying.
            CharacterInventory last = live.remove(characterId);
            if (last != null) {
                lastKnown.put(characterId, last);
                repository.markDirty(characterId);
            }
        }
    }
}
