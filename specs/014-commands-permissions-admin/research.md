# Phase 0 · Recherche: B14 · Commands, Permissions & Admin-Tools

**Datum:** 2026-09-03 · **Branch:** `014-commands-permissions-admin`

Alles hier ist am Quelltext oder am aufgelösten Jar nachgeprüft. Wo etwas **nicht** geprüft werden
konnte, steht es ausdrücklich als offener Punkt — nicht als Annahme.

---

## 1. Brigadier: verfügbar, und ohne neuen Klassenlader-Blindfleck

**Entscheidung:** Das Kommandogerüst baut auf Papers Brigadier-Anbindung.

**Belegt am aufgelösten Jar** (`paper-api-26.2.build.112-stable.jar`), nicht aus dem Gedächtnis:

| Klasse | vorhanden |
|---|---|
| `io.papermc.paper.command.brigadier.Commands` | ja |
| `io.papermc.paper.command.brigadier.CommandSourceStack` | ja |
| `io.papermc.paper.command.brigadier.BasicCommand` | ja |
| `io.papermc.paper.command.brigadier.argument.ArgumentTypes` | ja |
| `io.papermc.paper.command.brigadier.argument.CustomArgumentType` | ja |
| `io.papermc.paper.plugin.lifecycle.event.LifecycleEventManager` | ja |

**Der Punkt, der Sorge gemacht hat, ist entschärft:** `com.mojang.brigadier` steckt **nicht** in
`paper-api` (null Einträge im Jar). Es löst aber **transitiv** auf — `com.mojang:brigadier:1.3.10`
liegt im Gradle-Cache, gezogen über `compileOnly(libs.paper.api)`. Zur Laufzeit liefert es der
Server selbst.

**Folge: B14 nimmt keinen einzigen neuen `libraries:`-Eintrag auf.** Damit entfällt genau das
Risiko, das [[vuntexrpg-classloader-blind-spot]] beschreibt und das B01 einmal getroffen hat. Das
ist das stärkste Argument für Brigadier gegenüber einem selbstgebauten Gerüst — und es war vorher
nicht bekannt, sondern eine offene Frage.

**Alternative verworfen:** ein eigenes Gerüst über `CommandExecutor`/`TabCompleter`. Es müsste
Argumenttypen, Vorschläge und Fehlermeldungen selbst bauen — also genau das nachbilden, was hier
fertig und vom Client unterstützt danebenliegt. Der einzige Vorteil wäre weniger Paper-Bindung; das
Projekt ist über `plugin.yml`, PDC, Scheduler und Display-Entities ohnehin tief an Paper gebunden.

### Der eine offene Punkt

**Ob der `commands:`-Block in `plugin.yml` entfallen muss oder darf, ist nicht geprüft.** Eine
Brigadier-Registrierung im Lebenszyklus und ein gleichnamiger `plugin.yml`-Eintrag können sich
gegenseitig überschreiben. Das ist mit keinem Test zu klären, sondern nur am laufenden Server —
und es ist genau die Art Frage, die dieses Projekt schon viermal falsch beantwortet hat, weil ein
grüner Test etwas anderes behauptete.

**Vorgehen:** Ein Wegwerf-Kommando **zuerst**, vor allem anderen, auf dem echten Server
registrieren und beide Fassungen ausprobieren. Erst danach die sechs echten Kommandos anfassen.
Der Rechtebaum (`permissions:`) bleibt in jedem Fall in `plugin.yml`, weil FR-010 das verlangt und
weil Bukkit ihn dort erwartet.

#### Beantwortet am 2026-09-05 (T007–T010)

Gemessen mit dem Wegwerf-Kommando `/b14probe` auf Paper 26.2, Java 25. Beide Fassungen antworteten
unterschiedlich (`B14PROBE: BRIGADIER …` gegen `B14PROBE: PLUGIN_YML …`), damit das Ergebnis
ablesbar ist und nicht erschlossen werden muss.

| Lage | Konsoleneingabe | Antwort |
|---|---|---|
| **nur** Brigadier, kein `plugin.yml`-Eintrag (T007) | `b14probe` | `B14PROBE: BRIGADIER (kein Argument)` |
| dieselbe Lage, mit Argument | `b14probe beta` | `B14PROBE: BRIGADIER (kind=beta)` |
| Brigadier **und** `plugin.yml`-Eintrag (T008) | `b14probe` | `B14PROBE: BRIGADIER (kein Argument)` |
| dieselbe Lage, mit Argument | `b14probe gamma` | `B14PROBE: BRIGADIER (kind=gamma)` |

