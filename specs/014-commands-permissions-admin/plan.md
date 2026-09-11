# Implementation Plan: B14 · Commands, Permissions & Admin-Tools

**Branch**: `014-commands-permissions-admin` | **Date**: 2026-09-03 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `specs/014-commands-permissions-admin/spec.md`

## Summary

B14 gibt dem Server **eine** Bedienoberfläche statt sechs Einzelstücken und holt vier ADR-Zusagen
ein, die seit B08b offenstehen. Der Kern ist ein Kommandogerüst auf Papers Brigadier-Anbindung: ein
Argumentbaum je Kommando, aus dem Prüfung *und* Tab-Completion abgeleitet werden, mit Rechteprüfung
an einer Stelle. Die sechs vorhandenen Kommandos ziehen um, ohne ihr sichtbares Verhalten zu
ändern; die Blockschnittstellen darunter bleiben unangetastet (**B08b**-FR-046).

Dazu kommen vier Admin-Werkzeuggruppen — Items geben, Kreaturen setzen, Konfiguration neu laden,
Stufe/Erfahrung/Klasse richtigstellen, plus lesende Einsicht in fremde Spielerdaten. Drei davon
sind überwiegend Verdrahtung: `ItemStackFactory`, `PaperMobPlacer`, `Progression.setProgress` und
`ClassSelection.choose` liegen fertig da.

**Der eigentliche Gewinn liegt woanders.** Zwei vollständig gebaute Pfade werden von keiner
Produktivzeile aufgerufen: `ConfigLoader.reloadAll()` samt sechs Modul-Nachladehaken, und die
Leseseite des Audit-Logs (`AuditLogRepository.between`). B14 ist die fehlende Verdrahtung — und
damit der Block, der herausfindet, ob B01s Zusage „ein Neuladen erreicht alle Module" in der
Wirklichkeit trägt.

Einzelheiten und Belege: [research.md](./research.md).

## Technical Context

**Language/Version**: Java 25 (ADR-001, `libs.versions.toml`)

**Primary Dependencies**: `paper-api 26.2.build.112-stable` (`compileOnly`);
`com.mojang:brigadier 1.3.10` — **transitiv über paper-api aufgelöst, zur Laufzeit vom Server
geliefert**. B14 nimmt **keinen** neuen `libraries:`-Eintrag auf.

**Storage**: nichts Neues. Das Audit-Log liegt in `V1__baseline.sql` und wird unverändert benutzt.
**Keine Migration, kein neues Schema.** Ein Testcontainers-Test kommt allerdings hinzu: der
**Lesepfad** `AuditLogRepository.between` ist nie gegen eine Datenbank gelaufen (FR-036). Nur die
Schreibseite ist über `AuditLogTest` gedeckt — die Leseseite hat außerhalb von Tests bis heute
keinen Aufrufer, also dieselbe Lage wie `reloadAll()`. Ein Block, der dieses Muster aufräumt, darf
es nicht selbst hinterlassen.

**Testing**: JUnit 6.1.3, AssertJ 3.27.7, MockBukkit 4.116.1 (`mockbukkit-v26.2`),
`FullBootstrapTest` als Verdrahtungsnachweis.

**Target Platform**: Paper 26.2 auf Java 25, ein Serverprozess, PostgreSQL im Docker-Container.

**Project Type**: Minecraft-Server-Plugin, mehrmodulig (Gradle): `rpg-core` → `rpg-platform` /
`rpg-persistence` → `rpg-plugin`, dazu `rpg-content`.

**Performance Goals**: Tick-Budget ≤ 5 ms wie jedes Subsystem. Tab-Completion und Rechteprüfung
laufen im Tick und müssen ohne Sammlung über alle Spieler auskommen. Die Sperrzeit wird
**zeitstempelbasiert lazy** ausgewertet — kein wiederkehrender Task (Prinzip II).

**Constraints**: Kein blockierender Aufruf im Tick — `AuditLogRepository.between` und
`ClassSelection.choose` liefern `CompletableFuture` und werden über den Scheduler in den Tick
zurückgeführt, niemals mit `join()` (Prinzip I). `rpg-core` bleibt bukkit-frei und bekommt von B14
nichts (Prinzip III). Alle Spielertexte über Nachrichtenschlüssel (Prinzip V).

**Scale/Scope**: 6 vorhandene Kommandos ziehen um (1042 Zeilen, davon 5 handgeschriebene
`onTabComplete`), dazu eine Wurzel `/rpg` mit rund acht Unterkommandos. Ein neuer Rechtebaum über
den heutigen acht Rechten. **41 funktionale Anforderungen, 10 Erfolgskriterien, 8 User Stories**,
40 Abnahmeschritte plus drei Vorabschritte.

## Constitution Check

