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
- Admin-Commands: Items geben, Mobs spawnen, Stufe/Erfahrung/Klasse setzen, Zone neu laden,
  Spielerdaten inspizieren, Konfiguration neu laden
  - **Eingeschränkt am 2026-09-03:** Hier stand „Level/**Werte** setzen". Einzelne **Attribute**
    direkt zu setzen ist raus (FR-024a, ADR fällig). `StatEngine` hat bewusst keinen Setzer — Werte
    sind aus `ModifierSet`s je Quelle abgeleitet, und `SourceKind` ist ein geschlossener
    Aufzählungstyp, dessen Deklarationsreihenfolge zugleich die Summationsreihenfolge ist. Ein
    Admin-Wert bräuchte einen siebten Eintrag darin und wäre nach dem nächsten Anmelden trotzdem
    weg, weil B04 Beiträge nicht persistiert.
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

## Offene Fragen — beantwortet am 2026-09-03

Die Antworten sind bindend und in `specs/014-commands-permissions-admin/spec.md` ausgeführt.

- [x] **Vollständige Command-Liste mit Syntax.** Die sechs vorhandenen Spielerkommandos (`/char`,
      `/coins`, `/trash`, `/xp`, `/top`, `/stats`) behalten Namen und Syntax und ziehen lediglich
      auf das gemeinsame Gerüst um (FR-005/FR-006 — die Einlösung von ADR-028 und ADR-051). Die
      neuen Admin-Werkzeuge liegen unter einer gemeinsamen Wurzel `/rpg <Unterkommando>`; diesen
      Namen benutzt `ZoneModule` in seinem Javadoc bereits (`/rpg reload`). *(2026-09-03)*
- [x] **Permission-Struktur und Rollen.** Spieler / Moderator / Admin werden als **Rechtebaum**
      ausgedrückt, nicht als eigenes Rollenmodell. „Moderator" ist die Menge der lesenden Rechte
      plus dem vorhandenen `rpg.admin.no-class`. Jedes Recht wird in `plugin.yml` mit Beschreibung
      und ausdrücklichem `default` deklariert; ein Recht, das nur im Quelltext existiert, fällt beim
      Start auf. *(2026-09-03)*
- [x] **Externes Permission-Plugin (LuckPerms): nein, keine Abhängigkeit.** Der Rechtebaum steht in
      `plugin.yml`, die Vergabe übernimmt Bukkits eigenes System oder ein beliebiges Plugin, das es
      bedient — LuckPerms funktioniert damit automatisch mit. Keine neue Bibliothek und damit kein
      zusätzlicher Klassenlader-Blindfleck. *(2026-09-03)*
- [x] **Web-/Konsolen-Tools außerhalb des Spiels: nein.** B14 bleibt Kommandos, Rechte und
      Audit-Log. Die Kommandos müssen allerdings von der **Serverkonsole** aus laufen und mit einer
      erklärenden Meldung abbrechen, wenn sie einen Spielerbezug brauchen. *(2026-09-03)*
- [x] **Zusatzfrage, die erst die Spec zeigte: zählt eine handgesetzte Kreatur gegen das
      Spawn-Budget?** Nein — sie steht in der `HordeRegistry` (sonst räumt ADR-050 sie weg), zählt
      aber gegen eine **eigene Obergrenze**. Das Budget bleibt aussagekräftig und die Umgehung ist
      beziffert statt unbegrenzt. *(2026-09-03)*

## Was die Bestandsaufnahme ergeben hat

Wie bei B13 ist der Block zur Hälfte Aufräumen, nicht Bauen:

- **Das Audit-Log ist fertig** (B02 FR-018: `AuditEntry`, `AuditLogRepository`, append-only, in
  `V1__baseline.sql`) und wird von B08b und B06 bereits benutzt. B14 baut es nicht, sondern benutzt
  es lückenlos — und macht es erstmals **lesbar**: `between()` hat außerhalb der Tests keinen
  Aufrufer.
- **Das Neuladen der Konfiguration ist fertig** (B01 FR-003/FR-004: `ConfigLoader.reloadAll()`,
  atomar mit Rückfall, sechs Module halten einen `ConfigHandle` und haben Nachladehaken) — **und
  wurde nie aufgerufen.** `reloadAll()` und `notifyReloaded()` haben nur Tests als Aufrufer. B14 ist
  die fehlende Verdrahtung; siehe das Muster in `vuntexrpg-block-done-criteria`.
- **Tab-Completion gibt es fünfmal, jede für sich gebaut**: `CharacterSheetCommand`, `TopCommand`,
  `StatisticsCommand`, `CoinsCommand` und `XpCommand` haben je eine eigene `onTabComplete`;
  `TrashCommand` nimmt kein Argument und braucht keine. Das Problem ist die Fünffachheit, nicht die
  Abwesenheit — jede Liste steht neben der Argumentprüfung desselben Kommandos und kann mit ihr
  auseinanderlaufen. **Brigadier kommt im ganzen Baum null mal vor**, registriert wird klassisch
  über `plugin.yml` und `setExecutor`.
- **Es fehlt tatsächlich**: ein Weg, ein Item zu geben (hat die B11-Abnahme behindert), ein Weg,
  eine Kreatur zu setzen, ein Weg, fremde Spielerdaten anzusehen, und Rate-Limits.

## Akzeptanzkriterien (Entwurf)

- Ohne passende Permission ist kein Admin-Command ausführbar.
- Jeder datenverändernde Eingriff erscheint im Audit-Log.
- Tab-Completion liefert kontextabhängig gültige Werte.
