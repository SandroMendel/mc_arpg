# Phase 1 Data Model: B03 · Spieler-Session & Datenlebenszyklus

Abgeleitet aus den Key Entities in `spec.md`. Konzeptionell — Feld- und Spaltennamen als Vertrag,
kein Implementierungscode.

Wie in B02 sind zwei Ebenen zu unterscheiden: **Laufzeitentitäten** leben nur im Speicher und
tragen den Lebenszyklus, **persistente Entitäten** entsprechen Tabellen.

## Laufzeitentitäten (`rpg-core/session`)

### Sitzung (`PlayerSession`)

Der im Speicher gehaltene, maßgebliche Zustand eines verbundenen Spielers (FR-016).

| Feld | Typ | Regeln |
|---|---|---|
| `playerId` | UUID | Die Account-Kennung; zugleich Schlüssel in der Registry |
| `state` | Enum | Bereitschaftszustand, siehe unten |
| `activeCharacter` | `PlayerCharacter` oder leer | Genau einer, solange die Sitzung besteht (FR-018); leer nur für einen Spieler ohne Charakter (FR-021) |
| `availableCharacters` | Liste | Alle Charaktere des Accounts, beim Laden mitgelesen |
| `loadedAt` | Zeitstempel | Wann die Sitzung bereit wurde; Grundlage für die Messung von SC-001 |

**Validierungsregeln**:

- Solange `state` nicht `READY` ist, liefert jede Abfrage von Spielerwerten eine erkennbare
  „noch nicht bereit"-Auskunft und **keine** Standardwerte (FR-004).
- `activeCharacter` wird beim Erzeugen der Sitzung gesetzt und ändert sich danach nicht
  (FR-021a/FR-021b). Es gibt keine Methode, die ihn austauscht — das Fehlen ist die Zusicherung.

### Bereitschaftszustand (`SessionState`)

| Wert | Bedeutung |
|---|---|
| `LOADING` | Der Zustand wird gelesen. Der Spieler ist, falls bereits in der Welt, bewegungsgesperrt und schadensimmun. |
| `READY` | Vollständig geladen. Der Spieler ist freigegeben, andere Blöcke dürfen Werte abfragen. |
| `UNLOADING` | Das Sitzungsende läuft; der abschließende Schreibvorgang ist angestoßen. |
| `FAILED` | Das Laden ist gescheitert. Der Spieler wird abgewiesen; es wird **nichts** geschrieben. |

**Erlaubte Übergänge**:

```
LOADING → READY        (Laden erfolgreich)
LOADING → FAILED       (Laden gescheitert oder Frist überschritten)
LOADING → UNLOADING    (Spieler trennt die Verbindung während des Ladens)
READY   → UNLOADING    (Sitzungsende auf beliebigem Weg)
UNLOADING → (entfernt) (abschließender Schreibvorgang abgeschlossen)
FAILED   → (entfernt)  (ohne jeden Schreibvorgang)
```

**Die beiden Übergänge, auf die es ankommt**:

- `FAILED → entfernt` **ohne** Schreibvorgang. Das ist FR-012: Eine gescheiterte Anmeldung darf den
  gespeicherten Datensatz nicht anfassen. Jeder Pfad, der von `FAILED` aus schreibt, wäre der Fehler,
  den dieser Block verhindern soll.
- `LOADING → UNLOADING`. Der Spieler trennt, während noch geladen wird (FR-015). Auch hier darf
  nichts geschrieben werden — es gibt keinen Zustand, den der Spieler je erhalten hätte. Das
  Ladeergebnis wird verworfen.

### Sitzungs-Registry (`SessionRegistry`)

| Feld | Typ | Regeln |
|---|---|---|
| `sessions` | Abbildung Spielerkennung → Sitzung | Höchstens ein Eintrag je Spieler (FR-014) |

**Validierungsregeln**:

- Das Anlegen einer zweiten Sitzung für denselben Spieler wird abgelehnt, nicht überschrieben. Ein
  stilles Überschreiben würde die erste Sitzung samt ungeschriebenem Fortschritt verlieren.
- Die Zahl der Einträge übersteigt die Zahl der verbundenen Spieler nicht dauerhaft (FR-009). Das
  ist keine Absicht, sondern wird durch den Abgleich erzwungen.

### Zwischenablage vorgeladener Sitzungen (`PendingSessionStash`)

Trägt eine im Vorlade-Ereignis fertig geladene Sitzung bis zum Betreten der Welt.

| Feld | Typ | Regeln |
|---|---|---|
| `pending` | Abbildung Spielerkennung → Sitzung mit Ablaufzeitpunkt | Ein Eintrag wird beim Abholen entfernt |

**Validierungsregeln**:

- Ein Eintrag, der nicht innerhalb einer kurzen Frist abgeholt wird, verfällt. Andernfalls bliebe
  eine vorgeladene Sitzung für immer liegen, wenn der Spieler nach erfolgreichem Vorladen doch nie
  die Welt betritt — etwa weil ein anderes Plugin ihn abweist.
