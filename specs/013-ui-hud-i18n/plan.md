# Implementation Plan: B13 · UI, HUD & Texte

**Branch**: `013-ui-hud-i18n` | **Date**: 2026-08-30 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/013-ui-hud-i18n/spec.md`

## Summary

B13 ordnet die Spieleranzeige auf drei Vanilla-Flächen, nimmt die zwei brachliegenden davon in
Dienst, holt die zwei ausdrücklich befristeten Fenster heim und macht eine zweite Sprache möglich.

**Der Kern des Entwurfs ist, dass an fünf Stellen etwas benutzt statt gebaut wird.**

1. **Der Sekundentakt existiert bereits** ([research.md](./research.md) R1). `StatusActionBar.startRefresh`
   ist eine Sekunde lang, aus genau dem Grund, den FR-011 nennt, und es plant sich nach ADR-007
   selbst neu ein statt eine wiederkehrende Aufgabe zu benutzen — das einzige Muster, in dem dieses
   Projekt einen Takt überhaupt ausdrücken kann. B13 **erweitert diesen einen** zum HUD-Takt und
   legt keinen zweiten an. Dieselbe Entscheidung wie B11 (`ConsumableBuffs` auf dem
   Fähigkeiten-Sweep) und B12 (`PlaytimeAccrual` auf dem Inventar-Sweep).
2. **Die Bossbar braucht keine neue Abfrage** (R3). `DamageDealtEvent.targetId` sagt, wen der
   Spieler schlägt, `MobKinds.ofEntity` ob es ein Boss ist, `CombatStatusSource.statusOf` liefert
   den Balkenfüllstand — für eine Kreatur genauso wie für einen Spieler, weil beide durch dieselbe
   Engine gehen.
3. **Die Kanalisierung liegt fertig in `RunningAbility`** (R4): `startedAt`, `dueAt`, `phase`. Der
   Balken ist eine Rechnung gegen die Uhr und keine Aufgabe.
4. **Die Cooldown-Restzeit kommt aus `AbilityRegistry.remainingCooldown`** (R5) — zeitstempelbasiert
   auf Anfrage, dieselbe Methode, die `AbilityRuntime` selbst benutzt. Keine zweite Rechnung.
5. **Die Sprachprüfung ist gebaut** (R8). `RpgPlugin.loadMessages` sammelt schon heute die
   Schlüssel aller Module und lässt `MessageKeyValidator` **alle** fehlenden auf einmal melden. Was
   B13 hinzufügt, ist die Auswahl der Datei — nicht die Prüfung.

**Der eine Punkt, an dem dieser Plan echte Vorsicht braucht**, ist die Schadensanzeige (R7). Sie
benutzt die Technik, die B12s Hologramm auf echtem Paper belegt hat, aber in drei Punkten genau
umgekehrt: **nicht persistent**, nur für den Verursacher sichtbar, und mit eingeplanter Entfernung.
Das Hologramm ist persistent, weil es sein *muss* — und deshalb räumt es vor dem Setzen auf. Eine
Schadenszahl, die dasselbe täte, wäre bei 150 Spielern in Minuten tausendfacher Müll, der einen
Neustart überlebt. Was der Server nicht speichert, kann ein Absturz nicht zurücklassen; das ist der
ganze Inhalt von SC-009.

**Und die eine Falle, die dieser Block erben kann**, ist die aus T112 (R2): der Takt läuft
asynchron, und `runSyncOnEntity` scheitert von dort für alles außer einem Spieler **still**. Für die
Actionbar ist das heute folgenlos; für die Schadensanzeigen wäre es genau der Fehler, der grün
bleibt und nur manchmal zeichnet.

## Technical Context

**Language/Version**: Java 25 (ADR-001)

**Primary Dependencies**: Paper 26.2 API (nur in `rpg-platform` und `rpg-plugin`), `rpg-core` ohne
Bukkit. Benutzt aus dem Bestand: `CombatStatusSource`, `CombatState`, `DamageDealtEvent` und
`DamageAggregator` (B05), `ProgressView` und `LevelUpEvent`/`ProgressChangedEvent` (B06),
`AbilityRegistry.remainingCooldown`, `RunningAbility` und `AbilityItemTag` (B08), `Currency` (B08b),
`ZoneChangedEvent` und `Zones` (B09), `MobKinds.ofEntity`/`MobKind.boss` (B10), `Items` und
`GearConditions` (B11), `StatSnapshot` und `Attribute` (B04), `Messages`/`MapMessages`/
`MessageKeyValidator` (B01), `Scheduler` mit `runAsyncDelayed`, `runSyncOnEntity`,
`runSyncOnEntityDelayed` und `runSyncAtLocation` (B01).

**Storage**: **Keine.** B13 legt kein Schema, keine Tabelle und keine Migration an (FR-013b,
SC-011). Anzeigen sind nur serverweit abschaltbar, also gibt es keinen dauerhaften Zustand je
Spieler oder Charakter — der Block lässt sich vollständig entfernen, ohne dass Spielerdaten fehlen.

**Testing**: JUnit ohne Server für Flächenzuordnung, Bossbar-Rangfolge, Balkenfüllstände aus
Zeitstempeln, Sichtbarkeitsregeln, Materialeindeutigkeit je Klasse und Schemaprüfung; MockBukkit für
Zuhörer, die drei Fenster und die Schadensanzeigen; ein Quellcode-Wächter für FR-015 nach dem Muster
von `ConfigOnlyAbilityTest` und `NoRawTypeNameLeftTest` (R9). Eine wiederholbare Messung für den
Sammeltakt bei 200 Spielern (SC-001). **Kein Testcontainers** — es gibt nichts zu persistieren.

**Target Platform**: Paper-Server, eine Instanz (ADR-002), reiner Vanilla-Client (ADR-005)

**Project Type**: Gradle-Mehrmodulprojekt, `plugin → platform → core`

**Performance Goals**: Der Sammeltakt bleibt **ein** Durchlauf über alle Spieler und unter 1 ms bei
200 (SC-001). Gesendet wird nur bei Änderung oder wenn das Ausblenden es erzwingt (FR-013) — eine
abgeschaltete Fläche kostet gar nichts (FR-013a, SC-010). Balkenfüllstände und Cooldown-Restzeiten
sind Zeitstempelrechnungen ohne eigene Aufgabe. Der verbindliche Zielwert des Projekts (150 Spieler,
p95 MSPT < 40 ms) bleibt und wird in B15 nachgewiesen (ADR-031).

**Constraints**: Paper-API nur im Tick; der HUD-Takt ist **asynchron**, also gehört für alles außer
einem Spieler `runSyncAtLocation` dorthin und nicht `runSyncOnEntity` (R2); keine wiederkehrende
Aufgabe je Spieler; Schadensanzeigen sind **nicht persistent**; kein Resource Pack (ADR-005); kein
Umbau an `AbilityHotbar` (FR-024), `ClassSelectionMenu` (FR-070) oder B12s Fenstern (FR-071).

**Scale/Scope**: Drei Flächen, zehn Attribute in der Übersicht, drei Fenster in B13s Hand (zwei
übernommen, eines neu), ein Kommando, ein Sprachsatz je Server, bis zu sieben Cooldown-Slots je
Klasse.

**Keine offenen Punkte.** Die sechs Fragen des Blocksteckbriefs sind vor der Spec beantwortet, fünf
weitere in `/speckit-clarify` an der geschriebenen Spec, und zehn beim Nachsehen im Code — die
stehen in [research.md](./research.md). Zwei davon haben Annahmen der Spec widerlegt (R10) und sind
dort bereits korrigiert.

## Constitution Check

*GATE: vor Phase 0 bestanden, nach Phase 1 erneut geprüft.*

| Prinzip | Wie dieser Plan es einhält |
|---|---|
| **I · Nebenläufigkeit** | Der HUD-Takt läuft asynchron und fasst die Paper-API nie selbst an; die Übergabe in den Tick ist explizit und **je Art des Ziels unterschiedlich**: `runSyncOnEntity` für Spieler, `runSyncAtLocation` für die Schadensanzeigen (R2). Die Entfernung einer Anzeige wird **innerhalb** des Ticks eingeplant, in dem sie entsteht — dort ist die Entität aufgelöst. Kein globaler veränderlicher Zustand: die Bossbar hängt am Spieler, die Anzeige an ihrem Empfänger. Nie der globale Bukkit-Scheduler (ADR-007). |
| **II · Performance** | **Kein zweiter Takt.** B13 erweitert den einen vorhandenen Sekundentakt (R1); es entsteht keine wiederkehrende Aufgabe je Spieler oder je Entität. Balkenfüllstand, Cooldown-Restzeit und Kanalisierungsfortschritt sind **zeitstempelbasiert lazy** (R4, R5) und nicht getrieben. Gezeichnet wird ereignisgesteuert bei Änderung; der Takt trägt nur, was das Ausblenden erzwingt (FR-011). Eine abgeschaltete Fläche erzeugt keine Arbeit. Kein Datenbankzugriff — B13 hat keinen. |
| **III · Architektur** | `rpg-core` ohne Bukkit: Flächenzuordnung, Rangfolge, Füllstandsrechnung, Sichtbarkeitsregeln, Konfigurationsschema, Message-Schlüssel. `rpg-platform` trägt Actionbar, Bossbar, Scoreboard, die drei Fenster, die Schadensanzeigen und das Cooldown-Overlay. **`HudRenderer` und `ItemRenderer` sind die Schnittstellen, die Constitution III.4 verlangt** — ein pack-fähiger Renderer tritt später an ihre Stelle, ohne dass B04, B05 oder B08 sich ändern (FR-022). B13 liest fremde Blöcke über deren öffentliche Nähte und fasst **keinen** von ihnen an; die zwei übernommenen Fenster kommen mit ihrer Zustimmung (ADR-028, ADR-032). |
| **IV · Datenhaltung** | Nicht anwendbar, und das ist eine Zusage und kein Zufall: **B13 persistiert nichts** (FR-013b). Kein Schema, keine Migration, keine versionierten Spielerdaten. Die Schadensanzeigen sind ausdrücklich **nicht persistent** (R7) — das ist die einzige Stelle, an der dieser Block überhaupt etwas in die Welt schreibt, und sie schreibt es bewusst flüchtig. |
| **V · Datengetriebenes Design** | Flächenzuordnung, Sichtbarkeit, Lebensdauern, Takt und Sprache in `ui.yml`, beim Start gegen ein Schema geprüft, Fail-Fast mit Datei, Schlüssel und Grund. **Zwei Prüfungen sind Regeln, keine Zahlenwahl**: die Materialien einer Klasse müssen eindeutig sein (FR-032, R6), und der gewählte Sprachsatz muss vollständig sein (FR-018). Alle Texte über Message-Schlüssel — und FR-015 sichert das **maschinell** ab, nach dem Muster der vier vorhandenen Wächter (R9). |
| **VI · Korrektheit & Sicherheit** | Der Server ist Autorität; die Anzeige zeigt nur, was andere Blöcke führen, und entscheidet nichts (FR-074). **Ein Fehler beim Zeichnen darf keinen Tick kosten** — `StatusActionBar` fängt heute schon lokal und protokolliert, und das gilt für jede neue Fläche. Ein fehlender Text kommt **nie** als Platzhalter beim Spieler an (FR-019); er bricht den Start ab, lange bevor jemand ihn sehen könnte. Kein Reflection, kein NMS. |
| **VII · Tests** | Jede Regel serverlos: Rangfolge, Füllstände, Sichtbarkeit, Materialeindeutigkeit, Schemaprüfung. MockBukkit für alles, was Paper-Typen berührt. Kein Testcontainers, weil es nichts zu persistieren gibt. Die Messung ohne Volllast für den Sammeltakt erfüllt Prinzip VII.4; der Lasttest bleibt B15 (ADR-031) und hält diesen Block nicht offen. |
| **VIII · Sprache** | Dokumentation deutsch, Code und Config-Schlüssel englisch, Spielertexte über Message-Schlüssel — und ab diesem Block in **einem Sprachsatz je Server**, mit Englisch als Standard. |

### Nachprüfung nach Phase 1

Das Design hat drei Punkte hinzugefügt, die die Prüfung oben nicht kannte — alle drei halten:

- **Die zwei Nähte liegen in `rpg-platform`**, nicht in `rpg-core`. `ItemRenderer` gibt einen
  Bukkit-Typ zurück und *kann* nach III.1 nicht tiefer liegen; `HudRenderer` könnte, hätte dort aber
  weder Aufrufer noch Umsetzung. Begründung in [contracts/hud-api.md](./contracts/hud-api.md) §2.
- **Die Entfernung einer Schadensanzeige wird im Tick geplant**, nicht aus dem Takt. Damit ist die
  Falle aus R2 nicht umgangen, sondern gar nicht erst betreten.
- **Die Bossbar braucht keine Warteschlange.** Ein verdrängter Bosskampf kehrt zurück, weil sein
  *Zustand* beim nächsten Takt noch besteht — nicht weil ihn jemand gemerkt hat. Kein zusätzlicher
  veränderlicher Zustand (Constitution I.6).

### Ein Eingriff in einen fremden Block — und drei, die es ausdrücklich nicht sind

Dieser Plan fasst **einen** fertigen Block an: `StatusActionBar` (B05/B06) wird hinter
`HudRenderer` gezogen und ihr Takt zum HUD-Takt erweitert. Das ist kein Übergriff, sondern die
Einlösung einer Zusage — ihr eigenes Javadoc sagt, sie heiße bewusst nicht `HudRenderer`, *„weil ein
größerer Name B13 gezwungen hätte, zwei Abstraktionen zu versöhnen statt eine zu erweitern."* Genau
diese Erweiterung passiert jetzt.

Nicht angefasst werden `AbilityHotbar` (FR-024), `ClassSelectionMenu` (FR-070) und B12s
`StatisticsMenu`/`LeaderboardMenu` (FR-071). Alle drei laufen, alle drei sind abgenommen, und keine
ist befristet. Siehe [Complexity Tracking](#complexity-tracking).

## Project Structure

### Documentation (this feature)

```text
specs/013-ui-hud-i18n/
├── plan.md              # Diese Datei
├── research.md          # Phase 0 — zehn Fragen an den gebauten Code
├── data-model.md        # Phase 1
├── quickstart.md        # Phase 1
├── contracts/
│   ├── hud-api.md       # Was B13 nach außen anbietet und was es erwartet
│   └── ui-config.md     # Das Schema von ui.yml
├── checklists/
│   └── requirements.md
└── tasks.md             # /speckit-tasks — nicht von /speckit-plan erzeugt
```

### Source Code (repository root)

```text
rpg-core/src/main/java/rpg/core/ui/                  # neu — bukkit-frei
├── HudSurface.java                          # ACTION_BAR, BOSS_BAR, SIDEBAR
├── BossBarOccasion.java                     # CHANNELLING > BOSS_FIGHT > ZONE_NAME
├── BossBarPriority.java                     # die Rangfolge als Regel (FR-004)
├── BarProgress.java                         # Fuellstand aus zwei Zeitstempeln (R4)
├── SidebarLines.java                        # welche Zeile welchen Wert traegt
├── UiConfig.java                            # der geprueft Inhalt von ui.yml
├── UiConfigSchema.java                      # Bindung und Pruefung, Fail-Fast
├── UiMessageKeys.java                       # die Schluessel dieses Blocks
├── LanguageSet.java                         # ein Sprachsatz und seine Datei (R8)
└── UiModule.java                            # Start, Laden, Nachladen

