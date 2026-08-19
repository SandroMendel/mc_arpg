# Specification Quality Checklist: B03 · Spieler-Session & Datenlebenszyklus

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-19
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

## Notes

### Validierungsdurchlauf 1 (2026-08-19)

**Abgrenzung zu B02 ausdrücklich gezogen.** Der größte Fehler, den diese Spec machen könnte, wäre
das Nachbauen von Mechanik, die B02 bereits ausgeliefert hat — Write-Behind, Autosave,
Shutdown-Flush, Zurückstellen bis zum Abschluss der Vorsitzung, Versionsprüfung beim Schreiben.
Die Anforderungen sind so formuliert, dass sie das **Verhalten** fordern, ohne eine zweite
Umsetzung zu verlangen; die Assumptions sagen das explizit. Bei `/plan` ist darauf besonders zu
achten.

**Architektureller Befund beim Schreiben.** Der geklärte Punkt „3 Charakter-Slots je Account" hat
eine Folge, die in B02 nicht vorweggenommen war: Der dortige Datensatz je Spieler beschreibt die
**Account**-Ebene, während der eigentliche Spielfortschritt am **Charakter** hängt. Das
widerspricht B02 nicht — dessen Datenmodell überlässt die fachlichen Spalten ausdrücklich den
besitzenden Blöcken — aber B03 führt damit eine Ebene ein, die es vorher nicht gab. In den
Assumptions festgehalten, weil B04, B06, B07 und B11 darauf aufbauen werden.

**Geklärt (2026-08-19)** — beide Punkte entschieden:

1. **FR-006**: Wie lange darf ein Spieler höchstens im gesperrten Zustand warten? Der Zielwert
   liegt bei 500 ms, aber der Zielwert ist keine Frist. Ohne Obergrenze bleibt ein Spieler bei
   einem hängenden Ladevorgang unbegrenzt bewegungsunfähig — kein vernünftiger Standard ableitbar,
   weil die Wahl zwischen „lieber warten" und „lieber abweisen" eine Betreiberentscheidung ist.
2. **FR-021**: Darf der aktive Charakter im laufenden Betrieb gewechselt werden? Das verändert den
   Umfang deutlich: Ein Wechsel im Betrieb erfordert einen vollständigen Entlade- und Ladezyklus
   bei verbundenem Spieler, inklusive aller Fehlerpfade aus User Story 3.

Beide fallen in die Kategorien „Scope" bzw. „User Experience" und sind damit klärungspflichtig.

### Ergebnis der Klärung (2026-08-19)

1. **FR-006** → 5 Sekunden, aufgeteilt in FR-006/FR-006a. Bewusst als Notbremse formuliert, nicht
   als erwartete Ladezeit: das Zehnfache des 500-ms-Zielwerts aus SC-001.
2. **FR-021** → Der aktive Charakter wird beim Verbinden festgelegt, aufgeteilt in
   FR-021a/FR-021b. FR-021b verbietet einen Wechsel in bestehender Sitzung ausdrücklich — sonst
   müsste der gesamte Lade- und Entladepfad samt Fehlerfällen ein zweites Mal für einen
   verbundenen Spieler existieren.

Umfang danach: 30 funktionale Anforderungen, 10 Erfolgskriterien, 6 User Stories.
Alle 16 Punkte bestehen (15/16 → 16/16).
