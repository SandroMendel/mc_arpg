# Phase 0 · Research — B09 · Zonen & Regionen

Zwölf Fragen mussten vor dem Entwurf beantwortet werden — die zehn, die die Spec an `/plan` übergeben
hat, plus zwei, die bei der Prüfung dazukamen. **Elf sind entschieden. Eine hat eine Anforderung der
Spec widerlegt** (R1) und verlangt deren Umformulierung.

---

## R1 · Ist FR-050b überhaupt zusagbar? — **Nein, nicht wörtlich**

FR-050b verlangt: „Buchung und Versetzung MÜSSEN zusammen gelten: es DARF NICHT vorkommen, dass
gebucht wurde und die Versetzung ausbleibt oder umgekehrt." Das ist so nicht einlösbar, und es ist
besser, das jetzt zu wissen als beim Testen.

**Was B08b anbietet.** `debit(characterId, amount, reason)` prüft und zieht **in einem Schritt** ab
und ist unteilbar gegenüber anderen Buchungen desselben Kontos. Der Vertrag sagt zugleich
ausdrücklich, dass es **keine** Fassung gibt, die nur prüft und eine Zusage zurückgibt — „zwei
Fähigkeiten im selben Tick würden sonst beide dasselbe Geld ausgeben". Eine Reservierung zu
verlangen wäre also nicht nur eine Erweiterung, sondern eine Umkehrung einer bewusst getroffenen
Entscheidung in einem abgeschlossenen Block.

**Was fehlt.** Die Versetzung ist ein Paper-Aufruf und kein Teil derselben Buchung. Sie kann
scheitern: ein anderes Plugin bricht das Teleport-Ereignis ab, die Zielwelt ist nicht geladen, der
Spieler verlässt den Server zwischen Klick und Ausführung.

**Entscheidung: `debit` zuerst, dann versetzen, und bei Fehlschlag zurückbuchen.**

1. `debit(..., WAYPOINT_TRAVEL)` — kommt `NOT_ENOUGH` zurück, endet der Vorgang mit einer Meldung,
   und nichts ist passiert.
2. Versetzen. Gelingt es, ist der Vorgang fertig.
3. Gelingt es **nicht**, `credit(..., WAYPOINT_REFUND)` — derselbe Betrag zurück, mit eigenem Grund.

**Warum diese Reihenfolge.** Umgekehrt — erst versetzen, dann buchen — wäre ein Fehlschlag beim
Buchen eine Freifahrt. Von den beiden möglichen Fehlern ist der rückholbare der bessere: Geld lässt
sich zurückbuchen, eine Versetzung nicht.

**Warum die Lücke schmal ist.** Alles drei läuft im **selben Tick** (Prinzip I: Paper-API nur im
Tick). Der Server ist dort einsträngig, also kann sich zwischen Abbuchung und Rückbuchung keine
andere Buchung desselben Charakters dazwischenschieben. Für den Spieler ist der Vorgang damit
ungeteilt. Offen bleibt allein ein Serverabsturz **zwischen** Abbuchung und Rückbuchung — und da
entscheidet der Schreib-Puffer aus B02, ob die Abbuchung überhaupt schon geschrieben war. Ein Fenster
von Mikrosekunden gegen einen Absturz ist kein Fall, für den sich eine Transaktionsschicht über zwei
fremde Systeme lohnt.

**Folge für die Spec: FR-050b ist umzuformulieren.** Zusagbar ist nicht „nie halb", sondern **„nie
mit Verlust"**: entweder die Reise fand statt, oder der Kontostand ist wiederhergestellt, und der
Verlauf erklärt beides. Zwei Buchungen sind dabei sichtbar, keine verschwindet — genau das, was B08bs
Zusage „jede Änderung nennt ihren Grund" verlangt.

**Zwei neue Buchungsgründe**, statt einem: `WAYPOINT_TRAVEL` (DEBIT) und `WAYPOINT_REFUND` (CREDIT).
Der Rückbuchungsgrund ist nicht Zierde — ohne ihn wäre eine Rückbuchung im Verlauf von einer
Gutschrift nicht zu unterscheiden, und niemand könnte nachsehen, wie oft der Fall eintritt.

**Alternativen geprüft:**

- **Reservierung in B08b ergänzen** — verworfen. Kehrt eine ausdrückliche Entscheidung des
  Vertrags um und öffnet genau das Loch, das dort beschrieben ist.
- **Versetzen und Buchen in einem Aufruf von B08b** — verworfen. B08b müsste dann Paper anfassen; es
  ist ein Schicht-1-Block ohne Weltbezug.
- **Nur `canAfford` prüfen und nach dem Teleport buchen** — verworfen. Der Vertrag nennt `canAfford`
  ausdrücklich **keine** Reservierung und warnt vor genau dieser Verwendung.

