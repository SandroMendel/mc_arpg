<!--
Sync Impact Report
- Version change: [TEMPLATE] → 1.0.0 (initial ratification)
- Modified principles: none (first real content, template placeholders had no prior values)
- Principle count adapted: template's 5 generic slots → 8 project-specific principles
  (I. Nebenläufigkeit, II. Performance, III. Architektur, IV. Datenhaltung,
  V. Datengetriebenes Design, VI. Korrektheit & Sicherheit, VII. Tests, VIII. Sprache),
  matching the project's own constitution.md — no [SECTION_2]/[SECTION_3] generic
  slots retained since all governance content is covered by these 8 principles.
- Added sections: all 8 Core Principles, Governance (Ausnahmen/Exceptions)
- Removed sections: generic [SECTION_2_NAME] / [SECTION_3_NAME] placeholders (not
  needed — project has no additional constraint category beyond the 8 principles)
- Templates requiring alignment:
  ✅ plan-template.md — Constitution Check gate reads this file at runtime, no edit needed here
  ✅ spec-template.md — no direct constitution references requiring sync
  ✅ tasks-template.md — no direct constitution references requiring sync
  ⚠ Follow-up: when /plan runs for B04+, verify the Constitution Check section
    concretely tests against Nebenläufigkeit/Performance/Architektur below.
- Deferred TODOs: none — RATIFICATION_DATE set to the date of this formal adoption
  into Spec-Kit, since no earlier ratification date exists for this document.

Sync Impact Report — 2026-08-23
- Version change: 1.0.0 → 1.1.0 (MINOR — bestehende Vorgabe wesentlich geändert)
- Modified principles: VII. Tests — Punkt 3 neu gefasst. Lasttests sind keine
  Bedingung mehr dafür, dass ein Block fertig ist; sie laufen gebündelt in einer
  eigenen Phase am Ende und gehören B15. Neu ergänzt: ein blockeigenes
  Leistungsziel braucht einen Beleg ohne Volllast.
- Added sections: none
- Removed sections: die namentliche Lasttestpflicht für B05 und B10
- Entscheidung des Auftraggebers vom 2026-08-23, festgehalten als ADR-031.
  Löst T122 aus B08b ohne die dort vorgesehene Aufnahme von B08b in die Liste —
  die Liste selbst entfällt.
- Templates requiring alignment:
  ✅ plan-template.md — Constitution Check liest diese Datei zur Laufzeit
  ✅ spec-template.md / tasks-template.md — keine Verweise auf die Lasttestregel
- Nachgezogen: blocks/B15-performance-observability.md (die Zeile „Lasttests sind
  Teil der Definition of Done für B05 und B10" entfällt), blocks/B08b (SC-006
  wartet nicht länger auf B10), specs/009-zones-regions/spec.md (SC-001 ist eine
  Messung, kein Lasttest).

Sync Impact Report — 2026-08-28
- Version change: 1.1.0 → 1.1.1 (PATCH — Klarstellung, keine inhaltliche Änderung)
- Modified principles: IV. Datenhaltung — Satz 4 und die Rationale nachgezogen.
  „Template-ID **und gewürfelte Roll-Werte**" wird zu „**die Template-ID**".
  ADR-027 hat den Roll-Mechanismus am 2026-08-22 abgeschafft; die Constitution
  hat das sechs Tage lang nicht nachvollzogen. Der Wortlaut hätte den
  Constitution Check von `/plan` für B11 gegen eine Regel prüfen lassen, die
  dieser Block gerade umsetzt — und zwar strenger, als sie dasteht.
- Warum PATCH und nicht MINOR: die geschützte Zusage (kein gerendertes Lore,
  keine berechneten Endwerte) ist unverändert. Es entfällt nur eine Erlaubnis,
  die niemand mehr nutzt. Wer der alten Fassung folgte, verstößt nicht gegen die
  neue.
