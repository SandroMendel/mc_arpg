-- B03 / ADR-011: an item belongs to a character, not to an account.
--
-- B02 hung item_instance off player_state because at that point the character level did not exist
-- yet. It does now, and the account is the wrong owner: the three characters of one account share
-- no progress, so a sword rolled by the Warrior must not appear in the Mage's inventory. Moving the
-- owner one level down costs one migration today; after B08 (inventory) and B11 build on it, it
-- costs a rework of both.
--
-- The backfill maps every existing item to the account's most recently played character. If any
-- item cannot be mapped - the account has no character at all - the NOT NULL below aborts the whole
-- migration and the transaction rolls back. That is the intended behaviour: a refused migration is
-- something an operator can look at, whereas silently dropping those items, or leaving them without
-- an owner, is a data loss nobody notices until a player asks where their gear went.

ALTER TABLE rpg.item_instance
    ADD COLUMN owner_character_id UUID REFERENCES rpg.character (character_id) ON DELETE CASCADE;

UPDATE rpg.item_instance i
SET owner_character_id = (
        SELECT c.character_id
        FROM rpg.character c
        WHERE c.player_id = i.owner_player_id
        ORDER BY c.last_played_at DESC, c.created_at DESC
        LIMIT 1);

ALTER TABLE rpg.item_instance
    ALTER COLUMN owner_character_id SET NOT NULL;

-- The old owner is dropped rather than kept "just in case": two owner columns would let a later
-- block write the one that no longer means anything, and nothing would fail.
DROP INDEX rpg.idx_item_owner;
ALTER TABLE rpg.item_instance DROP COLUMN owner_player_id;

-- The session load reads every item of a character in one go.
CREATE INDEX idx_item_owner_character ON rpg.item_instance (owner_character_id);

COMMENT ON COLUMN rpg.item_instance.owner_character_id IS
    'The owning character (ADR-011). Deleting the character deletes its items; deleting the account '
    'deletes its characters, so anonymisation still reaches every item.';
