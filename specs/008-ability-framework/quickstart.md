# Quickstart · B08 nachprüfen

Wie sich die Zusagen dieses Blocks tatsächlich nachvollziehen lassen — was sich im Test zeigt und was
einen laufenden Paper-Server braucht. Kein Implementierungscode; der gehört in `tasks.md`.

---

## Voraussetzungen

- Java 25, Gradle-Wrapper aus dem Projekt
- Docker läuft (Testcontainers braucht eine echte PostgreSQL-Instanz — Prinzip VII verbietet Mocks
  gegen die Datenbank)
- Für die Punkte unter „Am laufenden Server": ein Paper-Server 26.2 mit dem gebauten Plugin

---

## Der ganze Durchlauf

```bash
./gradlew test
```

Grün heißt: Konfiguration bindet, Zielauswahl stimmt, Cooldown- und Manaarithmetik stimmen, die
Persistenz überlebt einen Neustart, und der Block ist im Plugin verdrahtet.

```bash
./gradlew :rpg-plugin:test --tests '*FullBootstrapTest*'
```

**Der wichtigste Einzeltest des Blocks.** Ein Modul, das grüne eigene Tests hat und nicht verdrahtet
ist, ist wirkungslos (ADR-012). Der Test beweist, dass Registry-Eintrag, Listener und
Sitzungsanhang tatsächlich vorhanden sind.

---

## Die Zusagen einzeln

### SC-001 — eine neue Fähigkeit rein per Konfiguration

Das Akzeptanzkriterium des Steckbriefs. Der Test legt eine Fähigkeit an, die **nur im Test existiert**,
aus vorhandenen Primitives, und löst sie aus.

```bash
./gradlew :rpg-core:test --tests '*ConfigOnlyAbilityTest*'
```

Er zählt als bestanden, wenn dafür **keine** Quelldatei angefasst wurde. Deshalb ist er in Phase 2
auch der **letzte** Schritt und nicht der erste: entstünde die Fähigkeit vor der Maschine, prüfte er
nichts.

### SC-002 — 100 Flächenfähigkeiten im Tick-Budget

```bash
./gradlew :rpg-core:test --tests '*AbilityBudgetTest*'
```

Misst, statt zu behaupten. Aufbau wie `CombatBudgetTest` in B05: eine große Zahl Kandidaten im
Radius, dann die Zusage, dass die Zielobergrenze greift und die Zeit im Rahmen bleibt.

### SC-003 — Mana, Cooldown und globale Sperre werden serverseitig durchgesetzt

```bash
./gradlew :rpg-core:test --tests '*AbilityRuntimeTest*'
```

Drei Fälle mal 1000 Versuche, jeder mit null Durchbrüchen. Wichtig ist der zweite Teil jeder Zusage:
eine abgewiesene Auslösung verbraucht **nichts** — kein Mana, kein Cooldown, keine Sperre.

### SC-004 — der Cooldown überlebt das Abmelden

```bash
./gradlew :rpg-persistence:test --tests '*AbilityStatePersistenceTest*'
```

Braucht Docker. Löst aus, schreibt, lädt neu, vergleicht die Restzeit. Und die Gegenrichtung: ein
abgelaufener Cooldown wird beim Laden **verworfen**, nicht geladen — sonst wüchse die Tabelle mit
jedem Kampf.

### SC-005 — keine wiederkehrende Aufgabe je Spieler

```bash
./gradlew :rpg-core:test --tests '*NoTaskPerPlayerTest*'
```

Die Zusage mit dem größten Schadenspotential, wenn sie bricht, und sie ist zählbar: die Zahl der
geplanten Aufgaben entspricht der Zahl der **laufenden Casts** und sonst nichts. Weder ein Cooldown
noch die Regeneration erzeugt eine.

