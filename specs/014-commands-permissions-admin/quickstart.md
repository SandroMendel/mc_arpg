# Quickstart: B14 · Commands, Permissions & Admin-Tools

Wie geprüft wird, dass dieser Block hält, was er zusagt — von den Unittests bis zum echten Server.

---

## Abschnitt 0 · Voraussetzungen

- Paper 26.2, Java 25, PostgreSQL im Container `vuntex-postgres`
- Testserver in `C:\Users\Ticoo\Desktop\MinecraftServer`
> Die `[[…]]`-Verweise unten zeigen auf Notizen im Projektgedächtnis, nicht auf Dateien im Repo.
> Was sie sagen, steht jeweils daneben — der Verweis ist die Quelle, nicht die Erklärung.

- Deploy (siehe [[vuntexrpg-server-deploy]]): `./gradlew build`, Jar aus `rpg-plugin/build/libs/` nach
  `plugins/`, **und geänderte YAMLs nach `plugins/VuntexRPG/` vergleichen** — die richtige Frage ist
  „welche Datei unterscheidet sich", nicht „welche ist neu". `persistence.yml` nie überschreiben.
- Für diesen Block relevant: **`plugin.yml` wächst um Rechte.** Sie steckt im Jar und braucht kein
  Kopieren — aber `messages.yml` wächst um die neuen Meldungsschlüssel und **muss** kopiert werden,
  sonst bricht der Start an `MessageKeyValidator` ab, bevor irgendeine B14-Prüfung läuft. Das ist
  derselbe Fehler, der bei B12 einen Abend gekostet hat.

## Abschnitt 1 · Ohne Server (Unittests)

```bash
./gradlew test
```

Erwartet: alle grün, **0 übersprungen**. Übersprungene Tests sind bei MockBukkit kein Erfolg,
sondern ein Nicht-Ergebnis ([[vuntexrpg-mockbukkit-skips]]).

Der Block gilt hier als abgedeckt, wenn geprüft wird:

- Argumentprüfung je Typ, einschließlich der Grenzen (`LEVEL` 1..60, `AMOUNT` mit Ober-/Untergrenze)
- Vorschlag und Prüfung liefern für denselben Typ dieselbe Menge — **die Zusage aus FR-002 als
  Test**, nicht als Absicht
- Die Rechteprüfung lehnt ab, ohne die Ausführung zu berühren
- Die Sperrzeit rechnet aus Zeitstempeln und lässt nach Ablauf wieder durch
- Ein Blatt ohne Spielerbezug läuft von der Konsole; eines mit Spielerbezug bricht mit Meldung ab
- **Der Wächter aus FR-034**: ein Kommando am Gerüst vorbei registriert → roter Test; ein benutztes
  Recht ohne `plugin.yml`-Eintrag → roter Test
- **Kein `args.length` und kein `switch (args[0])` im Produktivcode** — als Test, nicht als Absicht
  (FR-001, SC-002). Sonst fängt das nächste Kommando wieder von vorn an
- **Das Audit-Log bleibt anfügend** (FR-031a): ein Architekturtest schlägt fehl, sobald aus B14
  heraus ein anderer Schreibweg als `append` entsteht
- **Kommandos rufen nur öffentliche Blockschnittstellen** (FR-007), und `rpg-core` bekommt von B14
  nichts (Prinzip III) — beides als Naht-Test, wie B13 ihn in `UiSeamGuardTest` hat

### Gegen eine echte Datenbank (Testcontainers)

**Der Lesepfad des Audit-Logs** (FR-036). `AuditLogRepository.between` ist nie gegen eine Datenbank
gelaufen — nur die Schreibseite ist über `AuditLogTest` gedeckt. Geprüft wird: mehrere Einträge über
einen Zeitraum, **neueste zuerst**, Grenzen des Zeitfensters beidseitig einschließend, und ein
Zeitraum ohne Einträge liefert eine leere Liste statt eines Fehlers.

## Abschnitt 2 · Startprüfungen

`FullBootstrapTest` muss jedes Kommando und jedes Recht auf einem echten Serverstart abdecken
(FR-035). **Grüne Modultests beweisen nichts über die Verdrahtung** —
[[vuntexrpg-block-done-criteria]].