- Added sections: none
- Removed sections: none
- Aufgefallen bei: `/speckit-specify` B11 (2026-08-28), festgehalten als ADR-039
- Templates requiring alignment:
  ✅ plan-template.md — Constitution Check liest diese Datei zur Laufzeit
  ✅ spec-template.md / tasks-template.md — keine Verweise auf Prinzip IV
- Mit nachgezogen: `constitution.md` und
  `minecraft-rpg-spec/minecraft-rpg-spec/constitution.md` trugen dieselbe Stelle
  in ihrer Kurzform („Template-ID und Roll-Werte"). Beide sind Quellfassungen,
  nicht die vom Werkzeug gelesene Datei, und beide sind jetzt gleichlautend —
  eine stehengelassene Quellfassung wäre die nächste Divergenz.
-->

# Minecraft RPG Plugin Constitution

## Core Principles

### I. Nebenläufigkeit

Paper-/Bukkit-API wird **ausschließlich im Server-Tick** aufgerufen. Datenbank-,
Datei- und Netzwerkzugriffe erfolgen **ausschließlich asynchron**. Kein
blockierender Aufruf im Tick-Pfad ist zulässig — kein `join()`, kein `get()`
auf einem Future, kein synchroner I/O. Ergebnisse asynchroner Arbeit werden
über einen expliziten Übergabepunkt in den Tick zurückgeführt, niemals durch
geteilten veränderlichen Zustand. Der globale Bukkit-Scheduler wird **niemals**
direkt benutzt; Scheduling erfolgt ausschließlich über die projekteigene
Abstraktion mit location- oder entity-gebundenen Aufrufen. Es gibt keinen
globalen veränderlichen Zustand im Gameplay-Pfad — Zustand hängt immer an dem
Objekt, zu dem er gehört (Spieler, Zone, Entity).

**Rationale**: Der Server-Tick ist laut ADR-002 die knappste Ressource des
gesamten Projekts (100–200 Spieler auf einer einzigen Instanz). Die
location-/entity-gebundene Scheduling-Regel hält zusätzlich laut ADR-007 den
Migrationspfad zu Folia offen, ohne dass dafür ein Umbau nötig wird.

### II. Performance

Jedes Subsystem hält im Normalbetrieb ein Tick-Budget von **≤ 5 ms** ein.
Wiederkehrende Tasks pro Spieler oder pro Entity sind unzulässig — zeitbasierte
Werte (Cooldowns, Mana-Regeneration, Buff-Laufzeiten) werden
**zeitstempelbasiert lazy** ausgewertet. Berechnungen laufen
**ereignisgesteuert bei Änderung**, nicht periodisch. Räumliche Abfragen laufen
über einen räumlichen Index, niemals über lineare Iteration aller Kandidaten.
Kein Datenbankzugriff pro Spielereignis — Schreibvorgänge werden gepuffert und
gebatcht. Allokationen im Hot Path werden vermieden; keine Streams oder Boxing
in Pro-Tick-Schleifen.

**Rationale**: Erfolgskriterium des Projekts ist stabile 20 TPS bei 150
gleichzeitigen Spielern unter Kampflast (siehe Vision & Scope, M4-Nachweis:
800 aktive Mobs bei 150 Spielern, p95 MSPT < 40 ms). Periodische Pro-Entity-
Tasks und lineare räumliche Suche skalieren dafür nicht.

### III. Architektur

Domänenlogik (`rpg-core`) enthält **keine** Bukkit-Abhängigkeit und ist ohne
laufenden Server testbar. Die Abhängigkeitsrichtung ist strikt
`plugin → platform → core` und niemals umgekehrt. Jeder Architekturblock
(B01–B17) hat eine explizite öffentliche Schnittstelle; Zugriff auf Interna
anderer Blöcke ist unzulässig. Rendering und Eingabe liegen hinter
Schnittstellen (`HudRenderer`, `ItemRenderer` u. ä.), damit ein späterer
Resource-Pack-Client ohne Umbau ergänzt werden kann. `Zone` ist niemals
gleich `World` — Zonen werden als `(worldId, Geometrie)` modelliert.

**Rationale**: Nur eine bukkit-freie Domänenschicht erlaubt die in Prinzip VII
geforderten serverlosen Unit-Tests für Formeln und Regeln. Die
Schnittstellen-vor-Direktzugriff-Regel hält laut ADR-005 den Wechsel auf ein
Resource Pack und laut ADR-006 die Zuordnung von Zonen zu Welten als reine
Konfigurationsänderung offen, nicht als Umbau.

### IV. Datenhaltung

Schemaänderungen erfolgen ausschließlich über versionierte Migrationen.
Solange ein Spieler online ist, ist der Speicher-Cache autoritativ, nicht die
Datenbank. Persistierte Spielerdaten sind versioniert und besitzen einen
Migrationspfad. Items speichern **die Template-ID**, niemals berechnete Endwerte
und niemals gerendertes Lore. Kein Datenverlust über das Autosave-Intervall
hinaus, auch bei Absturz.

**Rationale**: Nur die Template-ID statt gerenderter Endwerte ermöglicht laut
ADR-004 späteres Balancing-Rework, ohne bestehende Spieleritems anzufassen. Die
Cache-Autorität während der Session ist die Grundlage der
Write-Behind-Persistenzstrategie (siehe 01-architecture.md).

Ursprünglich stand hier „Template-ID **und gewürfelte Roll-Werte**". ADR-027 hat
den Roll-Mechanismus abgeschafft — jedes Item trägt feste Attributwerte —, und
damit ist die erste Hälfte gegenstandslos geworden. **Die Zusage wird dadurch
stärker, nicht schwächer**: ohne Roll ist die Vorlage die einzige Quelle, und
eine Balancing-Änderung wirkt auf jedes vorhandene Exemplar statt nur auf neue.

### V. Datengetriebenes Design

Klassen, Fähigkeiten, Mobs, Zonen, Items und alle Balancing-Zahlen liegen in
versionierten Konfigurationsdateien, nicht im Code. Konfigurationen werden
beim Start gegen ein Schema validiert; Fehler führen zu **Fail-Fast** mit
klarer Meldung. Neue Inhalte derselben Art dürfen keine Codeänderung
erfordern. Es gibt keine hartcodierten Spielertexte — alle Texte laufen über
Message-Schlüssel.

**Rationale**: Erfolgskriterium des Projekts ist, dass neue Zonen, Mobs, Items
und Fähigkeiten ohne Codeänderung ergänzbar sind (siehe Vision & Scope). Die
Message-Schlüssel-Pflicht bereitet zudem laut ADR-005 spätere
Mehrsprachigkeit strukturell vor, ohne sie jetzt umzusetzen.

### VI. Korrektheit & Sicherheit

Der Server ist alleinige Autorität für alle Werte; Client-Eingaben werden
validiert und rate-limitiert. Eine Ausnahme im Gameplay-Pfad darf keinen
Spieler in einen inkonsistenten Zustand versetzen — Fehler werden lokal
begrenzt und protokolliert. Reflection- und NMS-Zugriff sind nur mit
dokumentierter Begründung zulässig und an einer einzigen Stelle gekapselt.

**Rationale**: Bei 100–200 gleichzeitigen Spielern auf einer einzigen Instanz
(ADR-002) darf ein einzelner Fehler nicht den ganzen Server oder andere
Spieler mitreißen. Gekapselter Reflection-/NMS-Zugriff begrenzt den Aufwand
bei Minecraft-Versionswechseln auf eine Stelle.

### VII. Tests

Jede Formel und jede Regel der Domänenschicht hat Unit-Tests ohne laufenden
Server. Persistenz wird gegen eine echte PostgreSQL-Instanz getestet
(Testcontainers), nicht gegen Mocks.

**Lasttests sind keine Bedingung dafür, dass ein Block fertig ist.** Sie laufen
gebündelt in einer eigenen Phase, wenn die inhaltlichen Blöcke stehen, und
gehören **B15**. Kein Block wird wegen eines fehlenden Lasttests offen gehalten.
Ein Leistungsziel, das ein Block für sich benennt, braucht dennoch einen Beleg —
aber nur einen, der **ohne Volllast** zu erbringen ist: eine wiederholbare
Messung der eigenen Rechenarbeit. Was sich erst unter 150 Spielern und 800 Mobs
zeigt, wird in der Lasttestphase geprüft, nicht vorher behauptet.

**Rationale**: Domänenlogik ohne Bukkit-Abhängigkeit (Prinzip III) ist nur
dann tatsächlich verlässlich, wenn sie auch tatsächlich serverlos getestet
wird. Mocks gegen die Datenbank hätten in der Vergangenheit divergierendes
Verhalten zwischen Test und Produktion verdeckt — echte Testcontainer-Instanzen
sind daher Pflicht.

Zur Lasttestphase: die frühere Fassung machte B05 und B10 namentlich
lasttestpflichtig, *bevor* sie als fertig gelten durften. In der Praxis war das
nicht einlösbar — ein Lasttest braucht Spieler, Mobs und Inhalt, also gerade
das, was die späteren Blöcke erst liefern. Die Regel hätte einen Block auf einen
Nachweis warten lassen, den ein anderer Block erst möglich macht. Gebündelt am
Ende ist der Nachweis aussagekräftiger, weil er das Zusammenspiel misst und
nicht ein Subsystem in einer künstlich leeren Welt. Der Preis ist benannt und
angenommen: ein Leistungsfehler zeigt sich später, und die Gegenmaßnahme dagegen
sind die Vorgaben aus Prinzip II, die für jeden Block *vor* dem Lasttest gelten.
Ausführlich in ADR-031.

### VIII. Sprache

Dokumentation und Diskussion erfolgen auf **Deutsch**. Code, Bezeichner,
Config-Keys, Commit-Messages und Spielertexte erfolgen auf **Englisch**
(internationaler Server, siehe Vision & Scope).

**Rationale**: Trennt die Sprache des Entwicklungsteams von der Sprache, die
Endspieler und der kompilierte Code tatsächlich sehen, und vermeidet
Vermischung in Commits, Konfigurationsdateien und Spieltexten.

## Governance

Diese Constitution gilt für **jede** Spec, jeden Plan und jede Implementierung
in diesem Projekt. Ein Vorschlag, der gegen sie verstößt, wird abgelehnt oder
verlangt eine ausdrückliche, begründete Ausnahme.

Jede Abweichung von diesen Regeln wird als ADR in `02-decisions.md`
festgehalten — mit Begründung, Alternative und Auswirkung. Eine unbegründete
Abweichung ist ein Fehler, kein Kompromiss. Verstößt ein `/plan` gegen diese
Constitution, wird der Plan geändert, nicht die Constitution.

**Versionierung**: Diese Constitution folgt semantischer Versionierung.
MAJOR bei rückwärtsinkompatibler Streichung oder Neudefinition eines
Prinzips, MINOR beim Hinzufügen eines Prinzips oder einer wesentlichen
Erweiterung bestehender Vorgaben, PATCH bei Klarstellungen und
Formulierungskorrekturen ohne inhaltliche Änderung.

**Compliance**: Jeder `/plan`-Durchlauf prüft explizit gegen diese acht
Prinzipien (Constitution Check). Jede in der Umsetzung getroffene
Architekturentscheidung wird als ADR in `02-decisions.md` nachgetragen.

**Version**: 1.1.1 | **Ratified**: 2026-08-19 | **Last Amended**: 2026-08-28
