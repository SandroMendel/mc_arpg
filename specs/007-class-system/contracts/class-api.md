# Vertrag · Öffentliche Schnittstelle von B07

Was andere Blöcke von B07 benutzen dürfen. Alles nicht Genannte ist Internes und nach Prinzip III für
andere Blöcke gesperrt.

---

## Für B04 (Stat-Engine) — `ClassStatContributor`

```
BaseStatContributor mit id() == "class"
```

Beigesteuert wird in **einem** Durchgang: Klassenbasis + Levelwachstum der Klasse + Werte der
erreichten Rüstungsstufe + Werte der erreichten Waffenstufe.

**Basiswerte, keine Modifikatoren.** Der Grund steht in research.md R1: das Modifikatorband wird um
den effektiven Basiswert gelegt, und bei 70 % Leiteranteil wäre ein am Level-1-Wert hängendes Band auf
der Endstufe grob falsch. `SourceKind.CLASS` bleibt von B07 **unbenutzt** und für spätere,
tatsächlich modifikatorförmige Klasseneffekte frei.

**Mobs**: Ein Halter ohne Charakter liefert keinen Beitrag und **keine Ausnahme** — B04 rechnet Mobs
durch denselben Pfad. Genau das Muster von `LevelStatContributor` aus B06.

**B04 wird nicht geändert.** B07 registriert sich an der vorhandenen Schnittstelle.

---

## Für B08 (Fähigkeiten) — `ClassRegistry`

```
Optional<CharacterClassDefinition>  definition(CharacterClass id)
List<AbilityBinding>                abilitiesOf(CharacterClass id)
List<AbilityBinding>                unlockedFor(UUID characterId)
```

`unlockedFor` leitet aus dem Level ab (FR-043) und fragt dafür B06. Es gibt **keinen** gespeicherten
Freischaltzustand.

B07 liefert `abilityId` als Zeichenkette und löst sie nicht auf. Was eine Fähigkeit **tut**, welchen
Hotbar-Slot sie belegt und was sie kostet, entscheidet B08 (FR-044).

---

## Für B11 (Items) — `BoundEquipment`

```
boolean  isBound(ItemStack item, UUID characterId)
boolean  isBound(ItemStack item)                    // gebunden an irgendeinen Charakter
```

Die Frage aus FR-025. **Jede** Bewegungs-, Verkaufs- und Wegwerfroute in B11 fragt sie und weist ein
gebundenes Item ab (ADR-018). Antwortet ohne Allokation und ohne Datenbankzugriff (SC-010) — die
Auskunft liegt im Pfad jedes Inventarklicks.

Ein Item, das für einen **anderen** Charakter gebunden ist, gilt für diesen Charakter als nicht
gebunden und wird beim Laden ersetzt, nicht anerkannt. Das macht Kopien wertlos (Prinzip VI).

```
TierAdvanceResult  advanceArmor(UUID characterId)
TierAdvanceResult  advanceWeapon(UUID characterId)
Map<String,Object> costOf(CharacterClass id, LadderSlot slot, int targetTier)
```

`costOf` gibt den Block **unausgelegt** zurück (FR-021). B07 kennt keine Coins. Wer bezahlt und
womit, entscheiden B11 und B16.

`advance*` prüft Levelanforderung und Endstufe und lehnt mit benannter Ursache ab
(`BELOW_REQUIRED_LEVEL`, `ALREADY_AT_TOP`, `UNKNOWN_CHARACTER`). Es prüft **keine** Kosten — der
Aufrufer hat sie bereits eingezogen.

---

## Für B13 (HUD) — Ereignisse und Anzeige

```
ClassChangedEvent   (characterId, CharacterClass)
TierAdvancedEvent   (characterId, LadderSlot, fromTier, toTier)
```

Beide werden über den Ereignisbus aus B01 veröffentlicht, nicht als Bukkit-Ereignis — `rpg-core` hat
keine Bukkit-Abhängigkeit.

```
String  displayNameKey(CharacterClass id)
```

Ein Message-Schlüssel, **kein** Text. „Berserker" steht in der Nachrichtendatei (Prinzip V).

---

## Für B03 (Sitzung) — `ClassSelection`

```
boolean                needsSelection(PlayerSession session)
Set<CharacterClass>    available(PlayerSession session)
ClassSelectionResult   choose(PlayerSession session, CharacterClass id)
```

`available` liefert nur Klassen, für die das Konto noch keinen Charakter hat (FR-035) — die Auswahl
zeigt es **vorher** an, statt beim Anlegen zu scheitern.

`choose` ist der einzige Weg, einen Charakter anzulegen. Bei gleichzeitigen Beitritten desselben
Kontos gewinnt genau einer; der andere bekommt `CLASS_ALREADY_TAKEN` (FR-036). Die Entscheidung fällt
am Schlüssel `(player_id, character_class)` aus B03, nicht in Anwendungscode.

**B03 wird nicht geändert.** `PlayerSession.activeCharacter()` ist bereits `Optional`; ein Spieler
ohne Charakter ist ein Zustand, den B03 schon kennt.

---

## Ausdrücklich nicht Teil der Schnittstelle

| Was | Warum nicht |
|---|---|
| Änderung der Klasse eines Charakters | Die Klasse ist permanent (FR-039). Es gibt keine Methode dafür. |
| Setzen einer Stufe auf einen beliebigen Wert | Nur Weiterschalten um eins. Ein Sprung wäre nicht von einem Fehler zu unterscheiden. |
| Erzeugen gebundener Gegenstände von außen | Nur B07 baut sie, weil nur B07 den Bindungsschlüssel setzt (Prinzip VI). |
| Auslegen des `cost`-Blocks | Workflow-Regel 5. |
| Auflösen von Fähigkeits-IDs | B08. |
| Bezug von Aufstiegsmaterial oder Coins | B11, B16. |
