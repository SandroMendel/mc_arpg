# Constitution — Minecraft RPG Plugin

> Grundlage für `/constitution` in Spec-Kit. Zielablage:
> `.specify/memory/constitution.md`

## Zweck

Diese Regeln gelten für **jede** Spec, jeden Plan und jede Implementierung in
diesem Projekt. Ein Vorschlag, der gegen sie verstößt, wird abgelehnt oder
verlangt eine ausdrückliche, begründete Ausnahme im Entscheidungs-Log.

---

## I. Nebenläufigkeit

1. Paper-/Bukkit-API wird **ausschließlich im Server-Tick** aufgerufen.
2. Datenbank, Datei- und Netzwerkzugriffe erfolgen **ausschließlich asynchron**.
3. Kein blockierender Aufruf im Tick-Pfad. Kein `join()`, kein `get()` auf einem
   Future, kein synchroner I/O.
4. Ergebnisse asynchroner Arbeit werden über einen expliziten Übergabepunkt in
   den Tick zurückgeführt, nie durch geteilten veränderlichen Zustand.
5. Es wird **niemals** der globale Bukkit-Scheduler direkt benutzt. Scheduling
   erfolgt über die projekteigene Abstraktion mit location- oder
   entity-gebundenen Aufrufen (hält den Folia-Pfad offen).
6. Kein globaler veränderlicher Zustand im Gameplay-Pfad. Zustand hängt an dem
   Objekt, zu dem er gehört (Spieler, Zone, Entity).

## II. Performance

1. Jedes Subsystem hält ein Tick-Budget von **≤ 5 ms** im Normalbetrieb ein.
2. Keine wiederkehrenden Tasks pro Spieler oder pro Entity. Zeitbasierte Werte
   (Cooldowns, Mana-Regeneration, Buff-Laufzeiten) werden **zeitstempelbasiert
   lazy** ausgewertet.
3. Berechnungen laufen **ereignisgesteuert bei Änderung**, nicht periodisch.
4. Räumliche Abfragen laufen über einen räumlichen Index, nie über lineare
   Iteration aller Kandidaten.
5. Kein Datenbankzugriff pro Spielereignis. Schreibvorgänge werden gepuffert und
   gebatcht.
6. Allokationen im Hot Path werden vermieden; keine Streams/Boxing in
   Pro-Tick-Schleifen.

## III. Architektur

1. Domänenlogik (`rpg-core`) enthält **keine** Bukkit-Abhängigkeit und ist ohne
   laufenden Server testbar.
2. Abhängigkeitsrichtung: `plugin → platform → core`. Niemals umgekehrt.
3. Jeder Block hat eine explizite öffentliche Schnittstelle. Zugriff auf Interna
   anderer Blöcke ist unzulässig.
4. Rendering und Eingabe liegen hinter Schnittstellen, damit ein späterer
   Resource-Pack-Client ohne Umbau ergänzt werden kann.
5. `Zone` ist niemals gleich `World`. Zonen werden als `(worldId, Geometrie)`
   modelliert.

## IV. Datenhaltung

1. Schemaänderungen ausschließlich über versionierte Migrationen.
2. Solange ein Spieler online ist, ist der Speicher-Cache autoritativ.
3. Persistierte Spielerdaten sind versioniert und besitzen einen Migrationspfad.
4. Items speichern **Template-ID und Roll-Werte**, niemals berechnete Endwerte
   oder gerendertes Lore.
5. Kein Datenverlust über das Autosave-Intervall hinaus, auch bei Crash.

## V. Datengetriebenes Design

1. Klassen, Fähigkeiten, Mobs, Zonen, Items und alle Balancing-Zahlen liegen in
   versionierten Konfigurationsdateien, nicht im Code.
2. Konfigurationen werden beim Start gegen ein Schema validiert. Fehler führen zu
   **Fail-Fast** mit klarer Meldung.
3. Neue Inhalte derselben Art dürfen keine Codeänderung erfordern.
4. Keine hartcodierten Spielertexte. Alle Texte laufen über Message-Schlüssel.

## VI. Korrektheit & Sicherheit

1. Der Server ist alleinige Autorität für alle Werte. Client-Eingaben werden
   validiert und rate-limitiert.
2. Eine Ausnahme im Gameplay-Pfad darf keinen Spieler in einen inkonsistenten
   Zustand versetzen. Fehler werden lokal begrenzt und protokolliert.
3. Reflection und NMS-Zugriff nur mit dokumentierter Begründung und gekapselt an
   einer Stelle.

## VII. Tests

1. Jede Formel und jede Regel der Domänenschicht hat Unit-Tests ohne Server.
2. Persistenz wird gegen eine echte PostgreSQL-Instanz getestet (Testcontainers),
   nicht gegen Mocks.
3. Performancekritische Blöcke (B05, B10) brauchen einen Lasttest-Nachweis, bevor
   sie als fertig gelten.

## VIII. Sprache

- Dokumentation und Diskussion: Deutsch.
- Code, Bezeichner, Config-Keys, Commit-Messages und Spielertexte: Englisch.

---

## Ausnahmen

Jede Abweichung von diesen Regeln wird als ADR in `docs/02-decisions.md`
festgehalten — mit Begründung, Alternative und Auswirkung. Eine unbegründete
Abweichung ist ein Fehler, kein Kompromiss.
