# Phase 0 · Research — B08b · Währung & Konto

Acht Fragen mussten vor dem Entwurf beantwortet werden. Sechs sind entschieden, eine bleibt bis zum
ersten Bau gegen das echte Paper-Artefakt offen (R1c), eine ist eine Entscheidung des Auftraggebers
beziehungsweise der Constitution-Pflege (R8).

---

## R1 · Woraus besteht ein Coin-Haufen?

**Entscheidung:** Ein gewöhnliches Vanilla-`Item`-Entity mit einem Datencontainer-Eintrag.

**Begründung.** Drei Anforderungen fallen damit ohne eigenen Laufzeitcode heraus:

| Anforderung | Vanilla liefert |
|---|---|
| FR-027 · nur der Berechtigte hebt auf | `Item.setOwner(UUID)` — andere Entities können den Stapel nicht aufnehmen |
| FR-029 · Verfall nach einer Frist | Der Vanilla-Verfall räumt liegengebliebene Gegenstände ab |
| FR-030 · keine wiederkehrende Aufgabe je Haufen | Der Server tickt Item-Entities ohnehin; der Block plant nichts |

Dazu Darstellung, Physik, Chunk-Verhalten und ein Aufhebe-Ereignis, an das sich hängen lässt.

**Der ausschlaggebende Punkt war der Sweep.** Eine eigene Haufenverwaltung hätte einen gebraucht, um
Verfall abzuräumen. Der einzige vorhandene ist `startAbilitySweep` in `RpgPlugin` — und der läuft über
`runAsyncDelayed`, also **ausserhalb des Ticks**, wo die Bukkit-API nicht angefasst werden darf
(Prinzip I). Ein Entity zu entfernen ist genau das. Es wäre also ein **zweiter** Sweep entstanden,
diesmal ein synchroner, und `Scheduler` bietet bewusst keine wiederkehrende synchrone Aufgabe — deren
Ergänzung wäre eine Erweiterung der Abstraktion gewesen, mit demselben Gewicht wie ADR-010 und
ADR-024, für einen Zweck, den Vanilla umsonst erledigt.

**Geprüfte Alternativen:**

- **Eigene Haufenverwaltung mit Display-Entity**: verworfen. Verfall, Physik und Sichtbarkeit wären
  alle selbst zu bauen, dazu der eben beschriebene zweite Sweep.
- **Sofort gutschreiben, nur eine Animation zeigen**: verworfen. Widerspricht der Entscheidung des
  Auftraggebers und FR-019 — der Kill soll die Gelegenheit sein, nicht die Gutschrift.
- **Vanilla-Erfahrungskugeln zweckentfremden**: verworfen. Sie kennen keinen Besitzer, tragen keinen
  Datencontainer, und ihr Einsammeln füllt die Vanilla-Erfahrungsleiste, die ADR-003 bereits für die
  Anzeige eigener Werte belegt hat.

### R1b · Wie kommt der Betrag an den Haufen?

**Entscheidung:** Im `PersistentDataContainer` des Stapels, nach dem Muster von `BoundItemTag` aus
B07 — dieselbe Begründung, die dort steht: Lore ist Darstellung, und Darstellung kann ein Client zum
Lügen gebracht werden. Der Container trägt drei Werte: **Betrag**, **Charakterkennung** (R3) und eine
**eindeutige Haufenkennung** (R2).

### R1c · Wie wird die Verfallszeit gesetzt? — *geprüft am 2026-08-22*

**Ergebnis:** Es gibt **keinen** Verfalls-Setter. `org.bukkit.entity.Item` bietet
`setPickupDelay`, `setOwner`, `setCanPlayerPickup`, `setCanMobPickup`, `setWillAge`,
`setUnlimitedLifetime` und `setHealth` — keine Frist. Der Weg ist deshalb der zweite: **Vorabaltern
über `Entity.setTicksLived(int)`**, das es gibt.

Vanilla räumt einen Gegenstand bei **6000 Ticks** Alter ab. Ein Haufen mit konfigurierter Frist *n*
Sekunden entsteht also mit `setTicksLived(6000 − n·20)`.

