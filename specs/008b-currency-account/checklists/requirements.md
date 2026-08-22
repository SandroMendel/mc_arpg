# Specification Quality Checklist: B08b · Währung & Konto

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-22
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

Alle Punkte erfüllt. Die Spec ist bereit für `/speckit-plan`.

### Durchlauf 1 — 2026-08-22

Zwei Formulierungen korrigiert:

1. **FR-034 nannte „Javadoc"** — ein Implementierungsdetail in einer Anforderung. Ersetzt durch
   „Dokumentation" (heute FR-055).
2. **FR-009 begründete über das Vorzeichen des Betrags**, was eine Signaturaussage ist. Umformuliert
   auf die fachliche Aussage: die Richtung ergibt sich aus Gutschrift oder Abbuchung.

Drei `[NEEDS CLARIFICATION]`-Marker blieben offen und wurden dem Auftraggeber vorgelegt.

### Durchlauf 2 — 2026-08-22, nach den Antworten

Alle drei Marker aufgelöst. Zwei davon als reine Antwort, der dritte als **Zuschnittsänderung**:

| Frage | Antwort | Wirkung auf die Spec |
|-------|---------|----------------------|
| Startguthaben | Konfigurierbar, Standard null | FR-011, ein Szenario in US1 |
| Verlust beim Tod | Kein Verlust | FR-012 als Verbot formuliert, ein Szenario in US1 |
| Historie | Dauerhafter Verlauf **und** Admin-Eingriffe; Coins fallen und werden aufgehoben | Neue US2 (Drop), neue US3 (Verlauf & Eingriff), FR-019 bis FR-046, SC-010/SC-011 |

Die dritte Antwort hat den Umfang deutlich vergrössert: aus 38 wurden 59 Anforderungen, aus fünf
Nutzergeschichten sechs, und der Block setzt nun **Objekte in die Welt**, was er zuvor nicht tat.

### Erneut geprüft und bewusst so belassen

- **`cost: { coins: 500 }` und `classes.yml` erscheinen wörtlich** in US4 und FR-047/FR-049. Das ist
  kein Implementierungsdetail, sondern **bestehende, ausgelieferte Konfiguration** aus B07, auf die
  sich der Block bezieht — ohne den Namen wäre die Anforderung nicht überprüfbar.
- **SC-006 nennt „≤ 5 ms Tick-Budget"** und **SC-005 „null Datenbankzugriffe"**. Technisch klingend,
  aber beides sind wörtliche Erfolgskriterien aus Prinzip II der Constitution und damit fachliche
  Vorgaben des Projekts, keine Implementierungsvorschriften.
- **FR-041 verweist auf das Audit-Log aus B02.** Kein Implementierungsdetail, sondern eine
  ausgelieferte Blockschnittstelle — B14s Architekturvorgabe verlangt ausdrücklich, dass jeder
  Eingriff an Spielerdaten dort landet.

### Durchlauf 3 — 2026-08-22, nach `/clarify`

Drei Klärungen aufgenommen; **alle 16 Punkte bleiben erfüllt**, keine Regression.

| Frage | Antwort | Wirkung |
|-------|---------|---------|
| Startguthaben rückwirkend? | **Nein** — Gutschrift bei der Erstellung, ohne Zeile gilt null | FR-011a–c, Szenarien 7/7a/7b, `STARTING_BALANCE` |
| Mindestbeteiligung für Coins? | **Nein** — rein anteilig wie die Erfahrung | FR-024a |
| Deckelung erreicht? | **Ältesten Haufen gutschreiben und abräumen** | FR-030a–d, Szenarien 7a/7b, `PILE_CASHED_IN` |
| *(zusätzlich vom Auftraggeber)* Wer sieht einen Haufen? | **Nur der Berechtigte** | FR-027a, Szenarien 5/5a |

**Eine Annahme des Auftraggebers musste korrigiert werden:** Die XP-Regel aus B06 kennt **keine**
Mindestbeteiligung — sie teilt rein proportional (Szenario 2: 60 % ergeben 60 XP, 40 % ergeben
40 XP), und ein Party-Mitglied ohne jeden Schaden erhält in Reichweite trotzdem seinen Anteil
(Szenario 5). Eine Schwelle für Coins wäre deshalb keine Spiegelung der XP-Regel gewesen, sondern
eine Abweichung von ihr. Nach der Klarstellung wurde die anteilige Verteilung bestätigt.

**Zwei Punkte, die vorher als „unentschieden" markiert waren, sind damit entschieden:** U1
(rückwirkendes Startguthaben) und U3 (Schweigen bei erreichter Deckelung). Beide waren als
Entscheidungen und nicht als Lücken eingeordnet — die Einordnung hat sich bestätigt.

### Durchlauf 4 — 2026-08-22, nach `/implement`

**145 von 150 Aufgaben erledigt.** Was die Umsetzung an der Spezifikation korrigiert hat — jeweils,
weil die Wirklichkeit anders aussah als angenommen:

| Fund | Wirkung |
|------|---------|
| `balanceOf` als blankes `long` hätte **„offline" mit „pleite"** verwechselt | Vertrag auf `OptionalLong` geändert, plus `balanceOrZero` für Hot Paths |
| Das Config-Layer kennt **keinen Dauer-Typ** | `despawn: 120s` → `despawn-seconds: 120`, `retention` → `retention-days` |
| Die Mob-Konvention ist `by-type` mit **GROSSSCHREIBUNG** | `per-mob` → `by-type`, wie `progression.yml` und `combat.yml` |
| `Item` hat **keinen Verfalls-Setter** (R1c) | Vorabalterung über `setTicksLived`; harte Obergrenze von 300 s, die das Schema kennt und begründet ablehnt |
| `CoinDropPlanner` zog sich eine **B04-Abhängigkeit** heran | Ein-Methoden-Schnittstelle `CharacterLookup` statt `StatEngine` |

**Zwei Funde, die keine Spec-Änderung waren, aber festgehalten gehören:**

1. **MockBukkit meldet Nicht-Implementiertes als *skipped*, nicht als Fehler.** Sechs Tests liefen
   still durch, und der Build sagte `SUCCESSFUL`. `Item.setOwner` und `Entity.setVisibleByDefault`
   sind dort nicht implementiert. Gelöst über eine benannte Naht (`PilePlatform`): der Test beweist
   jetzt, **was wir verlangen und wem gegenüber** — dass Paper es befolgt, zeigt der echte Server.
2. **Ein echter „tut so, als ob"-Fehler.** `pruneNow` meldete „1 Zeile gelöscht", und die Zeile war
   danach noch da: der Pool gibt Verbindungen mit `autoCommit = false` heraus. Der Rückgabewert log.
   Gefunden mit einer Wegwerf-Sonde, die direkt in die Tabelle sah — die erste Vermutung (Kaskade
   defekt) war falsch.

### Was die Spec bewusst offen lässt

Drei Punkte stehen unter *Offene Punkte für `/plan`* und blockieren die Spezifikation nicht:

1. Der **Schichtbruch beim Admin-Kommando** — eine Abweichung von der Architekturvorgabe, die nach
   der Governance-Regel eine begründete Ausnahme als ADR verlangt.
2. Ob der Coin-Haufen den Block **lasttestpflichtig** macht. Prinzip VII nennt B05 und B10; mit einem
   Wurfobjekt je Kill bei 800 Mobs erfüllt dieser Block dasselbe Kriterium, ohne genannt zu sein.
3. Der **Standardwert der Aufbewahrungsdauer** des Verlaufs — Betriebssache, mit Blick darauf, dass
   diese Tabelle binnen Wochen die grösste des Projekts wird.