*GATE: vor Phase 0 geprüft, nach Phase 1 erneut. Constitution v1.1.1.*

| Prinzip | Bewertung | Wie B14 es einhält |
|---|---|---|
| **I. Nebenläufigkeit** | ✅ mit Auflage | Kommandos laufen im Tick. `between()` und `choose()` geben `CompletableFuture` — Rückgabe über die projekteigene Scheduler-Abstraktion, **kein `join()`**. Kein globaler Bukkit-Scheduler. Die Sperrzeit hängt am Absender, nicht an globalem Zustand. |
| **II. Performance** | ✅ mit Auflage | Keine wiederkehrenden Tasks. Die Sperrzeit wird lazy aus einem Zeitstempel gerechnet, wie Cooldowns. Vorschlagslisten werden begrenzt und am Getippten gefiltert (FR-033), nie vollständig gesendet. |
| **III. Architektur** | ✅ | `rpg-core` bekommt nichts. Das Gerüst liegt in `rpg-plugin`, wo die Paper-Typen hingehören. Kommandos rufen ausschließlich öffentliche Blockschnittstellen (FR-007). |
| **IV. Datenhaltung** | ✅ | B14 persistiert nichts Eigenes. Das Audit-Log ist bestehende, versionierte Struktur. |
| **V. Datengetriebenes Design** | ✅ | Sperrzeiten und die Obergrenze für handgesetzte Kreaturen stehen in der Konfiguration. Alle Texte über Nachrichtenschlüssel (FR-009). |
| **VI. Korrektheit & Sicherheit** | ✅ | Rechteprüfung an einer Stelle (FR-003), Argumentprüfung aus derselben Deklaration wie die Vorschläge, Sperrzeiten (FR-032). Kein Reflection, kein NMS. |
| **VII. Tests** | ✅ | Regeln des Gerüsts als serverlose Unit-Tests. Der Verdrahtungsnachweis läuft über `FullBootstrapTest` (FR-035). Kein Lasttest als Bedingung — der gehört B15. |
| **VIII. Sprache** | ✅ | Unterlagen deutsch, Code und Spielertexte englisch. |

**Zwei Abweichungen sind ADR-pflichtig** und unten in *Complexity Tracking* begründet.

**Ein Punkt, der kein Verstoß ist, aber Aufmerksamkeit braucht:** Prinzip III sagt, Zugriff auf
Interna anderer Blöcke sei unzulässig. B14 fasst `HordeRegistry.Entry` an — das ist B10s
**öffentliche** Bauart, kein Internum, und die Änderung wird als ADR festgehalten statt am Rand
mitgenommen.

## Project Structure

### Documentation (this feature)

```text
specs/014-commands-permissions-admin/
├── plan.md              # diese Datei
├── spec.md              # 8 User Stories, 39 FR, 10 SC
├── research.md          # Phase 0 — zehn Befunde, alle am Code belegt
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1 — Abnahmeschritte
├── contracts/
│   ├── command-framework.md   # der Argumentbaum und seine Regeln
│   └── permissions.md         # der Rechtebaum
├── checklists/
│   └── requirements.md
└── tasks.md             # Phase 2, erzeugt /speckit-tasks — NICHT von /speckit-plan
```

### Source Code (repository root)

```text
rpg-core/                       # UNVERÄNDERT — B14 fügt hier nichts hinzu
└── src/main/java/rpg/core/
    ├── mob/HordeRegistry.java          # + Herkunft am Entry (ADR-1)
    └── item/ItemModule.java            # notifyReloaded() → applyReloadedConfig()

rpg-platform/
└── src/main/java/rpg/platform/
    ├── item/TrashCommand.java          # zieht um nach rpg-plugin
    └── item/ItemStackFactory.java      # unverändert benutzt

rpg-plugin/
└── src/main/java/rpg/plugin/
    ├── RpgPlugin.java                  # 8 setExecutor/setTabCompleter fallen weg
    └── command/
        ├── framework/                  # NEU — das Gerüst
        │   ├── RpgCommand.java             # ein Kommando als Argumentbaum
        │   ├── CommandTree.java            # Aufbau und Registrierung
        │   ├── Arguments.java              # Argumenttypen inkl. „Spieler" (online+offline)
        │   ├── CommandPermissions.java     # die eine Prüfstelle
        │   ├── RateLimits.java             # lazy, zeitstempelbasiert
        │   └── AdminAudit.java             # eine Stelle, die ins Audit-Log schreibt
        ├── CharacterSheetCommand.java  # ziehen um: Hülle raus, Inhalt bleibt
        ├── CoinsCommand.java
        ├── StatisticsCommand.java
        ├── TopCommand.java
        ├── XpCommand.java
        ├── TrashCommand.java           # hierher gezogen
        └── admin/                      # NEU — die vier Werkzeuggruppen
            ├── ItemGiveCommand.java
            ├── MobSpawnCommand.java
            ├── ReloadCommand.java
            ├── SetCommand.java             # Stufe, Erfahrung, Klasse
            ├── InspectCommand.java         # rein lesend
            └── AuditCommand.java           # die Leseseite von between()

rpg-plugin/src/main/resources/plugin.yml    # commands: entfällt evtl., permissions: wächst
```

