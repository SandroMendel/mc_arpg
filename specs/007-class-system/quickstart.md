# Validierungsleitfaden · B07 Klassen-System

Elf Abschnitte. Die Abschnitte 1 bis 8 laufen ohne Server. Abschnitt 9 braucht Docker
(Testcontainers), Abschnitte 10 und 11 einen echten Paper-Server.

Ein Abschnitt gilt als bestanden, wenn das **erwartete Ergebnis** eintritt — nicht, wenn der Befehl
ohne Fehler durchläuft.

---

## 1 · Bauen und Gesamtlauf

```bash
./gradlew build
```

**Erwartet**: `BUILD SUCCESSFUL`, keine Fehlschläge, **keine übersprungenen Tests**. Ein
übersprungener Test ist hier kein neutrales Ergebnis: MockBukkit meldet nicht implementierte
Server-Funktionen als „skipped", und genau die müssen sichtbar bleiben.

---

## 2 · Klassendefinition lädt

```bash
./gradlew :rpg-core:test --tests '*ClassConfigSchemaTest*'
```

**Erwartet**: Alle drei Klassen werden geladen; jede liefert acht Basiswerte, acht Zuwachsraten und
zwei Leitern. Die Leiterlängen sind 5/6 (Warrior), 6/6 (Rogue) und 7/7 (Mage) — nicht überall fünf.

---

## 3 · Jede Zusage der Konfiguration bricht den Start ab

```bash
./gradlew :rpg-core:test --tests '*ClassConfigValidationTest*'
```

**Erwartet**: Je Zusage V1 bis V19 aus [class-config.md](./contracts/class-config.md) ein
fehlgeschlagener Ladevorgang mit **benannter** Ursache. Insbesondere:

- eine vierte Klassen-ID → Abbruch, die ID wird genannt (V1)
- eine fehlende der drei bekannten → Abbruch, die Klasse wird genannt (V1)
- ein fehlender Basiswert → Abbruch, das Attribut wird genannt (V2)
- eine nicht steigende Leiter → Abbruch, Stufe wird genannt (V5)
- zwei Stufen mit identischem Material, Farbe und Trim → Abbruch (V7)
- `color` auf `CHAINMAIL` → Abbruch (V9)
- Kupfer-Rüstung in zwei Klassen → Abbruch (V11)
- fünf statt sechs Fähigkeiten → Abbruch (V15)

**Nicht** abbrechen darf: ein `cost`-Block mit unbekanntem Inhalt (V18) und eine leere
Fähigkeitsliste.

---

## 4 · Werte treffen die Caps aus ADR-008

```bash
./gradlew :rpg-core:test --tests '*ClassValueBudgetTest*'
```

**Erwartet**: Für alle drei Klassen und alle acht Attribute liegt der Wert auf Level 1 Stufe 1 und auf
Level 60 Endstufe innerhalb der Wertebereiche aus ADR-008, mit **Abweichung unter 3 %** (SC-004). Für
die fünf Attribute mit Levelwachstum liegt der Leiteranteil zwischen 60 % und 80 % (SC-005).

Der Test rechnet die Summe aus Basis, Levelwachstum und Endstufe **selbst** aus der geladenen
Konfiguration. Er vergleicht keine hinterlegten Erwartungswerte — sonst prüfte er die Tabelle in der
Spec statt die Konfiguration.

---

## 5 · Leiternormierung ist längenunabhängig

```bash
./gradlew :rpg-core:test --tests '*EquipmentLadderTest*'
```

**Erwartet**: Eine Leiter mit fünf, sechs und sieben Stufen erreicht denselben Endwert (SC-014). Jede
ist streng steigend. Eine Leiter mit einer Stufe wird abgewiesen.

---

## 6 · Die Klasse ist Basiswert, nicht Modifikator

```bash
./gradlew :rpg-core:test --tests '*ClassStatContributorTest*'
```

**Erwartet**: Der Beitrag erscheint als **Basiswert**, und das Modifikatorband liegt um den
effektiven Basiswert einschließlich Stufenwerten. Konkret prüfbar am Warrior auf Level 60 Stufe 5: das
Band spannt sich um ~2012 Health, nicht um 40. Weiter: ein Halter ohne Charakter liefert keinen
Beitrag und **keine Ausnahme**. `SourceKind.CLASS` bleibt leer.

---

## 7 · Bindungsprädikat

```bash
./gradlew :rpg-core:test --tests '*BoundEquipmentTest*'
```

**Erwartet**: Klassenrüstung ja, Klassenwaffe ja, beliebiges anderes Item nein (FR-025, US4.7). Ein
Item, das für einen anderen Charakter gebunden ist, gilt für diesen als **nicht** gebunden.

---

## 8 · Fähigkeitsbindung

```bash
./gradlew :rpg-core:test --tests '*AbilityBindingTest*'
```

**Erwartet**: Der Warrior nennt sechs IDs — vier aktive einschließlich der Unique, zwei passive. Auf
Level 19 ist eine Fähigkeit mit Freischaltstufe 20 nicht dabei, auf Level 20 ist sie es. Fünf oder
sieben Einträge werden abgewiesen. Eine leere Liste wird angenommen.

---

## 9 · Persistenz gegen echtes PostgreSQL

```bash
./gradlew :rpg-persistence:test --tests '*ClassProgress*'
```

Braucht einen laufenden Docker-Daemon (Testcontainers). Prinzip VII verbietet Mocks gegen die
Datenbank.

**Erwartet**:

