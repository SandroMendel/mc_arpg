# Vertrag · Das Schema von `statistics.yml`

**Spec**: [../spec.md](../spec.md) · **Recherche**: [../research.md](../research.md) (R9)

Prinzip V: alle Zahlen dieses Blocks liegen hier, keine im Code. Beim Start wird gegen dieses
Schema geprüft; ein Fehler führt zu **Fail-Fast mit Datei, Schlüssel und Grund** — nach dem Muster
von `MobConfigSchema` und `ItemConfigSchema`.

---

## Aufbau

```yaml
statistics:

  # --- Erfassung ------------------------------------------------------------
  capture:
    # Anteil am Schaden, ab dem ein Kill zählt (FR-007, FR-007d).
    # Echt zwischen 0 und 1 — 0 gäbe jedem Treffer einen Kill, >1 griffe nie.
    kill-credit-share: 0.05

    # Nach dieser Dauer ohne Aktivität steht die aktive Uhr (FR-014b).
    idle-after-seconds: 300

  # --- Ranglisten -----------------------------------------------------------
  leaderboards:
    refresh-interval-seconds: 300   # FR-032
    places: 10               # FR-033

  # --- Saisons --------------------------------------------------------------
  # Lückenlos und überschneidungsfrei, sonst bricht der Start ab (FR-049).
  seasons:
    - key: 2026-q3
      from: 2026-07-01
      to:   2026-09-30
    - key: 2026-q4
      from: 2026-10-01
      to:   2026-12-31

  # --- Gesamtwertung (ADR-046) ---------------------------------------------
  # Punkte je Einheit. Mindestens eine bekannte Metrik, sonst bricht der Start
  # ab (FR-050d). Nur öffentliche Zähler- und Maximumsmetriken sind zulässig —
  # Zustandswerte und private Werte werden abgewiesen (FR-050c).
  score:
    weights:
      # mob_kills und boss_kills sind disjunkt (FR-009a): mob_kills zaehlt die
      # Arten OHNE Boss-Kennzeichen, boss_kills nur die mit. Ein Bosskill kommt
      # damit genau einmal in die Punktzahl, nicht zweimal.
      mob_kills:       1
      boss_kills:      50
      playtime_active: 2      # je angefangener Stunde
      # damage_max ist ein Maximum, kein Zaehler: sein Beitrag waechst nicht mit
      # der Spielzeit, sondern springt einmal. Vorgabe 0 - wer ihn gewichtet,
      # sollte wissen, dass ein einziger Treffer die Wertung tragen kann.
      damage_max:      0
      deaths:          0

  # --- Belohnungen ----------------------------------------------------------
  # Je Platz der Gesamtwertung, nicht je Rangliste (FR-051).
  rewards:
    1:
      coins: 5000
      items:
        - template: cosmetic_trim_gold
          amount: 1
    2:
      coins: 2500
    3:
      coins: 1000

  # --- Hologramm ------------------------------------------------------------
  # Fehlt der Abschnitt, entfällt die Anzeige. Ist die Stelle nicht ladbar,
  # entfällt sie mit einer Warnung — der Start läuft trotzdem (FR-064).
  hologram:
    world: world
    x: 0.5
    y: 65.0
    z: 0.5
    board: season_score        # welche Rangliste gezeigt wird
    period: season
    places: 10
```

---

## Prüfungen beim Start

| Schlüssel | Regel | Bei Verstoß |
|---|---|---|
| `capture.kill-credit-share` | `0 < x < 1` | Abbruch (FR-007d) |
| `capture.idle-after-seconds` | positive Dauer | Abbruch |
| `leaderboards.refresh-interval-seconds` | positive Dauer | Abbruch |
| `leaderboards.places` | `> 0` | Abbruch |
| `seasons` | lückenlos, überschneidungsfrei, `from <= to`, Schlüssel eindeutig | Abbruch (FR-049) |
| `score.weights` | mindestens ein Eintrag, jede **Aggregation** bekannt, jede öffentlich, Gewicht `>= 0` | Abbruch (FR-050c, FR-050d) |
| `rewards` | Platz `> 0`, Vorlagen-ID bei B11 bekannt | Abbruch |
| `hologram` | Abschnitt optional; wenn vorhanden, muss `board` eine bekannte Rangliste sein | Abbruch bei unbekannter Rangliste, Warnung bei nicht ladbarer Stelle (FR-064) |
| Message-Schlüssel | jeder benötigte vorhanden | Abbruch (FR-047) |

**Die Gewichtung adressiert Aggregationen, keine Metriken.** `mob_kills`, `boss_kills`,
`playtime_active`, `damage_max` und `deaths` sind Ranglisten, nicht Speicherschlüssel: `boss_kills`
hat nach FR-009 bewusst keinen eigenen Zähler, und `deaths` wie `playtime_active` haben keine
Gesamtzeile, sondern entstehen durch Summieren über ihre Familie (FR-013). Die Sichtbarkeit wird
deshalb **an der Aggregation** geprüft und nicht an der zugrundeliegenden Metrik: privat ist die
*Aufschlüsselung* (`deaths.<verursacher>`, `playtime_active.<zone>`), öffentlich ist die *Summe*
(FR-037, FR-038a, ADR-043). Zustandswerte sind ausgeschlossen, weil es für sie gar keine
Aggregation gibt — das ist der Riegel aus ADR-046, und er braucht keine eigene Regel in dieser
Tabelle.

**Dauern stehen als Zahl mit Einheit im Schlüssel** (`idle-after-seconds: 300`), nicht als
Kurzform (`idle-after: 5m`). Der erste Entwurf hatte die Kurzform; jede andere Konfiguration
dieses Projekts benutzt die erste Form (`cooldown-ms`, `cleanup-after-seconds`,
`respawn-minutes`). Ein zweiter Dauernbegriff für eine einzige Datei wäre eine Ausnahme, die jeder
Leser einmal nachschlagen muss.

**Drei dieser Prüfungen sind Regeln, keine Zahlenwahl** — sie sichern eine Zusage, die sonst durch
eine spätere Balancing-Änderung still verloren ginge: die Saisonlückenlosigkeit, die
Mindestgewichtung und die Schwelle. Dasselbe Vorgehen, mit dem B11 die Ordnung „ein Tod wiegt
schwerer als viele Treffer" zur Startprüfung gemacht hat (FR-044 dort).

---

## Was **nicht** in dieser Datei steht

- **Welche Ranglisten es gibt.** Sie entstehen aus dem Metrikverzeichnis mal den vier Zeiträumen
  (FR-032a). Eine kuratierte Liste wäre eine Pflegeaufgabe, die niemand pflegt (ADR-044).
- **Metrikschlüssel als Text.** Das Verzeichnis steht im Code, weil zu jeder Metrik ein
  Erfassungspunkt gehört; die Konfiguration verweist nur darauf.
- **Mob-Arten und Zonen.** Die Dimensionen kommen aus `mobs.yml` (B10) und `zones.yml` (B09). Eine
  zweite Liste hier wäre eine zweite Wahrheit.
- **Spielertexte.** Alle über Message-Schlüssel (Prinzip V, FR-047).

---

## Nachladen

`statistics.yml` wird wie `mobs.yml` und `items.yml` beim Start geladen und ist zur Laufzeit
nachladbar. **Zwei Dinge ändern sich beim Nachladen nicht:**

- der **Endstand einer abgeschlossenen Saison** samt seiner Gewichtung (FR-050, SC-021);
- bereits **angelegte Ansprüche**.

Alles andere — Schwelle, Intervall, Plätze, Gewichte der laufenden Saison, Hologramm — greift ab
der nächsten Auffrischung.
