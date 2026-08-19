# Specification Quality Checklist: B02 · Persistenz-Layer

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

**Behoben während des Schreibens** — der Blocksteckbrief nennt durchgehend konkrete
Technologien (PostgreSQL, HikariCP, Flyway, Testcontainers). Diese wurden für die Spec
bewusst in fachliche Formulierungen übersetzt („Datenhaltung", „Verbindungspool",
„versionierte Migrationsschritte", „echte Datenbankinstanz"), damit die Spec das WAS
beschreibt und die Technologiewahl der `/plan`-Phase überlassen bleibt. Die
Technologiefestlegungen selbst sind durch ADR-001/ADR-003 und den Steckbrief bereits
gedeckt und gehen nicht verloren.

**Geklärt (2026-08-19)** — die ursprüngliche Frage in FR-009a (Verhalten beim Erreichen der
Puffergrenze während eines Datenbankausfalls) ist entschieden: Spieler werden kontrolliert
getrennt, kein stilles Verwerfen. Aufgeteilt in FR-009a/b/c. Begründung: von den drei
denkbaren Verhalten ist es das einzige, das FR-009 („ohne stillen Verlust") und
Constitution VI nicht widerspricht.

### Validierungsdurchlauf 2 — nach `/speckit-clarify` (2026-08-19)

Fünf Klärungsfragen gestellt und beantwortet; Ergebnis unter „Clarifications" in der Spec.
Alle 16 Punkte bestehen weiterhin (16/16 → 16/16), kein Marker musste umgestellt werden.

Auswirkung auf die Spec: 22 → 37 funktionale Anforderungen, 8 → 11 Erfolgskriterien.
Neu hinzugekommen sind die Unterpunkte FR-005a/b, FR-011a, FR-016a–c, FR-017a–c und
FR-019a–c; sie präzisieren bestehende Anforderungen, ohne den Umfang des Blocks zu erweitern.

Ein Widerspruch wurde dabei aufgelöst: Die Flush-Frist beim Herunterfahren (FR-011) war
unbestimmt, während B01 jedem Modul hart 10 Sekunden einräumt und danach zwangsterminiert.
Ohne die Festlegung auf 8 Sekunden wäre B02 im Ernstfall mitten im Schreiben abgeschnitten
worden — SC-002 wäre in genau dem Fall verletzt gewesen, für den es diesen Block gibt.

Ein früherer, nun überholter Edge-Case-Text („der erneute Verbindungsversuch darf keinen
veralteten Zustand laden") wurde durch die konkrete Regel aus FR-019a/c ersetzt, nicht
zusätzlich aufgenommen.

### Validierungsdurchlauf 3 — nach `/speckit-analyze` (2026-08-19)

Die Analyse fand einen kritischen Befund, der die Spec betraf: `tasks.md` verlangte
Meldungstexte über Message-Schlüssel (Constitution V), ohne dass die Spec eine entsprechende
Anforderung enthielt — und ohne dass es im Projekt eine solche Ablage gab. Nachgezogen:

- **FR-023 / FR-023a** ergänzt: spielersichtbare Texte laufen über Message-Schlüssel, fehlende
  Schlüssel brechen den Start ab
- **SC-012** ergänzt: kein Text im Code, fehlender Schlüssel verhindert den Start
- **User Story 5** ergänzt (Datenschutz & Nachvollziehbarkeit, P3) — Anonymisierung und
  Prüfprotokoll hingen zuvor nur in der Polish-Phase, obwohl SC-010 daran hängt

Umfang danach: 39 funktionale Anforderungen, 12 Erfolgskriterien, 5 User Stories.
Alle 16 Punkte bestehen weiterhin (16/16 → 16/16), kein Marker musste umgestellt werden.

Nebenbefund an B01, außerhalb dieser Spec: `PreJoinGuard` enthält drei hartcodierte
Spielertexte und verletzt damit Constitution V. Der Verstoß ist beim Constitution Check von B01
nicht aufgefallen und wird über Aufgabe T016 mitbehoben.

### Betriebliche Vorbedingung (kein Spec-Mangel)

Auf der Entwicklungsmaschine ist derzeit weder eine Container-Laufzeit noch eine lokale
Datenbank installiert. SC-008 (Test gegen echte Datenbankinstanz, Constitution VII)
ist damit zum Zeitpunkt der Umsetzung nicht erfüllbar. Blockiert `/clarify`, `/plan`
und `/tasks` nicht, muss aber vor `/implement` gelöst sein.
