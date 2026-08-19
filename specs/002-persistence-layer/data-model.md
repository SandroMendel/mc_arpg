# Phase 1 Data Model: B02 · Persistenz-Layer

Abgeleitet aus den Key Entities in `spec.md`. Konzeptionell — Feld- und Spaltennamen als Vertrag,
kein Implementierungscode.

Zwei Ebenen sind zu unterscheiden:

- **Laufzeitentitäten** leben nur im Speicher und tragen die Write-Behind-Mechanik.
- **Persistente Entitäten** entsprechen Tabellen und werden über Flyway-Migrationen angelegt.

## Laufzeitentitäten (nur im Speicher, `rpg-core`)

### Änderungsvormerkung (`DirtyMark`)

Der Vermerk, dass ein Aggregat seit dem letzten Schreibvorgang verändert wurde.

| Feld | Typ | Regeln |
|---|---|---|
| `aggregateType` | Enum | `PLAYER_STATE`, `STATISTICS`, `ITEM_INSTANCE`, `AUDIT_LOG` |
| `aggregateId` | String | Fachlicher Schlüssel des Aggregats (siehe je Aggregat unten) |
| `markedAt` | Zeitstempel | Zeitpunkt der ersten Vormerkung seit dem letzten Flush |

**Zentrale Regel — Koaleszieren**: Für dieselbe Kombination aus `aggregateType` und `aggregateId`
existiert **höchstens eine** Vormerkung. Eine erneute Änderung überschreibt keine bestehende
Vormerkung und legt keine zweite an. Daraus folgt die in `research.md` hergeleitete Eigenschaft,
dass der Puffer nicht mit der Dauer eines Ausfalls wächst, sondern nur mit der Zahl
unterschiedlicher berührter Aggregate.

`markedAt` behält bewusst den Zeitpunkt der **ersten** Vormerkung, nicht der letzten: Nur so lässt
sich messen, wie lange eine Änderung bereits ungeschrieben ist (Grundlage für FR-006 und die
Diagnose).

### Write-Behind-Puffer (`WriteBehindBuffer`)

Die Menge aller offenen Vormerkungen.

| Feld | Typ | Regeln |
|---|---|---|
| `marks` | Menge von `DirtyMark` | Eindeutig über (`aggregateType`, `aggregateId`) |
| `capacity` | Ganzzahl | Konfigurierbare Obergrenze, Standard 50 000 (FR-009a) |
| `warnThreshold` | Bruchteil | Standard 0,8 — ab hier wird gewarnt (FR-009c) |

**Zustandsregeln**:

- Erreicht `marks` die `capacity`, tritt FR-009b in Kraft: alle Spieler werden getrennt, neue
  Sitzungen abgelehnt. Vormerkungen werden dabei **nicht** verworfen.
