# B17 · Test & Deployment

| | |
|---|---|
| **Schicht** | Querschnitt |
| **Status** | Entwurf |
| **Abhängig von** | B01 |
| **Benötigt von** | alle |

## Zweck

Sicherstellen, dass Regeländerungen nachweisbar korrekt sind und der Weg vom
Commit zum laufenden Server reproduzierbar ist.

## Umfang

- Unit-Tests der Domänenschicht ohne Server
- Integrationstests gegen echte PostgreSQL-Instanz (Testcontainers)
- Lasttests (siehe B15)
- Build-Pipeline und Artefakterzeugung
- Deployment auf Test- und Produktivserver
- Backup- und Wiederherstellungsverfahren

## Architekturvorgaben

- Jede Formel und jede Regel in `rpg-core` hat Unit-Tests. Das ist Voraussetzung
  dafür, dass `rpg-core` bukkit-frei bleibt (Constitution III.1).
- Persistenz wird gegen eine echte Datenbank getestet, nicht gegen Mocks.
- Ein Testserver mit produktionsnaher Konfiguration existiert getrennt vom
  Livebetrieb.
- Datenbank-Backups werden regelmäßig **und deren Wiederherstellung geprüft**.

## Offene Fragen

- [ ] CI-System (GitHub Actions o. a.)?
- [ ] Wie wird auf den Produktivserver ausgeliefert?
- [ ] Backup-Intervall und Aufbewahrung?
- [ ] Gibt es ein öffentliches Testrealm für Spieler?
- [ ] Mindest-Testabdeckung als Merge-Kriterium?

## Akzeptanzkriterien (Entwurf)

- Die Pipeline baut, testet und erzeugt bei jedem Commit ein Artefakt.
- Ein Rollback auf die vorherige Version ist ohne Datenverlust möglich.
- Die Wiederherstellung aus einem Backup ist mindestens einmal erfolgreich
  durchgeführt und dokumentiert.
