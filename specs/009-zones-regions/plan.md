# Implementation Plan: B09 · Zonen & Regionen

**Branch**: `009-zones-regions` | **Date**: 2026-08-23 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/009-zones-regions/spec.md`

## Summary

Sechs benannte Regionen über die Levelbänder 1–60, jede eine Zone mit einem Schutzkern um ihren
Spawn. Die Geometrie ist eine Liste von Quadern, die beim Laden zu einer Abbildung `Chunk → Zone`
verdichtet wird; die Abfrage ist damit ein Lookup und nie eine Iteration. Darauf setzen fünf
Verhaltensweisen auf: eine Warnung statt einer Sperre unter dem Levelband, PvP als Zonenregel
(Vorgabe aus, im Schutzkern immer aus), der Tod führt in den Schutzkern der eigenen Region, ein
Kampf-Logout gilt als Tod, und Wegpunkt-Kristalle bringen Spieler gegen Coins zwischen den Regionen
hin und her.

**Der Kern des technischen Entwurfs ist ein einziger Index, der viermal benutzt wird.** Region,
Schutzkern, Spawn-Bereich und Kristall-Auslösebereich haben dieselbe Geometrieform, also gibt es ein
Geometriesystem statt vier — und derselbe Chunk-Schlüssel beantwortet „in welcher Zone stehe ich",
„bin ich geschützt" und „habe ich einen Kristall angeklickt".

**Phase 0 hat eine Anforderung der Spec widerlegt.** FR-050b verlangte, dass Buchung und Versetzung
„zusammen gelten". Das ist nicht zusagbar: die Buchung liegt in B08b, die Versetzung in Paper, und
eine gemeinsame Transaktion gibt es nicht — B08bs Vertrag schliesst eine Reservierung sogar
ausdrücklich aus. Zusagbar ist die Wirkung: abbuchen, versetzen, bei Fehlschlag mit **eigenem Grund**
zurückbuchen, alles im selben Tick. Die Anforderung ist entsprechend umformuliert und trägt die
Begründung; Einzelheiten in [research.md](./research.md) R1.

## Technical Context

**Language/Version**: Java 25 (ADR-001)

**Primary Dependencies**: Paper 26.2 API (nur in `rpg-platform` und `rpg-plugin`), `rpg-core`
kompiliert und testet **ohne** Bukkit auf dem Klassenpfad

**Storage**: PostgreSQL über B02s Schreib-Puffer. Ein neuer Aggregattyp
`CHARACTER_ZONE_STATE`, zwei Tabellen, Migration `V9_1__character_zone_state.sql`

**Testing**: JUnit ohne Server für die gesamte Zonenlogik; MockBukkit für die Listener;
Testcontainers gegen eine echte PostgreSQL-Instanz für die neuen Tabellen (Prinzip VII.2)

**Target Platform**: Paper-Server, eine Instanz (ADR-002)

**Project Type**: Gradle-Mehrmodulprojekt, `plugin → platform → core`

**Performance Goals**: Zonenzuordnung für 200 Spieler unter **0,5 ms** (SC-001), als wiederholbare
Messung ohne Server. Tickbudget ≤ 5 ms. Keine wiederkehrende Aufgabe je Spieler

**Constraints**: Paper-API nur im Tick; kein Datenbankzugriff je Bewegung, Zonenwechsel oder Warnung;
kein Boxing im heissen Pfad; `PlayerMoveEvent` ist eines der häufigsten Ereignisse des Servers

**Scale/Scope**: sechs Regionen, Richtgröße 6.000×6.000 bis 10.000×10.000 Blöcke (≈ 390.000 Chunks),
150 gleichzeitige Spieler als Zielprofil

**Keine offenen Punkte.** Alle zehn an `/plan` übergebenen Fragen sind in
[research.md](./research.md) beantwortet, zwei weitere kamen bei der Prüfung dazu.

## Constitution Check

*GATE: Vor Phase 0 bestanden, nach Phase 1 erneut geprüft.*

Constitution **1.1.0** (Prinzip VII am 2026-08-23 geändert, ADR-031).

| Prinzip | Beurteilung |
|---|---|
| **I. Nebenläufigkeit** | ✅ Der Index wird beim Laden gebaut und zur Laufzeit **nur gelesen** — kein veränderlicher globaler Zustand im Gameplay-Pfad. Kein blockierender Aufruf im Tick: Freischaltungen gehen über B02s Puffer, nicht über eine synchrone Abfrage. Versetzen und Buchen laufen im Tick, wo sie hingehören. **Der Block registriert keine einzige Aufgabe beim `Scheduler`** — die Abwesenheit ist der Beleg |
| **II. Performance** | ✅ Räumlicher Index statt linearer Suche (FR-005). Keine wiederkehrende Aufgabe je Spieler; die Auswertung ist ereignisgesteuert. Der Bewegungswächter ist Ganzzahlarithmetik auf Blockkoordinaten ohne Allokation, der Index hat `long`-Schlüssel ohne Boxing. Kein Datenbankzugriff je Bewegung (FR-063). Die Ratensperre für den Rechtsklick ist zeitstempelbasiert und lazy |
| **III. Architektur** | ⚠️ **Drei Abweichungen, alle als ADR festgehalten** — siehe *Complexity Tracking*. Im Übrigen eingehalten: `rpg-core` bleibt bukkit-frei, die Richtung `plugin → platform → core` ist gewahrt, der Zugriff läuft nur über [contracts/zone-api.md](./contracts/zone-api.md), und `Zone` ist niemals `World` |
| **IV. Datenhaltung** | ✅ Versionierte Migration. Der Speicher-Cache ist autoritativ, solange der Spieler online ist. Es werden keine berechneten Werte gespeichert — die Zone eines Charakters wird aus seiner Position ermittelt, nie geschrieben |
| **V. Datengetriebenes Design** | ✅ Alle Zonen, Bänder, Schalter, Koordinaten und Preise stehen in `zones.yml`, beim Start schemageprüft mit Fail-Fast. Eine siebte Region braucht keine Codeänderung (SC-003). Kein Spielertext im Code; auch die Zonennamen laufen über Message-Schlüssel |
| **VI. Korrektheit & Sicherheit** | ✅ Der Server entscheidet alles; der Client liefert nur einen Rechtsklick, und der ist ratenbegrenzt. Eine Ausnahme in der Zonenauswertung wird örtlich begrenzt und protokolliert (FR-064). Kein Reflection, kein NMS |
| **VII. Tests** | ✅ Die gesamte Zonenlogik ist serverfrei geprüft (SC-014). Die neuen Tabellen werden gegen eine echte PostgreSQL-Instanz getestet, nicht gegen Mocks. **Lasttests sind seit ADR-031 keine Bedingung** dafür, dass dieser Block fertig ist; SC-001 ist eine Messung ohne Server |
| **VIII. Sprache** | ✅ Diese Unterlagen deutsch, Code, Bezeichner, Config-Keys und Spielertexte englisch |

### Erneute Prüfung nach Phase 1

Der Entwurf hat **keine** neue Abweichung erzeugt. Zwei Beobachtungen:

- **Prinzip II hätte gebrochen werden können und tut es nicht.** FR-020 („keine Neubewertung
  innerhalb eines Chunks") und FR-016 („Kernwechsel feuert") widersprechen sich, weil eine
  Kerngrenze mitten durch Chunks läuft. Der naheliegende Ausweg — jede Bewegung auswerten — hätte
  einen Index-Zugriff in das häufigste Ereignis des Servers gelegt. Der Grenzchunk-Marker
  (research.md R4) löst beides: teuer wird es nur in den wenigen Chunks, in denen eine Grenze
  verläuft.
- **Prinzip III wird an einer Stelle *nicht* gebrochen, obwohl es naheliegt.** Die Reise könnte
  bequem in B08b gebucht *und* ausgeführt werden. Das hätte B08b Paper anfassen lassen — einem
  Schicht-1-Block ohne Weltbezug. Stattdessen ruft B09 auf und versetzt selbst.

## Project Structure

### Documentation (this feature)

```text
specs/009-zones-regions/
├── plan.md                     # Diese Datei
├── research.md                 # Phase 0 — zwölf Fragen, elf Entscheidungen, ein Widerspruch
├── data-model.md               # Phase 1 — Werte, Index, Ereignisse, Tabellen
├── quickstart.md               # Phase 1 — vier Abschnitte, 47 Serverschritte
├── contracts/
│   ├── zone-api.md             # die öffentliche Schnittstelle, ab jetzt ADR-pflichtig
│   └── zone-config.md          # zones.yml und die Message-Schlüssel
├── checklists/
│   └── requirements.md         # 16/16, über beide /clarify-Runden unverändert
└── tasks.md                    # Phase 2 — von /speckit-tasks, nicht von hier
```

### Source Code (repository root)

```text
rpg-core/src/main/java/rpg/core/zone/
├── package-info.java           # die Blockgrenze: Geometrie und Regeln hierher, Paper nirgends
├── Cuboid.java                 # zwei Ecken, Y optional
├── Area.java                   # Liste von Quadern; touchedChunks() nur beim Laden
├── LevelBand.java              # einschliessende Grenzen
├── SafeCore.java               # Bereich + der eine Respawn-Punkt
├── SpawnArea.java              # Kennung + Geometrie, nichts sonst
├── WaypointCrystal.java        # Kennung + Auslösebereich + Preis, kein Ziel
├── Zone.java
├── ChunkZoneIndex.java         # long-Schlüssel, Grenzchunk-Marker, nur lesend
├── Zones.java                  # die Abfrage (Schnittstelle)
├── DefaultZones.java
├── ZoneConfig.java
├── ZoneConfigSchema.java       # Fail-Fast, die zehn Startverweigerungen
├── ZoneChangedEvent.java
├── SafeAreaCrossedEvent.java
├── ZoneDamagePermission.java   # umschliesst B05s Vorgaberegel, ersetzt sie nicht doppelt
├── ZoneWorldCondition.java     # löst B08s isOpenWorld ein
├── Waypoints.java              # Freischaltungen (Schnittstelle)
├── WaypointUnlockRepository.java
├── Travel.java                 # travelTo + TravelResult
├── DefaultTravel.java          # abbuchen → versetzen → bei Fehlschlag zurückbuchen
├── Teleporter.java             # Schnittstelle; die Plattform setzt sie um
├── PendingRespawn.java         # der Merker für den Kampf-Logout
├── ZoneMessageKeys.java
└── ZoneModule.java             # start(), applyReloadedConfig()