Aufbau wie `AttackWindowTest` in B05 (*„nothing is ever scheduled — the whole point of a timestamp"*):
ein zählender Scheduler in der Testfixtur, und die Zusage ist eine Zahl.

### SC-006 — jede Klasse hat sechs Fähigkeiten

```bash
./gradlew :rpg-plugin:test --tests '*ShippedAbilityConfigTest*'
```

Gegen die **ausgelieferte** `abilities.yml` und `classes.yml`, nicht gegen eine Fixtur. Vier aktive,
zwei passive, genau eine Unique je Klasse — und die Unique darf passiv sein (ADR-022).

### SC-008 — eine fehlerhafte Konfiguration verhindert den Start

```bash
./gradlew :rpg-core:test --tests '*AbilityConfigValidationTest*'
```

Je Prüfung aus [ability-config.md](./contracts/ability-config.md) ein Fall, und geprüft wird die
**Meldung**. Ein Betreiber, der achtzehn Fähigkeiten bearbeitet, muss erfahren, welche gemeint ist —
„ungültige Konfiguration" schickt ihn durch alle achtzehn.

### SC-009 — ein unterbrochener Cast hinterlässt nichts

```bash
./gradlew :rpg-core:test --tests '*CastInterruptionTest*'
```

1000 Versuche, keine Manadifferenz, kein Cooldown. Alle sechs Abbruchgründe aus dem Datenmodell.

---

## Am laufenden Server

Fünf Dinge, die kein Test zeigt, weil sie einen echten Client brauchen. Sie gehören in `tasks.md` als
ausdrückliche Validierungsläufe — nach dem Muster, mit dem B07 seine vier Serverpunkte offengelassen
hat.

1. **Der Rechtsklick löst aus, der Linksklick nicht.** Beides mit einem Fähigkeits-Item in der Hand.
   Der zweite Teil ist der wichtigere: ein Linksklick auf ein Monster mit dem Ziegenhorn darf **keinen
   Nahkampfschaden** machen (FR-054).
2. **Die Hotbar sieht richtig aus.** Waffe auf 0, freigeschaltete Fähigkeiten auf 1 bis 4 in der
   Reihenfolge ihrer Freischaltstufe, Marker ab 5. Ein nicht freigeschalteter Slot bleibt **leer** und
   lässt sich nicht befüllen.
3. **Der Doppelsprung des Mage.** Zweimal Springen in der Luft trägt, ein drittes Mal vor Bodenkontakt
   nicht. Der Fall ist verlangsamt.
4. **Die Regeneration ist spürbar und im Kampf schwächer.** Verletzt aus dem Kampf gehen und die
   Uhr mitlaufen lassen: rund 50 Sekunden bis voll. Im Kampf deutlich langsamer, aber nie null.
   *Das ist zugleich der erste Beweis überhaupt, dass ein Spieler heilt* — vor ADR-023 heilte er
   nicht (ADR-013 hatte die Vanilla-Regeneration abgeschaltet, ohne Ersatz).
5. **Der Cast-Abbruch fühlt sich richtig an.** Manaschild anfangen, Schaden nehmen, und das Mana ist
   unverändert.

---

## Wenn etwas rot ist

| Symptom | Wahrscheinliche Ursache |
|---|---|
| Start bricht mit „unknown ability id" ab | eine Klassenbindung in `classes.yml` nennt eine ID, die `abilities.yml` nicht definiert (V25) |
| Start bricht mit „kind mismatch" ab | Bindung und Definition sind sich über aktiv/passiv uneins (V26) |
| Rogue-Loadout wird abgewiesen | die Invariante `unique ⇒ ACTIVE` in `AbilityBinding` ist noch nicht entfernt — Second Life ist passiv und unique (ADR-022) |
| `NoDatabaseAccessPerGameEventTest` rot | eine der drei Registrierungen für `CHARACTER_ABILITIES` fehlt (ADR-015) |
| `FullBootstrapTest` rot, Modultests grün | genau der Fall, für den ADR-012 existiert: gebaut, aber nicht verdrahtet |
| Testcontainers startet nicht | Docker läuft nicht |
