# Specification Quality Checklist: B06 · Progression (Erfahrung & Level)

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

## Prüfnotizen (2026-08-20)

**Keine Klärungsmarker.** Die vier offenen Fragen des Blocksteckbriefs wurden vor dem Schreiben der
Spezifikation entschieden und sind im Abschnitt `Clarifications` festgehalten. Alles weitere Unklare
wurde als Annahme dokumentiert, statt es offen zu lassen.

**Bewusste Abweichung bei „technology-agnostic".** SC-004 nennt Datenbankzugriffe, SC-012 die
Aufgabenanzahl des Schedulers, SC-015 die Vanilla-Erfahrung. Das sind keine eingeschleppten
Implementierungsdetails, sondern die wörtlichen nichtfunktionalen Vorgaben aus Prinzip II und aus
dem Blocksteckbrief („1000 XP-Ereignisse pro Sekunde erzeugen keinen einzigen DB-Zugriff"). Ohne
diese Begriffe wäre das Kriterium nicht mehr prüfbar. B05 verfährt in SC-004 und SC-005 genauso.

**Anforderungen ohne eigenes Nutzerszenario.** FR-038 (Message-Schlüssel), FR-057 (Versionierung und
Migration), FR-061 (keine wiederkehrenden Aufgaben) und FR-063 (kein Vanilla-XP als Speicher) sind
Randbedingungen, keine Abläufe. Sie werden über SC-012, SC-015 und SC-016 messbar geprüft, nicht
über eine Nutzergeschichte — das ist beabsichtigt.

**Nach der ersten Prüfrunde ergänzt.** FR-063 und FR-057 hatten zunächst kein messbares Kriterium;
dafür wurden SC-015 und SC-016 nachgetragen. Damit ist jede Anforderung entweder durch ein
Abnahmeszenario oder durch ein Erfolgskriterium abgedeckt.

**Offen für die Planungsphase, nicht für die Spezifikation.** Der genaue Erweiterungspunkt in B04 für
das Levelwachstum (Grundwertbeitrag gegenüber Modifikator) ist eine Umsetzungsfrage. Der
Blocksteckbrief fordert „über das Modifier-Modell aus B04, nicht über direkte Wertmanipulation";
die Spezifikation formuliert das als „offizielle Erweiterungspunkte von B04" (FR-020), weil
dauerhaftes Levelwachstum eher ein Grundwertbeitrag als ein Modifikator ist. `/speckit-plan`
entscheidet das mit Blick auf die tatsächlichen B04-Verträge.

**Nach der Klärungsrunde erneut geprüft (2026-08-20).** Fünf Fragen beantwortet, eingearbeitet als
FR-023a/b, FR-029a–c, FR-041a, FR-043, FR-053a sowie SC-006, SC-017 und SC-018. Kein Punkt der
Checkliste hat den Zustand gewechselt: 16/16 vor und nach der Runde. Die Spezifikation ist damit
umfangreicher, aber nicht ungenauer geworden — jede neue Anforderung trägt entweder ein
Abnahmeszenario oder ein Erfolgskriterium.

**Zweite Klärungsrunde (2026-08-20).** Fünf weitere Fragen, eingearbeitet als FR-009a, FR-021a/b,
FR-022a–c, FR-023c, FR-024 bis FR-024c und FR-060 sowie SC-019 bis SC-021. Wieder kein
Zustandswechsel in der Checkliste: 16/16. Zwei der fünf Antworten haben bestehende Aussagen
**ersetzt** statt ergänzt — FR-060 (unbekannter Mob gibt jetzt den Standardbetrag statt null XP,
angeglichen an `mobs:` in `combat.yml`) und FR-024 (die Zusage „Level sinkt nie" gilt nun
ausdrücklich nur gegenüber dem Spieler). Beide alten Formulierungen wurden entfernt, nicht
danebengestellt; eine Widerspruchssuche über die ganze Datei bestätigt das.

**Eine Entscheidung gegen die Empfehlung.** Bei den Ressourcen im Level-Up wurde Option D
(vollständig auffüllen) gewählt, empfohlen war A (anteilig). Die Folge ist in der Spezifikation
festgehalten, nicht weggelassen: der Aufstieg lässt sich in einen Bosskampf hinein aufsparen und
wirkt dort als planbare Vollheilung. Selbstbegrenzend, weil jedes Level genau einmal steigt und auf
Maximallevel entfällt.

## Notes

- Items marked incomplete require spec updates before `/speckit-clarify` or `/speckit-plan`
- Alle Punkte bestanden in Runde 2 von maximal 3.
