# Specification Quality Checklist: B11 · Items, Ausrüstung & Loot

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-28
**Feature**: [spec.md](../spec.md)

## Content Quality

- [x] No implementation details (languages, frameworks, APIs)
- [x] Focused on user value and business needs
- [x] Written for non-technical stakeholders
- [x] All mandatory sections completed

## Requirement Completeness

- [x] No [NEEDS CLARIFICATION] markers remain
- [x] Requirements are testable and unambiguous
- [x] Success criteria are measurable
- [x] Success criteria are technology-agnostic (no implementation details)
- [x] All acceptance scenarios are defined
- [x] Edge cases are identified
- [x] Scope is clearly bounded
- [x] Dependencies and assumptions identified

## Feature Readiness

- [x] All functional requirements have clear acceptance criteria
- [x] User scenarios cover primary flows
- [x] Feature meets measurable outcomes defined in Success Criteria
- [x] No implementation details leak into specification

## Umfang

7 User Stories · 87 funktionale Anforderungen · 18 Erfolgskriterien · 23 Edge Cases.
Nummerierung geprüft: FR-001 bis FR-081 lückenlos, dazu sechs Nachträge mit Buchstabensuffix
(FR-026a–d, FR-040a, FR-041a) nach dem Muster von FR-018c und FR-024b in den Nachbarblöcken.
SC-001 bis SC-017 lückenlos, dazu SC-006a.

## Notes

### Bewusste Abweichung: benannte Nähte statt Technologieneutralität

