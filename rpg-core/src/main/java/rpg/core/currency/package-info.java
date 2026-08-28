/**
 * B08b - one balance per character, bookings on it, and a cost check other blocks call.
 *
 * <p><b>What this block owns.</b> The balance of a character, every change to it, the reason behind
 * each change, the durable ledger of those changes, and the operator's ability to correct one.
 * Nothing else.
 *
 * <p><b>What it deliberately does not own - and this is the whole point of the block.</b> It does
 * not own <em>what anything costs</em>. Prices stay with whoever charges them: tier costs in
 * {@code classes.yml}, rank costs in {@code abilities.yml}, repair in B11 (ADR-027). A central price
 * catalogue would be a second home for numbers that already have one, and the two would drift the
 * first time somebody edited only one of them.
 *
 * <ul>
 *   <li><b>B07 classes</b>: the opaque {@code cost} block on each equipment tier. B07 passes it
 *       through and never reads it - {@code ClassSourceInvariantsTest} forbids the vocabulary in its
 *       sources. This package resolves it, which is why that test can stay green untouched.
 *   <li><b>B08 abilities</b>: rank costs. The check sits in front of {@code advanceRank}; the number
 *       lives in the ability configuration.
 *   <li><b>B10 mobs</b>: what a creature drops. This block configures it until B10 exists, then B10
 *       replaces {@link rpg.core.currency.MobCoinProvider} - the same arrangement B06 uses for
 *       experience and B05 for mob attributes.
 *   <li><b>B11 items</b>: the NPC vendor, selling and repair. This block only books.
 *   <li><b>B12 statistics</b>: evaluation of the ledger. This block writes it and offers the read.
 *   <li><b>B13 UI</b>: where a player sees their balance. This block offers the query and the
 *       message keys; the window here is provisional (ADR-028).
 *   <li><b>B14 commands</b>: the command that calls {@link rpg.core.currency.CurrencyAdmin}. It
 *       lives in the plugin module for now, by ADR-028, and B14 replaces the shell without touching
 *       the interface.
 * </ul>
 *
 * <p><b>Four promises that shape every class here.</b>
 *
 * <ol>
 *   <li><b>A balance is never negative.</b> A booking that would go below zero is refused, not
 *       capped - a silent cap is a gift nobody notices. The database carries the same rule as a
 *       {@code CHECK}, so it survives a write path that does not exist yet.
 *   <li><b>No booking without a reason.</b> There is no signature that changes a balance without
 *       naming where the change came from. Currency is the part players complain about, and a
 *       complaint without a trail cannot be settled.
 *   <li><b>Checking and debiting are one step.</b> {@code canAfford} is not a reservation. Two
 *       abilities in the same tick must not both spend the same coins, so the only honest way to
 *       pay is {@code debit}.
 *   <li><b>No database access per game event.</b> A booking marks the character; the write-behind
 *       buffer from B02 does the rest.
 * </ol>
 *
 * <p><b>Not one string a player sees is written in this package</b>; everything goes through
 * {@link rpg.core.currency.CurrencyMessageKeys} (Constitution V).
 *
 * <h2>The contract, and what changing it costs</h2>
 *
 * <p>{@link rpg.core.currency.Currency} is the only way in and out of a balance,
 * {@link rpg.core.currency.CoinLedger} the only way to read what happened to one, and
 * {@link rpg.core.currency.CurrencyAdmin} the only way an operator changes one. B07, B08, B11, B12
 * and B13 are built against those three. <b>A change to any of them is ADR-worthy from now on</b> -
 * the same rule {@code CombatPipeline}, {@code StatEngine} and {@code AbilityRegistry} hold for
 * themselves.
 *
 * <h2>Two things that look like duplication and are not</h2>
 *
 * <ul>
 *   <li><b>The never-negative rule is written down three times</b> - in
 *       {@link rpg.core.currency.CharacterBalance}, in {@link rpg.core.currency.DefaultCurrency} and
 *       as a {@code CHECK} in the table. That is on purpose: it is the promise the whole block rests
 *       on, and a rule enforced only in the application stops working the moment some later write
 *       path goes around it.
 *   <li><b>A ledger entry stores the balance before and after</b>, which is derivable from its
 *       neighbours - while the chain is unbroken. After a crash that cost one autosave interval it
 *       is not, and an entry that stays readable on its own is worth the two columns.
 * </ul>
 *
 * <h2>The one asymmetry to leave alone</h2>
 *
 * <p>A coin pile whose <em>timer</em> runs out is credited to nobody; a pile the <em>server</em>
 * clears away to free its object budget is credited to its owner. Own neglect costs, server load does
 * not. It reads like an inconsistency and is a decision (FR-029, FR-030d) - whoever unifies the two
 * later takes away one side's reason for existing.
 *
 * <h2>Provisional by design</h2>
 *
 * <p>The {@code /coins} command and the window it opens live outside this package and are meant to be
 * replaced: B14 takes the command, B13 takes the display (ADR-028). What stays is everything in here.
 * That is why the command is thin enough to throw away.
 */
package rpg.core.currency;
