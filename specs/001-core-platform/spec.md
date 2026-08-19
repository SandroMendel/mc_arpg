# Feature Specification: B01 · Core & Plattform

**Feature Branch**: `001-core-platform`

**Created**: 2026-08-19

**Status**: Implementiert (2026-08-19) — 42/42 Tasks, 88 Tests grün, auf echtem Paper-Server verifiziert

**Input**: Blocksteckbrief `blocks/B01-core-platform.md` — das technische Fundament, gegen
das alle anderen Blöcke (B02–B17) des Minecraft-RPG-Plugins entwickelt werden.

## Clarifications

### Session 2026-08-19

- Q: Wie viele Sekunden Zeitlimit soll ein Modul beim Shutdown maximal bekommen, bevor es zwangsterminiert wird? → A: 10 Sekunden pro Modul
- Q: Wie werden Module eindeutig identifiziert? → A: Stabiler, sprechender String-Bezeichner (z. B. `"stat-engine"`), unabhängig vom Code
- Q: Werden bei einer Ausnahme in einem Event-Bus-Abonnenten die übrigen Abonnenten desselben Ereignisses trotzdem benachrichtigt? → A: Ja, verbleibende Abonnenten werden trotzdem benachrichtigt, der Fehler wird isoliert protokolliert
- Q: Gilt Hot-Reload für alle Module gleichzeitig oder selektiv pro Modul? → A: Globaler Reload — alle Module laden ihre Konfiguration gleichzeitig neu
- Q: Gibt es einen maximal akzeptablen Zeitrahmen für den kompletten Server-Bootstrap? → A: 30 Sekunden

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Server startet zuverlässig oder bricht klar erkennbar ab (Priority: P1)

Als Server-Betreiber starte ich den Server mit dem Plugin. Entweder starten alle Module
fehlerfrei und ich sehe im Log den Status jedes Moduls, oder der Start bricht wegen eines
konkreten, klar benannten Konfigurationsfehlers ab — nie mit einem stillen Fehlverhalten
zur Laufzeit.

**Why this priority**: Ohne einen zuverlässigen, nachvollziehbaren Start ist kein anderer
Block (B02–B17) betreibbar. Das ist die absolute Grundvoraussetzung des gesamten Projekts.

**Independent Test**: Server mit gültiger Konfiguration starten → alle Module melden
Status im Log. Server mit absichtlich fehlerhafter Konfiguration starten → Start bricht ab,
Meldung nennt Datei, Pfad und erwarteten Wert.

**Acceptance Scenarios**:

1. **Given** eine vollständige, gültige Konfiguration, **When** der Server startet,
   **Then** initialisieren sich alle Module in einer aus ihren Abhängigkeiten abgeleiteten,
   deterministischen Reihenfolge und melden ihren Status im Log.
2. **Given** eine fehlerhafte Konfiguration (z. B. fehlender Pflichtwert), **When** der
   Server startet, **Then** bricht der Start ab und die Meldung nennt Datei, Pfad und
   erwarteten Wert.
3. **Given** ein einzelnes Modul wirft während der Laufzeit eine unerwartete Ausnahme,
   **When** dies im laufenden Betrieb passiert, **Then** wird der Fehler lokal begrenzt
   protokolliert und der Server-Tick sowie andere Module laufen unbeeinträchtigt weiter.

---

### User Story 2 - Neue Module entstehen ohne Kopplung an Interna anderer Module (Priority: P2)

Als Entwickler eines nachfolgenden Blocks (z. B. B04 Stat-Engine) registriere ich meine
Dienste über die Modul-Registry, kommuniziere über den internen Event-Bus und plane
Aufgaben über die Scheduler-Abstraktion — ohne direkten Zugriff auf Interna anderer Module
und ohne den globalen Bukkit-Scheduler zu benutzen.

**Why this priority**: Jeder weitere Block (B02–B17) hängt von dieser Erweiterbarkeit ab.
Ohne sie entsteht Direktkopplung, die den Folia-Migrationspfad (ADR-007) und die
Testbarkeit der Domänenschicht (Constitution Prinzip III/VII) verhindert.

