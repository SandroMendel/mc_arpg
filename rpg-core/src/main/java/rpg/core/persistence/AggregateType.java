package rpg.core.persistence;

/**
 * The kinds of aggregate this layer persists.
 *
 * <p>An enum rather than an open registry: the set is small, fixed by the data model, and every
 * value needs a matching table and batch writer. A block that needs a new aggregate adds a value
 * here together with its migration, which keeps the two from drifting apart.
 */
public enum AggregateType {
    /** Durable state of one player, keyed by their unique id. */
    PLAYER_STATE,
    /** One metric for one player on one calendar day (FR-016a). */
    STATISTICS,
    // ITEM_INSTANCE stand hier bis ADR-039. Die Tabelle wurde bei jedem Sitzungsstart geladen und
    // nie geschrieben; B11 fuehrt ein Item im PersistentDataContainer innerhalb von
    // CHARACTER_INVENTORY, nicht in einer eigenen Zeile (research.md R2, V11_1). Ein Aggregattyp
    // ohne Schreiber ist eine Einladung an den naechsten Block, eine zweite Wahrheit anzulegen.
    /** One administrative action; append-only. */
    AUDIT_LOG,
    /** One character of an account, bound to a class (B03). */
    CHARACTER,
    /**
     * The current health and mana of one character (B04).
     *
     * <p>A table of its own rather than two columns on {@code character}: sharing a row would mean
     * sharing a writer and a revision counter between B03 and B04, so every change to B04's values
     * would be a change to B03's write path.
     */
    CHARACTER_STATS,
    /**
     * The level and the experience inside that level of one character (B06).
     *
     * <p>Own table for the same reason as {@link #CHARACTER_STATS}: one owner, one writer, one
     * position in the flush order. Additive, so no existing contract changes.
     */
    CHARACTER_PROGRESS,
    /**
     * The reached armour and weapon tier of one character (B07).
     *
     * <p>Own table for the same reason as the two above. The class itself is <b>not</b> stored here -
     * it already lives in {@code rpg.character} from B03, and a second copy would be a second truth.
     *
     * <p>Registration 1 of 3 (ADR-015): adding this value is not enough. It also has to appear in
     * {@code FlushCycle.WRITE_ORDER} after {@code CHARACTER}, and a repository has to be wired for
     * it. A type missing from the write order has its marks counted as failed on every flush and is
     * never written - which looks like a database problem and is none.
     */
    CHARACTER_CLASS_PROGRESS,

    /**
     * The stored contents of one character's inventory.
     *
     * <p>Groundwork for B11, brought forward because B07 made it necessary. The selection lets a player
     * switch between their characters, and the Minecraft inventory belongs to the <em>player</em>, not
     * to any one of them. With nowhere to keep it, the only consistent behaviour was to empty it on
     * every entry - which threw away whatever had been farmed.
     *
     * <p><b>Und B11 hat es nicht ersetzt, sondern uebernommen.</b> Hier stand einmal, dieser Typ sei
     * "distinct from ITEM_INSTANCE, which is B11's model for RPG items with a template and rolled
     * values". Beides ist ueberholt: die Roll-Werte sind mit ADR-027 entfallen, und die zweite
     * Tabelle mit ADR-039 - ein B11-Item traegt seine Vorlagen-ID im PersistentDataContainer und
     * liegt damit in genau diesem Blob. Es gibt nur noch eine Haltung fuer Gegenstaende.
     *
     * <p>Registration 1 of 3 (ADR-015), as above.
     */
    CHARACTER_INVENTORY,

    /**
     * What a character owns per ability: its rank, its running cooldown and its toggle (B08).
     *
     * <p><b>Many rows per character, unlike every type above.</b> The flush therefore writes a set and
     * deletes what fell back to the default - rank 1, no cooldown, no toggle. Without that the table
     * would keep a row for every ability a player ever used, all of them empty afterwards.
     *
     * <p>What is deliberately <em>not</em> here: the unlock state, which follows from the level
     * (FR-061), and rage, charges and any running ability, which are runtime and computed from a
     * timestamp plus elapsed time (ADR-025).
     *
     * <p>Registration 1 of 3 (ADR-015), as above.
     */
    CHARACTER_ABILITIES,

