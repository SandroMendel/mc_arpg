# Vertrag · Die öffentliche Schnittstelle von B08b

Der einzige Weg hinein. B07, B08, B11, B12 und B13 werden dagegen gebaut; ein Griff an
`DefaultCurrency`, `CoinLedger` oder die Haufen-Entities vorbei ist unzulässig (Prinzip III).

**Ab jetzt ist eine Änderung hier ADR-pflichtig** — dieselbe Regel, die `CombatPipeline`,
`StatEngine` und `AbilityRegistry` für sich festgehalten haben.

---

## `Currency` — lesen

```java
/** Der Stand dieses Charakters, oder leer, wenn er nicht geladen ist. */
OptionalLong balanceOf(UUID characterId);

/** Dasselbe primitiv für den Hot Path; 0, wenn nichts geladen ist. */
long balanceOrZero(UUID characterId);

/** Kann dieser Charakter das bezahlen? Ändert nichts. */
boolean canAfford(UUID characterId, long amount);
```

**Leer heisst „nicht geladen", nicht „null"** — dieselbe Unterscheidung, die `Progression.levelOf`
in B06 mit `OptionalInt` trifft. Ein blankes `long` hätte **abgemeldet** mit **pleite** verwechselt,
und genau diese beiden muss das Admin-Fenster auseinanderhalten: der Stand eines abgemeldeten
Charakters kommt über den Repository-Pfad, nicht über diese Methode.

`balanceOrZero` existiert aus demselben Grund wie `levelOrZero` in B06: der Wert wird in Pfaden
gebraucht, die keine Allokation je Aufruf versprechen.

`canAfford` ist **keine Reservierung**. Wer wirklich zahlen will, ruft `debit` — die Antwort auf
„kann er" ist ab dem nächsten Tick möglicherweise falsch. Die Methode existiert für die Anzeige
(B13 färbt einen Preis rot) und für ein Vorab-Nein mit besserer Meldung, nicht als erste Hälfte eines
zweiteiligen Kaufs.

## `Currency` — buchen

```java
/** Schreibt gut. Der Grund ist Pflicht (FR-005). */
BookingResult credit(UUID characterId, long amount, BookingReason reason);

/**
 * Bucht ab - prüfen und abziehen in einem Schritt (FR-006).
 *
 * <p>Es gibt bewusst keine Fassung, die nur prüft und eine Zusage zurückgibt. Zwei Fähigkeiten im
 * selben Tick würden sonst beide dasselbe Geld ausgeben.
 */
BookingResult debit(UUID characterId, long amount, BookingReason reason);
```

**Beide sind unteilbar gegenüber anderen Buchungen desselben Kontos.** Der Betrag ist immer positiv;
die Richtung steckt in der Methode, nie im Vorzeichen (FR-009).

**Rückgabe statt Ausnahme.** Zu wenig Guthaben ist ein normaler Spielausgang, kein Fehler. Ein
ungültiger Betrag dagegen ist ein Aufruffehler und kommt als `INVALID_AMOUNT` zurück, damit ein
falscher Aufruf im Gameplay-Pfad keinen Spieler in einen inkonsistenten Zustand versetzt
(Prinzip VI).

## `BookingResult`

```java
enum BookingResult {
    OK,                 // gebucht
    NOT_ENOUGH,         // zu wenig Guthaben; der Stand ist unverändert
    INVALID_AMOUNT,     // null oder negativ - Aufruffehler
    WOULD_OVERFLOW,     // die Gutschrift überschritte den darstellbaren Bereich
    NO_SUCH_CHARACTER;  // es gibt diesen Charakter nicht

    boolean isSuccess();
    MessageKey messageKey();   // null bei OK
}
```

## `CostSpec` — was etwas kostet

```java
/** Legt den undurchsichtigen cost-Block einer Ausrüstungsstufe aus (FR-047). */
static CostSpec parse(Map<String, Object> costBlock);

/** Der Coin-Anteil. Null bei leerem Block - "kostenlos" (FR-049). */
long coins();

/** Leer, wenn nichts zu zahlen ist. */
boolean isFree();
```

