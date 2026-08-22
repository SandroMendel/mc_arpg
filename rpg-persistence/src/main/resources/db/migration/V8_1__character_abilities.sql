-- B08: what a character owns per ability - its rank, its running cooldown and, for the one ability
-- that has a setting, that setting.
--
-- Version space note: each block numbers its migrations V{block}_{seq}, so B08 uses V8_1. Flyway
-- reads the underscore as a version separator, giving 1 < 3.1 < 3.2 < 4.1 < 6.1 < 7.1 < 7.2 < 8.1 -
-- the same order as the blocks themselves.
--
-- WHAT IS DELIBERATELY NOT HERE:
--
-- 1. No unlock state. Whether an ability is available follows from the character's level alone
--    (FR-061), exactly as B07 derives its bindings. Storing it would create a second truth the
--    moment somebody edits an unlock level in classes.yml.
--
-- 2. No row per character and ability by default. An ability at rank 1 with no running cooldown is
--    the normal case and is not stored - a fresh character would otherwise produce eighteen rows of
--    pure defaults on its first login. The row appears at the first rank-up, the first cooldown that
--    outlives a session, or the first toggle change.
--
-- 3. No rage, no charges, no sustained state. All three are runtime and follow from a timestamp plus
--    elapsed time; none survives a logout on purpose (ADR-025).
--
-- Why a table of its own rather than columns on rpg.character: the same argument B04 used for
-- character_stats, B06 for character_progress and B07 for character_class_progress. A shared row
-- means a shared writer and a shared revision counter, and the block boundary from Principle III
-- would exist only on paper.

CREATE TABLE rpg.character_abilities (
    character_id   UUID    NOT NULL
                           REFERENCES rpg.character (character_id) ON DELETE CASCADE,

    -- The ability id from abilities.yml. Text rather than an enum or a foreign key: the set of
    -- abilities is configuration and may grow without a migration, which is the whole point of the
    -- block (SC-001).
    ability_id     TEXT    NOT NULL,

    -- 1-based. The upper bound is max-rank from abilities.yml and is deliberately NOT a CHECK here -
    -- it differs per ability and is allowed to change, exactly as B07 argued for its ladder lengths.
    rank           INTEGER NOT NULL DEFAULT 1,

    -- NULL means no cooldown is running. A cooldown already in the past is discarded on load rather
    -- than loaded (FR-031); a row that is then back at rank 1 with no toggle is deleted, so the table
    -- does not grow with every fight.
    cooldown_until TIMESTAMPTZ,

    -- NULL means the ability's default. Only one ability has a setting today - the mage's
    -- Rise & Fall - and one column carries it rather than a second table (FR-052d).
    toggle_state   TEXT,

    -- Version of the record *format*, so an old save can be migrated on load.
    data_version   INTEGER NOT NULL DEFAULT 1,
    -- Incremented on every write, exactly as in the other aggregate tables.
    revision       BIGINT  NOT NULL DEFAULT 0,
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (character_id, ability_id),

    -- Enforced here rather than trusted from the application: a rank below 1 is not a balancing
    -- choice, it is a bug, and it should be impossible to store one.
    CONSTRAINT chk_ability_rank CHECK (rank >= 1),
    CONSTRAINT chk_ability_toggle CHECK (toggle_state IS NULL
                                         OR toggle_state IN ('ON', 'OFF', 'PARTIAL'))
);

-- The load path reads every row of one character at once, which the primary key already serves. No
-- second index: there is no query by ability across characters, and B12 will aggregate from its own
-- statistics rather than from here.

-- ON DELETE CASCADE above also settles anonymisation: B02's deletion path removes the character and
-- these rows go with it, without B02 needing to know that B08 exists.

COMMENT ON TABLE rpg.character_abilities IS
    'Per-character ability rank, running cooldown and toggle (B08). Unlock is derived from level and '
    'is not stored - see the V8_1 header.';
