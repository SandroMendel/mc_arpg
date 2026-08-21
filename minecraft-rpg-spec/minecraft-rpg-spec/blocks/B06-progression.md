# B06 · Progression (Erfahrung & Level)

| | |
|---|---|
| **Schicht** | 1 — Regel-Engine |
| **Status** | Implementiert (2026-08-20) — 151 Aufgaben, davon 150 erledigt; 147 eigene Tests, 753 im Projekt, 0 Fehler, 0 übersprungen. Offen allein: Durchlauf auf einem echten Paper-Server |
| **Abhängig von** | B03, B04, B05 |
| **Benötigt von** | B07, B08, B09, B11, B12, B13, B14 |

## Zweck

Eigenes Erfahrungs- und Levelsystem, unabhängig von Vanilla-XP.

## Umfang

- XP-Quellen und deren Höhe (Mob-Kills, Zonen-Ziele, ggf. Quests)
- XP-Kurve und Maximallevel
- Wirkung eines Level-Ups auf die Attribute (B04)
- Level-Anforderungen für Zonen (B09), Fähigkeiten (B08) und Items (B11)
- Party-Modell und XP-Teilungsregeln (Mitgliedschaft, Nähe-Bonus)
- Anzeige des Fortschritts (B13)
- Persistierung (B02)

### Ausdrücklich nicht in B06

- Party-Befehle (`/party invite`, `/party leave`, …) → B14, wie alle Befehle
- Party- und Fortschrittsanzeige → B13, wie alles Sichtbare
- Paragon, Prestige oder sonstige Progression jenseits Level 60
- Klassenspezifische Basiswerte und Wachstumskurven → B07

## Architekturvorgaben

- Vanilla-XP und die Vanilla-Erfahrungsleiste werden **nicht** als
  Fortschrittsspeicher verwendet (Glossar-Abgrenzung).
- XP-Zuwachs erzeugt keinen DB-Zugriff; er markiert die Session als dirty.
- Die XP-Kurve ist eine konfigurierte Tabelle, kein Code.
- Level-Up-Wirkungen laufen über das Modifier-Modell aus B04, nicht über direkte
  Wertmanipulation.
- Die Party ist reiner Laufzeitzustand. Sie wird nicht persistiert und löst sich
  auf, wenn das letzte Mitglied die Sitzung verlässt.
- B06 liest Schadensanteile aus `CombatDeathEvent` (B05), berechnet sie nicht neu.

## Offene Fragen — geklärt (2026-08-20)

- [x] **Maximallevel**: 60, moderat ansteigende Kurve. *(2026-08-19)*
- [x] **Was gibt ein Level-Up**: Jedes Level gibt eine kleine feste
      Wertsteigerung (kein Skillpunkt-System). Fähigkeiten werden separat per
      Level freigeschaltet und mit Coins aufgewertet (siehe B08). *(2026-08-19)*
- [x] **Freie Punkte / Reset**: entfällt — es gibt keine frei verteilbaren
      Punkte, siehe oben. *(2026-08-19)*
- [x] **XP-Kurve**: explizite Tabelle in `progression.yml`, eine Zeile je Level
      von 2 bis 60. Keine Formel. Bei 60 Leveln ist die Tabelle kurz genug, um
      lesbar zu bleiben, und jedes Level lässt sich einzeln nachjustieren, ohne
      alle anderen zu verschieben. Die Konfiguration wird beim Start
      vollständig validiert: alle Level lückenlos vorhanden, jeder Wert positiv,
      streng monoton steigend.
- [x] **XP-Skalierung bei Levelunterschied**: keine. Die XP eines Mobs hängt
      ausschliesslich am Mob, nie am Levelabstand zum Spieler. Konsequenz,
      bewusst in Kauf genommen: Powerleveling durch Mitnehmen in hohe Zonen ist
      möglich, und Startzonen-Mobs bleiben unbegrenzt farmbar. Die Begrenzung
      liegt damit allein bei den Zonenzugängen aus B09 (Levelanforderung je
      Zone), nicht bei der XP-Rechnung.
- [x] **Gruppen-/Party-XP**: echtes Party-System. Mitgliedschaft per Einladung,
      geteilte XP, Nähe-Bonus. Das Modell und die Teilungsregeln liegen in B06,
      die Befehle in B14, die Anzeige in B13 — B06 selbst hat keine
      spielerseitige Oberfläche.
- [x] **Nach Maximallevel**: nichts. Level 60 ist das Ende der
      Levelprogression. Weiteres Wachstum läuft über Coins (Fähigkeiten,
      B08) und Ausrüstung (B11). XP über Level 60 verfällt still. Paragon oder
      Prestige bleiben als eigener Block nachrüstbar, sind aber nicht Teil
      von B06.

### XP-Verteilung — Zusammenspiel mit B05

