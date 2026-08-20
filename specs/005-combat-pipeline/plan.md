# Implementation Plan: B05 · Kampf- & Schadens-Pipeline

**Branch**: `005-combat-pipeline` | **Date**: 2026-08-20 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/005-combat-pipeline/spec.md`

## Summary

B05 ist der erste Block, der im Tick-Pfad *arbeitet* statt nur zu reagieren. Vier Entscheidungen
prägen die Umsetzung:

1. **Die Vanilla-Quellentabelle ist erschöpfend über den Aufzählungstyp, nicht über eine Liste.**
   Der Blocksteckbrief nennt 17 Schadensursachen; Paper 26.2 kennt rund 30. Eine handgepflegte
   Liste hätte die übrigen stillschweigend durchgelassen — genau das, was ADR-003 ausschließt. Die
   Zuordnung ist deshalb ein vollständiger Switch über `DamageCause` mit einem
   **Verweigerungs-Standardfall**: eine unbekannte Ursache wird neutralisiert und einmal
   protokolliert. Ein Minecraft-Update, das eine Ursache hinzufügt, kann damit keinen Schaden
   durchlassen, sondern erzeugt eine Protokollzeile, die zur Entscheidung auffordert.

2. **Kein Objekt je Treffer.** Der Schadensvorgang ist ein wiederverwendbares Objekt je Tick-Thread,
   das die Pipeline durchreicht und am Ende zurücksetzt. Die Formel selbst ist statisch und rechnet
   auf Gleitkommazahlen. Bei 150 Spielern gegen 800 Mobs ist das der Unterschied zwischen einem
   ruhigen und einem sägenden Garbage Collector.

3. **Projektile tragen ihren Rohschaden, nicht ihren Schnappschuss.** Beim Abschuss wird der
   Rohschaden aus dem Schützen-Schnappschuss berechnet und als einzelne Zahl am Projektil
   hinterlegt. Beim Einschlag wird sie gelesen. Damit hält nichts einen Schnappschuss über
   unbestimmte Zeit fest, und es gibt keine Liste fliegender Projektile, die lecken könnte.

4. **Kampfzustand und Angriffszeitfenster sind dasselbe Muster**: ein Zeitstempel je Träger, nur
   bei Abfrage ausgewertet. Kein Timer, keine Aufgabe, keine Liste, die abläuft.

Was wie ein Konflikt mit B04 aussieht und keiner ist: B04 spiegelt `attackSpeed` auf das
Vanilla-Attribut, B05 verlangt, dass der Vanilla-Waffencooldown wirkungslos ist. Beides gilt
gleichzeitig — der Vanilla-Cooldown skaliert nur *Vanilla-Schaden*, den B05 ohnehin auf null setzt.
Die Spiegelung treibt damit nur noch die Cooldown-Anzeige im Client, und die zeigt dank derselben
Zahl genau das an, was B05 tatsächlich durchsetzt.

## Technical Context

**Language/Version**: Java 25 (ADR-001), Toolchain aus B01 unverändert.

**Primary Dependencies**: Keine neuen. Paper-API (compileOnly) in `rpg-platform`, sonst nichts —
B05 braucht keine Datenbank.

**Storage**: **Keine.** B05 legt keine Tabelle an und schreibt nichts. Kampfzustand,
Angriffszeitfenster und Beitragsfenster sind ausdrücklich flüchtig: wer sich abmeldet, ist nicht
mehr im Kampf, und ein Mob, der verschwindet, nimmt sein Beitragsfenster mit.

**Testing**: JUnit 5 + AssertJ für Formel, Pipeline, Zeitfenster und Attribution — vollständig
serverfrei in `rpg-core`, mit einer gesteuerten Uhr statt Wartezeiten. MockBukkit in
`rpg-platform` für die Quellenzuordnung, die Listener und die Rückmeldung. Der bestehende
`FullBootstrapTest` in `rpg-plugin` beweist die Verdrahtung (ADR-012). **Lasttest ist Pflicht**:
Prinzip VII nennt B05 ausdrücklich als Block, der ohne Lasttest-Nachweis nicht als fertig gilt.

**Target Platform**: Linux-VPS, Paper-Server (Minecraft 26.2 / Java 25).

**Project Type**: Regel-Engine-Block innerhalb des Multi-Modul-Gradle-Projekts aus B01.

**Performance Goals**: 150 Angreifer gegen 800 Ziele im Dauerkampf, p95 MSPT < 40 ms (SC-004).
10 000 Treffer ohne vermeidbare Objekterzeugung (SC-005). Im Leerlauf null Tickzeit und null
geplante Aufgaben (SC-013).

**Constraints**: Jeder Bukkit-Zugriff im Tick des betroffenen Trägers (Prinzip I). Keine
wiederkehrende Aufgabe je Spieler oder Mob (Prinzip II, FR-022, FR-030d, FR-044). Beitragsfenster
in Anzahl **und** Alter begrenzt (FR-032, FR-033). Kein Bukkit-Import in `rpg-core` — durch die
Gradle-Modulgrenze erzwungen. Kein Zugriff auf Ausrüstung oder Itemwerte (FR-030).

**Scale/Scope**: 100–200 gleichzeitige Spieler (ADR-002), bis zu 800 aktive Mobs. Konsumenten sind
B06, B08, B10, B11, B12 und B13.

### Abgrenzung — was B05 ausdrücklich nicht tut

| Gehört zu | Was B05 stattdessen liefert |
|---|---|
| B06 Progression | XP-Kurve, Levelregeln. B05 liefert die Schadensaufteilung im Todesereignis. |
| B08 Fähigkeiten | Fähigkeiten, Manakosten, Cooldownverwaltung. B05 liefert den Schadensfaktor als Vertrag und den Kampfzustand. |
| B09 Zonen | Zonenregeln. B05 hat die eine Entscheidungsstelle, die B09 später füllt. |
| B10 Mobs | Mob-Definitionen, Spawning, Verhalten. B05 überbrückt nur die Werte, hinter einer Schnittstelle. |
| B11 Items | Itemdefinitionen, Haltbarkeit, Loot-Tabellen, Ausrüstungsschaden beim Tod. B05 liefert das Todesereignis. |
| B12 Statistiken | Auswertung und Leaderboards. B05 veröffentlicht die Ereignisse. |
| B13 UI | HUD, Darstellung von Schadenszahlen. B05 liefert zusammengefasste Anzeigeereignisse, aber keine Anzeigeobjekte. |

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Prüfung | Status |
|---|---|---|
| I. Nebenläufigkeit | Schadensereignisse treffen bereits im Tick ein; die Pipeline läuft synchron darin zu Ende und ruft nichts Blockierendes auf. Rückmeldung (Trefferanimation, Rückstoß) geschieht am selben Träger im selben Tick. Kein globaler Scheduler, kein `join()`, kein I/O. Zustand hängt am Träger, nicht an globalem veränderlichem Zustand. | PASS |
| II. Performance | Keine wiederkehrende Aufgabe: Angriffszeitfenster, Kampfzustand und Beitragsalter sind Zeitstempel, die erst bei Zugriff ausgewertet werden. Der Schadensvorgang ist ein wiederverwendetes Objekt je Tick-Thread; die Formel arbeitet auf `double` ohne Boxing, ohne Streams. Das Beitragsfenster ist ein Array fester Größe je Ziel. Kein Datenbankzugriff — B05 hat gar keinen. | PASS |
| III. Architektur | Formel, Pipeline, Zeitfenster, Attribution und Kampfzustand liegen vollständig in `rpg-core` und sind serverfrei prüfbar. Die Paper-Anbindung (Quellenzuordnung, Listener, Rückmeldung, Mob-Ausstattung) liegt in `rpg-platform` hinter Schnittstellen. Zugriff auf B04 ausschließlich über `StatEngine`. Richtung `plugin → platform → core` bleibt durch den Gradle-Graphen erzwungen. | PASS |
| IV. Datenhaltung | Nicht berührt: B05 persistiert nichts. Die drei Zustände sind bewusst flüchtig — ein Abmelden beendet den Kampf, ein entladener Mob nimmt sein Beitragsfenster mit. Kein Migrationsbedarf, kein Datenverlustrisiko. | PASS |
| V. Datengetriebenes Design | Schadensbeträge je Umgebungsquelle, Mob-Wertesätze, Beitragsfenster, Anzeigefenster und Kampfzustandsdauer stehen in `combat.yml` und werden beim Start gegen ein Schema geprüft; ein Fehler bricht den Start ab. Balancing erfordert keine Codeänderung. | PASS |
| VI. Korrektheit & Sicherheit | Der Server ist alleinige Autorität; kein Wert stammt aus einer Client-Eingabe. Die Quellenzuordnung ist erschöpfend mit Verweigerungs-Standardfall — eine unbekannte Ursache kann keinen Schaden durchlassen. Eine Ausnahme in einer Pipeline-Stufe bleibt auf den Vorgang begrenzt (FR-010), nach dem Muster von B01s `ModuleFaultBarrier`. Kein Reflection, kein NMS: `playHurtAnimation`, `knockback`, `setNoDamageTicks` und die Gameregeln sind öffentliche Paper-API. | PASS |
| VII. Tests | Formel, Pipeline, Zeitfenster und Attribution serverfrei unit-getestet mit dokumentierten Beispielrechnungen (SC-012). **B05 ist ausdrücklich lasttestpflichtig** — Prinzip VII nennt ihn beim Namen. Der Block gilt erst mit dem Nachweis p95 MSPT < 40 ms als fertig. | PASS |
| VIII. Sprache | Planung und Spec-Artefakte auf Deutsch; Pakete, Typen, Felder, Config-Keys auf Englisch. | PASS |

**Ergebnis: 8/8 PASS.** Drei Entwurfsentscheidungen berühren fremde Blöcke oder weichen von einem
etablierten Muster ab und sind unter *Complexity Tracking* begründet.

### Nachprüfung nach Phase 1

Nach Ausarbeitung von Datenmodell, Verträgen und Validierungsleitfaden erneut geprüft: unverändert
8/8 PASS. Zwei Punkte wurden im Entwurf nachgeschärft:

- Der wiederverwendete Schadensvorgang wäre ein Fehlerherd gewesen, wenn eine Pipeline-Stufe ihn
  über den Vorgang hinaus festhält. Der Vertrag sagt das jetzt ausdrücklich, und die Stufen bekommen
  eine unveränderliche Lesesicht statt des Objekts selbst.
- Die Vanilla-Invulnerabilitätsticks (`noDamageTicks`) hätten das eigene Angriffszeitfenster
  überlagert: ein Spieler mit hoher Angriffsgeschwindigkeit hätte trotzdem nur alle 10 Ticks
  getroffen. Sie werden abgeschaltet, damit die Schlagfolge allein aus `attackSpeed` folgt.

## Project Structure

### Documentation (this feature)

```text
specs/005-combat-pipeline/
├── plan.md              # Diese Datei
├── research.md          # Phase 0 — Entwurfsentscheidungen mit Alternativen
├── data-model.md        # Phase 1 — Typen, Regeln, Vanilla-Quellentabelle, Zustandsübergänge
├── quickstart.md        # Phase 1 — Validierungsleitfaden inkl. Lasttest
├── contracts/           # Phase 1 — öffentliche Schnittstellen des Blocks
│   ├── combat-pipeline.md
│   ├── damage-sources.md
│   ├── combat-config.md
│   └── events.md
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2 — erzeugt von /speckit-tasks
```

### Source Code (repository root)

```text
rpg-core/src/main/java/rpg/core/combat/
├── DamageType.java                 # PHYSICAL | MAGIC | ENVIRONMENT (FR-002, FR-012b)
├── DamageOrigin.java               # MELEE, PROJECTILE, ABILITY, ENVIRONMENT, ADMIN
├── DamageContext.java              # der wiederverwendete Vorgang (FR-045)
├── DamageView.java                 # unveränderliche Lesesicht für Pipeline-Stufen
├── PipelineStage.java              # die sechs benannten Stufen (FR-007)
├── DamageInterceptor.java          # Eingriffspunkt für B08/B11 (FR-008)
├── CombatPipeline.java             # oeffentliche Schnittstelle des Blocks
├── DefaultCombatPipeline.java      # Ablauf, Abbruch, Fehlerbarriere (FR-007 bis FR-010)
├── DamageResult.java               # Ergebnis eines Vorgangs samt Ablehnungsgrund
├── RejectReason.java               # NONE, NOT_PERMITTED, ATTACK_TOO_SOON, SESSION_NOT_READY, ...
├── EnvironmentSource.java          # die abgebildeten Umgebungsquellen (FR-012)
├── FallDamageConfig.java           # sichere Hoehe, Schaden je Block, Obergrenze (FR-012c)
├── DeathCause.java                 # Todesursache im Todesereignis
├── ProjectileDamage.java           # Rohschaden am Projektil hinterlegen (FR-024b)
├── DamageFormula.java              # die reine Formel (FR-001 bis FR-006)
├── AttackWindow.java               # Angriffszeitfenster je Träger (FR-020 bis FR-023)
├── CombatState.java                # Kampfzustand je Träger (FR-030c bis FR-030f)
├── AttributionWindow.java          # begrenztes, alterndes Beitragsfenster (FR-031 bis FR-036)
├── DamageShare.java                # Aufteilung beim Tod (FR-034)
├── DamagePermission.java           # die EINE Erlaubnisstelle (FR-042, FR-042a)
├── MobStatProvider.java            # Schnittstelle, die B10 übernimmt (FR-019c)
├── DamageFeedback.java             # Schnittstelle für Animation und Rückstoß (FR-037)
├── DamageAggregator.java           # Zusammenfassung für die Anzeige (FR-038)
├── CombatConfig.java               # validierte combat.yml
├── CombatModule.java               # Modulverdrahtung nach B01-Vertrag
├── DamageDealtEvent.java           # zusammengefasstes Anzeigeereignis (FR-038)
├── CombatDeathEvent.java           # Todesereignis mit Aufteilung (FR-026 bis FR-028)
│                                   # NICHT "EntityDeathEvent": so heisst Bukkits eigene Klasse,
│                                   # die CombatDeathListener importiert. Zwei gleichnamige Typen
│                                   # in einer Datei sind ein falscher Import mit Ansage.
└── CombatStateChangedEvent.java    # Kampf betreten/verlassen (FR-030e)