**Zwei Folgen, die in die Anforderungen gehören und nicht in einen Kommentar:**

1. **Die Frist kann die Vanilla-Lebensdauer nicht überschreiten** — 300 Sekunden. Ein höherer Wert
   wirkt nicht, und eine Zahl, die nicht wirkt, ist schlimmer als keine. Das Schema lehnt ihn mit
   einer Meldung ab, die den Grund nennt. `setUnlimitedLifetime(true)` wäre die Alternative und ist
   ausgeschlossen: dann räumte **niemand** mehr ab, und die Deckelung wäre die einzige Bremse.
2. **Die Rechnung setzt die Vanilla-Rate voraus.** Ein Betreiber kann `item-despawn-rate` serverseitig
   ändern; dann liegt ein Haufen kürzer oder länger als konfiguriert. **Die TPS-Zusage bleibt davon
   unberührt** — abgeräumt wird in jedem Fall —, nur die Zahl in `currency.yml` stimmt dann nicht
   mehr genau. Das steht als Hinweis in der Konfiguration.

### R1d · Sichtbarkeit je Spieler — *geprüft am 2026-08-22*

FR-027a verlangt, dass nur der Berechtigte seinen Haufen sieht. Im Projekt war Sichtbarkeit je
Spieler bislang nirgends benutzt, also war auch das zu prüfen statt anzunehmen.

**Ergebnis:** Beides vorhanden. `Entity.setVisibleByDefault(boolean)` macht den Haufen für alle
unsichtbar, `Player.showEntity(Plugin, Entity)` zeigt ihn genau dem Berechtigten. Dazu
`Player.canSee(Entity)`, mit dem ein Test das Ergebnis prüfen kann.

**Die Sperre gegen das Aufheben bleibt trotzdem bestehen.** Unsichtbarkeit ist Darstellung, und
Darstellung ist nie die Autorität (Prinzip VI) — ein Client, der das Entity trotzdem kennt, darf
nicht aufheben können.

---

## R2 · Warum das Zusammenlegen die gefährlichste Stelle des Blocks ist

**Entscheidung:** Vanilla-Zusammenlegen wird **ausgeschlossen**, und FR-028 wird vor dem Erzeugen
erfüllt.

**Begründung.** Vanilla legt zwei Gegenstands-Entities zusammen, wenn ihre Stapel *ähnlich* sind, und
addiert dabei die **Stückzahl**. Bei einem Betrag im Datencontainer ist das ein stiller Wertverlust:
zwei Haufen à 500 Coins ergäben einen Stapel der Grösse 2 mit dem Container-Wert 500 — der Spieler
verlöre die Hälfte, und niemand sähe einen Fehler. Genau die Sorte Fehler, die der Steckbrief bei der
Währung ausdrücklich fürchtet.

Zwei Stapel sind nur dann ähnlich, wenn auch ihre Datencontainer übereinstimmen. Eine **eindeutige
Kennung je Haufen** macht Ähnlichkeit unmöglich, und Vanilla fasst sie nie an.

Das Zusammenlegen, das FR-028 verlangt, geschieht stattdessen **vor** dem Erzeugen: ein Blick in
einen kleinen, konfigurierten Umkreis über die chunk-gebundene Umkreisabfrage des Servers — kein
lineares Durchlaufen aller Entities, wie Prinzip II es verbietet. Findet sich ein Haufen desselben
Charakters, wächst sein Betrag; sonst entsteht ein neuer. Damit ist das Zusammenlegen **eine
Addition im Container** statt eines Vanilla-Verhaltens, das man nur beobachten kann.

**Geprüfte Alternativen:**

- **Die Stapelgrösse *ist* der Betrag**: verworfen. Ein Stapel fasst 64; Beträge in die Tausende
  bräuchten Dutzende Entities je Kill — das Gegenteil von FR-026.
- **Vanilla zusammenlegen lassen und beim Aufheben aus der Stückzahl rechnen**: verworfen. Es
  funktioniert nur, solange alle Haufen denselben Einzelwert haben, und der hängt am Kreaturtyp.

---

## R3 · `Item.setOwner` kennt Spieler, ADR-011 kennt Charaktere