---

## R2 · Wo die Geometrie wohnt

**Entscheidung:** die ganze Rechnung in `rpg-core`, die Übersetzung in `rpg-platform`.

| Schicht | Was dort liegt |
|---|---|
| `rpg-core/zone` | `Cuboid`, `Area` (Liste von Quadern), `ChunkZoneIndex`, `Zone`, `ZoneRules`, `Zones` (die Abfrage), `WaypointCrystal`, die Regelauswertung |
| `rpg-platform/zone` | Übersetzung `org.bukkit.Location` → `WorldPosition`, alle Listener, das Auswahlfenster, das Versetzen |

**`WorldPosition` wird nicht neu erfunden.** `rpg-core/scheduler/WorldPosition` existiert bereits als
`(UUID worldId, double x, double y, double z)` und trägt in seinem Javadoc genau diese Begründung:
„rpg-core must compile and be testable without Bukkit on the classpath". Ein zweiter Ortstyp im
selben Modul wäre die Doppelung, die ADR-015 Punkt 6 vermeiden wollte. Der Wohnort im
`scheduler`-Paket ist dabei ein Schönheitsfehler, kein Grund für einen Umzug: ein ausgelieferter Typ
zieht nicht um, damit ein neuer Nutzer ihn schöner findet.

Damit ist SC-014 („die gesamte Zonenlogik ist ohne laufenden Server geprüft") strukturell erfüllt und
nicht bloss beabsichtigt.

---

## R3 · Der Chunk-Index

**Entscheidung:** je Welt eine Abbildung von **gepacktem Chunk-Schlüssel** auf Kandidaten.

- Schlüssel: `((long) chunkX << 32) | (chunkZ & 0xFFFFFFFFL)` — ein `long`, kein Objekt.
- Wert: ein Feld der Zonen, die diesen Chunk berühren. Im Normalfall genau eine.
- Aufbau beim Laden: jeder Quader stempelt die von ihm überdeckten Chunks. Zur Laufzeit wird nur
  gelesen.

**Kein Boxing im heissen Pfad** (Prinzip II): die Abbildung ist eine `long`-Schlüssel-Struktur, keine
`HashMap<Long, ...>`. Bei einem einzigen Kandidaten entfällt der Quadertest ganz.

**Grenzchunks werden markiert.** Ein Chunk, den mehr als ein Bereich berührt — zwei Regionen, oder
eine Region und ihr Schutzkern, oder ein Kristall — trägt ein Kennzeichen. Das löst einen Widerspruch,
der sonst erst im Spiel aufgefallen wäre: siehe R4.

**Speicher:** 10.000 × 10.000 Blöcke sind 625 × 625 = 390.625 Chunks. Selbst wenn alle sechs Regionen
die ganze Karte überdeckten, ist das eine Abbildung mit weniger als 400.000 `long`-Einträgen — ein
paar Megabyte, einmal beim Start gebaut.

---

## R4 · Wann eine Bewegung neu bewertet wird

**Der Widerspruch.** FR-020 verlangt: Bewegung innerhalb desselben Chunks löst **keine**
Neubewertung aus. FR-016 verlangt: das Überschreiten der Schutzkerngrenze feuert ein Ereignis. Eine
Schutzkerngrenze verläuft aber mitten durch Chunks — mit der Chunk-Regel allein bliebe sie
unbemerkt, bis der Spieler den Chunk verlässt.

**Entscheidung:** der Grenzchunk-Marker aus R3 entscheidet.

```
Bewegung:
  wenn Ziel-Chunk == Herkunfts-Chunk und Chunk ist KEIN Grenzchunk  → nichts tun
  sonst                                                            → auswerten
```

`PlayerMoveEvent` ist eines der häufigsten Ereignisse eines Servers — `DoubleJumpListener` schreibt
das im Projekt schon so hin. Der Vergleich ist deshalb reine Ganzzahlarithmetik auf den
Blockkoordinaten (`from.getBlockX() >> 4`), ohne `Chunk`-Objekt und ohne Allokation. Erst wenn er
anspricht, folgt der Index-Zugriff.

**Wirkung:** in der weiten Fläche einer Region kostet Bewegung zwei Vergleiche. Nur in den wenigen
Chunks an einer Grenze oder am Schutzkern wird je Bewegung ausgewertet — und das ist genau dort, wo
es nötig ist. FR-020 ist damit eingehalten, ohne FR-016 zu brechen.

---

## R5 · Der Rechtsklick auf einen Kristall

**Entscheidung:** derselbe Index, ein zweiter Eintrag. `PlayerInteractEvent` liefert den angeklickten
Block; aus dessen Koordinaten wird der Chunk-Schlüssel gebildet und im Kristall-Index gesucht. Kein
Durchlauf über alle Kristalle, kein Blocktyp-Vergleich.

`AbilityTriggerListener` zeigt dabei das Muster, an dem sich der Listener orientiert — einschliesslich
der dort dokumentierten Falle, dass ein Interact-Ereignis auf Luft von Geburt an abgebrochen ist.

**Warum kein Blocktyp:** ein Kristall wird als Bauwerk gesetzt (Assumption der Spec). Würde der
Blocktyp die Erkennung tragen, hinge das Reisen daran, dass niemand den Stein abbaut oder einen
zweiten desselben Typs aufstellt.

**Ratenbegrenzung** (Prinzip VI: Client-Eingaben werden rate-limitiert): das Öffnen des Fensters
bekommt eine kurze, zeitstempelbasierte Sperre je Spieler — lazy ausgewertet, keine wiederkehrende
Aufgabe. Ohne sie wäre gedrückt gehaltene rechte Maustaste ein Fenster je Tick.

---

## R6 · Alle Anwesenden nach einem Neuladen neu bewerten

**Entscheidung:** `ZoneModule.applyReloadedConfig()`, aufgerufen aus `RpgPlugin.reloadConfiguration()`
— **genau das Muster, das B04 schon benutzt**:

```java
configLoader.reloadAll();
if (statsModule != null) { statsModule.applyReloadedConfig(); }   // vorhanden
if (zoneModule  != null) { zoneModule.applyReloadedConfig(); }    // neu
```

Der Aufruf baut den Index neu und läuft **einmal** über die angemeldeten Spieler. Ein einmaliger
Durchlauf ist keine wiederkehrende Aufgabe je Spieler — Prinzip II ist eingehalten, und der Beleg ist
die Abwesenheit jeder `Scheduler`-Registrierung in diesem Block.

Ereignisse feuern dabei nur für tatsächliche Änderungen (FR-018): verglichen wird die vorher
festgehaltene Zuordnung mit der neuen.

`AbstractConfigLoader.reloadAll()` ist bereits **unteilbar mit Rückabwicklung** — ein abgelehntes
Dokument lässt die vorige Konfiguration für alle Module aktiv. B09 erbt das und braucht dafür nichts.

---

## R7 · Wo die Warnung über die vorläufigen Koordinaten entsteht

**Entscheidung:** im `start()` des Moduls, **nicht** in `applyReloadedConfig()`.

FR-065b verlangt die Warnung „bei jedem Start". Läge sie im gemeinsamen Ladepfad, käme sie bei jedem
`/rpg reload` erneut — technisch harmlos, aber es verwässert genau das Signal, das sie sein soll.

Sie folgt der vorhandenen Protokollform (`[config] phase=START ...`), damit sie dort steht, wo
Betreiber ohnehin hinsehen.

---

## R8 · Wo die Freischaltungen liegen

**Entscheidung:** ein **eigener Aggregattyp** `CHARACTER_ZONE_STATE`, mit den drei Eintragungen, die
ADR-015 Punkt 7 verlangt — die Enum-Konstante, ihr Platz in der Schreibreihenfolge, und die
Registrierung im Flush-Zyklus. Migration `V9_1__character_zone_state.sql`.

Zwei Tabellen, ein Aggregat:

| Tabelle | Inhalt |
|---|---|
| `rpg.character_waypoints` | eine Zeile je freigeschaltetem Kristall — wächst nur |
| `rpg.character_zone_state` | eine Zeile je Charakter mit dem **ausstehenden Respawn** (R11) |

Beide mit `REFERENCES rpg.character (character_id) ON DELETE CASCADE`, wie jede charakterbezogene
Tabelle im Projekt. Damit erledigt FR-051b1 sich von selbst, und die Anonymisierung ebenso — dieselbe
Begründung, die in `V4_1` und `V6_1` schon als Kommentar steht.

**Warum nicht im Sitzungsbündel mitreisen:** Freischaltungen wachsen und werden selten angefasst. In
einem Datensatz, der bei jedem Login vollständig gelesen und bei jedem Flush vollständig geschrieben
wird, wären sie mit jeder Sitzung teurer.

**Warum ein Aggregat für zwei Tabellen:** beides ist Zonenzustand desselben Charakters und wird im
selben Moment geschrieben. Zwei Aggregattypen wären zwei Plätze in der Schreibreihenfolge für eine
Sache. `CHARACTER_INVENTORY` zeigt im Projekt schon, dass ein Aggregat mehr als eine Tabelle tragen
darf.

---

## R9 · Ein eigenes Ereignis für den Schutzkern

**Entscheidung:** ein **zweiter Ereignistyp**, nicht ein Feld am Zonenwechsel.

FR-016 sagt, dass der Schutzkernwechsel **kein** Zonenwechsel ist. Ein Zonenereignis mit „Zone
unverändert" wäre eine Unwahrheit im Typ, und jeder Verbraucher, der auf Zonenwechsel hört, müsste
lernen, manche davon zu ignorieren. Zwei Typen kosten eine Datei und ersparen jedem Verbraucher eine
Bedingung:

- `ZoneChangedEvent(characterId, from, to)` — eine Seite darf leer sein.
- `SafeAreaCrossedEvent(characterId, zoneKey, entered)`.

---

## R10 · Wie die Schadenserlaubnis ersetzt wird

**Entscheidung:** eine `ZoneDamagePermission`, die `DamagePermission.defaultRule()` **umschliesst**
und über `CombatPipeline.setPermission` eingesetzt wird.

```
isAllowed(...):
  1. Ziel im Schutzkern      → nein          (FR-028a)
  2. Angreifer im Schutzkern → nein          (FR-028a)
  3. Spieler gegen Spieler   → pvp-Schalter der Zone des Ziels, Vorgabe nein
  4. sonst                   → defaultRule() unverändert
```

Die ausgelieferte Regel wird damit **benutzt statt nachgebaut** — die vier Fälle, die B09 nichts
angehen, kommen weiterhin aus B05s eigener Methode. `SinglePermissionPointTest` bleibt grün, weil
genau das der Vorgang ist, für den er geschrieben wurde: austauschen, nicht kopieren. SC-008 verlangt
als Nachweis, dass B05s vorhandene Tests **unverändert** grün bleiben.

**Umweltschaden im Schutzkern** fällt unter Schritt 1: `attackerId == null` erreicht die
Vorgaberegel nicht mehr, wenn das Ziel im Kern steht. Das ist die einzige Stelle, an der B09 die
ausgelieferte Antwort umdreht — und sie ist in FR-028a ausgeschrieben.

---

## R11 · Wie ein Kampf-Logout zum Tod wird

**Das Problem:** die Folge des Todes ist eine Versetzung, aber der Spieler verlässt gerade den
Server. Ein Teleport in `PlayerQuitEvent` ist auf Paper nicht verlässlich — die Position wird
möglicherweise nach dem Ereignis geschrieben.

**Entscheidung:** zwei Schritte über einen persistierten Merker.

1. **Beim Verlassen:** gilt der Charakter als im Kampf, wird ein **ausstehender Respawn** auf die
   Zone gesetzt, in der er sich befand, und ein `CombatDeathEvent` mit `DeathCause.LOGOUT`
   veröffentlicht — `killerId` leer, `playerVictim` wahr. Damit sehen B06 und später B12 denselben
   Vorgang wie jeden anderen Tod.
2. **Beim nächsten Anmelden:** steht ein Merker, wird der Charakter an den Respawn-Punkt dieser Zone
   versetzt, die Meldung ausgegeben und der Merker gelöscht.

`DeathCause` bekommt seinen vierten Wert. Der Compiler zeigt jede Stelle, die über die Aufzählung
verzweigt — das ist der Nachweis, den ADR-030 dafür verlangt.

**Der Merker liegt im Aggregat aus R8**, nicht im Sitzungsbündel: er muss einen Neustart überstehen,
denn zwischen Logout und nächster Anmeldung kann der Server neu starten.

**Offen für `/tasks`, nicht für den Entwurf:** ob der Logout-Tod zusätzlich die Vanilla-Gesundheit
anfasst. Die Neigung ist **nein** — der Charakter erscheint beim nächsten Login ohnehin am
Respawn-Punkt, und B05s Respawn füllt Leben und Mana auf.

---

## R12 · Was aus `XpSource.ZONE_OBJECTIVE` und `SourceKind` wird

**Entscheidung: nichts.** Beide bleiben unbefüllt, und das ist kein Versäumnis.

- `XpSource.ZONE_OBJECTIVE` braucht **Zonenziele**. Dieser Block definiert keine — er definiert
  Zonen. Ein Ziel ist Inhalt und gehört zu B10 oder einem Questblock, den es nicht gibt.
- `SourceKind` für zonengebundene Effekte braucht einen **Effekt je Zone**. Das wäre der
  Schwierigkeitsmodifikator, und der ist bei `/clarify` bewusst herausgenommen worden (FR-056).

Die Spec sagt das in ihren Annahmen schon; hier steht es, damit `/tasks` nicht versucht, eine Aufgabe
daraus zu machen. **US7 löst zwei der vier wartenden Schnittstellen ein** — `WorldCondition` und die
Spawn-Bereiche — und benennt die beiden anderen als weiterhin wartend. Das ist der ehrliche Stand.
