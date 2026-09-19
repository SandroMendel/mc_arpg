# B16 Inventory v1

`inventory-b16.ps1` ist eine serverfreie Prüfroutine für T033/T033a. Sie vergleicht die gezielt in
T002 inventarisierten Java-Defaults mit den aktuellen B16-YAML-Zielpfaden unter
`rpg-content/src/main/resources`.

Der Abgleich ist bewusst serverfrei und ohne YAML-Laufzeitparser, aber semantisch enger als ein
reiner Zahlen-Scan:

- Jeder Manifest-Eintrag enthält Fundstelle, benannten Java-Wert, YAML-Datei/-Pfad, Einheit,
  Kategorie, erwarteten Scanstatus und eine Begründung.
- Das Manifest wird vor jedem Dateizugriff schemafest geprüft: unbekannte oder fehlende Kernfelder,
  doppelte IDs, unbekannte Status/Kategorien/Semantikprüfungen und ungültige Regexe werden
  abgelehnt. `MIGRATABLE` braucht immer `yamlFile` und `yamlPath`; alle `sourceFile`-/`yamlFile`-
  Angaben müssen nicht-absolute, traversal-freie Pfade unter `RepoRoot` sein.
- `scanRegions` begrenzen die Suche auf konkrete Default-/Fallback-Blöcke. Statement- und
  Block-Regexe mit benannten Gruppen prüfen die Zuordnung; der YAML-Teil liest skalare,
  eingerückte Mapping-Pfade sowie numerische Inline-Sequenzen mit indexierten Pfaden wie
  `wear.warn-at[0]`.
- Für jede Scan-Region baut ein kleiner JDK-`JavacTree`-Helper ein AST-Literalinventar mit
  Originaltoken und Quellspanne. Ein maskierter lexikalischer Cross-Check verifiziert zusätzlich,
  dass Kommentare, Strings und Char-Literale nicht als Zahlen erscheinen. Der AST erkennt Dezimal-,
  Binär-, Oktal- und Hex-Java-Literale. In begrenzten Regionen wird der gesamte Bereich, in
  unbeschränkten Regionen das vollständige Statement um jeden Kandidaten geprüft. Jedes solche
  Literal muss innerhalb eines manifestierten Probe-Treffers liegen; andernfalls entsteht explizit
  ein `GAP`.
- Nicht durch einen Probe abgedeckte Treffer in einer gezielten Region werden als `GAP` ergänzt.
  Ein fehlender oder mehrfacher Probe-Treffer, ein YAML-Pfad-/Wertfehler bei einem
  `MIGRATABLE`-Kandidaten oder ein manifestierter `GAP` macht den Report `status: GAP` und
  beendet den Lauf mit Exit-Code 1.
- `EXCEPTION` bedeutet eine bewusst begründete Schutzgrenze, Einheitendefinition, Plattformphysik
  oder technische Konstante. Sie wird nicht als migrierbarer Contentwert gezählt; ein absichtlich
  abweichender oder nur verwandter YAML-Wert wird als Abweichungsgrund im Report gehalten, aber
  nicht zu `GAP` herabgestuft.
- Der AST-Scan erhebt keinen Vollständigkeitsanspruch für beliebige Java-Semantik. Indirekte
  Formeln, Reflection, generierte Quellen und nicht in den T002-Regionen liegende Literale bleiben
  außerhalb der Abdeckung und müssen in einer späteren Entscheidung separat behandelt werden.

## Ausführen

```powershell
pwsh -NoProfile -File tools/b16-inventory/scripts/inventory-b16.ps1 `
  -RepoRoot C:\Users\Ticoo\Desktop\VuntexRPG\b15-performance-observability `
  -OutputDirectory tools/b16-inventory/reports/current-v1
```

Der aktuelle Lauf ist nach der B16-Entscheidung erfolgreich (`status: VALID`, keine GAPs). Die drei
früher offenen Kandidaten `mobs.admin-spawn-limit`, `exception.ability.behind-angle` und
`exception.ability.projectile-speed` sind jetzt als begründete `EXCEPTION`s mit den Kategorien
`PROTECTION_BOUNDARY`, `ALGORITHM_CONSTANT` und `PLATFORM_PHYSICS` erfasst. Technische
Region-/Unmapped-GAPs und bekannte `EXCEPTION`-Abweichungen erscheinen nicht als GAP. Kein
Zielverzeichnis wird überschrieben. Die drei Ausgaben sind
`b16-inventory.json`, `.csv` und `.md`.

## Serverfreie Tests

```powershell
pwsh -NoProfile -File tools/b16-inventory/tests/test-inventory-b16.ps1
```

Die Tests prüfen den positiven Fixture-Lauf, den AST-/Lexikalkonsens für Dezimal-/Hex-Literale und
Kommentare/Strings, geänderte Java-Literale als `GAP`, ungültige Manifest-Semantik und Pfade, den
aktuellen Manifest-Lauf, deterministische Ausgabe, UTF-8 ohne BOM/LF, externe ReparsePoints,
Schutz vor Überschreiben und die Negativsemantik ohne Docker, Bukkit, Paper, Netzwerk oder
Spreadsheet-Import. Der Helper wird nur in einem Temp-Verzeichnis kompiliert und danach entfernt.
