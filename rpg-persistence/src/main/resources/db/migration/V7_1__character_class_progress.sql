-- B07: the reached armour and weapon tier of a character.
--
-- Version space note: B02 owns V1, B03 owns V3_x, B04 owns V4_1, B06 owns V6_1, and each block from
-- here numbers its migrations V{block}_{seq}. B07 therefore uses V7_1. Flyway reads the underscore
-- as a version separator, giving 1 < 3.1 < 3.2 < 4.1 < 6.1 < 7.1 - the same order as the blocks
-- themselves.
--
-- What this migration does NOT touch, deliberately (ADR-019):
-- the column rpg.character.character_class and the constraint chk_character_class from V3_1. The SET
-- of classes lives in code - the enum CharacterClass and that CHECK. A fourth class is a later
-- upgrade and gets its own migration then; it is not a configuration entry, and it is not this file.
--
-- Why a table of its own rather than two columns on rpg.character:
-- the same argument B04 used for character_stats and B06 for character_progress. A shared row means
-- a shared writer and a shared revision counter, and the block boundary from Principle III would
-- exist only on paper. One owner, one writer, one position in the flush order.
--
-- Why the class is not repeated here:
-- it already stands in rpg.character. A second copy would be a second truth, and the two could
-- disagree.

CREATE TABLE rpg.character_class_progress (
    character_id UUID    PRIMARY KEY
                         REFERENCES rpg.character (character_id) ON DELETE CASCADE,

    -- 1-based. A fresh character wears tier 1 of both ladders.
    armor_tier   INTEGER NOT NULL DEFAULT 1,
    -- Independent of armor_tier: advancing one leaves the other untouched.
    weapon_tier  INTEGER NOT NULL DEFAULT 1,

    -- Version of the record *format*, so an old save can be migrated on load.
    data_version INTEGER NOT NULL DEFAULT 1,
    -- Incremented on every write, exactly as in player_state, character, character_stats and
    -- character_progress.
    revision     BIGINT  NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Enforced here rather than trusted from the application: a tier below 1 is not a balancing
    -- choice, it is a bug, and it should be impossible to store one.
    CONSTRAINT chk_class_progress_armor  CHECK (armor_tier >= 1),
    CONSTRAINT chk_class_progress_weapon CHECK (weapon_tier >= 1)
);

-- Deliberately NO upper bound on either tier. The ladder length follows from classes.yml and differs
-- per class and per slot - warrior 5/6, rogue 6/6, mage 7/7 - and it is allowed to change. A
-- CHECK (armor_tier <= 5) would be wrong for two of the three classes today and wrong for all of
-- them after the next balancing pass. The startup check compares a stored tier against the
-- configured length instead and refuses to start rather than silently demoting a character (FR-024).

-- ON DELETE CASCADE above also settles anonymisation: B02's deletion path removes the character and
-- this row goes with it, without B02 needing to know that B07 exists.

COMMENT ON TABLE rpg.character_class_progress IS
    'Reached armour and weapon tier (B07). The class itself lives in rpg.character - see V7_1 header.';