rpg-platform/src/main/java/rpg/platform/combat/
├── VanillaDamageMapping.java       # erschöpfender Switch, Verweigerungs-Standard (FR-011)
├── VanillaDamageListener.java      # fängt jeden Schaden ab (FR-016, FR-018)
├── ProjectileCombatListener.java   # Abschuss und Einschlag (FR-024a, FR-024b)
├── MobEquipmentListener.java       # Werte beim Erscheinen, Freigabe beim Ende (FR-019a, FR-019d)
├── CombatDeathListener.java        # Tod, Beute und Erfahrung unterdrücken (FR-030a, FR-030b)
├── PaperDamageFeedback.java        # Trefferanimation und Rückstoß (FR-037)
└── PaperMobStatProvider.java       # Wertesätze aus der Konfiguration (FR-019b)

rpg-plugin/src/main/resources/
└── combat.yml

rpg-core/src/test/java/rpg/core/combat/          # der Großteil, serverfrei
rpg-platform/src/test/java/rpg/platform/combat/  # Zuordnung, Listener, MockBukkit
rpg-plugin/src/test/java/rpg/plugin/             # FullBootstrapTest erweitert
```

**Structure Decision**: Regeln in `rpg-core`, Paper-Anbindung in `rpg-platform`, Zusammenbau in
`rpg-plugin` — wie B04. Ein Unterschied: **`CombatModule` liegt in `rpg-core`, nicht in
`rpg-persistence`.** B02, B03 und B04 haben ihre Module dort, weil sie ein Repository aufbauen
mussten. B05 hat keine Datenbank; das Modul dort abzulegen würde eine Abhängigkeit vortäuschen, die
nicht existiert.

## Complexity Tracking

| Abweichung | Warum nötig | Verworfene einfachere Alternative |
|---|---|---|
| B05 stattet Mobs mit Werten aus (B10-Gebiet) | FR-018 lässt Wesen ohne Stat-Träger unangetastet, und kein Block vergibt heute welche. Ohne die Überbrückung wirkt die gesamte Pipeline auf nichts außer Spieler — fertig, grün getestet, im Spiel unsichtbar. Zudem wäre der lasttestpflichtige Nachweis (150 gegen 800) bis B10 nicht durchführbar. | Nur die Schnittstelle liefern und auf B10 warten. Verworfen, weil B05 damit nicht abnehmbar wäre: sein eigenes Erfolgskriterium hinge an einem Block, der drei Blöcke später kommt. |
| `CombatModule` in `rpg-core` statt `rpg-persistence` | B05 greift auf keine Datenbank zu. Ein Modul in `rpg-persistence` ohne Persistenz führt jeden in die Irre, der später die Abhängigkeiten liest. | Dem Muster folgen und es trotzdem nach `rpg-persistence` legen. Verworfen: das Muster kam aus einer Notwendigkeit, die hier fehlt. |
| Kampfzustand liegt in B05, obwohl B08 ihn braucht | Nur B05 sieht jeden Treffer. Läge er in B08, bräuchten B12 und B13 später eigene Zähler, und es gäbe drei Antworten auf dieselbe Frage. | B08 seinen eigenen führen lassen. Verworfen aus dem genannten Grund. |

## Phasen

### Phase 0 — Recherche

Abgeschlossen. Entwurfsentscheidungen mit Alternativen in [research.md](./research.md). Keine
offenen `NEEDS CLARIFICATION`: die sieben Blockfragen und acht weitere aus zwei `/clarify`-Runden
sind in der Spec verankert.

### Phase 1 — Entwurf & Verträge

Abgeschlossen:

- [data-model.md](./data-model.md) — Typen, Validierungsregeln, vollständige Vanilla-Quellentabelle,
  Zustandsübergänge, Speicherabschätzung für 800 Beitragsfenster.
- [contracts/combat-pipeline.md](./contracts/combat-pipeline.md) — die Schnittstelle, gegen die B06
  bis B13 entwickeln.
- [contracts/damage-sources.md](./contracts/damage-sources.md) — jede Vanilla-Ursache mit ihrer
  Behandlung, einschließlich der vom Blocksteckbrief nicht genannten.
- [contracts/combat-config.md](./contracts/combat-config.md) — Aufbau und Schema von `combat.yml`.
- [contracts/events.md](./contracts/events.md) — die drei veröffentlichten Ereignisse.
- [quickstart.md](./quickstart.md) — Validierungsabschnitte einschließlich des pflichtigen
  Lasttests.

### Phase 2 — Aufgaben

Nicht Teil dieses Befehls. Erzeugt durch `/speckit-tasks`.
