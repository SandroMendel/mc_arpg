# Quickstart · B10 · Mobs & Horden-Spawning

Wie man nachprüft, dass dieser Block tut, was die Spec zusagt. Drei Abschnitte: was ohne Server geht,
was mit Konfiguration allein geht, und was nur ein echter Paper-Server beweist.

**Die Aufteilung ist keine Bequemlichkeit.** B09 hat gezeigt, wie teuer es ist, sie zu verwischen:
1940 grüne Tests und trotzdem ein Plugin, das auf dem echten Server nicht startete, weil die
Ladeordnung in keinem Test vorkam. Was unten in Abschnitt 3 steht, steht dort, weil es dort
hingehört — nicht, weil es lästig ist.

---

## 1 · Ohne Server: Tests und Messung

```
./gradlew :rpg-core:test --tests "rpg.core.mob.*"
./gradlew :rpg-platform:test --tests "rpg.platform.mob.*"
./gradlew test
```

**Erwartet:** grün, und **0 übersprungen**. MockBukkit meldet Nicht-Implementiertes als
*übersprungen* und nicht als Fehler — ein grüner Lauf, der nichts angesehen hat, ist der schlechteste
Ausgang. Die Zahl steht im Testbericht:

```
find . -path "*/build/test-results/test/TEST-*.xml" -exec grep -o 'skipped="[0-9]*"' {} \;
```

### Die Messung (FR-038, SC-007)

```
./gradlew :rpg-core:test --tests "rpg.core.mob.HordeBudgetBenchmarkTest"
```

Misst zwei Dinge und keines davon unter Volllast:

1. **Ein Spawn-Durchlauf** — Budget prüfen, Bereich wählen, Art würfeln, Chunk-Zählung nachführen.
2. **Ein Aufräum-Durchlauf** über einen Bestand fester Größe.

**Was diese Messung ausdrücklich nicht ist:** ein Lasttest. Sie misst die eigene Rechenarbeit dieses
Blocks, nicht das Setzen der Entität, nicht die Pfadfindung, nicht das Entity-Ticking. Der Nachweis
mit 800 Kreaturen bei 150 Spielern (p95 MSPT < 40 ms) bleibt verbindlich und gehört seit ADR-031 zu
**B15**. Wer diesen Test für einen Lasttest hält, hat den Block für schneller gehalten, als er
bewiesen ist.

**Gemessener Ausgangswert (2026-08-26, Entwicklungsrechner, T092):** ein Spawn- und ein
Aufräum-Durchlauf bei 130 Kreaturen zusammen in **1.300 ns** (Budget 1.000.000 ns, Marge ≈ 769×).
Über drei Läufe stabil identisch — kein Ausreißer. Dieser Wert ist der Vergleichspunkt für einen
späteren Regressionsverdacht, keine Zusage über eine bestimmte Hardware hinweg.

---

## 2 · Ohne Server: die Konfiguration wirkt

**2.1 — Eine neue Art entsteht ohne Code (SC-001).** In `mobs.yml` einen Eintrag unter `kinds`
ergänzen und unter `hordes.<zone>.areas.<area>` verweisen. Start, keine Codeänderung. Der Test
`ConfigOnlyMobTest` prüft dasselbe automatisch — und prüft zusätzlich, dass **kein Bezeichner einer
einzelnen Art im Code vorkommt** (FR-003). Vorbild: `ConfigOnlyAbilityTest` aus B08.

**2.2 — Alle 48 Arten und 6 Bosse laden (SC-002).** Vollständige `mobs.yml` laden. Erwartet: Start
ohne Beanstandung, `MobKinds.all()` hat 54 Einträge, je Zone genau ein Boss.

**2.3 — Fehler brechen den Start ab.** Nacheinander einbauen und je einen Startversuch:

| Fehler | Erwartete Meldung nennt |
|---|---|
| `base: NOT_A_MOB` | Datei, Schlüssel, unbekannter Entity-Typ |
| `area:` verweist ins Leere | den Bereichsschlüssel und die Zone |
| `kind:` verweist ins Leere | den Artschlüssel |
| zwei Bosse in einer Zone | die Zone und beide Arten |
| `per-chunk` > `per-zone` | beide Zahlen |
| `display-name-key` fehlt in `messages.yml` | den Schlüssel |

Jede Meldung muss **Datei, Schlüssel und Grund** nennen. Eine, die nur „invalid configuration" sagt,
ist ein Fehler dieses Blocks und kein bestandener Prüfschritt.

**2.4 — Der Schlüsselwechsel bricht nichts (FR-009, R5).** Eine Kreatur ohne Vermerk fragt weiterhin
mit ihrem Vanilla-Typnamen. Prüfen: die vorhandenen `mobs.by-type`-Einträge aus `combat.yml` gelten
für einen ungekennzeichneten Zombie unverändert.

---

## 3 · Auf einem echten Paper-Server

Das hier beweist, was kein Test beweisen kann. **Jar deployen und die neue `mobs.yml` mitnehmen** —
Bukkit überschreibt vorhandene Konfigurationen nicht, das Jar allein deployt YAML-Änderungen nicht
mit.

### 3.1 Die Unterdrückung (US2b, SC-010)

1. Server starten, `/time set night`.
2. Zehn Minuten auf der Oberfläche stehen, weit weg von jedem Spawn-Bereich. **Erwartet:** nichts
   entsteht.
