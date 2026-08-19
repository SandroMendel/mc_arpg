# Contract: Schema-Migration und Verbindungsaufbau (`rpg-persistence`)

Regelt den Start: Verbindung herstellen, Schemastand prüfen, fehlende Migrationen anwenden — oder
den Start abbrechen.

## Schnittstelle

```java
public interface SchemaMigrator {
    MigrationOutcome migrateToLatest() throws PersistenceStartupException;
}

public record MigrationOutcome(int applied, String schemaVersion, Duration took) {}

public interface ConnectionPools extends AutoCloseable {
    DataSource writePool();
    DataSource loginPool();
    @Override void close();
}
```

## Verhaltensverträge

### Migration

- `migrateToLatest` läuft **vor** der Freigabe der Repositories und **vor** dem ersten Join. Kein
  Block darf auf eine Datenbank zugreifen, deren Schemastand nicht bestätigt ist.
- Auf einer leeren Datenbank wird das vollständige Schema angelegt; auf einer bestehenden nur die
  fehlenden Schritte (FR-013). Bereits angewendete Schritte werden nie erneut ausgeführt.
- Migrationsdateien sind versioniert, in fester Reihenfolge nummeriert und **unveränderlich**,
  sobald sie ausgeliefert wurden. Eine nachträglich geänderte Datei MUSS über die Prüfsumme
  erkannt werden und den Start abbrechen — sonst laufen zwei Server mit gleicher Versionsnummer
  auf unterschiedlichen Schemata.
- Schlägt eine Migration fehl, wird `PersistenceStartupException` geworfen und der Start
  abgebrochen (FR-014). Es wird **nicht** gegen ein unvollständiges Schema weitergearbeitet.

### Verbindungsaufbau

- Ist die Datenhaltung beim Start nicht erreichbar, bricht der Start mit einer Meldung ab, die
  Wirt, Port und Datenbanknamen nennt — aber **niemals** das Passwort (FR-014, FR-022).
- Es werden zwei getrennte Pools bereitgestellt (siehe `research.md`):
  - `writePool()` für Batches, Shutdown-Flush und Statistik
  - `loginPool()` für das Laden beim Verbinden
- Kein Aufrufer außerhalb von `rpg-persistence` erhält Zugriff auf eine `DataSource`. Die
  Trennung wäre wertlos, wenn ein anderer Block sich selbst Verbindungen ziehen könnte.
- `close()` schließt beide Pools und wird beim Modul-Shutdown **nach** dem abschließenden Flush
  aufgerufen — vorher wäre der Flush unmöglich.

### Fail-Fast als Modulverhalten

B02 ist ein Modul nach dem B01-Vertrag. Ein Fehler in `start(ModuleContext)` markiert das Modul
als `FAILED` und bricht den Bootstrap ab (B01/FR-013) — genau das gewünschte Verhalten: Ein Server
ohne funktionierende Persistenz darf keine Spieler annehmen.

## Konfiguration

Wird über B01s `ConfigLoader` geladen und beim Start gegen ein Schema validiert (FR-022).
Fail-Fast mit Datei, Pfad und erwartetem Wert.

| Schlüssel | Typ | Standard | Bedeutung |
|---|---|---|---|
| `persistence.host` | Text | — (Pflicht) | Wirt der Datenbank |
| `persistence.port` | Ganzzahl | 5432 | Port |
| `persistence.database` | Text | — (Pflicht) | Datenbankname |
| `persistence.user` | Text | — (Pflicht) | Benutzer |
| `persistence.password` | Text | — (Pflicht) | Passwort; erscheint in keiner Log-Ausgabe |
| `persistence.pool.write-size` | Ganzzahl | 8 | Größe des Schreib-Pools |
| `persistence.pool.login-size` | Ganzzahl | 4 | Größe des Login-Pools |
| `persistence.autosave-seconds` | Ganzzahl | 45 | Autosave-Intervall, gültig 30–60 |
| `persistence.buffer-capacity` | Ganzzahl | 50000 | Obergrenze vorgemerkter Aggregate |
| `persistence.shutdown-flush-seconds` | Ganzzahl | 8 | Frist des Shutdown-Flush, gültig 1–8 |

Die Obergrenze von 8 bei `shutdown-flush-seconds` ist bewusst hart: Ein höherer Wert würde in
B01s 10-Sekunden-Modulbudget laufen und den Flush mitten im Schreiben zwangsterminieren lassen.
Eine Fehlkonfiguration darf hier nicht möglich sein (FR-011a).

## Nicht Teil dieses Contracts

- Der Inhalt der einzelnen Migrationsdateien über die Grundtabellen hinaus — spätere Blöcke
  ergänzen ihre eigenen Spalten über eigene Migrationen.
- Sicherungskopien und Wiederherstellung der Datenbank; das ist Betriebsaufgabe (siehe
  Assumptions in `spec.md`).
