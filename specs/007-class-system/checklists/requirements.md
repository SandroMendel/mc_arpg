# Specification Quality Checklist: B07 · Klassen-System

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-21
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

Durchlauf 1 fand zwei Fehler, beide behoben:

1. **SC-005 war falsch formuliert.** Es forderte einen Leiteranteil von 60–80 % „je Attribut und
   Klasse". Angriffsgeschwindigkeit, Laufgeschwindigkeit und Fähigkeiten-Cooldown haben nach
   Vorgabe aus B06 kein Levelwachstum und kommen damit zu 100 % aus der Leiter — das Kriterium wäre
   für drei von acht Attributen strukturell unerfüllbar gewesen. Jetzt auf die fünf Attribute mit
   Levelwachstum eingeschränkt, mit ausdrücklicher Ausnahme für die übrigen drei.
2. **Ein Edge Case nannte einen Datenbankschlüssel** (`(player_id, character_class)`). Ersetzt durch
   die Beschreibung der Regel, die er durchsetzt.

Durchlauf 2 (nach Klärung von Materialexklusivität und Mage-Waffe) fand einen echten Fehler und eine
Lücke:

3. **Der Mage trug Kupfer auf Stufe 3**, obwohl Kupfer als Warrior-exklusiv erklärt wurde. Korrigiert
   auf Eisen; damit sind Leder, Eisen, Diamant und Netherite geteilt und je Klasse genau ein
   Exklusivsatz auf Stufe 2 (Kupfer / Kettenhemd / Gold). Neue Anforderung FR-016a setzt die
   Exklusivität beim Laden durch, neues Kriterium SC-012 weist sie nach.
4. **Der Waffentyp war eine unmodellierte neunte Wertquelle.** Vanilla-Waffen tragen eigene
   `ATTACK_SPEED`-Modifikatoren, und die Brücke zu den Vanilla-Attributen setzt nur den Basiswert.
   Der Rogue wäre auf der Axt trotz +50 % die langsamste Klasse gewesen, und B05 hätte sein
   Angriffsfenster gegen einen anderen Wert gerechnet als die Anzeige zeigt. Neue Anforderungen
   FR-046 bis FR-048 und Kriterium SC-011.

Durchlauf 3 (nach der Revision der Rüstungs- und Waffenpfade) war der größte Eingriff:

5. **Die feste Stufenzahl fünf war falsch.** Die genannten Materiallisten ergeben Warrior 5/6,
   Rogue 6/6, Mage 7/7 — Rüstung und Waffe einer Klasse dürfen unterschiedlich lang sein. FR-013
   verliert die feste Zahl, FR-013a normiert Wertekurve und Levelanforderungen auf die eigene Länge
   jeder Leiter, SC-014 weist es für fünf, sechs und sieben Stufen nach. ADR-017 und der Steckbrief
   sind nachgezogen. Alle Werttabellen wurden neu gerechnet.
6. **FR-016 war unhaltbar.** Die Regel „zwei Stufen derselben Leiter dürfen nicht dasselbe Material
   tragen" hätte den Mage unmöglich gemacht, der durchgehend Leder trägt. Ersetzt durch
   Unterscheidbarkeit des *Erscheinungsbilds* aus Material, Färbung und Trim, plus FR-016a bis
   FR-016c für Pflichtfelder, Färbbarkeit und Familientrennung.
7. **Kosmetik war als Addon eingeplant und ist jetzt Pflicht.** Für Mage und Rogue trägt Färbung
   beziehungsweise Trim die Stufe, weil das Material sie nicht mehr unterscheidet. FR-022 umgestellt,
   ADR-017 korrigiert.
8. **Die Mage-Angriffsgeschwindigkeit war bei sieben Stufen nicht streng steigend.** Ziel +5 % ergab
   bei zwei Dezimalstellen zwei Stufen auf 0 — ein Startfehler nach FR-017. Auf +6 % angehoben, damit
   sechs unterscheidbare Schritte über null bleiben.

Nachgerechnet nach der Revision: **keine Verletzung** über alle 48 Attribut-Leiter-Kombinationen —
jede Leiter streng steigend, kein Cap überschritten, alle Abweichungen unter 3 % (größte: Rogue
Defense −2,6 %), Leiteranteil 66–77 % für die fünf wachsenden Attribute.

Am Paper-Artefakt `26.2.build.112-stable` verifiziert, statt angenommen:
Kupfer- und Kettenhemd-Rüstung existieren; der Speer existiert in sieben Materialstufen; Kettenhemd
existiert nur als Rüstung; Mace und Trident haben keine Materialvarianten und können keine Leiter
tragen. Ebenfalls geprüft: `LeatherArmorMeta.setColor` existiert, und `TrimPattern` liefert 18 Muster
bei 11 `TrimMaterial`-Werten — genug für die Rogue-Stufen 4 bis 6. Die entsprechenden Annahmen wurden
aus dem Abschnitt „Assumptions" entfernt.

Bewusst beibehaltene Grenzfälle der Prüfkriterien:

- **SC-010 nennt „Datenbankzugriff".** Streng gelesen ist das ein Implementierungsdetail. Es steht
  hier, weil Prinzip II der Constitution genau diese Aussage zur nachweispflichtigen Eigenschaft
  macht — dieselbe Formulierung wie in den Erfolgskriterien von B02, B03 und B06. Eine
  technologiefreie Umschreibung würde die Prüfbarkeit verlieren.
- **Die Clarifications nennen „Enum-Wert plus Migration".** Das ist Teil der zitierten Entscheidung
  ADR-019 und beschreibt bewusst den Preis des Upgradepfads. Ohne diese Angabe wäre die
  Entscheidung nicht nachvollziehbar.
- **Alle Zahlen im Abschnitt „Ausgearbeiteter Inhalt" sind Balancing-Ausgangswerte**, nicht
  Anforderungen. Sie sind nach Prinzip V über Konfiguration änderbar; die Anforderungen darüber
  fordern nur, dass sie änderbar sind und die Caps treffen.

Rechnerisch geprüft: alle acht Attribute treffen für alle drei Klassen die Wertebereiche aus ADR-008
mit einer Abweichung unter 3 % (größte Abweichung: Warrior Mana −2,5 %; Rogue Defense −2,6 %), der
Leiteranteil liegt für die fünf wachsenden Attribute zwischen 66 % und 77 %, und kein prozentuales
Attribut überschreitet seinen Cap.
