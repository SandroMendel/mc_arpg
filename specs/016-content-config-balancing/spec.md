# Feature Specification: Content-Konfiguration & Balancing

**Status:** Specify-Entwurf (2026-09-12)
**Clarify-Grundlage:** `minecraft-rpg-spec/minecraft-rpg-spec/blocks/B16-content-config-balancing.md`
**Abhängig von:** B01
**Berührt:** B04–B11, B14

## User Scenarios & Testing

### User Story 1 — Betreiber bearbeitet versionierte Content-Dateien (Priority: P1)

Als Betreiber möchte ich spielrelevante Content- und Balancing-Werte in gebündelten YAML-Dateien
sehen und ändern können, damit eine Zahlenänderung keine Java-Änderung und keinen In-Game-Editor
braucht.

**Acceptance Scenarios:**

1. **Given** eine ausgelieferte B16-Datei, **When** der Betreiber sie im Plugin-Datenordner öffnet,
   **Then** findet er eine dokumentierte `schemaVersion` und die Werte nach Content-Typ gruppiert.
2. **Given** eine gültige Änderung an einem bestehenden Wert, **When** der Betreiber `/rpg reload`
   ausführt, **Then** verwenden die betroffenen Module den neuen Wert ohne Serverneustart.
3. **Given** ein nicht von B16 verwaltetes Betriebs- oder Textdokument, **When** B16 migriert wird,
   **Then** wird es nicht als Content-Datei umdeklariert.

### User Story 2 — Ungültiger Content wird früh und verständlich abgelehnt (Priority: P1)

Als Betreiber möchte ich bei einem Fehler Datei, Dokumentpfad, erwarteten Typ/Bereich und Istwert
sehen, damit ich eine Konfiguration reparieren kann, bevor sie Spielzustand beeinflusst.

**Acceptance Scenarios:**

1. **Given** eine fehlende Pflichtangabe, einen falschen Typ, einen ungültigen Wertebereich oder
   eine unbekannte `schemaVersion`, **When** der Server startet, **Then** startet er nicht und nennt
   die konkrete Datei und den konkreten Dokumentpfad.
2. **Given** eine syntaktisch gültige, aber semantisch inkonsistente Datei, **When** sie geladen wird,
   **Then** wird sie mit der verletzten Referenz- oder Invariante abgelehnt.
3. **Given** ein unbekannter Schlüssel in einem festen Abschnitt, **When** validiert wird,
   **Then** wird er nicht stillschweigend ignoriert; dynamische Entitäts-IDs bleiben nur dort erlaubt,
   wo das jeweilige Schema sie ausdrücklich vorsieht.

### User Story 3 — Hot-Reload bleibt atomar (Priority: P1)

Als Betreiber möchte ich mehrere Content-Dateien gemeinsam neu laden können, ohne dass Module einen
gemischten alten/neuen Zustand sehen.

**Acceptance Scenarios:**

1. **Given** alle B16-Dateien sind gültig, **When** `/rpg reload` erfolgreich endet, **Then** ist der
   neue Content-Snapshot für alle betroffenen Module sichtbar.
2. **Given** genau eine B16-Datei ist ungültig, **When** `/rpg reload` ausgeführt wird, **Then** bleibt
   der vollständige vorherige gültige Content-Snapshot aktiv.
3. **Given** eine gültige Änderung erzeugt eine ungültige Querverbindung, **When** neu geladen wird,
   **Then** wird nichts veröffentlicht und der Fehler nennt beide beteiligten Domänen/Schlüssel.
4. **Given** ein erfolgreicher oder abgelehnter Reload, **When** das Audit-Log betrachtet wird,
   **Then** ist die Aktion mit Ergebnis und Quelle nachvollziehbar.

### User Story 4 — Bestehender Content wird ohne unbeabsichtigtes Rebalancing migriert (Priority: P1)

Als Maintainer möchte ich die heute geltenden Werte in YAML überführen, ohne das Spielgefühl vor
der späteren Test- und Balancing-Phase zu verändern.

**Acceptance Scenarios:**

