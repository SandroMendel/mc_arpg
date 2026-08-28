/**
 * B11, Paper-Seite - hier und nur hier wird Bukkit angefasst.
 *
 * <p>Was ein Item <em>ist</em>, steht in {@code rpg.core.item} und kommt ohne Server aus
 * (Constitution III). Dieses Paket macht daraus einen {@code ItemStack}, schreibt den Vermerk in
 * den Persistent Data Container, laesst Beute fallen, fuehrt die sechs Haendler und rechnet den
 * Verschleiss aus dem, was im Kampf geschieht.
 *
 * <p><b>Drei Naehte kommen von aussen, und keine wird hier zum zweiten Mal gebaut</b> (FR-079):
 *
 * <ul>
 *   <li><b>B05s Kern-Ereignisbus</b> - Beute und Verschleiss haengen an {@code CombatDeathEvent}
 *       und {@code DamageDealtEvent}, nicht an Bukkits {@code EntityDeathEvent}. Dieselbe Wahl,
 *       die {@code CoinDropListener} getroffen hat, und aus demselben Grund: wer worauf Anspruch
 *       hat, entscheidet der Kern.
 *   <li><b>B07s {@code BoundEquipment}</b> - jede Verkaufs-, Lager- und Vernichtungsroute fragt
 *       dasselbe Praedikat. Eine eigene Pruefung waere eine zweite Wahrheit darueber, was
 *       Klassenausruestung ist.
 *   <li><b>B08bs {@code Currency}</b> - Verkaufserloes, Reparatur und Stufenkauf buchen dort. Der
 *       Stufenkauf laeuft ueber die vorhandene Route {@code EquipmentPurchase}; ein zweiter
 *       Kaufmechanismus ist unzulaessig (FR-061).
 * </ul>
 *
 * <p><b>Der Vermerk am Item traegt zwei Werte und nicht mehr</b> - Vorlagen-ID und
 * Schema-Version. Name, Lore, Raritaetsfarbe und Wirkungstext werden bei jedem Laden aus der
 * Vorlage abgeleitet (FR-002). Wer hier einen berechneten Wert hineinschreibt, nimmt dem Projekt
 * die Faehigkeit, nach dem Release noch zu balancieren.
 */
package rpg.platform.item;