**Independent Test**: Ein neues Testmodul registriert einen Dienst, abonniert ein Ereignis
eines anderen Moduls und plant eine location-gebundene Aufgabe — alles ohne Kenntnis der
internen Implementierung des anderen Moduls und ohne direkten Bukkit-Scheduler-Aufruf.

**Acceptance Scenarios**:

1. **Given** zwei unabhängige Module, **When** Modul A einen Dienst registriert,
   **Then** kann Modul B diesen Dienst über die Registry beziehen, ohne Modul A statisch
   zu referenzieren.
2. **Given** ein Modul veröffentlicht ein Ereignis auf dem internen Event-Bus,
   **When** ein anderes Modul dieses Ereignis abonniert hat, **Then** wird es zuverlässig
   benachrichtigt, ohne eine direkte Referenz auf den Herausgeber zu benötigen.
3. **Given** mehrere Module haben dasselbe Ereignis abonniert, **When** einer der
   Abonnenten bei der Verarbeitung eine unerwartete Ausnahme wirft, **Then** werden die
   übrigen Abonnenten trotzdem benachrichtigt und der Fehler wird isoliert protokolliert.
4. **Given** ein Modul möchte eine wiederkehrende oder verzögerte Aufgabe planen,
   **When** es die Scheduler-Abstraktion nutzt, **Then** ist die Aufgabe eindeutig als
   synchron (Server-Tick) oder asynchron gekennzeichnet und ausschließlich location- oder
   entity-gebunden — ein globaler, nicht ortsgebundener Scheduling-Aufruf steht nicht zur
   Verfügung.

---

### User Story 3 - Konfiguration im laufenden Betrieb ändern (Priority: P3)

Als Server-Betreiber ändere ich einen Konfigurationswert (z. B. ein Balancing-Wert aus
B16) und lade ihn im laufenden Betrieb neu, ohne den Server neu zu starten und ohne dass
aktive Spieler ihren Fortschritt verlieren.

**Why this priority**: Wichtig für den Betrieb (schnelle Balancing-Iteration ohne
Downtime), aber der Server ist auch ohne Hot-Reload grundsätzlich betreibbar (Neustart als
Fallback) — daher niedrigere Priorität als P1/P2.

**Independent Test**: Konfigurationswert bei laufendem Server ändern und Reload auslösen
→ neuer Wert wirkt, keine aktive Spielersitzung verliert Daten.

**Acceptance Scenarios**:

1. **Given** ein laufender Server mit aktiven Spielersitzungen, **When** ein Betreiber
   einen Reload auslöst, **Then** laden alle Module ihre Konfiguration gleichzeitig neu,
   ohne dass eine Spielersitzung getrennt wird oder Daten verliert.
2. **Given** eine beim Reload fehlerhafte neue Konfiguration, **When** der Reload
   ausgeführt wird, **Then** bleibt die zuvor gültige Konfiguration aktiv und der Betreiber
   erhält eine klare Fehlermeldung statt eines Absturzes.

### Edge Cases

- Was passiert, wenn zwei Module eine zyklische Abhängigkeit deklarieren? → Start muss mit
  einer Meldung abbrechen, die den Zyklus benennt, statt in eine Endlosschleife oder eine
  zufällige Reihenfolge zu laufen.
- Was passiert, wenn zwei Module denselben String-Bezeichner verwenden? → Start muss mit
  einer den Konflikt benennenden Meldung abbrechen, statt eines der beiden Module still zu
  überschreiben.
- Wie reagiert das System, wenn während des Bootstraps bereits Spieler verbinden (Race
  zwischen Serverstart und erstem Join)? → Spieler dürfen erst nach vollständigem,
  erfolgreichem Bootstrap eine Sitzung erhalten.
- Was passiert, wenn ein Modul beim Herunterfahren nicht sauber terminiert (hängt fest)?
  → Nach 10 Sekunden wird das Modul zwangsterminiert; eine klare Fehlermeldung wird
  protokolliert, das Shutdown wird nicht unbegrenzt blockiert.
