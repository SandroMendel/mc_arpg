# Quickstart · B09 · Zonen & Regionen

Vier Abschnitte. Die ersten zwei laufen **ohne Server**, der dritte braucht einen echten
Paper-Server, der vierte gehört nicht mehr diesem Block.

---

## 1 · Ohne Server: Tests und Messung

```bash
./gradlew test
```

**Erwartet: 0 Fehler, 0 übersprungen.** Auf übersprungene Tests achten — MockBukkit meldet
Nicht-Implementiertes als *skipped*, nicht als Fehler, und ein „skipped" ist hier ein Befund.

Die gesamte Zonenlogik ist serverfrei prüfbar (SC-014): Quader, Index, Levelband, die
Schadenserlaubnis und die Reiseabfolge liegen in `rpg-core` ohne Bukkit.

### Die Messung zu SC-001

```bash
./gradlew test --tests '*ZoneLookupBenchmarkTest*'
```

Ermittelt die Zonenzuordnung für **200 Positionen** und hält die Zeit fest. Erwartet: **unter
0,5 ms** für alle 200.

Das ist eine **Messung, kein Lasttest** (ADR-031). Sie braucht keinen Server, weil der Index reine
Rechnung ist. Was sich erst unter 150 Spielern und 800 Mobs zeigt, gehört in B15s Lasttestphase und
hält diesen Block nicht offen.

### Die Zusicherung, die grün bleiben muss

```bash
./gradlew test --tests '*SinglePermissionPointTest*' --tests '*DamagePermissionTest*'
```

**Diese beiden dürfen nicht angepasst werden.** Sie sind der Nachweis für SC-008: B09 *ersetzt* die
Schadenserlaubnis, statt eine zweite Kopie anzulegen. Ein angepasster Test wäre keine Bestätigung,
sondern deren Umgehung.

---

## 2 · Ohne Server: die Konfiguration wirkt

Belegt SC-003, SC-004 und SC-013 — dass Zonen wirklich konfigurationsdefiniert sind.

| Schritt | Was geändert wird | Erwartet |
|---|---|---|
| 2.1 | eine **siebte** Region in `zones.yml` ergänzen | sie existiert nach dem Neuladen, ohne Codeänderung (SC-003) |
| 2.2 | `world:` einer Zone auf eine andere Welt stellen | die Zone liegt dort, ohne Codeänderung (SC-004) |
| 2.3 | zwei Zonen überlappen lassen | **Start verweigert**, Meldung benennt beide Zonen und den Quader (FR-012) |
| 2.4 | Schutzkern aus seiner Zone herausschieben | Start verweigert (FR-009) |
| 2.5 | Spawn-Bereich in den Schutzkern legen | Start verweigert (FR-055) |
| 2.6 | Kristall in eine Zone ohne Schutzkern legen | Start verweigert (FR-051c) |
| 2.7 | `zone.<key>.name` aus `messages.yml` entfernen | Start verweigert (FR-003c) |
| 2.8 | Startregion entfernen, dann zwei setzen | beide Male Start verweigert (FR-037a) |
| 2.9 | `provisional: true` setzen und starten | Warnung im Protokoll (FR-065b); bei `/rpg reload` **nicht** erneut (research.md R7) |
| 2.10 | `provisional` entfernen | nur die Warnung verschwindet, sonst ändert sich nichts (FR-065c) |
| 2.11 | einen Zonennamen in `messages.yml` ändern | der neue Name erscheint überall, auch im Wegpunktfenster; **keine** weitere Datei angefasst (SC-017) |

---

## 3 · Auf einem echten Paper-Server

Grüne Tests sagen nichts über Papers `libraries:`-Klassenlader, und `PlayerInteractEvent`,
`PlayerMoveEvent` und ein Inventarfenster kann MockBukkit nur teilweise nachstellen. Was serverfrei
geprüft ist: **dass und wem gegenüber** wir sie verlangen.

### Zonen und Ereignisse

| # | Schritt | Erwartet |
|---|---|---|
| 1 | Server mit den sechs vorläufigen Regionen starten | Start gelingt, **Warnung über Platzhalterkoordinaten** im Protokoll |
| 2 | in *The Greenfields* einloggen | Region sofort bekannt, ohne Bewegung (FR-017) |
| 3 | Grenze nach *The Dustlands* zu Fuss überschreiten | **genau ein** Zonenwechsel (SC-005) |
| 4 | den Schutzkern verlassen | **genau ein** Kernereignis, **kein** Zonenwechsel (SC-006) |
| 5 | innerhalb einer Region weit umherlaufen | kein weiteres Ereignis; keine spürbare Last |
| 6 | per Teleport in eine andere Region | Wechsel feuert genauso (FR-017) |
| 7 | ausloggen und wieder einloggen | Region sofort bekannt, kein doppeltes Ereignis (FR-018) |
| 8 | in einen Zwischenraum ohne Region laufen | „keine Zone", keine Warnung, kein Fehler (FR-007) |

### Levelband

| # | Schritt | Erwartet |
|---|---|---|
| 9 | mit Stufe 7 *The Dustlands* betreten | **genau eine** Warnung, kein Aufhalten (SC-007) |
| 10 | an derselben Grenze mehrfach hin und her | keine Wiederholung der Warnung (FR-024) |
| 11 | mit Stufe 60 *The Greenfields* betreten | keine Warnung (FR-023) |
| 12 | mit Stufe 10 *The Greenfields* betreten | keine Warnung — die Bandgrenze schliesst ein |

### Der Schutzkern

