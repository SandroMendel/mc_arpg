# B07 · Klassen-System

| | |
|---|---|
| **Schicht** | 1 — Regel-Engine |
| **Status** | Entwurf |
| **Abhängig von** | B04, B06 |
| **Benötigt von** | B08, B11, B13 |

## Zweck

Drei wählbare Spielerklassen mit je eigenem Rollenprofil, Basiswerten,
Wachstumskurven und Fähigkeitensatz.

## Umfang

- Klassendefinition: Basiswerte je Attribut, Wachstum pro Level, zugeordnete
  Fähigkeiten
- **Ausrüstungsleitern je Klasse**: ein Rüstungs- und ein Waffenpfad mit je fünf
  Stufen, feste Werte je Stufe (ADR-017). Die erreichte Stufe ist Charakterstand
  und wird von B07 persistiert.
- **Bindungsprädikat**: B07 beantwortet „ist dieses Item Bestandteil des
  Charakters?" — es kennt die Stufenleitern. B11 fragt das Prädikat bei jeder
  Bewegungs-, Verkaufs- und Wegwerfroute (ADR-018).
- **Inventarsperre**: gebundene Items nicht ablegbar oder verschiebbar; die
  Drop-Aktion ist für **alle** Items abgeschaltet. Mob-Loot ist unberührt
  (ADR-018).
- Auswahlablauf beim ersten Join
- Klassenbindung von Items (B11) und Fähigkeiten (B08)

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
- [ ] Basiswerte je Klasse für alle acht Attribute → bei `/specify` B07
      auszuarbeiten.
- [ ] Wachstumskurven je Klasse und Attribut → bei `/specify` B07 auszuarbeiten.
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