1. **Given** ein heutiger Default-Wert, **When** er in eine B16-Datei überführt wird, **Then** ist
   sein numerischer Wert und seine Einheit unverändert.
2. **Given** eine Legacy-Datei ohne `schemaVersion`, **When** die explizite Migration ausgeführt wird,
   **Then** entsteht eine versionierte Datei und die Quelldatei wird nicht stillschweigend überschrieben.
3. **Given** eine bereits migrierte Datei, **When** sie erneut migriert wird, **Then** ist der Vorgang
   idempotent und erzeugt keine zweite Zahlenänderung.

### User Story 5 — Balancing wird reproduzierbar ausgewertet (Priority: P2)

Als Designer möchte ich die versionierten YAML-Dateien mit einem externen, repo-lokalen Werkzeug
auswerten können, damit Schadenskurven, TTK und andere Kennzahlen reproduzierbar vergleichbar sind.

**Acceptance Scenarios:**

1. **Given** gültige YAML-Dateien und ein festgelegtes Szenario, **When** das Analysewerkzeug läuft,
   **Then** erzeugt es deterministische CSV-, JSON- und/oder Markdown-Ergebnisse.
2. **Given** ungültige YAML- oder Szenariodaten, **When** das Werkzeug läuft, **Then** endet es mit
   einem Fehler und erzeugt keinen gültigen Analysebericht.
3. **Given** ein Analysebericht, **When** er erneut mit identischen Eingaben erzeugt wird,
   **Then** unterscheiden sich die Ergebnisse nicht durch Uhrzeit, Zufall oder externe Dienste.
4. Das Werkzeug schreibt keine Laufzeitdatei in das Plugin und ist keine Runtime-Abhängigkeit.

## Requirements

### Functional Requirements

- **FR-001:** B16 MUST spielrelevante Content- und Balancing-Werte aus den fachlich betroffenen
  Bereichen B07–B11 in versionierten YAML-Dateien abbilden; operative, textuelle und reine
  Performance-Konfiguration bleibt beim jeweiligen Block.
- **FR-002:** B16 MUST die Dateien gebündelt pro Content-Typ organisieren; eine einzelne Entität
  erhält keine eigene Datei, sofern keine fachliche oder technische Grenze dies zwingend erfordert.
- **FR-003:** Jede B16-verwaltete Datei MUST am Dokumentanfang eine positive ganzzahlige
  `schemaVersion` besitzen.
- **FR-004:** Die geladene `schemaVersion` MUST exakt zur unterstützten Schema-Version passen.
  Fehlende, unbekannte oder nicht unterstützte Versionen MUST den Start beziehungsweise Reload
  fail-fast ablehnen.
- **FR-005:** B16 MUST YAML als einziges Laufzeitformat verwenden; HOCON und TOML werden nicht als
  parallele Parser- oder Dateiformate eingeführt.
- **FR-006:** Die versionierten Default-Dateien MUST im `rpg-content`-Modul liegen. `rpg-content`
  besitzt Content-Definitionen, Defaults, domänenspezifische Schemas und Querverbindungsprüfungen;
  es hängt nur von `rpg-core` ab und kennt weder Bukkit noch Paper.
- **FR-007:** `rpg-core` MUST die generischen Verträge und serverfreien Validierungsprimitive behalten;
  `rpg-platform` MUST nur das YAML-Parsing beitragen; `rpg-plugin` MUST Bootstrap, Kopieren der
  Defaults in den Datenordner und `/rpg reload` verdrahten.
- **FR-008:** Die erste Migration MUST die bestehenden Laufzeitdateinamen (`classes.yml`,
  `abilities.yml`, `progression.yml`, `combat.yml`, `zones.yml`, `mobs.yml`, `items.yml`,
  `currency.yml` und die jeweils B16-verwalteten Teilbereiche weiterer Dateien) im Plugin-Datenordner
  beibehalten, damit bestehende Module und Betreiberpfade nicht unnötig brechen.
- **FR-009:** Die B16-Schemas MUST Pflichtfelder, Typen, Wertebereiche, Einheiten und feste
  Abschnittsnamen prüfen. Dynamische Entitäts-IDs und freie Schlüssel sind nur in explizit
  deklarierter Registry-Struktur zulässig.
