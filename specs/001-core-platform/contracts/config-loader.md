# Contract: ConfigLoader (`rpg-core` Interface, `rpg-platform` YAML-Implementierung)

Lädt und validiert Konfigurationsquellen gegen ein deklariertes Schema; unterstützt
Fail-Fast beim Start und Hot-Reload im laufenden Betrieb.

## Schnittstelle (Signaturvertrag, kein Implementierungscode)

```java
public interface ConfigLoader {
    <T> T loadAndValidate(Path source, ConfigSchema<T> schema) throws ConfigValidationException;
    <T> void reloadAll() throws ConfigValidationException; // global, siehe Clarification 2026-08-19
}

public class ConfigValidationException extends Exception {
    // enthält: Quelldatei, betroffener Pfad im Dokument, erwarteter Wert/Typ
}
```

## Verhaltensverträge

- `loadAndValidate` MUSS bei einem Schema-Verstoß eine `ConfigValidationException` werfen,
  die Datei, Pfad und erwarteten Wert benennt (FR-002) — keine stille Verwendung eines
  Default-Werts bei einem Pflichtfeld.
- `reloadAll()` lädt die Konfiguration **aller** registrierten Module gleichzeitig neu
  (globaler Reload, Clarification 2026-08-19 — kein selektiver Pro-Modul-Reload).
- Schlägt `reloadAll()` für irgendeine Konfigurationsquelle fehl, MUSS die zuvor gültige
  Konfiguration alle Module aktiv bleiben (FR-004) — kein Teil-Reload mit gemischtem alten/
  neuen Zustand.
- Ein Reload MIT aktiven Spielersitzungen darf keine Sitzung trennen oder Daten verlieren
  (FR-003) — das ist eine Verhaltensanforderung an alle Module, die auf `reloadAll()`
  reagieren, nicht nur an den `ConfigLoader` selbst.

## Nicht Teil dieses Contracts

- Das konkrete YAML-Schema-Format (Feldnamen, Struktur je Block) — das ist Teil der
  jeweiligen Block-Specs (z. B. B16 Content-Konfiguration), nicht von B01.
