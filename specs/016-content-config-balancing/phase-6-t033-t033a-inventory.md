# B16 Phase 6 — T033/T033a Java-/YAML-Inventar v1

## Ergebnis

Die serverfreie Prüfroutine liegt unter `tools/b16-inventory/`. Sie erzeugt ein versioniertes
Inventar mit JSON als Primärformat sowie CSV und Markdown als lesbare Ableitungen. Der Manifeststand
ist `b16-inventory-v1` und basiert auf den gezielten T001-/T002-Kandidaten.

Der aktuelle Lauf ist **gültig**: `status=VALID`, Exit-Code `0`. Die drei zuvor fachlich offenen
Kandidaten sind entschieden und werden nicht als offene GAPs geführt; nicht zuordenbare Zahlen
werden weiterhin niemals stillschweigend als migriert markiert.

## Abgleichsgrenze

Die Prüfung umfasst die drei vorgegebenen Java-Wurzeln und konzentriert sich auf:

- `StatConfig.defaults()` mit Attribut-Basen sowie explizit als Schutzgrenzen markierten Minima,
  Maxima und Modifier-Bändern;
- `CombatConfig.defaults()`, `FallDamageConfig.defaults()` und die Mob-Typ-/Defaultwerte;
- den Plugin-Mob-Fallback, der auf `StatConfig.defaults()` delegiert und keine zweite Zahl enthält;
- `MobConfig.DEFAULT_ADMIN_SPAWN_LIMIT`, den optionalen Zonen-Warning-Default und den Inventory-
  Warning-Default;
- die in T002 ausdrücklich genannten Schutz-, Plattform-, Einheiten- und Algorithmuskonstanten.

Die Semantikprüfung ist serverfrei und nicht nur ein globaler Zahlenvergleich: begrenzte Methoden-
oder Statement-Regionen werden mit benannten Regex-Gruppen geprüft, anschließend wird der konkrete
skalare YAML-Pfad gelesen. Sekunden/Millisekunden werden beim Inventory-Warning explizit mit der
Einheitenumrechnung geprüft. Ergänzend baut ein JDK-`JavacTree`-Helper ein AST-Inventar der
numerischen Literal-Spans; der maskierte Lexikalscan dient als Cross-Check für Kommentare, Strings
und Char-Literale. Er erkennt Dezimal-, Binär-, Oktal- und Hex-Literale und verlangt, dass jedes
Literal innerhalb einer begrenzten Scan-Region beziehungsweise des vollständigen Statements eines
unbeschränkten Kandidaten von einem manifestierten Probe-Treffer abgedeckt ist; ein unbedecktes
Literal erzeugt einen expliziten `GAP`. Der YAML-Teil unterstützt dabei auch indexierte numerische
Inline-Sequenzen wie `wear.warn-at[0]`. Das ist kein Vollständigkeitsbeweis für beliebige
Java-Semantik;
Reflection, generierte Quellen, indirekte Formeln außerhalb der Regionen und nicht in T002
aufgelistete Literale bleiben offen dokumentierte Grenzen.

Vor dem Dateizugriff wird das Manifest schemafest validiert: IDs sind eindeutig, Kernfelder und
Regexe sind vorhanden, Status/Kategorien/Semantikprüfungen stammen aus den erlaubten Mengen,
`MIGRATABLE`-Kandidaten besitzen immer `yamlFile` und `yamlPath`, und alle `sourceFile`-/`yamlFile`-
Pfade sind relativ, traversal-frei und auf `RepoRoot` begrenzt. ReparsePoints/Junctions/Symlinks,
die aus dem Repository hinauszeigen, werden ebenfalls abgelehnt. Ungültige Manifest-Semantik oder
Pfade enden fail-fast mit Exit-Code `2`.

## Statusmodell

| Status | Bedeutung |
|---|---|
| `MIGRATABLE` | Genau eine Java-Fundstelle, genau ein YAML-Zielpfad, Kategorie und Einheitenabgleich sind nachgewiesen. |
| `EXCEPTION` | Bewusst begründete Schutzgrenze, Plattformphysik, Einheitendefinition oder technische Konstante; nicht als freier Contentwert behandelt. |
| `GAP` | Keine eindeutige Zuordnung, fehlender/mehrfacher Probe-Treffer, YAML-Abweichung eines `MIGRATABLE`-Kandidaten oder offener Fachentscheid. Der Lauf endet mit Exit-Code 1. |

## Abgeschlossene Fachentscheidungen im aktuellen Bestand

- `mobs.admin-spawn-limit = 20`: `mobs.yml` ist Owner; maximal 20 gleichzeitig registrierte
  `Origin.ADMIN`-Mobs serverweit, Java-`20` nur Kompatibilitäts-Fallback.
- `BehindTargetCheck.DEFAULT_ANGLE = 90.0` Grad: globale Core-Regel für die hintere Hemisphäre;
  genau seitlich zählt nicht; kein YAML-Owner vorerst.
- `ProjectileEffect.DEFAULT_SPEED = 1.6` blocks/tick: technische Core-/Plattform-Physik-Konstante;
  kein YAML-Owner vorerst und kein `PROJECTILE`-Eintrag in `abilities.yml`.

Diese drei Werte stehen im Report mit Fundstelle, Wert, Einheit und abgeschlossener Semantik;
der aktuelle Manifestlauf enthält keine GAPs. Region-Endmarker werden im
Textabschnitt ab dem Startmarker gesucht; dadurch zählt keine frühere schließende Klammer als
Bereichsende. Unbeschränkte Regionen werden pro Kandidat auf das vollständige Statement erweitert,
damit auch ein früheres Literal im selben Statement nicht still verschwindet. Bekannte
`EXCEPTION`-Kandidaten behalten abweichende oder nur verwandte YAML-Werte als Begründung im Report,
werden aber nicht zu GAP herabgestuft. Die zweite aktuelle
`MobProviders.stats(..., StatConfig.defaults())`-Verdrahtung ist als eigene Delegate-EXCEPTION
abgedeckt.

## Serverfreie Verifikation

Ausgeführt wird:

```powershell
pwsh -NoProfile -File tools/b16-inventory/tests/test-inventory-b16.ps1
```

Die Tests prüfen Syntax, positiven Fixture-Lauf, geänderte Dezimal-/Hex-Literale als GAP,
unbedeckte numerische Literale in vollständigen Statements, ungültige Manifest-Semantik/Pfade mit
Exit-Code `2`, externe ReparsePoints, den aktuellen `VALID`-Report ohne offene GAPs,
byte-identische Wiederholungen, UTF-8 ohne BOM, LF-only, AST-Temp-Cleanup, Schutz vor
Überschreiben und den erwarteten Exit-Code. Docker, Paper, Bukkit, Netzwerk und Spreadsheet-Import
werden nicht benötigt.

`tasks.md` wurde für diesen Nachweis auf den abgeschlossenen T033/T033a-Stand aktualisiert. Es gab keine Änderungen an Java-
Produktionscode, keinen Commit und keinen Push.
