# B07 · Klassen-System

| | |
|---|---|
| **Schicht** | 1 — Regel-Engine |
| **Status** | Implementiert (2026-08-22) — 144 Aufgaben, davon 140 erledigt; 1162 Tests im Projekt, 0 Fehler, 0 übersprungen. Offen allein: vier Punkte, die einen laufenden Paper-Server brauchen. Spec unter `specs/007-class-system/` |
| **Abhängig von** | B04, B06 |
| **Benötigt von** | B08, B11, B13 |

## Zweck

Drei wählbare Spielerklassen mit je eigenem Rollenprofil, Basiswerten,
Wachstumskurven und Fähigkeitensatz.

## Umfang

- Klassendefinition: Basiswerte je Attribut, Wachstum pro Level, zugeordnete
  Fähigkeiten
- **Ausrüstungsleitern je Klasse**: ein Rüstungs- und ein Waffenpfad mit fester
  Wertetabelle je Stufe (ADR-017). Die **Länge ist je Leiter konfiguriert**, nicht
  global fünf — Warrior 5/6, Rogue 6/6, Mage 7/7. Die erreichte Stufe ist
  Charakterstand und wird von B07 persistiert (`V7_1`).
- **Bindungsprädikat**: B07 beantwortet „ist dieses Item Bestandteil des
  Charakters?" — es kennt die Stufenleitern. B11 fragt das Prädikat bei jeder
  Bewegungs-, Verkaufs- und Wegwerfroute (ADR-018).
- **Inventarsperre**: gebundene Items nicht ablegbar oder verschiebbar; die
  Drop-Aktion ist für **alle** Items abgeschaltet. Mob-Loot ist unberührt
  (ADR-018).
- **Auswahlablauf bei jedem Join** (ADR-021, erweitert ADR-020). Nicht nur beim
  ersten: die Auswahl ist auch der Weg, mit dem ein Konto zwischen seinen bis zu
  drei Charakteren wechselt. Ein Slot mit vorhandenem Charakter wird fortgesetzt
  und nennt in der Lore Level, beide Stufen und den letzten Spielzeitpunkt.
- Klassenbindung von Items (B11) und Fähigkeiten (B08)

## Was dieser Block zusätzlich mitgebracht hat

Vier Nähte, die das Projekt vorsah und nie gebaut hatte, plus eine Vorleistung für
B11. Alles davon ist ADR-021 festgehalten und für B08 relevant, weil B08 auf
denselben Nähten aufsetzt.

- **`SessionObserver`** (`rpg-platform.session`): B03 erlaubt genau einen
  Join- und einen Quit-Handler, also bekommen andere Blöcke die bereite Sitzung
  gemeldet statt selbst zu lauschen. **B08 wird das ebenso brauchen.**
- **`SessionLifecycle.activateCharacter` und `SessionAttachment.onCharacterActivated`**:
  der Weg von „Klasse gewählt" in den Spielzustand. Die Wahl legte den Charakter an,
  aber die laufende Sitzung konnte ihn nicht annehmen. Der Rückruf bekommt das
  `SessionBundle` mit, damit B04, B06 und B07 die gespeicherten Werte nicht ein
  zweites Mal abfragen.
- **`SessionAttachment.order()`**: Zulieferer laufen vor der Rechnung. Die
  Modulstartreihenfolge ist hier die falsche, weil B04 rechnet und B06/B07 liefern.
- **Charakter-Inventar und Enderchest** (`V7_2`, Vorleistung für B11): beide
  Behälter gehören in Vanilla dem *Spieler*, und die Auswahl lässt ihn zwischen
  Charakteren wechseln. Ohne Haltung ging Gefarmtes verloren. Das Format ist
  undurchsichtig; **B11 wird es voraussichtlich ersetzen**, die Naht sollte bleiben.
- **Anzeigen**: Actionbar (eigene Werte) und eine Chatzeile über das getroffene
  Ziel, beide bewusst *nicht* `HudRenderer` genannt — der Name bleibt B13. Dabei
  fiel auf, dass `DamageAggregator` ein untätiges Fenster nie schloss und
  `publishExpiredCombatStates` in der Produktion von nichts angetrieben wird.
  Ersteres ist behoben, Letzteres ausdrücklich offen (siehe unten).

