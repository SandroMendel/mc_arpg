package rpg.core.persistence;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/** Access to item instances. See {@code contracts/repository.md}. */
public interface ItemInstanceRepository extends Repository<UUID, ItemInstance> {

    /**
     * Every item a character owns.
     *
     * <p>Per character, not per account (ADR-011) - the account's other characters own their own
     * items and must not see these.
     */
    CompletableFuture<List<ItemInstance>> loadByOwner(UUID ownerCharacterId);

    /**
     * Registers a newly rolled item so the next flush writes it.
     *
     * <p>Takes the whole instance rather than only its id: an item that does not exist in storage
     * yet cannot be re-read from it at flush time.
     */
    void create(ItemInstance instance);
}