**Drei Feststellungen.**

1. **Eine Brigadier-Registrierung braucht keinen `plugin.yml`-Eintrag.** Ohne den Eintrag existiert
   das Kommando, führt aus und nimmt sein Argument entgegen.
2. **Bei Namensgleichheit gewinnt Brigadier.** Und zwar nicht, weil der andere Weg fehlschlug: der
   Eintrag war da und `setExecutor` lief durch — der Start protokollierte
   `[b14probe] plugin.yml-Fassung verdrahtet`. Die Fassung ist verdrahtet und wird trotzdem **nie
   erreicht**. Sie überschreiben sich nicht gegenseitig; die Brigadier-Registrierung liegt schlicht
   davor.
3. **Kein Klassenlader-Problem** (T009). Im Startlog steht keine `ClassNotFoundException`, kein
   `NoClassDefFoundError` und keine Brigadier-Warnung. `com.mojang.brigadier` löst transitiv auf und
   kommt zur Laufzeit vom Server — **B14 nimmt keinen `libraries:`-Eintrag auf.**

**Entscheidung: der `commands:`-Block entfällt**, sobald ein Kommando auf Brigadier umgezogen ist.
Er ist dann totes Gewicht — ein Eintrag, der beschreibt, was er nicht mehr bedient. Zwei Dinge, die
er heute nebenbei trägt, ziehen mit um und dürfen dabei nicht verlorengehen:

- **`permission:`** je Kommando. Wandert in `requires(...)` am Brigadier-Knoten; die Rechte selbst
  bleiben im `permissions:`-Block von `plugin.yml` (FR-010).
- **`description:` und `usage:`**. Die Brigadier-Fassung liest sie nicht — sie gehören künftig an
  den Knoten und ihre Texte nach `messages.yml` (Prinzip V).

**Was der Versuch NICHT belegt:** ob die Vervollständigung beim *Client* ankommt. Die Konsole hat
das Argument entgegengenommen, was für einen lebenden Knotenbaum spricht, aber Vorschläge sind ein
Paket an den Spieler. Das steht als quickstart-Schritt 0 offen und braucht einen Spieler.

---

## 2. Wo das Gerüst wohnt

**Entscheidung:** `rpg-plugin`, im Paket `rpg.plugin.command`.

Brigadier und `CommandSourceStack` sind Paper-Typen. Prinzip III verbietet Bukkit in `rpg-core`;
`rpg-platform` wäre denkbar, aber die fünf vorhandenen Kommandos liegen bereits in
`rpg.plugin.command`, und die Registrierung braucht ohnehin die Plugin-Instanz für den
`LifecycleEventManager`.

**`rpg-core` bekommt von B14 kein neues Interface und keine neue Abhängigkeit.** Drei Aufgaben
ändern dort trotzdem etwas Bestehendes, und beide Gründe stehen fest: die Umbenennung von
`ItemModule.notifyReloaded()` (§5) und die Herkunft am `HordeRegistry.Entry` (§7, ADR-pflichtig).
Alles andere lebt in `rpg-plugin`.

**Ein Umzug fällt an:** `TrashCommand` liegt heute als einziges in
`rpg-platform/.../item/TrashCommand.java`. Es zieht zu den anderen — sonst bleibt die Ausnahme
bestehen, die B14 gerade abschaffen soll.

---

## 3. Tab-Completion: nicht abwesend, sondern fünffach

**Befund, korrigiert:** Fünf der sechs Kommandos haben Tab-Completion —
`CharacterSheetCommand`, `TopCommand`, `StatisticsCommand`, `CoinsCommand`, `XpCommand`
implementieren je `TabCompleter` mit eigener `onTabComplete`. `TrashCommand` hat keine und braucht
keine: es nimmt kein Argument.

Registriert wird klassisch, in `RpgPlugin`: `getCommand("x").setExecutor(...)` plus
`.setTabCompleter(...)` an acht Stellen.

