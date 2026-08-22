# Phase 1 · Datenmodell — B08b · Währung & Konto

Zwei Tabellen, zwei Aggregattypen, ein Laufzeitobjekt ohne Persistenz.

---

## 1 · `rpg.character_balance` — der Kontostand

Migration `V8_2__character_balance.sql`. Eine Zeile je Charakter, veränderlich.

| Spalte | Typ | Regeln |
|---|---|---|
| `character_id` | `uuid` | Primärschlüssel, Fremdschlüssel auf `rpg.character`, `ON DELETE CASCADE` |
| `balance` | `bigint` | `NOT NULL`, `CHECK (balance >= 0)` |
| `updated_at` | `timestamptz` | `NOT NULL` |
| `revision` | `bigint` | `NOT NULL`, wie bei den übrigen Charakter-Aggregaten |

**Warum eine eigene Tabelle und nicht eine Spalte auf `rpg.character`.** Dieselbe Begründung, die
`CHARACTER_STATS` in `AggregateType` bereits trägt: eine gemeinsame Zeile hiesse ein gemeinsamer
Writer und ein gemeinsamer Revisionszähler zwischen B03 und B08b — jede Buchung wäre dann eine
Änderung an B03s Schreibpfad.

**Warum `CHECK (balance >= 0)` und nicht nur die Prüfung im Code.** FR-003 ist die Zusage, an der der
ganze Block hängt. Eine Prüfung allein im Speicher fällt aus, sobald irgendein späterer Weg an ihm
vorbeischreibt; die Datenbank kann das nicht. Der `CHECK` ist die Zusage, die auch dann noch gilt,
wenn jemand sie vergisst.

**Warum `bigint`.** FR-010 verlangt, dass eine überlaufende Gutschrift abgelehnt statt umgelaufen
wird. `bigint` legt die Grenze so hoch, dass sie im Spiel nie erreicht wird, und die Ablehnung
schützt den Rand.

**Kein Eintrag heisst null** (FR-011b) — **nicht** „der gerade konfigurierte Startwert". Sonst hätte
eine spätere Änderung der Zahl rückwirkend die Stände aller noch unbebuchten Charaktere geändert,
ohne Buchung und ohne Spur.

Das Startguthaben ist stattdessen eine **Gutschrift bei der Charaktererstellung** mit dem Grund
`STARTING_BALANCE` (FR-011a). Ist es null, entsteht keine Buchung und keine Zeile — und dann kann
auch nichts driften, weil null gleich null ist. Der Auslieferungszustand kostet damit weiterhin keine
einzige Schreiboperation je erstelltem Charakter.

**Aggregattyp:** `CHARACTER_BALANCE`, in `FlushCycle.WRITE_ORDER` **nach** `CHARACTER`.
Registrierung 1 und 2 von 3; die dritte ist das Repository in `CurrencyModule`.

**Laden:** über `SessionBundle`, als zehnte Komponente (`List<CharacterBalance> balances`) — keine
zweite Datenbankrunde im Anmeldepfad (ADR-015 Punkt 3).

---

## 2 · `rpg.coin_ledger` — der Verlauf

Migration `V8_3__coin_ledger.sql`. Viele Zeilen je Charakter, **nur anfügend**.

| Spalte | Typ | Regeln |
|---|---|---|
| `id` | `bigserial` | Primärschlüssel |
| `character_id` | `uuid` | `NOT NULL`, Fremdschlüssel auf `rpg.character`, `ON DELETE CASCADE` |
| `occurred_at` | `timestamptz` | `NOT NULL` |
| `amount` | `bigint` | `NOT NULL`, `CHECK (amount > 0)` — die Richtung steht in `direction` |
| `direction` | `text` | `NOT NULL`, `CREDIT` oder `DEBIT` |
| `reason` | `text` | `NOT NULL`, ein Wert aus `BookingReason` |
| `balance_before` | `bigint` | `NOT NULL`, `CHECK (>= 0)` |
| `balance_after` | `bigint` | `NOT NULL`, `CHECK (>= 0)` |
| `actor` | `text` | `NULL`, ausser bei einem Eingriff des Betreibers |

Index auf `(character_id, occurred_at DESC)` — die einzige Abfrage, die es gibt: „was geschah mit
diesem Konto, neueste zuerst".

**Warum Stand davor und danach gespeichert werden.** Sie sind ableitbar, solange die Kette lückenlos
ist — und genau das ist nach einem Absturz und dem Verlust eines Autosave-Intervalls nicht mehr
garantiert. Als Tatsache gespeichert bleibt jeder Eintrag für sich lesbar, auch wenn ein Nachbar
fehlt. Es sind keine berechneten Endwerte im Sinne von Prinzip IV, sondern der protokollierte Zustand
zum Zeitpunkt der Buchung.

