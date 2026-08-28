package rpg.core.session;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import rpg.core.ability.AbilityState;
import rpg.core.classes.ClassProgress;
import rpg.core.currency.CharacterBalance;
import rpg.core.inventory.CharacterInventory;
import rpg.core.persistence.PlayerState;
import rpg.core.progression.CharacterProgress;
import rpg.core.stats.CharacterResources;
import rpg.core.zone.ZoneCharacterState;

/**
 * Everything a session needs, read in one go (FR-005).
 *
 * <p>Blocks that own per-character data add a list here rather than reading their own row at login:
 * B02's promise is that the login path never waits on a second round trip. B04 added
 * {@code resources}, B06 added {@code progress}, and a later block would do the same.
 *
 * @param playerId the account
 * @param accountState the stored account record, empty for a first-time player
 * @param characters every character of the account, at most one per class
 * @param resources stored health and mana per character (B04)
 * @param progress stored level and experience per character (B06)
 * @param classProgress reached armour and weapon tier per character (B07)
 * @param inventories stored inventory contents per character
 * @param abilities rank, running cooldown and toggle per character and ability (B08)
 * @param balances coins held per character (B08b)
 */
public record SessionBundle(
        UUID playerId,
        Optional<PlayerState> accountState,
        List<PlayerCharacter> characters,
        List<CharacterResources> resources,
        List<CharacterProgress> progress,
        List<ClassProgress> classProgress,
        List<CharacterInventory> inventories,
        List<AbilityState> abilities,
        List<CharacterBalance> balances,
        List<ZoneCharacterState> zoneStates) {

    public SessionBundle {
        Objects.requireNonNull(playerId, "playerId");
        Objects.requireNonNull(accountState, "accountState");
        characters = List.copyOf(Objects.requireNonNull(characters, "characters"));
        resources = List.copyOf(Objects.requireNonNull(resources, "resources"));
        progress = List.copyOf(Objects.requireNonNull(progress, "progress"));
        classProgress = List.copyOf(Objects.requireNonNull(classProgress, "classProgress"));
        inventories = List.copyOf(Objects.requireNonNull(inventories, "inventories"));
        abilities = List.copyOf(Objects.requireNonNull(abilities, "abilities"));
        balances = List.copyOf(Objects.requireNonNull(balances, "balances"));
        zoneStates = List.copyOf(Objects.requireNonNull(zoneStates, "zoneStates"));
    }

    /** A bundle without zone state - the shape before B09 existed. */
    public SessionBundle(
            UUID playerId,
            Optional<PlayerState> accountState,
            List<PlayerCharacter> characters,
            List<CharacterResources> resources,
            List<CharacterProgress> progress,
            List<ClassProgress> classProgress,
            List<CharacterInventory> inventories,
            List<AbilityState> abilities,
            List<CharacterBalance> balances) {
        this(
                playerId,
                accountState,
                characters,
                resources,
                progress,
                classProgress,
                inventories,
                abilities,
                balances,
                List.of());
    }

    /**
     * A bundle without balances - the shape before B08b existed.
     *
     * <p>Same reason as the three constructors below: nine call sites predate this list, and a test
     * about sessions should not have to name a type it does not use.
     */
    public SessionBundle(
            UUID playerId,
            Optional<PlayerState> accountState,
            List<PlayerCharacter> characters,
            List<CharacterResources> resources,
            List<CharacterProgress> progress,
            List<ClassProgress> classProgress,
            List<CharacterInventory> inventories,
            List<AbilityState> abilities) {
        this(
                playerId,
                accountState,
                characters,
                resources,
                progress,
                classProgress,
                inventories,
                abilities,
                List.of(),
                List.of());
    }

    /**
     * What one character holds in coins, or empty if it has never been written (B08b).
     *
     * <p>Loaded here rather than fetched later, for the reason B02 gives once and every block since
     * has followed: the login path never waits on a second round trip.
     *
     * <p><b>Empty means no stored row, which means zero</b> - not the configured starting balance.
     * That is credited once at creation as an ordinary booking (FR-011a, FR-011b).
     */
    public Optional<CharacterBalance> balanceOf(UUID characterId) {
        return balances.stream()
                .filter(balance -> balance.characterId().equals(characterId))
                .findFirst();
    }

    /**
     * What one character carries out of the zone block, or empty if it has never been placed (B09).
     *
     * <p>Loaded here for the same reason as everything else in this bundle: the login path never
     * waits on a second round trip, and the tick never waits at all.
     *
     * <p><b>Empty means never placed</b>, which is not the same as "nothing discovered". The first is
     * a new character and sends them to the start region (B09/FR-037b); the second is somebody who
     * has simply not found a crystal yet.
     */
    public Optional<ZoneCharacterState> zoneStateOf(UUID characterId) {
        return zoneStates.stream()
                .filter(state -> state.characterId().equals(characterId))
                .findFirst();
    }

    /**
     * A bundle without stored abilities - the shape before B08 existed.
     *
     * <p>Same reason as the two constructors below: a caller that predates the table, and a test that
     * is about sessions, should not have to name a type it does not use.
     */
    public SessionBundle(
            UUID playerId,
            Optional<PlayerState> accountState,
            List<PlayerCharacter> characters,
            List<CharacterResources> resources,
            List<CharacterProgress> progress,
            List<ClassProgress> classProgress,
            List<CharacterInventory> inventories) {
        this(
                playerId,
                accountState,
                characters,
                resources,
                progress,
                classProgress,
                inventories,
                List.of());
    }

    /** A bundle without stored inventories - the shape before that existed. */
    public SessionBundle(
            UUID playerId,
            Optional<PlayerState> accountState,
            List<PlayerCharacter> characters,
            List<CharacterResources> resources,
            List<CharacterProgress> progress,
            List<ClassProgress> classProgress) {
        this(playerId, accountState, characters, resources, progress, classProgress, List.of());
    }

    /** The stored contents of one character, or empty if it has never stored any. */
    public Optional<CharacterInventory> inventoryOf(UUID characterId) {
        return inventories.stream()
                .filter(inventory -> inventory.characterId().equals(characterId))
                .findFirst();
    }

    /**
     * What one character owns per ability - rank, running cooldown and toggle (B08).
     *
     * <p>Loaded here rather than fetched later, for the same reason B07's tiers are: the rank scales
     * every number an ability produces, and a character whose ranks arrived a moment after the session
     * was declared ready would briefly act at rank 1 and then correct itself. The session load already
     * batches its queries, so this is one more read on a connection that is open anyway.
     *
     * <p>An empty list is the ordinary case, not a miss: a row only exists once something differs from
     * the default (see the {@code V8_1} header).
     */
    public List<AbilityState> abilitiesOf(UUID characterId) {
        return abilities.stream()
                .filter(state -> state.characterId().equals(characterId))
                .toList();
    }

    /**
     * A bundle without class progress.
     *
     * <p>Not a shortcut for the loader - it fills the list - but a meaningful state in its own right:
     * B03's own version migrator rebuilds a bundle without knowing that classes exist, and a test that
     * is about sessions has no business naming a B07 type. Both would otherwise have to carry an empty
     * list they do not care about.
     */
    public SessionBundle(
            UUID playerId,
            Optional<PlayerState> accountState,
            List<PlayerCharacter> characters,
            List<CharacterResources> resources,
            List<CharacterProgress> progress) {
        this(playerId, accountState, characters, resources, progress, List.of());
    }

    /** A player connecting for the very first time: no record, no characters, no items. */
    public static SessionBundle empty(UUID playerId) {
        return new SessionBundle(
                playerId,
                Optional.empty(),
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of());
    }

    /**
     * The stored resources of one character, or empty if it has none yet - which means new (B04).
     *
     * <p>Carried in this bundle rather than loaded separately because FR-019b needs a calculated
     * holder <em>before</em> the player is released, and this load runs in the pre-login event,
     * before a player object even exists. A second load afterwards would put someone into the world
     * with the wrong health for at least a tick.
     *
     * <p><b>Dieser Absatz nannte einmal {@code ItemInstance} als das Beispiel dafuer, dass der
     * Bundle nicht B03s Privatbesitz ist.</b> Die Liste ist mit ADR-039 weggefallen: sie wurde bei
     * jedem Sitzungsstart geladen, und niemand hat sie je gelesen. Ein Gegenstand lebt seit B11 im
     * PersistentDataContainer innerhalb von {@code inventories} und braucht keine eigene Zeile
     * (research.md R2, V11_1). Das Argument selbst gilt unveraendert - {@code resources},
     * {@code progress}, {@code classProgress}, {@code abilities}, {@code balances} und
     * {@code zoneStates} stehen alle aus diesem Grund hier.
     */
    public Optional<CharacterResources> resourcesOf(UUID characterId) {
        return resources.stream().filter(r -> r.characterId().equals(characterId)).findFirst();
    }

    /** Stored progress of one character, empty when it has never been written (B06, FR-058). */
    public Optional<CharacterProgress> progressOf(UUID characterId) {
        return progress.stream().filter(p -> p.characterId().equals(characterId)).findFirst();
    }

    /**
     * The reached armour and weapon tier of one character (B07).
     *
     * <p>Loaded here rather than fetched later, on purpose: the class contributes the tier values to
     * the base stats, so a character whose tier arrived a moment after the session was declared ready
     * would briefly compute with tier 1 and then correct itself, visibly. The session load already
     * batches its queries, so this is one more read on a connection that is open anyway.
     */
    public Optional<ClassProgress> classProgressOf(UUID characterId) {
        return classProgress.stream().filter(p -> p.characterId().equals(characterId)).findFirst();
    }

    /** Whether this account has never been stored before. */
    public boolean isNewAccount() {
        return accountState.isEmpty();
    }

    /** The character to play, or empty if none exists yet (FR-021). */
    public Optional<PlayerCharacter> preferredCharacter() {
        // Most recently played first; a player with no character gets a session without one rather
        // than a silently invented one.
        return characters.stream()
                .max((a, b) -> a.lastPlayedAt().compareTo(b.lastPlayedAt()));
    }

    /** A character record written by a build newer than this one cannot be interpreted (FR-027). */
    public Optional<PlayerCharacter> anyFromFutureVersion() {
        return characters.stream().filter(PlayerCharacter::isFromFutureVersion).findFirst();
    }
}