- Wie verhält sich ein Hot-Reload, der mitten in einem asynchronen Vorgang eines Moduls
  ausgelöst wird? → Laufende asynchrone Vorgänge dürfen nicht mit inkonsistentem Zustand
  abgebrochen werden.

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: System MUSS beim Start alle Module in einer aus deklarierten Abhängigkeiten
  abgeleiteten, deterministischen Reihenfolge initialisieren.
- **FR-001a**: System MUSS jedes Modul über einen eindeutigen, stabilen String-Bezeichner
  identifizieren und den Start abbrechen, falls zwei Module denselben Bezeichner
  verwenden.
- **FR-002**: System MUSS die Konfiguration beim Start gegen ein Schema validieren und den
  Start bei einer ungültigen Konfiguration abbrechen (Fail-Fast); die Fehlermeldung MUSS
  Datei, Pfad und erwarteten Wert benennen.
- **FR-003**: System MUSS eine Konfigurationsänderung im laufenden Betrieb (Hot-Reload)
  unterstützen, ohne den Server neu zu starten und ohne dass aktive Spielersitzungen Daten
  verlieren. Ein Reload ist global — alle Module laden ihre Konfiguration gleichzeitig neu;
  ein selektiver Reload einzelner Module ist nicht Teil dieser Spec.
- **FR-004**: System MUSS bei einem Reload mit fehlerhafter neuer Konfiguration die zuvor
  gültige Konfiguration beibehalten und darf nicht abstürzen.
- **FR-005**: System MUSS eine Modul-/Service-Registry bereitstellen, über die Module
  Dienste registrieren und Dienste anderer Module beziehen können, ohne direkte statische
  Kopplung.
- **FR-006**: System MUSS einen internen Event-Bus bereitstellen, über den Module
  Ereignisse veröffentlichen und abonnieren können, ohne sich gegenseitig referenzieren zu
  müssen.
- **FR-006a**: Wirft ein Abonnent bei der Verarbeitung eines Ereignisses eine unerwartete
  Ausnahme, MUSS das System den Fehler lokal begrenzt protokollieren und die
  Ereigniszustellung an die übrigen Abonnenten desselben Ereignisses fortsetzen.
- **FR-007**: System MUSS eine Scheduler-Abstraktion bereitstellen, die synchrone
  (Server-Tick-gebundene) und asynchrone Aufgaben bereits im Typsystem unterscheidbar
  macht.
- **FR-008**: Die Scheduler-Abstraktion DARF ausschließlich location- oder
  entity-gebundene Terminierung anbieten; ein globaler, nicht ortsgebundener
  Scheduling-Aufruf DARF in der öffentlichen Schnittstelle NICHT existieren.
- **FR-009**: System MUSS eine unerwartete Ausnahme innerhalb eines einzelnen Moduls
  lokal begrenzt abfangen und protokollieren, ohne den laufenden Server-Tick oder andere
  Module zu beeinträchtigen.
- **FR-010**: System MUSS bei Start, Reload und Shutdown den Status jedes Moduls
  strukturiert protokollieren.
- **FR-011**: System MUSS eine zyklische Abhängigkeit zwischen Modulen beim Start erkennen
  und den Start mit einer den Zyklus benennenden Meldung abbrechen.
- **FR-012**: System MUSS beim Shutdown pro Modul ein Zeitlimit von 10 Sekunden für die
  Terminierung durchsetzen und danach zwangsterminieren, statt unbegrenzt zu blockieren.
- **FR-013**: System MUSS verhindern, dass ein Spieler eine Sitzung erhält, bevor der
  Bootstrap vollständig und erfolgreich abgeschlossen ist. Der vollständige Bootstrap
  (alle Module initialisiert, bereit für den ersten Join) MUSS innerhalb von 30 Sekunden
  abgeschlossen sein.
- **FR-014**: System MUSS eine klar abgegrenzte, vorerst intern gehaltene
  Schnittstellengrenze definieren, über die künftig eine öffentliche Erweiterungs-API für
  Drittplugins bereitgestellt werden könnte, ohne bestehende Module umzubauen.
- **FR-015**: Die über Registry und Event-Bus bereitgestellte Domänenlogik MUSS, soweit
  sie keine Plattform-Abhängigkeit besitzt, ohne einen laufenden Server aufrufbar und
  testbar sein.

