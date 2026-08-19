# B14 · Commands, Permissions & Admin-Tools

| | |
|---|---|
| **Schicht** | 3 — Meta |
| **Status** | Entwurf |
| **Abhängig von** | alle |
| **Benötigt von** | — |

## Zweck

Bedienoberfläche für Spieler und Betreiber sowie das Rechtemodell.

## Umfang

- Spieler-Commands: Charakter, Statistiken, Leaderboard, Fähigkeiten, Reise
- Admin-Commands: Items geben, Mobs spawnen, Level/Werte setzen, Zone neu laden,
  Spielerdaten inspizieren, Konfiguration neu laden
- Permission-Baum
- Audit-Log für Eingriffe an Spielerdaten
- Tab-Completion

## Architekturvorgaben

- Einheitliches Command-Framework mit Brigadier-Integration statt manueller
  Argumentzerlegung.
- Jeder Admin-Eingriff, der Spielerdaten verändert, wird protokolliert (wer, was,
  wann, an wem).
- Commands rufen ausschließlich öffentliche Blockschnittstellen auf, nie Interna.
- Rate-Limits auf Commands, die Datenbankabfragen auslösen.

## Offene Fragen

- [ ] Vollständige Command-Liste mit Syntax
- [ ] Permission-Struktur und Rollen (Spieler, Moderator, Admin, Entwickler)
- [ ] Wird ein externes Permission-Plugin unterstützt (LuckPerms)?
- [ ] Bedarf an Web-/Konsolen-Tools außerhalb des Spiels?

## Akzeptanzkriterien (Entwurf)

- Ohne passende Permission ist kein Admin-Command ausführbar.
- Jeder datenverändernde Eingriff erscheint im Audit-Log.
- Tab-Completion liefert kontextabhängig gültige Werte.