**Warum die Richtung eine Spalte ist und kein Vorzeichen.** FR-009 verbietet die Vorzeichenführung
über den Betrag. Was für die Schnittstelle gilt, gilt für die Ablage: ein negativer Betrag in dieser
Tabelle wäre eine zweite Ausdrucksform desselben Sachverhalts.

**Aggregattyp:** `COIN_LEDGER`, nach `CHARACTER`. Schreibmuster wie `AUDIT_LOG` — eine synthetische
Warteschlangen-Kennung, die markiert wird, ein Writer, der beim Flush leert (R5).

**Aufbewahrung:** konfigurierbar je Buchungsart (FR-038). Einträge mit gesetztem `actor` — Eingriffe
des Betreibers — werden **nie** aufgeräumt. Das Aufräumen läuft im Autosave-Zyklus mit, nicht als
eigene Aufgabe.

---

## 3 · Werte in `rpg-core`

### `BookingReason` — der abgeschlossene Vorrat

| Wert | Wann | Richtung |
|---|---|---|
| `STARTING_BALANCE` | Charaktererstellung mit konfiguriertem Startguthaben (FR-011a) | Gutschrift |
| `PILE_PICKED_UP` | ein Coin-Haufen wurde aufgehoben | Gutschrift |
| `PILE_CASHED_IN` | die Deckelung griff; der älteste Haufen wurde abgeräumt (FR-030a) | Gutschrift |
| `EQUIPMENT_TIER` | eine Ausrüstungsstufe wurde bezahlt | Abbuchung |
| `ABILITY_RANK` | ein Fähigkeitsrang wurde bezahlt | Abbuchung |
| `VENDOR_SALE` | Verkauf an einen NPC (B11 füllt) | Gutschrift |
| `VENDOR_PURCHASE` | Kauf bei einem NPC (B11 füllt) | Abbuchung |
| `REPAIR` | Reparatur (B11 füllt) | Abbuchung |
| `ADMIN_SET` / `ADMIN_ADD` / `ADMIN_REMOVE` | Eingriff des Betreibers | beides |

**Abgeschlossen, nicht offen** (FR-005). Ein freier Text hätte Tippfehler zu Buchungsarten gemacht
und die Frage „wo kommen diese Coins her" unbeantwortbar. Die drei Werte für B11 stehen jetzt schon
da: sie kosten nichts und ersparen B11 eine Änderung an einem fremden Enum.

### `BookingResult`

`OK`, `NOT_ENOUGH`, `INVALID_AMOUNT`, `WOULD_OVERFLOW`, `NO_SUCH_CHARACTER` — jeder ausser `OK` mit
einem `MessageKey`, nach dem Muster von `RankResult` und `AbilityResult`.

### `CharacterBalance`

Das Aggregat: Charakterkennung, Betrag, Revision. Unveränderlich; eine Buchung erzeugt einen neuen
Wert.

### `LedgerEntry`

Ein Verlaufseintrag als Record, spiegelt die Tabelle: Zeitpunkt, Charakter, Betrag, Richtung, Grund,
Stand davor und danach, optionaler Verursacher.

### `CostSpec`

Die ausgelegte Form des undurchsichtigen `cost`-Blocks aus B07 — heute genau ein Feld, `coins`. Eine
leere Map ergibt „kostenlos" (FR-049), ein unbekannter Schlüssel einen Startfehler (FR-050).

---

## 4 · `CoinPile` — das Laufzeitobjekt ohne Persistenz

Ein Haufen wird **nicht gespeichert** (FR-033). Er lebt als Vanilla-`Item`-Entity, und alles, was der
Block über ihn weiss, steht im Datencontainer des Stapels:

| Eintrag | Zweck |
|---|---|
| `amount` | der Betrag (R1b) |
| `character` | der berechtigte **Charakter**, nicht der Spieler (R3) |
| `pile` | eine eindeutige Kennung je Haufen — verhindert das Vanilla-Zusammenlegen (R2) |
| `created` | Entstehungszeitpunkt — die Reihenfolge, in der bei erreichter Deckelung abgeräumt wird (FR-030a) |

Dazu die Vanilla-Eigenschaft `owner` mit der **Spieler**kennung, als grober und kostenloser Vorfilter.

**Sichtbar ist ein Haufen nur für seinen Berechtigten** (FR-027a). Wer nicht berechtigt ist, sieht ihn
überhaupt nicht — ein sichtbarer, aber unaufhebbarer Haufen sähe für den Spieler aus wie ein Fehler.
Die Sperre gegen das Aufheben bleibt trotzdem bestehen: Unsichtbarkeit ist Darstellung, und
Darstellung ist nie die Autorität (Prinzip VI).

**Ein Serverneustart vernichtet liegende Haufen.** Das ist beabsichtigt und deckungsgleich mit FR-029:
ein Haufen ist eine Gelegenheit mit Frist, kein Besitz. Ihn zu persistieren hiesse, einen dritten
Aggregattyp für etwas anzulegen, das binnen Minuten ohnehin verfällt.