## Architekturvorgaben

- Der **Inhalt** jeder Klasse ist vollständig datengetrieben, die **Menge** der
  Klassen steht im Code (ADR-019). Kein Sonderfall je Klasse in der Logik.
- Die Klasse liefert Basiswerte, Wachstumskurven und die Werte der erreichten
  Ausrüstungsstufen als Modifier-Quelle `SourceKind.CLASS` an B04, nicht als
  Sonderfall in der Berechnung. Nach ADR-017 ist das die **dominante**
  Stat-Quelle.
- Die Auswahl-GUI arbeitet mit Vanilla-Materialien (ADR-005).
- Die Regel liegt in `rpg-core`, der Listener der Inventarsperre in
  `rpg-platform` — dasselbe Muster wie `VanillaDamageListener` in B05.
- **Kein Vorgriff:** B07 benennt Fähigkeits-IDs und Freischaltstufen, B08
  implementiert sie. B07 definiert die Stufenleiter und einen undurchsichtigen
  `cost`-Block, B11/B16 legen aus, wer den Aufstieg bezahlt.

## Offene Fragen

- [x] **Die drei Klassen**: Warrior, Mage, Rogue. Rollenprofil: Warrior =
      Nahkampf/Tank (hohe Health/Defense), Rogue = agiler Nahkampf (hoher
      Attackspeed), Mage = Magieschaden/Mobilität (hoher Magic Damage/Mana).
      *(2026-08-19)*
- [x] **Basiswerte je Klasse für alle acht Attribute**: ausgearbeitet in
      `specs/007-class-system/spec.md` und ausgeliefert in `classes.yml`.
      *(2026-08-21)*
- [x] **Wachstumskurven je Klasse und Attribut**: ebenso. Sie **ersetzen** das
      klassenneutrale Levelwachstum aus B06, sie addieren sich nicht dazu —
      `ClassesModule` entfernt B06s Contributor beim Start und bricht ab, wenn er
      nicht da war. Die drei Prozentattribute bleiben bei null und kommen
      vollständig aus der Leiter. *(2026-08-21)*
      **Falle, die dabei fast durchging:** `attackSpeed` hat die Basis 4,0 und
      `movementSpeed` die Basis 0,1 — die Werte sind absolut, nicht prozentual.
      `movement-speed: 0.30` hätte +300 % bedeutet. Die Umrechnungstabelle steht im
      Kopf von `classes.yml`.
- [x] **Waffen-/Rüstungsbeschränkungen**: Nicht als Filter, sondern als fester
      Pfad. Je Klasse genau eine Rüstungs- und eine Waffenleiter mit festen,
      nicht gewürfelten Werten. **Die Länge ist je Leiter konfiguriert**, nicht
      global fünf: Warrior 5/6, Rogue 6/6, Mage 7/7 (Rüstung/Waffe). Warrior
      trägt schwere Rüstung, Rogue Gold und Kettenhemd, Mage durchgehend Leder in
      wechselnder Farbe. Waffen: Warrior und Rogue Schwert mit unterschiedlicher
      Materialfolge, Mage Speer. Ersetzt Beute-Ausrüstung → **ADR-017**.
      *(2026-08-21)*
- [x] **Sichtbarkeit der Progression**: Färbung und Trim sind für Mage und Rogue
      **Pflicht**, nicht Addon — bei ihnen unterscheidet das Material die oberen
      Stufen nicht mehr. Leder ist färbbar, Gold und Kettenhemd nicht → ADR-017.
      *(2026-08-21)*
- [x] **Ablegen und Droppen**: gebundene Items (Klassenrüstung, Klassenwaffe) nie
      ablegbar; die Drop-Aktion für alle Items abgeschaltet; ungebundene Items
      frei beweglich; Mob-Loot unberührt. B07 bringt Sperre und Bindungsprädikat
      mit → **ADR-018**. *(2026-08-21)*
- [x] **Vierte Klasse**: Drei Klassen bleiben im Code festgeschrieben, ihr Inhalt
      ist Config. Eine vierte Klasse ist ein späteres Upgrade und kostet einen
      Enum-Wert plus Migration → **ADR-019**. *(2026-08-21)*
