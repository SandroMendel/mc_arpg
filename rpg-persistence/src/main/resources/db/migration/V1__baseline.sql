-- B02 baseline schema.
--
-- Scope note: this migration creates the *frame* of each aggregate - key, versioning, and the
-- columns B02 itself owns. The domain columns (level, class, zone progress, concrete metrics)
-- belong to the blocks that own those aggregates (B03, B06, B07, B11) and arrive through their own
-- migrations. Without that split, B02 would have to be touched every time another block changes
-- its content.
--
-- Values are stored in real columns rather than a serialised blob: leaderboards (B12) must be able
-- to sum without deserialising every row, and ADR-004 requires a balancing rework to be possible
-- without touching existing player items.

CREATE SCHEMA IF NOT EXISTS rpg;

-- ---------------------------------------------------------------------------------------------
-- player_state: durable state of one player
-- ---------------------------------------------------------------------------------------------
CREATE TABLE rpg.player_state (
    player_id     UUID        PRIMARY KEY,
    -- Version of the record *format*, so a future migration path for stored data stays open
    -- (FR-021). Distinct from `revision`, which counts writes.
    data_version  INTEGER     NOT NULL DEFAULT 1,
    -- Incremented on every write; a write based on a stale value is rejected (FR-019b). This is
    -- the safety net behind the session handover: it stops a ghost session from flushing over a
    -- newer one and rolling a player back.
    revision      BIGINT      NOT NULL DEFAULT 0,
    last_seen_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Set once the record has been stripped of its personal reference (FR-017a).
    anonymized    BOOLEAN     NOT NULL DEFAULT FALSE,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

COMMENT ON TABLE rpg.player_state IS
    'Frame of a player aggregate. Domain columns are added by the owning blocks (B03, B06, B07).';

-- ---------------------------------------------------------------------------------------------
-- player_statistic_daily: one metric, one player, one calendar day (FR-016a)
-- ---------------------------------------------------------------------------------------------
-- Individual events are never stored. They are summed in memory onto the day's value, which is
-- what keeps this table at roughly 73k rows a year for 200 players instead of tens of millions a
-- day - and what lets the database share a machine with the server (ADR-002).
CREATE TABLE rpg.player_statistic_daily (
    player_id  UUID   NOT NULL,
    metric     TEXT   NOT NULL,
    day        DATE   NOT NULL,
    value      BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (player_id, metric, day)
);

-- This exact key is what makes `INSERT ... ON CONFLICT DO UPDATE SET value = value + excluded.value`
-- possible, and therefore FR-007 (no read before write) achievable.

-- Leaderboards (B12) sum per metric across days.
CREATE INDEX idx_statistic_metric_day ON rpg.player_statistic_daily (metric, day);
-- Per-player time range queries.
CREATE INDEX idx_statistic_player_day ON rpg.player_statistic_daily (player_id, day);

COMMENT ON TABLE rpg.player_statistic_daily IS
    'Daily aggregate per player and metric. Retained indefinitely (FR-017); never purged.';

-- ---------------------------------------------------------------------------------------------
-- item_instance: one concrete item owned by a player
-- ---------------------------------------------------------------------------------------------
CREATE TABLE rpg.item_instance (
    instance_id     UUID        PRIMARY KEY,
    owner_player_id UUID        NOT NULL REFERENCES rpg.player_state (player_id) ON DELETE CASCADE,
    -- Template id from the content configuration (B16).
    template_id     TEXT        NOT NULL,
    -- The rolled values of this one instance. ADR-004: template id + rolls only, NEVER computed
    -- final values and never rendered lore - otherwise a balancing rework could not reach existing
    -- player items. This is the only JSONB in B02: its shape differs per template and it is always
    -- read whole, never filtered or sorted on.
    rolled_values   JSONB       NOT NULL DEFAULT '{}'::jsonb,
    revision        BIGINT      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_item_owner ON rpg.item_instance (owner_player_id);

-- ---------------------------------------------------------------------------------------------
-- audit_log: administrative actions, append-only
-- ---------------------------------------------------------------------------------------------
-- No UPDATE and no DELETE are ever issued against this table: an editable audit log is worthless.
CREATE TABLE rpg.audit_log (
    entry_id         BIGINT      GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    occurred_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    actor            TEXT        NOT NULL,
    action           TEXT        NOT NULL,
    target_player_id UUID,
    details          JSONB       NOT NULL DEFAULT '{}'::jsonb
);

CREATE INDEX idx_audit_occurred_at ON rpg.audit_log (occurred_at);
CREATE INDEX idx_audit_target ON rpg.audit_log (target_player_id) WHERE target_player_id IS NOT NULL;

COMMENT ON TABLE rpg.audit_log IS
    'Append-only record of administrative actions (FR-018), including anonymisations (FR-017c).';
