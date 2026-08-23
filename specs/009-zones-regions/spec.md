# Feature Specification: B09 · Zonen & Regionen

**Feature Branch**: `009-zones-regions`

**Created**: 2026-08-23

**Status**: Draft

**Input**: Blocksteckbrief `blocks/B09-zones-regions.md` in der Fassung vom 2026-08-23, in der alle
offenen Fragen geschlossen wurden — sechs benannte Regionen über die Levelbänder 1–60, jede mit einem
Schutzkern um ihren Spawn, Geometrie als Quader mit Chunk-Index, **Wegpunkt-Kristalle** zum Reisen
(freischaltbar, gegen Coins, mit Auswahlfenster — bei `/clarify` an die Stelle der zuerst
beschlossenen Config-Portale getreten), Warnung statt Sperre unter dem Levelband, PvP als Zonenregel,
und der Kampf-Logout als Tod. Hängt ab von **B01**
(Konfiguration mit Schema-Validierung, Ereignisbus, Scheduling-Abstraktion), **B02** (Persistenz der
Freischaltungen), **B03** (`Character`,
An- und Abmeldung), **B05** (`DamagePermission` über `CombatPipeline.setPermission`, `CombatState`,
`DeathCause`, `CombatDeathEvent`), **B06** (Levelabfrage für das Levelband) und **B08b** (Kontostand,
Kostenprüfung und Buchung des Reisepreises). Löst Schnittstellen
ein, die **B08** (`WorldCondition.isOpenWorld`, FR-052b dort) und **B04** (`SourceKind` für
zonengebundene Effekte) bereits verdrahtet bereithalten. Wird benötigt von **B10** (Spawn-Bereiche)
und **B13** (Zonenanzeige). Verbindlich: **ADR-006** (Kontinent-Welt plus
Instanzwelten; eine `Zone` ist niemals eine `World`), **ADR-030** (ein Logout im Kampf ist der Tod,
angenommen am 2026-08-23), **ADR-032** (Wegpunkt-Kristalle: Eingabe, Fenster, Persistenz und ein
neuer Buchungsgrund in einem Schicht-2-Block — zu schreiben vor Beginn der Umsetzung), **ADR-027**
(Preise stehen bei dem, der sie verlangt; kein zentraler Katalog), **ADR-011** (alles hängt am
Charakter, nicht am Account), Prinzip II (räumlicher Index statt linearer Suche, keine wiederkehrende
Aufgabe je Spieler, ≤ 5 ms Tickbudget), Prinzip III (`rpg-core` ohne Bukkit, `Zone` ≠ `World`,
Zugriff nur über die Blockschnittstelle), Prinzip V (Zonen vollständig konfigurationsdefiniert,
Fail-Fast bei Schemafehlern, keine hartcodierten Spielertexte), Prinzip VI (der Server ist alleinige
Autorität), Prinzip VIII (Dokumentation deutsch, Bezeichner und Spielertexte englisch).

## Clarifications

### Session 2026-08-23 — vor `/specify`, festgehalten im Steckbrief und in ADR-030

- Q: Wie viele Zonen zum Start, und wie heißen sie? → A: **Sechs benannte Regionen** mit den
  Levelbändern 1–10 *The Greenfields*, 11–20 *The Dustlands*, 21–30 *The Safari Plains*, 31–40 *The
  Terracotta Canyons*, 41–50 *The Darkforest*, 51–60 *The Pale Wilds*. Das ersetzt die vorläufige
  Antwort „4–5 Zonen" vom 2026-08-19. Die sechs Bänder decken 1 bis 60 lückenlos ab, also genau den
  Bereich, den die Kurve in `progression.yml` aufspannt — der höchste Schlüssel dieser Kurve *ist*
  die Maximalstufe, bewusst keine Konstante im Code. Weitere Regionen folgen per Konfiguration.

- Q: Safe-Zone und Danger-Zone — zwei Zonen oder eine mit Kern? → A: **Eine Zone mit Schutzkern.**
  Die Region *ist* die Zone; der Schutzkern um den Spawn liegt als innerer Bereich darin und
  überschreibt einzelne Regeln. `zoneAt()` gibt damit immer die Region zurück, das HUD zeigt „The
  Greenfields" und nicht „Greenfields Safe", und Levelband wie Respawn-Punkt hängen an genau einem
  Objekt. Das Verlassen des Spawns ist ein eigenes, leichteres Ereignis und **kein** Zonenwechsel —
  sonst feuerte der Wechsel zwölfmal statt sechsmal, und jeder Verbraucher müsste erst zum
  Elternteil hochlaufen, um Name und Levelband zu finden.

- Q: Zonengeometrie — Quader, Polygon oder Chunk-Menge? → A: **Quader-Mengen mit Chunk-Index.** Ein
  Bereich ist eine Liste von Quadern, zwei Ecken je Quader, Y optional unbegrenzt. Beim Laden
  stempelt jeder Quader die von ihm berührten Chunks in eine Abbildung `Chunk → Zone`; die Abfrage
  ist im Normalfall ein Lookup, und nur wenn ein Chunk von zwei Bereichen berührt wird, folgt ein
  exakter Quadertest.

  *Warum nicht Polygon:* organische Grenzen wären schöner, kosten aber eine teurere Prüfung, brauchen
  denselben Index trotzdem, und jeder Stützpunkt müsste von Hand als Koordinate gepflegt werden.

  *Warum nicht reine Chunk-Menge:* der Lookup wäre einen Schritt kürzer, aber bei bis zu
  10.000×10.000 Blöcken ist eine Chunk-Menge von Hand nur als Chunk-Rechteck pflegbar — also
  faktisch ein Quader in gröberem Raster, mit auf 16 Blöcke gerundeten Grenzen. Für eine Region
  belanglos, für einen Schutzkern um den Spawn zu grob.

  *Was der Entscheid offenhält:* die Schnittstelle verspricht nur `contains(worldId, x, y, z)`.
  Polygone bleiben damit nachrüstbar, ohne dass ein Verbraucher etwas merkt.

- Q: Reisesystem — Laufen, Portale, Wegpunkte, Kosten? → A: **Portale in den Safe-Zones, kostenlos.**
  **⚠ Überholt am selben Tag bei `/clarify`** — ersetzt durch Wegpunkt-Kristalle gegen Coins
  (ADR-032, siehe Session-Eintrag weiter unten). Der Eintrag bleibt stehen, weil die Begründung
  festhält, was der Wechsel gekostet hat: die alte Fassung hielt B09 in seiner Schicht.
  In der Konfiguration ist ein Portal nur ein weiterer Quader mit Zielkoordinate — dieselbe
  Geometrie, dieselbe Datei, kein neues Teilsystem. Zugleich der natürliche Eingang, falls später
  doch eine Instanzwelt dazukommt.

  *Warum keine Wegpunkte gegen Coins:* sie wären eine willkommene Coin-Senke, brauchten aber einen
  neuen Buchungsgrund in B08b — eine Änderung an einem fertigen Block —, dazu ein Auswahlfenster
  (B13-Gebiet, für das ADR-028 schon eine befristete Ausnahme brauchte) und eine Persistenz der
  Freischaltungen. Nachrüstbar, sobald B13 und B14 stehen.

- Q: Spieler unterhalb des Levelbereichs — blockieren oder warnen? → A: **Nur warnen.** Jeder darf
  jede Region betreten. Die Mobs setzen die Staffelung von selbst durch; Zurückschieben an einer
  unsichtbaren Linie bräuchte Anti-Klemm-Logik und Sonderfälle bei Teleport und Relogin.

- Q: Ersetzt B09 die PvP-Regel, und mit welchem Inhalt? → A: **Ja, je Zone schaltbar, Vorgabe aus.**
  B09 tauscht `DamagePermission` gegen eine zonenabhängige Regel und löst damit B05s FR-042 ein. Alle
  sechs Regionen starten mit `pvp: false`, im Schutzkern ist PvP immer aus. Spielerisch ändert sich
  nichts, aber der Austausch ist bewiesen statt behauptet — `SinglePermissionPointTest` bewacht genau
  diese Stelle. Echtes PvP ab Tag eins zöge Fragen nach sich, die dieser Block nicht beantworten
  kann: was ein Tod durch einen Spieler kostet, ob es Beute vom Gegner gibt, und ob die sechs Klassen
  gegeneinander überhaupt ausgewogen sind.

- Q: Wohin führt der Tod? → A: **In die Safe-Zone der Region, in der gestorben wurde**, mit
  Nachricht. Kein XP-Verlust, kein Item-Verlust — das war am 2026-08-19 beschlossen und bleibt.

