/**
 * Das Kommandogerüst — B14. Ein Kommando wird hier <b>deklariert</b>, nicht zerlegt.
 *
 * <p>Ein Kommando ist ein Baum aus Verzweigungen und typisierten Argumenten. Es gibt kein
 * {@code args.length} und kein {@code switch (args[0])}: Vorschlag und Prüfung eines Arguments
 * lesen <b>dasselbe Feld</b>, womit ein vorgeschlagener Wert, den die Prüfung anschließend ablehnt,
 * nicht unwahrscheinlich ist, sondern ausgeschlossen. Das Recht prüft das Gerüst vor der
 * Ausführung, nie das Kommando selbst — <b>ein Kommando kann seine Rechteprüfung nicht vergessen,
 * weil es sie nicht durchführt</b>; fehlt das Recht, erscheint der Zweig gar nicht erst in der
 * Vervollständigung und die Ausführung bricht ab, ohne etwas zu verändern. Eine Fehlermeldung nennt
 * das betroffene Argument und seinen Wertebereich statt die Nutzungszeile zu wiederholen, und jeder
 * spielersichtbare Text kommt aus einem Nachrichtenschlüssel. Jedes Kommando läuft auch von der
 * Konsole: braucht es einen Spielerbezug, bricht es mit einer Meldung ab statt mit einer Ausnahme.
 * Wer Spielerdaten verändert, hinterlässt eine Spur im Audit-Log; was die Datenbank befragt, hat
 * eine Sperrzeit; nichts blockiert den Tick; und Kommandos rufen ausschließlich öffentliche
 * Blockschnittstellen — kein Reflection, kein NMS. Der Umzug der sechs vorhandenen Kommandos ändert
 * nichts, was ein Spieler merkt.
 *
 * <p>Der Vertrag in voller Länge steht in
 * {@code specs/014-commands-permissions-admin/contracts/command-framework.md}; die zehn Absätze
 * oben sind seine zehn Punkte.
 *
 * <p><b>Zur Registrierung</b> (beantwortet am 2026-09-05 auf dem echten Server, research.md §1):
 * Registriert wird über den {@code LifecycleEventManager} und {@code Commands}. Ein gleichnamiger
 * {@code plugin.yml}-Eintrag wird dabei <b>nie erreicht</b> — Brigadier liegt davor. Deshalb
 * entfällt der {@code commands:}-Block je Kommando beim Umzug, und {@code permission:} wandert nach
 * {@code requires(...)} am Knoten, während der Rechtebaum selbst in {@code plugin.yml} bleibt.
 */
package rpg.plugin.command.framework;
