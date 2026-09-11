# Specification Quality Checklist: B13 · UI, HUD & Texte

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-30
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

- **Zu „keine Implementierungsdetails": bewusste Abweichung, wie in B09 bis B12.** Die Spec nennt
  vorhandene Klassen (`StatusActionBar`, `AbilityHotbar`, `WaypointMenu`, `CurrencyMenu`,
  `ClassSelectionMenu`, `StatisticsMenu`) und die zwei geforderten Schnittstellennamen
  (`HudRenderer`, `ItemRenderer`). Das ist kein Entwurf, sondern **Bestand**: B13 ist ein
  Brownfield-Block, dessen Aufgabe zur Hälfte darin besteht, genau benanntes vorhandenes Verhalten
  zu übernehmen oder ausdrücklich nicht anzufassen. Eine Abgrenzung wie FR-024 („`AbilityHotbar`
  nicht umbauen") wäre ohne den Namen nicht prüfbar. Die beiden Schnittstellennamen stehen so im
  Blockdokument und in ADR-005.
- **Zwei Runden Klärung, beide am 2026-08-30.** Zuerst die sechs Fragen des Blockdokuments (vor der
  Spec, wie die Roadmap es verlangt), danach `/speckit-clarify` an der geschriebenen Spec. Die
  zweite Runde war nicht überflüssig: sie hat fünf Lücken gefunden, die beim Schreiben entstanden
  sind — darunter eine, die eine ganze User Story unbaubar gemacht hätte (**FR-050 verlangte ein
  Fenster, aber kein Satz sagte, wie ein Spieler hineinkommt** — genau die Lücke, für die ADR-028
  geschrieben wurde), und ein `ODER` mitten in FR-055, das keine Anforderung war.
- **Ein Ergebnis der zweiten Runde hat den Block verkleinert:** Anzeigen sind nur serverweit
  abschaltbar, nicht je Spieler. Damit führt B13 **keinen dauerhaften Zustand** und hängt nicht an
  B02 — festgehalten in FR-013b und SC-011.
- **Ein Punkt bleibt bewusst der Planung überlassen** und steht als Annahme, nicht als Lücke: der
  genaue Zuschnitt der Konfigurationsdatei.
- **Zwei ADRs sind zu schreiben**, bevor implementiert wird: einer für `/char` nach dem Muster von
  ADR-028, und die Schließvermerke an ADR-028 und ADR-032.
