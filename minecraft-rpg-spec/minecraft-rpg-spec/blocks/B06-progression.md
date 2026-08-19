# B06 · Progression (Erfahrung & Level)

| | |
|---|---|
| **Schicht** | 1 — Regel-Engine |
| **Status** | Entwurf |
| **Abhängig von** | B03, B04 |
| **Benötigt von** | B07, B08, B09, B11, B12 |

## Zweck

Eigenes Erfahrungs- und Levelsystem, unabhängig von Vanilla-XP.

## Umfang

- XP-Quellen und deren Höhe (Mob-Kills, Zonen-Ziele, ggf. Quests)
- XP-Kurve und Maximallevel
- Wirkung eines Level-Ups auf die Attribute (B04)
- Level-Anforderungen für Zonen (B09), Fähigkeiten (B08) und Items (B11)
- Anzeige des Fortschritts (B13)
- Persistierung (B02)

## Architekturvorgaben

- Vanilla-XP und die Vanilla-Erfahrungsleiste werden **nicht** als
  Fortschrittsspeicher verwendet (Glossar-Abgrenzung).
- XP-Zuwachs erzeugt keinen DB-Zugriff; er markiert die Session als dirty.
- Die XP-Kurve ist eine konfigurierte Funktion oder Tabelle, kein Code.
- Level-Up-Wirkungen laufen über das Modifier-Modell aus B04, nicht über direkte
  Wertmanipulation.

## Offene Fragen

- [x] **Maximallevel**: 60, moderat ansteigende Kurve. *(2026-08-19)*
- [x] **Was gibt ein Level-Up**: Jedes Level gibt eine kleine feste
      Wertsteigerung (kein Skillpunkt-System). Fähigkeiten werden separat per
      Level freigeschaltet und mit Coins aufgewertet (siehe B08). *(2026-08-19)*
- [x] **Freie Punkte / Reset**: entfällt — es gibt keine frei verteilbaren
      Punkte, siehe oben. *(2026-08-19)*
- [ ] XP-Skalierung bei Levelunterschied zum Mob (Anti-Powerleveling)?
- [ ] Gruppen-/Party-XP-Teilung vorgesehen?
- [ ] Gibt es nach Maximallevel eine Fortsetzung (Paragon, Prestige)?

## Akzeptanzkriterien (Entwurf)

- XP-Kurve und Level-Wirkungen sind vollständig konfigurierbar und unit-getestet.
- Ein Level-Up aktualisiert Attribute, HUD und Freischaltungen in einem Vorgang.
- 1000 XP-Ereignisse pro Sekunde erzeugen keinen einzigen DB-Zugriff.