---

## 5 · Zustandsübergänge

### Buchung

```
        credit(betrag, grund)                debit(betrag, grund)
              │                                     │
              ▼                                     ▼
     ┌─────────────────┐                   ┌─────────────────┐
     │ Betrag > 0 ?    │── nein ──▶ INVALID_AMOUNT ◀── nein ──│ Betrag > 0 ?    │
     └────────┬────────┘                   └────────┬────────┘
              │ ja                                  │ ja
              ▼                                     ▼
     ┌─────────────────┐                   ┌─────────────────┐
     │ Überlauf ?      │── ja ──▶ WOULD_OVERFLOW     │ Stand >= Betrag?│── nein ──▶ NOT_ENOUGH
     └────────┬────────┘                   └────────┬────────┘
              │ nein                                │ ja
              └────────────┬───────────────────────┘
                           ▼
            Stand ändern + Verlaufseintrag + Dirty-Mark
                           ▼
                          OK
```

Der gesamte Ablauf läuft **unter einer Sperre je Konto** (FR-006). Prüfung und Änderung sind ein
Schritt; zwischen ihnen kann nichts geschehen. Der Verlaufseintrag entsteht **innerhalb** der Sperre,
damit `balance_before` und `balance_after` zusammenpassen — er wird nur eingereiht, nicht geschrieben,
also kostet das nichts.

### Haufen

```
   Kill ──▶ Anteile (ShareCalculator) ──▶ je Berechtigtem ein Betrag
                                                  │
                          ┌───────────────────────┴──────────────────────┐
                          ▼                                              ▼
            Haufen desselben Charakters im Umkreis?            kein Haufen in der Nähe
                          │ ja                                          │
                          ▼                                    Deckelung erreicht?
              Betrag im Container erhöhen                   ├── ja ──▶ ältesten Haufen
                          │                                 │          weltweit gutschreiben
                          │                                 │          (PILE_CASHED_IN),
                          │                                 │          abräumen
                          │                                 ▼
                          │                          neues Item-Entity, owner +
                          │                          Container setzen, nur für den
                          │                          Berechtigten sichtbar
                          └───────────────┬─────────────────────────────┘
                                          ▼
                                    liegt in der Welt
                          ┌───────────────┴───────────────┐
                          ▼                               ▼
              Aufhebeversuch                        Frist läuft ab
                          │                               │
              Charakter passt?                            ▼
                 ├── nein ──▶ abbrechen,            Vanilla entfernt,
                 │            liegen lassen         niemandem gutgeschrieben
                 └── ja ──▶ Ereignis abbrechen,
                            gutschreiben (PILE_PICKED_UP),
                            Entity entfernen
```

**Warum das Aufhebe-Ereignis abgebrochen wird.** Ein Coin-Haufen darf nie in einem Inventar landen
(FR-033). Abbrechen, buchen, Entity entfernen — in dieser Reihenfolge. Schlägt die Buchung fehl,
bleibt der Haufen liegen; das kann nur bei einem Überlauf passieren, und dann ist Liegenlassen die
richtige Antwort.

**Warum Verfallen und Abräumen verschieden enden.** Beides entfernt ein Entity, aber nur eines kostet
den Spieler etwas. Läuft die **Frist** ab, hatte er Zeit und hat sie verstreichen lassen — nichts
wird gutgeschrieben (FR-029). Räumt der **Server** ab, weil das Objektbudget voll ist, kann er nichts
dafür — der Betrag wird gutgeschrieben (FR-030a bis FR-030d). Eigene Versäumnisse kosten, Serverlast
nicht. Das sieht auf den ersten Blick widersprüchlich aus und ist es nicht; wer es später
„vereinheitlicht", nimmt einer der beiden Seiten ihre Begründung.

---

## 6 · Beziehungen

```
rpg.character (B03)
   │ 1
   ├──── 0..1 ── rpg.character_balance      (dieser Block)
   ├──── 0..n ── rpg.coin_ledger            (dieser Block)
   ├──── 0..1 ── rpg.character_progress     (B06)
   ├──── 0..1 ── rpg.character_class_progress (B07)
   └──── 0..n ── rpg.character_abilities    (B08)

CoinPile ── berechtigt für ──▶ character_id     (nur Laufzeit, nie gespeichert)
```

Beide neuen Tabellen hängen mit `ON DELETE CASCADE` am Charakter. Damit bleibt der Löschpfad der
Anonymisierung aus B02/FR-017a unverändert gültig: die Kaskade
`player_state → character → {balance, ledger}` räumt beide mit ab, ohne dass der Pfad davon wissen
muss — dieselbe Nebenwirkung, die ADR-011 für `item_instance` bereits festgehalten hat.
