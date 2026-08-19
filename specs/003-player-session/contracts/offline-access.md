# Contract: Lesen ohne Sitzung (`rpg-core`)

Der Weg, auf dem B12 (Bestenlisten) und B14 (Verwaltung) an Spielerdaten kommen, ohne eine Sitzung
zu erzeugen.

## Schnittstelle

```java
public interface OfflinePlayerReader {
    CompletableFuture<Optional<PlayerSnapshot>> read(UUID playerId);
    CompletableFuture<List<PlayerCharacter>> charactersOf(UUID playerId);
}

public record PlayerSnapshot(UUID playerId, List<PlayerCharacter> characters, boolean online) {}
```

## Verhaltensverträge

- **Ein Zugriff erzeugt niemals eine Sitzung** (FR-022). Das ist der eigentliche Zweck dieser
  Schnittstelle: Ohne sie müsste jedes Werkzeug entweder eine Sitzung anlegen — und damit den
  Zustand verfälschen, den es nur lesen wollte — oder an der Datenhaltung vorbeigreifen und die
  Kapselung brechen.
- **Ein Zugriff schreibt nichts** (FR-023). Es gibt keine schreibende Methode.
- Ist der Spieler **verbunden**, liefert die Schnittstelle den aktuellen Sitzungszustand, nicht den
  gespeicherten Stand (FR-024). Ein Bestenlisten-Eintrag, der den Fortschritt der letzten
  45 Sekunden nicht kennt, wäre für den betroffenen Spieler sichtbar falsch.
- `online` im Ergebnis sagt dem Aufrufer, aus welcher Quelle die Daten stammen — damit ein
  Werkzeug den Unterschied darstellen kann, statt ihn zu verbergen.
- Alle Methoden laufen außerhalb des Ticks.

## Nicht Teil dieses Contracts

- Schreibender Zugriff auf Daten nicht verbundener Spieler. Ein Verwaltungswerkzeug, das etwas
  ändern muss, tut das über den zuständigen Fachblock — nicht an der Sitzungsverwaltung vorbei.
