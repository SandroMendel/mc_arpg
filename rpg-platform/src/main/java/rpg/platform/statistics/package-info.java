/**
 * B12s Paper-Seite: hier und <b>nur</b> hier wird die Spielwelt angefasst.
 *
 * <p>Jede Klasse dieses Pakets tut dasselbe: sie nimmt etwas entgegen, das die Spielwelt gemeldet
 * hat, und übersetzt es in einen Aufruf gegen {@code rpg.core.statistics} — eine Zahl, eine
 * Kennung, ein Metrikschlüssel. Gerechnet wird dort, nicht hier.
 *
 * <h2>Die vier Nähte von außen</h2>
 *
 * <ul>
 *   <li><b>B05 — die Kampfereignisse.</b> {@code CombatDeathEvent} trägt Kills, Bosskills und
 *       Tode bei; {@code DamageDealtEvent} den höchsten Einzeltreffer (FR-016a). Ein Kill zählt
 *       für <em>jeden</em> Beteiligten oberhalb der Schwelle, auch außerhalb einer Party
 *       (ADR-042).
 *   <li><b>B09 — der Zonenwechsel.</b> {@code ZoneChangedEvent} schließt die laufende Zonenuhr und
 *       öffnet die nächste; ohne ihn wäre die Spielzeit je Zone eine Schätzung.
 *   <li><b>B04 — {@code holderOf(characterId)}.</b> Der einzige Weg vom Charakter zum Halter. B08
 *       hat einmal die Charakterkennung dort hineingegeben, wo eine Halterkennung erwartet wurde;
 *       jeder Aufruf warf, jede Ausnahme wurde brav gefangen, und kein Spieler wurde mehr geheilt.
 *       Deshalb wird hier nie selbst zugeordnet.
 *   <li><b>B06 — die Party.</b> Wer im Umkreis zur Gruppe gehört, entscheidet, für wen ein Kill
 *       zählt — die Statistik führt dazu keine eigene Liste.
 * </ul>
 *
 * <p><b>Was hier nicht passieren darf:</b> rechnen, eine Formel auswerten, einen Metrikschlüssel
 * als Zeichenkette bilden ({@code NoMetricLiteralsTest}) oder die Datenbank berühren. Der Tick
 * bezahlt hier nur einen Zählerinkrement im Speicher — kein Datenbankzugriff, keine Berechnung.
 *
 * @see rpg.platform.stats für B04s Vanilla-Attributbrücke
 */
package rpg.platform.statistics;