- Q: Wie wird ein Logout im Kampf bestraft? → A: **Er wird wie ein Tod behandelt** (ADR-030). Wer
  innerhalb der acht Kampfsekunden aus `combat.yml` den Server verlässt, dessen Charakter stirbt; beim
  nächsten Login steht er in der Safe-Zone seiner Region und liest, warum.

  *Warum nicht härter:* der Tod kostet in diesem Spiel bewusst wenig. Wer fürs Fliehen mehr zahlt als
  fürs Sterben, bleibt stehen und stirbt — die Strafe hätte das Gegenteil dessen bewirkt, wofür sie da
  ist. Gleichstand ist die richtige Höhe: der Gewinn des Weglaufens ist, dem Tod zu entgehen; bringt
  Weglaufen genau den Tod, ist der Gewinn null.

  *Warum kein Platzhalter-Wesen, keine Coin-Strafe, kein Login-Debuff:* siehe ADR-030. Kurz — ein
  Ersatzwesen verlangt eine Kampfpipeline, die mit Offline-Haltern rechnet; eine Coin-Strafe bräuchte
  einen neuen Buchungsgrund in einem fertigen Block und trifft ungleich; ein Debuff bestraft zu einem
  Zeitpunkt, an dem der Spieler den Zusammenhang nicht mehr sieht.

- Q: Gibt es Instanzen zum Start? → A: **Nein.** Die Boss-Mobs stehen in ihrer Region, nicht in einer
  eigenen Bosswelt. Damit liefert B09 ein `WorldCondition.isOpenWorld`, das überall ja sagt — als
  **Entscheidung**, nicht als Platzhalter. Bisher war das eine benannte Lücke mit einer bewusst
  freundlichen Vorgabe; jetzt ist es eine getroffene Wahl. Separate Welten für Instanzierbares
  bleiben nach ADR-006 vorgesehen und sind über einen Kristall anschließbar, ohne dass sich das Modell
  ändert.

### Session 2026-08-23 — bei `/specify`

- Q: Wovor schützt der Schutzkern — nur vor PvP und Spawns, oder vor allem? → A: **Vollständig
  schadensfrei.** Im Kern wird jeder Schaden abgelehnt, dessen Ziel dort steht, und jeder Schaden,
  dessen Angreifer dort steht. Umweltschaden eingeschlossen. Damit heißt „safe" *hier passiert
  nichts*, nicht bloß *hier entsteht nichts*.

  **Zwei Folgen, die zusammengehören.** Weil auch aus dem Kern heraus niemand verletzen kann, gibt es
  den Kernrand als Schießstand nicht — wer aus der Sicherheit heraus angreifen will, muss die
  Sicherheit verlassen. Und weil im Kern niemand sterben kann, ist der Respawn-Punkt tatsächlich ein
  Zufluchtsort und nicht nur der Ort, an dem man wieder erscheint.

  *Was der Kern ausdrücklich nicht verhindert:* den Kampfzustand. Wer draußen getroffen wurde und
  hineinläuft, gilt die restlichen Sekunden weiter als im Kampf — mit allen Folgen für die
  Regeneration und für ADR-030. Sonst wäre der Schutzkern genau der Ort, an dem sich Fliehen wieder
  lohnt.

  *Was das an B05s ausgelieferter Regel ändert:* genau eine Sache. Ausserhalb der Kerne bleibt jeder
  der sechs Fälle unverändert; innerhalb greift eine Ablehnung, die es vorher nicht gab. Es bleibt bei
  **einer** Stelle, an der entschieden wird.

- Q: Was liefert die Zonenkonfiguration aus, solange die handgebaute Karte nicht existiert? → A:
  **Alle sechs Regionen mit vorläufigen Koordinaten — und eine Warnung bei jedem Serverstart.**

  Die Vorläufigkeit steht als **Wert** in der Datei, nicht als Kommentar, und solange der Wert
  gesetzt ist, protokolliert der Server bei jedem Start, dass die Koordinaten Platzhalter auf einer
  Testwelt sind. Das Entfernen des Wertes ändert nichts außer der Warnung — es gibt keine zweite
  Betriebsart.

  *Warum sechs und nicht eine Beispielregion:* mit nur einer Region wären die Erfolgskriterien, die
  von sechs sprechen, erst prüfbar, wenn die Karte steht — der Block wäre auf unbestimmte Zeit halb
  abnehmbar.

  *Warum eine Startwarnung und nicht nur ein Kopfkommentar:* bei T103 hat ein Kommentar nicht
  gereicht. Ein Platzhalter muss sich als solcher zu erkennen geben, und zwar dort, wo hingesehen
  wird.

### Session 2026-08-23 — bei `/clarify`

- Q: Wie wird eine Region identifiziert und benannt — technischer Schlüssel plus Message-Schlüssel,
  oder direkt über den Anzeigenamen? → A: **Technischer Schlüssel plus Message-Schlüssel.** Die
  Kennung (`greenfields`) ist das, worauf Kristalle, Spawn-Bereiche und spätere Blöcke verweisen; der
  sichtbare Name kommt aus `messages.yml`.

  **Warum das mehr als Ordnung ist:** „The Greenfields" ist ein Spielertext, und Prinzip V verlangt
  für Spielertexte ausnahmslos Message-Schlüssel. Wäre der Anzeigename gleichzeitig die Kennung,
  hinge jeder Verweis aus B10, B11 und B13 an einem Text — und ein Umbenennen der Region hätte sie
  alle gebrochen. So ändert ein neuer Name eine Zeile in `messages.yml`, und kein Verweis merkt es.
  Zugleich bleibt ADR-005s Zusage eingelöst, die Mehrsprachigkeit strukturell offenzuhalten, ohne sie
  jetzt zu bauen.

- Q: Soll B09 die Felder für Schwierigkeitsmodifikator und Loot-Zuordnung schon tragen, obwohl es sie
  nicht auswertet? → A: **Nein, weglassen.** Der Block trägt nur, was er selbst auswertet und deshalb
  auch prüfen kann. B10 ergänzt den Schwierigkeitsmodifikator, B11 die Loot-Zuordnung — jeder mit
  einer Form, die er validieren kann.

  **Warum, obwohl der Steckbrief beides als Umfang nennt:** genau dieses Muster hat das Projekt schon
  einmal Geld gekostet. B07 trug `cost: { coins: 500 }` durch, ohne es auslegen zu können; die
  Roadmap nennt es beim Namen — „reichte undurchsichtig durch" —, und erst B08b hat es mit ADR-027
  aufgelöst. Seitdem verbietet `ClassSourceInvariantsTest` die Vokabel dort ausdrücklich. Ein Feld,
  dessen Bedeutung ein Block nicht kennt, kann er nicht validieren; ein Tippfehler darin fällt dann
  erst dem übernehmenden Block auf, Monate später.

  *Was das kostet:* B10 und B11 erweitern das Zonenschema, wenn sie dran sind. Das ist ein Eingriff
  in eine Konfigurationsdatei, deren Bedeutung sie dann besitzen — also billig und am richtigen Ort.

  *Was daraus folgt:* der Umfang im Steckbrief `blocks/B09-zones-regions.md` ist um diese zwei Punkte
  geschmälert und dort nachgezogen.

- Q: Liefert B09 die Spawn-Bereiche mit aus, und ist der Boss-Bereich eine eigene Art? → A:
  **Ausliefern, ohne Rolle.** Je Region werden benannte Bereiche mit Kennung und Geometrie
  ausgeliefert — vorläufig, wie die Regionen selbst. Es gibt **keine** Unterscheidung zwischen
  normalem Bereich und Boss-Bereich.

  **Die Trennlinie:** Geometrie ist das Fach dieses Blocks, Rolle ist B10s Fach. Ein Boss-Bereich
  unterscheidet sich geometrisch von nichts — er unterscheidet sich nur darin, was darin steht, und
  das steht hier nicht. Eine Art „Boss" mitzuliefern hiesse zu behaupten, es gebe genau eine
  Boss-Sorte je Region; das ist eine inhaltliche Aussage über Mobs, die dieser Block nicht besitzt.

  *Warum nicht ganz weglassen:* dann wären die sechs Regionen leer, und niemand könnte vorführen, dass
  die Abfrage überhaupt etwas liefert. Ein Bereich ohne Rolle ist prüfbar — er liegt in seiner Region
  und ausserhalb ihres Schutzkerns, und genau das wird beim Start geprüft.

- Q: Wie lösen Portale aus — sofort beim Betreten, nach Verweilen, oder auf Interaktion? → A:
  **Keines davon. Es werden Wegpunkt-Kristalle**, wie in einem Hack'n'Slash: ein Kristall oder Stein,
  per Rechtsklick zunächst **freischaltbar**; ein weiterer Rechtsklick öffnet ein Fenster mit allen
  Kristallen. Wählbar sind nur die schon freigeschalteten; die übrigen sind **sichtbar, aber
  gesperrt**, und ein Klick darauf meldet, dass sie noch nicht freigeschaltet sind.

  **Das ersetzt die Entscheidung „Portale als Config-Quader" vom selben Tag** und ändert den Zuschnitt
  des Blocks erheblich. Drei Dinge kommen hinzu, die die Spec zuvor ausdrücklich ausgeschlossen hatte:
  eine **Eingabe** (Rechtsklick), ein **Auswahlfenster** und **dauerhafter Zustand je Charakter**.
  Eingabe und Fenster gehören B13, Persistenz gehört B02 — dieser Block greift damit über seine
  Schicht hinaus. Dafür gibt es einen Präzedenzfall und dieselbe Form der Ausnahme: **ADR-032**, nach
  dem Muster von ADR-028, befristet bis B13 und B14.

  *Warum die gesperrten Ziele sichtbar bleiben:* ein verborgenes Ziel ist kein Anreiz, es zu suchen.
  Sichtbar und gesperrt ist der Grund, überhaupt hinzulaufen.

  *Was das Modell im Kern richtig macht:* die **erste Reise bleibt erhalten**. Man muss jede Region
  einmal zu Fuß erreicht haben, bevor sie ein Ziel wird.

