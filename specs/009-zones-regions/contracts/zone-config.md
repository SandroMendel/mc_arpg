# Vertrag · `zones.yml`

Zonen sind **vollständig konfigurationsdefiniert** (FR-001, Prinzip V). Eine siebte Region entsteht
durch Konfiguration allein — kein Code, kein Neubau (SC-003).

Die Datei wird beim Start gegen ein Schema geprüft. **Jeder Fehler verhindert den Start** mit einer
Meldung, die die verantwortliche Stelle benennt (FR-013). Zur Laufzeit ist sie neu ladbar; ein
abgelehntes Dokument lässt die vorige Fassung für alle Module aktiv — das erbt B09 von
`AbstractConfigLoader`.

**Preise stehen hier, nicht in `currency.yml`** (ADR-027, FR-050c). Es gibt keinen zentralen
Preiskatalog.

---

## Aufbau

```yaml
# B09 - zones and regions. Every balancing number lives here, never in code (Principle V).
#
# A Zone is NEVER a World (ADR-006). Moving a zone into its own world is the `world:` line
# below and nothing else.
#
# Prices for waypoint travel live HERE, with whoever demands them - there is no central
# price catalogue (ADR-027).

# WARNING: these coordinates are PLACEHOLDERS on a test world. The hand-built continent
# does not exist yet. While `provisional: true` the server logs a warning on every start.
provisional: true

# The point used when a character dies outside every zone (FR-034).
fallback-point:
  world: world
  x: 0.5
  y: 64.0
  z: 0.5

zones:
  greenfields:
    world: world
    start-region: true          # exactly one zone carries this (FR-037a)
    level-band: { min: 1, max: 10 }
    pvp: false                  # default; the safe core refuses it regardless (FR-028)
    area:
      - { min-x: -500, min-z: -500, max-x: 500, max-z: 500 }   # y omitted = full height
    safe-core:
      area:
        - { min-x: -60, min-z: -60, max-x: 60, max-z: 60 }
      respawn-point: { x: 0.5, y: 65.0, z: 0.5 }
    crystal:
      key: greenfields-crystal
      price: 25
      trigger-area:
        - { min-x: -2, min-y: 64, min-z: -2, max-x: 2, max-y: 67, max-z: 2 }
    spawn-areas:
      - { key: greenfields-east, area: [ { min-x: 120, min-z: -80, max-x: 300, max-z: 80 } ] }
      - { key: greenfields-west, area: [ { min-x: -300, min-z: -80, max-x: -120, max-z: 80 } ] }

  dustlands:
    world: world
    level-band: { min: 11, max: 20 }
    pvp: false
    area: [ ... ]
    safe-core: { ... }
    crystal: { key: dustlands-crystal, price: 25, trigger-area: [ ... ] }
    spawn-areas: [ ... ]

  # safari-plains 21-30, terracotta-canyons 31-40, darkforest 41-50, pale-wilds 51-60
```

---

## Felder

### Auf oberster Ebene

| Schlüssel | Pflicht | Bedeutung |
|---|---|---|
| `provisional` | nein, Vorgabe `false` | solange `true`, warnt der Server bei **jedem Start**, dass die Koordinaten Platzhalter sind (FR-065a, FR-065b). Das Entfernen ändert **nichts ausser der Warnung** (FR-065c) |
| `fallback-point` | ja | Ausweichpunkt für Tod ausserhalb aller Zonen (FR-034) |
| `zones` | ja, mindestens eine | die Zonen, Schlüssel ist die technische Kennung |

### Je Zone

| Schlüssel | Pflicht | Bedeutung |
|---|---|---|
| *(Kartenschlüssel)* | ja | die **technische Kennung** (FR-003). Verweise laufen nur darüber (FR-003b). Der sichtbare Name steht als `zone.<key>.name` in `messages.yml` (FR-003a) — **hier steht kein Spielertext** |
| `world` | ja | Weltname. **Dies ist die eine Zeile, die eine Zone in eine eigene Welt verschiebt** (SC-004) |
| `start-region` | nein, Vorgabe `false` | genau eine Zone im ganzen Dokument trägt `true` (FR-037a) |
| `level-band` | ja | `min` und `max`, beide **einschliessend** (FR-003). Unter `min` warnt der Eintritt (FR-021); über `max` warnt nichts (FR-023) |
| `pvp` | nein, Vorgabe `false` | Spieler gegen Spieler in der Gefahrenzone (FR-027). Im Schutzkern **immer** aus (FR-028) |
| `area` | ja, mindestens ein Quader | die Geometrie |
| `safe-core` | nein | Schutzkern mit `area` und `respawn-point`. Ohne ihn kein Respawn und kein Kristall |
| `crystal` | nein | Wegpunkt-Kristall, siehe unten. Nur zulässig, wenn ein Schutzkern existiert (FR-051c) |
| `spawn-areas` | nein | benannte Bereiche für B10 (FR-053) |

