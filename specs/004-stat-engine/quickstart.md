# Validierungsleitfaden: B04 · Attribut- & Stat-Engine

**Feature**: `specs/004-stat-engine` | **Datum**: 2026-08-20

Acht Abschnitte. Die Abschnitte 1 bis 6 laufen auf jedem Entwicklungsrechner; Abschnitt 7 braucht
Docker für Testcontainers; Abschnitt 8 braucht einen echten Paper-Server.

**Wichtig — Lehre aus B03**: MockBukkit meldet Nicht-Implementiertes als *übersprungenen* Test, nicht
als Fehler. Die Gradle-Konsole sieht dann grün aus, während Tests still ausfallen. Nach jedem Lauf
gilt deshalb Abschnitt 0.

---

## 0 · Übersprungene Tests ausschließen (nach jedem Lauf)

```powershell
Get-ChildItem -Recurse -Filter "TEST-*.xml" -Path */build/test-results |
  Select-String -Pattern 'skipped="[1-9]' |
  Select-Object -ExpandProperty Path -Unique
```

**Erwartet**: keine Ausgabe. Jede Zeile ist eine Testklasse mit stillschweigend übersprungenen
Tests und muss geprüft werden, bevor der Block als fertig gilt.

---

## 1 · Formeln und Grenzen (SC-005, SC-006, SC-007)

```powershell
./gradlew :rpg-core:test --tests "rpg.core.stats.*"
```

**Erwartet**: alle grün, keine übersprungen. Abgedeckt sein müssen:

| Prüfung | Erwartung |
|---|---|
| Basis 100, `+50` flat, `+20 %` | exakt `180.0` |
| zwei Beiträge von je `+50 %` | Faktor `2.0`, **nicht** `2.25` |
| Prozentsumme unter `−100 %` | Endwert gleich `min`, nie negativ |
| `abilityCooldown` mit Beiträgen über 40 % | genau `0.40` |
| `DamageMitigation.reductionFactor(300)` | exakt `0.75` |
| `DamageMitigation.reductionFactor(0)` | exakt `0.0` |
| `DamageMitigation.afterDefense(x, -1000)` | endlich, positiv, kein Vorzeichenwechsel |
| `attackSpeed` mit `+200 %` bei Band `0.50` | Basis × `1.5` |
| jeder Wert `0` als Basis, Beitrag und Grenze | keine Ausnahme, kein `NaN` |

---

## 2 · Rundlauf ohne Drift (SC-004)

```powershell
./gradlew :rpg-core:test --tests "rpg.core.stats.ModifierRoundTripTest"
```

**Erwartet**: Nach 1000 Durchläufen von Anlegen und Ablegen derselben Quelle ist jeder der acht
Werte **bitgleich** dem Ausgangswert. Der Test vergleicht mit `isEqualTo`, nicht mit einer Toleranz
— eine Toleranz würde genau den Fehler durchlassen, den der Test finden soll.

Zusätzlich: dieselbe Quellenmenge in unterschiedlicher Einfügereihenfolge ergibt bitgleiche
Ergebnisse (FR-016).

---

## 3 · Bündelung und Leerlauf (SC-001, SC-002, SC-003)

```powershell
./gradlew :rpg-core:test --tests "rpg.core.stats.RecalculationBudgetTest"
```

**Erwartet**:

1. Sechs Ausrüstungsquellen in einem Tick gesetzt → der Zähler der Neuberechnungen steht auf `1`.
2. 200 Träger mit je 20 Quellen, 1200 Ticks ohne Änderung → Zähler unverändert `200` (die
   Erstberechnung), also **null** weitere Neuberechnungen.
3. 100 Träger ändern im selben Tick → Gesamtdauer unter 5 ms.
4. Der Leerlauf plant keine einzige Aufgabe: die Attrappe des Schedulers zählt `0` Aufrufe.

Punkt 4 ist der eigentliche Beweis für Prinzip II — ein Zeitmesswert allein würde einen billigen,
aber vorhandenen Durchlauf nicht auffallen lassen.

---

## 4 · Ressourcen und Klemmregeln (Teil von SC-011)

```powershell
./gradlew :rpg-core:test --tests "rpg.core.stats.ResourcePoolTest"
```

**Erwartet**:

| Ausgang | Vorgang | Ergebnis |
|---|---|---|
| 500/1000 | Maximum auf 1200 | 500/1200, kein Auffüllen |
| 900/1000 | Maximum auf 800 | 800/800, Ereignis mit Ursache `CLAMPED_BY_MAX`, kein Tod |
| 10/1000 | Verbrauch von 50 | 0/1000, kein negativer Stand |
| neu angelegt | — | Stand gleich Maximum, Ursache `INITIALISED` |
| 0 Mana | Verbrauch von 5 | kein Ereignis (Änderung um null) |

---

## 5 · Konfiguration und Fail-Fast (SC-009)

```powershell
./gradlew :rpg-core:test --tests "rpg.core.stats.StatConfigValidationTest"
```

**Erwartet**: Jede der neun Prüfregeln aus [contracts/stat-config.md](./contracts/stat-config.md)
hat einen eigenen Fall, und jede Fehlermeldung nennt Attribut **und** Feld. Ein Test, der nur prüft,
dass eine Ausnahme fliegt, reicht nicht — die Meldung ist das Produkt.