## Abschnitt 3 · Auf dem echten Server

> **Schritt 0 kommt vor allem anderen und vor jeder Zeile Umbau.**

### Der Beweis, den kein Test führen kann

| # | Was | Erwartet |
|---|---|---|
| **0** | Ein Wegwerf-Kommando **nur** über Brigadier registrieren, ohne `plugin.yml`-Eintrag | Es existiert im Spiel und vervollständigt |
| **0a** | Dasselbe Kommando zusätzlich in `plugin.yml` eintragen | Festhalten, welche Fassung gewinnt — **hiervon hängt ab, ob der `commands:`-Block bleibt oder entfällt** |
| **0b** | Server neu starten, Startlog ansehen | Keine Warnung, kein `ClassNotFoundException` zu Brigadier — der Klassenlader-Punkt ist damit belegt statt vermutet |

### Das Gerüst und der Umzug (US1, US2)

| # | Was | Erwartet |
|---|---|---|
| 1 | `/stats ` + Tab | Nur die gültigen Zeiträume |
| 2 | `/top ` + Tab | Nur die gültigen Ranglisten |
| 3 | `/xp give` absenden, ohne weiteres | Meldung nennt das fehlende Argument und seinen Bereich |
| 4 | `/xp give <Spieler> abc` | Meldung nennt das Argument, nicht die Nutzungszeile |
| 5 | `/coins set <Spieler> <Klasse> <Betrag>` wie bisher | Ergebnis wie vor dem Umzug (FR-005, FR-006) |
| 6 | `/char`, `/trash`, `/stats`, `/top` wie bisher | Alles unverändert — kein Abnahmeschritt aus B08b, B11, B12, B13 muss angepasst werden (SC-008) |
| 7 | `/char` von der **Serverkonsole** | Bricht ab mit „braucht einen Spieler", keine Ausnahme im Log (FR-008) |
| 8 | Als Spieler **ohne** `rpg.admin.*`: `/rpg ` + Tab | Kein Admin-Unterkommando erscheint (FR-014) |
| 9 | Derselbe Spieler: `/rpg reload` volltippen | Abgelehnt, nichts passiert (FR-013) |
| 10 | Frischer Server, kein Rechte-Plugin, normaler Spieler | `/char`, `/stats`, `/top`, `/coins`, `/trash` gehen; kein Admin-Kommando geht (FR-011, FR-012) |

### Items geben (US3)

| # | Was | Erwartet |
|---|---|---|
| 11 | `/rpg item give <Spieler> ` + Tab | Die Vorlagen aus `items.yml` |
| 12 | Einen Trank vergeben | Er sieht aus wie ein erbeuteter — gleiche Lore, gleicher Zustand |
| 13 | Vergeben bei vollem Inventar | Dieselbe Regel wie Beute (B11 US7), nichts geht verloren |
| 14 | `/rpg item give <Spieler> gibtsnicht` | Bricht ab, nennt den unbekannten Schlüssel |
| 15 | Danach `/rpg audit` | Der Eintrag steht da: wer, was, wann, an wem |

### Neuladen (US4) — der tote Pfad, erstmals benutzt

| # | Was | Erwartet |
|---|---|---|
| 16 | Eine Zahl in `mobs.yml` ändern, `/rpg reload` | Wirkt sofort, ohne Neustart |
| 17 | **Nachweisen, dass sie wirkt** — nicht nur, dass das Kommando „ok" sagt | Am Spielverhalten oder in der Datenbank belegt |
| 17a | **Ins Serverlog sehen** | Eine Zeile nennt **alle sechs** Module, deren Nachladehaken gelaufen ist — `CombatModule`, `ItemModule`, `MobModule`, `StatisticsModule`, `UiModule`, `ZoneModule`. Fehlt eines, ist FR-023 **nicht** erfüllt (FR-023a) |
| 18 | `zones.yml` auf eine Welt zeigen lassen, die es nicht gibt, `/rpg reload` | Abgelehnt; Meldung nennt Datei, Pfad und Grund; **Server läuft weiter** (FR-022) |
| 19 | Direkt danach eine *gültige* Änderung prüfen, die vor Schritt 18 aktiv war | Unverändert aktiv — die Ablehnung hat **kein** Modul angefasst (FR-021) |
| 19a | Nach Schritt 18 erneut ins Log sehen | **Keine** Haken-Zeile — ein abgelehntes Neuladen führt keinen Haken aus |
| 20 | `items.yml` auf eine unbekannte Zone zeigen lassen, `/rpg reload` | Abgelehnt — `ItemModule`s Nachladehaken prüft Zonen, Arten und Namen; **das ist der Beleg, dass der Haken wirklich läuft** (FR-020, FR-023) |

