# B09 · Zonen & Regionen

| | |
|---|---|
| **Schicht** | 2 — Welt & Content |
| **Status** | **Spezifiziert** *(2026-08-23)* — `specs/009-zones-regions/`, alle offenen Fragen geschlossen, `/specify` und `/clarify` durchlaufen, bereit für `/plan`. Zwei ADRs dabei entstanden: **ADR-030** (Kampf-Logout ist der Tod) und **ADR-032** (Wegpunkt-Kristalle) |
| **Abhängig von** | B01 |
| **Benötigt von** | B10, B11, B13 |

## Zweck

Räumliche Gliederung der Spielwelt in Gebiete mit eigenen Regeln, Levelbereichen
und Inhalten.

## Umfang

- Zonendefinition mit Geometrie und Metadaten
- Räumlich indizierte Zonenerkennung
- Zonenregeln: Levelbereich, PvP, Respawn-Punkt — und nur diese drei.
  **Schwierigkeitsmodifikator und Loot-Zuordnung sind bei `/clarify` am 2026-08-23
  herausgenommen worden**: ein Feld, dessen Bedeutung dieser Block nicht kennt,
  kann er nicht prüfen, und B07s undurchsichtig weitergereichter `cost`-Block hat
  gezeigt, was das kostet (ADR-027). B10 ergänzt den Modifikator, B11 die
  Loot-Zuordnung — jeder mit einer Form, die er validieren kann
- **Schutzkern je Region** (Safe-Zone) mit überschriebenen Regeln
- Enter-/Leave-Ereignisse, getrennt für Region und Schutzkern
- **Benannte Spawn-Bereiche** als Anschluss für B10 — die Bereiche gehören
  hierher, die Horden nicht
- **Wegpunkt-Kristalle** in den Safe-Zones: Freischaltung je Charakter,
  Auswahlfenster, Reise gegen Coins (ADR-032)
- Tod, Respawn und die Behandlung des Kampf-Logouts
- Zonenanzeige im HUD (B13)

## Zentrale Architekturvorgabe

> **Eine `Zone` ist niemals eine `World`.**

Eine Zone wird modelliert als `(worldId, Geometrie)`. Jeder Zugriff läuft über
die Zonen-API. Damit ist „Zone X liegt in der Hauptwelt" gegenüber „Zone X ist
eine eigene Welt" eine Konfigurationszeile und kein Umbau. Diese Vorgabe gilt
unabhängig vom Ausgang von ADR-006.

## Die sechs Regionen *(entschieden 2026-08-23)*

| # | Name | Levelband | Rolle |
|---|---|---|---|
| 1 | **The Greenfields** | 1–10 | Startgebiet |
| 2 | **The Dustlands** | 11–20 | |
| 3 | **The Safari Plains** | 21–30 | |
| 4 | **The Terracotta Canyons** | 31–40 | |
| 5 | **The Darkforest** | 41–50 | |
| 6 | **The Pale Wilds** | 51–60 | End-Game |

Die sechs Bänder decken **1 bis 60 lückenlos** ab — genau den Bereich, den
`progression.yml` aufspannt: der höchste Schlüssel der XP-Kurve *ist* die
Maximalstufe, bewusst keine Konstante im Code. Weitere Regionen kommen später
**per Konfiguration** dazu, nicht per Code (Prinzip V).

Das ersetzt die vorläufige Antwort „4–5 Zonen" vom 2026-08-19. Die Staffelung
selbst bleibt, wie sie beschlossen war: aufsteigende, nicht überlappende
Levelbereiche.

## Entscheidungen *(2026-08-23)*

### Safe und Danger: die Region ist die Zone, der Schutzkern liegt darin

Jede Region hat einen **Schutzkern** um ihren Spawn (Safe-Zone) und darum herum
die Gefahrenzone, in der die Mobs stehen. Modelliert wird das als *eine* Zone
mit einem inneren Bereich, der einzelne Regeln überschreibt — nicht als zwei
gleichrangige Zonen.