Zusätzlich manuell:

```powershell
# stats.yml im Serververzeichnis: health.max auf 5 setzen (unter health.base von 100)
# Server starten
```

**Erwartet**: Start bricht ab, das Protokoll nennt `health`, `base` und `max` mit ihren Werten.
Kein Spieler kann sich verbinden.

---

## 6 · Vanilla-Spiegelung (SC-008)

```powershell
./gradlew :rpg-platform:test --tests "rpg.platform.stats.*"
```

**Erwartet**:

| Fall | Erwartung |
|---|---|
| 500 von 1000 Leben | angezeigte Gesundheit `10.0`, `GENERIC_MAX_HEALTH` `20.0` |
| maximales Leben steigt auf 2000 | `GENERIC_MAX_HEALTH` unverändert `20.0` |
| 0,4 von 1000 Leben | angezeigte Gesundheit `0.5`, nicht `0.0` |
| 0 von 1000 Leben | angezeigte Gesundheit `0.0` |
| Änderung an `movementSpeed` | `GENERIC_MOVEMENT_SPEED` im selben Vorgang gesetzt |
| natürliche Regeneration | Gameregel steht auf `false`, Sättigung wird festgehalten |
| Fall-, Feuer-, Ertrinkungsschaden | von B04 **nicht** angefasst (FR-030b) |

Der letzte Punkt wird als Negativtest geführt: die Plattformschicht registriert keinen Handler auf
`EntityDamageEvent`. Ohne diesen Test wandert der Vorgriff auf B05 unbemerkt hinein — dieselbe
Fehlerklasse, gegen die B03 seinen `NoCompetingSessionListenersTest` hat.

---

## 7 · Persistenz gegen echtes PostgreSQL (SC-011, SC-012)

Voraussetzung: laufender Docker-Daemon.

```powershell
./gradlew :rpg-persistence:test --tests "rpg.persistence.stats.*"
```

**Erwartet**:

1. `V4_1__character_stats.sql` läuft sauber durch; die Tabelle trägt Primärschlüssel,
   Fremdschlüssel mit `ON DELETE CASCADE` und beide `CHECK`-Bedingungen.
2. Der Versionsraum bleibt geordnet: `1 < 3.1 < 3.2 < 4.1`.
3. Rundlauf: Stand setzen → Flush → neu laden → identischer Stand.
4. Löschen eines Charakters entfernt seinen Ressourcensatz ohne Zutun von B04.
5. Ein Charakter ohne Ressourcenzeile lädt als neuer Träger mit vollem Stand, ohne Fehler.
6. **Zugriffszählung**: eine simulierte Sitzung mit 500 Ressourcenänderungen erzeugt genau
   **einen** Schreibvorgang je Flush-Zyklus, nicht 500 (SC-012).

---

## 8 · Gesamtbild auf einem echten Server

```powershell
./gradlew :rpg-plugin:test --tests "rpg.plugin.FullBootstrapTest"
./gradlew build
```

**Erwartet aus dem Bootstrap-Test** (ADR-012 — ein Modul, das nicht verdrahtet ist, ist nicht
fertig, egal wie grün seine eigenen Tests sind):

- `StatsModule` ist im Modulregister angemeldet und startet nach `SessionModule`.
- `StatEngine` ist als Dienst abrufbar.
- `stats.yml` wird beim ersten Start in das Datenverzeichnis geschrieben.
- Der Regenerationswächter ist als Listener angemeldet.
- `AggregateType.CHARACTER_STATS` ist in `FlushCycle` registriert, und zwar **nach** `CHARACTER`.

Anschließend auf einem Paper-Server:

| Schritt | Erwartung |
|---|---|
| Verbinden | Herzleiste zeigt 20 Punkte bei vollem Leben |
| `/rpg stats` (B14, sofern vorhanden) oder Protokollausgabe | acht Werte entsprechen `stats.yml` |
| Schaden nehmen bis ~halbes Leben | Herzleiste zeigt rund 10 Punkte |
| 30 Sekunden stillstehen | Herzleiste steigt **nicht** — Vanilla-Regeneration ist aus |
| Ausloggen und wieder verbinden | derselbe Gesundheitsstand, höchstens ein Autosave-Intervall alt |
| `timings`/`spark` im Leerlauf | kein B04-Eintrag im Tick-Profil |

---

## Abnahmeprüfliste

| Kriterium | Abschnitt |
|---|---|
| SC-001 eine Neuberechnung je Wechsel | 3 |
| SC-002 Leerlauf ohne Tick-Kosten | 3 |
| SC-003 100 Neuberechnungen unter 5 ms | 3 |
| SC-004 kein Drift nach 1000 Rundläufen | 2 |
| SC-005 alle Formeln serverfrei geprüft | 1 |
| SC-006 Verteidigung 300 → 75 % | 1 |
| SC-007 Abklingzeit nie über 40 % | 1 |
| SC-008 Herzleiste korrekt, nie null bei lebendem Spieler | 6 |
| SC-009 Balancing ohne Codeänderung, Fail-Fast | 5 |
| SC-010 kein Speicherrest nach 200 Sitzungen | 3, 8 |
| SC-011 Stand überlebt Ab- und Anmelden | 7, 8 |
| SC-012 kein Datenbankzugriff je Spielereignis | 7 |
