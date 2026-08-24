/**
 * B10 - was eine Kreatur ist, wie viele davon leben duerfen, und wann sie wieder verschwinden.
 *
 * <p><b>Was dieser Block besitzt.</b> Die Mob-Arten aus der Konfiguration, die Entscheidung, was wo
 * gesetzt werden soll, die drei Budgets, die Entscheidung, wer aufgeraeumt wird, die Dichte nach
 * Spielerzahl und den Zustand des Bosses je Region. Alles davon ist Arithmetik und Regel - es
 * kompiliert und wird geprueft, ohne dass Bukkit auf dem Klassenpfad steht (Prinzip III.1).
 *
 * <p><b>Kein Paper in diesem Paket.</b> Eine Entitaet zu setzen, ihr einen Vermerk anzuheften, sie
 * zu entfernen, das natuerliche Spawnen abzuschalten und ein Ziel umzulenken - all das liegt in
 * {@code rpg.platform.mob}. Der Grund ist derselbe wie bei B09: nur eine bukkitfreie Domaenenschicht
 * laesst sich serverlos pruefen, und der TPS-kritische Teil dieses Blocks ist die
 * <em>Entscheidung</em>, nicht das Setzen.
 *
 * <p><b>Die eine Zusage, um die herum dieser Block gebaut ist.</b> Drei andere Bloecke halten seit
 * Monaten eine Schnittstelle offen, jeder mit demselben Satz im Javadoc: <em>„until B10 exists, then
 * B10 replaces the provider through this same interface"</em>. B05 fuer die Attributwerte einer
 * Kreatur, B06 fuer die Erfahrung, B08b fuer die Coins. Dieser Block loest sie ein, und zwar
 * <b>unter derselben Schnittstellenform</b> - es wird keine zweite eingefuehrt. Was sich aendert,
 * ist die Bedeutung des Schluessels: bis heute der Vanilla-Typname, ab jetzt die Mob-Art. Mit acht
 * Arten je Region auf wenigen Vanilla-Entities konnte der Typname nicht mehr unterscheiden, welche
 * Kreatur gefallen ist - vier Arten auf {@code ZOMBIE} waren vier Mal derselbe Schluessel.
 *
 * <p><b>Was dieser Block ausdruecklich nicht besitzt.</b>
 *
 * <ul>
 *   <li><b>Geometrie.</b> Wo eine Horde stehen darf, sagt B09 mit {@code Zones.spawnAreasOf} - ein
 *       Schluessel und eine Geometrie, sonst nichts. Dieser Block verweist darauf und haelt keine
 *       eigenen Koordinaten; verschiebt jemand einen Bereich, zieht der Boss mit.
 *   <li><b>Beute ueber Coins hinaus.</b> B11. Dieser Block liefert die Entity-Technik, die der
 *       NPC-Haendler dort mitbenutzt - den Haendler selbst nicht.
 *   <li><b>Eine eigene Kampfrechnung.</b> Kreaturen und Spieler benutzen dasselbe Attribut- und
 *       Kampfmodell (B04, B05). Eine Parallelimplementierung waere eine zweite Wahrheit ueber
 *       denselben Schlag.
 *   <li><b>Eine Persistenz.</b> Eine Kreatur ueberlebt keinen Neustart, also gibt es nichts zu
 *       migrieren, keinen Aggregattyp und keine Tabelle. Der erste Block seit B03, fuer den das
 *       gilt - und das ist kein Versehen, sondern die Folge davon, dass eine Horde Zustand der
 *       Welt ist und nicht des Charakters.
 *   <li><b>Elite- und Champion-Varianten, und den Dungeon-Boss.</b> Der Boss dieses Blocks ist eine
 *       Mob-Art mit hoeheren Zahlen und einem Respawn-Timer, mehr nicht. Der eigentliche Bosskampf
 *       mit Phasen und Faehigkeiten braucht Instanzen und kommt spaeter.
 * </ul>
 *
 * <p><b>Der Punkt, an dem dieser Block ueber TPS entscheidet.</b> Es gibt keine wiederkehrende
 * Aufgabe je Spieler und keine je Entitaet (Prinzip II). Was es gibt, ist ein einziger Durchlauf je
 * <em>bevoelkerter Zone</em> - hoechstens sechs -, der nachsetzt und aufraeumt. Spawnen ist von
 * Natur aus periodisch, denn das Ereignis „hier fehlt eine Kreatur" gibt es nicht; was sich
 * vermeiden laesst, ist die Periodizitaet je Kreatur, und die vermeidet dieser Zuschnitt.
 */
package rpg.core.mob;
