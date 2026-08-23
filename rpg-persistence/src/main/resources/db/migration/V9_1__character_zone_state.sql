-- B09: what a character carries out of the zone block.
--
-- Two tables, one aggregate type (CHARACTER_ZONE_STATE). They are written in the same moment and
-- belong to the same character, so splitting them into two aggregates would mean two places in the
-- flush order for one thing. character_inventory already shows an aggregate may carry more than one
-- table.
--
-- Version space: V8_2 and V8_3 belong to B08b, which was inserted behind B08 by ADR-027 and
-- continued in B08's number space precisely to leave V9_x free for this block. Here it is.

-- ---------------------------------------------------------------------------------------------
-- Which waypoint crystals a character may travel to.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE rpg.character_waypoints (
    character_id UUID        NOT NULL
                             REFERENCES rpg.character (character_id) ON DELETE CASCADE,

    -- The crystal key from zones.yml. Deliberately NOT a foreign key onto anything: a crystal can
    -- disappear from the configuration for a while - an operator reworking the map - and the unlock
    -- has to survive that (FR-051b). An orphaned row is a valid state here, not a data error, and
    -- it becomes meaningful again the moment the key comes back.
    crystal_key  TEXT        NOT NULL,

    unlocked_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (character_id, crystal_key)
);

-- Grows only. No row is ever deleted except with the character (FR-051b2): an unlock is not taken
-- away by death, by a reconfiguration, or by time. The set is bounded by the number of crystals,
-- so it cannot grow without an operator adding content.

COMMENT ON TABLE rpg.character_waypoints IS
    'Waypoint crystals a character has discovered (B09). Per character, never per account (ADR-011).';

-- ---------------------------------------------------------------------------------------------
-- What a character owes the zone block on their next login.
-- ---------------------------------------------------------------------------------------------
CREATE TABLE rpg.character_zone_state (
    character_id         UUID        PRIMARY KEY
                                     REFERENCES rpg.character (character_id) ON DELETE CASCADE,

    -- Set when a character dies by logging out in combat (ADR-030, FR-038). Read, applied and
    -- cleared on the next join.
    --
    -- A zone KEY and not a coordinate: between the logout and the next login the server may restart
    -- and the map may be re-configured. Storing the point would freeze a coordinate that no longer
    -- means anything; storing the key lets the respawn resolve against whatever the configuration
    -- says at the time - and fall back cleanly when the region is gone (FR-037).
    pending_respawn_zone TEXT        NULL,

    -- Whether this character has ever been placed by this block. Absence of a row is what tells a
    -- NEW character from a returning one, which is how the start region gets used exactly once
    -- (FR-037b) - the same inference B08b makes from a missing balance row.
    first_seen_at        TIMESTAMPTZ NOT NULL DEFAULT now(),

    data_version         INTEGER     NOT NULL DEFAULT 1,
    revision             BIGINT      NOT NULL DEFAULT 0,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE rpg.character_zone_state IS
    'Pending respawn and first-seen marker per character (B09). No row means a character this block has never placed.';

-- ON DELETE CASCADE on both tables also settles anonymisation: B02's deletion path removes the
-- character and these rows go with it, without B02 needing to know that B09 exists - the same side
-- effect V4_1 and V6_1 already record for their own tables. That single line is the whole of
-- FR-051b1: unlocks end with the character, and a new character in the same slot inherits nothing.