rpg-platform/src/main/java/rpg/platform/ui/          # neu — Paper erlaubt
├── HudRenderer.java                         # die Schnittstelle aus ADR-005
├── ItemRenderer.java                        # dito, fuer Gegenstaende
├── PaperHudRenderer.java                    # die Vanilla-Umsetzung
├── HudTick.java                             # DER eine Sammeltakt (R1)
├── PaperBossBar.java                        # eine Bossbar je Spieler (FR-004b)
├── PaperSidebar.java                        # das Scoreboard
├── AbilityCooldownOverlay.java              # das Vanilla-Overlay (R5, R6)
├── DamageNumbers.java                       # kurzlebig, nicht persistent (R7)
├── CharacterSheetMenu.java                  # neu (FR-050 bis FR-057)
├── CharacterSheetListener.java
├── WaypointMenu.java                        # UMGEZOGEN aus rpg.platform.zone
├── WaypointMenuListener.java                # UMGEZOGEN
├── CrystalInteractListener.java             # UMGEZOGEN (ADR-032 nennt die Eingabe)
├── CurrencyMenu.java                        # UMGEZOGEN aus rpg.platform.currency
├── CurrencyMenuListener.java                # UMGEZOGEN
└── MenuFrame.java                           # der gemeinsame Rahmen fuer DIESE drei