**Das Problem ist die Doppelung, nicht die Lücke.** Jede der fünf Listen steht *neben* der
Argumentprüfung desselben Kommandos, in einer anderen Methode, und kann mit ihr auseinanderlaufen —
ein Vorschlag, den die Prüfung anschließend ablehnt, ist ein Fehler, den kein Test sieht. Brigadier
leitet beides aus **einem** Argumentbaum ab; damit ist die Abweichung strukturell ausgeschlossen
statt nur unwahrscheinlich (FR-002).

> **Eine frühere Fassung dieser Unterlage behauptete, es gebe gar keine Tab-Completion.** Die
> Behauptung stammte aus einem Suchbefehl, der von der Worktree-Isolation abgebrochen wurde; seine
> leere Ausgabe wurde für einen Befund gehalten. Festgehalten, weil es dieselbe Fehlerklasse ist,
> die diesem Projekt schon viermal einen grünen Test beschert hat: **eine Suche, die nichts findet,
> ist erst dann ein Befund, wenn sie gelaufen ist.**

---

## 4. Das Audit-Log: fertig, benutzt, nicht lesbar

**Entscheidung:** unverändert übernehmen, konsequent benutzen, um eine Leseseite ergänzen.

Vorhanden aus B02 (**B02**-FR-018):

- `AuditEntry(occurredAt, actor, action, targetPlayerId, details)` — das Javadoc nennt als
  Beispielaktion ausgerechnet `item_granted`.
- `AuditLogRepository.append(entry)` und `between(from, to)` → `CompletableFuture<List<AuditEntry>>`.
  **Kein Update, kein Delete** — und das ist die Zusage, nicht eine Konvention.
- Bezug über die Dienstregistrierung: `PersistenceModule` meldet mit
  `context.registry().registerService(ID, AuditLogRepository.class, auditLog)` an,
  `CurrencyModule` holt es mit `context.getService(AuditLogRepository.class)`. B14 macht es genauso.

**Schon benutzt** von B08b (`JdbcCurrencyAdmin`) und B06 (`Progression.setProgress` nimmt eine
`actorId`).

**`between()` hat außerhalb der Tests keinen Aufrufer.** B14 ist der erste Leser. Wichtig:
`between()` gibt ein `CompletableFuture` zurück — Prinzip I verbietet ein `join()` im Tick. Das
Ergebnis geht über den Scheduler zurück in den Tick.

**Aus `XpCommand` übernommen:** Für die Konsole steht als Handelnder eine feste Null-UUID
(`new UUID(0L, 0L)`), ausdrücklich damit das Log greppbar bleibt. B14 hebt das ins Gerüst, statt es
je Kommando zu wiederholen.

---

## 5. Das Neuladen: der tote Pfad

**Entscheidung:** B14 baut den Einstiegspunkt, den `ZoneModule` in seinem Javadoc schon beschreibt.

`ConfigLoader.reloadAll()` existiert seit B01 (**B01**-FR-003 / **B01**-FR-004) mit einer starken
Zusage: **atomar** —
schlägt eine Quelle fehl, wird kein Handle aktualisiert und für *alle* Module bleibt die vorherige
Konfiguration aktiv. `ConfigHandle.get()` gibt immer den gültigen Stand, deshalb erreicht ein
Neuladen die Module ohne weitere Verdrahtung.

**Sechs Module halten einen `ConfigHandle` und haben einen Nachladehaken:**

| Modul | Haken |
|---|---|
| `CombatModule` | `applyReloadedConfig()` |
| `MobModule` | `applyReloadedConfig()` + `onReload(Runnable)` |
| `StatisticsModule` | `applyReloadedConfig()` + `onReload(Runnable)` |
| `UiModule` | `applyReloadedConfig()` + `onReload(Runnable)` |
| `ZoneModule` | `applyReloadedConfig()` + `onReload(Runnable)` |
| `ItemModule` | **`notifyReloaded()`** + `onReload(Runnable)` |

**Und kein Produktivcode ruft irgendetwas davon auf.** `reloadAll()` hat nur Tests als Aufrufer;
`notifyReloaded()` hat genau einen — `ItemModuleReloadTest`. Das ist derselbe Riss wie in
[[vuntexrpg-block-done-criteria]], nur diesmal vor dem Bauen gefunden statt danach.

