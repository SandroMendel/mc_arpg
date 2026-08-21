# Vertrag: veröffentlichte Ereignisse

**Block**: B06 | **Paket**: `rpg.core.progression` | **Stand**: 2026-08-20

Drei Ereignisse auf dem Ereignisbus aus B01. Alle drei sind reine Meldungen: sie tragen Zahlen und
Kennungen, niemals ein Anzeigeobjekt (FR-023b).

---

## `LevelUpEvent`

```java
record LevelUpEvent(
        UUID characterId,
        UUID playerId,
        int previousLevel,
        int newLevel,
        boolean byAdmin) implements Event {}
```

Genau **einmal** je Aufstieg (FR-023). Bei einem Aufstieg über drei Level auf einmal: ein Ereignis
mit `previousLevel = 12`, `newLevel = 15` — nicht drei Ereignisse (SC-009).

`byAdmin` unterscheidet den Verwaltungseingriff vom natürlichen Aufstieg (FR-024c). B13 wird ein
gesetztes Level nicht feiern wollen, B12 es aber trotzdem zählen. Ohne das Feld müsste jeder
Empfänger raten.

**Empfänger**: B08 (Fähigkeiten freischalten), B12 (zählen), B13 (Aufstieg anzeigen).

**Nicht veröffentlicht**, wenn das Level unverändert bleibt — insbesondere nicht auf Maximallevel
(FR-050).

---

## `ProgressChangedEvent`

```java
record ProgressChangedEvent(
        UUID characterId,
        UUID playerId,
        long gained,
        int level,
        long xpInLevel,
        long xpForNextLevel) implements Event {}
```

Der innerhalb eines Fensters zusammengefasste Zuwachs (FR-023a). `gained` ist die Summe aller
Gewinne im Fenster, nicht der letzte.

Das Ereignis trägt Level und beide XP-Werte mit, damit B13 den Balken zeichnen kann, **ohne**
nachzufragen und ohne zu rechnen. Ein Ereignis, das nur `gained` trägt, hätte B13 zu einer
Rückfrage je Meldung gezwungen — bei 1000 Ereignissen je Sekunde genau das, was die Bündelung
vermeiden soll.

**Auf Maximallevel**: `xpForNextLevel = 0`. Kein Ereignis, wenn XP dort verfällt (FR-050).

---

## Reihenfolgezusage

Tritt ein Aufstieg ein, während ein Bündel offen ist, kommt **zuerst** das
`ProgressChangedEvent` des alten Levels, **dann** das `LevelUpEvent` (FR-023c).

```
XP-Gewinn 1  ──┐
XP-Gewinn 2  ──┼─ Fenster offen
XP-Gewinn 3  ──┘  ← überschreitet die Schwelle
                  1. ProgressChangedEvent(level=12, ...)
                  2. LevelUpEvent(12 -> 13)
                  3. Fenster zurückgesetzt
```

Ohne diese Zusage könnte ein älteres Bündel nach dem Aufstieg eintreffen und den Fortschrittsbalken
rückwärts springen lassen — ein Fehler, der erst bei einem Spieler auffällt und dann nicht
reproduzierbar ist. Nachgewiesen durch SC-020.

**Beim Sitzungsende** wird ein offenes Bündel **verworfen**, nicht ausgeliefert: es ist reine
Anzeige, und der Empfänger ist bereits weg. Die XP selbst ist angerechnet und wird geschrieben.

---

## `PartyChangedEvent`

```java
record PartyChangedEvent(
        UUID partyId,
        PartyChange change,
        UUID affectedPlayer,
        UUID leader,
        List<UUID> members) implements Event {}
```

`PartyChange`: `CREATED`, `JOINED`, `LEFT`, `REMOVED`, `LEADER_CHANGED`, `DISBANDED`.

Bei `DISBANDED` ist `members` leer und `leader` der letzte Anführer — B13 braucht ihn, um die
Anzeige der richtigen Party zu schliessen.

Eine Rollenübergabe erzeugt **zwei** Ereignisse: erst `LEFT` oder `REMOVED` für das ausgeschiedene
Mitglied, dann `LEADER_CHANGED` für den neuen Anführer. Ein zusammengefasstes Ereignis hätte zwei
Bedeutungen in einem Feld, und ein Empfänger, der nur die Mitgliederliste pflegt, müsste beide
Fälle unterscheiden können.

**Empfänger**: B13 (Anzeige), B14 (Rückmeldung an den Spieler).

---

## Was B06 nicht veröffentlicht

| Nicht veröffentlicht | Warum |
|---|---|
| Ein Ereignis je XP-Gewinn | FR-062 — bei 1000 je Sekunde eine Objekterzeugung je Gewinn |
| Ein Ereignis je durchlaufenes Level | FR-023 — ein Aufstieg ist ein Vorgang, kein Stapel |
| Ein Ereignis beim Verfall auf Maximallevel | FR-050 — dort ändert sich nichts, also gibt es nichts zu melden |
| Ein Ereignis für eine abgelehnte Vergabe | die Ablehnung steht im Rückgabewert; ein Ereignis dafür wäre ein Protokoll auf dem Bus |
| Ein Ereignis für eine offene Einladung | B14 antwortet dem Spieler direkt aus dem `PartyResult` |