rpg-platform/src/main/java/rpg/platform/hud/
└── StatusActionBar.java                     # GEAENDERT: hinter HudRenderer, Takt wird HudTick

rpg-plugin/src/main/java/rpg/plugin/
├── RpgPlugin.java                           # GEAENDERT: UiModule, Sprachwahl, /char
└── command/CharacterSheetCommand.java       # neu, vorlaeufig (FR-058)

rpg-plugin/src/main/resources/
├── ui.yml                                   # neu
├── messages.yml                             # bleibt der englische Standardsatz
└── plugin.yml                               # GEAENDERT: ein Kommando mehr
```

**Structure Decision**: Ein neues Paket je Schicht (`rpg.core.ui`, `rpg.platform.ui`) nach dem
Muster jedes vorherigen Blocks. Die zwei übernommenen Fenster ziehen **mit ihren Listenern** um, weil
ADR-032 die Eingabe ausdrücklich mitnennt und ein Fenster ohne seinen Listener ein halber Umzug
wäre. `MenuFrame` fasst nur die drei Fenster zusammen, die B13 in der Hand hat — die zwei fremden
bleiben, wo sie sind, und bekommen keinen gemeinsamen Rahmen aufgezwungen (R10).

## Complexity Tracking

| Abweichung | Warum nötig | Einfachere Alternative verworfen, weil |
|---|---|---|
| **`StatusActionBar` wird angefasst** (fremder Block) | FR-020 und FR-023 verlangen, dass die Ausgabe hinter `HudRenderer` liegt und dabei kein vorhandener Wert verschwindet. Ihr eigenes Javadoc kündigt diesen Schritt an. | Eine zweite Actionbar-Ausgabe daneben wäre exakt die „zwei Abstraktionen", die ihr Javadoc als Grund nennt, warum sie den Namen `HudRenderer` **nicht** trägt. Zwei Sender auf eine Fläche heißt: der letzte gewinnt, und welcher das ist, hängt an der Reihenfolge. |
| **Ein Kommando `/char` in einem Schicht-3-Block** | Ein Fenster ohne Aufrufweg ist für den Spieler nicht vorhanden (FR-057). | Eine Eingabegeste wäre unsichtbar, und der Offhand-Tausch wird von `EquipmentLockListener` bereits angefasst. Präzedenz: ADR-028 (`/coins`) und B12 (`/stats`, `/top`) — die Kommandostruktur sammelt B14 ein, nicht dieser Block. **Braucht einen eigenen ADR nach dem Muster von ADR-028.** |
| **Zwei Fenster ziehen um** | ADR-028 und ADR-032 nennen sie ausdrücklich befristet und B13 als Ziel. | Sie stehen zu lassen hieße, zwei ADRs offen zu lassen, obwohl der Block da ist, auf den sie warten. Das ist die Sorte Schuld, die niemand einfordert. |
| **Schadensanzeigen schreiben Entities in die Welt** | FR-040; B05 bündelt seit Monaten ausdrücklich für diesen Zweck. | Eine Textzeile statt einer Zahl am Trefferort verfehlt den Punkt: die Zahl soll **dort** stehen, wo getroffen wurde. Das Risiko liegt nicht in der Technik, sondern in der Lebensdauer — deshalb nicht persistent (R7). |

**Nicht** in dieser Tabelle, weil ausdrücklich nicht getan: `AbilityHotbar`, `ClassSelectionMenu`
und B12s Fenster bleiben unberührt.