### Quader

| Schlüssel | Pflicht | Bedeutung |
|---|---|---|
| `min-x`, `min-z`, `max-x`, `max-z` | ja | waagerechte Ecken, in beliebiger Reihenfolge angebbar (sie werden normalisiert) |
| `min-y`, `max-y` | nein | **fehlen sie, gilt der Quader über die ganze Welthöhe** (FR-004). Sind sie da, erfasst er einen Spieler darüber oder darunter nicht |

### Kristall

| Schlüssel | Pflicht | Bedeutung |
|---|---|---|
| `key` | ja | eindeutig im ganzen Dokument. Die Freischaltungen der Spieler verweisen darauf |
| `price` | ja | Coins je Reise (FR-050c). `0` ist erlaubt und macht das Reisen kostenlos |
| `trigger-area` | ja | wo ein Rechtsklick zählt. Muss innerhalb der eigenen Zone liegen (FR-051d) |

**Kein `target`.** Gereist wird an den `respawn-point` des Schutzkerns der Zone, zu der der Kristall
gehört (FR-045a). Eine Region hat **einen** Ankunftsort, der Spielbeginn, Tod und Reise bedient.

### Spawn-Bereich

| Schlüssel | Pflicht | Bedeutung |
|---|---|---|
| `key` | ja | eindeutig innerhalb der Zone |
| `area` | ja | Geometrie. Muss in der Zone und **ausserhalb** ihres Schutzkerns liegen (FR-055) |

**Nichts weiter.** Keine Art, keine Rolle, keine Mobliste, keine Zahl (FR-053a).

---

## Was den Start verhindert

Vollständige Liste in [data-model.md §5](../data-model.md). Kurz: überlappende Zonen, doppelte
Kennungen, ein Schutzkern ausserhalb seiner Zone, ein Spawn-Bereich ausserhalb seiner Zone oder im
Kern, ein Kristall ohne Schutzkern in seiner Zone, ein fehlender Message-Schlüssel, keine oder mehr
als eine Startregion.

**Kein Vorrangregelwerk zur Laufzeit.** Was nicht vorkommen darf, wird beim Start abgewiesen.

---

## Message-Schlüssel in `messages.yml`

Alle Spielertexte dieses Blocks (FR-025, FR-035, FR-051e, FR-061). Ein fehlender Schlüssel
verhindert den Start — das prüft der Bootstrap bereits für das ganze Plugin.

```yaml
zone:
  greenfields:
    name: 'The Greenfields'
  dustlands:
    name: 'The Dustlands'
  safari-plains:
    name: 'The Safari Plains'
  terracotta-canyons:
    name: 'The Terracotta Canyons'
  darkforest:
    name: 'The Darkforest'
  pale-wilds:
    name: 'The Pale Wilds'

  # Entering a zone below its level band (FR-021). Never blocks (FR-022).
  too-dangerous: 'This area is too dangerous for you. Recommended level: {min}-{max}.'
  # Death (FR-035)
  died: 'You died.'
  # First login after a combat logout (ADR-030, FR-041)
  died-logout: 'You logged out in combat and died.'

  waypoint:
    menu-title: 'Waypoints'
    unlocked: 'Waypoint discovered: {zone}.'
    locked: 'You have not discovered this waypoint yet.'
    not-enough-coins: 'You need {missing} more coins to travel there.'
    in-combat: 'You cannot travel while in combat.'
    gone: 'That waypoint no longer exists.'
    travelled: 'You travelled to {zone} for {price} coins.'
    refunded: 'The journey failed. Your {price} coins were returned.'
```

Der Zonenname wird **überall** aus diesen Schlüsseln aufgelöst — auch im Auswahlfenster (FR-048b).
Es gibt keinen zweiten Ort für Zonennamen.
