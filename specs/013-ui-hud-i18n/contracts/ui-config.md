# Contract · `ui-config` — das Schema von `ui.yml`

Geprüft beim Start. **Jede Meldung nennt Datei, Schlüssel und Grund** (Prinzip V, Fail-Fast) — wie
in jedem Block seit B01.

---

## §1 · Die Datei

```yaml
# Welcher Sprachsatz gilt. Die Datei muss VOLLSTAENDIG sein, sonst startet der Server nicht.
language: en

hud:
  # Der eine Sammeltakt. Kuerzer heisst mehr Pakete ohne Gewinn; laenger heisst, die
  # Actionbar blinkt, weil Minecraft sie nach etwa zwei Sekunden ausblendet.
  tick-ms: 1000

  action-bar:
    enabled: true
  boss-bar:
    enabled: true
    # Wie lange der Zonenname stehen bleibt, wenn ihn nichts Wichtigeres verdraengt.
    zone-notice-seconds: 4
  sidebar:
    enabled: true

damage-numbers:
  enabled: true
  # Wie lange eine Zahl in der Welt steht. Sie ist NICHT persistent - ein Absturz
  # laesst keine zurueck.
  lifetime-ms: 1200
  # Hoehe ueber dem Trefferpunkt, in Bloecken.
  offset: 1.4
```

---

## §2 · Die Regeltabelle

| Schlüssel | Typ | Regel | Meldung bei Verstoß nennt |
|---|---|---|---|
| `language` | Text | nicht leer; eine Sprachdatei mit diesem Kürzel muss existieren | Datei, Schlüssel, welche Datei gesucht wurde |
| `hud.tick-ms` | Ganzzahl | > 0 | Datei, Schlüssel, gelesener Wert |
| `hud.action-bar.enabled` | Wahrheitswert | — | — |
| `hud.boss-bar.enabled` | Wahrheitswert | — | — |
| `hud.boss-bar.zone-notice-seconds` | Ganzzahl | > 0 | Datei, Schlüssel, gelesener Wert |
| `hud.sidebar.enabled` | Wahrheitswert | — | — |
| `damage-numbers.enabled` | Wahrheitswert | — | — |
| `damage-numbers.lifetime-ms` | Ganzzahl | > 0 | Datei, Schlüssel, gelesener Wert |
| `damage-numbers.offset` | Kommazahl | endlich | Datei, Schlüssel, gelesener Wert |

---

## §3 · Zwei Prüfungen, die keine Zahlenwahl sind

Die Tabelle oben prüft Werte. Diese zwei prüfen **Regeln** — sie können auch dann fehlschlagen, wenn
jeder einzelne Wert für sich in Ordnung ist. Beide brechen den Start ab.

### §3.1 · Ein Sprachsatz ist vollständig oder er ist keiner

Der gewählte Satz muss **jeden** Schlüssel kennen, den irgendein Modul deklariert (FR-018).

**Warum das nicht kulant sein darf:** ein fehlender Text darf nie als Platzhalter oder leere Zeile
beim Spieler ankommen (FR-019). Die Situationen, die diese Texte abdecken, sind genau die seltenen —
eine abgelehnte Anmeldung während eines Datenbankausfalls, ein erzwungener Rauswurf bei vollem
Puffer. Das sind die schlechtesten denkbaren Momente, um einen fehlenden Text zu entdecken.

**Die Maschinerie ist gebaut** ([research.md](../research.md) R8):
`MessageKeyValidator.verifyAllPresent` meldet bereits **alle** fehlenden Schlüssel auf einmal, nicht
nur den ersten — *„an operator fixing a configuration file wants the whole list in one pass."*

**Die zwei Sonderwege bleiben bestehen.** B09 prüft seine Zonennamen und B10 seine Artnamen selbst,
weil deren Schlüssel erst nach dem Lesen von `zones.yml` beziehungsweise `mobs.yml` feststehen. Ein
Sprachwechsel darf sie **nicht** umgehen — sonst fällt eine unvollständige Übersetzung genau dort
durch, wo die meisten Schlüssel liegen.

### §3.2 · Keine zwei Fähigkeiten einer Klasse auf demselben Material

Geprüft über die **Vereinigung aus `Ability.item()` und `Ability.items()` je Klasse** (FR-032,
R6).

**Warum es diese Prüfung gibt:** das Vanilla-Cooldown-Overlay hängt am *Material des Spielers*,
nicht am Slot. Zwei gleiche Materialien in einem Loadout heißen: ein Cooldown läuft, **zwei** Slots
werden grau — und einer davon lügt.

**Warum die Mehrzahl in `items()` kein Detail ist:** eine passive Fähigkeit kann *mehrere*
Markierungsitems legen. Der Magier belegt mit *Rise & Fall* zwei Slots aus einer Fähigkeit, und eine
Klasse kommt auf bis zu sieben von neun. Wer nur `item()` prüft, prüft die Hälfte.

**Warum beim Start und nicht zur Laufzeit:** im Spiel sähe der Fehler wie ein Balancing-Zufall aus.
Eine stille Anzeigelücke wäre die schlechtere Antwort auf einen Konfigurationsfehler.

Die Meldung nennt Klasse, beide Fähigkeiten und das geteilte Material.

---

## §4 · Was ausdrücklich **nicht** konfigurierbar ist

| Nicht konfigurierbar | Warum |
|---|---|
| Welcher Wert auf welcher Fläche steht | Das ist die Frage, die dieser Block gerade beantwortet hat (FR-001). Zur Wahl gestellt wäre sie wieder offen — und zwei Flächen mit demselben Wert sind kein Layout, sondern eine doppelte Wahrheit |
| Die Bossbar-Rangfolge | FR-004. Eine wählbare Rangfolge hieße, dass niemand mehr sagen kann, was ein Spieler sieht |
| Das Skill-Leisten-Layout | Gehört B08 (FR-024) |
| Anzeigen je Spieler | Nur serverweit (FR-013a). Persönliche Einstellungen wären dauerhafter Zustand je Charakter — ein B02-Schema und eine Migration für Anzeigevorlieben |
| Spielertexte | Stehen im Sprachsatz, nicht hier (Prinzip V) |

---

## §5 · Nachladen

`UiModule.applyReloadedConfig` tauscht die Konfiguration **im Ganzen**, nach dem Muster von
`ZoneModule`, `MobModule` und `ItemModule`. Ein Nachladen wirkt auf die nächste Zeichnung — es gibt
nichts Laufendes, das eine alte Einstellung festhält.

**Die Sprache ist davon ausgenommen** und wirkt beim Start. Sie zur Laufzeit zu tauschen hieße,
mitten in einer Sitzung jeden gehaltenen Text ungültig zu machen; der Gewinn wäre, einen Neustart zu
sparen, den ein Betreiber ohnehin einplant, wenn er die Sprache umstellt.