- **FR-010:** B16 MUST unbekannte feste Schlüssel und doppelte YAML-Schlüssel ablehnen. Eine
  Ausnahmeregel für offene Textschlüssel wie `messages.yml` darf nicht als allgemeiner Bypass für
  Content-Dateien dienen.
- **FR-011:** B16 MUST Querverweise zwischen Klassen, Fähigkeiten, Mobs, Zonen, Items, Loot,
  Formeln und Drop-/Währungsdefinitionen vor dem Veröffentlichen eines Snapshots prüfen.
- **FR-012:** Ein ungültiger Wert MUSS eine `ConfigValidationException`-kompatible Fehlermeldung mit
  Datei, Dokumentpfad, Erwartung und Istwert liefern; ein Querverbindungsfehler MUSS zusätzlich
  Ziel- und Quellschlüssel benennen.
- **FR-013:** Der Content-Reload MUST alle zu B16 gehörenden Quellen zuerst vollständig validieren
  und erst danach einen gemeinsamen gültigen Snapshot veröffentlichen.
- **FR-014:** Schlägt Parsing, Schema-, Migrations- oder Querverbindungsprüfung fehl, MUSS der
  vollständige vorherige Snapshot aktiv bleiben; ein halb neuer Content-Zustand ist unzulässig.
- **FR-015:** Ein erfolgreicher Content-Reload MUSS die vorhandenen Modul-Hooks über die bestehende
  `ConfigHandle`-/`reloadAll()`-Verdrahtung erreichen, ohne eine neue wiederkehrende Aufgabe je
  Spieler oder Entity einzuführen.
- **FR-016:** Jede Mutation der Laufzeit-Konfiguration MUSS über den bestehenden Admin-Rechtepfad
  `rpg.admin.reload` und `/rpg reload` erfolgen. B16 MUST keinen In-Game-Editor und keinen Befehl
  einführen, der YAML in das Plugin zurückschreibt.
- **FR-017:** Die geprüften Default-Dateien MUST beim ersten Start aus `rpg-content` in den
  Plugin-Datenordner kopiert werden, ohne eine vorhandene Betreiberdatei zu überschreiben.
- **FR-018:** Migrationen MUST explizite, getestete Schritte sein. Eine Legacy-Datei ohne Version
  darf nur über den dokumentierten Migrationsweg in eine neue Version überführt werden; ein normaler
  Start darf keine Datei stillschweigend umschreiben.
- **FR-019:** Die erste Migration MUST die heutigen Werte, Einheiten, IDs und bestehenden fachlichen
  Entscheidungen erhalten. Bewusstes Rebalancing ist nicht Teil dieser Migration.
- **FR-020:** Frühere offene Fachfragen, insbesondere globale Cooldowns, Casting-Zeiten, Raritäten,
  Affixe und neue Mob-Balance, MUST nicht durch eine technische B16-Dateistruktur implizit entschieden
  werden. Nicht entschiedene Felder bleiben außerhalb des ersten B16-Schemas oder werden als aktueller
  Bestand ausdrücklich dokumentiert.
- **FR-021:** B16 MUST ein repo-lokales Analysewerkzeug unter `tools/b16-balance/` vorsehen. Es liest
  versionierte YAML-Dateien und Szenario-Fixtures und erzeugt deterministische CSV-, JSON- und
  Markdown-Ausgaben.
- **FR-022:** Das Analysewerkzeug MUST ohne Netzwerk, Minecraft-Server, Spreadsheet-Programm oder
  Plugin-Classloader laufen können.
- **FR-023:** YAML bleibt die einzige Quelle der Wahrheit. Ein CSV-/Spreadsheet-Rückimport gehört
  nicht zur ersten B16-Ausbaustufe.
- **FR-024:** Analyseberichte MUST Eingabedatei-/Schema-Versionen, Szenario-ID, verwendete IDs und
  die verwendeten Baseline-Werte ausweisen, damit Ergebnisse nicht aus verschiedenen Ständen
  unbemerkt vermischt werden.
