/**
 * B11 - Items, Ausruestung & Loot. Die Domaenenschicht, ohne Bukkit.
 *
 * <p><b>Was hierher gehoert:</b> Item-Vorlagen, die beiden Kategorien (Verbrauchbares und
 * Kosmetik), Beutetabellen, die Verschleisskurve, die Preise und der Entscheidungsweg, wem ein
 * gefallener Gegenstand gehoert. Alles davon ist ohne laufenden Server testbar (Prinzip III, VII).
 *
 * <p><b>Paper kommt hier nicht vor.</b> Ein {@code ItemStack} ist ein Bukkit-Typ; dieses Paket
 * kennt nur Vorlagen und Kennungen. Was aus einer Vorlage ein Exemplar in der Welt macht, steht in
 * {@code rpg.platform.item}.
 *
 * <p><b>Ausruestung gehoert ausdruecklich NICHT hierher.</b> Ruestung und Waffe sind seit ADR-017
 * Klassenprogression: je Klasse eine feste Leiter mit festen Werten, geliefert von B07 aus
 * {@code classes.yml}. Dieser Block kennt keine Ausruestungsvorlage, keine Ausruestung als Beute
 * und keine Ausruestung als Ware (FR-081). Eine Beutetabelle, die es dennoch versucht, nennt eine
 * Kennung, die es nicht gibt, und der Start bricht ab.
 *
 * <p><b>Und ein Item speichert die Vorlagen-ID, sonst nichts.</b> Keine berechneten Endwerte, kein
 * gerendertes Lore (ADR-004 in der Fassung von ADR-027, Constitution IV). Das ist der Grund, aus
 * dem Balancing nach dem Release noch moeglich ist: eine geaenderte Vorlage wirkt nach einem
 * Reload auf jedes vorhandene Exemplar in jedem Inventar, ohne dass ein Inventar angefasst wird.
 * Seit ADR-027 gibt es dabei nichts mehr zu wuerfeln - die Vorlage ist die einzige Quelle, und die
 * Zusage ist dadurch staerker geworden, nicht schwaecher.
 *
 * <p><b>Was andere Bloecke schon halten und hier nicht noch einmal entsteht</b> (FR-079): der
 * Kontostand (B08b), das Bindungspraedikat fuer Klassenausruestung (B07 {@code BoundEquipment}),
 * Rucksack und Enderchest je Charakter (B03 {@code CharacterInventory}), der Stufenkauf (B08b
 * {@code EquipmentPurchase}) und die Eigentumsmechanik fuer liegende Gegenstaende
 * ({@code rpg.platform.drop}).
 */
package rpg.core.item;
