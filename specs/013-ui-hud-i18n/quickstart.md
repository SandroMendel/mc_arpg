# Quickstart · B13 nachweisen

**Spec**: [spec.md](./spec.md) · **Plan**: [plan.md](./plan.md) · **Verträge**:
[contracts/](./contracts/)

Wie man belegt, dass dieser Block tut, was er zusagt — von der billigsten Prüfung zur teuersten.
Jeder Abschnitt nennt die Erfolgskriterien, die er abdeckt.

**Eine Besonderheit dieses Blocks:** er zeichnet. Was auf dem Bildschirm ankommt, kann kein Test
sehen — er kann nur belegen, dass das Richtige gesendet wurde. Abschnitt 5 ist deshalb hier nicht
Kür, sondern die einzige Stelle, an der die eigentliche Zusage geprüft wird.

---

## 1 · Ohne Server (Prinzip VII)

```powershell
./gradlew :rpg-core:test --tests "rpg.core.ui.*"
```

Deckt ab: **SC-003** (Herzleiste), **SC-006** (Sprachsatz), **SC-011** (kein Schema).

Die Regeln dieses Blocks sind Zuordnung und Rechnung und brauchen keinen Server:

- **Rangfolge**: alle drei Anlässe zugleich → `CHANNELLING` gewinnt. Kanalisierung endet, Bosskampf
  besteht noch → der Bosskampf ist wieder da, **ohne** dass ihn jemand neu gemeldet hat. Zonenname
  während eines Bosskampfs → entfällt und wird **nicht** nachgeholt (FR-004a).
- **Füllstand**: `BarProgress` gegen eine gestellte Uhr — bei `startedAt`, in der Mitte, bei `dueAt`
  und darüber hinaus. Nie außerhalb von `[0,1]`.
- **Flächenzuordnung**: jeder Wert liegt auf genau einer Fläche. Der Test geht die Werte durch und
  zählt die Flächen — zwei wären eine doppelte Wahrheit (FR-001).
- **Materialeindeutigkeit**: eine Klasse mit zwei Fähigkeiten auf demselben Material bricht ab, und
  die Meldung nennt Klasse, beide Fähigkeiten und das Material. **Der Fall mit `items()` in der
  Mehrzahl gehört dazu** — sonst prüft der Test die Hälfte (siehe [research.md](./research.md) R6).
- **Schemaprüfung**: je ein Fall für jede Regel aus [contracts/ui-config.md](./contracts/ui-config.md);
  geprüft wird nicht nur *dass* es scheitert, sondern **dass die Meldung den Schlüssel nennt**.

---

## 2 · Der Wächter gegen hartcodierte Texte

```powershell
./gradlew :rpg-platform:test --tests "*NoHardcodedPlayerTextTest"
```

Deckt ab: **SC-002**.

Nach dem Muster von `ConfigOnlyAbilityTest` und `NoRawTypeNameLeftTest` (R9): gesucht wird in der
**Quelle**, Kommentare werden vorher entfernt — eine Klasse zu erklären ist erlaubt, sie zu rufen
nicht. Und wie dort prüft der Test seine eigene Ausnahmeliste mit: ein Wächter, dessen Ausnahmen ins
Leere zeigen, bewacht nichts.

> **Die zweite Hälfte der Zusage steht woanders.** Dieser Wächter sieht keinen Schlüssel, der zwar
> benutzt, aber nie deklariert wird — den fängt erst die Startprüfung aus Abschnitt 4. Beide
> zusammen ergeben FR-014 und FR-015; einer allein nicht.

---

## 3 · Mit MockBukkit

```powershell
./gradlew :rpg-platform:test --tests "rpg.platform.ui.*"
```

Deckt ab: **SC-005** (eine Ansicht trägt alles), **SC-007** (Cooldown ohne Zahl), **SC-010**
(abgeschaltet ist kostenlos).

