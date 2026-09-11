/**
 * B10s Paper-Haelfte: hier und nur hier wird Bukkit angefasst.
 *
 * <p>Alles, was eine Entscheidung ist - welche Art, wie viele, wer weg soll, wann der Boss darf -
 * liegt in {@code rpg.core.mob} und wird ohne Server geprueft. Hier liegt, was ohne laufenden Server
 * nicht geht: eine Entitaet setzen, ihr den Vermerk anheften, ihr die Attribute geben, sie
 * entfernen, das natuerliche Spawnen abschalten und ein Ziel umlenken.
 *
 * <p><b>Es gibt keine oeffentliche {@code spawn(...)}.</b> Wer eine Kreatur will, setzt sie ueber
 * Bukkit und bekommt die Standardwerte. Eine Methode dieses Blocks, mit der ein anderer eine
 * Kreatur erzeugen koennte, waere eine Umgehung des Budgets mit unserem eigenen Segen - und das
 * Budget ist die einzige Zusage, auf der die Leistungsaussage dieses Projekts steht.
 *
 * <p><b>Warum Vanillas Pfadfindung stehen bleibt.</b> Die Architekturvorgabe des Steckbriefs nennt
 * „ggf. vereinfachte AI statt Vanilla-Pathfinding" - mit „ggf.", also als Moeglichkeit und nicht als
 * Auftrag. Vanillas Pfadfinder ist bereits stark optimiert; ihn durch eigenen Java-Code zu ersetzen
 * ist eine Wette, die man nur eingeht, wenn eine Messung sie verlangt. Diese Messung braucht 150
 * Spieler und 800 Kreaturen und gehoert seit ADR-031 zu B15.
 *
 * <p>Stattdessen wird die Stellschraube mit dem groessten Hebel gezogen: {@code FOLLOW_RANGE} je
 * Art. Die Zielsuche ist quadratisch im Radius, und Vanillas Standard von 16 bis 48 Bloecken ist
 * fuer eine Horde zu grosszuegig. FR-035 bis FR-037 werden damit erfuellt - mit dem kleinsten
 * Mittel, das sie erfuellt, und nicht vertagt.
 *
 * <p><b>Zwei Schichten fuer die Unterdrueckung, und die Reihenfolge ist der Punkt.</b> Spielregeln
 * halten den Spawner-Durchlauf an, sodass gar kein Kandidat entsteht - kein Ereignisobjekt, keine
 * Zuweisung. Sie decken aber laengst nicht alle Wege ab: {@code SpawnReason} kennt gut vierzig
 * Gruende, und Raids, Portale, Verstaerkung, Jockeys und Silberfischbloecke haengen an keiner Regel.
 * Der Ereignis-Riegel faengt die - und er ist billig, gerade weil die Regeln davor stehen.
 */
package rpg.platform.mob;
