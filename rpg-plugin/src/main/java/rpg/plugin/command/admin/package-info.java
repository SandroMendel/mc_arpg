/**
 * Die Betreiberwerkzeuge unter {@code /rpg} — B14.
 *
 * <p>Hier wohnt die Wurzel {@code /rpg} und was unter ihr hängt: Gegenstände vergeben, Kreaturen
 * setzen, die Konfiguration neu laden, Stufe und Klasse richtigstellen, fremde Spielerdaten
 * <em>lesend</em> ansehen und das Audit-Log lesen.
 *
 * <p><b>Diese Klassen enthalten keine Regel.</b> Sie erklären dem Gerüst in
 * {@link rpg.plugin.command.framework}, welcher Baum sie sind, und rufen dann eine öffentliche
 * Blockschnittstelle — {@code Progression.setProgress}, {@code ClassSelection.choose},
 * {@code ConfigLoader.reloadAll}, {@code AuditLogRepository.between}. Wächst hier eine Regel, steht
 * sie im falschen Modul. Der Maßstab ist derselbe wie eine Ebene höher: wie wenig eine Klasse
 * enthält.
 *
 * <p><b>Zwei Grenzen, die bewusst gezogen sind.</b>
 *
 * <ul>
 *   <li><b>Keine Attribute setzen</b> (FR-024a). {@code StatEngine} hat keinen Setzer, und das ist
 *       Absicht: Werte sind aus {@code ModifierSet}s je Quelle abgeleitet, {@code SourceKind} ist
 *       geschlossen, und seine Deklarationsreihenfolge <em>ist</em> die Summationsreihenfolge —
 *       Grundlage der Zusage „gleiche Quellen, gleiche Zahlen". Ein Admin-Attributwert bräuchte
 *       einen weiteren Eintrag darin und wäre nach dem nächsten Anmelden trotzdem weg. Stufe,
 *       Erfahrung und Klasse bleiben im Umfang; sie haben ihre Schnittstellen schon.
 *   <li><b>Handgesetzte Kreaturen zählen gegen eine eigene Obergrenze</b>, nicht gegen das
 *       Zonenbudget. Sie stehen trotzdem in der {@code HordeRegistry}, sonst räumt ADR-050 sie beim
 *       Chunk-Laden weg. Damit bleibt das Budget die Wahrheit und die Umgehung ist beziffert.
 * </ul>
 *
 * <p>Was hier verändert wird, steht im Audit-Log — wer, was, wann, an wem. Von der Konsole aus ist
 * der Handelnde die feste Null-UUID, damit das Log greppbar bleibt.
 */
package rpg.plugin.command.admin;