**Begründung:** `zoneAt()` gibt damit immer die Region zurück, das HUD zeigt
„The Greenfields" statt „Greenfields Safe", und Levelband wie Respawn-Punkt
hängen an genau einem Objekt. Das Verlassen des Spawns ist ein eigenes,
leichteres Ereignis und **kein** Zonenwechsel — sonst feuerte der Wechsel
zwölfmal statt sechsmal und jeder Verbraucher müsste erst zum Elternteil
hochlaufen.

Im Schutzkern gilt: keine Mob-Spawns, kein PvP.

### Geometrie: Quader-Mengen mit Chunk-Index

Ein Bereich — Region, Schutzkern, Spawn-Bereich und Kristall-Auslösebereich gleichermaßen — ist
eine **Liste von Quadern**: zwei Ecken je Quader, Y optional unbegrenzt. Beim
Laden stempelt jeder Quader die von ihm berührten Chunks in eine Abbildung
`Chunk → Zone`. Die Abfrage ist im Normalfall ein Lookup; nur wenn ein Chunk von
zwei Bereichen berührt wird, folgt ein exakter Quadertest.

**Begründung:** von Hand schreibbar (zwei Ecken), exakte Grenzen, und die
Vorgabe „nie durch Iteration über alle Zonen" ist damit erfüllt statt nur
gemeint. Dieselbe Form trägt Schutzkern, Spawn-Bereich und Kristall-Auslösebereich, also gibt es
ein Geometriesystem und nicht drei. Polygone bleiben nachrüstbar, weil die API
nur `contains(worldId, x, y, z)` verspricht.

### Reisen: Wegpunkt-Kristalle *(ersetzt bei `/clarify` am 2026-08-23 die Portale, ADR-032)*

In jeder Safe-Zone steht ein **Kristall**. Der erste Rechtsklick schaltet ihn für
diesen Charakter frei; ein weiterer öffnet ein Fenster mit **allen** Kristallen —
wählbar sind nur die freigeschalteten, die übrigen bleiben sichtbar und melden
beim Anklicken, dass sie noch nicht freigeschaltet sind. Eine Reise kostet Coins.

**Begründung:** das Modell erhält die **erste Reise** — jede Region muss einmal zu
Fuß erreicht worden sein, bevor sie ein Ziel wird. Sichtbare, aber gesperrte Ziele
sind genau der Anreiz, sie zu suchen.

**Was es kostet, und warum es trotzdem hier entsteht:** vier Eingriffe über die
Blockgrenze hinaus — Eingabe und Auswahlfenster (B13), dauerhafter Zustand je
Charakter (B02) und ein neuer Buchungsgrund (B08b, abgeschlossen). Festgehalten in
**ADR-032**, Fenster und Eingabe befristet bis B13, nach dem Muster von ADR-028.
Auf B13 zu warten hiesse, bis dahin gar kein Reisen zu haben — und der Rückweg vom
*Pale Wilds* ins *Greenfields* führte jedes Mal über fünf Regionen.

Der Preis steht in der Zonenkonfiguration, bei dem, der ihn verlangt (ADR-027);
die Freischaltungen hängen am Charakter, nicht am Account (ADR-011).

> **Überholt, nicht gelöscht:** am Morgen desselben Tages war entschieden, das
> Reisen über **Portale als Konfigurationsquader** zu lösen — ein Quader mit
> Zielkoordinate, kostenlos, ohne Bedienoberfläche. Diese Fassung hielt den Block
> in seiner Schicht; sie ist an den Wegpunkt-Kristallen gescheitert, nicht an einem
> Fehler.

### Levelgate: keine Sperre, nur eine Warnung

Jeder Spieler darf jede Region betreten, unabhängig von seiner Stufe. Liegt er
unter dem Levelband, bekommt er beim Betreten **eine Meldung**, dass es hier für
ihn zu gefährlich ist.

**Begründung:** die Mobs setzen die Staffelung von selbst durch. Zurückschieben
an einer unsichtbaren Linie bräuchte Anti-Klemm-Logik und Sonderfälle bei
Teleport und Relogin — Aufwand für eine Grenze, die man versehentlich
überschreitet.

### PvP: Schutzkern aus, Gefahrenzone je Zone schaltbar, Vorgabe aus