**Ein Stolperstein, der es in sich hat:** `ItemModule` heißt als einziges `notifyReloaded()` statt
`applyReloadedConfig()`. Wer die Reihe abarbeitet und dabei der Namensregel folgt, übersieht genau
ein Modul — und zwar das, dessen Haken zusätzlich prüft, dass alle Zonen, Arten und Namen noch
existieren. **Empfehlung: `ItemModule` auf `applyReloadedConfig()` umbenennen.** Klein, und es
beseitigt eine Falle statt sie zu dokumentieren.

**Offen, ausdrücklich:** ob wirklich *alle* Module den Handle halten statt beim Start einmal
`.get()` einzufrieren, beweist erst der erste echte Neuladen. Das ist der eigentliche Wert dieser
User Story.

---

## 6. Items geben

**Entscheidung:** `ItemStackFactory.create(templateKey, amount)` → `Optional<ItemStack>`.

Vorhanden in `rpg-platform/.../item/ItemStackFactory.java`. Ein leeres `Optional` bedeutet
„Vorlage unbekannt" und ist die Meldung aus FR-015. `render(stack)` und `templateOf(stack)` liegen
daneben. Vorschlagsquelle für die Tab-Completion sind die Schlüssel aus `Items`.

Damit ist FR-016 (nicht von Beute zu unterscheiden) keine Anforderung an neuen Code, sondern die
Folge davon, denselben Erzeuger zu benutzen wie der Beutepfad.

**Volles Inventar (FR-017):** B11 hat die Regel in US7 bereits entschieden und gebaut. B14 ruft sie
auf und erfindet nichts.

---

## 7. Kreaturen setzen — und die Budgetfrage

**Entscheidung:** `PaperMobPlacer.place(kind, location, zoneKey)` → `Optional<Entity>`, danach
Eintrag in die `HordeRegistry`.

**Der Haken:** `HordeRegistry.Entry` ist
`(entityId, kindKey, zoneKey, chunkKey, spawnedAt)` — **es gibt kein Feld, das eine handgesetzte
Kreatur von einer gespawnten unterscheidet.** `total()` ist ausdrücklich „gegen das serverweite
Budget" dokumentiert, `countIn(zoneKey)` ebenso.

FR-019a verlangt getrennte Zählung. Zwei Wege:

1. **Ein Feld an `Entry`** (etwa `Origin origin` mit `BUDGET` / `ADMIN`), und `total()`/`countIn()`
   zählen nur `BUDGET`, dazu ein `countAdmin()`. Ehrlich, an einer Stelle, aber es ändert B10s
   öffentliche Bauart — **ADR-pflichtig**.
2. **Ein zweiter Bestand** neben der Registry. Vermeidet die Änderung an `Entry`, aber B10s
   Budgetrechnung müsste ihn trotzdem kennen — also dieselbe Berührung, nur verteilt auf zwei
   Stellen und mit der Gefahr, dass die beiden auseinanderlaufen.

**Empfehlung: Weg 1**, mit ADR. Weg 2 kauft nichts ein und bezahlt mit einer zweiten Wahrheit —
genau das, wovor `MobKinds` im eigenen Javadoc warnt und was ADR-050 gerade erst bereinigt hat.

### Eine Korrektur an FR-019b

Die Spec verlangt, die Trennung der Zählung müsse **einen Neustart überleben**. Das ist
gegenstandslos: Die `HordeRegistry` wird beim Start nicht wiederhergestellt, und ADR-050 entfernt
jede getaggte Kreatur, die nicht darin steht, beim nächsten Chunk-Laden. **Nach einem Neustart lebt
also keine handgesetzte Kreatur mehr**, und beide Zählungen stehen ohnehin wieder auf null. Es
braucht keine Kennzeichnung im PDC und keine Persistenz. FR-019b ist entsprechend zu kürzen — die
Unterscheidung lebt ausschließlich im Speicher, so lange der Server läuft.

---

## 8. Stufe, Werte und Klasse — hier schrumpft der Umfang

**Stufe und Erfahrung: vorhanden.**
`Progression.setProgress(actorId, characterId, level, xpInLevel)` ist genau der Admin-Weg und
schreibt laut Javadoc „alten und neuen Stand" ins Audit-Log (**B06**-FR-024b). `/xp` benutzt ihn
schon.

**Klasse: vorhanden.**
`ClassSelection.choose(...)` → `CompletableFuture<ClassSelectionResult>`. FR-024 verlangt, dass der
Admin-Wechsel denselben öffentlichen Weg nimmt — der ist damit erfüllt, ohne neuen Code. Achtung:
`CompletableFuture`, also Rückgabe in den Tick über den Scheduler (Prinzip I).