- **FR-025:** Jede B16-Scheibe MUST serverfreie Tests für gültige Daten, fehlende Pflichtdaten,
  Typ-/Bereichsfehler, unbekannte feste Schlüssel, Querverbindungen und atomaren Rollback besitzen.
- **FR-026:** Die Migration MUST durch Verhaltenserhalt- oder Golden-Fixtures beweisen, dass die
  Überführung ohne bewusstes Rebalancing erfolgt.
- **FR-027:** Ein gezielter Source-Scan MUST verbleibende spielbestimmende Zahlen im Java-Code finden;
  zugelassene technische Schutzgrenzen, Enum-Werte und algorithmische Konstanten müssen dabei
  ausdrücklich dokumentiert werden.

## Baseline versus fachliche Entscheidung

Die folgende Matrix ist für B16 verbindlich. **Baseline** bezeichnet den heutigen, zu erhaltenden
Ist-Bestand; **fachlich offen** bezeichnet die spätere Balancing- oder Ownership-Entscheidung. Ein
Baseline-Wert wird dadurch nicht zu einer neuen Balanceentscheidung und darf in der ersten
Migration nicht verändert werden.

| Thema | Baseline im Ist-Stand | Status für B16 | Migrations-/Schema-Regel |
|---|---|---|---|
| Globaler Cooldown | `abilities.yml:runtime.global-cooldown-ms=750` | Baseline bestätigt; zukünftige Abstimmung offen | Wert unverändert übernehmen; kein neues Cooldown-Ziel ableiten |
| Casting-Zeiten und Ability-Zeiten | vorhandene Ability-Felder und aktuelle YAML-Werte | Baseline dokumentieren; Semantik-/Balancing-Entscheidung offen | vorhandene Felder nur mit belegter Einheit migrieren; keine neuen Felder erfinden |
| Mob-Balance, Budgets und Wellenlogik | vorhandene `mobs.yml`-Budgets, Dichte-, Horde- und Cleanup-Werte | Baseline bestätigt; echtes Balancing nach Tests und vollständigen Mobdaten offen | aktuelle Werte/IDs unverändert übernehmen |
| Raritäten und Affixe | im ersten B16-Inventar kein entschiedener Bestandsvertrag | fachlich offen / nicht im ersten Schema | keine Platzhalter-Registry und kein impliziter Vertrag |
| `mobs.yml:admin-spawn-limit` | aktueller Wert `20`, serverweit gleichzeitig registrierte `Origin.ADMIN`-Mobs | als `PROTECTION_BOUNDARY` entschieden; `mobs.yml` ist Owner, Java `20` Fallback | Wert unverändert erhalten; kein neues Balancing-Ziel |
| `stats.yml:attributes.*.min/max` | aktuelle feldweise Minima/Maxima | Owner-/Schutzgrenzen-Entscheidung offen | Werte zunächst erhalten; Schema muss die offene Entscheidung explizit tragen |

Diese Matrix löst keine der alten Balancing-Fragen vorzeitig auf. Sie verhindert lediglich, dass
vorhandene YAML-Semantik, technische Defaults und spätere Zielwerte im Migrations- oder
Schemaentwurf vermischt werden.

### Key Entities

- **ContentDocument:** Eine gebündelte YAML-Quelle mit Dateischlüssel, Domäne, `schemaVersion`
  und validiertem Inhalt.
- **ContentRegistry:** Eine typisierte Sammlung von Klassen, Fähigkeiten, Mob-Arten, Zonen,
  Item-Vorlagen, Loot-/Drop-Regeln und Formeln innerhalb eines Dokuments.
- **ContentReference:** Eine geprüfte Verbindung zwischen stabilen IDs verschiedener Registries.
- **ContentSnapshot:** Der unveränderliche, gemeinsam veröffentlichte Zustand, den die Module nach
  einem erfolgreichen Reload beobachten.
- **MigrationStep:** Eine explizite Transformation von einer bekannten älteren Dateiversion in die
  nächste Version; sie schreibt keine Quelldatei beim normalen Serverstart.
