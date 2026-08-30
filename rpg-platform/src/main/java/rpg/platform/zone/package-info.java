/**
 * B09 platform adapter - here and only here is Paper touched.
 *
 * <p>Everything in this package exists because {@code rpg.core.zone} may not see Bukkit
 * (Constitution III.1). Three kinds of thing live here and nothing else:
 *
 * <ul>
 *   <li><b>Translation.</b> {@link rpg.platform.zone.BukkitPositions} turns an
 *       {@code org.bukkit.Location} into a {@link rpg.core.scheduler.WorldPosition} and back. It is
 *       the single place where a Paper location becomes a core value.
 *   <li><b>Listeners.</b> Movement, join, quit, respawn, and the right-click on a crystal. They read
 *       events, ask the core for a decision, and apply it. They hold no rules.
 *   <li><b>Acting on the world.</b> Teleporting, and the selection window.
 * </ul>
 *
 * <p><b>On {@code PlayerMoveEvent}.</b> It is one of the busiest events a server has -
 * {@code DoubleJumpListener} says the same thing for the same reason. The movement listener
 * therefore checks a boundary-chunk guard first, as integer arithmetic on block coordinates with no
 * {@code Chunk} object and no allocation, and only then touches the index (FR-019, FR-020,
 * research.md R4).
 *
 * <p><b>On the right-click.</b> The clicked block's chunk key goes into the crystal index; there is
 * no iteration over all crystals and no block-type comparison. A crystal is a built structure - if
 * the block type carried the detection, travel would depend on nobody mining the stone
 * (research.md R5). Opening the window is rate-limited per player, timestamp-based and lazy, because
 * a held right mouse button would otherwise be one window per tick (Constitution VI).
 *
 * <h2>Das Fenster und die Eingabe sind seit B13 WEG</h2>
 *
 * <p>Sie standen hier befristet — ADR-032, dieselbe Abmachung, die ADR-028 für B08bs Kontofenster
 * getroffen hatte. B13 existiert jetzt, und beide sind eingelöst:
 * {@code WaypointMenu}, {@code WaypointMenuListener} und {@code CrystalInteractListener} liegen in
 * {@link rpg.platform.ui}.
 *
 * <p><b>Beide, nicht nur das Fenster</b> (FR-060): ADR-032 nennt die Eingabe ausdrücklich mit, und
 * ein Fenster ohne seinen Listener wäre ein halber Umzug. Das ist der Unterschied zu ADR-028, wo
 * nur die Anzeige wanderte und {@code /coins} stehen blieb — eine Eingabegeste ist Präsentation,
 * ein Kommando mit Rechtebaum und Tab-Completion ist es nicht.
 *
 * <p><b>Was hier geblieben ist, ist die Entscheidung</b> (FR-063): welche Wegpunkte offen sind, was
 * eine Reise kostet, wo ein Kristall steht. B13 hat die Anzeige übernommen, nicht die Regel.
 * {@code NoDisplayCodeLeftInZoneTest} hält das fest.
 *
 * <p>{@link rpg.platform.zone.BukkitPositions} bleibt ebenfalls hier: eine Umrechnung von
 * Koordinaten ist keine Anzeige.
 */
package rpg.platform.zone;
