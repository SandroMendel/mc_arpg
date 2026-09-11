# Phase 1 · Datenmodell — B12 · Statistiken & Leaderboards

**Spec**: [spec.md](./spec.md) · **Recherche**: [research.md](./research.md)

Drei Ebenen, und sie werden bewusst auseinandergehalten: **was gespeichert wird** (eine einzige
vorhandene Tabelle plus zwei neue), **was daraus abgeleitet wird** (vier Sichten und ein
Speicher-Cache) und **was nur zur Laufzeit lebt** (Zeitabschnitte und Aktivitätsstempel).

---

## 1 · Gespeichert

### 1.1 `rpg.player_statistic_daily` — vorhanden, unverändert

| Spalte | Typ | Bedeutung |
|---|---|---|
| `player_id` | UUID | Konto, nicht Charakter (Fund 3, FR-005) |
| `metric` | TEXT | Metrikschlüssel, bei dimensionierten Metriken einschließlich Dimension |
| `day` | DATE | Kalendertag in **UTC** (FR-026) |
| `value` | BIGINT | Summe oder Maximum — je nach Metrikart |

**Das Schema ändert sich nicht.** Neu ist allein ein zweiter Schreibweg auf derselben Zeile:

- Metrikart **SUM**: `ON CONFLICT ... DO UPDATE SET value = value + excluded.value` *(vorhanden)*
- Metrikart **MAX**: `ON CONFLICT ... DO UPDATE SET value = GREATEST(value, excluded.value)` *(neu, ADR-040)*

Beide schreiben **ohne vorher zu lesen**. Welcher gilt, entscheidet die Metrikart aus dem
Verzeichnis, nicht die Aufrufstelle.

**Nie gelöscht, nie verdichtet** (ADR-044). Mechanisch gesichert durch `NoDirectDatabaseAccessTest`.

### 1.2 Metrikschlüssel

| Metrik | Schlüssel | Art | Sichtbar |
|---|---|---|---|
| Mob-Kills je Art | `mob_kills.<kindKey>` | SUM | öffentlich |
| Tode je Verursacher | `deaths.<kindKey>` / `deaths.environment` / `deaths.void` / `deaths.player` | SUM | **privat** |
| Aktive Zeit je Zone | `playtime_active.<zoneKey>` / `playtime_active.wilderness` | SUM | **privat** |
| Onlinezeit | `playtime_online` | SUM | **privat** |
| Höchster Schaden | `damage_max` | MAX | öffentlich |

**Gesamtwerte sind Aggregationen, keine eigenen Schlüssel** (FR-013): „aktive Gesamtzeit" ist die
Summe über `playtime_active.*`, „Tode gesamt" die Summe über `deaths.*`.

**Bei den Kills sind es zwei disjunkte Aggregationen** (FR-009a):

| Rangliste | Aggregation |
|---|---|
| **Mob-Kills** | Summe über `mob_kills.*` **ohne** die Arten mit `boss: true` |
| **Bosskills** | Summe über `mob_kills.*` **nur** über die Arten mit `boss: true` |

Beide zusammen ergeben alle Kills, und keine Kreatur zählt in beiden. Die Trennung sitzt in der
Aggregation, nicht im gespeicherten Schlüssel — ein Bosskill liegt weiterhin als
`mob_kills.<kindKey>` in der Tabelle. Verliert eine Art ihr Boss-Kennzeichen, wandern ihre Kills
rückwirkend von der einen Rangliste in die andere; das ist dieselbe Zusage, die B11 für Balancing
gibt, und derselbe Preis.

**Die Sichtbarkeit hängt am Detail, nicht an der Summe.** `deaths.*` ist privat, die daraus
gebildete Gesamtzahl öffentlich. Ebenso `playtime_active.*`: die Aufteilung privat, die Summe
öffentlich und rankbar.

### 1.3 `rpg.season_result` — neu (V12_2)

Der eingefrorene Endstand einer abgeschlossenen Saison.

| Spalte | Typ | Bedeutung |
|---|---|---|
| `season_key` | TEXT | Teil des Primärschlüssels |
| `player_id` | UUID | Teil des Primärschlüssels |
| `rank` | INTEGER | Platz, Gleichstände tragen denselben |
| `score` | BIGINT | die erreichte Punktzahl |
| `weights` | JSONB | **die Gewichtung, mit der gerechnet wurde** (FR-050, SC-021) |
| `data_version` | INTEGER | Formatversion |
| `frozen_at` | TIMESTAMPTZ | wann der Abschluss lief |

`PRIMARY KEY (season_key, player_id)`. Nach dem Einfrieren **nur noch gelesen**. Die Gewichtung
liegt hier und nicht in der Konfiguration, weil eine spätere Balancing-Änderung eine vergebene
Platzierung sonst rückwirkend umsortierte.

### 1.4 `rpg.season_reward_claim` — neu (V12_3)

| Spalte | Typ | Bedeutung |
|---|---|---|
| `season_key` | TEXT | Teil des Primärschlüssels |
| `player_id` | UUID | Teil des Primärschlüssels |
| `rank` | INTEGER | der belohnte Platz |
| `reward` | JSONB | Coins und/oder Vorlagen-IDs mit Stückzahl |
| `claimed_at` | TIMESTAMPTZ NULL | **NULL heißt offen** |
| `claimed_by_character` | UUID NULL | wer eingelöst hat |

`PRIMARY KEY (season_key, player_id)`. **Kein Verfall** (FR-053a) — es gibt keine Ablaufspalte, weil
es keine Frist gibt.

**Der Riegel gegen doppelte Einlösung** ist ein bedingtes Update:
`UPDATE ... SET claimed_at = now(), claimed_by_character = ? WHERE season_key = ? AND player_id = ?
AND claimed_at IS NULL` — und **erst wenn genau eine Zeile betroffen war**, wird gutgeschrieben.
Ein Absturz zwischen beidem kostet höchstens eine Belohnung und verdoppelt keine (R2, FR-053).

