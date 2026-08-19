# Phase 1 Data Model: B01 · Core & Plattform

Abgeleitet aus den Key Entities in `spec.md`. Rein konzeptionell (Feldnamen/Typen als
Vertrag, keine Implementierungscode).

## Module

Repräsentiert einen registrierten Baustein des Plugins (z. B. `stat-engine`, `zones`).

| Feld | Typ | Regeln |
|---|---|---|
| `id` | String | Eindeutig, stabil, unveränderlich nach Registrierung. Start bricht ab, wenn zwei Module dieselbe `id` registrieren (Clarification 2026-08-19). |
| `dependencies` | List\<String\> | Referenzen auf `id`s anderer Module. Muss zyklenfrei sein (Kahn-Algorithmus, siehe research.md), sonst Start-Abbruch mit Zyklusbenennung. |
| `lifecycleState` | Enum | `INITIALIZING`, `ACTIVE`, `FAILED`, `STOPPING`, `STOPPED` (siehe research.md, Modul-Lifecycle). |
| `services` | List\<ServiceRegistryEntry\> | Über dieses Modul bereitgestellte Dienste. |

**Validierungsregeln**:
- `id` darf nicht leer sein und muss über den gesamten Bootstrap-Vorgang eindeutig bleiben.
- Ein Modul im Zustand `FAILED` verhindert den Übergang des Gesamtsystems in einen
  betriebsbereiten Zustand (Fail-Fast, FR-013).

## ServiceRegistryEntry

Verknüpft eine Dienst-Schnittstelle mit ihrer aktuellen Implementierung.

| Feld | Typ | Regeln |
|---|---|---|
| `serviceInterface` | Typ-Referenz | Die öffentliche Schnittstelle, über die andere Module zugreifen. |
| `implementation` | Instanz | Konkrete Implementierung; wird ausschließlich über `serviceInterface` referenziert (Constitution III.3 — kein Zugriff auf Interna). |
| `owningModuleId` | String → `Module.id` | Modul, das diesen Dienst bereitstellt; für Fehlermeldungen und Deregistrierung beim Shutdown. |

## ConfigSchema

Definiert erlaubte Struktur und Werte einer Konfigurationsquelle.

| Feld | Typ | Regeln |
|---|---|---|
| `schemaVersion` | Integer | Versioniert (Constitution IV.1); ermöglicht künftige Migrationspfade. |
| `fields` | List\<FieldDefinition\> | Pflicht-/Optional-Kennzeichnung je Feld, erwarteter Typ, ggf. Wertebereich. |
| `sourceFile` | Pfad | Für Fail-Fast-Fehlermeldungen (FR-002: Datei, Pfad, erwarteter Wert). |

**Validierungsregeln**:
- Eine Konfigurationsdatei, die nicht allen Pflichtfeldern ihres Schemas entspricht, wird
  beim Start abgelehnt (Fail-Fast) und beim Reload verworfen, wobei die zuvor gültige
  Konfiguration aktiv bleibt (FR-004).

## Event

Eine auf dem internen Event-Bus veröffentlichte Nachricht.

| Feld | Typ | Regeln |
|---|---|---|
| `type` | Typ-Referenz | Eindeutiger Ereignistyp; Abonnenten registrieren sich auf einen Typ. |
| `payload` | Objekt | Ereignisspezifische Nutzdaten, unveränderlich nach Veröffentlichung. |
| `publishedByModuleId` | String → `Module.id` | Für Diagnose/Logging bei Abonnenten-Fehlern. |

**Zustellregeln**:
- Wirft ein Abonnent eine Ausnahme, wird sie isoliert protokolliert; die Zustellung an
  verbleibende Abonnenten desselben Ereignisses wird fortgesetzt (FR-006a).

## ScheduledTask

Eine über die Scheduler-Abstraktion eingereichte Arbeit.

| Feld | Typ | Regeln |
|---|---|---|
| `executionMode` | Enum | `SYNC` (Server-Tick-gebunden) oder `ASYNC` — im Typsystem unterschieden (FR-007), nicht nur als Flag. |
| `binding` | Enum + Referenz | `LOCATION` (an eine Welt-Position gebunden) oder `ENTITY` (an eine Entity gebunden). Ein ungebundener globaler Modus existiert nicht (FR-008). |
| `submittedByModuleId` | String → `Module.id` | Für Diagnose. |

**Validierungsregeln**:
- Es gibt keinen dritten `binding`-Wert „global/ungebunden" — die öffentliche Schnittstelle
  bietet ihn syntaktisch nicht an (harte Durchsetzung von ADR-007 im Typsystem, nicht nur
  per Konvention).

## Beziehungen

```
Module 1───* ServiceRegistryEntry   (ein Modul stellt beliebig viele Dienste bereit)
Module 1───* Event                  (publishedByModuleId)
Module 1───* ScheduledTask          (submittedByModuleId)
Module *───* Module                 (dependencies, zyklenfrei — topologische Sortierung)
```
