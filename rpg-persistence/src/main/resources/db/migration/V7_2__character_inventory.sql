-- B07 (groundwork for B11): the stored contents of a character's inventory.
--
-- Why this exists here and not in B11:
-- B07's selection appears on every join and is how a player switches between their characters. The
-- Minecraft inventory belongs to the *player*, though, not to any one character - so without
-- somewhere to keep it, entering a character had to empty it, and everything farmed was lost. The
-- need is created by B07, so B07 pays for it. B11 owns what items *are*; this owns where they sit.
--
-- Why not rpg.item_instance from V3_2:
-- that table is B11's model for RPG items - a template id and rolled values. Most of what a player
-- carries is vanilla loot with no template at all. Forcing rotten flesh through an item-instance row
-- would invent an identity it does not have, and B11 would then have to tell the two apart again.
--
-- Why one blob rather than one row per slot:
-- the aggregate is the inventory, not the slot. One row means one revision counter, one dirty mark
-- and one write - the same shape every other per-character table here has. Slot-level rows would
-- turn a single pickup into up to 41 marks and buy nothing: nothing queries a single slot.
--
-- Why the format is opaque:
-- only the server can serialise an item stack, and it versions the format itself. Storing a parsed
-- structure would mean this schema knowing about materials, components and enchantments, and every
-- Minecraft update would become a migration. data_version below is *our* record format, not theirs.

CREATE TABLE rpg.character_inventory (
    character_id UUID    PRIMARY KEY
                         REFERENCES rpg.character (character_id) ON DELETE CASCADE,

    -- Bukkit's own item serialisation. Empty for a character carrying nothing.
    contents     BYTEA   NOT NULL,

    -- The ender chest, for the same reason and in the same format. Vanilla hangs it off the player
    -- too, so without this it would be the one container shared between a player's three characters -
    -- exactly the leak the main inventory was fixed for, one door along.
    ender_chest  BYTEA   NOT NULL,

    -- Version of the record *format*, so an old save can be migrated on load.
    data_version INTEGER NOT NULL DEFAULT 1,
    -- Incremented on every write, exactly as in every other aggregate here.
    revision     BIGINT  NOT NULL DEFAULT 0,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- ON DELETE CASCADE above also settles anonymisation: B02's deletion path removes the character and
-- this row goes with it, without B02 needing to know that this table exists.

COMMENT ON TABLE rpg.character_inventory IS
    'Raw inventory contents per character (B07 groundwork for B11). Class equipment is NOT stored - '
    'it is rebuilt from the reached tier on every entry.';
