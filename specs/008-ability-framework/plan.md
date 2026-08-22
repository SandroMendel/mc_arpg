# Implementation Plan: B08 · Fähigkeiten-Framework

**Branch**: `008-ability-framework` | **Date**: 2026-08-22 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-ability-framework/spec.md`

## Summary

B08 ist der umfangreichste Block des Projekts, und er ist es aus einem bestimmten Grund: er hat **vier
Ebenen statt einer** — Definition, Effekt-Primitives, Zielbestimmung und Laufzeit. Zehn
Entscheidungen prägen die Umsetzung.

1. **Eine Fähigkeit ist Daten, kein Objekt mit Verhalten.** Es gibt genau eine `Ability`-Klasse und je
   Primitive eine kleine, zustandslose Anwendung. Was eine Fähigkeit *tut*, steht in ihrer Liste von
   `EffectSpec`. Das ist die einzige Bauform, in der SC-001 wahr sein kann — eine neue Fähigkeit rein
   per Konfiguration. Eine Java-Klasse je Fähigkeit hätte achtzehn Klassen erzeugt und beim
   neunzehnten Eintrag wieder eine.

2. **Der Cast braucht eine verzögerte Aufgabe im Tick, und die gibt es heute nicht.** `Scheduler`
   bietet `runSyncAtLocation`, `runSyncOnEntity`, `runAsync` und `runAsyncDelayed` — **kein verzögertes
   synchrones Einzelstück**. Eine Wirkzeit ist genau das: eine einmalige Tick-Arbeit zu einem
   bestimmten späteren Zeitpunkt. B08 ergänzt deshalb `runSyncOnEntityDelayed`. Das ist eine
   Erweiterung der Abstraktion und damit ADR-pflichtig — mit demselben Muster wie ADR-010, das
   `runAsyncDelayed` für B02s Autosave ergänzt hat. Siehe [research.md](./research.md) R2, dort stehen
   auch die zwei verworfenen Alternativen.

3. **Die Regeneration rechnet ohne Ereignis und ohne Aufgabe.** Sie hält je Charakter zwei Werte: den
   Zeitpunkt der letzten Abrechnung und den Zeitpunkt, zu dem der zuletzt gesehene Kampf endet
   (`lastCombatAt + timeout`, ablesbar aus `remainingCombatTime`). Damit lässt sich jedes Intervall
   exakt in einen Kampf- und einen Ruheanteil zerlegen, **ohne** auf `CombatStateChangedEvent` zu
   warten. Das ist nicht nur eleganter, es ist notwendig: das Ereignis der *verlassenden* Flanke wird
   in der Produktion derzeit überhaupt nicht ausgelöst (R3 — ein Fund in B05, kein Problem von B08).

4. **Die Fähigkeits-Items erben die Sperre, statt eine zweite zu bauen.** `BoundItemTag` markiert seit
   B07 charaktergebundene Gegenstände, und `EquipmentLockListener` bricht `InventoryClickEvent`,
   `InventoryDragEvent`, `PlayerDropItemEvent` und `PlayerSwapHandItemsEvent` für markierte Items ab.
   Tragen die Fähigkeits-Items dieselbe Marke, ist FR-057 ohne eine Zeile neuen Sperrcode erfüllt.
   Neu ist allein, dass ein *Linksklick* mit einem Fähigkeits-Item keinen Nahkampf auslösen darf
   (FR-054) — das ist der einzige Griff in den Kampfpfad.

5. **Ein neuer Aggregattyp heißt drei Registrierungen, nicht eine.** `CHARACTER_ABILITIES` braucht den
   Enum-Wert, seinen Platz in `FlushCycle.WRITE_ORDER` (**nach** `CHARACTER`, wie jedes Kind) und die
   Repository-Verdrahtung. ADR-015 ist genau aus dem Vergessen einer dieser drei entstanden;
   `NoDatabaseAccessPerGameEventTest` prüft die Vollständigkeit als Invariante. Migration `V8_1`.

6. **Passive sind Trigger, keine Sonderfälle.** Fünf Trigger — `ALWAYS`, `ON_DAMAGE_DEALT`,
   `ON_DAMAGE_TAKEN`, `ON_KILL`, `ON_DEATH` — und jeder hängt an einem Einhängepunkt, den B05 bereits
   hat: `DamageInterceptor` auf den Stufen `MODIFIERS` und `APPLICATION` für die drei
   schadensbezogenen, das Todesereignis für `ON_DEATH`. `ALWAYS` ist der einzige, der gar keinen
   Einhängepunkt braucht: er meldet einen `ModifierSet` an und ist danach fertig. **B05 wird nicht
   erweitert** — die Einhängepunkte existieren und warten seit ihrer Spezifikation auf B08.

7. **Der Rang skaliert die Zahlen, nicht die Struktur.** Eine `EffectSpec` trägt ihren Wert für Rang 1
   plus einen Zuwachs je Rang. Damit ist der Rang eine Multiplikation beim Auslesen und kostet keinen
   zweiten Satz Definitionen. Wer ihn bezahlt, bleibt draußen (FR-065): es gibt im Projekt keine
   Währung, und eine zu erfinden wäre der Vorgriff, den Workflow-Regel 5 verbietet.

8. **Haltende Fähigkeiten sind der dritte Laufzeitzustand, und der zweiphasige Abbruch ist der Kern
   daran.** Sieben der achtzehn Fähigkeiten wirken über eine Dauer und enden per zweitem Rechtsklick
   (ADR-025). Ein Abbruch in der *Vorbereitung* erstattet und startet keinen Cooldown; das Beenden
   einer *laufenden* Wirkung behält beides. Ohne diese Trennung wäre Sofort-Abbrechen ein kostenloses
   Werkzeug. Der Zustand entscheidet das, nicht der Aufrufer.

9. **Wirkung je Sekunde entsteht aus einem Feld, nicht aus vier Primitives.** Ein Effekt bekommt ein
   optionales `interval`; damit ist `DAMAGE` mit Intervall ein DoT und `MANA_RESTORE` mit Intervall
   der Manatrank. **Alle** laufenden Intervall-Effekte laufen über **eine** serverweite Auswertung —
   nie eine je Ziel, was Prinzip II verletzt hätte und der Grund war, DoT ursprünglich abzulehnen.

10. **Warriors Wut ist ein Zähler, keine Ressource.** 0 bis 100, Aufbau bei Schaden, Zerfall nach
    Ruhefrist — und trotzdem ohne Tabelle und ohne Aufgabe, weil sich der Stand aus dem letzten Wert
    plus verstrichener Zeit ergibt. Der Attributbeitrag wird bei jedem Schadensereignis neu gesetzt;
    das ist ohnehin der einzige Moment, in dem er zählt.

**Was diesen Block groß macht, ist nicht die Laufzeit, sondern die Breite.** Sechzehn Primitives, neun
Zielbestimmungen, fünf Trigger und achtzehn Fähigkeiten sind je für sich klein. Die Reihenfolge in
Phase 2 muss das ausnutzen: erst die Maschine mit *einem* Primitive und *einer* Zielbestimmung
lauffähig machen, dann die übrigen als parallelisierbare Einzelstücke.

**Drei Mechaniken bleiben bis B10 und B09 unvollständig** und das ist bewusst so: der Klon zieht keine
Mobs, die Unsichtbarkeit hält keine ab, und Zweites Leben prüft nicht auf Instanzen. B08 definiert die
Einhängepunkte — dasselbe Muster, mit dem B07 die Fähigkeits-IDs an B08 abgegeben hat.

## Technical Context

**Language/Version**: Java 25 (ADR-001), Toolchain aus B01 unverändert.

**Primary Dependencies**: Keine neuen externen. B08 nutzt `StatEngine`, `ResourceView` und
`ModifierSet` aus B04, `CombatPipeline` mit `abilityDamage`, `registerInterceptor`, `isInCombat` und
`remainingCombatTime` aus B05, `Progression` aus B06 für das Level, `ClassRegistry.abilitiesOf` und
`BoundItemTag` aus B07, den Write-Behind-Puffer und `AggregateType` aus B02, `Scheduler` aus B01 —
**mit einer Erweiterung**, siehe unten.

**Storage**: PostgreSQL, **eine neue Tabelle** `rpg.character_abilities` (Migration `V8_1`), eine Zeile
je Charakter und Fähigkeit mit Rang und Cooldown-Ablaufzeitpunkt. Kein Freischaltzustand — der wird
aus dem Level abgeleitet (FR-061, setzt B07s Regel fort).

**Testing**: JUnit 5 + AssertJ serverfrei in `rpg-core` für Konfigurationsbindung, Zielauswahl,
Cooldown- und Manaarithmetik, Rangkurven und die Trigger-Auflösung. Testcontainers mit echtem
PostgreSQL für Repository und Migration (Prinzip VII). MockBukkit in `rpg-platform` für
Rechtsklick-Auslösung, Hotbar-Belegung, Doppelsprung und die Projektil-Primitive. `FullBootstrapTest`
in `rpg-plugin` für die Verdrahtung (ADR-012).

**Target Platform**: Linux-VPS, Paper-Server (Minecraft 26.2 / Java 25), API-Artefakt
`26.2.build.112-stable`.

**Project Type**: Regel-Engine-Block mit Persistenz- und Plattformanteil, innerhalb des
Multi-Modul-Gradle-Projekts aus B01.

**Performance Goals**: Tick-Budget ≤ 5 ms (Prinzip II). 100 gleichzeitig wirkende Flächenfähigkeiten
im Budget (SC-002). Die Zahl der geplanten Aufgaben entspricht der Zahl der **laufenden Casts und haltenden Fähigkeiten** und
sonst nichts (SC-005) — weder Cooldown noch Regeneration, Wut oder Ladungen erzeugen eine; alle Intervall-Effekte teilen sich **eine** Auswertung. Die Zielsuche liegt
im Hot Path und muss allokationsfrei antworten.

**Constraints**: Die Fähigkeitsdefinitionen liegen genau **einmal** im Speicher, nicht je Spieler.
Cooldowns und Regeneration sind reine Zeitstempelarithmetik. Der einzige Tick-gebundene Zustand ist
der Cast, und er existiert nur, solange gecastet wird. Kein Datenbankzugriff je Auslösung — Ränge und
Cooldowns gehen über den Write-Behind-Puffer.

**Scale/Scope**: 16 Primitives, 9 Zielbestimmungen, 5 Trigger, 18 Fähigkeiten über drei Klassen.
Rund 100 funktionale Anforderungen, 10 Erfolgskriterien.

**Eine Erweiterung an B01**: `Scheduler.runSyncOnEntityDelayed(EntityRef, Duration, Runnable)`. Ohne
sie lässt sich eine Wirkzeit nicht ausdrücken, die zu einem bestimmten Zeitpunkt im Tick wirkt. Siehe
R2 und die Complexity-Tracking-Tabelle.

## Constitution Check

*GATE: Muss vor Phase 0 bestehen. Nach Phase 1 erneut geprüft.*

| Prinzip | Bewertung | Begründung |
|---|---|---|
| **I. Nebenläufigkeit** | ✅ | Fähigkeitsdefinitionen werden beim Start geladen und danach nur gelesen — unveränderlich, kein geteilter veränderlicher Zustand. Jede Wirkung läuft im Tick. Der Cast geht über den **entity-gebundenen** Scheduler, nie über den globalen Bukkit-Scheduler; die Erweiterung `runSyncOnEntityDelayed` ist ausdrücklich entity-gebunden und hält damit den Folia-Pfad offen (ADR-007). Ränge werden asynchron über den Puffer aus B02 geschrieben. |
| **II. Performance** | ✅ | **Keine wiederkehrende Aufgabe je Spieler.** Cooldowns, globale Sperre und beide Regenerationsraten sind Zeitstempelarithmetik. Der einzige geplante Ablauf ist ein laufender Cast, und er hängt am Cast, nicht am Spieler. Zielsuche über den räumlichen Index von Paper statt linearer Iteration (R5). Flächeneffekte tragen eine Pflicht-Obergrenze, die der Start erzwingt. Kein Datenbankzugriff je Auslösung. |
| **III. Architektur** | ✅ | Definition, Primitives, Zielbestimmung, Cooldown- und Manaarithmetik in `rpg-core` ohne eine Bukkit-Referenz. Die Zielauflösung ist hinter einer Schnittstelle gekapselt, deren Paper-Umsetzung in `rpg-platform` liegt — dieselbe Bauform wie `MobStatProvider` in B05. Listener, Itemaufbau und Projektile in `rpg-platform`, Verdrahtung in `rpg-plugin`. Schaden ausschließlich über `CombatPipeline.abilityDamage`, nie daran vorbei (FR-068). |
| **IV. Datenhaltung** | ✅ | Schemaänderung nur über die versionierte Migration `V8_1`. Der Speicher-Cache ist während der Sitzung autoritativ; geschrieben wird über den Write-Behind-Puffer. Gespeichert werden **Rang und Cooldown-Zeitstempel**, nie eine berechnete Wirkung — dieselbe Regel, die ADR-004 für Items zieht, damit Rebalancing kein Migrationsproblem wird. Ein abgelaufener Cooldown wird beim Laden verworfen statt geladen (FR-031). |
| **V. Datengetriebenes Design** | ✅ | Kosten, Cooldowns, Wirkzeiten, Reichweiten, Zielobergrenzen, Rangkurven, globale Sperre und die beiden Kampf-Faktoren liegen in `abilities.yml`, die Klassenbindung in `classes.yml`. Validierung beim Start mit Fail-Fast und benannter Ursache. Alle Spielertexte über Message-Schlüssel. **SC-001 ist die Prüfung dieses Prinzips** und wird als Test geführt. |
| **VI. Korrektheit & Sicherheit** | ✅ | Der Server entscheidet über jede Auslösung; das Item ist reine Eingabe und trägt keine Logik (FR-058). Mana- und Cooldown-Prüfung sind serverseitig. Eine Ausnahme in einem Effekt wird abgefangen, mit der Kennung der Fähigkeit protokolliert und auf das eine Ereignis begrenzt (FR-017) — dieselbe Barriere wie bei B01s Modulen, B04s Beiträgen und B05s Interceptoren. **Kein Reflection, kein NMS.** |
| **VII. Tests** | ✅ | Alle Formeln und Regeln serverfrei in `rpg-core`. Persistenz gegen echtes PostgreSQL, keine Mocks. **B08 ist kein Block mit ausdrücklicher Lasttestpflicht** — Prinzip VII nennt B05 und B10 — aber SC-002 (100 Flächenfähigkeiten im Tick-Budget) ist als messender Test formuliert und nicht als Behauptung. |
| **VIII. Sprache** | ✅ | Diese Dokumente auf Deutsch, Code, Config-Keys und Spielertexte auf Englisch. |

**Ergebnis: kein Verstoß.** Ein Punkt braucht eine ausdrückliche Begründung und steht deshalb in der
Complexity-Tracking-Tabelle — die Erweiterung der `Scheduler`-Abstraktion.

Drei Punkte, die knapp an einem Verstoß vorbeigehen und deshalb benannt sind:

- **Der Cast-Zustand ist Tick-gebundene Arbeit je Spieler.** Prinzip II verbietet *wiederkehrende*
  Aufgaben je Spieler, nicht einmalige. Ein Cast ist ein Einzelstück mit einem Endzeitpunkt, und ein
  Spieler ohne laufenden Cast hat keine Aufgabe. Die Zusage ist messbar formuliert (SC-005) statt
  behauptet.
- **`PlayerInteractEvent` feuert häufig.** B08 bringt den ersten Interact-Listener des Projekts. Die
  Prüfung ist ein Nachschlagen der Marke im `PersistentDataContainer` des gehaltenen Items — derselbe
  allokationsfreie Griff, den B07 für das Bindungsprädikat gewählt hat und der dort bei 10 000
  Abfragen gemessen wurde.
- **Zwölf Primitives sind zwölf Umsetzungen.** Das sieht nach Umfang aus, ist aber die Gegenrichtung:
  jedes ist zustandslos und in sich klein, und die Alternative wäre eine Klasse je *Fähigkeit* — also
  achtzehn statt zwölf, mit dem Unterschied, dass die neunzehnte wieder eine kostet.

## Project Structure

### Documentation (this feature)

```text
specs/008-ability-framework/
├── plan.md              # Diese Datei
├── research.md          # Phase 0 — die sieben technischen Unbekannten
├── data-model.md        # Phase 1 — Entitäten, Tabelle, Zustandsübergänge
├── quickstart.md        # Phase 1 — nachvollziehbare Prüfläufe
├── contracts/
│   ├── ability-config.md   # Schema von abilities.yml, Prüfungen V1..Vn
│   └── ability-api.md      # Die öffentliche Schnittstelle für B12 und B13
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 (/speckit-tasks — nicht von /plan erzeugt)
```

### Source Code (repository root)

```text
rpg-core/src/main/java/rpg/core/ability/
├── Ability.java                 # die Definition, unveränderlich
├── AbilityConfig.java           # alle Definitionen + globale Sperre + Kampf-Faktoren
├── AbilityConfigSchema.java     # Bindung und Startprüfungen
├── AbilityKindMismatchException.java
├── EffectSpec.java              # Primitive-Art + Parameter + Rangkurve
├── EffectType.java              # die zwölf Primitives
├── TargetSpec.java              # Modus, Reichweite, Winkel, Obergrenze
├── TargetMode.java              # die sieben Zielbestimmungen
├── TargetResolver.java          # Schnittstelle; Paper-Umsetzung in rpg-platform
├── AbilityTrigger.java          # die fünf Trigger
├── AbilityState.java            # Rang + Cooldown je Charakter und Fähigkeit
├── AbilityStateRepository.java
├── CastState.java               # ein laufender Cast
├── AbilityRuntime.java          # Auslösung, Kosten, Cooldown, globale Sperre, Cast
├── ResourceRegeneration.java    # beide Raten, zeitstempelbasiert lazy
├── AbilityRegistry.java         # die öffentliche Schnittstelle (FR-066)
├── AbilityMessageKeys.java
├── effect/                      # je Primitive eine zustandslose Anwendung
└── package-info.java

