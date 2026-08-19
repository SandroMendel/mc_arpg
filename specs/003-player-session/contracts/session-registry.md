# Contract: SessionRegistry (`rpg-core`)

Die Schnittstelle, über die B04–B12 an den Zustand eines verbundenen Spielers kommen. Kein
Bukkit-Bezug, vollständig ohne laufenden Server nutzbar und testbar.

## Schnittstelle (Signaturvertrag, kein Implementierungscode)

```java
public interface SessionRegistry {
    Optional<PlayerSession> find(UUID playerId);
    PlayerSession require(UUID playerId);          // wirft SessionNotReadyException
    boolean isReady(UUID playerId);
    int activeSessionCount();
}

public interface PlayerSession {
    UUID playerId();
    SessionState state();
    Optional<PlayerCharacter> activeCharacter();
    List<PlayerCharacter> availableCharacters();
}
```

## Verhaltensverträge

- **`find` liefert `Optional.empty()` für einen nicht verbundenen Spieler und für einen, dessen
  Sitzung noch lädt.** „Nicht da" und „noch nicht bereit" dürfen für den Aufrufer nicht dasselbe
  sein wie „hier sind Standardwerte" — deshalb gibt es keinen Rückgabepfad, der einen erfundenen
  Zustand liefert (FR-004).
- `require` wirft `SessionNotReadyException`, wenn die Sitzung fehlt oder nicht `READY` ist. Für
  aufrufende Blöcke ist das der Normalfall des Fehlens, nicht ein Ausnahmezustand — sie fangen ihn
  oder prüfen vorher mit `isReady`.
- **Es gibt keine Methode, die eine Sitzung erzeugt, verändert oder entfernt.** Der Lebenszyklus
  gehört B03; ein Block, der eine Sitzung anlegen könnte, könnte auch eine zweite anlegen und damit
  FR-014 aushebeln.
- `activeCharacter()` ist über die Lebensdauer einer Sitzung konstant (FR-021a). Es gibt bewusst
  kein `setActiveCharacter` — der Wechsel im Betrieb ist durch FR-021b ausgeschlossen, und das
  Fehlen der Methode ist die Durchsetzung.
- Alle Methoden sind threadsicher und dürfen aus dem Tick aufgerufen werden; sie führen keinen
  Datenbankzugriff aus.

## Nicht Teil dieses Contracts

- Der fachliche Inhalt eines Charakters (Level, Attribute, Ausrüstung) — der gehört den besitzenden
  Blöcken.
- Das Lesen von Daten nicht verbundener Spieler — siehe `contracts/offline-access.md`.