**B07 ruft das nicht auf.** Der Aufrufer ist der, der den Aufstieg auslöst — `ClassSourceInvariantsTest`
verbietet die Vokabel in B07s Quellen, und das bleibt so (R6).

## `CoinLedger` — der Verlauf

```java
/** Eine Seite des Verlaufs, neueste zuerst. */
List<LedgerEntry> historyOf(UUID characterId, int offset, int limit);

/** Wieviele Einträge es insgesamt gibt — für die Seitenzahl. */
long historyCount(UUID characterId);

/** Ein Zeitraum statt einer Seite, ebenfalls begrenzt. */
List<LedgerEntry> historyOf(UUID characterId, Instant from, Instant to, int limit);
```

**Ein Versatz, nicht nur ein Limit** (ADR-028). Ohne ihn ist Blättern nicht ausdrückbar, und ohne
Blättern ist ein Verlauf über Hunderte Zeilen nicht anzeigbar. **Es gibt keine unbegrenzte
Abfrage** — diese Tabelle wird bei 800 Mobs die grösste des Projekts, und eine Methode, die alles
liefert, wäre die eine Stelle, an der das zum Ausfall führt.

**Nur lesen.** Eingereiht wird ausschliesslich von `Currency` selbst, innerhalb der Sperre, die auch
den Stand ändert — sonst passten `balanceBefore` und `balanceAfter` nicht zum Eintrag daneben.

**Diese beiden Methoden lesen die Datenbank.** Sie gehören nicht in einen Spielereignis-Pfad. B12 und
B14 rufen sie aus Kommandos, und dort sieht B14 Rate-Limits vor.

## `CurrencyAdmin` — der Eingriff

```java
/** Setzt den Stand auf genau diesen Wert. */
BookingResult set(UUID characterId, long amount, String actor);

/** Erhöht um den Betrag. */
BookingResult add(UUID characterId, long amount, String actor);

/** Senkt um den Betrag. Auch der Betreiber erzeugt keinen negativen Stand (FR-003). */
BookingResult remove(UUID characterId, long amount, String actor);
```

**`actor` ist Pflicht und darf nicht leer sein.** Ein Eingriff ohne Verursacher wäre genau die
Fehlbuchung, die niemand mehr zuordnen kann.

**Jeder Aufruf schreibt zweimal**: in den Verlauf mit dem passenden `ADMIN_*`-Grund (FR-040) und in
das Audit-Log aus B02 (FR-041).

**Online und offline sind kein Unterschied für den Aufrufer** (FR-042). Ist der Charakter angemeldet,
wirkt der Eingriff im maßgeblichen Cache; sonst auf dem gespeicherten Stand. Welcher Weg gilt,
entscheidet der Block, nicht der Aufrufer.

## `MobCoinProvider` — was eine Kreatur fallen lässt

```java
/** Betrag für einen Kreaturtyp, oder leer für den konfigurierten Standardwert. */
OptionalLong coinsFor(String mobTypeKey);
```

**Leer heisst „kein eigener Eintrag", nicht „null"** (FR-023) — wörtlich dieselbe Zusage, die
`MobXpProvider` in B06 macht, und aus demselben Grund: eine Kreatur, die Mojang letzte Woche ergänzt
hat, soll nicht stillschweigend wertlos sein.

**B10 löst diesen Provider später ab**, über genau diese Schnittstelle (FR-032) — dieselbe Anordnung,
die B05 für Mob-Attribute und B06 für Erfahrung bereits benutzt.

---

## Was **nicht** in diesem Vertrag steht, und warum

| Nicht hier | Gehört zu |
|---|---|
| Ein Preiskatalog | Preise stehen bei dem, der sie verlangt (ADR-027) |
| Handel zwischen Spielern | Ausgeschlossen durch `00-vision-scope.md` |
| Der NPC-Händler | B11 |
| Die Anzeige des Stands | B13; hier steht nur `balanceOf` |
| Das Kommando | Vorläufig hier, geht an B14 (FR-046, R7) |
| Ein Zugriff auf Haufen-Entities | Interna. Wer Coins geben will, ruft `credit`. |