- Q: Kostet ein Teleport zwischen freigeschalteten Kristallen etwas? → A: **Ja, Coins je Reise.** Der
  Betrag ist konfigurierbar.

  **Zwei Folgen, die aus bereits getroffenen Entscheidungen zwingend sind und deshalb nicht neu
  verhandelt wurden.** Erstens: der Preis steht in der **Zonenkonfiguration**, bei dem, der ihn
  verlangt — ADR-027 verbietet einen zentralen Preiskatalog, und die Währungskonfiguration ist nicht
  der Ort für Reisepreise. Zweitens: die Buchung braucht einen **eigenen Grund** in B08bs Aufzählung,
  damit der Verlauf eine Reise von einem Einkauf trennt — und das ist ein Eingriff in einen
  abgeschlossenen Block, also Teil von ADR-032.

  *Was daran heikel ist und deshalb als Anforderung steht:* Buchung und Versetzung müssen zusammen
  gelten (FR-050b). Gebucht und nicht gereist ist ein Diebstahl, gereist und nicht gebucht ein
  Freifahrtschein.

## User Scenarios & Testing *(mandatory)*

### User Story 1 - Der Spieler steht in einer Region, und das System weiß es (Priority: P1)

Ein Spieler läuft über den Kontinent. Das System weiß zu jedem Zeitpunkt, in welcher Region er sich
befindet und ob er im Schutzkern steht — schnell genug, dass die Antwort in jedem Tick für alle
Spieler zu haben ist. Überschreitet er eine Regionsgrenze, feuert genau ein Ereignis. Verlässt er den
Schutzkern seiner Region, feuert ein zweites, leichteres Ereignis, das **kein** Zonenwechsel ist.

**Why this priority**: Ohne diese Auskunft hat kein anderer Teil dieses Blocks eine Grundlage, und
drei ausgelieferte Blöcke warten mit verdrahteten Schnittstellen darauf. Sie allein ist schon
lieferbar: sechs Regionen aus der Konfiguration, eine Abfrage und zwei Ereignisse.

**Independent Test**: Eine Konfiguration mit sechs Regionen laden, einen Spieler über eine Grenze
laufen lassen und zählen: genau ein Zonenwechsel-Ereignis, mit alter und neuer Region. Danach den
Spawn verlassen: genau ein Schutzkern-Ereignis, kein Zonenwechsel. Der Zeitbedarf für 200 Abfragen
wird gemessen.

**Acceptance Scenarios**:

1. **Given** sechs Regionen aus der Konfiguration, **When** die Zone für eine Position innerhalb von
   *The Dustlands* abgefragt wird, **Then** wird *The Dustlands* mit Name und Levelband 11–20
   geliefert, ohne dass über alle Regionen iteriert wurde.
2. **Given** ein Spieler in *The Greenfields*, **When** er die Grenze nach *The Dustlands*
   überschreitet, **Then** feuert genau ein Zonenwechsel-Ereignis mit *Greenfields* als alter und
   *Dustlands* als neuer Region.
3. **Given** ein Spieler im Schutzkern von *The Greenfields*, **When** er den Kern verlässt, **Then**
   feuert genau ein Schutzkern-Ereignis und **kein** Zonenwechsel-Ereignis, weil er dieselbe Region
   nicht verlassen hat.
4. **Given** ein Spieler in *The Darkforest*, **When** er sich per Teleport nach *The Pale Wilds*
   bewegt, **Then** feuert der Wechsel genauso zuverlässig wie beim Laufen.
5. **Given** ein Spieler, der in *The Safari Plains* ausgeloggt hat, **When** er sich wieder
   anmeldet, **Then** ist seine Region sofort bekannt, ohne dass er sich erst bewegen muss.
6. **Given** eine Position, die in keiner Region liegt, **When** die Zone abgefragt wird, **Then**
   wird „keine Region" geliefert, und die Vorgaberegeln gelten (kein PvP, keine Warnung).
7. **Given** eine Konfiguration, in der sich zwei Regionen überlappen, **When** der Server startet,
   **Then** startet er nicht, und die Meldung benennt beide Regionen und den überlappenden Quader.

---

### User Story 2 - Die Warnung, dass es hier zu gefährlich ist (Priority: P2)

Ein Spieler auf Stufe 7 betritt *The Dustlands* (11–20). Er wird nicht aufgehalten, aber er liest
eine Meldung, dass es hier für ihn zu gefährlich ist. Betritt er später mit Stufe 15 dieselbe Region,
bleibt es still.

**Why this priority**: Es ist die erste im Spiel sichtbare Wirkung des Blocks und die
Verhaltensweise, die die Levelstaffelung überhaupt erst mitteilt. Baut allein auf US1 auf.

**Independent Test**: Ein Charakter auf Stufe 7 betritt *The Dustlands* — genau eine Meldung. Auf
Stufe 15 dieselbe Grenze — keine Meldung.

**Acceptance Scenarios**:

1. **Given** ein Charakter auf Stufe 7, **When** er *The Dustlands* (11–20) betritt, **Then** erhält
   er genau eine Warnung, und er wird nicht zurückgesetzt oder aufgehalten.
2. **Given** ein Charakter auf Stufe 15, **When** er *The Dustlands* betritt, **Then** erhält er
   keine Warnung.
3. **Given** ein Charakter auf Stufe 60, **When** er *The Greenfields* (1–10) betritt, **Then**
   erhält er keine Warnung — die Obergrenze des Bandes ist keine Schranke, nur die Untergrenze
   warnt.
4. **Given** ein Charakter auf Stufe 7 an der Grenze, **When** er innerhalb kurzer Zeit mehrfach hin
   und her läuft, **Then** wird die Warnung nicht bei jedem Schritt wiederholt.
5. **Given** ein Charakter auf Stufe 10, **When** er *The Greenfields* (1–10) betritt, **Then**
   erhält er keine Warnung — die Bandgrenze ist einschließend.

---

### User Story 3 - PvP wird zur Regel der Zone (Priority: P3)

Der Betreiber setzt `pvp: true` für eine Region. Von diesem Moment an dürfen Spieler sich dort
verletzen — überall sonst nicht, und im Schutzkern dieser Region auch nicht. Es gibt weiterhin genau
eine Stelle, an der diese Entscheidung fällt.