rpg-platform/src/main/java/rpg/platform/zone/
├── package-info.java           # hier und nur hier wird Paper angefasst
├── BukkitPositions.java        # Location → WorldPosition
├── BukkitTeleporter.java
├── ZoneMovementListener.java   # PlayerMoveEvent, Grenzchunk-Wächter
├── ZoneJoinListener.java       # Zuordnung bei Anmeldung, ausstehender Respawn
├── ZoneQuitListener.java       # Kampf-Logout → Tod
├── ZoneRespawnListener.java    # Tod → Schutzkern der eigenen Region
├── CrystalInteractListener.java# PlayerInteractEvent, Index-Lookup, Ratensperre
├── WaypointMenu.java           # nach dem Muster von CurrencyMenu (ADR-028)
└── WaypointMenuListener.java

rpg-persistence/src/main/java/rpg/persistence/zone/
├── package-info.java
├── JdbcWaypointUnlockRepository.java
├── JdbcPendingRespawnRepository.java
└── ZonePersistenceModule.java  # die drei Eintragungen nach ADR-015 Punkt 7

rpg-persistence/src/main/resources/db/migration/
└── V9_1__character_zone_state.sql

rpg-plugin/src/main/resources/
├── zones.yml                   # sechs Regionen, provisional: true
└── messages.yml                # erweitert um den zone.*-Baum

