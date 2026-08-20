# Phase 0 — Recherche: B05 · Kampf- & Schadens-Pipeline

**Feature**: `specs/005-combat-pipeline` | **Datum**: 2026-08-20

Acht Entwurfsentscheidungen. Die fünfzehn Produktfragen sind bereits geklärt (sieben aus dem
Blocksteckbrief, acht aus zwei `/clarify`-Runden) und stehen in der Spec; hier stehen ausschließlich
Umsetzungsfragen.

---

## E1 · Die Vanilla-Quellentabelle ist erschöpfend, nicht handgepflegt

**Entscheidung**: Die Zuordnung ist ein vollständiger Switch über Papers `DamageCause` mit einem
**Verweigerungs-Standardfall**: Eine Ursache, die niemand entschieden hat, wird neutralisiert und
einmal je Ursache protokolliert.

**Begründung**: Der Blocksteckbrief nennt 17 Schadensursachen. Paper 26.2 kennt rund 30 — darunter
`FREEZE`, `CRAMMING`, `DRYOUT`, `SONIC_BOOM`, `WORLD_BORDER`, `FALLING_BLOCK`, `DRAGON_BREATH`,
`CAMPFIRE`, `THORNS` und `FLY_INTO_WALL`. Eine handgepflegte Liste hätte jede davon stillschweigend
durchgelassen, und ADR-003 verlangt ausdrücklich, dass **jede** Quelle eine Entscheidung hat.

Der Verweigerungs-Standardfall dreht zusätzlich die Richtung des Risikos: Ein Minecraft-Update, das
eine Ursache hinzufügt, kann keinen Schaden durchlassen. Es erzeugt eine Protokollzeile, die zur
Entscheidung auffordert — statt eines Spielers, der aus unerfindlichem Grund stirbt.

Der Switch ist zudem erschöpfbar: fehlt eine Konstante, meldet es der Compiler, nicht der Betrieb.

**Alternativen**:

- *Liste aus der Konfiguration* — verlagert die Vollständigkeit in eine Datei, die niemand prüft,
  und ein Tippfehler wäre eine stillschweigend fehlende Zuordnung.
- *Pauschal alles abschalten außer einer Erlaubnisliste* — sicher, aber es würde bedeuten, dass jede
  neue Umgebungsgefahr erst auffällt, wenn sich jemand wundert, warum Lava nicht mehr wehtut.

---

## E2 · Ein wiederverwendeter Schadensvorgang, aber eine unveränderliche Sicht nach außen

**Entscheidung**: `DamageContext` ist ein veränderliches Objekt, das je Tick-Thread wiederverwendet
und nach jedem Vorgang zurückgesetzt wird. Pipeline-Stufen und Eingriffspunkte bekommen nicht das
Objekt, sondern `DamageView` — eine unveränderliche Sicht mit einem schmalen Satz gezielter
Änderungsmethoden.

**Begründung**: FR-045 verlangt, je Treffer keine vermeidbaren Objekte zu erzeugen. Bei 150 Spielern
mit vier Angriffen je Sekunde plus Mobs sind das leicht mehrere tausend Vorgänge je Sekunde; ein
Objekt je Vorgang ist Müll, den der Tick bezahlt.

Der Preis der Wiederverwendung ist eine Falle: Eine Stufe, die den Vorgang über sein Ende hinaus
festhält, sieht später fremde Daten — und der Fehler zeigt sich als falscher Schaden an einer ganz
anderen Stelle. Die Lesesicht macht daraus einen Vertragsbruch, den man nicht versehentlich begeht.

**Alternativen**:

- *Je Treffer ein neues Objekt* — einfach und sicher; verworfen wegen FR-045, aber als Rückfallebene
  vorgemerkt, falls der Lasttest zeigt, dass die Wiederverwendung nichts einbringt.
- *Alles als Methodenparameter* — keine Allokation, aber die Signatur trüge acht Werte durch sechs
  Stufen, und jeder neue Eingriffspunkt änderte sie.

---

## E3 · Projektile tragen ihren Rohschaden, nicht ihren Schnappschuss

**Entscheidung**: Beim Abschuss berechnet B05 den Rohschaden aus dem Schnappschuss des Schützen und
hinterlegt ihn als einzelne Zahl am Projektil. Beim Einschlag wird sie gelesen; ist keine da, gilt
der Treffer als nicht von diesem System stammend und der Vanilla-Schaden wird nur neutralisiert.

