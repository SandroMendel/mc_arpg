# Specification Quality Checklist: B12 · Statistiken & Leaderboards

**Purpose**: Validate specification completeness and quality before proceeding to planning
**Created**: 2026-08-28
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

## Umfang

6 User Stories · 94 funktionale Anforderungen · 23 Erfolgskriterien · 23 Edge Cases.
Nummerierung geprüft: FR-001 bis FR-070 lückenlos, dazu 23 Nachträge aus der zweiten und dritten
Klärungsrunde mit Suffix (FR-007a–e, FR-014a–f samt FR-014c1/c2, FR-016a, FR-032a, FR-038a,
FR-050a–f, FR-053a) nach dem Muster von FR-026a–d in B11. SC-001 bis SC-023 lückenlos.

## Notes

### Bewusste Abweichung: benannte Nähte statt Technologieneutralität

Die Spec nennt konkrete Stellen des Bestands — `player_statistic_daily`, `StatisticsRepository`,
`REPOINT_STATISTICS`, `DamageShare`, `SummonEffect`, Materialized Views. Das ist dieselbe
bewusste Abweichung wie in B10 und B11 und aus demselben Grund: **B12 baut sein Fundament nicht,
sondern schließt es an.** Ohne die namentliche Nennung ließe sich FR-002 („keinen zweiten
Speicherort für dieselben Zahlen anlegen") nicht prüfen, und genau dieser Verstoß — eine zweite
Statistiktabelle neben der seit B02 vorhandenen — ist bei diesem Block der wahrscheinlichste.

Die Materialized Views sind zudem keine Wahl dieser Spec, sondern eine **Architekturvorgabe des
Blockdokuments**: *„Leaderboards werden über Materialized Views mit periodischem Refresh
bereitgestellt und zusätzlich im Speicher gecacht."* Sie wegzulassen hätte die Vorgabe verloren,
nicht die Spec verbessert.

### Klärungssitzung 2026-08-28 — vier Fragen, alle beantwortet

- **Q1 (Metriken)** → voller Umfang: Level + XP, Coins, Mob-Kills je Art, Tode je Verursacher-Art,
  Bosskills, Spielzeit, höchster Schaden; je in vier Zeiträumen (FR-006 bis FR-018, FR-024)
- **Q2 (öffentlich/privat)** → alles öffentlich außer der Aufschlüsselung der eigenen Tode nach
  Verursacher (FR-036 bis FR-038, SC-006)
- **Q3 (Saison)** → Saison mit Abschluss und Belohnung, Anspruch statt Ausschüttung
  (FR-048 bis FR-058, SC-008, SC-009)
- **Q4 (Anzeige)** → GUI-Fenster über Commands **und** Hologramm im Hub
  (FR-041 bis FR-047, FR-059 bis FR-064)

### Zweite Klärungsrunde 2026-08-29 — fünf Fragen, alle beantwortet

- **Q5 (Party und der Kill)** → jedes Mitglied in Reichweite bekommt den Kill gezählt
  (FR-007, FR-007a bis FR-007c, SC-014)
- **Q6 (Untätigkeit)** → die Rangliste zählt die **aktive** Zeit mit Pause bei Untätigkeit; die
  gesamte Onlinezeit wird zusätzlich erfasst, bleibt aber privat (FR-014a bis FR-014c, FR-038a,
  SC-015)
- **Q7 (Spielzeit je Region)** → die Zeit wird je Zone aufgeschlüsselt; die Aufteilung ist
  **komplett privat**, öffentlich ist allein die aktive Gesamtsumme (FR-014d bis FR-014f,
  FR-036 bis FR-038, SC-016, SC-017)
- **Q8 (Datenmenge)** → unverändert hinnehmen, kein Verdichten (Annahme „Datenmenge")
- **Q9 (nicht abgeholte Belohnung)** → bleibt unbegrenzt bestehen (FR-053a, SC-018)

**Die Party-Antwort hat eine Aussage der ersten Runde umgedreht.** Die Fassung vom 2026-08-28
schrieb „die Beute rotiert innerhalb einer Party — der Kill nicht" und ließ dabei offen, wer den
Kill in einer Party überhaupt bekommt. Jetzt bekommt ihn **jeder in Reichweite**, aus demselben
Grund, aus dem B06 Erfahrung und Coins verteilt. Der widersprüchliche Satz ist ersetzt, nicht
ergänzt. Der Preis steht als FR-007c in der Spec: die Summe aller Kill-Zähler übersteigt die Zahl
der getöteten Kreaturen, und die Anzeige muss die Metrik als **Beteiligung** benennen.

**Die Sichtbarkeitsregel ist keine Ausnahme mehr, sondern eine Liste.** Aus einem privaten Wert
sind drei geworden: Tode je Verursacher, gesamte Onlinezeit, Zeit je Zone. Damit ist FR-037 die
Anforderung mit der größten Wahrscheinlichkeit, beim Bauen still verloren zu gehen — SC-017 prüft
sie über alle vier Ausgabewege, das Hologramm eingeschlossen.

### Dritte Klärungsrunde 2026-08-29 — fünf Fragen, alle beantwortet

- **Q10 (Bosskill bei vielen Beteiligten)** → jeder Spieler über einer Schadensschwelle bekommt
  den Kill gezählt, Bosse eingeschlossen (FR-007, FR-007d, FR-007e, SC-019)
- **Q11 (was gilt als Aktivität)** → alles, was der Spieler auslöst (FR-014c1, FR-014c2)
- **Q12 (Klonschaden)** → zählt für den beschwörenden Spieler (FR-016a, ADR-047)
- **Q13 (welche Ranglisten)** → alle Kombinationen automatisch, keine kuratierte Liste
  (FR-032a, SC-023)
- **Q14 (was wird belohnt)** → eine einzige **Gesamtwertung** (FR-050a bis FR-050f, FR-051,
  SC-020 bis SC-022)

**Die Kill-Frage musste zweimal neu beantwortet werden.** Runde 1 sagte „der größte
Beitragende" — die Regel, nach der B11 die Beute vergibt. Runde 2 ergänzte den Party-Fall.
Runde 3 hat beides ersetzt: **jeder Beteiligte über einer Schwelle**, weil B05 Erfahrung ohnehin
nach Anteil an alle verteilt und neun von zehn Bossteilnehmern sonst nichts vorzuweisen gehabt
hätten. Der überholte Wortlaut ist an beiden Stellen ersetzt statt ergänzt — in der Spec unter
„Was sich daraus zwangsläufig ergibt", in ADR-042 als durchgestrichener Halbsatz mit Nachtrag.

**Die Gesamtwertung ist die größte Einzeländerung dieser Runde.** Aus „alle Ranglisten belohnt"
wird „zweiundzwanzig Ranglisten als Ehre, eine dreiundzwanzigste mit Preis". Das bringt eine
Gewichtung mit sich, die konfiguriert, beim Start geprüft, im Fenster nachrechenbar und mit dem
Saisonendstand eingefroren werden muss (ADR-046).

**Eine bewusste Asymmetrie zu B11** steht jetzt in der Spec: der beschworene Klon nutzt nichts ab
(B11 FR-041a), sein Schaden zählt aber für den Spieler (FR-016a). ADR-047 begründet es und wird
von beiden Seiten aus gebraucht — wer die Stellen nebeneinander liest, hält es sonst für einen
Fehler.

### Drei Funde im Code, die die Spec geformt haben

**1. Das Fundament steht seit B02 und hat nie einen Schreiber gesehen.** `player_statistic_daily`,
`StatisticsRepository`, die Write-Behind-Registrierung in `PersistenceModule` und `FlushCycle`,
dazu ein Index mit dem Kommentar *„Leaderboards (B12) sum per metric across days"*.
Produktionsaufrufe von `increment()`: null. Dieselbe Lage wie `EquipmentPurchase` vor B11.

**2. Der Schreibweg kann nur addieren.** `ON CONFLICT ... SET value = value + excluded.value` ist
exakt das, was B02s FR-007 („kein Lesen vor dem Schreiben") möglich macht. Daran zerbrechen zwei
der gewünschten Metriken: **höchster Schaden** ist ein Maximum (FR-016, FR-017 fordern einen
zweiten Schreibweg, der ebenfalls nicht liest), und **Level, XP, Coins** sind Zustände, deren
Tagessumme bedeutungslos wäre (FR-019 bis FR-023 lesen sie dort, wo sie ohnehin stehen).

**3. Die Statistik hängt am Konto, das Spiel seit ADR-011 am Charakter.** Für Zähler ist das
richtig — eine Rangliste vergleicht Menschen, nicht Rollen (FR-005). Für Zustände ist es falsch,
weshalb ein Konto-Level als höchstes Charakterlevel definiert wird (FR-021) und Coins als Summe
(FR-022). Dazu die Anonymisierung: `REPOINT_STATISTICS` erhält die Zahlen und verliert den Namen —
FR-039 zieht daraus die Konsequenz für die öffentliche Rangliste.

### Was ohne Rückfrage entschieden wurde

Refresh-Intervall (5 Minuten), Saisonlänge (Quartal), Anzahl der Plätze (10), Zeitzone (UTC, weil
die Tagesangabe es bereits ist), Empfänger einer Saisonbelohnung (Konto, eingelöst durch einen
Charakter). Alle fünf stehen als Annahme in der Spec und sind konfigurierbar — keine davon ist
eine Weiche, die sich später nur mit Umbau umlegen ließe.

### Vor `/speckit-plan` — die ADRs stehen

~~**Ein ADR ist fällig.**~~ **Erledigt: ADR-040 bis ADR-047** in `02-decisions.md`. Acht
Entscheidungen, jede mit verworfenen Alternativen; ADR-042 zusätzlich mit einem Nachtrag vom
selben Tag:

1. **ADR-040** — ein zweiter Schreibweg auf `player_statistic_daily` (Maximum neben Summe). Die
   erste Erweiterung eines B02-Fundaments durch einen Block der dritten Schicht; die Eigenschaft
   „ohne Lesen schreiben" bleibt erhalten.
2. **ADR-041** — Zustandswerte werden gelesen, nicht gespiegelt. Absage an eine zweite Wahrheit,
   Preis: eine Rangliste hat zwei mögliche Quellen.
3. **ADR-042** — ein Kill zählt für jeden Beteiligten über einer Schwelle, in der Party für jeden
   in Reichweite. Dieselbe Kollision zwischen B05 und B06, die ADR-039 für die Beute auflösen
   musste, nur an einer anderen Metrik — und sie brauchte zwei Anläufe (Nachtrag im ADR).
4. **ADR-043** — zwei Uhren für die Spielzeit und drei private Werte. Beide Uhren ohne eine
   einzige neue wiederkehrende Aufgabe.
5. **ADR-044** — die Aufschlüsselung wird in Zeilen bezahlt, nicht durch Verdichten. Der Grund ist
   B02s Aufbewahrungszusage, nicht die Größenordnung.
6. **ADR-045** — die Saisonbelohnung ist ein Anspruch ohne Verfall.
7. **ADR-046** — die Saison kürt einen Spieler, nicht zwei Dutzend Ranglisten: eine gewichtete
   Gesamtwertung, ohne Zustandswerte und ohne private Werte.
8. **ADR-047** — der Klon leistet für den Spieler, kostet ihn aber nichts. Die bewusste
   Asymmetrie zu B11 FR-041a.

**Offen für den Plan, nicht für die Spec**: wo der eingefrorene Saisonendstand und der
Belohnungsanspruch liegen (eigene Tabellen, eigene Migration), und ob sie über den
Write-Behind-Weg laufen oder als seltene, direkte asynchrone Schreibvorgänge — ein
Belohnungsanspruch entsteht viermal im Jahr, nicht tausendmal am Tag. ADR-045 benennt die Frage
ausdrücklich und überlässt sie dem Plan.

~~**Ebenfalls dem Plan überlassen**: welche Ranglisten es konkret gibt.~~ **In der dritten Runde
beantwortet**: alle Kombinationen entstehen automatisch — zweiundzwanzig Ranglisten plus die
Gesamtwertung, keine kuratierte Liste (FR-032a). ~~Was dem Plan bleibt, ist die Frage, ob
zweiundzwanzig Materialized Views ebenso viele Refreshes brauchen.~~ **Vom Plan beantwortet:**
vier Sichten, eine je Zeitraum, jede über alle Metriken (research.md R1).

**Beim Planen aufgefallen**: die Klärungsrunde rechnete mit „achtzehn" und hatte den höchsten
Schaden als eigene rankbare Metrik übersehen. Richtig sind zweiundzwanzig. Die Zahl steht in
Spec, Checkliste und ADR-046 korrigiert; an keiner Anforderung ändert sie etwas.

### Was `/speckit-analyze` gefunden hat (2026-08-29)

Ein kritischer Befund, vier hohe, fünf mittlere — alle behoben:

- **Die beiden Zustandsranglisten hatten keine einzige Aufgabe** (FR-019 bis FR-023). Level und
  Coins wären als zwei von zweiundzwanzig Ranglisten still gefehlt, und ADR-041 hätte keinen Code
  hinter sich gehabt. Neu: T075a bis T075f.
- **Bosskills waren in der Gesamtwertung doppelt gewichtet.** `boss_kills` war definitionsgemäß
  eine Teilmenge von `mob_kills.*`; wer beide gewichtet, zählt einen Bosskill zweimal. Gelöst
  durch **FR-009a**: die beiden Ranglisten sind jetzt disjunkt, ohne Sonderregel und ohne
  Änderung an der ausgelieferten Gewichtung.
- **SC-012 widersprach FR-032a** — ein Überbleibsel aus Runde 1 („entsteht allein durch
  Konfiguration"), das Runde 3 überholt hatte. Umformuliert.
- **FR-007c war nirgends eingelöst**: die Zusage, dass jede Anzeige die Kill-Metrik als
  *Beteiligung* benennt, hatte weder Message-Key noch Test. Das ist der angenommene Preis von
  ADR-042 — ohne die Beschriftung wird die Rangliste als kaputt gemeldet. Neu: T079a, T079b.
- **FR-002 hatte keinen Wächter.** „Kein zweiter Speicherort für dieselben Zahlen" stand nur in
  zwei `package-info`; B11 hat für die gleichlautende Zusage einen Architekturtest. Neu: T012a,
  dazu T012b für ADR-041.

Dazu fünf Testlücken (FR-014e, SC-005, SC-018), eine Vertragslücke (Zustandsmetriken vertrugen im
Vertrag einen Zeitraum, den es für sie nicht gibt) und eine Bezeichnerkollision (`FR-041a` meinte
in T050 eine B11-Anforderung, während B12 ein eigenes FR-041 hat).

**Offen gelassen**: die Abgrenzungen FR-068 bis FR-070 haben keine Wächtertests, und „aktive Zeit"
wechselt sich stellenweise mit „aktive Spielzeit" ab. Beides kosmetisch.