    /**
     * What one character holds in coins (B08b).
     *
     * <p>Own table for the same reason as the four above: one owner, one writer, one position in the
     * flush order. Belongs to the character and never to the account (ADR-011) - two characters of
     * one player keep separate purses.
     *
     * <p><b>Absence of a row means zero</b>, not the configured starting balance. That balance is
     * credited once at creation as an ordinary booking (FR-011a); reading it from configuration
     * instead would let a later change to that number enrich every unbooked character silently.
     *
     * <p>Registration 1 of 3 (ADR-015), as above.
     */
    CHARACTER_BALANCE,

    /**
     * Every change to a balance, append-only (B08b).
     *
     * <p><b>Unlike every type above, this one is never updated.</b> A correction is a new entry with
     * its own reason; an editable history is not a history. The write path therefore follows
     * {@link #AUDIT_LOG} rather than the character aggregates: entries are queued behind one
     * synthetic id and drained by the writer, so no entry is ever marked twice or written twice.
     *
     * <p>Bookings from ordinary play are pruned after a configured time; entries naming an operator
     * are kept indefinitely (FR-038). At 800 mobs this becomes the largest table in the project
     * within weeks, which is why there is a retention rule at all.
     *
     * <p>Registration 1 of 3 (ADR-015), as above.
     */
    COIN_LEDGER,

    /**
     * What a character carries out of the zone block (B09).
     *
     * <p><b>Two tables under one type</b>, and that is the unusual part: the discovered waypoint
     * crystals and the pending respawn left by a combat logout. They belong to the same character and
     * are written in the same moment, so two types would mean two positions in the write order for
     * one thing. {@link #CHARACTER_INVENTORY} already shows an aggregate may carry more than one
     * table.
     *
     * <p><b>Absence of a row means this block has never placed that character</b> - which is exactly
     * what tells a new character from a returning one, and therefore what makes the start region
     * apply exactly once (B09/FR-037b). The same inference {@link #CHARACTER_BALANCE} makes from a
     * missing balance row.
     *
     * <p>Registration 1 of 3 (ADR-015), as above.
     */
    CHARACTER_ZONE_STATE,

    /**
     * Der Verschleisszustand der beiden Ausruestungsleitern eines Charakters (B11).
     *
     * <p><b>Der Typ, der sich am haeufigsten aendert - und deshalb am wenigsten oft geschrieben
     * werden darf.</b> Er aendert sich bei jedem Treffer. Genau dafuer gibt es das Write-Behind:
     * markiert wird im Kampfpfad, geschrieben wird in einem Stapel, und der Kampfpfad sieht nie eine
     * Datenbank (Prinzip II). Aus demselben Grund liegt er nicht bei
     * {@link #CHARACTER_CLASS_PROGRESS}: eine gemeinsame Zeile hiesse, den ganzen Fortschritt eines
     * Charakters bei jedem Schlag als schmutzig zu markieren.
     *
     * <p><b>Fehlende Zeile heisst voll</b>, nicht kaputt. Die Alternative waere, einen Ladefehler in
     * eine stille Schwaechung zu uebersetzen - und niemand kaeme auf die Idee, dort zu suchen.
     *
     * <p>Registration 1 of 3 (ADR-015), as above.
     */
    CHARACTER_GEAR_CONDITION,

    /**
     * Die gekauften Trimfarben eines Charakters (B11).
     *
     * <p><b>Mehrere Zeilen je Charakter</b>, wie {@link #CHARACTER_ABILITIES}: wer drei Farben
     * gekauft hat, besitzt drei. Der Stapel schreibt deshalb eine Menge und loescht, was nicht mehr
     * dabei ist - nur zu schreiben, was da ist, liesse eine abgelegte Farbe fuer immer als getragen
     * stehen.
     *
     * <p><b>Hoechstens eine getragen, und das erzwingt die Datenbank</b> (FR-071): ein partieller
     * UNIQUE-Index in {@code V11_3}. Eine Regel, die nur in der Anwendung lebt, gilt genau so lange,
     * wie jeder Schreiber durch die Anwendung geht - ein Betreiberbefehl oder ein Reparaturskript
     * haelt sich nicht daran, und der Fehler faellt erst auf, wenn zwei Farben getragen werden und
     * niemand sagen kann, welche gilt.
     *
     * <p>Registration 1 of 3 (ADR-015), as above.
     */
    CHARACTER_COSMETIC
}