**Begründung**: FR-024b verlangt den Wertestand vom Abschuss. Die naheliegende Lösung — eine Karte
von Projektil-Kennung auf Schnappschuss — ist ein Leck mit Ansage: Ein Pfeil, der in einem
entladenen Chunk verschwindet, räumt seinen Eintrag nie auf. Eine Zahl am Projektil verschwindet mit
dem Projektil.

Zusätzlich hält niemand einen Schnappschuss über unbestimmte Zeit fest, was FR-021 aus B04 sauber
hält.

**Alternativen**:

- *Karte von Projektil auf Schnappschuss mit Verfallszeit* — funktioniert, verlangt aber genau die
  Aufräumaufgabe, die Prinzip II vermeiden will.
- *Beim Einschlag neu berechnen* — verstößt gegen FR-024b: ein Schütze, der zwischen Abschuss und
  Einschlag sein Schwert wechselt, bekäme rückwirkend anderen Schaden.

---

## E4 · Zeitstempel statt Timer — dreimal dasselbe Muster

**Entscheidung**: Angriffszeitfenster, Kampfzustand und Beitragsalter sind je ein Zeitstempel, der
erst bei Zugriff gegen die aktuelle Zeit geprüft wird. Es gibt keinen Timer, keine Aufgabe und keine
Ablaufliste.

**Begründung**: Prinzip II sagt es wörtlich: zeitbasierte Werte werden zeitstempelbasiert lazy
ausgewertet. Der Unterschied ist nicht theoretisch — eine Aufgabe je Spieler wären bei 200 Spielern
200 wiederkehrende Aufgaben, und der Kampfzustand allein bräuchte eine je Träger, also bis zu 1000.

