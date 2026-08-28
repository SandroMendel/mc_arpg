# Vertrag · Konfiguration von B08b

Alle Zahlen dieses Blocks stehen in `currency.yml` und werden beim Start gegen ein Schema geprüft.
Ein Fehler ist ein Startfehler mit klarer Meldung (Prinzip V, FR-058) — der Server startet nicht mit
einer Währung, deren Regeln niemand gelesen hat.

**Preise stehen nicht hier.** Stufenkosten in `classes.yml`, Rangkosten in `abilities.yml`,
Reparatur in B11 (ADR-027, FR-053).

---

## `currency.yml`

```yaml
# B08b - Waehrung & Konto.
# Prices are NOT here: tier costs live in classes.yml, rank costs in abilities.yml (ADR-027).

account:
  # What a freshly created character starts with (FR-011).
  # Zero is the shipped value: the first equipment tier costs nothing, so nobody is blocked.
  starting-balance: 0

drops:
  # What a creature drops when it dies (FR-022). Keys are mob type keys, same vocabulary
  # progression.yml uses for experience, upper case. B10 replaces this provider later (FR-032).
  default: 5
  by-type:
    ZOMBIE: 5
    SKELETON: 5
    CREEPER: 8
    ENDERMAN: 18

  # How long a pile lies before it is gone for good (FR-029). Nobody is credited for a pile
  # that expires.
  despawn-seconds: 120

  # Piles of the same character within this radius are merged into one instead of a second
  # entity being spawned (FR-028). Kept small: it is a lookup per kill.
  merge-radius: 3.0

  # Hard ceiling on piles lying in the world at once (FR-030). When it is reached, the OLDEST
  # pile in the world is credited to its owner and cleared away so the new one can appear
  # (FR-030a). No coin is ever lost this way. At 400 piles and a 120s despawn this only bites
  # above roughly 3.3 new piles per second - horde load, not everyday play.
  max-piles: 400

ledger:
  # How long gameplay bookings are kept (FR-038). Operator interventions are never pruned.
  retention-days: 30

history:
  # Entries per page in the history window (FR-046a). 45 = five rows; the bottom row carries
  # the paging buttons. Every page is one database query, so this is a ceiling, not a hint.
  page-size: 45
```

---

## Regeln des Schemas

| Schlüssel | Typ | Prüfung |
|---|---|---|
| `account.starting-balance` | ganzzahlig | `>= 0` |
| `drops.default` | ganzzahlig | `>= 0` |
| `drops.by-type.*` | ganzzahlig | `>= 0`; Schlüssel nicht leer |
| `drops.despawn-seconds` | ganzzahlig | `> 0`; siehe Hinweis unten |
| `drops.merge-radius` | Kommazahl | `> 0`, und `<= 16` — darüber wäre es keine Umkreisabfrage mehr |
| `drops.max-piles` | ganzzahlig | `> 0` |
| `ledger.retention-days` | ganzzahlig | `> 0` |
| `history.page-size` | ganzzahlig | `> 0`, und `<= 45` — mehr passt nicht in ein Fenster, das noch eine Navigationsreihe braucht |

**`drops.despawn-seconds` steht unter Vorbehalt** (research.md R1c): welche Fristen setzbar sind, hängt davon
ab, was das Paper-Artefakt anbietet. Lässt sich die Frist nicht je Entity setzen, ist der einzige
zulässige Wert der Vanilla-Wert, und das Schema lehnt jeden anderen mit einer Meldung ab, die das
erklärt. **Kein stilles Ignorieren** — eine Zahl, die nicht wirkt, ist schlimmer als keine.

**`default: 0` ist erlaubt.** Ein Server, der Coins ausschliesslich über NPC-Verkauf vergeben will,
ist eine zulässige Einstellung. `0` heisst dann „diese Kreatur lässt nichts fallen"; es ist der
einzige Fall, in dem ein Kill leer ausgeht, und er ist ausdrücklich gewählt statt vergessen (FR-023
spricht vom **fehlenden Eintrag**, nicht von der ausdrücklichen Null).

---

## Der `cost`-Block in `classes.yml` und `abilities.yml`

Ausgelegt von **diesem** Block, nicht von B07 oder B08 (R6).

```yaml
# classes.yml - unveraendert, wird ab jetzt nur endlich gelesen
tiers:
  - level: 10
    cost: { coins: 500 }
  - level: 1
    cost: {}          # kostenlos (FR-049)
```

```yaml
# abilities.yml - NEU: was ein Rang kostet
abilities:
  power-strike:
    max-rank: 5
    rank-cost: { coins: 250 }   # je Rangaufstieg; fehlt der Block, kostet er nichts (FR-054)
```

