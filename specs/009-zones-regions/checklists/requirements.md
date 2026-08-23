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

**Alle Punkte bestanden — nach beiden `/clarify`-Runden (Stand 2026-08-23).** 16 von 16, kein Punkt
hat je seinen Zustand geändert. Der Block ist planungsreif.

---

## `/clarify` — zweite Runde, fünf weitere Fragen *(2026-08-23)*

Die Kristalle aus Runde 1 hatten viel neue Oberfläche geschaffen; dort saßen alle fünf Lücken.

1. **Reisen im Kampf ist gesperrt** (FR-051f, FR-051g, SC-023). Die wichtigste der zehn Antworten
   dieses Tages nach ADR-030 selbst: ohne die Sperre wäre Reisen der bezahlte Fluchtweg aus jedem
   Kampf geworden — dieselbe Lücke, die ADR-030 beim Ausloggen gerade geschlossen hatte, nur mit
   Coins als Eintrittspreis. Kostet nichts Neues, weil derselbe Kampfzustand gelesen wird.
2. **Ein Ankunftsort je Region** statt zweier (FR-045a, SC-024). Der Kristall trägt kein eigenes Ziel
   mehr. Zwei Koordinaten im selben Schutzkern wären auseinandergedriftet, und es wäre erst
   aufgefallen, wenn jemand nach dem Tod woanders steht als nach der Reise. **Zwei Anforderungen sind
   dadurch entfallen**, nicht hinzugekommen.
3. **Freischaltungen enden mit dem Charakter** (FR-051b1, FR-051b2, SC-027). Die Prüfung ergab, dass
   das Projekt dafür längst ein Muster hat — jede charakterbezogene Tabelle trägt
   `ON DELETE CASCADE`, B02 besitzt den Löschpfad. Kein neuer Anschluss nötig; die in der Frage
   vermutete Abhängigkeit zu B03 gab es nicht.
4. **Gesperrte Ziele zeigen Name und Levelband** (FR-048a, FR-048b, SC-026), aus demselben
   Message-Schlüssel wie überall. Verdeckt würde nur, was ein Spaziergang ohnehin enthüllt.
5. **Die Startregion gehört hierher** (FR-037a bis FR-037d, SC-025). Eine echte Lücke: die Spec regelte
   Tod und Logout, aber nicht den Spielbeginn. Ohne Regel hing der wichtigste Ort des Spiels an einem
   einmal richtig ausgeführten `/setworldspawn`. Damit dient die eine Koordinate je Region **dreimal**
   — Spielbeginn, Tod, Reiseziel.

Nach beiden Runden: **96 Anforderungen, 27 Erfolgskriterien** (Ausgangsstand 70 und 16). *Stand nach `/plan` und `/analyze`: **100 Anforderungen, 28 Erfolgskriterien**, 143 Aufgaben.*

---

## `/clarify` — fünf Fragen, fünf Antworten *(2026-08-23)*

Der Durchlauf war **nicht** überflüssig, obwohl die Checkliste vorher schon vollständig bestanden war.
Er hat den Zuschnitt des Blocks an zwei Stellen geschmälert und an einer erheblich erweitert:

1. **Zonenidentität.** Technische Kennung plus Message-Schlüssel statt Anzeigename (FR-003 bis
   FR-003c, SC-017). Ohne das hätte jeder Verweis aus B10, B11 und B13 an einem Spielertext gehangen.
2. **Vorausgreifende Felder entfernt.** Schwierigkeitsmodifikator und Loot-Zuordnung fallen weg
   (FR-056, FR-057, FR-057a). Ein Feld, dessen Bedeutung der Block nicht kennt, kann er nicht prüfen —
   genau das Muster, das B07s `cost`-Block gekostet hat.
3. **Spawn-Bereiche ohne Rolle.** Ausgeliefert werden sie, aber ohne Art oder Absicht (FR-053a). Ein
   Boss-Bereich unterscheidet sich geometrisch von nichts.
4. **Portale wurden Wegpunkt-Kristalle** (FR-045 bis FR-051e). **Die grösste Änderung des Tages** —
   siehe unten.
5. **Reisen kostet Coins.** Preis in der Zonenkonfiguration (ADR-027), eigener Buchungsgrund in B08b.

### Der Zuschnitt ist gewachsen, und das ist ausdrücklich festgehalten

Die Wegpunkt-Kristalle bringen **vier Eingriffe über die Blockgrenze**: Eingabe und Auswahlfenster
(B13), dauerhafter Zustand je Charakter (B02) und ein neuer Buchungsgrund in **B08b, einem
abgeschlossenen Block**. Die Spec hatte bei den Portalen wörtlich „kein Auswahlfenster" verlangt; das
ist jetzt das Gegenteil. Deshalb **ADR-032**, nach dem Muster von ADR-028, mit Fenster und Eingabe
befristet bis B13.