- [x] **Klassenwechsel**: Nicht möglich, Klasse ist permanent. *(2026-08-19)*
- [x] **Charakter-Slots**: 3 Slots pro Account (ein Slot je Klasse).
      *(2026-08-19)*
- [x] **Vor der Klassenwahl**: kein Spielzustand. Die GUI öffnet sich nach dem
      Laden der Sitzung und lässt sich nicht schließen; bis zur Wahl kein
      Stat-Snapshot, kein Schaden, keine Bewegung. B04 und B05 brauchen damit
      keinen „kein Charakter"-Fall. Die Tutorialwelt aus ADR-006 bleibt
      nachrüstbar → **ADR-020**. *(2026-08-21)*
- [ ] Kosmetik (Netherite-Templates, Färbung) ist laut Entscheidung Addon. Im
      Stufen-Schema ist ein Feld reserviert; wann es gefüllt wird, ist offen.

## Akzeptanzkriterien (Entwurf)

- Der Klassenlader weist eine unbekannte Klassen-ID ausdrücklich ab statt sie
  stillschweigend zu überspringen; damit ist belegt, dass nur Enum und Migration
  über die Klassenmenge entscheiden (ADR-019).
- Basiswerte, Wachstumskurven und beide Ausrüstungsleitern aller drei Klassen
  ändern sich rein über Config, ohne Codeänderung; der Test weist das nach.
- Die Klassenwahl ist persistent und übersteht Relogin und Serverneustart.
- Die erreichte Rüstungs- und Waffenstufe übersteht Relogin und Serverneustart.
- Jede Route zum Ablegen oder Droppen eines gebundenen Items ist einzeln getestet
  und abgewiesen — eine vergessene Route ist ein Loch in einer Regel, die als
  absolut gilt (ADR-018). Der Gegentest gehört dazu: ein **ungebundenes** Item
  lässt sich frei bewegen.
- Jeder Weg aus der Auswahl-GUI heraus führt zurück in die GUI: Escape,
  Inventarwechsel, Befehl, Weltwechsel (ADR-020). Dieselbe Vollständigkeitspflicht.
- Basiswerte aller Klassen sind in einer Übersicht dokumentiert und getestet.

## Was offen bleibt

**Vier Punkte brauchen einen laufenden Paper-Server** und sind nicht durch
MockBukkit belegbar. Bei Hand geprüft und bestätigt (2026-08-22): Auswahl öffnet
sich bei jedem Beitritt und ist nicht schließbar, Bewegung gesperrt, Rüstung über
keine Route ablegbar, Relogin erhält Klasse, Stufen, Level, Leben, Inventar und
Enderchest, Meldung bei vollem Inventar. Nicht geprüft, weil es dafür noch keinen
Weg im Spiel gibt:

- **Stufenaufstieg je Klasse** (T142). `TierAdvance` ist getestet, aber niemand
  ruft es — der Aufstieg braucht einen Auslöser, und wer ihn bezahlt, entscheiden
  B11 und B16 (der `cost`-Block bleibt uninterpretiert).
- **Gleiche Schlagrate bei Schwert und Stab** (T121/T143). Der Quelltextnachweis
  steht in `AttributeNeutralizationTest`; MockBukkit kann leere Überschreibung
  nicht von keiner unterscheiden, das misst nur ein echter Server.

**Zwei Befunde aus anderen Blöcken**, hier notiert weil sie hier auffielen:

- `DefaultCombatPipeline.publishExpiredCombatStates()` wird in der Produktion von
  nichts angetrieben. Derzeit harmlos, weil `isInCombat` aus einem Zeitstempel
  rechnet — aber sobald B12 oder B13 auf die abfallende Flanke hört, fehlt sie.
- Die Mobwerte in `combat.yml` stammen aus B05 und wurden nach ADR-017 nicht
  nachgezogen. Ein Standardmob macht bei einem Warrior auf Level 1 rund 0,7 von
  20 Vanilla-Leben pro Treffer, auf Level 60 rund 0,02. Das ist eine
  Balancing-Entscheidung, keine technische, und gehört zur Mobskalierung
  (B09/B10).
