# Specification Quality Checklist: B10 · Mobs & Horden-Spawning

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-24
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

**Zu „No implementation details":** Die Spec nennt vorhandene Schnittstellen beim Namen
(`Zones.spawnAreasOf`, `MobStatProvider`, `MobXpProvider`, `MobCoinProvider`). Das ist bewusst und
kein Verstoß: es sind keine Vorgaben, wie dieser Block gebaut wird, sondern die Beschreibung
dessen, was drei andere Blöcke bereits zugesagt haben und was hier eingelöst werden muss. Ohne die
Namen wäre FR-006 bis FR-008 nicht prüfbar. Dieselbe Praxis wie in `specs/009-zones-regions/spec.md`.

**Zu „No [NEEDS CLARIFICATION] markers remain":** Es sind keine offen. Die drei Fragen, die den
Zuschnitt berührten — Vanilla-Unterdrückung, Wellenlogik, Boss-Mechanik — sind am 2026-08-24
beantwortet und in den Clarifications festgehalten; die übrigen zuvor offenen Punkte sind unter
*Assumptions* mit begründeten Vorgaben belegt. Was unter *Offene Punkte für `/plan`* steht, sind
Zahlen und Wege, keine Entscheidungen über den Umfang.

**Zu SC-007:** Das Erfolgskriterium ist bewusst zweigeteilt — eine Messung ohne Volllast gehört zu
diesem Block, der Nachweis unter 800 Kreaturen und 150 Spielern gehört seit ADR-031 zu B15. Der
Zielwert bleibt verbindlich; nur der Ort des Nachweises hat sich verschoben.