Der überholte Portal-Eintrag in den Clarifications ist **stehengeblieben und als überholt markiert**,
nicht gelöscht: seine Begründung hält fest, was der Wechsel gekostet hat.

Nach Runde 1 waren es **85 Anforderungen und 22 Erfolgskriterien** — vorher 70 und 16.

---

## Die zwei Klärungen aus `/specify` und ihre Antworten

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
   Kopfkommentar hat bei **B08bs T103** nicht gereicht — deshalb an der Stelle, wo hingesehen wird.
   *(Nicht mit T103 dieses Blocks verwechseln, das ist der Kristall-Index.)*

**Zu „No implementation details" und „technology-agnostic".** Die Spec benennt Quader-Geometrie,
Chunk-Index, die bukkit-freie Domänenschicht, die Namen wartender Schnittstellen und einzelne
Konfigurationsdateien (`messages.yml` in SC-017, `currency.yml` in SC-021). Das ist in diesem Projekt
**gewollt** und folgt den acht vorangegangenen Specs: die Constitution macht Architekturvorgaben
selbst zu Anforderungen (Prinzip II räumlicher Index, Prinzip III `rpg-core` ohne Bukkit und
`Zone` ≠ `World`, Prinzip V Konfiguration statt Code). Eine Spec, die das ausspart, wäre gegen die
Constitution nicht prüfbar. Die Grenze, die eingehalten wird: **keine** Klassennamen, Signaturen,
Paketpfade oder Bibliotheken als Vorschrift — die stehen erst im Plan.

**Zu SC-008.** Das Kriterium ist absichtlich als „die vorhandenen Tests der Kampfpipeline bleiben
unverändert grün" formuliert und nicht als neue Zusicherung. Der Austausch der Schadenserlaubnis ist
genau der Vorgang, für den B05 seinen Quelltest angelegt hat; ein angepasster Test wäre kein
Nachweis, sondern dessen Umgehung.

**Zehn Punkte sind an `/plan` übergeben** und stehen im Abschnitt *Offene Punkte für `/plan`* — sie
sind Planungsfragen, keine offenen Spezifikationsfragen: der Ort der Startwarnung, der Schnitt
zwischen Domäne und Plattform bei der Geometrie, das Neubewerten aller Anwesenden ohne wiederkehrende
Aufgabe, die Form des Schutzkern-Ereignisses, die Ausführung der Messung (die Lasttestfrage selbst ist
mit ADR-031 entschieden) und der Eingriff in B05s Todesgrund-Enum — dazu vier neue aus `/clarify`:
das Schreiben von ADR-032, die Ablage der Freischaltungen (eigener Aggregattyp oder Anhang am
Charakter), das Abfangen des Rechtsklicks ohne Prüfung aller Kristalle bei jedem Klick, und was
passiert, wenn mehr Kristalle existieren als in ein Fenster passen.

**Was `/plan` zu prüfen hatte, und was dabei herauskam.** Die Frage war, ob FR-050b haltbar ist —
damals formuliert als „Buchung und Versetzung müssen zusammen gelten". **`research.md` R1 hat sie
widerlegt:** die Buchung liegt in B08b, die Versetzung in Paper, eine gemeinsame Transaktion gibt es
nicht, und B08bs Vertrag schliesst eine Reservierung ausdrücklich aus. Zusagbar ist die Wirkung —
abbuchen, versetzen, bei Fehlschlag mit eigenem Grund zurückbuchen, alles im selben Tick. FR-050b ist
entsprechend neu gefasst, FR-050d bis FR-050f und SC-020a kamen hinzu, und `WAYPOINT_REFUND` wurde
der zweite neue Buchungsgrund. Bewacht von T098.

Es bleibt die einzige Stelle des Blocks, an der ein Fehler Spielern direkt Besitz nimmt.

**Nach `/analyze` (2026-08-23)** kam ein kritischer Befund hinzu: die Prüfung, ob die Weltangabe einer
Zone eine existierende Welt benennt, war beim Umschreiben von FR-051c verschwunden — sie hatte an der
falschen Anforderung gehangen. Sie steht jetzt als **FR-002a** bei FR-002. Ebenfalls ergänzt:
**FR-065d** (die Levelbänder decken 1 bis zur Maximalstufe lückenlos ab — T030 prüfte das schon,
ohne dass eine Anforderung es aussprach), sowie Aufgaben für FR-058 und FR-060, die keine hatten.
