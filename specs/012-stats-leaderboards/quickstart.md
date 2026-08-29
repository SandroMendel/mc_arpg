# Quickstart · B12 nachweisen

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Verträge**:
[contracts/](./contracts/)

Wie man belegt, dass dieser Block tut, was er zusagt — von der billigsten Prüfung zur teuersten.
Jeder Abschnitt nennt die Erfolgskriterien, die er abdeckt.

---

## 1 · Ohne Server (Prinzip VII)

```powershell
./gradlew :rpg-core:test --tests "rpg.core.stats.*"
```

Deckt ab: **SC-001** (Rechenaufwand der Erfassung), **SC-013** (Mitternacht), **SC-015**
(Untätigkeit), **SC-016** (Summe der Zonenzeiten), **SC-019** (Beteiligungsschwelle), **SC-020**
(Punktzahl nachrechenbar), **SC-022** (Startprüfung der Gewichtung).

Die Regeln dieses Blocks sind reine Rechnung und brauchen keinen Server:

- **Schwelle**: ein `DamageShare` mit drei Beitragenden, davon einer unter der Schwelle → zwei
  Gutschriften. Ein Party-Mitglied mit Anteil null in Reichweite → trotzdem gutgeschrieben.
- **Zeitrechnung**: ein Abschnitt von 23:40 bis 00:30 teilt sich auf zwei Tage. Eine Pause nach
  der Untätigkeitsschwelle hält beide Uhren unterschiedlich an.
- **Zeiträume**: ein Tag gehört genau einer Saison; die Woche beginnt montags in UTC.
- **Punktformel**: dieselbe Eingabe, dieselbe Punktzahl — und die Aufschlüsselung summiert sich
  auf den ausgewiesenen Wert.

---

## 2 · Gegen echtes PostgreSQL (Testcontainers)

```powershell
./gradlew :rpg-persistence:test --tests "rpg.persistence.stats.*"
```

Deckt ab: **SC-002** (ein Schreibvorgang statt tausend), **SC-004** (Neustart), **SC-007**
(Anonymisierung), **SC-008**/**SC-009** (Anspruch genau einmal), **SC-021** (Gewichte eingefroren).

Was hier zu belegen ist und nirgends sonst belegt werden kann:

- **Der Maximum-Schreibweg schreibt, ohne zu lesen** und überschreibt einen niedrigeren Wert nicht
  mit einem noch niedrigeren (ADR-040).
- **Tausend Inkremente zwischen zwei Flushes** erzeugen eine Zeile und einen Schreibvorgang.
- **Die vier Sichten** liefern nach `REFRESH ... CONCURRENTLY` denselben Stand wie eine direkte
  Aggregation über die Rohdaten — der eigentliche Beweis, dass die Sichtdefinition stimmt.
- **Ein anonymisiertes Konto** behält seine Zahlen (`REPOINT_STATISTICS`) und taucht in keiner
  Ranglistenabfrage mit auflösbarem Namen auf.
- **Der Anspruchsriegel**: zwei gleichzeitige Einlösungen, genau eine Gutschrift.
- **Nichts löscht Rohdaten** — `NoDirectDatabaseAccessTest` läuft ohnehin mit.

---

## 3 · Mit MockBukkit

```powershell
./gradlew :rpg-platform:test --tests "rpg.platform.stats.*"
```

Deckt ab: **SC-006**/**SC-017** (die drei privaten Werte), **SC-011** (ein Fehler ändert kein
Spielereignis), **SC-014** (Party), **SC-010** (Hologramm verdoppelt sich nicht).

- **Die Sichtbarkeitsprüfung geht über alle vier Ausgabewege**: eigenes Fenster, Fremdprofil,
  Rangliste, Hologramm. Drei davon dürfen die privaten Werte nicht kennen — und das ist die
  Anforderung, die beim Bauen am leichtesten still verloren geht.
- **Der Statistikdienst wird absichtlich zum Scheitern gebracht**: Kills, Tode und Beute laufen
  unverändert weiter.
- **Zwei Starts hintereinander** hinterlassen genau ein Hologramm.

> **Übersprungene Tests sind zu prüfen, nicht zu übersehen.** MockBukkit meldet Nicht-Implementiertes
> als „skipped", nicht als Fehler. Ein grüner Lauf mit übersprungenen Fällen ist kein grüner Lauf.

---

## 4 · Der ganze Bootstrap

```powershell
./gradlew :rpg-plugin:test --tests "rpg.plugin.FullBootstrapTest"
```

**Dieser Test entscheidet, ob der Block fertig ist.** Modultests reichen nicht: das Modul muss im
Plugin verdrahtet sein.

Erwartete Änderungen gegenüber heute:

- `PlayerMoveEvent` trägt **einen Handler mehr** (R7) — die Zahl im Test wird mitgezogen, und genau
  dafür gibt es sie.
- `InventoryClickEvent` und `InventoryCloseEvent` bekommen je einen Handler für die
  Statistikfenster.
- Zwei neue Commands sind registriert.

---

## 5 · Auf dem Testserver

```powershell
./gradlew build
# Jar deployen — und daran denken: Bukkit überschreibt vorhandene Configs NICHT.
# statistics.yml muss von Hand mit, sonst läuft der Server gegen eine Datei ohne
# die neuen Schlüssel und bricht beim Start ab.
```

Was nur hier auffällt:

1. **Der Klassenlader.** Grüne Tests beweisen nichts über Papers `libraries:`-Mechanismus. Nur ein
   echter Serverstart tut das.
2. **Die Startprüfungen**, absichtlich provoziert: eine Saisonlücke, eine leere Gewichtung, eine
   Schwelle von 0. Jede muss mit Datei, Schlüssel und Grund abbrechen.
3. **Die Hologrammstelle in einer nicht geladenen Welt**: Warnung, keine Anzeige, Server läuft.
4. **Ein Blick in die Tabelle** nach einer Spielstunde: die Zeilen tragen Schlüssel mit Dimension,
   und die Summe der Zonenzeiten entspricht der aktiven Gesamtzeit.

---

## 6 · Was hier ausdrücklich **nicht** nachgewiesen wird

Der **Lasttest**. 150 Spieler und 800 Mobs gehören B15 (ADR-031) und halten diesen Block nicht
offen. Was B12 für sich beweisen muss, ist die eigene Rechenarbeit ohne Volllast — eine
wiederholbare Messung des Erfassungspfades und des Bewegungshandlers, mehr nicht (SC-001).
