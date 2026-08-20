# Vertrag: Ereignisse von B05

**Feature**: `specs/005-combat-pipeline` | **Datum**: 2026-08-20

Drei unveränderliche Records über B01s `EventBus`. Wie in B04 fängt `DefaultEventBus` Ausnahmen
eines Abonnenten ab und protokolliert sie, ohne die übrigen oder den Veröffentlichenden
mitzureißen — ein fehlerhaftes HUD kann keinen Kampf beschädigen (Prinzip VI).

---

## 1 · `CombatDeathEvent` (FR-026 bis FR-028)

```java
public record CombatDeathEvent(
        UUID victimId,
        UUID victimCharacterId,   // null bei einem Mob
        UUID killerId,            // null bei Umgebungstod
        DeathCause cause,
        DamageShare shares,       // vollstaendige Aufteilung, kann leer sein
        boolean playerVictim) {}
```

**Wann**: genau einmal je Tod. Weiterer Schaden auf ein totes Ziel erzeugt kein zweites Ereignis,
auch nicht bei zwei tödlichen Treffern im selben Tick (SC-011).

**Wofür**

| Abonnent | Was er daraus macht |
|---|---|
| B06 Progression | XP nach den Anteilen in `shares` verteilen |
| B11 Items | Beute an `shares.topContributor()`; bei `playerVictim` den Ausrüstungsschaden anwenden |
| B12 Statistiken | Abschüsse und Tode mitschreiben |

**Zusicherungen**

- `shares` ist nie `null`, kann aber leer sein — ein Mob, der im Lava stirbt, hat keine
  Beitragenden. Abonnenten müssen den leeren Fall behandeln, statt einen Beitragenden anzunehmen.
- `killerId` ist der letzte Angreifer, `shares.topContributor()` der größte Beitragende. **Das sind
  nicht dasselbe**, und genau darin liegt die Entscheidung gegen Kill-Stealing: Beute richtet sich
  nach dem zweiten, nicht nach dem ersten.
- B05 fasst die Ausrüstung des Opfers nicht an (FR-030). Das Ereignis trägt alles, was B11 dafür
  braucht.
- Veröffentlicht **nachdem** Erfahrung und Beute von Vanilla unterdrückt wurden, damit kein Abonnent
  auf einen Zustand trifft, in dem beides noch vorhanden ist.

---

## 2 · `DamageDealtEvent` (FR-038, FR-040)

```java
public record DamageDealtEvent(
        UUID attackerId,     // null bei Umgebungsschaden
        UUID targetId,
        DamageType type,
        double totalDamage,  // Summe des Fensters, nicht ein einzelner Treffer
        int hitCount,
        boolean lethal) {}
```

**Wann**: zusammengefasst je Angreifer-Ziel-Paar, wenn das Fenster abläuft oder das Ziel stirbt.
Zwanzig Treffer in einem Fenster ergeben **ein** Ereignis mit `hitCount == 20` (SC-009).

Ein Treffer ohne Schadenswirkung erzeugt gar kein Ereignis (FR-040).

**Wofür**: B13 zeichnet die Zahl, B12 schreibt sie mit.

**Zusicherungen**

- `totalDamage` ist der **angewandte** Schaden nach Verteidigung, nicht der Rohschaden.
- B05 erzeugt selbst keine Anzeigeobjekte in der Welt (FR-039). Bei 150 Spielern gegen 800 Mobs
  wären das tausende kurzlebige Entitäten je Sekunde — die Zusammenfassung hier ist der Grund,
  warum B13 das überhaupt darstellen kann.
- Die Bündelung ist **nicht** dasselbe wie die Trefferanimation: die wird je Treffer sofort
  ausgelöst (FR-037), nur die Zahl wird zusammengefasst.

---

## 3 · `CombatStateChangedEvent` (FR-030e)

```java
public record CombatStateChangedEvent(
        UUID holderId,
        UUID characterId,   // null bei einem Mob
        boolean inCombat) {}
```

**Wann**: beim Betreten und beim Verlassen des Kampfes. Das Verlassen wird beim nächsten Zugriff auf
den Zustand erkannt, nicht von einem Timer gemeldet (FR-030d) — es kann also einen Moment nach dem
tatsächlichen Ablauf eintreffen. Für den einzigen bekannten Verbraucher, die Mana-Regeneration in
B08, ist das ohne Bedeutung, weil sie selbst zeitstempelbasiert rechnet.

**Wofür**

| Abonnent | Was er daraus macht |
|---|---|
| B08 Fähigkeiten | Mana-Regeneration im Kampf reduzieren |
| B12 Statistiken | Kampfdauer mitschreiben |
| B13 UI | Kampfanzeige ein- und ausblenden |

---

## Was B05 ausdrücklich **nicht** veröffentlicht

| Nicht veröffentlicht | Warum |
|---|---|
| Ereignis je einzelnem Treffer | Bei 150 Spielern gegen 800 Mobs tausende je Sekunde. Wer jeden Treffer braucht, registriert einen Eingriffspunkt an der Stufe `AFTERMATH`. |
| Ereignis je verworfenem Angriff | Klick-Spam würde damit mehr Ereignisse erzeugen als tatsächliche Kämpfe. |
| XP- oder Level-Ereignis | Gehört B06. |
| Beute-Ereignis | Gehört B11. |
| Respawn-Ereignis | Vanilla hat eines; ein zweites danebenzustellen erzeugt nur die Frage, welches gilt. |