- Migration `V7_1` legt `rpg.character_class_progress` an; `V3_1` bleibt unverändert
- eine Stufe übersteht Schreiben, Neuladen und Serverneustart (SC-006)
- Rüstungs- und Waffenstufe sind unabhängig (US3.6)
- `CHARACTER_CLASS_PROGRESS` steht in `FlushCycle.WRITE_ORDER` **nach** `CHARACTER` — geprüft von
  `NoDatabaseAccessPerGameEventTest` als Invariante (ADR-015)
- eine Konfiguration mit weniger Stufen als ein gespeicherter Stand bricht den Start ab (V19), statt
  herabzustufen

---

## 10 · Plattform: Sperre, Auswahl, Itemaufbau

```bash
./gradlew :rpg-platform:test --tests '*rpg.platform.classes.*'
./gradlew :rpg-plugin:test --tests '*FullBootstrapTest*'
```

**Erwartet**:

- **Jede** Route für ein gebundenes Item wird abgewiesen: Klick auf Rüstungsslot, Slot-Tausch,
  Shift-Klick, Hotbar-Tausch, Offhand-Tausch, Wurf-Aktion. Je Route ein eigener Test — eine vergessene
  Route ist ein Loch in einer Regel, die als absolut gilt.
- **Gegentest**: ein ungebundenes Item lässt sich in allen Fällen bewegen (US4.3).
- Die Wurf-Aktion ist für **alle** Items abgewiesen, auch ungebundene (FR-027).
- **Attributmodifikatoren sind neutralisiert** — geprüft am **Quelltext**, nicht am Verhalten.
  MockBukkit behandelt `setAttributeModifiers(leer)` als Nulloperation und kann „leere Überschreibung"
  nicht von „keine Überschreibung" unterscheiden; ein Test auf den Getter wäre auch ohne den Aufruf
  grün. Geprüft wird deshalb: der Aufruf mit leerem, nicht-null Multimap steht da, `null` kommt
  nirgends vor, und `HIDE_ATTRIBUTES` steht **nach** dem Aufruf statt an seiner Stelle. Der
  Verhaltensnachweis ist Abschnitt 11 Punkt 14 — und nur der.
- Ein Schließversuch der Auswahl führt zurück in die Auswahl (US1.2).
- `FullBootstrapTest` ist grün: das Modul ist im Plugin tatsächlich verdrahtet (ADR-012). Grüne
  Modultests allein beweisen das nicht.

---

## 11 · Echter Paper-Server

Die Abschnitte 1 bis 10 beweisen nichts über den `libraries:`-Klassenlader von Paper und nichts über
tatsächliches Client-Verhalten. Nur ein echter Start tut das.

**Vorbereitung**: Plugin bauen, in einen Paper-26.2-Server legen, Datenbank erreichbar.

**Durchzuführen**:

1. **Start mit gültiger Konfiguration** → Server startet, drei Klassen im Log genannt.
2. **Start mit vierter Klassen-ID** → Server startet **nicht**, die ID steht im Log.
3. **Neuer Spieler tritt bei** → Auswahl öffnet sich von selbst.
4. **Escape drücken, Inventartaste, `/spawn`, Weltwechsel** → jedes Mal zurück in die Auswahl.
5. **Bewegen versuchen** → Spieler bleibt stehen.
6. **Schaden zufügen lassen** → kein Schaden.
7. **Klasse wählen** → Charakter existiert, Spielzustand betreten, Ausrüstung Stufe 1 getragen.
8. **Rüstung ausziehen versuchen** — jede Route aus Abschnitt 10 von Hand → nichts bewegt sich.
9. **Beliebiges Item werfen** → landet nicht in der Welt.
10. **Mob töten** → Beute fällt normal (FR-029). Das ist der Gegentest zu Punkt 9.
11. **Stufe weiterschalten** (Verwaltungsweg) → Material wechselt sichtbar, Werte steigen.
12. **Mage-Stufe weiterschalten** → die Lederfarbe wechselt sichtbar. Ohne diesen Punkt ist nicht
    belegt, dass Färbung als Stufenmarker funktioniert.
13. **Rogue auf Stufe 4** → der Trim erscheint auf dem Kettenhemd.
14. **Angriffsgeschwindigkeit vergleichen**: Warrior mit Schwert und Mage mit Speer bei künstlich
    gleichem Attributwert → **gleiche Schlagrate** (SC-011). Der Punkt, an dem sich zeigt, ob die
    Neutralisierung im echten Spiel greift.
15. **Relogin und Serverneustart** → Klasse und beide Stufen unverändert.
16. **Inventar vollmachen und Beute erzeugen** → Warnung erscheint, nichts verschwindet.

**Erwartet**: alle sechzehn Punkte wie beschrieben. Punkt 14 ist der wichtigste — er ist der einzige,
der die Kernentscheidung aus research.md R2 im laufenden Spiel prüft.

---

## Was dieser Leitfaden nicht abdeckt

- **Aufstiegskosten.** B07 gibt den `cost`-Block unausgelegt weiter; ohne B11 gibt es keinen regulären
  Weg, eine Stufe zu bezahlen. Punkt 11 nutzt deshalb den Verwaltungsweg.
- **Fähigkeitsverhalten.** B07 benennt nur IDs. Ob „Wirbel" wirbelt, prüft B08.
- **Lasttest.** Prinzip VII fordert ihn für B05 und B10, nicht für B07. SC-009 und SC-010 sind als
  gewöhnliche Tests formuliert.
