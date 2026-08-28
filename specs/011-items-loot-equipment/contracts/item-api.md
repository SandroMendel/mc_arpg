# Vertrag · Was B11 nach außen anbietet

**Spec:** [../spec.md](../spec.md) · **Plan:** [../plan.md](../plan.md)

B11 ist der letzte Block der Schicht 2, der von anderen etwas will. Nach ihm fragen nur noch B12
(Statistiken) und B13 (Anzeige). Die öffentliche Fläche ist deshalb bewusst klein.

---

## 1 · Was B11 anbietet

### `Items` — die Fassade

```java
public interface Items {

    /** Erzeugt ein Exemplar. Leer, wenn die Vorlage unbekannt ist. */
    Optional<ItemStackHandle> create(String templateKey, int amount);

    /** Die Vorlage eines Exemplars. Leer für alles, was kein B11-Item ist. */
    Optional<ItemTemplate> templateOf(ItemStackHandle stack);

    /** Was ein Charakter beim Verkauf dafür bekaeme. Leer, wenn unverkaeuflich (FR-016). */
    OptionalLong sellPriceOf(ItemStackHandle stack);
}
```

`ItemStackHandle` ist die vorhandene Hülle der Plattformschicht — `rpg-core` sieht nie einen
Bukkit-Typ (Prinzip III).

### `GearConditions` — für B12 und B13

```java
public interface GearConditions {

    /** Zustand eines Slots in [0, 100]. 100 fuer einen unbekannten Charakter. */
    double conditionOf(UUID characterId, LadderSlot slot);

    /** Der Faktor, mit dem der Stufenbeitrag multipliziert wird (FR-047). */
    double factorOf(UUID characterId, LadderSlot slot);
}
```

**`factorOf` ist genau die Naht, die B07 füllt** — siehe unten. Sie ist öffentlich, weil B13 den
Zustand anzeigen und B12 ihn auswerten will, und weil zwei Wege zu derselben Zahl einer zu viel
wären.

---

## 2 · Was B11 von anderen verlangt

### Neu in B07 — additiv, mit neutralem Vorgabewert

```java
package rpg.core.classes;

@FunctionalInterface
public interface GearConditionFactor {

    /** Ohne B11: jede Stufe traegt voll bei. */
    GearConditionFactor NONE = (characterId, slot) -> 1.0;

    /**
     * @return Faktor in [0, 1] auf den Grundbeitrag dieses Slots
     */
    double factorFor(UUID characterId, LadderSlot slot);
}
```

**Zwei Signaturen ändern sich:**

| Vorher | Nachher |
|---|---|
| `new ClassStatContributor(config, classOf, levelOf, progressOf)` | `… , progressOf, gearFactor)` |
| `EquipmentLadder.contributeTo(int reachedTier, BaseStatSink sink)` | `… , double factor)` |

**Zusicherung an B07:** mit `GearConditionFactor.NONE` verhält sich B07 bitgenau wie heute. Der
bestehende Testbestand bleibt unverändert grün; das ist die Abnahmebedingung für diesen Eingriff.

**Warum kein Modifikator.** Die Stufenwerte sind **Grundwerte**. Ein negativer
`SourceKind.EQUIPMENT`-Beitrag läge in B04s Modifikatorband um einen Grundwert, der den vollen
Stufenwert bereits enthält, und würde bei 80 % Abzug **stillschweigend abgeschnitten** — genau der
Fehler, den B07s Javadoc beschreibt und vermeidet (research.md R1).

### Neu in `rpg-platform` — herausgezogen aus B08b

```java
package rpg.platform.drop;

public interface OwnedDrops {

    /**
     * Legt einen Gegenstand ab, der nur seinem Eigentuemer gehoert.
     *
     * <p>Setzt Sichtbarkeit, Vanilla-Eigentum und die eigene Kennung gegen das Verschmelzen.
     * Raeumt NICHT auf - das macht Vanillas Verfall (FR-032).
     */
    void drop(ItemStackHandle stack, Location at, UUID ownerCharacterId);

    /** Nach Relogin oder Charakterwechsel wieder sichtbar machen (FR-030). */
    void restoreVisibility(Player player, UUID characterId);
}
```

**B08b benutzt das danach**, statt seine eigene Fassung zu halten. `CoinPile` behält, was ihm eigen
ist — den Betrag im Datencontainer und das Zusammenfassen **vor** dem Ablegen —, und gibt die
Eigentumsmechanik ab.

**Die sechs Fallen, die dieser Vertrag löst** (aus `rpg.platform.currency`s `package-info`):
Sichtbarkeit je Spieler; Sichtbarkeit überlebt keinen Relogin; `setOwner` kennt Spieler statt
Charaktere; Vanilla verschmilzt ähnliche Stapel; kein eigener Sweep; **Unsichtbarkeit ist niemals
das einzige Schloss**.

### Unverändert benutzt

| Block | Was |
|---|---|
| B03 | `CharacterInventory` — Rucksack und Enderchest je Charakter |
| B04 | `StatEngine.apply` mit `SourceKind.BUFF` für Verbrauchbares |
| B05 | `CombatDeathEvent.lootRecipient()`, `playerVictim`, `DamageOrigin`, der ankommende Schaden |
| B06 | `PartyRegistry.partyOf` und `membersOf`, die Reichweite aus `progression.yml` |
| B07 | `BoundEquipment.isBoundTo` und `.isBound`, `TierAppearance`, `LadderSlot` |
| B08b | `Currency`, `EquipmentPurchase`, `BookingReason.VENDOR_PURCHASE` und `.REPAIR` |
| B09 | die sechs Safe-Cores, die Regionen |
| B10 | die Mob-Arten, die Platzierungstechnik für die NPCs |

**Keine dieser Schnittstellen bekommt eine zweite Fassung** (FR-079). Ein Test hält das fest.

---

## 3 · Was zurückgebaut wird

| Weg | Warum |
|---|---|
| `rpg.item_instance` (Tabelle) | wird geladen, nie geschrieben; als Item-Speicher wäre sie eine zweite Wahrheit neben B03s Inventar-Blob (research.md R2) |
| `ItemInstance`, `ItemInstanceRepository`, `JdbcItemInstanceRepository` | dazugehörig |
| `SessionBundle.items` | lädt bei jedem Sitzungsstart eine Liste, die niemand liest |

**Die Migration bricht ab, wenn Zeilen vorhanden sind**, statt sie zu löschen. Ein Betreiber kann
sich das ansehen; ein klammheimlich geleerter Bestand fällt niemandem auf.

---

## 4 · Ereignisse

B11 veröffentlicht auf dem Kern-Ereignisbus, nicht über Bukkit:

| Ereignis | Wann | Für wen |
|---|---|---|
| `LootDroppedEvent` | ein Gegenstand ist gefallen und einem Charakter zugeordnet | B12 |
| `GearConditionChangedEvent` | ein Zustandswert hat eine Warnschwelle unterschritten | B13 |
| `CosmeticAppliedEvent` | eine Trimfarbe wurde angewandt | B13 |

B11 **hört** auf `CombatDeathEvent` (Beute, Todesverschleiß), `DamageDealtEvent` (Verschleiß je
Slot) und `PartyChangedEvent` (Reihum-Zeiger aufräumen) — alle auf demselben Bus, kein
`EntityDeathEvent` (research.md R4).
