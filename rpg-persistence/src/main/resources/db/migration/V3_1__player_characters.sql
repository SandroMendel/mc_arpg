-- B03: the character level.
--
-- Version space note: B02 owns V1. Each block from here on numbers its migrations
-- V{block}_{seq}, so B03 uses V3_1, B04 will use V4_1, and so on. Flyway reads the underscore as
-- a version separator, giving 1 < 3.1 < 4.1 - the same order as the blocks themselves. Collisions
-- between blocks are therefore structurally impossible rather than avoided by agreement.
--
-- Scope: this creates the frame only - identity, class, versioning, timestamps. The actual
-- progress (level, attributes, abilities) belongs to B04, B06 and B07 and arrives through their
-- own migrations. Same boundary B02 drew for the account record, for the same reason: otherwise
-- this file would have to be touched every time another block changes its content.

CREATE TABLE rpg.character (
    character_id    UUID        PRIMARY KEY,
    player_id       UUID        NOT NULL REFERENCES rpg.player_state (player_id) ON DELETE CASCADE,
    character_class TEXT        NOT NULL,
    -- Version of the record *format*, so old saves can be migrated on load (FR-025 to FR-027).
    data_version    INTEGER     NOT NULL DEFAULT 1,
    -- Incremented on every write, exactly as in B02's player_state.
    revision        BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    last_played_at  TIMESTAMPTZ NOT NULL DEFAULT now(),

    CONSTRAINT chk_character_class
        CHECK (character_class IN ('WARRIOR', 'MAGE', 'ROGUE'))
);

-- The rule "at most one character per class" (FR-017, FR-020) lives in this key rather than in
-- application code. A second Warrior for the same account is impossible at the database level, no
-- matter which block later tries it - and the three-character cap follows from it without anyone
-- having to count, because there are exactly three classes.
CREATE UNIQUE INDEX uq_character_player_class
    ON rpg.character (player_id, character_class);

-- The session load reads every character of an account in one go.
CREATE INDEX idx_character_player ON rpg.character (player_id);

COMMENT ON TABLE rpg.character IS
    'Frame of a character aggregate. Progress columns are added by the owning blocks (B04, B06, B07).';