| # | Schritt | Erwartet |
|---|---|---|
| 13 | im Schutzkern von einem Mob angreifen lassen | **kein Schaden** (SC-012) |
| 14 | aus dem Kern heraus einen Mob draussen angreifen | **kein Schaden** — der Kern ist kein Schiessstand |
| 15 | im Kern aus der Höhe fallen | kein Schaden (FR-028a) |
| 16 | eine Region auf `pvp: true` stellen, im Kern zuschlagen | abgelehnt (SC-009) |
| 17 | dieselbe Region, ausserhalb des Kerns zuschlagen | zugelassen (SC-009) |
| 18 | alle Regionen auf `pvp: false`, irgendwo zuschlagen | abgelehnt, **wie vor diesem Block** (SC-008) |

### Tod und Kampf-Logout

| # | Schritt | Erwartet |
|---|---|---|
| 19 | in *The Terracotta Canyons* sterben | Erscheinen im Schutzkern **dieser** Region, mit Meldung (SC-010) |
| 20 | ausserhalb aller Regionen sterben | Erscheinen am Ausweichpunkt (FR-034) |
| 21 | nach dem Tod Erfahrung und Inventar prüfen | unverändert und vollständig (FR-036) |
| 22 | **während eines Kampfes ausloggen**, dann einloggen | Schutzkern der Region, Meldung nennt den Grund (SC-011) |
| 23 | acht Sekunden nach dem letzten Treffer ausloggen | nichts passiert; Erscheinen dort, wo man war (SC-011) |
| 24 | `combat-logout: none` setzen, im Kampf ausloggen | nichts passiert (FR-042) |
| 25 | Verbindung im Kampf hart trennen | wie ein absichtliches Verlassen (FR-043) |

### Wegpunkt-Kristalle

| # | Schritt | Erwartet |
|---|---|---|
| 26 | frischen Charakter erstellen | Erscheinen in der Startregion, **unabhängig vom Weltspawn** (SC-025) |
| 27 | den Kristall zum ersten Mal rechtsklicken | freigeschaltet, Meldung, **kein Fenster** (FR-047) |
| 28 | denselben Kristall erneut rechtsklicken | Fenster mit **allen sechs**, eines wählbar (FR-048) |
| 29 | einen gesperrten Eintrag ansehen | **Name und Levelband** sichtbar, erkennbar gesperrt (SC-026) |
| 30 | einen gesperrten Eintrag anklicken | Meldung, **keine** Buchung, keine Versetzung (FR-049) |
| 31 | zu Fuss in die zweite Region, dort freischalten, zurückreisen | Kontostand um den Preis gesunken, Ankunft im Schutzkern |
| 32 | Ankunft mit dem Todesort von Schritt 19 vergleichen | **dieselbe** Koordinate (SC-024) |
| 33 | mit zu wenig Coins reisen | nichts gebucht, nicht versetzt, Meldung nennt den fehlenden Betrag (SC-020) |
| 34 | **im Kampf** reisen wollen | abgelehnt, Meldung nennt den Kampf; im Kern trotzdem unverletzbar (SC-023) |
| 35 | acht Sekunden warten, dieselbe Reise | gelingt (SC-023) |
| 36 | rechte Maustaste gedrückt halten | das Fenster öffnet nicht je Tick (research.md R5) |
| 37 | Server neu starten, Fenster erneut öffnen | Freischaltungen sind noch da (SC-019) |
| 38 | zweiten Charakter desselben Accounts, Fenster öffnen | **keine** Freischaltungen geerbt (SC-019) |
| 39 | Charakter löschen, neuen im selben Slot, Fenster öffnen | **keine** Freischaltung (SC-027) |
| 40 | Kristall aus `zones.yml` entfernen, neu laden, Fenster öffnen | er fehlt; die Freischaltung schadet nicht (FR-051b) |
| 41 | ihn zurückschreiben, neu laden | die Freischaltung wirkt wieder (FR-051b) |
| 42 | Verlauf über `/coins` prüfen | die Reise ist als eigener Grund erkennbar, getrennt von Einkauf und Reparatur (SC-022) |

### Neuladen zur Laufzeit

| # | Schritt | Erwartet |
|---|---|---|
| 43 | mit mehreren Spielern in verschiedenen Regionen neu laden | alle werden neu bewertet; Ereignisse **nur** für tatsächliche Änderungen (FR-014, FR-018) |
| 44 | eine Region entfernen, während jemand darin steht | er ist danach in keiner Region; kein Fehler, keine Zwangsversetzung |
| 45 | ein fehlerhaftes `zones.yml` neu laden | abgelehnt, die **vorige** Fassung bleibt für alle Module aktiv |

### Für B10 vorbereitet

| # | Schritt | Erwartet |
|---|---|---|
| 46 | Spawn-Bereiche jeder Region abfragen | jede liefert mehrere, mit Kennung und Geometrie — **und nichts sonst** (FR-053a) |
| 47 | Zweitleben des Rogue in jeder Region prüfen | wirkt überall; `isOpenWorld` sagt überall ja — als Entscheidung (FR-052) |

---

## 4 · Last

**Gehört nicht diesem Block** (ADR-031). Der Nachweis der Zielwerte aus B15 — 150 Spieler, 800 Mobs,
p95 MSPT < 40 ms — läuft gebündelt in B15s Lasttestphase, wenn die inhaltlichen Blöcke stehen.
Mitzumessen ist dort namentlich die Zonenzuordnung unter Bewegungslast.

Dieser Block gilt als fertig, wenn Abschnitt 1 bis 3 durchlaufen sind.