**Entscheidung:** Beide Sperren. Vanilla filtert grob über die Spielerkennung, der Block prüft genau
über die Charakterkennung aus dem Datencontainer.

**Begründung.** `setOwner` nimmt eine Spielerkennung — es gibt keine Vanilla-Vorstellung von einem
Charakter. Ein Spieler hat aber bis zu drei, und B03 lässt ihn zwischen ihnen wechseln. Ohne die
zweite Prüfung höbe Charakter B auf, was Charakter A verdient hat, und der Kontostand wäre nicht mehr
charaktergebunden — ein direkter Verstoss gegen ADR-011, und zwar der unauffälligen Sorte, weil er
nur beim Wechsel mitten in der Sitzung auftritt.

Die Vanilla-Sperre wird trotzdem gesetzt: sie hält fremde Spieler und Mobs fern, ohne dass ein
einziges Ereignis unseren Code erreicht. Der Block prüft nur noch den Rest.

**Verhalten bei Nichtübereinstimmung:** Das Aufheben wird abgebrochen und der Haufen bleibt liegen —
nicht entfernt, nicht gutgeschrieben. Er gehört weiterhin dem Charakter, der ihn verdient hat, und
verfällt mit seiner Frist.

---

## R4 · Die Anspruchsregel wird geteilt, nicht kopiert

**Entscheidung:** Der Anteilsrechner wird aus `XpDistributor` in ein eigenes, aufrufbares Stück
herausgelöst (`ShareCalculator`), das in **B06s** Paket bleibt. `XpDistributor` benutzt es weiter,
B08b benutzt es ebenfalls. **ADR-pflichtig**, weil es ein ausgelieferter Block ist.

**Begründung.** Die Regel steht heute in `XpDistributor` und besteht aus fünf Schritten: Betrag,
Schadensanteile aus B05 (nie neu berechnet), Gruppe als **ein** Beitragender, gleichmäßige Teilung auf
die Mitglieder in Reichweite, Bonus je zusätzlichem Mitglied — abgerundet, Rest bleibt liegen. Das ist
zu viel Verhalten, um es zu wiederholen. Zwei Umsetzungen blieben genau so lange gleich, bis jemand
eine von beiden anfasst, und die Abweichung träfe zwei Spieler derselben Gruppe unterschiedlich.

Die Herauslösung ist **verhaltensneutral**: dieselben Schritte, dieselbe Reihenfolge, dieselbe
Rundung, nur von aussen aufrufbar. B06s vorhandene Tests decken sie ab und müssen unverändert grün
bleiben — das ist die Abnahmebedingung der Aufgabe.

**Geprüfte Alternativen:**

- **Die Regel für Coins nachbauen**: verworfen, siehe oben.
- **`XpDistributor` von B08b aus aufrufen**: verworfen. Es *vergibt* Erfahrung; ein Aufruf für Coins
  hätte sie ein zweites Mal vergeben.
- **Den Rechner nach `rpg-core/currency` verschieben**: verworfen. Der Besitzer der Regel ist B06.
  Etwas zieht nicht um, weil ein zweiter Nutzer dazukommt.

---

## R5 · Zwei Aggregate, zwei Schreibmuster

**Entscheidung:** `CHARACTER_BALANCE` folgt dem Muster von `CHARACTER_PROGRESS`, `COIN_LEDGER` dem
von `AUDIT_LOG`.

**Begründung.** Die beiden schreiben grundverschieden, und der Puffer kennt beide Formen bereits:

- **Der Kontostand ist veränderlich, eine Zeile je Charakter.** Dirty-Mark auf die Charakterkennung,
  beim Flush wird der lebende Wert gelesen. Genau wie B06s Fortschritt — samt der Pflicht, den letzten
  Wert **vor** der Freigabe beiseitezulegen (ADR-015 Punkt 7), weil der Flush asynchron läuft und
  nach `release` nichts Lebendiges mehr findet.
- **Der Verlauf ist nur anfügend, viele Zeilen.** `JdbcAuditLogRepository` löst das bereits über eine
  synthetische Warteschlangen-Kennung: `append` reiht ein und markiert diese eine Kennung, der Writer
  leert beim Flush die Warteschlange. Kein Dirty-Mark je Eintrag, keine Zeile, die zweimal geschrieben
  wird.