rpg-persistence/src/main/java/rpg/persistence/ability/
├── JdbcAbilityStateRepository.java
└── AbilityModule.java
rpg-persistence/src/main/resources/db/migration/
└── V8_1__character_abilities.sql

rpg-platform/src/main/java/rpg/platform/ability/
├── AbilityHotbar.java           # Slotbelegung, Items, Nachziehen bei Freischaltung
├── AbilityItemTag.java          # Marke: welcher Slot trägt welche Fähigkeit
├── AbilityTriggerListener.java  # Rechtsklick löst aus, Linksklick tut nichts
├── CastInterruptListener.java   # Schaden, Slotwechsel, Tod, Bewegung
├── DoubleJumpListener.java      # PlayerToggleFlightEvent für Magic Boost & Fall
├── PaperTargetResolver.java     # die sieben Zielbestimmungen über Papers Index
├── AbilityProjectile.java
└── package-info.java

rpg-plugin/src/main/resources/abilities.yml
rpg-plugin/src/main/resources/classes.yml     # die Loadouts von Rogue und Mage
```

**Structure Decision**: Dieselbe Vierteilung wie B04 bis B07 — Regeln in `rpg-core`, Persistenz in
`rpg-persistence`, Paper-Berührung in `rpg-platform`, Verdrahtung in `rpg-plugin`. Neu ist die
Schnittstelle `TargetResolver`: die Zielbestimmung ist eine Regel (welcher Kegel, welche Obergrenze,
welche Reihenfolge), aber ihre Ausführung braucht die Welt. Getrennt wie `MobStatProvider` in B05, aus
demselben Grund und mit demselben Nutzen — die Auswahlregeln sind serverfrei prüfbar.

## Änderungen an bestehenden Blöcken

B08 ist der erste Block, der an bereits abgeschlossenem Code arbeitet. Die Liste ist vollständig und
absichtlich kurz:

| Ort | Änderung | Grund |
|---|---|---|
| `Scheduler` (B01) | `runSyncOnEntityDelayed` ergänzen | Wirkzeit ist verzögerte Tick-Arbeit; es gibt keine Methode dafür (R2) |
| `AbilityBinding` (B07) | Invariante `unique ⇒ ACTIVE` entfernen | ADR-022 — die Unique darf passiv sein |
| `CharacterClassDefinition` (B07) | `ACTIVE_ABILITIES` und `PASSIVE_ABILITIES` entfernen | ADR-025 — der Rogue ist 3+3; geprüft wird nur noch „genau sechs, höchstens eine Unique" |
| `CharacterClassDefinition` (B07) | Zählregel auf „4 aktiv, 2 passiv, höchstens eine Unique" ohne Kopplung an `kind` | dieselbe Entscheidung |
| `classes.yml` | Loadouts für Rogue und Mage füllen | FR-045 aus B07 war ausdrücklich auf B08 vertagt |
| `AggregateType`, `FlushCycle.WRITE_ORDER`, Repository-Verdrahtung (B02) | `CHARACTER_ABILITIES` in allen dreien | ADR-015 |

**Nicht geändert wird B05.** Die Einhängepunkte für Trigger und Lifesteal existieren, `abilityDamage`
existiert, der Kampfzustand ist lesbar. Das ist kein Zufall — B05 hat sie für diesen Block gebaut und
in seinem Javadoc namentlich zugesagt.

## Constitution Check — erneut nach Phase 1

Nach data-model.md und den Verträgen erneut geprüft: **unverändert kein Verstoß.**

Zwei Dinge, die das Design gegenüber der ersten Prüfung verbessert haben:

- **Die Regeneration braucht kein Ereignis.** Der erste Entwurf hätte an `CombatStateChangedEvent`
  gehangen und damit an einer Zusage, die die Produktion derzeit nicht einlöst (R3). Die
  Zwei-Zeitstempel-Lösung ist nicht nur unabhängig davon, sie ist auch exakt statt näherungsweise —
  und sie erfüllt Prinzip II strenger, weil sie auch die Ereignisverarbeitung einspart.
- **Die Zielobergrenze ist ein Pflichtfeld, kein Vorgabewert.** Ein Standardwert hätte eine vergessene
  Zeile von einer bewussten Entscheidung ununterscheidbar gemacht — dieselbe Begründung, aus der B07
  alle Attributfelder verlangt, auch die mit Null.

## Complexity Tracking

| Verstoß | Warum nötig | Verworfene einfachere Alternative |
|---|---|---|
| **Erweiterung der `Scheduler`-Abstraktion um `runSyncOnEntityDelayed`** | Eine Fähigkeit mit Wirkzeit muss zu einem bestimmten späteren Zeitpunkt **im Tick** wirken und dabei die Paper-API berühren. Keine der vier vorhandenen Methoden drückt das aus. | **`runAsyncDelayed` gefolgt von `runSyncOnEntity`**: funktioniert mit der heutigen Schnittstelle, kostet aber einen Threadwechsel für rein tickgebundene Arbeit, macht die Wirkzeit um bis zu einen Tick ungenau und erzeugt zwei Scheduler-Aufrufe je Cast. **Lazy auswerten wie ein Cooldown**: unmöglich — ein Cooldown wird gelesen, wenn jemand fragt; ein Cast muss wirken, auch wenn niemand fragt. Papers `EntityScheduler` kennt `runDelayed` nativ, die Erweiterung bildet also 1:1 ab, statt etwas nachzubauen. |

Die Erweiterung folgt der Bauform, die die Abstraktion selbst vorschreibt: entity-gebunden, einmalig,
nicht wiederkehrend. Sie öffnet keinen Weg zu einer periodischen Aufgabe und hält den Folia-Pfad
offen. **ADR-024 hält sie fest**, so wie ADR-010 die vorige Erweiterung festgehalten hat.

## Was Phase 2 aufnehmen muss

Die Reihenfolge, von der abzuweichen teuer wird:

1. **Erst die Maschine mit genau einem Primitive und einer Zielbestimmung.** Damage auf Selbst reicht,
   um Kosten, Cooldown, globale Sperre, Auslösung und Auskunft durchgängig zu prüfen. Wer zuerst zwölf
   Primitives baut, testet zwölfmal denselben ungeprüften Rahmen.
2. **Die drei Registrierungen aus ADR-015 gemeinsam**, mit ihrem Test unmittelbar dahinter — nicht in
   der Polish-Phase.
3. **`runSyncOnEntityDelayed` vor der ersten Fähigkeit mit Wirkzeit.** Sonst entsteht ein Provisorium
   über `runAsyncDelayed`, das genau die Alternative ist, die oben verworfen wurde.
4. **Die Loadouts von Rogue und Mage zuletzt.** Sie sind Konfiguration und der Beweis für SC-001: wenn
   sie nach der Maschine ohne Codeänderung entstehen, ist das Akzeptanzkriterium des Steckbriefs
   erfüllt. Werden sie vorher gebaut, ist es nur behauptet.
5. **Der Widerruf der Invariante in B07 vor dem Rogue-Loadout.** Second Life ist passiv und unique;
   ohne den Widerruf lehnt `AbilityBinding` die Konfiguration ab.
