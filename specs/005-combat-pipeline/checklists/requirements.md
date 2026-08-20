# Specification Quality Checklist: B05 · Kampf- & Schadens-Pipeline

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-20
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

## Notes (zweite `/clarify`-Runde, 2026-08-20)

Weiterhin 16/16. Drei zusätzliche Klärungen, davon eine, die den Block sonst wirkungslos gemacht
hätte:

- **Mobs ohne Werte** (FR-019a bis FR-019e): FR-018 lässt Wesen ohne Stat-Träger unangetastet, und
  kein Block vergibt heute welche. Die vollständige Pipeline hätte damit auf nichts ausser Spieler
  gewirkt — grün getestet und im Spiel unsichtbar, dieselbe Fehlerklasse, die ADR-012 für
  unverdrahtete Module beschreibt. B05 überbrückt das mit konfigurierten Werten je Mob-Art hinter
  einer Schnittstelle, die B10 übernimmt.
- **Kampfzustand** (FR-030c bis FR-030f): B08 hat bereits entschieden, dass Mana-Regeneration im
  Kampf reduziert ist. Nur B05 sieht jeden Treffer; ohne diesen Zustand wäre die B08-Entscheidung
  nicht umsetzbar oder würde dreimal nachgebaut.
- **Vanilla-Belohnungen** (FR-030a, FR-030b): zwei sichtbare Fortschrittssysteme nebeneinander.

Zwei weitere Punkte wurden geprüft und brauchten **keine** Frage, weil eine frühere Entscheidung
sie bereits beantwortet: Vanilla-Schilde und Vanilla-Rüstungswerte bleiben wirkungslos — „kein
Blocken" schliesst das eine aus, und eigener Schaden auf eigenes Leben das andere. Beides steht als
Annahme.

## Notes (erste `/clarify`-Runde, 2026-08-20)

Erneute Prüfung nach fünf weiteren Klärungen: weiterhin 16/16, keine Regression.

Zwei der fünf haben **Lücken geschlossen, die die Spec sonst mit sich getragen hätte**:

- **Projektile** (FR-024a, FR-024b): FR-016 setzt jeden Vanilla-Schaden auf null. Ohne eine eigene
  Behandlung wäre ein Bogen damit ab dem ersten Tag wirkungslos gewesen — eine ganze Kampfform
  still kaputt, ohne dass irgendeine Anforderung das gesagt hätte.
- **Inventar beim Tod** (FR-029b): Vanilla lässt beim Tod alles fallen. Die gewählte Todesstrafe
  „Ausrüstungsschaden" wäre daneben bedeutungslos gewesen — zwei Strafen, von denen die
  unbeabsichtigte die beabsichtigte verdeckt.

Die übrigen drei schärfen Verträge: der Schadensfaktor (FR-002a) ist die Schnittstelle, gegen die
B08 entwickelt; der Vanilla-Todesablauf (FR-029a) hält B05 aus einem Ablauf heraus, den es nicht
verbessern muss; Mob gegen Mob (FR-042a) sitzt bewusst an derselben Entscheidungsstelle wie die
PvP-Regel, damit es genau eine Stelle gibt, an der Erlaubnis entschieden wird.

## Notes (Erstprüfung)

Die sieben offenen Fragen des Blocksteckbriefs wurden **vor** `/specify` beantwortet und sind im
Abschnitt „Clarifications" festgehalten — die Roadmap-Regel „Offene Fragen zuerst" ist damit
eingehalten. Zwei Antworten hatten eine Konsequenz, die in der Spec ausdrücklich gezogen wird:

- **Todesstrafe Ausrüstungsschaden** hängt an B11, das es noch nicht gibt. FR-030 verbietet B05
  deshalb ausdrücklich den Zugriff auf Ausrüstung; FR-027 und FR-028 stellen stattdessen sicher,
  dass das Todesereignis alles trägt, was B11 später braucht.
- **Schadenszahlen** sind geteilt: die Bündelung ist Schadenslogik und bleibt hier (FR-038), das
  Zeichnen ist B13 und wird durch FR-039 ausgeschlossen.

Drei Punkte waren im Blocksteckbrief nicht festgelegt. Alle drei wurden am 2026-08-20 vorgelegt und
bestätigt beziehungsweise korrigiert:

1. **Umgebungsschaden: fester Betrag** — korrigiert. Der erste Entwurf hatte prozentualen Schaden
   angenommen, damit Umgebungsgefahren über die Progression gefährlich bleiben. Das war die falsche
   Richtung: Umgebungsgefahren *sollen* für ausgerüstete Spieler belanglos werden. Feste Beträge
   (FR-012a) erreichen genau das; SC-012a weist es nach.
2. **Verteidigung greift bei Umgebungsschaden nicht** (FR-012b) — bestätigt.
3. **Rückstoß bleibt Vanilla-Rückstoß** — bestätigt. Ein eigenes Rückstoßmodell steht in keinem
   Blocksteckbrief und wäre Umfangszuwachs ohne Auftrag.

Zwei erste Prüfrunden führten zu Korrekturen:

- FR-021 sagte zunächst „innerhalb des Zeitfensters abweisen", ohne zu klären, ob ein abgewiesener
  Schlag einen Beitrag zur Attribution erzeugt. Ergänzt: er erzeugt weder Schaden noch Animation
  noch Beitrag.
- SC-004 nannte „ohne Einbruch der Tickrate" ohne Zahl. Ersetzt durch die 40-Millisekunden-Grenze
  aus dem Blocksteckbrief.
