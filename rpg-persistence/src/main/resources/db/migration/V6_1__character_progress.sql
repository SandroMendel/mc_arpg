-- B06: the progress of a character - level reached and experience inside that level.
--
-- Version space note: B02 owns V1, B03 owns V3_x, B04 owns V4_1, and each block from here numbers
-- its migrations V{block}_{seq}. B06 therefore uses V6_1. Flyway reads the underscore as a version
-- separator, giving 1 < 3.1 < 3.2 < 4.1 < 6.1 - the same order as the blocks themselves.
--
-- Why a table of its own rather than two columns on rpg.character:
-- sharing a row would mean sharing a writer and a revision counter between B03 and B06. Every
-- experience gain would then be a change to B03's write path, and the block boundary from
-- Principle III would exist only on paper. Same argument, same answer as character_stats in B04:
-- one owner, one writer, one position in the flush order.
--
-- Why level AND xp_in_level, and never a running total:
-- a single total would make the level a function of whatever curve is loaded right now. Raise the
-- curve later and every existing character silently drops levels, losing zone access (B09) and
-- abilities (B08). FR-024 forbids exactly that. This is the same rule ADR-004 sets for items -
-- store what was earned, never what was computed from it.

CREATE TABLE rpg.character_progress (
    character_id UUID    PRIMARY KEY
                         REFERENCES rpg.character (character_id) ON DELETE CASCADE,

    -- The level reached. 1 for a character that has never gained anything.
    level        INTEGER NOT NULL DEFAULT 1,
    -- Experience inside that level, not a total across all levels.
    xp_in_level  BIGINT  NOT NULL DEFAULT 0,

    -- Version of the record *format*, so an old save can be migrated on load.
    data_version INTEGER NOT NULL DEFAULT 1,
    -- Incremented on every write, exactly as in player_state, character and character_stats.
    revision     BIGINT  NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Enforced here rather than trusted from the application: a level below 1 or negative
    -- experience is not a balancing choice, it is a bug, and it should be impossible to store one.
    CONSTRAINT chk_character_progress_level CHECK (level >= 1),
    CONSTRAINT chk_character_progress_xp    CHECK (xp_in_level >= 0)
);

-- Deliberately NO check on an upper level bound. The maximum level follows from progression.yml and
-- is allowed to change; a CHECK (level <= 60) would freeze a balancing decision into the schema and
-- turn raising the ceiling into a migration.

-- ON DELETE CASCADE above also settles anonymisation: B02's deletion path removes the character and
-- this row goes with it, without B02 needing to know that B06 exists.

COMMENT ON TABLE rpg.character_progress IS
    'Level and experience inside that level (B06). Never a running total - see V6_1 header.';
