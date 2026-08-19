# Phase 0 — Recherche: B04 · Attribut- & Stat-Engine

**Feature**: `specs/004-stat-engine` | **Datum**: 2026-08-20

Sieben Entwurfsentscheidungen. Die fünf Produktfragen sind bereits in `/clarify` geklärt und stehen
im Abschnitt „Clarifications" der Spec; hier stehen ausschließlich Umsetzungsfragen.

---

## E1 · Wie werden acht Attributwerte je Träger gehalten?

**Entscheidung**: Ein geschlossener Aufzählungstyp `Attribute` mit acht Konstanten. Werte,
Flat-Summen und Prozent-Summen liegen jeweils in einem `double[Attribute.values().length]`,
indiziert über `ordinal()`. Der Schnappschuss hält eine private Kopie dieses Arrays und gibt Werte
ausschließlich über `get(Attribute)` heraus.

**Begründung**: Bei 200 Spielern plus bis zu 800 Mobs ist jede Map-Abfrage im Rechenpfad ein
Vielfaches der eigentlichen Rechnung, und jedes `Double` ist eine Allokation, die der Garbage
Collector im Tick bezahlt (Constitution II). Ein Array über `ordinal()` kostet einen Indexzugriff
und nichts sonst. Der geschlossene Satz wurde in `/clarify` Frage 3 ausdrücklich gewählt: ein
neuntes Attribut ist eine Konstante mehr plus ein Konfigurationseintrag, und die Berechnung merkt
davon nichts, weil sie ohnehin über `Attribute.values()` läuft.

Die Kapselung des Arrays im Schnappschuss ist nicht kosmetisch. FR-020 sagt „unveränderlich" zu;
ein herausgegebenes Array wäre eine Zusage, die jeder Aufrufer versehentlich brechen kann.

**Alternativen**:

- *`EnumMap<Attribute, Double>`* — lesbarer, aber Boxing bei jedem Zugriff und eine Allokation je
  Eintrag. Bei 1000 Trägern × 8 Werten ist das messbar, und zwar dort, wo es am teuersten ist.
- *`record StatSnapshot(double health, double defense, …)` mit acht benannten Feldern* — schnellste
  Variante und angenehm zu lesen, aber sie macht aus dem generischen Modell acht Sonderfälle. Der
  Blocksteckbrief verlangt ausdrücklich das Gegenteil, und jede spätere Erweiterung um
  Sekundärwerte (ADR-008) würde jede Signatur anfassen.
- *Registratur zur Laufzeit* — in `/clarify` Frage 3 verworfen.

---

## E2 · Vollständige Neuberechnung oder inkrementelles Fortschreiben?

**Entscheidung**: Bei jeder Neuberechnung wird aus **allen** verbliebenen Quellen vollständig neu
summiert. Ein entfernter Beitrag wird nie durch Subtraktion herausgerechnet.

**Begründung**: SC-004 verlangt, dass 1000 Rundläufe exakt beim Ausgangswert enden. Gleitkomma-
Addition ist nicht assoziativ; `(a + b) − b` ist im Allgemeinen nicht `a`. Inkrementelles
Fortschreiben würde bei jedem Ablegen eines Ausrüstungsteils einen winzigen Rest hinterlassen, der
sich über eine Spielsitzung aufsummiert und in keinem Test auffällt, der nur einen Rundlauf prüft.
Vollständiges Neusummieren macht die Eigenschaft strukturell wahr statt geprüft: dieselbe
Quellenmenge ergibt bitgleich dasselbe Ergebnis, und das erfüllt FR-016 (Reihenfolgeunabhängigkeit)
gleich mit — sofern die Summierung eine feste Reihenfolge hat, siehe E3.

Der Preis ist gering: 20 Quellen mit je wenigen Beiträgen ergeben rund 160
Gleitkomma-Operationen — deutlich unter einer Mikrosekunde und nur bei tatsächlicher Änderung.

**Alternativen**:

