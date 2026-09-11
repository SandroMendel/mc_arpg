# Specification Quality Checklist: B14 · Commands, Permissions & Admin-Tools

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-09-03
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

### Durchlauf 1 (2026-09-03)

**Ein `[NEEDS CLARIFICATION]`-Marker war offen und ist geklärt.** Er betraf die Frage, ob eine vom
Betreiber gesetzte Kreatur gegen das Spawn-Budget zählt. Er wurde bewusst nicht geraten: ADR-050
stützt beide Antworten und die Wahl verändert sowohl FR-018/FR-019 als auch B10s Laufzeitverhalten.

**Antwort (2026-09-03):** eigener, begrenzter Bestand — geführt in der Registry, getrennt vom
Zonenbudget gezählt. Eingearbeitet als FR-019a und FR-019b, SC-010, zwei zusätzliche
Abnahmeszenarien in User Story 5 und der Abschnitt *Die eine geklärte Frage*.

### Durchlauf 2 (2026-09-03)

Alle Punkte erfüllt. Die Spec ist bereit für `/speckit-plan`.

### Durchlauf 3 — Querprüfung nach Phase 1 (2026-09-03)

Spec, Plan, Recherche, Datenmodell, Verträge und Quickstart gegeneinander geprüft. **Keine
CRITICAL-Befunde, keine Constitution-Verstöße.** Vier HIGH und fünf MEDIUM wurden gefunden und
eingearbeitet:

- **H1 Nummernkollision.** Fremde Anforderungsnummern (`B01-FR-004`, `B02-FR-018`, `B04-FR-016`,
  `B06-FR-024b`, `B08b-FR-046`) kollidierten mit B14s eigenen FR-003, FR-004, FR-016, FR-018 und
  FR-024. Neun Stellen umgeschrieben, die Schreibweise steht jetzt als Regel in der Spec.
- **H2 Nachladehaken.** FR-023 verlangte, dass *alle* Module ihren Haken ausführen, belegt wurde
  einer. Neu: FR-023a fordert eine Logzeile, die die sechs Module beim Namen nennt, plus
  Abnahmeschritte 17a und 19a.
- **H3 Anfügend.** FR-031 hatte weder Test noch Schritt. Neu: FR-031a, ein Architekturtest.
- **H4 Lesepfad ungedeckt.** `between()` war nie gegen eine Datenbank gelaufen — dieselbe Lage wie
  `reloadAll()`. Neu: FR-036, ein Testcontainers-Test.
- **M1** Zahl in `plan.md` korrigiert (jetzt 41 FR, gezählt statt behauptet). **M2** zwölf
  Anforderungen werden jetzt in Quickstart und Verträgen bei ihrer Nummer genannt, damit die
  Abdeckung maschinell zuordenbar ist. **M3** Schritt 31a vergleicht die Daten des Betroffenen vor
  und nach einer Einsicht — kein Audit-Eintrag beweist nur, dass nichts *protokolliert* wurde.
  **M4** Schritt 23a liest die Budgetzahl selbst aus. **M5** ein Begriff statt drei: **Sperrzeit**.
- **L1** die `[[…]]`-Verweise sind als Gedächtnisnotizen gekennzeichnet. **L2** US8 heißt jetzt nach
  dem, was daran wirklich nachrangig ist — die Leseseite.

**Stand: 41 FR, 10 SC, 8 User Stories, 40 Abnahmeschritte plus drei Vorabschritte.** Bereit für
`/speckit-tasks`; der vollständige `/speckit-analyze` läuft erst danach, weil er `tasks.md` braucht.

**Bewusste Grenzfälle der Prüfung:**

- *Keine Implementierungsdetails*: Die Spec nennt an mehreren Stellen vorhandene Klassennamen
  (`ConfigHandle`, `AuditEntry`, `HordeRegistry`, `ItemStackFactory`). Das ist gewollt und in diesem
  Projekt üblich — der Abschnitt *Ausgangslage* belegt damit, **was schon existiert**, statt es neu
  zu fordern. Genau diese Belege haben bei B13 verhindert, dass fünf vorhandene Dinge ein zweites
  Mal gebaut wurden. In den Anforderungen (FR) und Erfolgskriterien (SC) steht kein Klassenname, wo
  er die Lösung vorwegnehmen würde.
- *Technologieneutrale Erfolgskriterien*: SC-002 zählt Vorkommen im Quelltext. Das ist eine
  überprüfbare Aussage über den Zustand des Blocks und die einzige Formulierung, die die Zusage
  „manuelle Argumentzerlegung verschwindet" messbar macht.
- Brigadier wird in den Anforderungen **nicht** genannt — es steht nur in den Annahmen, wo es als
  bindende Vorentscheidung des Blockdokuments hingehört.

**Nicht als Klärungsbedarf markiert, sondern als Annahme entschieden** (jeweils mit Begründung in
der Spec): Kommandostruktur `/rpg <Unterkommando>` für Admin-Werkzeuge, Beibehaltung der sechs
Spielerkommandonamen, Voreinstellung der Sperrzeit, unveränderte Übernahme des Audit-Logs.