rpg-plugin/src/main/java/rpg/plugin/RpgPlugin.java
└── Verdrahtung: Modul, Listener, setPermission, applyReloadedConfig (ADR-012)
```

**Structure Decision**: Vier Module, wie jeder Block vor ihm. Die Aufteilung folgt der einen Frage,
die Prinzip III stellt — *braucht es Paper?* Quader, Index, Levelband, Schadenserlaubnis und die
Reiseabfolge brauchen es nicht und liegen in `rpg-core`; die Übersetzung eines Bukkit-Orts, alle
Listener, das Fenster und das Versetzen brauchen es und liegen in `rpg-platform`. Die Freischaltungen
liegen in `rpg-persistence`, weil dort die Aggregattypen und die Migrationen zu Hause sind. Neue
Eingriffe in fremde Pakete gibt es genau drei, und alle drei stehen unten.

`WorldPosition` wird **nicht** neu erfunden: der Typ liegt bereits in `rpg-core/scheduler` und trägt
in seinem Javadoc dieselbe Begründung, die dieser Block bräuchte (research.md R2).

## Complexity Tracking

> Drei Eingriffe über die Blockgrenze hinaus. Alle drei sind vor Beginn der Umsetzung als ADR
> festgehalten — die Governance-Regel der Constitution verlangt für eine Abweichung von Prinzip III
> eine ausdrückliche, begründete Ausnahme.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|--------------------------------------|
| **Auswahlfenster und Rechtsklick in einem Schicht-2-Block** (B13-Gebiet) — ADR-032, befristet bis B13 | Ein Wegpunktsystem ohne Auswahlmöglichkeit ist kein Wegpunktsystem. Auf B13 zu warten hiesse, bis dahin gar kein Reisen zu haben — und der Rückweg vom *Pale Wilds* ins *Greenfields* führte jedes Mal über fünf Regionen | Auf B13 warten: das Reisen ist kein Beiwerk dieses Blocks, sondern der Grund, warum sechs getrennte Regionen bewohnbar sind. Dieselbe Anordnung hat ADR-028 für B08bs Kontofenster schon getroffen, und `CurrencyMenu` ist das Muster, an dem sich `WaypointMenu` orientiert |
| **Zwei neue Buchungsgründe in B08b** (`WAYPOINT_TRAVEL`, `WAYPOINT_REFUND`) — abgeschlossener Block, ADR-032 | Der Verlauf muss eine Reise von einem Einkauf trennen (FR-050d), und eine **Rückbuchung** von einer gewöhnlichen Gutschrift (FR-050f). Ohne den zweiten Grund könnte niemand nachsehen, wie oft eine Reise scheitert | Einen bestehenden Grund mitbenutzen: `VENDOR_PURCHASE` für eine Reise wäre eine Unwahrheit im Verlauf, und genau der Verlauf ist B08bs Zusage. Nur *einen* neuen Grund nehmen: dann wäre eine Rückbuchung im Verlauf nicht von einer Gutschrift zu unterscheiden |
| **Ein vierter Wert in `DeathCause`** (`LOGOUT`) in B05 — abgeschlossener Block, ADR-030 | B12 soll später „gestorben" und „abgehauen" trennen können, und das Protokoll soll ehrlich bleiben | Den Logout-Tod als `COMBAT` führen: dann wäre die Statistik unwahr und die Regel unauffindbar. Das Javadoc von `DeathCause` begründet ausdrücklich, dass die Aufzählung Fälle *unterscheiden* soll — ein vierter Wert ist damit vereinbar, seine Ergänzung durch einen späteren Block aber ADR-pflichtig |

**Was ausdrücklich keine Abweichung ist:** der Austausch von `DamagePermission`. B05 hat diese Stelle
selbst als Austauschpunkt angelegt und mit `SinglePermissionPointTest` bewacht; B09 tut genau das,
wofür sie da ist. Der Nachweis ist, dass B05s vorhandene Tests **unverändert** grün bleiben (SC-008).