B09 ersetzt `DamagePermission` durch eine zonenabhängige Regel und erfüllt damit
FR-042. Alle sechs Regionen starten mit `pvp: false`.

**Begründung:** spielerisch ändert sich nichts, aber der Austausch ist bewiesen
statt behauptet — `SinglePermissionPointTest` bewacht genau diese Stelle. Eine
PvP-Zone ist danach eine Konfigurationszeile. Echtes PvP ab Tag eins zöge Fragen
nach sich, die B09 nicht beantworten kann: was ein Tod durch einen Spieler
kostet, ob es Beute vom Gegner gibt, und ob die sechs Klassen gegeneinander
überhaupt ausgewogen sind.

### Tod und Respawn

Wer stirbt, wird in die **Safe-Zone seiner Region** teleportiert und bekommt die
Nachricht, dass er gestorben ist. Kein XP-Verlust, kein Item-Verlust — das war
am 2026-08-19 beschlossen und bleibt.

### Kampf-Logout wird wie ein Tod behandelt

Verlässt ein Spieler den Server, während er als im Kampf gilt, **stirbt sein
Charakter**. Beim nächsten Login steht er in der Safe-Zone seiner Region und
liest, warum.

- Der Kampfzustand ist bereits vorhanden: `CombatState` mit
  `combat-timeout-seconds: 8` aus `combat.yml`. Diese acht Sekunden sind die
  Karenz — wer sich nach dem Kampf sammelt und dann ausloggt, wird nicht
  bestraft.
- Der Todesgrund wird unterscheidbar: `DeathCause.LOGOUT`, damit B12 später
  „gestorben" und „abgehauen" auseinanderhalten kann und das Protokoll ehrlich
  bleibt. Das fasst ein ausgeliefertes Enum in B05 an und war damit
  ADR-pflichtig → **ADR-030, angenommen am 2026-08-23**, vor Beginn der
  Umsetzung.
- Der Ausrüstungsschaden, den ein normaler Tod kostet, braucht B11 und bleibt
  bis dahin eine benannte Lücke (Regel 5).
- Die Regel steht als Schalter in der Konfiguration:
  `combat-logout: death | none`.

**Begründung:** der Gewinn des Weglaufens ist, dem Tod zu entgehen. Bringt
Weglaufen genau den Tod, ist der Gewinn null — mehr braucht Abschreckung nicht.
Eine *härtere* Strafe als der Tod wäre in diesem Spiel verkehrt herum: der Tod
kostet hier bewusst wenig, und wer fürs Fliehen mehr zahlt als fürs Sterben,
bleibt lieber stehen und stirbt.

### Keine Instanzen zum Start

Die Boss-Mobs stehen **in** ihrer Region, nicht in einer eigenen Bosswelt. Damit
gibt es zum Start keine Instanzen, `WorldCondition.isOpenWorld` sagt weiterhin
überall ja, und das Zweitleben des Rogue wirkt überall (FR-052b).

Bisher war das eine Lücke mit einer bewusst freundlichen Vorgabe; jetzt ist es
eine Entscheidung. Separate Welten für Instanzierbares bleiben nach ADR-006
vorgesehen und sind über einen Kristall anschließbar, ohne dass das Modell sich
ändert.

## Topologie (ADR-006, bestätigt 2026-08-19)

- Eine große, handgebaute und **vorgenerierte** Kontinent-Welt mit hartem World
  Border für offene Level- und Sozialzonen; Richtgröße 6.000×6.000 bis
  10.000×10.000 Blöcke; keine Vanilla-Weltgenerierung zur Laufzeit
- Separate Welten nur für Instanzierbares: Dungeons, Bossräume, Tutorial
- Simulation-Distance 4–6, View-Distance 8–10
- Mehrere gleichwertige Zonen je Levelbereich, um Spieler zu verteilen

**Begründung:** Paper tickt alle Welten in einem Main-Thread. Mehrere Welten
bringen keine CPU-Parallelität — der Nutzen liegt allein in Entladbarkeit,
Instanzierbarkeit und Per-Welt-Regeln.