> Schritt 19 und 20 sind die eigentlichen Prüfungen dieses Blocks. Alles davor testet ein Kommando;
> diese beiden testen eine Zusage, die seit B01 unbelegt im Raum steht.

### Kreaturen setzen (US5)

| # | Was | Erwartet |
|---|---|---|
| 21 | `/rpg mob spawn ` + Tab, eine Art setzen | Erscheint mit den Werten des Spawnplaners |
| 22 | Chunk entladen und wieder laden | Sie ist **noch da** — ADR-050 entfernt sie nicht |
| 23 | In einer Zone mit vollem Budget setzen | Gelingt trotzdem — eigene Obergrenze, nicht das Budget (FR-019a) |
| 23a | **Die Budgetzahl selbst auslesen**, vor und nach mehreren Admin-Spawns | Unverändert. Das Budget ist eine Aussage über die *gespawnte* Population, und die wächst durch Handgesetztes nicht (SC-010) |
| 24 | Die eigene Obergrenze ausreizen | Bricht ab und nennt die Grenze (FR-019a) |
| 25 | Server neu starten, nachsehen | Keine handgesetzte Kreatur mehr, beide Zählungen auf null (FR-019b) |

### Setzen und Einsicht (US6, US7)

| # | Was | Erwartet |
|---|---|---|
| 26 | `/rpg set level <Spieler> 35` | Abgeleitete Werte gelten sofort; Log führt alten und neuen Stand (FR-024, FR-028) |
| 27 | `/rpg set class <Spieler> <Klasse>` | Wechsel wie bei einer Klassenwahl durch den Spieler, kein Sonderzustand (FR-024) |
| 28 | `/rpg set level <Spieler> 999` | Abgelehnt, nichts verändert (FR-025) |
| 28a | Nachsehen, ob es ein Kommando zum Setzen einzelner **Attribute** gibt | Es gibt keines, und das ist Absicht (FR-024a) |
| 29 | Fremdes Charakterblatt eines **offline** Spielers ansehen | Geht; Name wird über den Cache aufgelöst (FR-026) |
| 30 | Einen Namen mit Tippfehler eingeben | Bricht ab und nennt den Namen — kein leeres Profil |
| 31 | Nach jeder Einsicht: `/rpg audit` | **Kein** Eintrag für das reine Ansehen (FR-029) |
| 31a | Werte des Betroffenen **vor und nach** jeder Einsicht vergleichen — Stufe, Erfahrung, Coins, Inventar | Bitgleich unverändert. Dass kein Audit-Eintrag entsteht, beweist nur, dass nichts *protokolliert* wurde, nicht dass nichts *passiert* ist (FR-027) |

### Audit und Sperrzeiten (US8)

| # | Was | Erwartet |
|---|---|---|
| 32 | Je einen Eingriff aus jeder Gruppe, dann `/rpg audit` über den Zeitraum | Alle erscheinen, neueste zuerst (FR-028, FR-030, SC-006) |
| 33 | `/stats` mehrfach in schneller Folge | Sperrzeit greift, Ablehnung sagt wann es weitergeht (FR-032) |
| 34 | Dasselbe von der Konsole | Keine Sperrzeit (FR-032) |
| 35 | Während der Abfrage auf die TPS sehen | Kein Einbruch — nichts blockiert den Tick (Prinzip I) |

---

## Was **nicht** geprüft wird, und warum

- **Lasttests.** Gehören B15 (Constitution VII, ADR-031). Kein Block wartet darauf.
- **Ein zweiter Spieler.** B14 braucht keinen — **alle 40 Schritte sind allein machbar**. Das ist
  ein Unterschied zu B11 (9 Party-Schritte) und B13 (1 Schritt zu zweit) und macht die Abnahme
  planbar.
