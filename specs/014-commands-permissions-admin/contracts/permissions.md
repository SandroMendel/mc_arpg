# Vertrag: Der Rechtebaum

## Grundsatz

**Nur Bukkit-Rechte.** Der Baum steht in `plugin.yml`, die Vergabe übernimmt Bukkits eigenes System
oder ein beliebiges Plugin, das es bedient. **LuckPerms funktioniert damit automatisch mit, ohne
dass wir eine Abhängigkeit aufnehmen** — keine neue Bibliothek und damit kein zusätzlicher
Klassenlader-Blindfleck.

Die Stufen Spieler / Moderator / Admin sind **Mengen von Rechteknoten**, kein eigenes Rollenmodell.
Bukkit kennt als Voreinstellung nur *alle* oder *Operator*; alles dazwischen vergibt der Betreiber
mit dem Werkzeug seiner Wahl.

## Der Baum

### Spieler — `default: true`

Vorhanden und unverändert. Die eigenen Zahlen anzusehen ist kein administrativer Akt.

| Knoten | Kommando |
|---|---|
| `rpg.ui.character` | `/char` |
| `rpg.statistics.own` | `/stats` |
| `rpg.statistics.top` | `/top` |
| `rpg.currency.balance` | `/coins` (eigener Stand) |
| `rpg.item.trash` | `/trash` |

`rpg.item.trash` bleibt bei `default: true`, und das ist kein Versehen: ADR-018 schaltet das
Fallenlassen ab, also ist `/trash` der einzige Weg, etwas Unverkäufliches loszuwerden. Es
einzuschränken hieße, ein volles Inventar zur Sackgasse zu machen.

### Moderator — `default: op`, gedacht zum Weitergeben

Rein lesend. Wer diese Menge bekommt, kann Support leisten und nichts kaputt machen.

| Knoten | Wirkung |
|---|---|
| `rpg.admin.inspect.sheet` | fremdes Charakterblatt |
| `rpg.admin.inspect.statistics` | fremde Statistiken |
| `rpg.admin.inspect.inventory` | fremdes Inventar |
| `rpg.admin.inspect.session` | Sitzungszustand |
| `rpg.admin.no-class` | vorhanden: die Welt ohne Klassenwahl betreten, für Moderation |

### Admin — `default: op`

Verändert Daten. Jede Ausführung steht im Audit-Log.

| Knoten | Kommando |
|---|---|
| `rpg.currency.admin` | `/coins set\|add\|remove` (vorhanden) |
| `rpg.progression.admin` | `/xp`, `/rpg set level\|xp` (vorhanden) |
| `rpg.admin.item.give` | `/rpg item give` |
| `rpg.admin.mob.spawn` | `/rpg mob spawn` |
| `rpg.admin.set.class` | `/rpg set class` |
| `rpg.admin.reload` | `/rpg reload` |
| `rpg.admin.audit` | `/rpg audit` — die Leseseite des Logs |

## Die vier Zusagen

1. **Jeder benutzte Knoten steht in `plugin.yml`**, mit Beschreibung und ausdrücklichem `default`.
   Ein Recht, das nur im Quelltext existiert, bricht den Start — es wirkt nicht still (FR-010).
2. **Ohne Recht keine Wirkung** (FR-013). Nicht „teilweise ausgeführt", nicht „ausgeführt und
   danach gemeldet".
3. **Ohne Recht kein Vorschlag** (FR-014). Wer `/rpg ` tippt, sieht nur, was er darf. Ein
   Unterkommando, das man sieht und nicht benutzen kann, ist eine Einladung zur Fehlermeldung.
4. **Ein frisch aufgesetzter Server ist brauchbar und sicher zugleich**: Alle fünf
   Spielerkommandos gehen sofort, kein einziges Admin-Kommando (US2, Szenario 3).

## Namensregel

`rpg.<bereich>.<sache>`, und wo es um einen Eingriff geht, `rpg.admin.<bereich>.<sache>`.

Die vorhandenen Knoten weichen davon ab — `rpg.currency.admin` und `rpg.progression.admin` tragen
das `admin` hinten. **Sie bleiben, wie sie sind.** Sie stehen in ausgelieferten Konfigurationen und
in Abnahmeschritten; sie umzubenennen würde bei jedem Betreiber, der sie vergeben hat, still ein
Recht entziehen. Die Regel gilt für Neues, nicht rückwirkend — und dieser Absatz steht hier, damit
die Abweichung als entschieden erkennbar ist und nicht als Schlamperei.