**Beide brauchen die drei Eintragungen** aus ADR-015 Punkt 7 — Enum-Wert, Platz in
`FlushCycle.WRITE_ORDER`, verdrahtetes Repository. Beide stehen **nach** `CHARACTER`, weil beide auf
einen Charakter verweisen.

**Migrationsnamen:** Flyway-Versionen sind numerisch; `V8b_1` wäre kein gültiger Name. Der Block
schreibt deshalb im Zahlenraum von B08 weiter — `V8_2__character_balance.sql` und
`V8_3__coin_ledger.sql` —, womit B09 seinen eigenen Raum behält.

**Aufbewahrung.** FR-038 verlangt eine konfigurierbare Frist für Buchungen aus dem Spielgeschehen und
nimmt Eingriffe des Betreibers aus. Bei 800 Mobs wird diese Tabelle binnen Wochen die grösste des
Projekts. Das Aufräumen läuft im Autosave-Zyklus mit, nicht als eigene Aufgabe. Der **Standardwert**
ist Betriebssache und in Phase 2 zu setzen.

---

## R6 · Wie der `cost`-Block ausgelesen wird, ohne B07 anzufassen

**Entscheidung:** Die Auflösung geschieht in B08b. B07 bleibt unverändert.

**Begründung.** `ClassSourceInvariantsTest` verbietet die Wörter `coins`, `Coins`, `price` und `Price`
in B07s Quellen, und `ClassConfigSchema` prüft den `cost`-Block ausdrücklich nur darauf, dass er eine
Map ist. Das ist kein Hindernis, sondern die Bauanleitung: B07 reicht die Map durch, B08b legt sie
aus. Der Invariantentest bleibt grün, ohne dass jemand ihn anfassen muss — er beweist ab jetzt, dass
die Auflösung am richtigen Ort geschieht.

**FR-050** (ein unbekannter Schlüssel im `cost`-Block ist ein Startfehler) entsteht damit als eigene
Startprüfung **hier**: B08b kennt die zulässigen Schlüssel, B07 nicht. Die Prüfung läuft über alle
Ausrüstungsstufen aller Klassen, sobald beide Konfigurationen geladen sind, und meldet Schlüssel,
Klasse und Stufe im Fehlertext.

**Bei B08 ist genau ein Test umzudrehen.** `AbilityRankTest` sichert heute ausdrücklich zu, dass
`RankResult` **kein** `NOT_ENOUGH_COINS` enthält, mit der Begründung, es gebe keine Währung. Das war
richtig und ist ab diesem Block falsch. Die Zusicherung wird **umgedreht statt gelöscht**, damit die
Änderung im Test sichtbar bleibt und nicht als verschwundene Zeile in einem Diff untergeht. Ebenso
das Javadoc von `RankResult` (FR-055). `AbilitySourceInvariantsTest` verbietet nur Bukkit-Pakete und
steht dem nicht im Weg.

**Die Kostenprüfung läuft zuletzt** (FR-052): erst Freischaltung, erst Höchstrang, **dann** das Geld.
Sonst zahlte ein Charakter am Höchstrang für nichts.

---

## R7 · Das Admin-Kommando, das Fenster und der Schichtbruch

**Entscheidung:** Ein vorläufiges Kommando in `rpg-plugin` als dünne Schale über `CurrencyAdmin`,
**und ein Fenster** in `rpg-platform` für Stand und Verlauf. Festgehalten in **ADR-028, angenommen am
2026-08-22** — vor Beginn der Umsetzung, wie die Governance-Regel es verlangt.

**Gelesen wird geklickt, geschrieben getippt.** Ein Betrag lässt sich in einem Inventar nicht sinnvoll
eintippen; ihn über Knöpfe zusammenzuklicken wäre eine Zahleneingabe, die wie eine Oberfläche
aussieht. Lesen dagegen ist genau das, wofür ein Fenster taugt — ein Verlauf über Hunderte Zeilen ist
im Chat unlesbar. Also: `/coins` öffnet das Fenster, `set`/`add`/`remove` bleiben Argumente.

