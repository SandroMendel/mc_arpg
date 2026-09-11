# Phase 1 · Datenmodell: B14

**B14 persistiert nichts.** Kein Schema, keine Migration. Was hier steht, ist Laufzeitstruktur —
mit **einer** Ausnahme: einer Änderung an einem bestehenden Datensatz aus B10.

---

## Neu, nur im Speicher

### `RpgCommand` — ein Kommando als Baum

| Feld | Bedeutung |
|---|---|
| `name` | der Name, unter dem es aufgerufen wird (`char`, `rpg`) |
| `description` | für die Hilfe; ein Nachrichtenschlüssel, kein Text |
| `permission` | der Rechteknoten, ohne den es nicht sichtbar und nicht ausführbar ist |
| `children` | Unterkommandos, selbst wieder `RpgCommand` |
| `arguments` | geordnete `Argument`-Liste des Blattes |
| `requiresPlayer` | ob ein Spielerbezug nötig ist (steuert die Konsolenmeldung, FR-008) |
| `rateLimit` | optionale Sperrzeit; gesetzt bei allem, was die Datenbank befragt |

**Regel:** Ein `RpgCommand` mit `children` hat keine eigene Ausführung — es ist eine Verzweigung.
Ein Blatt hat `arguments` und eine Ausführung. Damit gibt es keinen Zustand „halb ausgeführt".

### `Argument` — die eine Deklaration

| Feld | Bedeutung |
|---|---|
| `name` | erscheint in Fehlermeldung und Hilfe |
| `type` | siehe Tabelle unten |
| `required` | ob es weggelassen werden darf |
| `suggestions` | woher die Vorschläge kommen — **dieselbe Quelle, die auch prüft** |

Das ist der Kern von FR-002: Vorschlag und Prüfung stammen aus **einem** Feld. Eine Vorschlagsliste,
die von der Prüfung abweicht, ist damit strukturell ausgeschlossen und nicht bloß unwahrscheinlich.

#### Argumenttypen

| Typ | Wertebereich | Vorschläge |
|---|---|---|
| `PLAYER` | ein Spieler, **online oder offline** | Namen online, ergänzt aus dem Namens-Cache |
| `ITEM_TEMPLATE` | Schlüssel aus `Items` | alle bekannten Vorlagen |
| `MOB_KIND` | Schlüssel aus `MobKinds` | alle bekannten Arten |
| `CHARACTER_CLASS` | Wert aus `CharacterClass` | die Klassen |
| `PERIOD` | Zeitraum, wie `/stats` und `/top` ihn heute kennen | die gültigen Zeiträume |
| `AMOUNT` | ganze Zahl mit Ober- und Untergrenze | keine |
| `LEVEL` | 1..60 (B06s Obergrenze) | keine |
| `DURATION` | Zeitspanne für die Audit-Abfrage | gängige Spannen |

**`PLAYER` ist der wichtigste Typ.** Er kapselt das Muster, das B12 bereits gefunden hat:
`getOfflinePlayer(name)` gefolgt von `hasPlayedBefore() || isOnline()`, das einen echten Namen von
einem Tippfehler trennt — ein Tippfehler ginge sonst als leeres Profil durch. Heute steht dieses
Muster einmal in `RpgPlugin`, und `CoinsCommand` benutzt daneben `getPlayerExact` (nur online).
Als Argumenttyp steht es **einmal**.

### `PermissionNode`

| Feld | Bedeutung |
|---|---|
| `key` | `rpg.<bereich>.<sache>` |
| `description` | Klartext, erscheint in `plugin.yml` |
| `default` | `true` (jeder) oder `op` |

**Zusage (FR-010):** Jeder Knoten, den Code benutzt, steht in `plugin.yml`. Ein Startprüfer stellt
das sicher — ein Recht, das nur im Quelltext existiert, bricht den Start, statt still zu wirken.

### `RateLimit`