- Ein erfolgreicher Flush entfernt genau die Vormerkungen, die er geschrieben hat — nicht die, die
  während des Schreibvorgangs neu hinzukamen (Edge Case „Aggregat während des Batch-Schreibens
  erneut verändert").

### Anonymes Ersatzkennzeichen (`AnonymizedId`)

Der Platzhalter, der nach einer Anonymisierung (FR-017a) an die Stelle der Spielerkennung tritt.

| Feld | Typ | Regeln |
|---|---|---|
| `value` | UUID | Neu erzeugt, ohne Bezug zur ursprünglichen Kennung |

**Validierungsregeln**:

- Wird **zufällig** erzeugt, niemals aus der ursprünglichen Kennung abgeleitet — kein Hash, keine
  Verschlüsselung. Jede ableitbare Beziehung würde FR-017b aushebeln, weil sich der Personenbezug
  über einen Abgleich wiederherstellen ließe.
- Die Zuordnung „alte Kennung → Ersatzkennzeichen" wird **nirgends** gespeichert. Genau das macht
  die Anonymisierung unumkehrbar.
- Erscheint in `player_statistic_daily.player_id` und `audit_log.actor` an der Stelle, an der
  zuvor die Spielerkennung stand, damit Fremdschlüssel und Aggregate gültig bleiben.

### Ausfallzustand (`OutageState`)

| Feld | Typ | Regeln |
|---|---|---|
| `reachable` | Wahrheitswert | Ob die Datenhaltung zuletzt erreichbar war |
| `unreachableSince` | Zeitstempel oder leer | Beginn des laufenden Ausfalls |
| `consecutiveFailures` | Ganzzahl | Steuert den Abstand der Wiederholversuche |

**Zustandsübergänge**: `reachable → unreachable` bei einem fehlgeschlagenen Schreibvorgang;
`unreachable → reachable` erst nach einem **erfolgreichen** Schreibvorgang, nicht nach einem
bloßen Verbindungsaufbau. Solange `unreachable`, werden Anmeldungen nach FR-005a abgelehnt,
bereits verbundene Spieler aber nicht angetastet (FR-005b).

## Persistente Entitäten (Tabellen)

Alle Tabellen liegen im Schema `rpg`. Bezeichner sind englisch (Constitution VIII).

### `player_state` — Spielerzustand

| Spalte | Typ | Regeln |
|---|---|---|
| `player_id` | UUID, Primärschlüssel | Minecraft-UUID des Spielers |
| `data_version` | Ganzzahl | Version des **Datensatzformats**, für Migrationspfade (FR-021) |
| `revision` | Bigint | Wird bei **jedem** Schreibvorgang erhöht; Grundlage des Versionsvergleichs (FR-019b) |
| `last_seen_at` | Zeitstempel mit Zone | Letzter bekannter Sitzungszeitpunkt |
| `anonymized` | Wahrheitswert | Ob dieser Datensatz nach FR-017a anonymisiert wurde |

**Validierungsregeln**:

- Ein Schreibvorgang mit einer `revision`, die nicht dem gespeicherten Stand entspricht, wird
  abgelehnt (`StaleVersionException`) und protokolliert — FR-019b.
- Weitere fachliche Spalten (Level, Erfahrung, Klasse …) gehören **nicht** zu B02. Sie werden von
  den Blöcken ergänzt, die das jeweilige Aggregat besitzen (B03, B06, B07), jeweils über eine
  eigene Migration. B02 legt nur den Rahmen und die Mechanik fest.
- `anonymized` ist der Nachweis, dass FR-017b eingehalten wurde: Ein anonymisierter Datensatz
  enthält keine Spielerkennung mehr und existiert nur noch, falls Fremdschlüssel ihn brauchen.

### `player_statistic_daily` — Tagesstatistik

Verdichtung je Spieler, Kennzahl und Kalendertag (FR-016a).

| Spalte | Typ | Regeln |
|---|---|---|
| `player_id` | UUID | Verweist auf `player_state.player_id`; nach Anonymisierung das Ersatzkennzeichen |
| `metric` | Text | Bezeichner der Kennzahl, z. B. `mob_kills`, `damage_dealt` |
| `day` | Datum | Kalendertag in UTC |
| `value` | Bigint | Aufsummierter Wert des Tages |

**Schlüssel**: Zusammengesetzter Primärschlüssel (`player_id`, `metric`, `day`). Genau dieser
Schlüssel macht `INSERT ... ON CONFLICT ... DO UPDATE SET value = value + excluded.value` möglich
und damit FR-007 (kein vorheriger Leseschritt) erfüllbar.

**Validierungsregeln**:

- Unbegrenzte Aufbewahrung (FR-017) — kein Löschauftrag, keine Archivierung.
- Allzeit- und Zeitraumsummen entstehen durch Summieren über `day` (FR-016b); Einzelereignisse
  werden nie gespeichert.
- Der Tageswechsel (FR-016c) ergibt sich aus dem Schlüssel von selbst: Ein Ereignis nach
  Mitternacht trifft auf einen anderen `day` und legt dort einen neuen Datensatz an. Es gibt
  keinen expliziten Umschaltvorgang, der etwas verlieren oder doppeln könnte.

### `item_instance` — Item-Instanz

| Spalte | Typ | Regeln |
|---|---|---|
| `instance_id` | UUID, Primärschlüssel | Identität dieses konkreten Exemplars |
| `owner_player_id` | UUID | Besitzer; verweist auf `player_state.player_id` |
| `template_id` | Text | Vorlagenkennung aus der Content-Konfiguration (B16) |
| `rolled_values` | JSONB | Die gewürfelten Werte dieses Exemplars |
| `revision` | Bigint | Wie bei `player_state` |

**Validierungsregeln**:

- Gespeichert werden **ausschließlich** Vorlagenkennung und gewürfelte Werte, **niemals**
  berechnete Endwerte oder gerenderte Beschreibungstexte (ADR-004, Constitution IV). Nur so bleibt
  ein späteres Balancing-Rework möglich, ohne bestehende Spieleritems anzufassen.
- `rolled_values` ist der einzige zulässige `JSONB`-Einsatz in B02: Der Inhalt ist je Vorlage
  verschieden und wird nie gefiltert oder sortiert, sondern immer als Ganzes gelesen.

### `audit_log` — Prüfprotokoll

| Spalte | Typ | Regeln |
|---|---|---|
| `entry_id` | Bigint, fortlaufend, Primärschlüssel | |
| `occurred_at` | Zeitstempel mit Zone | Zeitpunkt des Eingriffs |
| `actor` | Text | Handelnde Person; nach Anonymisierung das Ersatzkennzeichen |
| `action` | Text | Art des Eingriffs, z. B. `item_granted`, `player_banned` |
| `target_player_id` | UUID oder leer | Betroffener Spieler, falls zutreffend |
| `details` | JSONB | Ergänzende Angaben zum Eingriff |

**Validierungsregeln**:

- Nur Anhängen, kein Ändern und kein Löschen bestehender Einträge — ein änderbares Prüfprotokoll
  wäre wertlos.
- Anonymisierungen nach FR-017a werden hier selbst festgehalten (FR-017c), jedoch ohne die
  anonymisierte Kennung — sonst würde das Protokoll genau den Personenbezug bewahren, den die
  Anonymisierung entfernen soll.

### `schema_version` — Migrationsverwaltung

Wird von Flyway selbst angelegt und gepflegt (Standardname `flyway_schema_history`). Kein eigener
Entwurf nötig; hier nur zur Vollständigkeit genannt, weil FR-012/FR-013 sich darauf beziehen.

## Beziehungen

```
player_state 1───* player_statistic_daily   (player_id)
player_state 1───* item_instance            (owner_player_id)
player_state 0───* audit_log                (target_player_id, optional)

WriteBehindBuffer 1───* DirtyMark           (eindeutig je aggregateType + aggregateId)
DirtyMark         *───1 Aggregat            (aggregateType + aggregateId verweist auf eine Zeile)
```

## Abgrenzung

Fachliche Spalten der einzelnen Aggregate (Level, Klasse, Zonenfortschritt, konkrete Kennzahlen)
gehören **nicht** zu B02. B02 liefert Tabellenrahmen, Schlüssel, Versionierung und die
Schreibmechanik; die Blöcke, die ein Aggregat besitzen, ergänzen ihre Spalten über eigene
Migrationen. Diese Grenze ist bewusst: Andernfalls müsste B02 bei jeder Inhaltsänderung eines
anderen Blocks angefasst werden.
