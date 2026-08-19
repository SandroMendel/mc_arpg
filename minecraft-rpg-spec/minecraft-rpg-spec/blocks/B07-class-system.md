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

- Klassendefinition: Basiswerte je Attribut, Wachstum pro Level, erlaubte
  Ausrüstung, zugeordnete Fähigkeiten
- Auswahlablauf beim ersten Join
- Regeln für Klassenwechsel und Reset
- Klassenbindung von Items (B11) und Fähigkeiten (B08)

## Architekturvorgaben

- Klassen sind **vollständig datengetrieben**. Drei Klassen heute, sechs später —
  ohne Codeänderung.
- Die Klasse liefert Basiswerte und Wachstumskurven als Modifier-Quelle an B04,
  nicht als Sonderfall in der Berechnung.
- Die Auswahl-GUI arbeitet mit Vanilla-Materialien (ADR-005).

## Offene Fragen

- [x] **Die drei Klassen**: Warrior, Mage, Rogue. Rollenprofil: Warrior =
      Nahkampf/Tank (hohe Health/Defense), Rogue = agiler Nahkampf (hoher
      Attackspeed), Mage = Magieschaden/Mobilität (hoher Magic Damage/Mana).
      *(2026-08-19)*
- [ ] Basiswerte je Klasse für alle acht Attribute → bei `/specify` B07
      auszuarbeiten.
- [ ] Wachstumskurven je Klasse und Attribut → bei `/specify` B07 auszuarbeiten.
- [ ] Waffen-/Rüstungsbeschränkungen je Klasse?
- [x] **Klassenwechsel**: Nicht möglich, Klasse ist permanent. *(2026-08-19)*
- [x] **Charakter-Slots**: 3 Slots pro Account (ein Slot je Klasse).
      *(2026-08-19)*
- [ ] Was passiert vor der Klassenwahl — Tutorialbereich, eingeschränkter
      Spielzustand?

## Akzeptanzkriterien (Entwurf)

- Eine vierte Klasse lässt sich rein über Konfiguration ergänzen; der Test weist
  das nach.
- Die Klassenwahl ist persistent und übersteht Relogin und Serverneustart.
- Basiswerte aller Klassen sind in einer Übersicht dokumentiert und getestet.
