-- B08b: what a character holds in coins.
--
-- Version space note: B08 owns V8_1 (character_abilities). B08b was inserted behind B08 by ADR-027
-- rather than renumbering B01-B17, and Flyway versions are numeric - "V8b_1" is not a valid name.
-- So this block continues in B08's number space with V8_2 and V8_3, which leaves V9_x free for B09.
--
-- Why a table of its own rather than a column on rpg.character:
-- the same argument character_stats, character_progress and character_class_progress each make.
-- Sharing a row means sharing a writer and a revision counter between B03 and this block; every
-- booking would then be a change to B03's write path.
--
-- Why the balance belongs to the character and not the account (ADR-011):
-- two characters of one player keep separate purses, exactly as they keep separate levels and
-- separate ability ranks. A shared account balance would make "how much do I have" a question with
-- three answers.
--
-- Why there is no row for every character:
-- a row appears on the first booking. A character with no row holds ZERO - not the configured
-- starting balance (FR-011b). The starting balance is credited once at creation as an ordinary
-- booking with its own ledger entry (FR-011a). Reading it from the configuration instead would mean
-- that raising that number later silently enriched every character who had never touched a coin,
-- with no booking and no trace.

CREATE TABLE rpg.character_balance (
    character_id UUID        PRIMARY KEY
                             REFERENCES rpg.character (character_id) ON DELETE CASCADE,

    -- Coins held. BIGINT rather than INTEGER: reaching the ceiling should be a refused credit
    -- (FR-010), never a silent wrap into a negative balance.
    balance      BIGINT      NOT NULL DEFAULT 0,

    -- Version of the record *format*, so an old save can be migrated on load.
    data_version INTEGER     NOT NULL DEFAULT 1,
    -- Incremented on every write, exactly as in the sibling tables.
    revision     BIGINT      NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- THE promise the whole block rests on, and the reason it is written down three times: here,
    -- in CharacterBalance, and in DefaultCurrency. A check in the application alone stops working
    -- the moment some later write path goes around it - and this one is not a balancing choice
    -- that might change, it is an invariant. A negative balance is not a poor player; it is a bug
    -- that has already been persisted.
    CONSTRAINT chk_character_balance_not_negative CHECK (balance >= 0)
);

-- Deliberately NO upper bound. What a player may accumulate is a balancing question that belongs in
-- configuration; a CHECK here would freeze it into the schema and turn a rebalance into a migration.

-- ON DELETE CASCADE above also settles anonymisation: B02's deletion path removes the character and
-- this row goes with it, without B02 needing to know that B08b exists (the side effect ADR-011
-- already recorded for item_instance).

COMMENT ON TABLE rpg.character_balance IS
    'Coins held per character (B08b). No row means zero, never the configured start - see V8_2 header.';