**Für das Fenster ist die Abweichung kleiner, als sie zunächst aussah.** B07 hält seine
Klassenauswahl (`ClassSelectionMenu`, `ClassSelectionListener`) bereits im eigenen Block — Oberfläche
beim Block ist gängige Praxis hier, und B13 besitzt HUD und Mehrsprachigkeit, nicht jedes Inventar.
Das Fenster folgt demselben Muster: reine Vanilla-Materialien (ADR-005), alle Texte über `Messages`.
Neu ist allein die **Seitenblätterung** — dafür gibt es im Projekt noch kein Vorbild, und `CoinLedger`
braucht deshalb einen **Versatz** statt nur eines Limits.

**Begründung.** Der Verstoss ist echt und wird nicht wegdefiniert: Kommandos, Rechtebaum und
Tab-Completion gehören B14, und B14 ist Schicht 3 und hängt von allen ab. Im gesamten Projekt
existiert bislang kein einziges Kommando; `plugin.yml` hat keinen `commands`-Block.

Die Fähigkeit ist trotzdem nicht verhandelbar — der Auftraggeber hat sie für den Betrieb angefordert,
und eine Schnittstelle ohne Aufrufweg wäre vorhanden und unbenutzbar. Der Verstoss wird deshalb
**eingegrenzt statt vermieden**:

- Die gesamte Logik liegt in `CurrencyAdmin` in `rpg-core` — berechtigungsfrei, bukkitfrei, serverfrei
  prüfbar.
- Das Kommando parst Argumente, prüft die Berechtigung und ruft. Sonst nichts.
- B14 ersetzt die Schale und lässt die Schnittstelle stehen (FR-046).

**Offline-Charaktere** (FR-042) sind der Punkt, an dem der Eingriff die Datenbank direkt anfasst.
Das ist zulässig: Prinzip II verbietet Datenbankzugriff **je Spielereignis**, und ein Admin-Kommando
ist keines — B14 sieht für genau solche Kommandos ausdrücklich Rate-Limits vor. Ist der Charakter
**online**, muss der Eingriff im maßgeblichen Cache wirken, sonst überschreibt ihn der nächste Flush
(Prinzip IV). Beide Wege sind zu bauen, und welcher gilt, entscheidet nicht der Aufrufer, sondern der
Block.

**Jeder Eingriff schreibt zweimal:** in den Verlauf (FR-040) und in das Audit-Log aus B02 (FR-041).
Kein Duplikat ohne Grund — der Verlauf beantwortet „was geschah mit diesem Konto", das Audit-Log
beantwortet „was hat dieser Betreiber alles getan". `AuditEntry` trägt Verursacher, Aktion,
betroffenen Spieler und eine freie Detailkarte und passt ohne Änderung.

---

## R8 · Wird der Block lasttestpflichtig? — *Entscheidung ausstehend*

**Sachlage.** Prinzip VII nennt zwei Blöcke namentlich: B05 (Kampf-Pipeline) und B10 (Mobs &
Horden-Spawning). B08b ist nicht genannt — der Steckbrief entstand, als der Block nur buchen sollte.

Mit der Entscheidung des Auftraggebers setzt er ein Entity je Kill in die Welt. Bei den 800 aktiven
Mobs aus dem Erfolgskriterium ist das dieselbe Grössenordnung, aus der B10 seine Nennung bezieht, und
die Deckelung aus FR-030 ist genau deshalb eine Anforderung geworden.

**Empfehlung:** aufnehmen. Ein Lasttest, der die Haufen mitmisst, ist ohnehin nötig, um SC-006 zu
belegen; ihn nicht als Pflicht zu führen hiesse, ein Erfolgskriterium ohne Nachweis zu lassen.

**Wirkung, falls aufgenommen:** eine Ergänzung von Prinzip VII in der Constitution — MINOR nach ihrer
eigenen Versionsregel, weil eine bestehende Vorgabe wesentlich erweitert wird — und eine
Abnahmebedingung mehr für diesen Block.

**Nicht in `/plan` zu entscheiden**, weil es die Constitution ändert und nicht den Block. Vorgelegt
beim Abschluss der Planung.
