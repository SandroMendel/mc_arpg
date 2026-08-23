# Specification Quality Checklist: B09 · Zonen & Regionen

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-23
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

**Alle Punkte bestanden (Stand 2026-08-23).** Der Block ist planungsreif; `/speckit-clarify` ist
nicht erforderlich, weil beide Klärungen bei `/specify` beantwortet wurden.

**Die zwei Klärungen und ihre Antworten.**

1. **Wovor schützt der Schutzkern? → Vollständig schadensfrei** (FR-028a, FR-028b). Jeder Schaden
   wird abgelehnt, dessen Ziel *oder* dessen Angreifer im Kern steht, Umweltschaden eingeschlossen.
   Bei der Klärung wurde eine Unstimmigkeit in der Fragestellung selbst korrigiert: die dort als
   Preis genannte „Torlinie" — vom Kernrand gefahrlos hinausschiessen — entsteht bei dieser Antwort
   gar nicht, weil aus dem Kern heraus ebenfalls kein Schaden möglich ist. Die Regel wirkt über den
   **Ort**, nicht über die Beteiligten.

   *Folge für FR-030:* die ausgelieferte Schadenserlaubnis bleibt in allen sechs Fällen unverändert —
   aber nur **ausserhalb** der Kerne. Innerhalb greift die einzige Abweichung, die dieser Block
   vornimmt. Entschieden wird weiterhin an genau einer Stelle.

2. **Was liefert die Konfiguration aus, solange die Karte fehlt? → Sechs Regionen mit vorläufigen
   Koordinaten plus Startwarnung** (FR-065 bis FR-065c, SC-016). Die Vorläufigkeit steht als Wert in
   der Datei, nicht als Kommentar; solange er gesetzt ist, warnt der Server bei jedem Start. Ein
   Kopfkommentar hat bei T103 nicht gereicht — deshalb an der Stelle, wo hingesehen wird.

**Zu „No implementation details".** Die Spec benennt Quader-Geometrie, Chunk-Index, die
bukkit-freie Domänenschicht und die Namen wartender Schnittstellen. Das ist in diesem Projekt
**gewollt** und folgt den acht vorangegangenen Specs: die Constitution macht Architekturvorgaben
selbst zu Anforderungen (Prinzip II räumlicher Index, Prinzip III `rpg-core` ohne Bukkit und
`Zone` ≠ `World`, Prinzip V Konfiguration statt Code). Eine Spec, die das ausspart, wäre gegen die
Constitution nicht prüfbar. Die Grenze, die eingehalten wird: **keine** Klassennamen, Signaturen,
Paketpfade oder Bibliotheken als Vorschrift — die stehen erst im Plan.

**Zu SC-008.** Das Kriterium ist absichtlich als „die vorhandenen Tests der Kampfpipeline bleiben
unverändert grün" formuliert und nicht als neue Zusicherung. Der Austausch der Schadenserlaubnis ist
genau der Vorgang, für den B05 seinen Quelltest angelegt hat; ein angepasster Test wäre kein
Nachweis, sondern dessen Umgehung.

**Sechs Punkte sind an `/plan` übergeben** und stehen im Abschnitt *Offene Punkte für `/plan`* — sie
sind Planungsfragen, keine offenen Spezifikationsfragen: der Ort der Startwarnung, der Schnitt
zwischen Domäne und Plattform bei der Geometrie, das Neubewerten aller Anwesenden ohne wiederkehrende
Aufgabe, die Form des Schutzkern-Ereignisses, die Lasttestpflicht (dieselbe Frage wie T122 bei B08b)
und der Eingriff in B05s Todesgrund-Enum.