**Attribute setzen: geht nicht, und sollte nicht.**

`StatEngine` ist „the public interface of B04 — the only way in" und hat **keine Methode, einen
Attributwert zu setzen**. Werte sind *abgeleitet*: Eingang sind `ModifierSet`s je Quelle, und
`SourceKind` ist ein geschlossener Aufzählungstyp aus sechs Werten — `CLASS`, `LEVEL`, `EQUIPMENT`,
`BUFF`, `AURA`, `ZONE`. Dessen **Deklarationsreihenfolge ist zugleich die Summationsreihenfolge**,
ausdrücklich, damit „gleiche Quellen, gleiche Zahlen" eine Eigenschaft ist und kein Zufall
(**B04**-FR-016). Das Interface sagt selbst: „changing anything here is ADR-worthy from now on."

Ein Admin-Attributwert bräuchte also einen **siebten `SourceKind`**, eine Position in der
Summationsreihenfolge — und wäre trotzdem nur ein Beitrag im Speicher, der beim nächsten Anmelden
verschwindet, weil B04 Beiträge nicht persistiert.

**Empfehlung: FR-024 auf Stufe, Erfahrung und Klasse kürzen.** Ein direktes Setzen von Attributen
gehört nicht in B14 — es verspricht Dauerhaftigkeit, die es nicht liefern kann, und bezahlt dafür
mit einem Eingriff in das Herzstück von B04. **Das ist eine Einschränkung gegenüber der
beantworteten Frage 3c und braucht Sandros Bestätigung.**

---

## 9. Fremde Spielerdaten ansehen

**Entscheidung:** B12s Muster übernehmen, nicht neu erfinden.

`CoinsCommand` löst Namen heute mit `server.getPlayerExact(name)` auf — **nur online**. FR-026
verlangt auch offline. B12 hat das in `RpgPlugin` bereits gelöst, mit einem Kommentar, der die
Falle benennt:

```java
org.bukkit.OfflinePlayer found = getServer().getOfflinePlayer(name);
return found.hasPlayedBefore() || found.isOnline()
        ? Optional.of(found.getUniqueId())
        : Optional.empty();
```

`hasPlayedBefore()` trennt einen echten Namen von einem Tippfehler, der sonst als leeres Profil
durchginge. **Das gehört ins Gerüst als Argumenttyp „Spieler"**, damit es nicht ein drittes Mal
abgeschrieben wird.

---

## 10. Rate-Limits: wirklich neu

**Geprüft:** In `rpg-core/src/main/java` und `rpg-platform/src/main/java` gibt es **keine**
wiederverwendbare Begrenzung. `RetargetThrottle` ist B10s Zielsuche und nicht übertragbar;
`InteractRateLimitTest` prüft trotz seines Namens das Verhalten der Wegkristalle („the first click
only unlocks"), keinen Begrenzer.

**Entscheidung:** eine kleine, speicherinterne Begrenzung je Absender und Kommando —
zeitstempelbasiert und lazy ausgewertet, wie Prinzip II es für Cooldowns vorschreibt, **kein**
wiederkehrender Task. Die Sperrzeit steht in der Konfiguration (Prinzip V). Für die Konsole gilt
sie nicht.

---

## Was daraus für den Plan folgt

1. **Zuerst der Beweis, dann der Umbau.** Ein Wegwerf-Kommando auf dem echten Server, bevor eines
   der sechs angefasst wird — die Frage aus Abschnitt 1 ist mit keinem Test zu klären.
2. **`rpg-core` bleibt unberührt.** Kein neues Interface, keine neue Abhängigkeit.
3. **Zwei ADRs zeichnen sich ab**: die Herkunft in `HordeRegistry.Entry` (Abschnitt 7) und die
   Absage an das Setzen von Attributen (Abschnitt 8).
4. **Zwei Korrekturen an der Spec** sind fällig: FR-019b kürzen (kein Neustart-Überleben nötig) und
   FR-024 auf Stufe, Erfahrung und Klasse begrenzen.
5. **Eine Aufräumarbeit nebenbei**: `ItemModule.notifyReloaded()` heißt um, `TrashCommand` zieht um.
