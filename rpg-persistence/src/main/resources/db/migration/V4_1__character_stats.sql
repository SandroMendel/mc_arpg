-- B04: the resources of a character - current health and current mana.
--
-- Version space note: B02 owns V1, B03 owns V3_x, and each block from here numbers its migrations
-- V{block}_{seq}. B04 therefore uses V4_1. Flyway reads the underscore as a version separator,
-- giving 1 < 3.1 < 3.2 < 4.1 - the same order as the blocks themselves.
--
-- Why a table of its own rather than two columns on rpg.character:
-- sharing a row would mean sharing a writer and a revision counter between B03 and B04. Every
-- change to a stat value would then be a change to B03's write path, and the block boundary from
-- Principle III would exist only on paper. A separate table follows the pattern already set by
-- player_state, character and item_instance: one owner, one writer, one position in the flush
-- order.
--
-- Why ONLY raw values:
-- no maxima, no computed totals, no modifiers. All of that is derived from configuration and the
-- sources currently in effect, and is rebuilt on load. It is the same rule ADR-004 sets for items,
-- for the same reason - otherwise every balancing change would become a data migration across
-- every character ever created.

CREATE TABLE rpg.character_stats (
    character_id   UUID             PRIMARY KEY
                                    REFERENCES rpg.character (character_id) ON DELETE CASCADE,

    -- The two values a player would notice losing. Everything else is recomputed.
    current_health DOUBLE PRECISION NOT NULL,
    current_mana   DOUBLE PRECISION NOT NULL,

    -- Version of the record *format*, so an old save can be migrated on load.
    data_version   INTEGER          NOT NULL DEFAULT 1,
    -- Incremented on every write, exactly as in player_state and character.
    revision       BIGINT           NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ      NOT NULL DEFAULT now(),

    -- Enforced here rather than trusted from the application: a negative resource is not a
    -- balancing choice, it is a bug, and it should be impossible to store one.
    CONSTRAINT chk_character_stats_health CHECK (current_health >= 0),
    CONSTRAINT chk_character_stats_mana   CHECK (current_mana   >= 0)
);

-- ON DELETE CASCADE above also settles anonymisation: B02's deletion path removes the character,
-- and this row goes with it without B02 needing to know that B04 exists.

COMMENT ON TABLE rpg.character_stats IS
    'Raw resource values of a character (B04). Maxima and computed values are never stored.';