- **Der Takt ist einer.** Ein Test zählt die eingeplanten Aufgaben über mehrere Durchläufe: es
  bleibt bei einer, egal wie viele Spieler online sind (FR-012).
- **Abgeschaltet heißt kostenlos.** Mit `sidebar.enabled: false` entstehen **keine**
  Scoreboard-Pakete — nicht unsichtbare, sondern gar keine (FR-013a).
- **Nur bei Änderung.** Zwei Durchläufe ohne Wertänderung senden einmal (für die Actionbar, die
  ausblenden würde) und sonst nichts (FR-013).
- **Cooldown-Overlay**: eine ausgelöste Fähigkeit setzt das Overlay auf **ihrem** Material; nach
  einer Wiederanmeldung steht die **verbleibende** Zeit da, nicht die volle und nicht keine
  (FR-033).
- **Schadenszahlen**: sichtbar nur für den Verursacher (FR-042), **nicht persistent** (FR-044), und
  nach der konfigurierten Lebensdauer weg. Der Test prüft ausdrücklich das Persistenz-Flag — das ist
  die Eigenschaft, an der SC-009 hängt.
- **Fenster**: die Übersicht baut nur bei geänderter `StatSnapshot`-Revision neu auf (FR-054); ein
  Charakterwechsel **schließt** sie (FR-055).

> ⚠️ **Was MockBukkit hier nicht beweist.** Ob Paper das Cooldown-Overlay, die Bossbar und das
> `TextDisplay` wirklich so setzt, sieht nur ein echter Server — dieselbe Lücke, die B12 beim
> Hologramm hatte. Siehe Abschnitt 5.

---

## 4 · Der ganze Bootstrap

```powershell
./gradlew :rpg-plugin:test --tests "*FullBootstrapTest"
```

Deckt ab: **SC-004** (`HudRenderer` austauschbar), **SC-006** (unvollständiger Sprachsatz bricht ab).

- Der Server startet mit `ui.yml` und dem englischen Sprachsatz.
- Ein Sprachsatz, dem ein Schlüssel fehlt, **bricht den Start ab** und die Meldung listet **alle**
  fehlenden auf einmal, nicht nur den ersten.
- Ein zweiter `HudRenderer` an derselben Stelle fällt auf — nach dem Muster von
  `NoCompetingMobProviderTest`: zwei Sender auf eine Fläche heißt, der letzte gewinnt, und welcher
  das ist, hängt an der Reihenfolge.

---

## 5 · Auf dem Testserver

**Hier wird der Block wirklich geprüft.** Alles davor belegt, dass das Richtige *gesendet* wird;
dass es *ankommt*, sieht nur ein Bildschirm.

```powershell
# Jar deployen - und daran denken: Bukkit ueberschreibt vorhandene Configs NICHT.
# ZWEI Dateien muessen von Hand mit:
#   ui.yml        - neu, wird ohne sie gar nicht erst angelegt
#   messages.yml  - VORHANDEN, aber um die Schluessel dieses Blocks gewachsen
#
# Die Frage ist nicht "welche Datei ist neu", sondern "welche unterscheidet sich":
#   diff --strip-trailing-cr <repo>/rpg-plugin/src/main/resources/X <server>/plugins/RpgPlugin/X
# Ohne --strip-trailing-cr meldet CRLF-vs-LF jede Datei als abweichend.
```

Schritte (allein machbar, sofern nicht anders vermerkt):

1. Anmelden — Actionbar, Sidebar und XP-Leiste stehen da, die Bossbar nicht.
1a. **Hinsehen, wo nichts steht**: die Actionbar trägt Leben, Mana und Verteidigung — **kein Level
    und keine Erfahrung** (FR-002a). Die stehen auf der Sidebar. Die XP-Leiste am unteren Rand
    zeigt sie ein zweites Mal; das ist die eine zugelassene Ausnahme (FR-001a). **Mehr als diese
    eine Doppelung darf nirgends zu sehen sein.**