3. In eine unbeleuchtete Höhle graben, zehn Minuten warten. **Erwartet:** nichts entsteht.
4. Ein Spawn-Ei benutzen. **Erwartet:** die Kreatur erscheint, mit Standardwerten. Unterdrückt ist
   das natürliche Spawnen, nicht das Setzen.
5. Ein Monsterspawner-Block. **Erwartet:** nichts.
6. `/summon`. **Erwartet:** die Kreatur erscheint.
7. Ein Dorf mit Eisengolem-Bau, ein Nether-Portal, ein Raid — je nachdem, was erreichbar ist.
   **Erwartet:** nichts. Das sind die Wege, die keine Spielregel abdeckt und die nur der Riegel
   auffängt (research.md R1). **Genau hier lohnt das Nachsehen am meisten.**
8. Eine neue Welt laden (`/mv create` oder ein zweiter Weltordner). **Erwartet:** auch dort nichts —
   die Regeln werden bei jedem `WorldLoadEvent` gesetzt, nicht nur beim Start.

### 3.2 Die Horde (US2, US4)

9. In *Greenfields* stellen. **Erwartet:** Kreaturen entstehen in den beiden Bereichen und nirgends
   sonst.
10. Zählen. **Erwartet:** die Zahl bleibt unter `per-zone`.
11. Vier weitere Spieler dazu (oder mit mehreren Clients). **Erwartet:** die Zahl steigt, die Werte
    einer einzelnen Kreatur bleiben gleich.
12. Zwanzig Spieler auf einmal. **Erwartet:** das Budget hält. **Das ist der Prüfschritt, für den
    dieser Block gebaut wurde** (FR-013, SC-003).
13. Eine Kreatur töten. **Erwartet:** binnen der Nachschubfrist steht wieder eine — kontinuierlich,
    ohne Wellenpause.

### 3.3 Aufräumen (US3)

14. Alle Spieler aus *Greenfields* heraus. Warten. **Erwartet:** nach der Aufräumfrist steht keine
    Kreatur dieser Region mehr; `/minecraft:kill` findet nichts.
15. Weit weglaufen, ohne die Zone zu verlassen. **Erwartet:** was hinter der Aufräumreichweite
    liegt, verschwindet.
16. Eine Kreatur schlagen und dann weglaufen. **Erwartet:** sie bleibt, solange der Kampfzustand
    läuft (FR-022) — eine Kreatur, die einem unter den Händen verschwindet, ist schlimmer als eine,
    die einen Tick zu lange lebt.
17. Aufgeräumte Kreaturen zählen. **Erwartet:** kein XP, keine Coins, kein Todesprotokoll (FR-021).
18. Server stoppen und neu starten. **Erwartet:** keine herrenlosen Kreaturen aus dem letzten Lauf
    (FR-023).

### 3.4 Werte, Erfahrung, Coins (US1)

19. Zwei Arten auf derselben `base` töten. **Erwartet:** unterschiedliche Coins und unterschiedliche
    Erfahrung. **Das ist der Prüfschritt für den Schlüsselwechsel** — vor B10 wären beide derselbe
    Schlüssel gewesen (research.md R5).
20. Namensschild ansehen. **Erwartet:** Name der Art und Level, nicht der Vanilla-Name.
21. Einen ungekennzeichneten Zombie töten. **Erwartet:** die Standardwerte, kein Fehler im Log.
22. Schaden nehmen und austeilen. **Erwartet:** die Zahlen der Art, über B04/B05 — keine zweite
    Rechnung.

### 3.5 Der Boss (US5)

23. Boss suchen. **Erwartet:** genau einer je Region, an seinem Bereich.
24. Töten. **Erwartet:** kein zweiter vor Ablauf des Timers.
25. Timer abwarten. **Erwartet:** er steht wieder an seinem Platz, nicht dort, wo er gefallen ist.
26. Boss aus seinem Bereich locken und dort töten. **Erwartet:** derselbe Platz beim Respawn.
27. Zone verlassen, warten, zurückkommen. **Erwartet:** der Boss wurde aufgeräumt, sein Timer lief
    unberührt weiter (FR-034).

### 3.6 AI und Klon (US6, US7)

28. Aus großer Entfernung nähern. **Erwartet:** die Kreatur reagiert erst innerhalb ihrer
    `follow-range`, nicht über die halbe Region.
29. Aus der Reichweite herauslaufen. **Erwartet:** sie verliert das Ziel.
30. Klon des Rogue in eine Gruppe setzen. **Erwartet:** die Kreaturen schwenken auf ihn um.
31. Klon auslaufen lassen. **Erwartet:** sie wählen wieder den Spieler.

### 3.7 Last im Kleinen

32. `/tps` und `/mspt` bei voller Zone mit einem Spieler notieren.
33. Dasselbe mit fünf Spielern.
34. Beide Zahlen ins Protokoll dieses Blocks. **Kein Bestehenskriterium** — der Lasttest gehört zu
    B15. Aber eine Zahl, die man später vergleichen kann, ist mehr wert als ein Eindruck.

---

## Was ein Fehlschlag hier bedeutet

Ein fehlgeschlagener Schritt aus Abschnitt 3 ist **kein Anlass, den Test anzupassen**. Er ist der
Grund, warum Abschnitt 3 existiert: er zeigt, was 1900 grüne Tests nicht sehen. Die Blockhaltung des
Warriors und die Ladeordnung des Plugins sind beide genau so gefunden worden — beide erst auf dem
echten Server, beide nachdem alles grün war.
