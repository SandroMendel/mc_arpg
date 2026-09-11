# Vertrag: Das Kommandogerüst

Was jedes Kommando dieses Servers zusagt — unabhängig davon, welcher Block es gebaut hat.

## 1. Ein Kommando ist ein Baum, kein Text

Ein Kommando wird als Baum aus Verzweigungen und typisierten Argumenten **deklariert**. Es
zerlegt keine Zeichenkette und zählt kein `args.length`.

**Prüfbar:** Nach B14 gibt es im Produktivcode kein `args.length` und kein `switch (args[0])` mehr
(SC-002). Ein Test stellt das fest, damit das nächste Kommando nicht wieder von vorn anfängt.

## 2. Vorschlag und Prüfung stammen aus derselben Quelle

Die Tab-Completion eines Arguments und seine Gültigkeitsprüfung lesen **dasselbe Feld**. Ein
vorgeschlagener Wert, den die Prüfung anschließend ablehnt, ist damit nicht bloß unwahrscheinlich,
sondern ausgeschlossen.

> Das ist die Antwort auf den heutigen Zustand: fünf handgeschriebene `onTabComplete`, jede in
> einer anderen Methode als die Prüfung, mit der sie übereinstimmen soll.

## 3. Das Recht wird an einer Stelle geprüft

Vor der Ausführung, im Gerüst, nie im Kommando selbst. **Ein Kommando kann seine Rechteprüfung
nicht vergessen, weil es sie nicht durchführt.**

Fehlt das Recht:
- Das Unterkommando erscheint **nicht** in der Tab-Completion (FR-014).
- Die Ausführung bricht ab, ohne etwas zu verändern (FR-013).

## 4. Eine Fehlermeldung sagt, welches Argument und was erlaubt ist

Nicht die Nutzungszeile wiederholen. Nennt das betroffene Argument und seinen Wertebereich.

| Lage | Meldung sagt |
|---|---|
| Argument fehlt | welches, und was dort hingehört |
| Wert außerhalb des Bereichs | den Bereich |
| Spieler unbekannt | den eingegebenen Namen |
| Vorlage/Art unbekannt | den unbekannten Schlüssel |

Alle Meldungen laufen über Nachrichtenschlüssel (Prinzip V). **Kein fest verdrahteter
spielersichtbarer Text.**

## 5. Jedes Kommando läuft von der Konsole

Braucht es einen Spielerbezug (`/char`, `/trash`), bricht es mit einer Meldung ab, die genau das
sagt — es wirft keine Ausnahme in die Konsole.

Der Handelnde ist dann die feste Null-UUID, damit das Audit-Log greppbar bleibt. Das ist die Regel,
die `XpCommand` heute für sich allein trifft; das Gerüst hebt sie hoch.

## 6. Wer Spielerdaten verändert, hinterlässt eine Spur

Jede datenverändernde Ausführung schreibt einen `AuditEntry`: wer, was, wann, an wem — bei einer
Wertänderung mit altem und neuem Stand.

**Rein lesende Kommandos schreiben nichts** (FR-029).

Das Log bleibt anfügend. B14 schafft keinen Weg, einen Eintrag zu ändern oder zu löschen — die
Schnittstelle hat solche Methoden bewusst nicht, und das ist die Zusage.

## 7. Was die Datenbank befragt, hat eine Sperrzeit

Zeitstempelbasiert und lazy ausgewertet, **kein wiederkehrender Task** (Prinzip II). Die Ablehnung
sagt, wann es wieder geht. Für die Konsole gilt sie nicht.

## 8. Nichts blockiert den Tick

`AuditLogRepository.between` und `ClassSelection.choose` geben `CompletableFuture`. Das Ergebnis
kehrt über die projekteigene Scheduler-Abstraktion in den Tick zurück — **niemals `join()`**
(Prinzip I). Der globale Bukkit-Scheduler wird nicht benutzt.

## 9. Kommandos rufen nur öffentliche Blockschnittstellen

Nie Interna. Ein Kommando, das mehr braucht als die Schnittstelle hergibt, ist ein Hinweis auf eine
fehlende Schnittstelle — nicht auf eine erlaubte Abkürzung.

**Belegbar an dem, was schon dasteht:** `ItemStackFactory.create`, `PaperMobPlacer.place`,
`Progression.setProgress`, `ClassSelection.choose`, `ConfigLoader.reloadAll`,
`AuditLogRepository.between`. B14 baut Hüllen, keine zweiten Wege.

## 10. Der Umzug ändert nichts, was ein Spieler merkt

Die sechs vorhandenen Kommandos behalten Namen, Syntax und Ausgabe (FR-005). Kein Abnahmeschritt
aus B08b, B11, B12 oder B13 muss angepasst werden (SC-008).

**Neu ist nur, was vorher fehlte:** einheitliche Fehlermeldungen und eine Vervollständigung, die
nicht von der Prüfung abweichen kann.

---

## Der offene Punkt, der zuerst geklärt wird

Ob ein `plugin.yml`-Eintrag und eine Brigadier-Registrierung desselben Namens sich vertragen oder
überschreiben, **ist nicht geprüft und mit keinem Test zu klären.**

Das wird auf dem echten Server entschieden, **bevor** eines der sechs Kommandos angefasst wird —
mit einem Wegwerf-Kommando in beiden Fassungen. Der Rechtebaum bleibt in jedem Fall in
`plugin.yml`, weil FR-010 es verlangt und Bukkit ihn dort erwartet.

> Vier Fehler dieses Projekts sind entstanden, weil ein grüner Test etwas über die Wirklichkeit
> behauptete, das er nicht prüfen konnte. Dieser Punkt bekommt deshalb keinen Test, sondern einen
> Serverstart.
