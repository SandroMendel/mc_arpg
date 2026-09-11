/**
 * Liegende Gegenstaende, die genau einem Charakter gehoeren.
 *
 * <p><b>Das einzige Paket dieses Projekts, das keinem Block allein gehoert.</b> B08b laesst
 * Coin-Haufen fallen, B11 laesst Beute fallen, und beide brauchen dieselbe Zusage: nur der
 * Berechtigte sieht den Gegenstand, und nur er hebt ihn auf. Die Mechanik dafuer stand zuerst in
 * {@code rpg.platform.currency} und ist hierher gehoben worden - nicht kopiert (research.md R5,
 * ADR-039).
 *
 * <p>Das ist dasselbe Vorgehen, mit dem ADR-029 den {@code ShareCalculator} aus dem
 * {@code XpDistributor} gezogen hat, und aus demselben Grund: <em>„a second implementation would
 * have stayed identical only until somebody touched one of them."</em> Fuer diese Mechanik gilt es
 * staerker, weil sie sechs Fallen enthaelt und fuenf davon erst im Betrieb auffallen.
 *
 * <p><b>Die sechs Fallen:</b>
 *
 * <ol>
 *   <li><b>Sichtbarkeit je Spieler.</b> {@code setVisibleByDefault(false)} plus
 *       {@code showEntity} fuer den Berechtigten. Unsichtbarkeit ist das <em>ehrliche</em> Schloss:
 *       ein sichtbarer Gegenstand, den man nicht aufheben kann, sieht aus wie ein Fehler - man
 *       laeuft darueber, nichts passiert, und niemand sagt einem warum.
 *   <li><b>Aber Sichtbarkeit ist niemals die Autoritaet</b> (Constitution VI). Das Aufsammelschloss
 *       bleibt unabhaengig davon bestehen. Darstellung ist Darstellung.
 *   <li><b>{@code showEntity} ist Zustand der VERBINDUNG</b>, nicht der Entitaet. Nach einem
 *       Relogin ist der Gegenstand wieder unsichtbar, waehrend beide Schloesser weiter passen -
 *       <em>unsichtbar aber aufsammelbar ist das Schlechteste von beidem</em>. Deshalb gibt es
 *       {@link rpg.platform.drop.OwnedDropRegistry}.
 *   <li><b>{@code Item.setOwner} kennt Spieler, ADR-011 kennt Charaktere.</b> Ein Spieler hat bis
 *       zu drei, und B03 laesst ihn mitten in der Sitzung wechseln. Ohne die zweite Pruefung
 *       sammelt Charakter B ein, was Charakter A verdient hat.
 *   <li><b>Verschmelzen ist eine Gefahr, kein Merkmal.</b> Vanilla fuehrt aehnliche Stapel
 *       zusammen. Bei Coins verloere ein Spieler dabei die Haelfte seines Betrags; bei Beute
 *       wechselte Besitz durch blosse Naehe. Eine eindeutige Kennung je Gegenstand macht keine
 *       zwei aehnlich.
 *   <li><b>Aufgeraeumt wird von Vanilla.</b> Der Verfall holt weg, was niemand geholt hat - dieses
 *       Paket plant <b>nichts</b> und haelt keine wiederkehrende Aufgabe (Constitution II).
 * </ol>
 */
package rpg.platform.drop;
