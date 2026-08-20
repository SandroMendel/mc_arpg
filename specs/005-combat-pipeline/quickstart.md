# Validierungsleitfaden: B05 · Kampf- & Schadens-Pipeline

**Feature**: `specs/005-combat-pipeline` | **Datum**: 2026-08-20

Neun Abschnitte. 1 bis 7 laufen auf jedem Entwicklungsrechner; Abschnitt 8 braucht einen echten
Paper-Server, Abschnitt 9 ist der **pflichtige Lasttest** — Prinzip VII nennt B05 ausdrücklich als
Block, der ohne ihn nicht als fertig gilt.

**Wichtig — Lehre aus B03 und B04**: MockBukkit meldet Nicht-Implementiertes als *übersprungenen*
Test, nicht als Fehler. Nach jedem Lauf gilt deshalb Abschnitt 0.

---

## 0 · Übersprungene Tests ausschließen (nach jedem Lauf)

```powershell
Get-ChildItem -Recurse -Filter "TEST-*.xml" -Path */build/test-results |
  Select-String -Pattern 'skipped="[1-9]' |
  Select-Object -ExpandProperty Path -Unique
```

**Erwartet**: keine Ausgabe.

---

## 1 · Die Formel (SC-002, SC-012)

```powershell
./gradlew :rpg-core:test --tests "rpg.core.combat.DamageFormulaTest"
```

**Erwartet**: alle Beispielrechnungen aus [data-model.md](./data-model.md) §3 exakt, ohne Toleranz.

| Angreifer | Faktor | Verteidigung | Erwartet |
|---|---|---|---|
| 50 physisch | 1,0 | 100 | 25,0 |
| 100 physisch | 1,0 | 300 | 25,0 |
| 100 physisch | 1,0 | 0 | 100,0 |
| 40 magisch | 1,8 | 100 | 36,0 |
| Fall aus 10 Blöcken | — | beliebig | 28,0 (unverändert durch Verteidigung) |

Zusätzlich: derselbe Sturz kostet bei 100 maximalem Leben einen deutlich größeren Anteil als bei
2000, bei gleichem absolutem Betrag (SC-012a).

---

## 2 · Vanilla-Quellen (SC-001)

```powershell
./gradlew :rpg-platform:test --tests "rpg.platform.combat.VanillaDamageMappingTest"
```

**Erwartet**:

1. **Jede** der 33 `DamageCause`-Konstanten hat eine Zuordnung — der Test iteriert über den
   Aufzählungstyp, nicht über eine Liste, damit eine neue Konstante ihn zum Fehlschlagen bringt.
2. Die Zuordnung entspricht [contracts/damage-sources.md](./contracts/damage-sources.md).
3. Eine unbekannte Ursache wird neutralisiert und protokolliert — nicht durchgelassen.
4. Kein Vanilla-Schadensereignis verlässt den Listener mit einem Schaden über null.
5. Ein Wesen ohne Stat-Träger bleibt unangetastet (FR-018).

---

## 3 · Pipeline und Erlaubnis (SC-010, SC-011)

```powershell
./gradlew :rpg-core:test --tests "rpg.core.combat.CombatPipelineTest" --tests "rpg.core.combat.DamagePermissionTest"
```

**Erwartet**:

| Fall | Erwartung |
|---|---|
| Spieler gegen Spieler | abgewiesen, kein Schaden, keine Animation, kein Beitrag |
| Mob gegen Mob | abgewiesen |
| Spieler gegen Mob, Mob gegen Spieler | erlaubt |
| Selbstschaden | erlaubt, aber kein Beitrag |
| Abbruch in jeder der sechs Stufen | Vorgang endet folgenlos |
| Werfender Eingriffspunkt | protokolliert, Pipeline läuft weiter, andere Kämpfe unberührt |
| Zwei tödliche Treffer im selben Tick | genau ein Todesereignis |

---

## 4 · Angriffszeitfenster und Kampfzustand (SC-006, SC-010e)

```powershell
./gradlew :rpg-core:test --tests "rpg.core.combat.AttackWindowTest" --tests "rpg.core.combat.CombatStateTest"
```

Mit einer **gesteuerten Uhr**, nicht mit Wartezeiten — sonst ist der Test langsam und wackelig.

**Erwartet**:

1. Bei 4 Angriffen je Sekunde zählen von zehn Schlägen in einer Sekunde genau vier.
2. Ein verworfener Schlag erzeugt nichts.
3. Eine geänderte Angriffsgeschwindigkeit gilt sofort beim nächsten Schlag.
4. Ein Träger gilt unmittelbar nach einem Treffer als im Kampf und nach Ablauf wieder nicht.
5. **Null geplante Aufgaben** über den gesamten Testlauf — die Attrappe des Schedulers zählt `0`.

Punkt 5 ist der eigentliche Beweis für Prinzip II.

---

## 5 · Attribution (SC-007, SC-008)

```powershell
./gradlew :rpg-core:test --tests "rpg.core.combat.AttributionWindowTest"
```

**Erwartet**:

| Fall | Erwartung |
|---|---|
| drei Angreifer mit 60/30/10 | genau diese Anteile, größter Beitragender ist der erste |
| 100 Angreifer bei 16 Plätzen | Fenster bleibt bei 16, kleinster Beitrag weicht |
| Beitrag älter als die Verfallszeit | nicht mehr beteiligt |
| Angreifer kehrt nach langer Pause zurück | wieder beteiligt, mit dem Beitrag ab der Rückkehr |
| Ziel stirbt ohne Spielerbeitrag | Aufteilung leer, niemand bekommt etwas |
| Ziel entfernt | Fenster freigegeben |