### Key Entities

- **Modul**: Ein Baustein des Plugins (z. B. B04, B09), eindeutig identifiziert durch einen
  stabilen, sprechenden String-Bezeichner (z. B. `"stat-engine"`), mit deklarierten
  Abhängigkeiten (Referenzen auf andere Modul-Bezeichner), einem Lifecycle-Status
  (initialisierend, aktiv, fehlerhaft, beendet) und über die Registry bereitgestellten
  Diensten.
- **Service-Registry-Eintrag**: Verknüpft eine Dienst-Schnittstelle mit ihrer aktuellen
  Implementierung; von anderen Modulen ausschließlich über die Schnittstelle abgerufen.
- **Konfigurations-Schema**: Definiert erlaubte Struktur und Werte einer
  Konfigurationsquelle; Grundlage für Fail-Fast-Validierung beim Start und bei Reload.
- **Ereignis**: Eine auf dem internen Event-Bus veröffentlichte Nachricht, die von
  beliebig vielen Modulen abonniert werden kann, ohne dass Herausgeber und Abonnent sich
  kennen.
- **Geplante Aufgabe**: Eine über die Scheduler-Abstraktion eingereichte Arbeit, eindeutig
  als synchron oder asynchron sowie als location- oder entity-gebunden gekennzeichnet.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Der Server startet mit vollständiger, gültiger Konfiguration in 100% der
  Testläufe fehlerfrei innerhalb von 30 Sekunden ab Plugin-Bootstrap bis zur
  Bereitschaft für den ersten Spieler-Join; jedes Modul meldet seinen Status im Log.
- **SC-002**: Eine absichtlich fehlerhafte Konfiguration verhindert den Start in 100% der
  Testfälle, mit einer Meldung, die Datei, Pfad und erwarteten Wert nennt.
- **SC-003**: Eine Konfigurationsänderung wird ohne Serverneustart übernommen, ohne dass
  eine zum Zeitpunkt der Änderung aktive Spielersitzung Daten verliert.
- **SC-004**: Eine unerwartete Ausnahme in einem einzelnen Modul führt in 100% der
  Testfälle nicht zum Absturz oder Einfrieren des Servers.
- **SC-005**: Es lässt sich nachweisen (Test oder statische Prüfung), dass im gesamten
  Projekt kein direkter, nicht ortsgebundener globaler Scheduling-Aufruf existiert.
- **SC-006**: Ein neues Modul lässt sich gegen Registry, Event-Bus und
  Scheduler-Abstraktion entwickeln, ohne eine Zeile Code eines bestehenden Moduls zu lesen
  oder zu verändern.
- **SC-007**: Ein Shutdown-Vorgang terminiert für jedes Modul innerhalb von maximal 10
  Sekunden, auch wenn ein einzelnes Modul nicht sauber reagiert.

## Assumptions

- Zielplattform ist Paper auf Minecraft 26.2 / Java 25 (ADR-001), ein einzelner Server
  ohne Proxy-Netzwerk mit 100–200 gleichzeitigen Spielern (ADR-002).
- Build-System, Konfigurationsformat (z. B. YAML/HOCON/TOML) und die konkrete
  DI-Bibliothek sind bewusst nicht Teil dieser Spec, sondern werden bei `/plan`
  entschieden.
- Eine öffentliche Erweiterungs-API für Drittplugins wird zum Start nicht implementiert;
  die Schnittstellengrenze wird jedoch so vorbereitet, dass sie später ergänzbar ist
  (Entscheidung 2026-08-19, siehe `blocks/B01-core-platform.md`).
- Die nachfolgenden Blöcke B02–B17 sind die primären Nutzer dieses Fundaments und bauen
  direkt auf Registry, Event-Bus und Scheduler-Abstraktion auf.
- Der Folia-Migrationspfad bleibt offen (ADR-007); die strikte location-/entity-gebundene
  Scheduler-Regel ist deshalb nicht verhandelbar, auch wenn Paper aktuell nur eine
  Haupt-Tick-Schleife hat.
