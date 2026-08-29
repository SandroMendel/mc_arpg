/**
 * B12 — Statistiken und Ranglisten, die rechnende Hälfte.
 *
 * <h2>Was hier wohnt</h2>
 *
 * <ul>
 *   <li>das <b>Metrikverzeichnis</b> — die eine Stelle, an der eine Metrik existiert, mit ihrer
 *       Art ({@code SUM}, {@code MAX}, {@code STATE}) und ihrer Sichtbarkeit (FR-018)
 *   <li>die <b>Zeiträume</b> — Tag, Woche, Saison, Allzeit, und die Frage, welcher Tag in welche
 *       Saison fällt (FR-026, FR-027)
 *   <li>die <b>Schwelle</b>, ab der ein Beitrag am Kill als Beteiligung zählt (FR-007d)
 *   <li>die <b>Zeitrechnung</b> der Spielzeit — zwei Uhren, aktiv und angemeldet (ADR-043)
 *   <li>die <b>Punktformel</b> der gewichteten Saisonwertung (ADR-046)
 * </ul>
 *
 * <h2>Was hier nicht wohnt</h2>
 *
 * <p><b>Paper nirgends.</b> Kein {@code org.bukkit}-Import, in keiner Klasse dieses Pakets. Was ein
 * Ereignis der Spielwelt ist, wird in {@code rpg.platform.statistics} zu einem Aufruf hierher
 * übersetzt — hier kommen nur noch Zahlen und Kennungen an.
 *
 * <p><b>Kein SQL.</b> Weder eine Abfrage noch ein Tabellenname noch ein {@code java.sql}-Typ. Die
 * Sichten, ihr Refresh und die beiden Saisontabellen liegen in
 * {@code rpg.persistence.statistics}, und {@code NoDirectDatabaseAccessTest} hält das fest.
 *
 * <h2>Warum {@code statistics} und nicht {@code stats}</h2>
 *
 * <p>{@code rpg.core.stats} ist <b>B04</b>, die Attribut- und Stat-Engine. Die Planung dieses
 * Blocks führte das Paket als frei; es ist es nicht. Nebeneinander gestanden hätten
 * {@code StatConfig} (B04) und {@code StatsConfig} (B12) — ein Buchstabe Unterschied zwischen
 * Kampfwerten und Ranglisteneinstellungen. Siehe ADR-048.
 *
 * @see rpg.core.stats für B04 — die Attribute selbst, nicht ihre Auswertung
 */
package rpg.core.statistics;