**Zulässig ist genau ein Schlüssel: `coins`.** Jeder andere — etwa `shards` — ist ein **Startfehler**
(FR-050). Die Meldung nennt Schlüssel, Klasse und Stufe beziehungsweise Fähigkeit. Das ist die
Umsetzung der Annahme „es gibt genau eine Währung": der `cost`-Block ist eine Map, damit er
erweiterbar ist, nicht damit er mehrdeutig ist.

---

## Message-Schlüssel

Alle Texte über `MessageKey`; `MessageKeyValidator` beweist beim Start, dass hinter jedem ein Text
steht (FR-057).

| Schlüssel | Wann |
|---|---|
| `currency.rejected.not-enough` | Eine Abbuchung scheiterte am Guthaben |
| `currency.rejected.invalid-amount` | Aufruffehler; sollte ein Spieler nie sehen |
| `currency.rejected.overflow` | Eine Gutschrift überschritte den Bereich |
| `currency.rejected.no-character` | Kein solcher Charakter |
| `currency.pile.picked-up` | Ein Haufen wurde aufgehoben |
| `currency.pile.cashed-in` | Die Deckelung griff; ein älterer Haufen wurde gutgeschrieben |
| `currency.starting-balance` | Das Startguthaben wurde bei der Charaktererstellung gutgeschrieben |
| `currency.balance.current` | Antwort auf die Standabfrage |
| `currency.admin.applied` | Ein Eingriff hat gewirkt |
| `currency.admin.denied` | Fehlende Berechtigung |
| `currency.admin.unknown-character` | Der Eingriff nannte keinen bekannten Charakter |
| `currency.menu.title-characters` | Titel der Charakterauswahl |
| `currency.menu.title-history` | Titel der Verlaufsseite |
| `currency.menu.character-entry` | Ein Charakter in der Auswahl: Name, Klasse, Stand |
| `currency.menu.history-entry` | Eine Buchung im Verlauf |
| `currency.menu.page-next` / `currency.menu.page-previous` | Die beiden Blätterknöpfe |
| `currency.menu.empty` | Dieser Charakter hat noch keine Buchung |

---

## Das vorläufige Kommando

**Provisorium** (FR-046, R7). B14 ersetzt es; die Schnittstelle darunter bleibt.

```
/coins                                      öffnet das Fenster für die eigenen Charaktere
/coins <player>                             öffnet es für einen fremden Spieler
/coins set    <player> <character> <amount>
/coins add    <player> <character> <amount>
/coins remove <player> <character> <amount>
```

Rechteknoten: `rpg.currency.balance` für das eigene Fenster — **standardmässig für alle** —,
`rpg.currency.admin` für ein fremdes Fenster und für die drei Eingriffe. `plugin.yml` bekommt dafür
seinen ersten `commands`-Block; im Projekt existiert bislang keiner.

**Gelesen wird geklickt, geschrieben getippt.** Ein Betrag lässt sich in einem Inventar nicht
sinnvoll eintippen; ihn über Knöpfe zusammenzuklicken wäre eine Zahleneingabe, die wie eine
Oberfläche aussieht. Lesen dagegen ist genau das, wofür ein Fenster taugt — und ein Verlauf über
Hunderte Zeilen ist im Chat unlesbar.

**Warum der Charakter gewählt werden muss.** Ein Spieler hat bis zu drei, und jeder hat seinen eigenen
Stand (ADR-011). Ein Eingriff ohne Charakterangabe wäre mehrdeutig, und der Betreiber merkte es erst,
wenn der falsche Charakter reicher ist. Im Fenster übernimmt die **Auswahl** diese Rolle.

---

## Das Fenster

Zwei Ebenen, aus **reinen Vanilla-Materialien** (ADR-005, kein Resource Pack), alle Texte über
Message-Schlüssel — nach dem Muster von `ClassSelectionMenu` aus B07.

1. **Charakterauswahl** — bis zu drei Einträge, je mit Name, Klasse und **eigenem Stand**. Es wird
   **nie zusammengezählt**: drei Charaktere sind drei Stände (FR-046b).
2. **Verlauf** des gewählten Charakters — eine Zeile je Buchung mit Zeitpunkt, Betrag, Richtung,
   Grund und, bei einem Eingriff, dem Verursacher.

Der Verlauf blättert: `history.page-size` Einträge je Seite, Vor- und Zurück-Knopf in der untersten
Reihe, an beiden Enden kein Knopf darüber hinaus. **Jede Seite ist eine Datenbankabfrage** — deshalb
greift hier dieselbe Begrenzung, die B14 später für alle abfragenden Kommandos vorsieht.

Aus dem Fenster lässt sich **nichts herausnehmen**. Die Einträge sind Anzeige, keine Gegenstände.