Alle drei Fälle brauchen dieselbe Antwort auf dieselbe Frage („ist seitdem genug Zeit vergangen?"),
also bekommen sie dasselbe Muster statt drei Erfindungen.

**Alternativen**:

- *Ablaufende Aufgaben je Träger* — verstößt gegen Prinzip II und wäre bei 800 Mobs allein für den
  Kampfzustand teurer als der Kampf selbst.
- *Ein serverweiter Aufräumdurchlauf* — dieselbe Diskussion wie bei B04s Bündelung, mit demselben
  Ergebnis: eine globale wiederkehrende Aufgabe ist genau das, was Prinzip I und II ausschließen.

---

## E5 · Das Beitragsfenster ist ein Array fester Größe

**Entscheidung**: Je Ziel ein Array mit 16 Plätzen (konfigurierbar) aus Angreifer-Kennung,
Schadenssumme und Zeitstempel. Ist es voll, weicht der kleinste Beitrag. Beim Zugriff werden
abgelaufene Plätze frei.

**Begründung**: FR-032 und die Architekturvorgabe des Blocksteckbriefs verlangen eine begrenzte,
verfallende Liste. Ein Array fester Größe erfüllt beides ohne jede Allokation im heißen Pfad: Ein
Treffer sucht linear über höchstens 16 Einträge — bei dieser Größe schneller als jede Map, weil
alles in derselben Cache-Zeile liegt.

**Speicherabschätzung**: 16 Plätze × (16 Byte Kennung + 8 + 8) rund 512 Byte je Ziel. Bei 800 Mobs
etwa 400 KB — vernachlässigbar, und es wächst nicht.

**Alternativen**:

- *`HashMap` je Ziel* — bequemer, aber eine Map je Mob ist bei 800 Mobs 800 Maps, jede mit
  Verdrängungslogik, die es trotzdem bräuchte.
- *Unbegrenzte Liste mit Aufräumaufgabe* — ausdrücklich vom Blocksteckbrief ausgeschlossen.

---

## E6 · Vanilla-Invulnerabilitätsticks werden abgeschaltet

**Entscheidung**: Für jeden Träger unter der Kontrolle von B05 wird `noDamageTicks` auf null
gesetzt.

**Begründung**: Vanilla macht ein Wesen nach jedem Treffer für zehn Ticks unverwundbar. Das ist ein
zweites, verstecktes Angriffszeitfenster — und es überlagert das eigene: Ein Spieler mit hoher
`attackSpeed` dürfte nach B05s Regel viermal je Sekunde treffen, käme aber wegen der
Unverwundbarkeit nur auf zwei. Das Attribut wäre teilweise wirkungslos, und niemand käme darauf,
warum.

Nach der Abschaltung folgt die Schlagfolge allein aus `attackSpeed`, wie FR-020 bis FR-024 es
festlegen.

**Alternativen**:

- *Vanilla-Ticks als Untergrenze belassen* — deckelt die Angriffsgeschwindigkeit stillschweigend bei
  zwei Treffern je Sekunde.
- *Eigenes Zeitfenster an die Vanilla-Ticks anpassen* — hieße, das Attribut an eine Vanilla-Konstante
  zu binden, die man nicht kontrolliert.

---

## E7 · Kein Konflikt zwischen B04s Spiegelung und dem abgeschalteten Waffencooldown

**Entscheidung**: B04 spiegelt `attackSpeed` weiterhin auf das Vanilla-Attribut. B05 setzt das
eigene Zeitfenster durch und ignoriert den Vanilla-Cooldown vollständig.

**Begründung**: Das sieht zunächst wie ein Widerspruch aus — FR-024 verlangt, dass der
Vanilla-Waffencooldown wirkungslos ist, und B04s FR-032 spiegelt genau dieses Attribut. Er ist
keiner: Der Vanilla-Cooldown skaliert ausschließlich *Vanilla-Schaden*, und den setzt B05 ohnehin
auf null. Übrig bleibt die Cooldown-Anzeige im Client — und die zeigt dank derselben Zahl genau die
Schlagfolge an, die B05 tatsächlich durchsetzt.

Die Spiegelung bleibt also, weil sie hier zufällig genau das Richtige tut. Wäre sie entfernt worden,
hätte der Spieler eine Anzeige ohne Bezug zu seinem Attribut.

**Alternativen**:

- *B04s Spiegelung entfernen* — nähme dem Spieler die einzige Rückmeldung über seine
  Angriffsgeschwindigkeit.
- *Vanilla-Attribut auf einen festen hohen Wert setzen* — entfernt die Anzeige faktisch und
  entkoppelt sie vom Attribut.

---

## E8 · Mob-Ausstattung hinter einer Schnittstelle, die B10 übernimmt

**Entscheidung**: `MobStatProvider` liefert zu einer Mob-Art einen Satz Beiträge. B05 bringt eine
Umsetzung mit, die die Werte aus `combat.yml` liest; B10 ersetzt sie später durch echte
Mob-Definitionen, ohne dass die Pipeline angefasst wird.

**Begründung**: Ohne Ausstattung wirkt die Pipeline auf nichts außer Spieler — FR-018 lässt Wesen
ohne Stat-Träger unangetastet. Der Block wäre fertig, vollständig getestet und im Spiel unsichtbar;
dieselbe Fehlerklasse, für die ADR-012 nach B02 und B03 geschrieben wurde. Zusätzlich hinge der
lasttestpflichtige Nachweis (150 gegen 800) an einem Block, der drei Blöcke später kommt.

Die Schnittstelle hält die Grenze trotzdem: B05 liefert **Zahlen**, keine Mob-Definitionen. Was ein
Mob *ist* — Name, Verhalten, Fähigkeiten, Beute — bleibt vollständig B10.

**Alternativen**:

- *Nur die Schnittstelle, keine Werte* — sauberste Grenze, aber B05 wäre nicht abnehmbar.
- *Feste Werte im Code* — verstößt gegen Prinzip V und macht jede Balancing-Runde zur Codeänderung.

---

## Zusammenfassung der Auswirkungen auf die Erfolgskriterien

| Kriterium | Getragen von |
|---|---|
| SC-001 (kein Vanilla-Schaden kommt durch) | E1 |
| SC-002 (Beispielrechnungen) | reine Formel, keine Entscheidung nötig |
| SC-004 (p95 MSPT < 40 ms) | E2, E4, E5 |
| SC-005 (kein Objekt je Treffer) | E2, E5 |
| SC-006 (Schlagfolge folgt attackSpeed) | E4, E6, E7 |
| SC-007 / SC-008 (Attribution und ihre Grenze) | E5 |
| SC-010a (Bogen macht Schaden) | E3 |
| SC-010c / SC-010d (Mobs haben Werte, ohne zu lecken) | E8 |
| SC-010e (Kampfzustand ohne Aufgabe) | E4 |
| SC-013 (Leerlauf kostet nichts) | E4 — ohne Timer gibt es nichts, was im Leerlauf liefe |
