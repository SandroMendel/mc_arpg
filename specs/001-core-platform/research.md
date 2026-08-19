# Phase 0 Research: B01 · Core & Plattform

Alle offenen technischen Fragen aus dem Technical Context sind hier aufgelöst. Es bleibt
keine `NEEDS CLARIFICATION`-Markierung übrig.

## Build-System

**Decision**: Gradle mit Kotlin DSL (`build.gradle.kts`), Multi-Projekt-Build mit
Versionskatalog (`libs.versions.toml`) für gemeinsame Abhängigkeitsversionen über die
fünf Module hinweg.

**Rationale**: Gradle ist der De-facto-Standard im Paper-/Bukkit-Plugin-Ökosystem;
`paperweight-userdev` (Remapping gegen Mojang-Mappings, Paper-API-Dependency-Handling)
ist primär für Gradle gebaut. Multi-Projekt-Builds mit klar erzwingbarer
Abhängigkeitsrichtung (`plugin → platform → core`, Constitution III.2) sind in Gradle über
`project(":rpg-core")`-Dependencies direkt abbildbar und vom Build selbst durchsetzbar —
ein Modul kann technisch nicht auf ein Modul zugreifen, das es nicht als Gradle-Dependency
deklariert.

**Alternatives considered**:
- **Maven**: Weiter verbreitet in klassischen Java-Enterprise-Projekten, aber deutlich
  verbose bei Multi-Modul-Setups und ohne direkten `paperweight`-Support; hätte manuelles
  Mapping-Handling erfordert.

## Konfigurationsformat

**Decision**: YAML (SnakeYAML als Parser), mit einer eigenen, deklarativen
Schema-Validierungsschicht (kein externes JSON-Schema-Tool, da YAML/JSON-Schema-Bridging
zusätzliche Komplexität ohne Mehrwert für dieses Projekt bringt).

**Rationale**: YAML ist die etablierte Konvention im Bukkit-/Paper-Ökosystem
(`plugin.yml`, `config.yml`); Server-Betreiber und Moderatoren, die Balancing-Werte
anpassen (B16), sind mit dem Format bereits vertraut. SnakeYAML ist ausgereift, ohne
zusätzliche Runtime-Abhängigkeiten über das hinaus, was Paper selbst schon mitbringt.

**Alternatives considered**:
- **HOCON**: Bietet Includes/Substitutionen, was für stark verschachtelte
  Balancing-Configs (B11 Item-Templates, B16) technisch attraktiv wäre, ist aber im
  Minecraft-Server-Ökosystem unüblich und für Server-Betreiber ungewohnt — höhere
  Einstiegshürde ohne zwingenden funktionalen Vorteil gegenüber einer sauber strukturierten
  YAML-Hierarchie mit eigener Schema-Validierung.
- **TOML**: Stringenter als YAML, aber schwach bei tief verschachtelten Strukturen und
  Listen-von-Objekten (z. B. mehrere Modifikatoren je Item-Template) — für den
  Content-Umfang dieses Projekts (B16) ungeeignet.

## Dependency Injection / Service-Registry

**Decision**: Eigene, leichtgewichtige, reflection-freie Service-Registry in `rpg-core`
(einfache `Map<ModuleId, Module>`-Registry mit explizit deklarierten Abhängigkeiten und
manueller Konstruktor-Injektion), **kein** DI-Framework (weder Guice noch Dagger).

**Rationale**: Die Modulanzahl ist mit 17 Architekturblöcken überschaubar — der
Hauptvorteil von Reflection-DI (Verwaltung sehr großer, dynamischer Objektgraphen) trägt
hier nicht. Ein Bukkit-Plugin läuft in einem mit anderen Plugins geteilten Classloader/JVM
— zusätzliche DI-Bibliotheken müssen relociert/geshadet werden, was ein bekanntes Risiko
für Klassenlader-Konflikte in der Paper-Plugin-Welt ist. Eine eigene Registry mit
explizit deklarierten Abhängigkeiten liefert außerdem direkt die in FR-001/FR-011
geforderte deterministische Startreihenfolge und Zyklus-Erkennung (einfache
topologische Sortierung über einen gerichteten Graphen), ohne Framework-Magie zu
verstecken. Das folgt demselben Muster wie die eigene Scheduler-Abstraktion (ADR-007).

**Alternatives considered**:
- **Google Guice**: Reflection-basiert, ausgereift, aber Laufzeit-Reflection-Kosten beim
  Bootstrap und zusätzliches Shading-Risiko; verdeckt die für FR-001/FR-011 geforderte
  explizite, nachvollziehbare Abhängigkeitsauflösung hinter Framework-Konventionen.
- **Dagger**: Compile-Time-Codegen vermeidet Laufzeit-Reflection, aber Annotation-
  Processing-Setup in einem Multi-Modul-Gradle-Build erhöht die Build-Komplexität für
  einen vergleichsweise kleinen Objektgraphen (5–17 Module).

## Modul-Abhängigkeitsauflösung & Zyklus-Erkennung

**Decision**: Deterministische Startreihenfolge über topologische Sortierung
(Kahn-Algorithmus) des von den Modulen deklarierten Abhängigkeitsgraphen; bei verbleibenden
Knoten nach dem Durchlauf (= Zyklus) wird der Start mit einer Meldung abgebrochen, die die
beteiligten Modul-Bezeichner nennt.

**Rationale**: Kahn-Algorithmus ist einfach, gut testbar (FR-011, Edge Case „zyklische
Abhängigkeit") und liefert bei mehreren gültigen Reihenfolgen ein deterministisches
Ergebnis, wenn Module zusätzlich nach ihrem stabilen String-Bezeichner sekundär sortiert
werden (verhindert nicht-deterministisches `HashMap`-Iterationsverhalten).

## Event-Bus-Dispatchstrategie

**Decision**: Synchroner In-Process-Dispatch (Aufruf aller Abonnenten direkt im
Publisher-Kontext), mit `try/catch` je Abonnent für Fehlerisolation (FR-006a). Kein
eigener Thread-Pool im Event-Bus selbst — ob ein Ereignis synchron (Tick-Kontext) oder
asynchron ausgelöst wird, bestimmt der Publisher über die Scheduler-Abstraktion, nicht der
Event-Bus.

**Rationale**: Entspricht Constitution I.4 (Ergebnisse asynchroner Arbeit werden über
einen expliziten Übergabepunkt zurückgeführt) — der Event-Bus selbst bleibt ein einfacher,
synchroner Mechanismus, der bereits threadgebunden korrekt eingesetzt werden muss;
komplexes eigenes Threading im Bus würde eine zweite, verdeckte Nebenläufigkeitsquelle
neben der Scheduler-Abstraktion schaffen.

## Modul-Lifecycle-Zustände

**Decision**: Vier Zustände — `INITIALIZING → ACTIVE`, `INITIALIZING → FAILED`,
`ACTIVE → STOPPING → STOPPED`. Ein Modul im Zustand `FAILED` blockiert den Bootstrap
(Fail-Fast), ein Modul, das während `STOPPING` das 10-Sekunden-Zeitlimit überschreitet,
wird zwangsterminiert und als `STOPPED (forced)` protokolliert.

**Rationale**: Deckt genau die in Spec FR-009/FR-012/FR-013 und den Edge Cases
beschriebenen Übergänge ab, ohne zusätzliche Zustände einzuführen, die keine Anforderung
trägt.