**Structure Decision**: Das Gerüst liegt in `rpg-plugin/src/main/java/rpg/plugin/command/framework/`,
die Werkzeuge daneben in `.../command/admin/`. Grund: Brigadier und `CommandSourceStack` sind
Paper-Typen (Prinzip III schließt `rpg-core` aus), und die Registrierung braucht ohnehin die
Plugin-Instanz für den `LifecycleEventManager`. Die fünf vorhandenen Kommandos liegen bereits in
`rpg.plugin.command` und bleiben dort; `TrashCommand` zieht als einziges dorthin um und beendet
damit die Ausnahme, die B14 gerade abschafft.

## Reihenfolge, die sich aufdrängt

1. **Zuerst der Beweis auf dem echten Server, vor jedem Umbau.** Ob ein `plugin.yml`-Eintrag und
   eine Brigadier-Registrierung desselben Namens sich vertragen oder überschreiben, ist mit keinem
   Test zu klären (research.md §1). Ein Wegwerf-Kommando, beide Fassungen, echter Serverstart. Erst
   danach werden die sechs echten angefasst.
2. **Dann das Gerüst plus *ein* umgezogenes Kommando** — `/trash` ist das kleinste (kein Argument)
   und deckt trotzdem Registrierung, Rechteprüfung und Konsolenfall ab.
3. **Dann die übrigen fünf.** Ab hier ist der Block sichtbar wertvoll, auch wenn kein einziges
   Admin-Werkzeug existiert: das ist die **MVP-Grenze** (US1 + US2).
4. **Dann die Werkzeuge**, in der Reihenfolge ihres Nutzens für die nächsten Abnahmen: Items geben
   (US3), Neuladen (US4), dann Kreaturen (US5), Setzen (US6), Einsicht (US7), Audit lesen (US8).

**US1 und US2 gehören in eine Hand** — die Rechteprüfung ist Teil des Gerüsts, nicht ein Aufsatz
darauf. **US3 bis US8 sind voneinander unabhängig** und können in beliebiger Reihenfolge oder
parallel entstehen, sobald das Gerüst steht.

## Complexity Tracking

| Abweichung | Warum nötig | Warum die einfachere Alternative nicht reicht |
|---|---|---|
| **ADR-1: `HordeRegistry.Entry` bekommt eine Herkunft** (`BUDGET` / `ADMIN`), `total()` und `countIn()` zählen nur `BUDGET` | FR-019a verlangt, dass handgesetzte Kreaturen gegen eine eigene Obergrenze zählen. Die Registry *ist* das Budget, und ADR-050 verlangt zugleich, dass jede getaggte Kreatur darin steht — sonst wird sie beim Chunk-Laden entfernt. Beides zusammen geht nur mit einer Unterscheidung im Eintrag. | Ein zweiter Bestand neben der Registry vermeidet die Änderung an `Entry` nicht wirklich: B10s Budgetrechnung müsste ihn trotzdem kennen. Man bezahlt dieselbe Berührung, verteilt sie auf zwei Stellen und riskiert, dass beide auseinanderlaufen — genau die zweite Wahrheit, vor der `MobKinds` im eigenen Javadoc warnt und die ADR-050 gerade beseitigt hat. |
| **ADR-2: kein Setzen einzelner Attribute** (FR-024a) — eine ausdrückliche Absage, keine Erweiterung | `StatEngine` ist „the only way in" für B04 und hat bewusst keinen Setzer. Die Absage muss festgehalten werden, weil das Blockdokument „Level/**Werte** setzen" versprach und die Einschränkung sonst wie ein Versehen aussieht. | Ein siebter `SourceKind` bräuchte einen Platz in der Summationsreihenfolge — der Grundlage der Zusage „gleiche Quellen, gleiche Zahlen" — und der Wert wäre nach dem nächsten Anmelden trotzdem weg, weil B04 Beiträge nicht persistiert. Ein Werkzeug, das Dauerhaftigkeit verspricht und sie nicht liefert, ist schlechter als keines. |

**Nicht in dieser Tabelle, weil keine Abweichung, sondern Aufräumen:**
`ItemModule.notifyReloaded()` heißt zu `applyReloadedConfig()` um (die anderen fünf Module heißen
schon so), und `TrashCommand` zieht von `rpg-platform` nach `rpg-plugin`. Beides klein, beides
beseitigt eine Falle statt sie zu dokumentieren.