- *Inkrementelles Fortschreiben von Flat- und Prozentsummen* — schneller im Einzelfall, aber
  driftbehaftet (siehe oben) und mit deutlich mehr Buchführung, weil jede Quelle ihren eigenen
  Beitrag exakt kennen müsste, um ihn zurückzunehmen.
- *`BigDecimal` statt `double`* — driftfrei, aber Allokation je Operation und rund zwei
  Größenordnungen langsamer. Für Spielwerte, die ohnehin auf halbe Herzen gerundet angezeigt
  werden, ist das ein Preis ohne Gegenwert.

---

## E3 · Wie wird die Reihenfolgeunabhängigkeit aus FR-016 sichergestellt?

**Entscheidung**: Quellen werden je Träger in einer `LinkedHashMap<SourceId, ModifierSet>` gehalten
und beim Summieren in einer festen, von der Einfügereihenfolge unabhängigen Reihenfolge durchlaufen:
sortiert nach `SourceKind`, dann nach dem Schlüssel der Quellen-ID.

**Begründung**: Zwei Spieler mit derselben Ausrüstung müssen dieselben Werte haben, auch wenn sie
die Teile in unterschiedlicher Reihenfolge angelegt haben. Ohne feste Summierreihenfolge liefert
Gleitkomma-Arithmetik in seltenen Fällen unterschiedliche letzte Stellen — was sich als „mein
Schaden ist 0,0001 niedriger als deiner" bemerkbar macht und nicht reproduzierbar ist. Die
Sortierung kostet nur bei einer Neuberechnung, nicht bei jedem Zugriff.

**Alternativen**:

- *`HashMap` und Iteration in Hash-Reihenfolge* — die Reihenfolge hängt an den Hashwerten und ist
  damit stabil genug, aber nicht *garantiert* stabil über Java-Versionen. Eine Zusicherung, die man
  nicht selbst kontrolliert, ist keine.
- *Sortieren erst beim Vergleich in Tests* — verschiebt das Problem in die Produktion.

---

## E4 · Wie entsteht „genau eine Neuberechnung je Tick" ohne globale Aufgabe?

**Entscheidung**: Jeder Träger hat ein Feld `recalcPending`. Die erste Änderung setzt es und plant
über `Scheduler.runSyncOnEntity(entityRef, …)` genau eine Aufgabe auf genau diesem Träger. Jede
weitere Änderung davor findet das Feld gesetzt und tut nichts. Die Aufgabe rechnet, löscht das
Feld und veröffentlicht das Ereignis. Zusätzlich gibt es `recalculateNow(holder)` für den Ladepfad
und für Träger ohne Entität.

**Begründung**: Der naheliegende Weg — eine Merkliste plus ein serverweiter Durchlauf am Tick-Ende
— verstößt gleich zweifach gegen die Constitution: er braucht eine globale, wiederkehrende Aufgabe
(Prinzip I verbietet den globalen Scheduler ausdrücklich und ADR-007 will den Folia-Pfad offen
halten), und er läuft in jedem Tick an, auch wenn nichts zu tun ist (Prinzip II). Die
entitätsgebundene Einmalaufgabe erreicht dasselbe Ergebnis — N Änderungen in einem Tick ergeben
eine Neuberechnung — und kostet in einem Tick ohne Änderung buchstäblich nichts, weil keine
Aufgabe existiert.