*Anmerkung 2026-08-23:* „mehrere gleichwertige Zonen je Levelbereich" ist mit den
sechs Regionen **noch nicht** eingelöst — je Levelband gibt es genau eine Region.
Die Verteilung der Spieler bleibt damit ein offener Punkt des Weltdesigns, kein
offener Punkt dieses Blocks: das Modell lässt eine zweite Region im selben Band
jederzeit als Konfiguration zu.

## Architekturvorgaben

- Zonenerkennung über räumlichen Index (Grid oder R-Tree), **nie** durch
  Iteration über alle Zonen.
- Zonenwechsel erzeugt genau ein Ereignis, auf das andere Blöcke reagieren.
- Zonen sind vollständig konfigurationsdefiniert und zur Laufzeit nachladbar.
- Die Entscheidung „wer darf wen verletzen" bleibt an **einer** Stelle; B09
  tauscht sie aus, statt eine zweite Kopie anzulegen (FR-042,
  `SinglePermissionPointTest`).

## Offene Fragen — alle geschlossen

- [x] **ADR-006 bestätigt**. *(2026-08-19)*
- [x] **Kartenbau**: Handgebaut. *(2026-08-19)*
- [x] **Anzahl Zonen zum Start**: **sechs** benannte Regionen, Levelbänder 1–10
      bis 51–60. *(2026-08-23; ersetzt „4–5 Zonen" vom 2026-08-19)*
- [x] **Safe/Danger**: die Region ist die Zone, der Schutzkern liegt darin.
      *(2026-08-23)*
- [x] **Zonengeometrie**: Quader-Mengen mit Chunk-Index. *(2026-08-23)*
- [x] **Reisesystem**: **Wegpunkt-Kristalle** — per Rechtsklick freischaltbar, danach
      Auswahlfenster mit allen Kristallen (gesperrte sichtbar), Reise gegen Coins.
      *(2026-08-23, bei `/clarify`, ADR-032; ersetzt die am Morgen beschlossenen
      kostenlosen Config-Portale)*
- [x] **Spieler unterhalb des Levelbereichs**: nicht blockiert, nur gewarnt.
      *(2026-08-23)*
- [x] **PvP**: je Zone schaltbar, Vorgabe aus, im Schutzkern immer aus.
      *(2026-08-23)*
- [x] **Kampf-Logout**: wird wie ein Tod behandelt. *(2026-08-23)*
- [x] **Instanzen**: keine zum Start; Bosse stehen in der Region. *(2026-08-23)*

## Was ausdrücklich nicht hierher gehört

- **Mobs (B10):** die acht Mob-Arten je Region, ihre Attribute, ihr Level, der
  Boss und die Hordenlogik. B09 liefert die Zone, das Levelband und die
  benannten Spawn-Bereiche; B10 füllt sie. **Auch der
  Schwierigkeitsmodifikator gehört B10** — samt dem Feld, in dem er steht.
- **Loot (B11):** die Loot-Tables der Mobs, der Ausrüstungsschaden beim Tod und
  seine Reparatur. **Auch die Loot-Zuordnung je Zone gehört B11** — samt dem
  Feld, in dem sie steht.
- **HUD-Text (B13):** die Anzeige des Zonennamens. B09 liefert Name und
  Ereignis.

## Akzeptanzkriterien (Entwurf)

- Zonenzugehörigkeit von 200 Spielern pro Tick zu ermitteln kostet < 0,5 ms.
- Eine Zone lässt sich per Konfiguration von der Hauptwelt in eine eigene Welt
  verschieben, ohne dass Code angefasst wird.
- Enter-/Leave-Ereignisse feuern zuverlässig, auch bei Teleport und Relogin.
- Eine **siebte Region** entsteht durch Konfiguration allein — kein Code.
- Betreten einer Region unter ihrem Levelband erzeugt genau eine Warnung, keine
  Sperre.
- Ein Tod führt in die Safe-Zone der Region, in der gestorben wurde.
- Ein Logout innerhalb der acht Kampfsekunden führt beim nächsten Login in die
  Safe-Zone, mit Nachricht; ein Logout danach nicht.
- Im Schutzkern spawnt kein Mob und wirkt kein PvP, auch wenn die Region
  `pvp: true` trägt.
