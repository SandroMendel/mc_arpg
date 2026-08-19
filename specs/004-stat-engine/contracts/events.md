# Vertrag: Ereignisse von B04

**Feature**: `specs/004-stat-engine` | **Datum**: 2026-08-20

B04 ist der erste Block, der B01s `EventBus` produktiv nutzt. Beide Ereignisse sind unveränderliche
Records und werden über `EventBus.publish` veröffentlicht; Abonnenten melden sich mit
`EventBus.subscribe(Typ, Handler)` an.

B01s `DefaultEventBus` fängt Ausnahmen eines Abonnenten ab und protokolliert sie, ohne die übrigen
Abonnenten oder den Veröffentlichenden mitzureißen (`EventBusFaultIsolationTest`). Ein fehlerhaftes
HUD kann damit die Berechnung nicht beschädigen (Prinzip VI).

---

## 1 · `StatsRecalculatedEvent` (FR-023)

```java
public record StatsRecalculatedEvent(
        UUID holderId,
        UUID characterId,        // null für Träger ohne Spielerbezug
        StatSnapshot previous,   // null bei der allerersten Berechnung
        StatSnapshot current) {}
```

**Wann**: nach jeder abgeschlossenen Neuberechnung, unabhängig davon, ob sie aus der Bündelung oder
aus `recalculateNow` stammt.

**Wofür**: B13 aktualisiert das HUD, ohne die Engine in einer Schleife abzufragen. B05 kann
zwischengespeicherte Werte verwerfen. B12 kann Höchstwerte mitschreiben.

**Zusicherungen**

- `current` ist niemals `null`; `previous` ist genau einmal je Träger `null`.
- Beide Schnappschüsse bleiben nach der Veröffentlichung gültig und lesbar, auch wenn der Träger
  danach entfernt wird (FR-021).
- Ein Abonnent, der Werte vergleichen will, braucht `current.get(…)` gegen `previous.get(…)` — es
  gibt bewusst keine vorberechnete Änderungsliste. Sie wäre in dem einen Fall Arbeit, in dem
  niemand sie braucht, nämlich beim Laden.
- Das Ereignis wird veröffentlicht, **nachdem** die Vanilla-Spiegelung angestoßen wurde; ein
  Abonnent sieht also nie einen Zustand, in dem Wert und Anzeige auseinanderlaufen.

---

## 2 · `ResourceChangedEvent` (FR-029)

```java
public record ResourceChangedEvent(
        UUID holderId,
        UUID characterId,        // null für Träger ohne Spielerbezug
        ResourceKind kind,       // HEALTH | MANA
        double previous,
        double current,
        double max,
        ChangeCause cause) {}    // DELTA | CLAMPED_BY_MAX | INITIALISED
```

**Wann**: bei jeder tatsächlichen Änderung eines Ressourcenstands. Eine Änderung um null — etwa ein
Verbrauch bei bereits leerem Mana — wird **nicht** veröffentlicht.

**Die drei Ursachen**

| Ursache | Bedeutung |
|---|---|
| `DELTA` | jemand hat `changeHealth`/`changeMana` gerufen |
| `CLAMPED_BY_MAX` | das Maximum ist gesunken und hat den Stand mitgezogen (FR-026) |
| `INITIALISED` | der Träger wurde angelegt oder geladen (FR-027) |

Die Unterscheidung ist für B13 wichtig: ein Klemmen durch einen Ausrüstungswechsel ist kein
Schaden und darf keine Trefferanimation auslösen.

**Zusicherungen**

- `current == 0` bei `kind == HEALTH` bedeutet, dass der Träger keine Gesundheit mehr hat. **B04
  löst daraus keinen Tod aus** — das ist B05s Entscheidung (Blockgrenze, FR-042).
- `max` ist der Wert aus dem zum Zeitpunkt der Änderung gültigen Schnappschuss.
- Das Ereignis wird veröffentlicht, nachdem der Schreibvermerk für B02s Write-Behind gesetzt wurde.
  Ein Abonnent kann sich also darauf verlassen, dass der Stand auf dem Weg in die Datenbank ist.

---

## Was B04 ausdrücklich **nicht** veröffentlicht

| Nicht veröffentlicht | Warum |
|---|---|
| Ereignis je hinzugefügtem oder entferntem Beitrag | Es gäbe bei einem Ausrüstungswechsel sechs davon, obwohl nur eine Neuberechnung folgt. Wer die Herkunft braucht, fragt `contributions(…)` ab. |
| Ereignis je Tick oder je Zeitscheibe | Prinzip II — es gibt keine wiederkehrende Arbeit. |
| Todes- oder Wiederbelebungsereignis | Gehört B05. |
| Level- oder Klassenereignis | Gehört B06 und B07. |