Die Spec nennt an mehreren Stellen konkrete Typen — `BoundEquipment`, `CharacterInventory`,
`SourceKind.BUFF`, `BookingReason.VENDOR_PURCHASE`, `EquipmentPurchase`, `TierAppearance`,
`CombatDeathEvent.lootRecipient()`, `DamageOrigin`. Das ist kein Durchschlagen von
Implementierungsdetails, sondern der **Abgrenzungszweck** dieses Blocks: B11 ist der erste Block,
bei dem die Hälfte des ursprünglichen Umfangs bereits von Nachbarblöcken gebaut wurde. Ohne die
namentliche Nennung ließe sich FR-079 („keine zweite Fassung einführen") nicht prüfen, und genau
dieser Verstoß ist bei einem verkleinerten Block der wahrscheinlichste.

Dieselbe Praxis steht in `specs/010-mobs-spawning/spec.md`, wo `MobStatProvider`, `MobXpProvider` und
`Zones.spawnAreasOf` namentlich vorkommen.

### Klärungssitzung 2026-08-28 — sieben Fragen, alle beantwortet

**Teil 1 (Umfang):**

- **Q1** → Aufstieg über den NPC gegen Level und Coins; Kategorie „Aufstiegsmaterial" entfällt
  (FR-017, FR-061, FR-062)
- **Q2** → Trimfarben als Endgame-Kosmetik, verkauft gegen Coins (FR-068 bis FR-073)
- **Q3** → Ausrüstung zerbricht nie, ihr Beitrag sinkt auf bis zu 20 % (FR-047, FR-048)

**Teil 2 (vor dem Plan):**

- **Q4** → Verschleiß durch erlittenen Schaden (Rüstung), Autoattacks (Waffe) und Tod (beide);
  Fähigkeitsschaden schont die Waffe. Der Tod wiegt ein Vielfaches (FR-040 bis FR-046)
- **Q5** → zwei getrennte Zustandswerte, Rüstung und Waffe (FR-039, FR-049, FR-052)
- **Q6** → ein NPC je Region, sechs insgesamt, Bestand skaliert (FR-057, FR-058)
- **Q7** → Beute fällt auf den Boden, ist aber **nur für ihren Eigentümer** sichtbar und
  aufsammelbar (FR-026 bis FR-032, SC-005, SC-006)

**Teil 3 (nach der Party-Frage des Auftraggebers):**

- **Q8** → in einer Party gilt die Party als **ein** Beitragender, der Gegenstand wandert **reihum
  je gefallenem Gegenstand** unter den Mitgliedern in Reichweite (FR-026a bis FR-026d, SC-006a)
- **Q9** → Verschleiß bemisst sich am **ankommenden** Schaden vor Abwehr, gegen die Abwärtsspirale
  (FR-040, FR-040a)
- **Q10** → der Klon aus B08 nutzt **nichts** ab (FR-041a)
- **Q11** → volles Inventar ändert nichts an der Beute: sie fällt, es wird gewarnt, sie verfällt

### Der Fund, den erst die Party-Frage sichtbar machte

Die ersten beiden Klärungsrunden hatten **B06s gebautes Party-System** (`Party`, `PartyRegistry`,
`ShareCalculator`) vollständig übersehen. B06 behandelt eine Party als **einen** Beitragenden und
verteilt Erfahrung und Coins gleichmäßig auf die Mitglieder in Reichweite — *„damit gemeinsames
Spielen nicht schlechter ist als allein zu spielen"*. Für Beute galt das nicht, weil
`DamageShare.topContributor` aus B05 stammt und **B05 keine Partys kennt**. In einer festen Gruppe
wäre jedes Item dauerhaft an denselben Spieler gegangen.

Zwei fertige Blöcke wollten Unvereinbares, und keine der beiden Spezifikationen war falsch — die
Lücke lag dazwischen. Aufgefallen ist sie nur, weil danach gefragt wurde.

### Drei Funde im Code, die die Spec geformt haben

**1. Der Steckbrief widerspricht dem Bestand.** Er kündigt „Durability und Reparatur" auf gebundener
Rüstung an. `BoundItemFactory.makeIndestructible()` setzt Klassenausrüstung aber ausdrücklich auf
`setUnbreakable(true)`, mit der Begründung: eine zerbrochene Waffe ließ den Krieger waffenlos zurück,
und *„if wear is ever wanted as a mechanic, it belongs to the tier, not to the item stack."* Die Spec
folgt dem Code: **der Verschleiß ist ein Wert am Charakter** (FR-038, FR-039).

**2. Beute teilt sich nicht wie Erfahrung und Coins.** B06 und B08b teilen nach Anteil über denselben
`ShareCalculator` (ADR-029). B05 hat für Items das Gegenteil entschieden — *„XP is split by share
because XP divides, loot goes to the largest contributor because a sword does not"* — und stellt
`CombatDeathEvent.lootRecipient()` bereit. FR-026 benutzt das, statt eine eigene Regel zu erfinden.

**3. Die schwierigste Anforderung ist bereits gelöst.** „Beute nur für ihren Eigentümer sichtbar und
aufsammelbar" ist kein Neubau: `rpg.platform.currency` macht das seit B08b für Coin-Haufen, und das
`package-info` benennt jede Falle — Sichtbarkeit als Verbindungszustand (Relogin!), `setOwner` kennt
Spieler statt Charaktere, Verschmelzen als Gefahr, Unsichtbarkeit ist niemals die Autorität. FR-028
bis FR-032 erben diese Liste.

### Eine Kollision, aufgelöst — und der Preis dafür ist benannt

Frei anwendbare Trimfarben hätten die Ausrüstungsstufen von **Schurke** (Stufen 4–6 dreimal
`CHAINMAIL`, nur durch Trim unterschieden) und **Krieger** (Stufen 5–6 zweimal `NETHERITE`)
ununterscheidbar gemacht — B07s FR-016 fordert das Gegenteil. FR-069 löst das, indem Kosmetik erst
auf der Höchststufe anwendbar ist, was der genannten Absicht („Grindfaktor nach Stufe 60")
entspricht.

**Falls der Auftraggeber Trims auf jeder Stufe will**, brauchen die beiden Leitern in `classes.yml`
ein zweites Unterscheidungsmerkmal. Das ist eine Änderung an **B07** und gehört in einen eigenen ADR,
nicht in diesen Block.

### Vor `/speckit-plan` — beides erledigt

- ~~**Constitution Prinzip IV anpassen**~~ — **erledigt.** PATCH 1.1.0 → 1.1.1, in allen drei
  Fassungen (`.specify/memory/constitution.md`, `constitution.md`,
  `minecraft-rpg-spec/minecraft-rpg-spec/constitution.md`). Der Constitution Check des Plans prüft
  jetzt gegen den gültigen Wortlaut.
- ~~**Neuer ADR fällig**~~ — **erledigt: ADR-039.** Vier Punkte darin sind mehr als Balancing:
  1. **Verschleiß als Charakterwert statt Item-Haltbarkeit** (Q3–Q5) — konkretisiert die
     Todesstrafe aus ADR-017, korrigiert den Steckbrief, bestätigt B07s Unzerstörbarkeit.
  2. **Beute gehört einem Charakter allein** (Q7) — und zwar dem größten Beitragenden, nicht
     anteilig wie Erfahrung und Coins. Abweichung von ADR-029 mit eigener Begründung.
  3. **In einer Party wandert die Beute reihum** (Q8) — die Auflösung eines Widerspruchs zwischen
     B05 und B06, den keiner der beiden Blöcke für sich sehen konnte.
  4. **Kosmetik erst auf der Höchststufe** (FR-069) — dokumentierte Abwägung gegen B07s FR-016.

  *(Erledigt: ADR-039 steht in `02-decisions.md` und deckt alle elf Antworten ab.)*