- **BalanceBaseline:** Ein reproduzierbarer Bestand der aktuell migrierten Werte und der zugehörigen
  Fixtures; er ist Vergleichsgrundlage, noch kein neues Balancing-Ziel.

## Scope Boundaries

### In Scope

- Gemeinsame B16-Datei- und Schema-Konvention.
- Migration der bestehenden, spielrelevanten Werte in YAML ohne bewusstes Rebalancing.
- `rpg-content` als Besitzer der ausgelieferten Content-Definitionen und Defaults.
- Validierung von Inhalt, Version, Typen, Bereichen und Querverweisen.
- Atomarer Reload über den bestehenden Admin-/ConfigLoader-Pfad.
- Repo-lokale, deterministische Balancing-Auswertung.

### Out of Scope

- Neue Spielmechaniken oder neue Content-Entscheidungen.
- Bewusstes Rebalancing vor den vorgesehenen Tests und der vollständigen Bestandsaufnahme.
- In-Game- oder Web-Editor.
- Spreadsheet als Runtime-Quelle oder automatischer Rückimport.
- Deployment-/VPS-Abnahme aus B17.
- Semantikentscheidungen, die in B07–B11 noch fachlich offen sind.

## Success Criteria

- **SC-001:** 100 % der B16-verwalteten Default-Dateien liegen im `rpg-content`-Artefakt, besitzen
  `schemaVersion` und werden beim ersten Start ohne Überschreiben in den Plugin-Datenordner kopiert.
- **SC-002:** Eine absichtlich fehlende Pflichtangabe, ein falscher Typ, ein Bereichsfehler, ein
  unbekannter fester Schlüssel und eine unbekannte Version werden jeweils mit Datei und Pfad
  abgelehnt.
- **SC-003:** Ein ungültiger Reload lässt in 100 % der Tests alle zuvor gültigen Content-Handles und
  abgeleiteten Modulzustände unverändert.
- **SC-004:** Ein gültiger Reload macht eine absichtlich geänderte Zahl in allen betroffenen Modulen
  sichtbar, ohne einen Serverneustart oder eine Spieler-/Entity-Schleife zu benötigen.
- **SC-005:** Die Verhaltenserhalt-Fixtures zeigen für alle migrierten Beispielwerte keine Änderung
  gegenüber dem aktuellen Bestand.
- **SC-006:** Ein identischer Analyse-Lauf erzeugt byte- beziehungsweise zeilenidentische Ergebnisse
  und benötigt weder Server noch Netzwerk noch Spreadsheet-Anwendung.
- **SC-007:** Kein CSV-/Spreadsheet-Ergebnis wird ohne expliziten, zukünftigen Import-Schritt als
  Plugin-Konfiguration akzeptiert.
- **SC-008:** Ein Querverbindungsfehler nennt Quelle und Ziel und veröffentlicht keinen Teil-Snapshot.
- **SC-009:** Der Source-Scan liefert für bewusst ausgewählte B16-Werte keine unerklärten Java-
  Literalduplikate; jede erlaubte Schutzgrenze ist dokumentiert.

## Assumptions

- Die bestehenden `ConfigLoader`-, `ConfigSchema`-, `ConfigHandle`- und `YamlConfigLoader`-Verträge
  bleiben die technische Basis; B16 erweitert sie nur dort, wo die Datei-Version und der gemeinsame
  Content-Snapshot sonst nicht beweisbar wären.
- Die aktuelle Plugin-Datenordner-Auflösung bleibt zunächst erhalten: relative Quellen werden gegen
  `YamlConfigLoader(Path baseDirectory)` aufgelöst.
- `rpg-content` wird in den deploybaren Plugin-JAR-Inhalt aufgenommen, wie es die bestehende
  `rpg-plugin`-Jar-Aufgabe bereits für Projektmodule vorsieht.
- Die späteren Balancing-Ziele entstehen aus Tests, Live-Beobachtung und vollständigen Mob-/Content-
  Daten; sie werden in einer nachfolgenden Clarify-/Balancing-Runde festgelegt.