Papers `EntityScheduler.run` führt die Aufgabe zu Beginn des Folgeticks im Kontext der Entität aus.
Das verschiebt das Ergebnis um höchstens einen Tick, was FR-021 ohnehin bereits zugesteht
(„laufende Vorgänge rechnen mit dem Stand ihres Auslösezeitpunkts") und im Edge-Case-Abschnitt der
Spec ausdrücklich festgehalten ist. Für den einen Fall, in dem das nicht reicht — die Freigabe
eines Spielers nach dem Laden (FR-019b) — steht der sofortige Weg bereit.

**Alternativen**:

- *Serverweiter Durchlauf am Tick-Ende* — siehe oben, verstößt gegen I und II.
- *Sofort rechnen bei jeder Änderung* — in `/clarify` Frage 5 verworfen: sechs Neuberechnungen je
  Ausrüstungssatz und 1200 statt 200 bei einer Anmeldewelle.
- *Aufrufer klammert selbst* — ebenfalls dort verworfen: jeder spätere Block müsste daran denken,
  und ein Vergessen fällt nicht auf.

---

## E5 · Wo werden die Ressourcenstände gespeichert?

**Entscheidung**: Eigene Tabelle `rpg.character_stats` mit `character_id` als Primärschlüssel und
Fremdschlüssel auf `rpg.character`, eigener Aggregattyp `CHARACTER_STATS`, eigenes Repository,
eingehängt in B02s `FlushCycle` **nach** `CHARACTER`. Gelesen wird im Bündelladen von B03, in
derselben Verbindung und derselben Transaktion.

**Begründung**: Die Alternative — zwei Spalten an `rpg.character` anhängen — sieht kleiner aus,
koppelt aber zwei Blöcke an derselben Zeile: B03s `JdbcCharacterRepository` müsste B04s Felder
kennen und mitschreiben, und beide Blöcke teilten sich einen Revisionszähler. Damit wäre jede
spätere Änderung an B04s Werten eine Änderung an B03s Schreibpfad. Eine eigene Tabelle behält die
Blockgrenze aus Prinzip III und fügt sich in das bereits etablierte Muster ein: B02 registriert
`PLAYER_STATE`, B03 `CHARACTER`, B11 `ITEM_INSTANCE` — jeweils mit eigenem Repository und eigener
Position in der Schreibreihenfolge.

Die Fremdschlüsselordnung ist der Grund für die Position nach `CHARACTER`: ein Ressourcensatz kann
erst geschrieben werden, wenn sein Charakter existiert — dieselbe Überlegung, die B03 für
`CHARACTER` zwischen `PLAYER_STATE` und `ITEM_INSTANCE` angestellt hat.

Gespeichert werden ausschließlich die beiden Rohwerte. Maxima, Endwerte und Schnappschüsse werden
nie geschrieben: sie sind abgeleitet und entstehen beim Laden neu. Das ist dieselbe Regel, die
ADR-004 für Items zieht — nur so bleibt Rebalancing möglich, ohne bestehende Spielerdaten
anzufassen (Prinzip IV).

**Alternativen**:

- *Zwei Spalten an `rpg.character`* — siehe oben, koppelt die Schreibpfade zweier Blöcke.
- *Ressourcen im `player_state`-JSON* — verlagert die Werte auf Account- statt Charakterebene und
  widerspricht ADR-011.
- *Gar nicht speichern, beim Anmelden auffüllen* — in `/clarify` Frage 1 verworfen.

---

## E6 · Wie kommen die Ressourcenstände rechtzeitig in den Träger?

**Entscheidung**: `SessionBundle` wird um `List<CharacterResources>` erweitert, `SessionBundleLoader`
um einen vierten `SELECT` auf derselben Verbindung. B04 hängt sich an den bestehenden Ladepfad,
statt einen eigenen zu eröffnen.

**Begründung**: FR-019b verlangt einen berechneten Träger *vor* der Freigabe des Spielers. B03 lädt
im asynchronen Vorlade-Ereignis, also bevor überhaupt ein Spielerobjekt existiert — das ist der
einzige Zeitpunkt, der früh genug ist. Ein eigener Ladevorgang danach hieße: der Spieler steht
für mindestens eine Runde mit falschem Leben in der Welt.

Die Erweiterung ist kein Sonderfall: `SessionBundle` trägt bereits `ItemInstance`, also Daten, die
B11 gehören. Der Bündellader ist als *der eine* Ladepfad angelegt, nicht als B03-Privatbesitz.
Sollte ein dritter Block dasselbe brauchen (B06 und B07 werden), lohnt sich dort die
Verallgemeinerung zu einer Liste von Beitragslesern — jetzt wäre sie Aufwand ohne zweiten Nutzer.

**Alternativen**:

- *Eigener Ladevorgang nach dem Bereitwerden* — siehe oben, sichtbar falscher Zustand.
- *Sofort eine Beitragsleser-Schnittstelle einführen* — richtig ab B06, verfrüht bei einem Nutzer.

---

## E7 · Wie wird die Vanilla-Anzeige gesetzt, ohne Vanilla dagegenarbeiten zu lassen?

**Entscheidung**: `GENERIC_MAX_HEALTH` wird einmalig fest auf 20 gesetzt. Die angezeigte Gesundheit
ergibt sich aus `max(kleinster Schritt, aktuell / maximal × 20)`, solange das aktuelle Leben größer
null ist. Zusätzlich schaltet B04 die Gameregel `naturalRegeneration` ab und hält den Sättigungswert
fest; beides über einen schmalen Wächter in `rpg-platform`, der ausschließlich Regenerations- und
Sättigungsereignisse abfängt und **keine** Schadensereignisse.

**Begründung**: Ohne den Wächter heilt Vanilla die Anzeige, die B04 gerade gesetzt hat, sichtbar
wieder hoch — die Herzleiste zeigt dann Werte, die mit dem tatsächlichen Leben nichts zu tun haben.
Das wurde in `/clarify` Frage 2 entschieden und ist in FR-030a und FR-030b festgehalten: der
Wächter ist eng gezogen, das Umlenken echter Schadensquellen (Fall, Feuer, Lava, Void) gehört zu
B05 und wird hier ausdrücklich nicht angefasst.

Die Untergrenze bei der Anzeige (FR-031) ist kein Schönheitsfehler: ohne sie zeigt ein Spieler mit
0,4 % Restleben null Herzen, sieht sich also tot, während er noch lebt. Papers kleinster
darstellbarer Schritt ist ein halbes Herz, also 0,5 Gesundheitspunkte.

Kein Reflection, kein NMS: alle drei Vanilla-Attribute und die Gameregel sind öffentliche
Paper-API (Prinzip VI).

**Alternativen**:

- *Gesundheitsanzeige über ein Scoreboard oder die Bossbar statt der Herzleiste* — gehört zu B13
  und ändert nichts daran, dass die Herzleiste sonst falsche Werte zeigt.
- *Vanilla-Regeneration über einen Schadensereignis-Filter neutralisieren* — wäre ein Vorgriff auf
  B05 und würde die Blockgrenze auflösen.
- *`GENERIC_MAX_HEALTH` mitwachsen lassen statt auf 20 festzunageln* — widerspricht ADR-003 direkt
  und bringt bei 2000 Leben eine Herzleiste über den halben Bildschirm.

---

## Zusammenfassung der Auswirkungen auf die Erfolgskriterien

| Kriterium | Getragen von |
|---|---|
| SC-001 (eine Neuberechnung je Wechsel) | E4 |
| SC-002 / SC-003 (Tick-Budget) | E1, E2, E4 |
| SC-004 (kein Drift) | E2, E3 |
| SC-005 (serverfrei geprüft) | E1 bis E3 liegen vollständig in `rpg-core` |
| SC-006 / SC-007 (Formeln und Caps) | E1, E2 |
| SC-008 (Herzleiste) | E7 |
| SC-009 (Balancing ohne Code) | Konfigurationsschema, siehe `contracts/stat-config.md` |
| SC-010 (kein Speicherrest) | E4 — ohne globale Merkliste gibt es nichts, was einen Träger überlebt |
| SC-011 (Stand bleibt erhalten) | E5, E6 |
| SC-012 (kein Zugriff je Ereignis) | E5 — geschrieben wird ausschließlich über B02s Write-Behind |