### 1.5 Was ausdrücklich **nicht** gespeichert wird

- **Level, XP, Coins** — gelesen aus `character_progress` und `character_balance` (ADR-041). Keine
  Spiegelung, keine zweite Wahrheit.
- **Gesamtzahlen** je Familie — Aggregation (FR-013).
- **Bosskills** — Auswertung über `MobKind.boss` (FR-009).
- **Der laufende Zwischenstand der Gesamtwertung** — gerechnet, nicht abgelegt. Nur der
  abgeschlossene Endstand wird eingefroren.
- **Der Anzeigename** — gehört zum Cache-Eintrag, nicht zur Zeile.

---

## 2 · Abgeleitet

### 2.1 Vier Materialized Views (V12_1, research.md R1)

```
mv_stat_alltime (metric, player_id, value)
mv_stat_season  (season_key, metric, player_id, value)
mv_stat_week    (iso_year, iso_week, metric, player_id, value)
mv_stat_day     (day, metric, player_id, value)
```

Die Metrik ist eine **Spalte**, kein Sichtname — deshalb wächst die Zahl der Sichten nicht mit der
Zahl der Metriken. `value` ist je Metrikart `sum(value)` oder `max(value)`; die Sicht entscheidet
das anhand desselben Verzeichnisses, das auch den Schreibweg bestimmt.

Jede Sicht trägt einen eindeutigen Index über ihren vollständigen Schlüssel, damit
`REFRESH MATERIALIZED VIEW CONCURRENTLY` möglich ist und die Auffrischung niemanden sperrt.

### 2.2 `LeaderboardCache` — im Speicher

Gefüllt aus **vier** Abfragen je Auffrischung, unabhängig von der Zahl der Ranglisten. Hält je
Rangliste die ersten N Einträge plus die vollständige Rangzuordnung, damit FR-033 („die eigene
Platzierung, auch außerhalb der ersten N") ohne Nachfrage beantwortbar ist.

| Feld | Bedeutung |
|---|---|
| `refreshedAt` | für die Altersangabe in jeder Ansicht (FR-032) |
| `entries` | Rang, Konto, Anzeigename, Wert |
| `rankOf` | Konto → Rang, für den Betrachter |

**Anonymisierte Konten sind hier bereits nicht mehr enthalten** (FR-039). Der Filter sitzt beim
Füllen, nicht beim Anzeigen: eine Ansicht, die filtern müsste, ist eine Ansicht, die es vergessen
kann.

### 2.3 Die Gesamtwertung

`score(player) = Σ (Gewicht(metrik) × Wert(metrik, Saisonzeitraum))` über die konfigurierten
Metriken. Nur **öffentliche Zähler- und Maximumsmetriken**; Zustandswerte und private Werte sind
ausgeschlossen (FR-050c, ADR-046).

Die Aufschlüsselung im Fenster zeigt je beitragender Metrik **Wert, Gewicht und Punkte** (FR-050f) —
womit SC-020 („nachrechenbar") prüfbar wird.

---

## 3 · Nur zur Laufzeit

| Objekt | Lebensdauer | Warum nicht gespeichert |
|---|---|---|
| **Aktivitätszeitstempel** | Sitzung | Ein Zeitstempel, aus dem die Untätigkeit folgt. Nach einem Neustart ist niemand untätig, weil niemand verbunden ist. |
| **Offener Zeitabschnitt** | bis Zonenwechsel, Tagesgrenze, Sweep oder Sitzungsende | Wird beim Schließen zu einem Delta auf `playtime_active.<zone>`. Ein Absturz kostet höchstens ein Autosave-Intervall — dieselbe Zusage wie Prinzip IV. |
| **Zwischenstand der Gesamtwertung** | bis zur nächsten Auffrischung | Gerechnet aus dem Cache. |

---

## 4 · Zustandsübergänge

### 4.1 Eine Saison

```
konfiguriert ──(Startdatum erreicht)──▶ laufend ──(Enddatum überschritten)──▶ abgeschlossen
                                           │                                      │
                                    Zwischenstand                          Endstand eingefroren
                                    sichtbar (FR-050e)                     + Ansprüche angelegt
```

Der Übergang nach *abgeschlossen* wird **beim Start nachgeholt**, wenn der Server über das Enddatum
hinweg aus war (FR-058) — und er läuft **genau einmal**: das Vorhandensein von Zeilen in
`season_result` für diesen `season_key` ist der Beleg.

### 4.2 Ein Belohnungsanspruch

```
angelegt (claimed_at IS NULL) ──(bedingtes Update greift)──▶ markiert ──▶ gutgeschrieben
        ▲                                                                      │
        └──────── Inventar voll: Ablehnung, Anspruch bleibt offen ◀────────────┘
```

Die Reihenfolge ist **markieren, dann gutschreiben** (R2). Ist kein Platz im Inventar, wird
**vorher** abgelehnt und gar nicht erst markiert (FR-055).

---

## 5 · Validierungsregeln (Startprüfung)

| Regel | Anforderung | Wirkung bei Verstoß |
|---|---|---|
| Saisons lückenlos und überschneidungsfrei | FR-049 | Start bricht ab |
| Gewichtung nennt mindestens eine bekannte Metrik | FR-050d | Start bricht ab |
| Beteiligungsschwelle echt zwischen 0 und 1 | FR-007d | Start bricht ab |
| Jeder benötigte Message-Schlüssel vorhanden | FR-047 | Start bricht ab |
| Hologrammstelle nicht ladbar | FR-064 | Warnung, Anzeige entfällt, Start läuft |
