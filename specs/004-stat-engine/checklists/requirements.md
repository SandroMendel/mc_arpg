# Specification Quality Checklist: B04 · Attribut- & Stat-Engine

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

## Notes (Stand nach `/clarify`, 2026-08-20)

- Erneute Prüfung nach fünf Klärungen: weiterhin 16/16 Punkte erfüllt, keine Regression.
- Neu hinzugekommen und geprüft: FR-004a, FR-014a, FR-019a, FR-019b, FR-030a, FR-030b sowie
  Schärfungen an FR-004, FR-013, FR-014, FR-018, FR-019, SC-001. Alle sind testbar und
  technologieneutral formuliert.
- Der Begriff „Vormerkung" ist einheitlich verwendet; die früheren Formulierungen „Neuberechnung
  angefordert", „Berechnungsvermerk" und „Merkliste" wurden ersetzt, damit kein zweiter Begriff für
  dieselbe Sache steht (Befund T1 aus `/analyze`, 2026-08-20).
- Nach `/analyze` korrigiert: FR-018 und FR-019 beschrieben noch einen serverweiten Durchlauf am
  Tick-Ende, den der Plan aus Verfassungsgründen verworfen hatte (Befund I1). Der Wortlaut folgt
  jetzt der trägergebundenen Einmalaufgabe aus `research.md` E4.
- Die frühere Annahme zu den Ressourcenbehältern ist nun eine Entscheidung und im Abschnitt
  „Clarifications" festgehalten; die Annahme verweist darauf.

## Notes (Erstprüfung)

- Erste Prüfrunde ergab zwei Befunde, beide korrigiert:
  1. FR-014 sprach zunächst von "Beiträge begrenzen" statt vom wirksamen Ergebnis — unklar, ob
     einzelne Beiträge oder die Summe gemeint sind. Neu formuliert als Grenze der wirksamen
     Veränderung gegenüber dem Basiswert.
  2. FR-031 nannte "kleinster darstellbarer Wert" ohne Bezugspunkt. Ergänzt um die Bedingung
     "solange das aktuelle Leben größer null ist" und um Szenario 4 in User Story 4 für den
     Gegenfall.
- Bewusst als Annahme statt als [NEEDS CLARIFICATION] festgehalten: die Zuordnung der
  Ressourcenbehälter (aktuelles Leben/Mana) zu B04. Begründung steht im Abschnitt "Assumptions";
  ADR-003 verlangt beide Werte für die Anzeigeformel, ein anderer Block besitzt sie zu diesem
  Zeitpunkt nicht.
- Begriffe wie `health` oder `GENERIC_MAX_HEALTH` sind keine Implementierungsdetails, sondern
  vertraglich festgelegte Bezeichner aus ADR-003 und dem Blocksteckbrief.