**Why this priority**: Es löst eine benannte Zusage aus B05 ein (FR-042 dort: „B09 replaces this line
with a per-zone rule") und wird von einem Quelltest bewacht. Am Spiel ändert sich zunächst nichts,
weil alle sechs Regionen mit `pvp: false` ausgeliefert werden — bewiesen wird die Austauschbarkeit,
nicht ein neuer Inhalt.

**Independent Test**: Mit allen Regionen auf `pvp: false` bleibt jeder Spieler-gegen-Spieler-Schlag
verboten, wie heute. Wird eine Region auf `pvp: true` gestellt, ist der Schlag dort erlaubt, in ihrem
Schutzkern und in allen anderen Regionen weiter verboten.

**Acceptance Scenarios**:

1. **Given** alle Regionen mit `pvp: false`, **When** ein Spieler einen anderen schlägt, **Then**
   wird der Schaden abgelehnt — genau wie vor diesem Block.
2. **Given** *The Pale Wilds* mit `pvp: true`, **When** ein Spieler dort außerhalb des Schutzkerns
   einen anderen schlägt, **Then** wird der Schaden zugelassen.
3. **Given** *The Pale Wilds* mit `pvp: true`, **When** der Schlag im Schutzkern derselben Region
   fällt, **Then** wird er abgelehnt.
4. **Given** eine Position außerhalb aller Regionen, **When** ein Spieler einen anderen schlägt,
   **Then** wird der Schaden abgelehnt — die Vorgabe bleibt „kein PvP".
5. **Given** die zonenabhängige Regel ist eingesetzt, **When** Mob gegen Spieler, Spieler gegen Mob,
   Umwelt gegen jeden und Mob gegen Mob **außerhalb der Schutzkerne** geprüft werden, **Then**
   verhalten sie sich unverändert zu B05s ausgelieferter Regel.
6. **Given** ein Spieler steht im Schutzkern, **When** ein Mob ihn dort angreift, **Then** wird der
   Schaden abgelehnt.
7. **Given** ein Spieler steht im Schutzkern, **When** er von dort aus einen Mob außerhalb angreift,
   **Then** wird der Schaden abgelehnt — der Kern ist kein Schießstand.
8. **Given** ein Spieler steht im Schutzkern, **When** er fällt oder ins Feuer gerät, **Then** nimmt
   er keinen Schaden.

---

### User Story 4 - Der Tod führt nach Hause (Priority: P4)

Ein Spieler stirbt in *The Terracotta Canyons*. Er erscheint in der Safe-Zone dieser Region und
liest, dass er gestorben ist. Er verliert keine Erfahrung und kein Item.

**Why this priority**: Es ist die zweite spürbare Wirkung im Spiel und die Voraussetzung für US5 —
ohne einen Respawn-Punkt je Region hat die Logout-Strafe kein Ziel.

**Independent Test**: In jeder der sechs Regionen sterben und prüfen, wo man wieder auftaucht. In
einem Gebiet ohne Region sterben und prüfen, dass der Ausweichpunkt greift.

**Acceptance Scenarios**:

1. **Given** ein Spieler in *The Terracotta Canyons*, **When** er stirbt, **Then** erscheint er in
   der Safe-Zone dieser Region und erhält die Todesmeldung.
2. **Given** ein Spieler außerhalb aller Regionen, **When** er stirbt, **Then** erscheint er am
   konfigurierten Ausweichpunkt.
3. **Given** ein Spieler stirbt, **When** danach sein Stand geprüft wird, **Then** ist seine
   Erfahrung unverändert und sein Inventar vollständig.
4. **Given** eine Region wurde nach dem Tod aus der Konfiguration entfernt, **When** der Spieler
   erscheint, **Then** greift der Ausweichpunkt, statt dass die Anmeldung fehlschlägt.

---

### User Story 5 - Wer im Kampf verschwindet, stirbt (Priority: P5)

Ein Spieler kämpft und verliert. Statt zu sterben, verlässt er den Server. Beim nächsten Anmelden
steht er in der Safe-Zone seiner Region und liest, dass er im Kampf ausgeloggt ist und deshalb
gestorben ist.

**Why this priority**: Die Regel schließt die verlässlichste Fluchtmöglichkeit im Spiel. Sie braucht
US4s Respawn-Punkt und ist deshalb danach eingeordnet, nicht weil sie unwichtiger wäre.

**Independent Test**: Innerhalb der acht Kampfsekunden ausloggen und wieder anmelden — Safe-Zone plus
Meldung. Nach Ablauf der acht Sekunden ausloggen — nichts passiert, der Spieler erscheint dort, wo er
war.

**Acceptance Scenarios**:

1. **Given** ein Spieler gilt als im Kampf, **When** er den Server verlässt, **Then** stirbt sein
   Charakter mit dem Todesgrund „Logout".
2. **Given** ein Charakter ist beim Logout gestorben, **When** der Spieler sich wieder anmeldet,
   **Then** steht er in der Safe-Zone seiner Region und erhält eine Meldung, die den Grund benennt.
3. **Given** der letzte Treffer liegt länger als die konfigurierten Kampfsekunden zurück, **When**
   der Spieler den Server verlässt, **Then** passiert nichts, und er erscheint beim nächsten
   Anmelden dort, wo er war.
4. **Given** `combat-logout: none` in der Konfiguration, **When** ein Spieler im Kampf ausloggt,
   **Then** passiert nichts — die Regel ist abschaltbar, ohne dass Code angefasst wird.
5. **Given** ein Charakter ist beim Logout gestorben, **When** die Historie geprüft wird, **Then**
   ist der Todesgrund von einem gewöhnlichen Kampftod unterscheidbar.
6. **Given** ein Spieler verliert die Verbindung, statt sie zu beenden, **When** er im Kampf war,
   **Then** gilt dieselbe Regel — es gibt keine Unterscheidung, die ein Client herbeiführen könnte.

---

### User Story 6 - Wegpunkt-Kristalle, freigeschaltet und bezahlt (Priority: P6)

In der Safe-Zone jeder Region steht ein Kristall. Der Spieler klickt ihn zum ersten Mal mit der
rechten Maustaste an und **schaltet ihn damit frei**. Klickt er ihn danach wieder an, öffnet sich ein
Fenster mit allen Kristallen der Welt: die freigeschalteten kann er wählen, die übrigen sieht er auch,
aber ein Klick darauf sagt ihm nur, dass sie noch nicht freigeschaltet sind. Wählt er ein Ziel,
kostet die Reise Coins — und er steht in der Safe-Zone der Zielregion.

**Why this priority**: Bequemlichkeit, kein Fundament — der Block ist ohne Kristalle spielbar, nur
mühsam. Zuletzt eingeordnet, obwohl es die aufwendigste Geschichte des Blocks ist: sie ist die
einzige mit Eingabe, Fenster, dauerhaftem Zustand und einer Buchung.

**Independent Test**: Mit einem frischen Charakter im *Greenfields* den Kristall anklicken (frei),
das Fenster öffnen, *Dustlands* anklicken (Meldung: noch nicht freigeschaltet), zu Fuß hinlaufen,
dort freischalten, zurückreisen — und prüfen, dass der Coinstand um den konfigurierten Betrag
gesunken ist und die Freischaltung einen Neustart übersteht.

**Acceptance Scenarios**:

1. **Given** ein Charakter hat einen Kristall noch nie angeklickt, **When** er ihn mit der rechten
   Maustaste anklickt, **Then** ist dieser Kristall für **diesen Charakter** freigeschaltet, und er
   erhält eine Meldung darüber.
2. **Given** ein Charakter hat einen Kristall freigeschaltet, **When** er ihn erneut anklickt,
   **Then** öffnet sich ein Fenster, das **alle** Kristalle zeigt — die freigeschalteten wählbar, die
   übrigen sichtbar aber gesperrt.
3. **Given** das Fenster ist offen, **When** der Charakter einen **gesperrten** Kristall anklickt,
   **Then** wird er nicht versetzt, es wird nichts gebucht, und er erhält die Meldung, dass dieser
   Kristall noch nicht freigeschaltet ist.
4. **Given** das Fenster ist offen und der Charakter hat genug Coins, **When** er einen
   freigeschalteten Kristall wählt, **Then** wird der konfigurierte Betrag gebucht, er steht in der
   Safe-Zone der Zielregion, und der Zonenwechsel ist gefeuert.
5. **Given** der Charakter hat **zu wenig** Coins, **When** er ein Ziel wählt, **Then** wird nichts
   gebucht, er wird nicht versetzt, und die Meldung benennt den fehlenden Betrag.
6. **Given** eine Freischaltung wurde erteilt, **When** der Server neu startet, **Then** ist sie noch
   da.
7. **Given** ein Account mit mehreren Charakteren, **When** ein zweiter Charakter dasselbe Fenster
   öffnet, **Then** sieht er **seine** Freischaltungen, nicht die des ersten (ADR-011).
8. **Given** ein Kristall wird aus der Konfiguration entfernt, **When** ein Charakter das Fenster
   öffnet, der ihn freigeschaltet hatte, **Then** erscheint er nicht mehr, und die Freischaltung
   schadet nicht.

---

### User Story 7 - Die wartenden Schnittstellen antworten (Priority: P7)

Drei ausgelieferte Blöcke halten Schnittstellen bereit, die heute mit einer bewusst freundlichen
Vorgabe antworten. Nach diesem Block antworten sie mit echten Zonendaten — oder ihre Antwort ist eine
festgehaltene Entscheidung statt eines Platzhalters. Zusätzlich stehen die benannten Spawn-Bereiche
bereit, aus denen B10 seine Horden setzt.

**Why this priority**: Es ist Anschlussarbeit ohne unmittelbare Wirkung im Spiel und deshalb hinten
eingeordnet — aber es ist der Grund, aus dem dieser Block vor B10 und B11 kommt.

**Independent Test**: Für jede Schnittstelle prüfen, dass sie von diesem Block bedient wird und dass
ihre Antwort begründet ist: `isOpenWorld` sagt überall ja, weil es keine Instanzen gibt; die
Spawn-Bereiche einer Region sind namentlich abfragbar; zonengebundene Effektquellen und
Zonenziel-Erfahrung sind anschließbar.

**Acceptance Scenarios**:

1. **Given** keine Instanzen in der Konfiguration, **When** `isOpenWorld` für einen Charakter in
   jeder der sechs Regionen gefragt wird, **Then** lautet die Antwort überall ja, und das ist als
   Entscheidung dokumentiert, nicht als fehlende Umsetzung.
2. **Given** eine Region mit benannten Spawn-Bereichen, **When** diese abgefragt werden, **Then**
   werden sie mit Kennung und Geometrie geliefert — und mit nichts sonst —, ohne dass dieser Block
   etwas darin spawnt.
3. **Given** die ausgelieferte Konfiguration, **When** die Spawn-Bereiche jeder der sechs Regionen
   abgefragt werden, **Then** liefert jede mehrere, sodass B10 einen Anknüpfungspunkt vorfindet.
4. **Given** ein Spawn-Bereich, der außerhalb seiner Region oder im Schutzkern liegt, **When** die
   Konfiguration geladen wird, **Then** startet der Server nicht, und die Meldung benennt Bereich und
   Region.

---

### Edge Cases

- **Position in keiner Region.** Auf einem handgebauten Kontinent bleiben Zwischenräume. „Keine
  Region" ist ein gültiges Ergebnis: keine Warnung, kein PvP, kein Respawn-Punkt — dort greift der
  konfigurierte Ausweichpunkt.
- **Überlappende Regionen, Schutzkern außerhalb seiner Region, Spawn-Bereich im Schutzkern.** Alles
  drei ist ein Konfigurationsfehler und verhindert den Start (Prinzip V). Kein Vorrangregelwerk zur
  Laufzeit — was nicht vorkommen darf, wird beim Start abgewiesen, nicht stillschweigend aufgelöst.
- **Ein Chunk gehört zu zwei Regionen.** Erlaubt, solange sich die Quader nicht überlappen: der
  Chunk-Index führt dann auf mehrere Kandidaten, und ein exakter Quadertest entscheidet.
- **Y-Achse.** Ein Quader ohne Y-Grenzen erfasst Höhle wie Himmel. Eine Region mit Y-Grenzen erfasst
  einen Spieler darüber oder darunter **nicht** — das ist beabsichtigt und die Grundlage dafür, dass
  später eine Zone über einer anderen liegen kann.
- **Konfiguration wird zur Laufzeit neu geladen.** Alle anwesenden Spieler werden neu bewertet.
  Ereignisse feuern nur für tatsächliche Änderungen; wer in derselben Region bleibt, merkt nichts.
- **Eine Region verschwindet, während jemand darin steht.** Er ist danach in keiner Region. Kein
  Fehler, keine Zwangsversetzung.
- **Tod im Schutzkern.** Kommt nicht vor: der Kern ist vollständig schadensfrei (FR-028a). Ein
  Charakter, der dort erscheint, ist dort sicher — auch dann, wenn ihn draußen noch etwas jagt.
- **Kampf im Schutzkern.** Der Kern verhindert Schaden, nicht den Kampfzustand. Wer draußen getroffen
  wurde und hineinläuft, gilt die restlichen Sekunden weiter als im Kampf — mit allen Folgen für
  Regeneration und für den Logout.
- **Logout im Kampf und im Schutzkern gleichzeitig.** Die Kampfregel entscheidet, nicht der Ort: wer
  als im Kampf gilt, stirbt. Der Kern schützt vor Schaden, nicht vor ADR-030 — sonst wäre er der Ort,
  an dem sich Fliehen wieder lohnt.
- **Logout im Kampf, während die Region gerade entfernt wird.** Der Tod gilt, der Respawn greift auf
  den Ausweichpunkt zurück.
- **Reise zum Kristall, an dem man gerade steht.** Zulässig, aber sinnlos: das Fenster zeigt den
  eigenen Standort als gewählt und nicht als Ziel — sonst zahlte jemand für nichts.
- **Der Kontostand reicht nicht.** Nichts passiert außer einer Meldung. Kein halber Vorgang: es darf
  nie gebucht sein, ohne dass gereist wurde.
- **Ein Kristall verschwindet aus der Konfiguration, während ein Charakter ihn freigeschaltet hatte.**
  Er erscheint nicht mehr im Fenster; die Freischaltung bleibt liegen und wirkt wieder, sobald der
  Kristall zurückkommt. Freischaltungen werden nicht aufgeräumt, weil ein Aufräumen einen Kristall
  endgültig verlieren liesse, den der Betreiber nur kurz herausgenommen hat.
- **Zwei Charaktere desselben Accounts.** Getrennte Freischaltungen (ADR-011). Wer mit dem Warrior
  überall war, fängt mit dem Mage wieder bei null an.
- **Rechtsklick auf einen Kristall in einer Region, die es nicht mehr gibt.** Der Kristall gehört der
  Konfiguration, nicht der Region — er funktioniert weiter, und sein Ankunftsort ist eine Koordinate,
  keine Zone.
- **Der Spieler wechselt den Charakter.** Region und Schutzkern-Zustand hängen am Charakter, nicht am
  Spielerkonto — nach dem Wechsel gilt, was für den neuen Charakter zutrifft, samt Levelband-Prüfung.
- **200 Spieler in derselben Region.** Kein Grund für eine langsamere Antwort: der Index wird nach
  Ort befragt, nicht nach Spielerzahl.
- **Ein Spieler bewegt sich innerhalb eines Chunks.** Keine Neubewertung, kein Ereignis, keine
  Arbeit.

## Requirements *(mandatory)*

### Functional Requirements — Zonendefinition und Erkennung (US1)

- **FR-001**: Das System MUSS Zonen vollständig aus versionierter Konfiguration aufbauen. Eine
  weitere Region entsteht ohne Codeänderung.
- **FR-002**: Eine Zone MUSS als `(worldId, Geometrie)` modelliert sein. Eine Zone ist **niemals**
  eine Welt, und kein Verbraucher darf aus einer Zone auf eine Welt schließen müssen.
- **FR-003**: Eine Zone MUSS eine **technische Kennung**, ein Levelband mit einschließender Unter- und
  Obergrenze und einen Regelsatz tragen. Die Kennung ist innerhalb einer Welt eindeutig; eine
  doppelte Kennung verhindert den Start.
- **FR-003a**: Der **sichtbare Name** einer Zone MUSS über einen Message-Schlüssel laufen. Die
  Konfiguration der Zone trägt keinen Spielertext, sondern nur die Kennung, aus der der Schlüssel
  folgt (Prinzip V, ADR-005).
- **FR-003b**: Jeder Verweis auf eine Zone — aus dem Ankunftsort eines Kristalls, einem Spawn-Bereich oder einem
  späteren Block — MUSS über die technische Kennung laufen, nie über den sichtbaren Namen.
- **FR-003c**: Fehlt der Message-Schlüssel zu einer konfigurierten Zone, MUSS das den Start
  verhindern. Eine Zone, deren Name im Spiel als Schlüsselzeichenkette erscheint, ist ein Fehler, der
  beim Start auffallen soll und nicht im Spiel.
- **FR-004**: Die Geometrie einer Zone MUSS eine Liste von Quadern sein, je Quader zwei Ecken, mit
  optionalen Y-Grenzen. Fehlen die Y-Grenzen, gilt der Quader über die ganze Welthöhe.
- **FR-005**: Das System MUSS die Zone zu einer Position über einen räumlichen Index ermitteln.
  Lineare Iteration über alle Zonen ist unzulässig (Prinzip II).
- **FR-006**: Der Index MUSS beim Laden aufgebaut werden, indem jeder Quader die von ihm berührten
  Chunks auf seine Zone abbildet. Zur Laufzeit ist die Abfrage ein Lookup, gefolgt von einem exakten
  Quadertest nur dann, wenn der Chunk mehrere Kandidaten trägt.
- **FR-007**: Die Zonenabfrage MUSS ein leeres Ergebnis liefern können. „Keine Region" ist ein
  gültiger Zustand und kein Fehler.
- **FR-008**: Jede Zone MUSS höchstens einen Schutzkern besitzen, beschrieben durch dieselbe
  Geometrieform wie die Zone selbst.
- **FR-009**: Der Schutzkern MUSS vollständig innerhalb seiner Zone liegen. Ein Verstoß verhindert
  den Start.
- **FR-010**: Das System MUSS zu einer Position beantworten können, ob sie im Schutzkern ihrer Zone
  liegt — ohne dass der Kern dabei als eigene Zone erscheint.
- **FR-011**: Die Zonenabfrage MUSS für eine Position im Schutzkern die **Region** liefern, nie den
  Kern.
- **FR-012**: Das System DARF NICHT zwei Zonen mit überlappenden Quadern in derselben Welt zulassen.
  Der Start scheitert mit einer Meldung, die beide Zonen und den überlappenden Quader benennt.
- **FR-013**: Die Konfiguration MUSS beim Start gegen ein Schema geprüft werden; jeder Fehler führt
  zu Fail-Fast mit einer Meldung, die die verantwortliche Stelle benennt (Prinzip V).
- **FR-014**: Zonen MÜSSEN zur Laufzeit neu geladen werden können. Danach werden alle anwesenden
  Charaktere neu bewertet.

### Functional Requirements — Ereignisse (US1)

- **FR-015**: Ein Wechsel der Zone MUSS genau **ein** Ereignis erzeugen, mit alter und neuer Zone;
  eine der beiden darf leer sein.
- **FR-016**: Das Betreten oder Verlassen eines Schutzkerns MUSS ein **eigenes** Ereignis erzeugen
  und **keinen** Zonenwechsel — die Zone hat sich dabei nicht geändert.
- **FR-017**: Ereignisse MÜSSEN auch bei Teleport, bei Anmeldung, bei Charakterwechsel und nach einem
  Neuladen der Konfiguration feuern, wenn sich die Zuordnung tatsächlich geändert hat.
- **FR-018**: Das System DARF NICHT für dieselbe unveränderte Zuordnung ein zweites Ereignis
  erzeugen.
- **FR-019**: Die Auswertung MUSS ereignisgesteuert bei Bewegung erfolgen. Eine wiederkehrende
  Aufgabe je Spieler ist unzulässig (Prinzip II).
- **FR-020**: Bewegung innerhalb desselben Chunks DARF KEINE Neubewertung auslösen, solange dabei
  keine Kerngrenze überschritten werden kann.

### Functional Requirements — Levelband und Warnung (US2)

- **FR-021**: Betritt ein Charakter eine Zone, dessen Stufe unter der Untergrenze ihres Levelbandes
  liegt, MUSS er genau eine Warnung erhalten.
- **FR-022**: Das System DARF NICHT den Zutritt verweigern, zurücksetzen oder verzögern.
- **FR-023**: Eine Stufe **über** der Obergrenze des Bandes DARF KEINE Warnung erzeugen.
- **FR-024**: Wiederholtes Überschreiten derselben Grenze in kurzer Folge DARF NICHT zu wiederholten
  Warnungen führen.
- **FR-025**: Der Warntext MUSS über einen Message-Schlüssel laufen; ein hartcodierter Spielertext
  ist unzulässig (Prinzip V).

### Functional Requirements — PvP als Zonenregel (US3)

- **FR-026**: Das System MUSS die eine ausgelieferte Schadenserlaubnis durch eine zonenabhängige
  **ersetzen**. Eine zweite Kopie der Entscheidung anzulegen ist unzulässig.
- **FR-027**: Jede Zone MUSS einen Schalter für Spieler-gegen-Spieler-Schaden tragen, Vorgabe **aus**.
- **FR-028**: Im Schutzkern MUSS Spieler-gegen-Spieler-Schaden **immer** abgelehnt werden, unabhängig
  vom Schalter der Zone.
- **FR-028a**: Der Schutzkern MUSS **vollständig schadensfrei** sein: jeder Schaden wird abgelehnt,
  dessen Ziel im Kern steht, **und** jeder Schaden, dessen Angreifer im Kern steht. Das schliesst
  Umweltschaden am Ziel ein. Im Kern kann niemand sterben, und aus dem Kern heraus kann niemand
  verletzen.
- **FR-028b**: Ein Mob, der einem Spieler in den Kern folgt, MUSS ihn dort nicht mehr treffen können.
  Ob Mobs am Hineinlaufen gehindert werden, gehört B10 — die Wirkung hängt nicht davon ab.
- **FR-029**: Außerhalb aller Zonen MUSS Spieler-gegen-Spieler-Schaden abgelehnt werden.
- **FR-030**: Alle übrigen Fälle der ausgelieferten Regel — Spieler gegen Mob, Mob gegen Spieler,
  Umwelt gegen jeden, Selbstschaden, Mob gegen Mob — MÜSSEN **ausserhalb der Schutzkerne**
  unverändert bleiben. Innerhalb eines Kerns gilt FR-028a und damit die einzige Abweichung, die
  dieser Block an der ausgelieferten Regel vornimmt.
- **FR-031**: Die sechs ausgelieferten Regionen MÜSSEN mit ausgeschaltetem PvP ausgeliefert werden.
  Am Spielgeschehen ändert dieser Block nichts; er beweist die Austauschbarkeit.

### Functional Requirements — Tod und Respawn (US4)

- **FR-032**: Jede Zone mit Schutzkern MUSS einen Respawn-Punkt in diesem Kern besitzen.
- **FR-033**: Stirbt ein Charakter in einer Zone, MUSS er am Respawn-Punkt dieser Zone erscheinen.
- **FR-034**: Stirbt ein Charakter außerhalb aller Zonen oder in einer Zone ohne Schutzkern, MUSS er
  an einem konfigurierten Ausweichpunkt erscheinen.
- **FR-035**: Der Tod MUSS eine Meldung über einen Message-Schlüssel erzeugen.
- **FR-036**: Der Tod DARF NICHT Erfahrung oder Gegenstände kosten.
- **FR-037**: Verschwindet die Zone zwischen Tod und Erscheinen, MUSS der Ausweichpunkt greifen; die
  Anmeldung DARF NICHT scheitern.

### Functional Requirements — Kampf-Logout (US5)

- **FR-038**: Verlässt ein Spieler den Server, während sein Charakter als im Kampf gilt, MUSS der
  Charakter sterben (ADR-030).
- **FR-039**: Das System MUSS für „gilt als im Kampf" den vorhandenen Kampfzustand aus B05 benutzen
  und dessen konfigurierte Dauer **lesen**. Eine zweite Zeitangabe für denselben Zweck ist
  unzulässig.
- **FR-040**: Der Todesgrund MUSS von einem gewöhnlichen Kampftod unterscheidbar sein, damit ein
  späterer Statistikblock „gestorben" und „abgehauen" trennen kann.
- **FR-041**: Bei der nächsten Anmeldung MUSS der Charakter am Respawn-Punkt seiner Zone stehen und
  eine Meldung erhalten, die den Grund benennt.
- **FR-042**: Die Regel MUSS über die Konfiguration abschaltbar sein, ohne dass Code geändert wird.
- **FR-043**: Ein Verbindungsabbruch MUSS wie ein absichtliches Verlassen behandelt werden. Es DARF
  KEINE Unterscheidung geben, die ein Client herbeiführen kann (Prinzip VI).
- **FR-044**: Der Ausrüstungsschaden, den ein Tod nach ADR-017 kostet, gehört B11 und ist hier
  **ausdrücklich nicht** enthalten. Bis dahin besteht die Strafe allein im Erscheinen am
  Respawn-Punkt — eine benannte Lücke, keine stille (Regel 5).

### Functional Requirements — Wegpunkt-Kristalle (US6)

- **FR-045**: Ein Kristall MUSS in der Konfiguration eine eindeutige **Kennung**, einen
  Auslösebereich in derselben Geometrieform wie Zone und Schutzkern, und einen Ankunftsort tragen.
- **FR-046**: Ein Rechtsklick innerhalb des Auslösebereichs MUSS den Kristall bedienen. Es DARF KEIN
  anderes Ereignis nötig sein und kein Vorbeilaufen etwas auslösen.
- **FR-047**: Ist der Kristall für diesen Charakter **noch nicht freigeschaltet**, MUSS der erste
  Rechtsklick ihn freischalten und eine Meldung erzeugen. Es öffnet sich dabei **kein** Fenster.
- **FR-048**: Ist der Kristall freigeschaltet, MUSS der Rechtsklick ein Auswahlfenster öffnen, das
  **alle** konfigurierten Kristalle zeigt — die freigeschalteten wählbar, die übrigen sichtbar und
  gesperrt. Ein verborgenes Ziel wäre kein Anreiz, es zu suchen.
- **FR-049**: Die Auswahl eines **gesperrten** Kristalls MUSS eine Meldung erzeugen und sonst nichts:
  keine Versetzung, keine Buchung.
- **FR-050**: Die Auswahl eines freigeschalteten Kristalls MUSS den konfigurierten Betrag buchen und
  den Charakter danach an dessen Ankunftsort versetzen.
- **FR-050a**: Reicht der Kontostand nicht, MUSS die Reise unterbleiben — **keine Buchung, keine
  Versetzung** — und die Meldung MUSS den fehlenden Betrag benennen.
- **FR-050b**: Buchung und Versetzung MÜSSEN zusammen gelten: es DARF NICHT vorkommen, dass gebucht
  wurde und die Versetzung ausbleibt oder umgekehrt.
- **FR-050c**: Der Preis MUSS in der Zonenkonfiguration stehen, bei dem, der ihn verlangt — **nicht**
  in der Währungskonfiguration und **nicht** in einem zentralen Preiskatalog (ADR-027).
- **FR-050d**: Die Buchung MUSS einen eigenen, unterscheidbaren Grund tragen, damit der Verlauf eine
  Reise von einem Einkauf trennt.
- **FR-051**: Jede der sechs Regionen MUSS mit einem Kristall in ihrem Schutzkern ausgeliefert werden.
- **FR-051a**: Freischaltungen MÜSSEN **je Charakter** geführt werden, nicht je Account (ADR-011),
  und einen Neustart überstehen.
- **FR-051b**: Eine Freischaltung DARF NICHT verloren gehen, wenn ein Kristall vorübergehend aus der
  Konfiguration verschwindet; sie wirkt wieder, sobald er zurückkommt.
- **FR-051c**: Ein Kristall, dessen Ankunftsort in einer unbekannten Welt liegt, MUSS den Start
  verhindern.
- **FR-051d**: Ein Ankunftsort außerhalb aller Zonen MUSS erlaubt sein und beim Laden protokolliert
  werden — Wildnis ist ein legitimes Ziel.
- **FR-051e**: Alle Texte dieser Geschichte — Freischaltung, Sperre, fehlende Coins, Fenstertitel —
  MÜSSEN über Message-Schlüssel laufen.

### Functional Requirements — Anschlüsse für andere Blöcke (US7)

- **FR-052**: Das System MUSS die vorhandene Abfrage „ist dieser Charakter in der offenen Welt"
  bedienen. Solange es keine Instanzen gibt, lautet die Antwort überall ja — als festgehaltene
  Entscheidung, nicht als unfertige Umsetzung.
- **FR-053**: Eine Zone MUSS Spawn-Bereiche tragen können, je Bereich eine innerhalb der Zone
  eindeutige **Kennung** und eine Geometrie in derselben Form wie die Zone selbst. Mehr trägt ein
  Bereich nicht.
- **FR-053a**: Ein Spawn-Bereich DARF KEINE Rolle, Art oder Absicht tragen — kein „normal" gegen
  „Boss", keine Mobliste, keine Zahl. Die Kennung ist der Anknüpfungspunkt, den B10 belegt.
- **FR-054**: Spawn-Bereiche MÜSSEN je Zone abfragbar sein. Dieser Block spawnt **nichts** darin;
  Mobarten, Attribute, Bosse und Hordenlogik gehören B10.
- **FR-055**: Ein Spawn-Bereich außerhalb seiner Zone oder innerhalb ihres Schutzkerns MUSS den Start
  verhindern. Ebenso eine doppelte Kennung innerhalb derselben Zone.
- **FR-055a**: Die ausgelieferte Konfiguration MUSS je Region mehrere benannte Spawn-Bereiche
  enthalten, damit die Abfrage vorführbar ist. Sie sind vorläufig wie die Regionen selbst und fallen
  unter dieselbe Startwarnung (FR-065b).
- **FR-056**: Eine Zone trägt **keinen** Schwierigkeitsmodifikator. B10 ergänzt ihn, wenn er die Form
  kennt, die er braucht.
- **FR-057**: Eine Zone trägt **keine** Loot-Zuordnung. B11 ergänzt sie, wenn er die Form kennt, die
  er braucht.
- **FR-057a**: Dieser Block DARF KEIN Konfigurationsfeld tragen, dessen Bedeutung er nicht kennt und
  dessen Inhalt er deshalb nicht prüfen kann. Was er trägt, validiert er auch.
- **FR-058**: Zonenkennung und Zonenereignis MÜSSEN so bereitstehen, dass B13 den Namen anzeigen kann,
  ohne dass dieser Block etwas darstellt. B13 erhält die **Kennung** und löst den Text über den
  Message-Schlüssel auf — dieser Block liefert keinen fertigen Anzeigetext.

### Functional Requirements — Rahmen

- **FR-059**: Die Zonenlogik MUSS ohne laufenden Server prüfbar sein; die Domänenschicht bleibt frei
  von Bukkit (Prinzip III).
- **FR-060**: Zugriff auf Zonen MUSS ausschließlich über die öffentliche Schnittstelle dieses Blocks
  laufen.
- **FR-061**: Alle Spielertexte dieses Blocks MÜSSEN über Message-Schlüssel laufen.
- **FR-062**: Alle Zahlen dieses Blocks — Levelbänder, Koordinaten, Schalter, Ausweichpunkt — MÜSSEN
  in der Konfiguration liegen.
- **FR-063**: Kein Datenbankzugriff je Bewegung, je Zonenwechsel oder je Warnung.
- **FR-064**: Eine Ausnahme in der Zonenauswertung DARF KEINEN Spieler in einen unklaren Zustand
  versetzen; der Fehler wird örtlich begrenzt und protokolliert (Prinzip VI).
- **FR-065**: Die ausgelieferte Konfiguration MUSS **alle sechs Regionen** mit Namen, Levelband und
  Koordinaten enthalten, damit der Block vollständig vorführbar ist. Die Koordinaten sind vorläufig,
  weil die handgebaute Karte noch nicht existiert.
- **FR-065a**: Die Konfiguration MUSS ihre Vorläufigkeit als Wert tragen, nicht nur als Kommentar.
- **FR-065b**: Solange dieser Wert gesetzt ist, MUSS der Server **bei jedem Start eine Warnung
  protokollieren**, die benennt, dass die Zonenkoordinaten Platzhalter auf einer Testwelt sind und
  keinen Kartenstand darstellen. Der Start wird davon nicht verhindert.
- **FR-065c**: Das Entfernen dieses Wertes DARF NICHTS ausser der Warnung ändern — die Vorläufigkeit
  ist ein Hinweis, keine zweite Betriebsart.

### Key Entities

- **Zone (Region)**: ein Gebiet mit **technischer Kennung**, Weltzugehörigkeit, Geometrie, Levelband,
  Regelsatz, optionalem Schutzkern, optionalen Spawn-Bereichen und einem optionalen Kristall. Die Kennung
  ist der einzige Anknüpfungspunkt für Verweise; der sichtbare Name liegt als Message-Schlüssel
  daneben und gehört nicht zur Zone selbst. Sechs Zonen werden ausgeliefert. Niemals eine Welt.
- **Geometrie**: eine Liste von Quadern mit optionalen Y-Grenzen. Dieselbe Form trägt Zone,
  Schutzkern, Spawn-Bereich und Kristall-Auslösebereich — ein Geometriesystem, nicht vier.
- **Schutzkern**: ein Bereich innerhalb einer Zone, der einzelne Regeln überschreibt: kein PvP, kein
  Mob-Spawn. Keine eigene Zone.
- **Zonenregeln**: Levelband, PvP-Schalter, Respawn-Punkt. Nur diese drei — jede ist hier auch
  ausgewertet. Schwierigkeitsmodifikator und Loot-Zuordnung fehlen bewusst (FR-056, FR-057).
- **Zonenindex**: die Abbildung von Chunk auf Zonenkandidaten, beim Laden gebaut, zur Laufzeit nur
  gelesen.
- **Spawn-Bereich**: ein benannter Bereich innerhalb der Gefahrenzone, aus dem B10 später Horden
  setzt.
- **Wegpunkt-Kristall**: Kennung, Auslösebereich, Ankunftsort und Reisepreis. Sechs davon werden
  ausgeliefert, einer je Schutzkern.
- **Freischaltung**: welcher Charakter welchen Kristall benutzen darf. Dauerhaft, je Charakter, wächst
  nur.
- **Zonenwechsel-Ereignis**: Charakter, alte Zone, neue Zone.
- **Schutzkern-Ereignis**: Charakter, Zone, betreten oder verlassen.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: Die Zonenzuordnung für 200 Spieler ist in **unter 0,5 ms** ermittelt — als
  wiederholbare Messung ohne laufenden Server, nicht als Lasttest (ADR-031).
- **SC-002**: Der Block hält im Normalbetrieb ein Tickbudget von **≤ 5 ms** ein und läuft ohne eine
  einzige wiederkehrende Aufgabe je Spieler.
- **SC-003**: Eine **siebte Region** entsteht durch Konfiguration allein — keine Codeänderung, kein
  Neubau, nur ein Neustart oder ein Neuladen.
- **SC-004**: Eine Zone wird per Konfiguration von der Kontinent-Welt in eine eigene Welt verschoben,
  ohne dass eine Zeile Code angefasst wird.
- **SC-005**: Ein Grenzübertritt erzeugt genau ein Zonenereignis — beim Laufen, beim Teleport, bei
  der Anmeldung und nach einem Neuladen der Konfiguration.
- **SC-006**: Das Verlassen des Schutzkerns erzeugt genau ein Kernereignis und **keinen**
  Zonenwechsel.
- **SC-007**: Ein Charakter unter dem Levelband erhält beim Betreten genau eine Warnung und wird
  nicht aufgehalten; ein Charakter im Band oder darüber erhält keine.
- **SC-008**: Mit ausgelieferter Konfiguration verhält sich die Schadenserlaubnis in allen sechs
  Fällen **identisch** zum Zustand vor diesem Block — nachgewiesen dadurch, dass die vorhandenen
  Tests der Kampfpipeline unverändert grün bleiben.
- **SC-009**: Eine Region auf `pvp: true` erlaubt Spieler-gegen-Spieler-Schaden dort und nirgends
  sonst, auch nicht in ihrem eigenen Schutzkern.
- **SC-010**: Ein Tod führt in jeder der sechs Regionen in den zugehörigen Schutzkern; ein Tod
  außerhalb aller Regionen führt an den Ausweichpunkt.
- **SC-011**: Ein Logout innerhalb der konfigurierten Kampfsekunden führt beim nächsten Anmelden in
  den Schutzkern, mit Meldung; ein Logout danach nicht.
- **SC-012**: Im Schutzkern gibt es **überhaupt keinen Schaden** — nicht zwischen Spielern, nicht von
  Mobs, nicht aus dem Kern heraus, nicht von der Umwelt — und keinen Spawn-Bereich. Auch dann nicht,
  wenn die Region `pvp: true` trägt.
- **SC-013**: Jede fehlerhafte Zonenkonfiguration — Überlappung, Kern außerhalb seiner Zone,
  Spawn-Bereich im Kern, Kristall-Ankunftsort in unbekannter Welt — verhindert den Start mit einer Meldung, die
  die verantwortliche Stelle benennt.
- **SC-014**: Die gesamte Zonenlogik ist ohne laufenden Server geprüft.
- **SC-015**: Kein Spielertext dieses Blocks steht im Code — der sichtbare Name einer Region
  eingeschlossen.
- **SC-017**: Eine Region umzubenennen ändert **eine Zeile in `messages.yml`**. Kein Kristall, kein
  Spawn-Bereich und kein Verweis eines späteren Blocks muss dafür angefasst werden.
- **SC-018**: Ein frischer Charakter kann von seinem Startkristall aus **kein** Ziel wählen. Nach dem
  Fußweg in eine zweite Region und einem Rechtsklick dort kann er es.
- **SC-019**: Eine erteilte Freischaltung übersteht einen Serverneustart, und ein zweiter Charakter
  desselben Accounts erbt sie **nicht**.
- **SC-020**: Bei zu geringem Kontostand bleibt Kontostand **und** Aufenthaltsort unverändert; die
  Meldung benennt den fehlenden Betrag. Es gibt keinen Zustand, in dem gebucht wurde und nicht
  gereist — oder umgekehrt.
- **SC-021**: Der Reisepreis steht in der Zonenkonfiguration. Weder `currency.yml` noch ein zentraler
  Katalog kennt ihn (ADR-027).
- **SC-022**: Der Verlauf unterscheidet eine Reise von einem Einkauf und von einer Reparatur.
- **SC-016**: Solange die Zonenkoordinaten als vorläufig markiert sind, steht bei **jedem** Serverstart
  eine Warnung im Protokoll, die sie als Platzhalter benennt. Wird die Markierung entfernt,
  verschwindet nur die Warnung — das Verhalten des Blocks bleibt gleich.

## Assumptions

- **Zwischenräume sind erlaubt.** Der Kontinent ist nicht flächendeckend in Regionen aufgeteilt.
  „Keine Region" ist ein gültiger Zustand mit Vorgaberegeln: kein PvP, keine Warnung, Ausweichpunkt
  beim Tod. Eine flächendeckende Aufteilung wäre eine Kartenentscheidung, keine Architekturfrage.
- **Der Ausweichpunkt ist konfiguriert**, nicht abgeleitet. Ein „irgendwo in der Nähe" wäre bei einem
  Tod in der Wildnis nicht vorhersagbar.
- **Die Warnung gilt je Betreten**, mit einer kurzen konfigurierten Sperre gegen Wiederholung an der
  Grenze. Eine Warnung „nur einmal je Charakter" wäre nach Wochen nicht mehr erklärbar.
- **Der Kristall ist gebaut, nicht gesetzt.** Er steht als Bauwerk in der Karte; die Konfiguration
  beschreibt nur den Bereich um ihn, in dem ein Rechtsklick zählt. Dieser Block setzt keine Blöcke und
  erkennt keinen Blocktyp — sonst hinge das Reisen daran, dass niemand den Stein abbaut.
- **Das Reiseziel ist eine Koordinate, keine Zone.** Ein Kristall verweist auf seinen Ankunftsort,
  nicht auf eine Region. Damit funktioniert er auch, wenn die Zonen sich ändern.
- **Freischaltungen werden nie automatisch entzogen.** Weder durch Tod noch durch einen Umbau der
  Konfiguration. Nur ein Admin-Eingriff könnte das, und der ist hier nicht vorgesehen.
- **Der Reisepreis ist eine Zahl je Kristall**, nicht je Entfernung. Eine Entfernungsformel wäre
  Balancing, das ohne gebaute Karte niemand herleiten kann.
- **Der Kampfzustand kommt aus B05** und wird gelesen, nicht nachgebaut. Die acht Sekunden gehören
  dort hin.
- **Zonen werden nicht persistiert.** Sie stehen in der Konfiguration; die Zuordnung eines Spielers
  wird zur Laufzeit ermittelt. Persistiert wird höchstens, dass ein Charakter beim Logout gestorben
  ist.
- **Der Schwierigkeitsmodifikator und die Loot-Zuordnung werden getragen, nicht ausgewertet.** Sie
  gehören inhaltlich B10 und B11; hier entsteht nur der Ort, an dem sie stehen.
- **Zonenziel-Erfahrung bleibt eine Schnittstelle.** Der vorhandene Erfahrungsgrund für Zonenziele
  wird nicht befüllt, weil dieser Block keine Ziele definiert.
- **Der Schutzkern wirkt über den Ort, nicht über die Beteiligten.** Geprüft wird, wo Ziel und
  Angreifer stehen — nicht, wer sie sind. Damit gilt die Regel auch für Fälle, die es heute noch nicht
  gibt, ohne dass jemand sie nachträgt.
- **Die vorläufigen Koordinaten liegen auf einer Testwelt** und sind so gewählt, dass sich die sechs
  Grenzen, die sechs Schutzkerne und die sechs Kristalle zu Fuß abgehen lassen. Sie sind keine
  Vorwegnahme des Kartenentwurfs.

## Offene Punkte für `/plan`

1. **Die Karte existiert noch nicht** — entschieden ist, wie die Konfiguration damit umgeht
   (FR-065 bis FR-065c): sechs Regionen mit vorläufigen Koordinaten auf einer Testwelt, die
   Vorläufigkeit als Wert, und eine Warnung bei jedem Start. Offen bleibt für den Plan nur, **wo**
   diese Warnung entsteht, damit sie nicht in einem Ladepfad hängt, der bei einem Neuladen zur
   Laufzeit ein zweites Mal durchlaufen wird.
2. **Wo die Geometrie wohnt.** Quader und Index sind reine Rechnung und gehören in die
   bukkit-freie Domänenschicht; die Übersetzung von einem Bukkit-Ort auf `(worldId, x, y, z)` gehört
   in die Plattformschicht. Die Schnittstelle muss so geschnitten sein, dass die Domäne keine Welt
   kennt, nur eine Welt-Id.
3. **Wie das Neuladen alle Anwesenden neu bewertet**, ohne eine wiederkehrende Aufgabe je Spieler
   anzulegen (Prinzip II). Ein einmaliger Durchlauf beim Neuladen ist keine wiederkehrende Aufgabe —
   das ist zu belegen, nicht zu behaupten.
4. **Ob der Schutzkern ein eigener Ereignistyp ist** oder ein Feld am Zonenereignis. FR-016 verlangt
   nur, dass es kein Zonenwechsel ist.
5. **Lasttestpflicht — geklärt am 2026-08-23 (ADR-031), hier nur noch zur Ausführung.** Lasttests
   sind keine Bedingung dafür, dass ein Block fertig ist; sie laufen gebündelt in B15s Phase, wenn
   die inhaltlichen Blöcke stehen. Für B09 heisst das: **SC-001 ist eine Messung, kein Lasttest**, und
   sie ist ohne laufenden Server zu erbringen — der Index ist reine Rechnung. Der Plan muss dafür eine
   wiederholbare Messung vorsehen, keine Behauptung. Was sich erst unter 150 Spielern und 800 Mobs
   zeigt, ist an B15 übergeben.
6. **Der vierte Todesgrund.** ADR-030 ist angenommen; der Wert im ausgelieferten Enum ist beim
   Planen als Eingriff in B05 zu behandeln, mit dem Compiler als Nachweis, dass jede auswertende
   Stelle den neuen Fall kennt.
7. **ADR-032 ist zu schreiben, vor Beginn der Umsetzung.** Die Wegpunkt-Kristalle bringen drei
   Abweichungen in einem: eine Eingabe und ein Auswahlfenster in einem Schicht-2-Block (B13-Gebiet,
   nach dem Muster von ADR-028 befristet), dauerhaften Zustand je Charakter (B02-Gebiet) und einen
   neuen Buchungsgrund in B08b (abgeschlossener Block). Drei Eingriffe, ein Grund — ein ADR.
8. **Wie die Freischaltungen liegen.** Ein eigener Aggregattyp oder ein Anhang am Charakter? ADR-015
   Punkt 7 verlangt für einen neuen Aggregattyp drei Eintragungen; ein Anhang wäre billiger, aber
   Freischaltungen wachsen und gehören nicht in einen Datensatz, der bei jedem Login vollständig
   gelesen wird. Zu entscheiden mit B02s Mustern vor Augen, nicht nach Gefühl.
9. **Wo der Rechtsklick abgefangen wird.** Nur in der Plattformschicht, und nur innerhalb des
   Auslösebereichs — die Domäne darf von einem Klick nichts wissen (Prinzip III). Zu klären ist, wie
   der Klick den Bereich findet, ohne bei jedem Rechtsklick im Spiel alle Kristalle zu prüfen
   (Prinzip II).
10. **Wie das Fenster mit vielen Kristallen umgeht.** Sechs passen in ein Inventar; bei zwanzig
    Regionen später nicht mehr. Der Plan soll benennen, was dann passiert, statt eine Grenze
    stillschweigend einzubauen.

## Dependencies

- **B01** — Konfiguration mit Schema-Validierung und Fail-Fast, Ereignisbus, Scheduling-Abstraktion.
- **B02** — Persistenz für die Freischaltungen der Wegpunkt-Kristalle: Migrationen, Schreib-Puffer und
  die Muster für einen Aggregattyp (ADR-015 Punkt 7).
- **B03** — `Character`, An- und Abmeldung, Charakterwechsel. Der Anknüpfungspunkt für Logout und
  Login.
- **B05** — die eine Schadenserlaubnis, die dieser Block ersetzt; der Kampfzustand samt konfigurierter
  Dauer; der Todesgrund; das Todesereignis.
- **B06** — die Levelabfrage für das Levelband.
- **B08** — die Abfrage „offene Welt", die dieser Block bedient.
- **B04** — die Effektquelle für zonengebundene Effekte, die dieser Block anschlussfähig macht.
- **B08b** — Kontostand und Buchung für den Reisepreis, samt der Kostenprüfung vor der Reise. Braucht
  dort einen neuen Buchungsgrund (ADR-032).

Wird benötigt von **B10** (Spawn-Bereiche, Schwierigkeitsmodifikator), **B11** (Loot-Zuordnung,
Ausrüstungsschaden beim Tod) und **B13** (Zonenanzeige im HUD).