---

## 6 · Rückmeldung und Zusammenfassung (SC-009)

```powershell
./gradlew :rpg-core:test --tests "rpg.core.combat.DamageAggregatorTest"
./gradlew :rpg-platform:test --tests "rpg.platform.combat.PaperDamageFeedbackTest"
```

**Erwartet**:

1. Zwanzig Treffer im Fenster ergeben ein Ereignis mit `hitCount == 20` und korrekter Summe.
2. Ein Treffer ohne Wirkung erzeugt kein Ereignis.
3. Trefferanimation und Rückstoß werden je Treffer ausgelöst — die Zusammenfassung betrifft nur die
   Zahl.
4. B05 erzeugt keine Anzeigeobjekte in der Welt (Negativtest über die Quellen, wie
   `NoDamageInterceptionTest` in B04).

---

## 7 · Mob-Ausstattung (SC-010c, SC-010d)

```powershell
./gradlew :rpg-platform:test --tests "rpg.platform.combat.MobEquipmentListenerTest"
```

**Erwartet**:

1. Ein feindliches Wesen hat unmittelbar nach dem Erscheinen einen Stat-Träger mit Werten aus der
   Konfiguration.
2. Ein friedliches Wesen bekommt keinen.
3. Eine Art ohne eigenen Eintrag bekommt die Standardwerte.
4. Nach 800 erschienenen und wieder entfernten Wesen existiert **kein** Träger mehr — der Lecktest,
   der bei 800 gleichzeitigen Mobs zählt.

---

## 8 · Gesamtbild auf einem echten Server

```powershell
./gradlew :rpg-plugin:test --tests "rpg.plugin.FullBootstrapTest"
./gradlew build
```

**Erwartet aus dem Bootstrap-Test** (ADR-012):

- `CombatModule` ist angemeldet und startet nach `stats`.
- `CombatPipeline` ist als Dienst abrufbar.
- `combat.yml` wird beim ersten Start angelegt.
- Die Kampf-Listener sind angemeldet: je genau einer auf `EntityDamageEvent`, `EntityDeathEvent`,
  `ProjectileLaunchEvent` und `CreatureSpawnEvent`.
- Gameregel `keep_inventory` steht auf `true`.

Anschließend auf einem Paper-Server:

| Schritt | Erwartung |
|---|---|
| Einen Zombie schlagen | er nimmt Schaden, zeigt Trefferanimation und Rückstoß |
| Schnell klicken | die Schlagfolge folgt der Angriffsgeschwindigkeit, nicht der Klickrate |
| Zombie töten | er stirbt, es fallen **keine** Vanilla-Beute und **keine** Erfahrungskugeln |
| Aus 10 Blöcken springen | Fallschaden nach Formel, Herzleiste stimmt |
| In Lava steigen | Schaden nach Formel |
| Trank der Verletzung trinken | keine Wirkung |
| Bogen benutzen | der Schuss macht Schaden |
| Anderen Spieler schlagen | nichts passiert |
| Sterben | Todesbildschirm, Inventar vollständig erhalten, nach Respawn volles Leben und Mana |
| 30 Sekunden nicht kämpfen | Kampfzustand endet |

---

## 9 · Lasttest — Abnahmebedingung (SC-004, SC-005, SC-013)

**Pflicht**, nicht optional: Prinzip VII nennt B05 als lasttestpflichtigen Block.

| Messung | Grenze |
|---|---|
| 150 Spieler gegen 800 Mobs im Dauerkampf, p95 MSPT | **< 40 ms** |
| 10 000 Treffer in Folge, Allokation je Treffer | kein vermeidbares Objekt |
| Leerlauf mit verbundenen Spielern ohne Kampf | keine messbare Tickzeit, null geplante Aufgaben |
| Speicher der Beitragsfenster bei 800 Mobs | unter 1 MB, nicht wachsend |

Vor dem Lasttest gilt Abschnitt 7 Punkt 4: Wenn Träger lecken, misst der Lasttest den Fehler statt
der Pipeline.

---

## Abnahmeprüfliste

| Kriterium | Abschnitt |
|---|---|
| SC-001 kein Vanilla-Schaden kommt durch | 2 |
| SC-002 Beispielrechnungen | 1 |
| SC-003 Herzleiste korrekt | 8 |
| SC-004 p95 MSPT < 40 ms | **9** |
| SC-005 keine Allokation je Treffer | **9** |
| SC-006 Schlagfolge folgt attackSpeed | 4 |
| SC-007 / SC-008 Attribution und Grenze | 5 |
| SC-009 Zusammenfassung | 6 |
| SC-010 kein PvP, kein Mob gegen Mob | 3 |
| SC-010a Bogen macht Schaden | 8 |
| SC-010b kein Itemverlust beim Tod | 8 |
| SC-010c / SC-010d Mobs haben Werte, ohne zu lecken | 7 |
| SC-010e Kampfzustand ohne Aufgabe | 4 |
| SC-010f keine Vanilla-Belohnungen | 8 |
| SC-011 genau ein Todesereignis | 3 |
| SC-012 / SC-012a Formel serverfrei geprüft | 1 |
| SC-013 Leerlauf kostet nichts | **9** |
