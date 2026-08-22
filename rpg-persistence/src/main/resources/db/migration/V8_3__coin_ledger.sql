-- B08b: every change to a balance, append-only.
--
-- Version space note: see the V8_2 header. B08b continues in B08's number space because Flyway
-- versions are numeric and "V8b_1" is not a valid name.
--
-- Why the balance before AND after are stored rather than derived:
-- they are derivable while the chain is unbroken - and after a crash that cost one autosave
-- interval, it is not. Stored as facts, every row stays readable on its own even when a neighbour
-- is missing. These are not "computed values" in the sense Principle IV warns about; they are the
-- state at the moment of the booking, which is exactly what a record of that moment is for.
--
-- Why the direction is a column and not the sign of the amount:
-- one fact, one representation. A negative amount here would be a second way of writing a debit,
-- and the two would eventually disagree. The same rule the application enforces (FR-009).
--
-- Why nothing here is ever updated:
-- a correction is a new row with its own reason. An editable history is not a history.

CREATE TABLE rpg.coin_ledger (
    id             BIGSERIAL   PRIMARY KEY,

    character_id   UUID        NOT NULL
                               REFERENCES rpg.character (character_id) ON DELETE CASCADE,

    occurred_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Always positive; the direction is its own column.
    amount         BIGINT      NOT NULL,
    direction      TEXT        NOT NULL,

    -- One of BookingReason. Text rather than an enum type: adding a reason should be a code change
    -- and not a migration, and the closed set is enforced in the application where it belongs.
    reason         TEXT        NOT NULL,

    balance_before BIGINT      NOT NULL,
    balance_after  BIGINT      NOT NULL,

    -- The operator who caused this, NULL for anything that happened through play. Also the flag
    -- that keeps a row out of the retention sweep (FR-038): the interventions are the ones somebody
    -- will ask about a year later.
    actor          TEXT,

    CONSTRAINT chk_coin_ledger_amount   CHECK (amount > 0),
    CONSTRAINT chk_coin_ledger_before   CHECK (balance_before >= 0),
    CONSTRAINT chk_coin_ledger_after    CHECK (balance_after >= 0),
    CONSTRAINT chk_coin_ledger_direction CHECK (direction IN ('CREDIT', 'DEBIT'))
);

-- The only query there is: "what happened to this account, newest first", paged. Without this index
-- every page of the history window would be a sequential scan over what becomes the largest table
-- in the project.
CREATE INDEX idx_coin_ledger_character_time
    ON rpg.coin_ledger (character_id, occurred_at DESC);

-- Retention deletes by age and skips rows with an actor (FR-038). A partial index on exactly that
-- predicate keeps the sweep off the operator rows entirely.
CREATE INDEX idx_coin_ledger_retention
    ON rpg.coin_ledger (occurred_at)
    WHERE actor IS NULL;

-- ON DELETE CASCADE above also settles anonymisation, as in V8_2.

COMMENT ON TABLE rpg.coin_ledger IS
    'Append-only record of every coin booking (B08b). Never updated - see V8_3 header.';