B05 hat bereits entschieden: XP anteilig nach Schadensanteil, Loot an den
höchsten Beitrag. Das Party-System setzt darauf auf und ersetzt es nicht:

1. Der Mob hat einen XP-Betrag aus der Konfiguration.
2. `DamageShare` aus `CombatDeathEvent` liefert die Anteile je Angreifer.
3. Eine Party gilt als **ein** Beitragender; ihr Anteil ist die Summe der
   Anteile ihrer Mitglieder.
4. Der Party-Anteil wird auf die Mitglieder **in Reichweite** gleichmässig
   verteilt, unabhängig davon, wer davon Schaden gemacht hat. Mitglieder
   ausserhalb der Reichweite bekommen nichts. Gemessen wird die Entfernung zum
   **gestorbenen Gegner** — der einzige Bezugspunkt, den alle gemeinsam haben.
5. Auf den Party-Anteil kommt ein **prozentualer** Nähe-Bonus je zusätzliches
   Mitglied in Reichweite, mit Obergrenze, damit gemeinsames Spielen nicht
   schlechter ist als allein zu spielen. Kein Festbetrag: der wäre auf Level 1
   riesig und auf Level 60 belanglos.

Alle Zahlen — Reichweite, Bonus je Mitglied, Bonusobergrenze, maximale
Partygrösse — stehen in `progression.yml`.

### Weitere Festlegungen aus der Klärungsrunde (2026-08-20)

- **Speicherform**: Level und XP **innerhalb** des Levels, nicht eine Gesamt-XP-
  Zahl. Sonst würde eine später erhöhte Kurve bestehende Charaktere rückwirkend
  im Level senken.
- **Party-Anführer**: genau einer je Party. Nur er lädt ein und entfernt;
  verlässt er die Party, geht die Rolle an das dienstälteste verbleibende
  Mitglied. Eine Party ist niemals ohne Anführer.
- **Fortschrittsmeldung**: XP-Gewinne werden innerhalb eines konfigurierten
  Fensters zu **einem** Ereignis gebündelt — dasselbe Muster wie die
  Schadenszahlen in B05. Bündelung in B06, Zeichnen in B13. Tritt ein Aufstieg
  bei offenem Bündel ein, wird das Bündel **zuerst** ausgeliefert, damit der
  Fortschrittsbalken nicht rückwärts springt.

### Zweite Klärungsrunde (2026-08-20)

- **Attributwachstum**: das Schema erlaubt alle acht Attribute mit je eigener
  Zuwachsrate, Null eingeschlossen. Die mitgelieferte Vorgabe lässt Leben, Mana,
  Verteidigung, physischen und magischen Schaden wachsen; Angriffsgeschwindigkeit,
  Laufgeschwindigkeit und Fähigkeiten-Cooldown stehen auf Null. Welche Attribute
  wachsen, ist damit Inhalt und keine Codeentscheidung — B07 kann Klassen
  unterscheiden, ohne dass B06 geändert wird.
- **Ressourcen beim Aufstieg**: Leben und Mana werden auf das **neue Maximum**
  aufgefüllt, genau einmal je Aufstieg. Bewusste Folge: der Aufstieg lässt sich
  in einen Bosskampf hinein aufsparen und wirkt dort als Vollheilung.
  Selbstbegrenzend, weil jedes Level nur einmal steigt.
- **Unbekannter Mob**: gibt den konfigurierten Standardbetrag, nicht null, plus
  eine einmalige Warnung je Mob-Art. Aufbau wie `mobs:` in `combat.yml` —
  `default` und darüber `by-type`.
- **Verwaltungseingriff**: Level und XP dürfen von einem Betreiber frei gesetzt
  werden, senken eingeschlossen; jeder Eingriff geht ins Audit-Log aus B02. Die
  Zusage „Level sinkt nie" gilt gegenüber dem Spieler, nicht gegenüber dem
  Betreiber — sonst wäre ein durch einen Fehler verschenktes Level nur von Hand
  in der Datenbank zu korrigieren.

## Akzeptanzkriterien (Entwurf)

- XP-Kurve und Level-Wirkungen sind vollständig konfigurierbar und unit-getestet.
- Die XP-Tabelle wird beim Start validiert; eine lückenhafte, nicht monotone
  oder negative Tabelle verhindert den Start mit einer benennenden Meldung.
- Ein Level-Up aktualisiert Attribute, HUD und Freischaltungen in einem Vorgang.
- 1000 XP-Ereignisse pro Sekunde erzeugen keinen einzigen DB-Zugriff.
- Eine Party aus fünf Mitgliedern verteilt XP ohne Zuweisung je Ereignis
  (Nullallokation im Kampfpfad, wie B05).
- XP auf Maximallevel verfällt, ohne einen Fehler zu erzeugen oder den
  Fortschrittsbalken zu verfälschen.