2. Schaden nehmen — Actionbar ändert sich sofort, Herzleiste zeigt den richtigen Prozentwert.
2a. Aufsteigen und eine Zone betreten — die Sidebar-Zeilen ändern sich **sofort**, nicht erst
    sichtbar verzögert (FR-009). Danach Coins verdienen: diese Zeile **darf** bis zu eine Sekunde
    brauchen (FR-009a). Das ist kein Fehler, sondern die Grenze, die B08b setzt — wer sie für einen
    Bug hält, liest T056d.
3. Eine Zone betreten — die Bossbar nennt den Zonennamen und **verschwindet** nach der
   konfigurierten Dauer.
4. Eine Fähigkeit mit Kanalisierung auslösen — der Balken läuft, und er **verdrängt** einen
   laufenden Bosskampf.
5. Nach der Kanalisierung: der Bossbalken ist **wieder da**, ohne dass etwas neu ausgelöst wurde.
6. Einen Boss angreifen — die Bossbar zeigt sein Leben und leert sich mit ihm.
7. Zonenwechsel **während** eines Bosskampfs — der Zonenname kommt nicht, auch nicht später.
8. Eine Fähigkeit auslösen — das graue Overlay läuft über **ihrem** Slot und über keinem zweiten.
9. Ab- und wieder anmelden, während ein Cooldown läuft — die Restzeit stimmt.
9a. Dasselbe mit **stehender Bossbar**: abmelden, während der Zonenname oder ein Bosskampf oben
    steht, und wieder anmelden — es steht **keine alte Leiste** da (FR-004c).
10. Eine Kreatur schlagen — die Zahl steht am Trefferort und verschwindet wieder.
11. `/char` — die Übersicht zeigt **jedes** Attribut, die Ausrüstung und ihren Zustand.
12. Charakter wechseln, während `/char` offen ist — das Fenster **schließt**.
13. Rechtsklick auf einen Wegpunkt-Kristall — dasselbe Fenster wie vorher.
14. `/coins` — dasselbe Fenster wie vorher.
15. `sidebar.enabled: false` setzen, neu starten — keine Sidebar, und im Log keine Spur davon, dass
    trotzdem gerechnet wird.
15a. Dasselbe **ohne Neustart**: `sidebar.enabled: false` setzen und nachladen — die Sidebar
    verschwindet beim nächsten Takt (FR-013c). Bleibt sie stehen, hat der Takt seine Konfiguration
    beim Start eingefroren, und das Nachladen hat Erfolg gemeldet, ohne etwas zu ändern.
16. Eine zweite Sprachdatei anlegen, `language` umstellen, neu starten — **jeder** Text ist die
    neue Sprache, einschließlich der Zonen- und Artnamen aus B09 und B10.
17. Aus derselben Datei einen Schlüssel entfernen, neu starten — der Start bricht ab und nennt ihn.
18. Server hart abbrechen, während Schadenszahlen in der Luft stehen; neu starten — **keine
    einzige** ist übrig (SC-009).
19. **Mit zwei Spielern**: A schlägt eine Kreatur, B steht daneben — B sieht As Zahl **nicht**
    (FR-042).

---

## 6 · Was hier ausdrücklich **nicht** nachgewiesen wird

| Nicht hier | Wo stattdessen |
|---|---|
| **SC-001** unter Volllast (200 Spieler, < 1 ms je Tick) | Eine wiederholbare Messung **ohne** Volllast belegt die eigene Rechenarbeit des Takts (Prinzip VII.4). Der Nachweis unter 150 Spielern bleibt B15 (ADR-031) und hält diesen Block nicht offen |
| Die Skill-Leiste selbst | Gehört B08 und wird nicht angefasst (FR-024) |
| `ClassSelectionMenu`, `StatisticsMenu`, `LeaderboardMenu` | Bleiben unberührt (FR-070, FR-071) — sie sind bereits abgenommen |
| Persistenz | B13 hat keine (FR-013b). Kein Testcontainers, kein Schema, keine Migration |