- Das Verfallen räumt nur die Zwischenablage; es schreibt nichts, weil der Spieler nie eine Sitzung
  erhalten hat.

### Abgleich (`SessionReconciler`)

Kein Datenhalter, sondern der Mechanismus hinter FR-009 und SC-008.

**Regel**: In festem Abstand wird die Schlüsselmenge der Registry gegen die tatsächlich verbundenen
Spieler verglichen. Sitzungen ohne verbundenen Spieler werden entladen und entfernt — unabhängig
davon, warum sie liegengeblieben sind. Dasselbe gilt für abgelaufene Einträge der Zwischenablage.

## Persistente Entitäten

### `rpg.character` — Charakter (neu, Migration `V3_1`)

| Spalte | Typ | Regeln |
|---|---|---|
| `character_id` | UUID, Primärschlüssel | Identität dieses Charakters |
| `player_id` | UUID | Verweist auf `rpg.player_state.player_id`; Löschen kaskadiert |
| `character_class` | Text | `WARRIOR`, `MAGE` oder `ROGUE` |
| `data_version` | Ganzzahl | Fassung des Datensatzformats (FR-025 bis FR-027) |
| `revision` | Bigint | Wird bei jedem Schreibvorgang erhöht, wie in B02 |
| `created_at` | Zeitstempel mit Zone | |
| `last_played_at` | Zeitstempel mit Zone | Wann zuletzt mit diesem Charakter gespielt wurde |

**Schlüssel und Validierungsregeln**:

- **Eindeutigkeit über `(player_id, character_class)`.** Damit steckt die Regel „höchstens ein
  Charakter je Klasse" (FR-017, FR-020) im Schlüssel und nicht in Anwendungslogik. Ein zweiter
  Charakter derselben Klasse ist auf Datenbankebene unmöglich, unabhängig davon, welcher Block es
  später versucht.
- Aus derselben Eindeutigkeit folgt die Obergrenze von drei Charakteren je Account, ohne dass eine
  Zählung nötig wäre — es gibt genau drei Klassen.
- Jeder Charakter ist eine eigene Zeile mit eigener `revision`. Damit ist der Fortschritt der
  Charaktere eines Accounts strukturell voneinander unabhängig (FR-019): Ein Schreibvorgang auf
  einen Charakter kann einen anderen nicht berühren.
- Fachliche Spalten (Level, Erfahrung, Attribute) gehören **nicht** zu B03. Sie kommen von den
  Blöcken, die sie besitzen (B04, B06, B07), über deren eigene Migrationen im jeweils eigenen
  Versionsraum.

### `rpg.item_instance` — Gegenstand (aus B02, umgestellt durch `V3_2`)

Hing ursprünglich über `owner_player_id` am Account. Mit drei Charakteren je Account war damit
unklar, wem ein Gegenstand gehört. Die Spalte wird durch `character_id` ersetzt (ADR-011).

Der Zeitpunkt war die eigentliche Entscheidung: Heute ist es ein Spaltenwechsel auf einer außerhalb
der Tests leeren Tabelle, in B11 wäre es eine Migration echter Spieleritems.

### `rpg.player_state` — Account (aus B02, unverändert)

Bleibt die Account-Ebene: Identität, Fassung, Revision, Anonymisierung, letzter Kontakt. B03 fasst
diese Tabelle nicht an. Die Trennung ist der Befund aus der Spec-Phase: Der Fortschritt hängt am
Charakter, die Identität am Account.

**Auswirkung auf die Anonymisierung aus B02**: Deren Löschen des Account-Datensatzes räumt über den
kaskadierenden Fremdschlüssel auch die Charaktere mit. Das ist die gewünschte Wirkung und erfordert
keine Änderung an B02 — der dortige Integrationstest prüft ausdrücklich, dass nach einer
Anonymisierung keine Tabelle die ursprüngliche Kennung mehr enthält, und deckt damit die neue
Tabelle mit ab, sobald sie existiert.

## Beziehungen

```
player_state 1───0..3 character          (player_id, eindeutig je character_class)
character    1───*    item_instance       (character_id, ADR-011)

PlayerSession 1───0..1 PlayerCharacter    (activeCharacter, unveränderlich)
PlayerSession 1───*    PlayerCharacter    (availableCharacters)
SessionRegistry 1───*  PlayerSession      (höchstens eine je Spielerkennung)
```

## Abgrenzung

Der eigentliche Spielfortschritt (Level, Attribute, Fähigkeiten, Ausrüstung) gehört **nicht** zu
B03. B03 liefert Identität, Lebenszyklus und Schlüssel; die besitzenden Blöcke ergänzen ihre
Spalten. Diese Grenze ist dieselbe, die B02 für sich gezogen hat, und aus demselben Grund:
Andernfalls müsste B03 bei jeder Inhaltsänderung eines anderen Blocks angefasst werden.