| Feld | Bedeutung |
|---|---|
| `key` | Absender + Kommando |
| `lastUsed` | Zeitstempel der letzten Ausführung |
| `window` | Sperrzeit aus der Konfiguration |

**Lazy, wie Prinzip II es für Cooldowns vorschreibt**: Beim Aufruf wird gerechnet, es läuft **kein**
wiederkehrender Task. Für die Konsole gilt die Grenze nicht. Einträge verfallen mit der Sitzung.

---

## Vorhanden, unverändert übernommen

### `AuditEntry` (B02, FR-018)

`(occurredAt, actor, action, targetPlayerId, details)` — B14 fügt **kein Feld** hinzu. Alles
Zusätzliche geht in `details`.

Die Aktionsnamen, die B14 vergibt:

| Aktion | ausgelöst durch |
|---|---|
| `item_granted` | `/rpg item give` — der Name, den das Javadoc von `AuditEntry` schon als Beispiel führt |
| `mob_spawned` | `/rpg mob spawn` |
| `config_reloaded` | `/rpg reload` |
| `progress_set` | `/rpg set level` / `/rpg set xp` — B06 schreibt ihn bereits |
| `class_changed` | `/rpg set class` |

**Der Handelnde von der Konsole** ist die feste Null-UUID, die `XpCommand` schon benutzt —
ausdrücklich, damit das Log greppbar bleibt. B14 hebt sie ins Gerüst.

**Rein lesende Zugriffe erzeugen keinen Eintrag** (FR-029). Protokolliert wird, was verändert,
nicht was angesehen wird.

---

## Die eine Änderung an bestehenden Daten

### `HordeRegistry.Entry` bekommt eine Herkunft

**Heute:** `(entityId, kindKey, zoneKey, chunkKey, spawnedAt)`

**Künftig:** dazu `Origin origin` mit `BUDGET` (der Spawnplaner) oder `ADMIN` (von Hand gesetzt).

Folgen für B10s Zählungen:

| Methode | heute | künftig |
|---|---|---|
| `total()` | alle | nur `BUDGET` |
| `countIn(zoneKey)` | alle der Zone | nur `BUDGET` der Zone |
| `countInChunk(chunkKey)` | alle | **alle** — die Chunkdichte ist eine Last­grenze, keine Budgetfrage |
| `countAdmin()` | — | neu: die handgesetzten, gegen die eigene Obergrenze |

**Warum `countInChunk` unverändert alle zählt:** Die Chunkgrenze schützt vor Ballungen an einem
Ort — dafür ist es gleichgültig, wer die Kreatur dort hingesetzt hat. Das Budget dagegen ist eine
Aussage über die *gespawnte* Population, und die wird durch Handgesetztes nicht größer.

**Keine Persistenz, ausdrücklich** (FR-019b): Die Registry überlebt keinen Neustart, und ADR-050
entfernt beim Chunk-Laden jede getaggte Kreatur, die nicht darin steht. Nach einem Neustart lebt
also keine handgesetzte mehr und beide Zählungen stehen wieder auf null. Eine Kennzeichnung im
`PersistentDataContainer` wäre Aufwand für einen Zustand, den es nie gibt.

**ADR-pflichtig** — es ist eine Änderung an B10s öffentlicher Bauart, siehe *Complexity Tracking*
in [plan.md](./plan.md).

---

## Was ausdrücklich **kein** Modell bekommt

**Attributwerte durch einen Betreiber** (FR-024a). `StatEngine` hat keinen Setzer; Werte sind aus
`ModifierSet`s je Quelle abgeleitet, und `SourceKind` ist geschlossen — seine
Deklarationsreihenfolge *ist* die Summationsreihenfolge. Ein Admin-Wert bräuchte einen siebten
Eintrag darin und wäre nach dem nächsten Anmelden trotzdem verschwunden. Hier entsteht keine
Struktur, sondern ein ADR, das sagt warum.
