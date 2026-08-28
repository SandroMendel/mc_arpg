# B08b · Währung & Konto

| | |
|---|---|
| **Schicht** | 1 — Regel-Engine |
| **Status** | **Implementiert** *(2026-08-22)* — 150 Aufgaben, davon 145 erledigt; **1609 Tests** im Projekt, 0 Fehler, 0 übersprungen (188 davon neu). Offen allein: der Durchlauf auf einem echten Paper-Server und der Lasttest, der B10 braucht. Spec unter `specs/008b-currency-account/` |
| **Abhängig von** | B02, B03, B06 |
| **Benötigt von** | B07, B08, B11, B12 |

## Warum es diesen Block gibt

Coins stehen seit dem 19.08. in der Vision, und drei Blöcke setzen sie voraus:

- **B07** schreibt `cost: { coins: 500 }` an jede Ausrüstungsstufe in `classes.yml` und liest die
  Zahl bewusst nicht aus — der Kostenblock wird als undurchsichtige Map durchgereicht
- **B08** lässt den Rangaufstieg umsonst sein, weil es nichts gibt, woran er scheitern könnte;
  `RankResult` kennt kein `NOT_ENOUGH_COINS`
- **B11** braucht sie für NPC-Verkauf und Reparatur

Keiner der drei besitzt sie. Beide bereits umgesetzten Blöcke haben die Lücke **benannt statt
gefüllt** (Workflow-Regel 5), und dieser Block füllt sie.

**Warum nicht in B11.** Der naheliegende Ort wäre der Item-Block — dort fliesst ohnehin Geld. Dagegen
spricht die Abhängigkeitsrichtung: B07 und B08 müssten dann von B11 abhängen, also Schicht 1 von
Schicht 2. Ein Kontostand hat mit Items nichts zu tun; er gehört zum Charakter, wie Level und
Erfahrung (ADR-027).

## Zweck

Ein Kontostand je Charakter, Buchungen darauf, und eine Kostenprüfung, die andere Blöcke aufrufen.

## Umfang

**In diesem Block:**

- **Kontostand je Charakter**, nicht je Konto (ADR-011). Zwei Charaktere eines Spielers haben
  getrennte Geldbeutel, wie sie getrennte Level und getrennte Fähigkeitsränge haben
- **Buchung mit Grund** — jede Änderung nennt, woher sie kommt. Ohne Grund ist eine
  Fehlbuchung nicht auffindbar, und Währung ist der Teil, bei dem Spieler sich beschweren
- **Kostenprüfung als Schnittstelle**: „kann dieser Charakter X zahlen" und „ziehe X ab, atomar".
  Prüfen und Abziehen dürfen nicht zwei Aufrufe sein, zwischen denen etwas passieren kann
- **Quellen**: Mob-Kills und Beute (B10/B11 melden), Verkauf an NPC (B11)
- Persistenz über den Schreib-Puffer wie jeder andere Aggregattyp (ADR-015: drei Registrierungen)

**Ausdrücklich nicht in diesem Block:**

- **Wofür etwas kostet.** Preise stehen bei dem, der sie verlangt: Stufenkosten in `classes.yml`,
  Rangkosten in `abilities.yml`, Reparaturkosten in B11. Ein zentraler Preiskatalog wäre ein zweiter
  Ort für Zahlen, die schon einen haben
- **Handel zwischen Spielern** — ausgeschlossen durch `00-vision-scope.md`
- **Der NPC-Händler selbst** — der gehört zu B11 (ADR-027). Dieser Block bucht nur

## Architekturvorgaben

- **Kein Datenbankzugriff je Spielereignis** (Prinzip II). Der Kontostand ist während der Sitzung im
  Cache maßgeblich und wird über den Schreib-Zyklus nachgeführt, wie Level und Fähigkeitszustand
- **Der Kontostand ist niemals negativ.** Eine Buchung, die darunter ginge, wird abgelehnt, nicht
  gekappt — eine stille Kappung wäre ein Geschenk, das niemand bemerkt
- **Eine Buchung ist atomar gegenüber der Prüfung.** Zwei Fähigkeiten im selben Tick dürfen nicht
  beide dasselbe Geld ausgeben
- Alle Beträge sind ganzzahlig. Bruchteile einer Münze sind eine Rundungsquelle ohne Nutzen

## Offene Fragen — alle vier geschlossen *(2026-08-22, bei `/clarify`)*

- [X] **Startguthaben** bei Charaktererstellung → **konfigurierbar, Standard null**, und es ist eine
      **Gutschrift bei der Erstellung** mit eigenem Verlaufseintrag, kein Wert, der beim Lesen gilt.
      Ein Charakter ohne Kontozeile hat **null**. Sonst hätte eine spätere Erhöhung der Zahl jeden
      noch unbebuchten Charakter über Nacht reicher gemacht — ohne Buchung und ohne Spur.
- [X] **Wieviel wirft ein Mob ab?** → Konfiguration in `currency.yml` (`drops.by-type` plus
      Standardwert), bis B10 den Provider über dieselbe Schnittstelle ablöst. Ein **fehlender**
      Eintrag heisst der Standardwert, nicht null; eine **ausdrückliche** Null heisst null.
- [X] **Sichtbarkeit** → Ein Fenster mit Charakterauswahl und seitenweisem Verlauf, vorläufig hier
      (ADR-028), später B13. **Drei Stände nebeneinander, nie eine Summe.**
- [X] **Verlust beim Tod?** → **Nein.** Das zieht die Linie von ADR-017 gerade durch: der Tod kostet
      Haltbarkeit und Zeit, nicht Fortschritt. Es gibt keinen Buchungsgrund dafür, und ein Test hält
      fest, dass das so bleibt.

## Was beim Umsetzen dazukam

Zwei Entscheidungen des Auftraggebers haben den Zuschnitt nach `/specify` erweitert:

- **Coins fallen, statt gutgeschrieben zu werden.** Ein Kill lässt einen Haufen liegen, den nur der
  Berechtigte **sieht** und aufheben kann; erst das Aufheben bucht. Nicht Abgeholtes verfällt.
- **Ein dauerhafter Verlauf und Admin-Eingriffe** — setzen, hinzufügen, entfernen, jeweils mit
  Verursacher, im Verlauf und zusätzlich im Audit-Log aus B02.

Daraus folgten zwei ADRs: **ADR-028** (Kommando und Fenster in einem Schicht-1-Block, befristet bis
B14/B13) und **ADR-029** (Herauslösung des Anteilsrechners aus `XpDistributor`, verhaltensneutral).

## Akzeptanzkriterien — erfüllt

- [X] Ein Charakter kann nicht mehr ausgeben, als er hat — geprüft mit 1000 nebenläufigen Abbuchungen.
- [X] Der Stand übersteht Relogin und Serverneustart — gegen echtes PostgreSQL.
- [X] Zwei Charaktere desselben Spielers haben getrennte Stände.
- [X] Eine Buchung ohne Grund ist nicht möglich — es gibt keine Signatur dafür, und ein Quelltest hält
      das fest.
- [X] Keine Buchung erzeugt einen Datenbankzugriff im Spielereignis.

## Offen

- **Der Durchlauf auf einem echten Paper-Server** (`specs/008b-currency-account/quickstart.md`,
  Abschnitt 3, 22 Schritte). Grüne Tests sagen nichts über Papers `libraries:`-Klassenlader, und zwei
  Paper-Aufrufe — `Item.setOwner` und `Entity.setVisibleByDefault` — kann MockBukkit gar nicht
  ausführen. Was serverfrei geprüft ist: **dass und wem gegenüber** wir sie verlangen.
- **Der Lasttest** für SC-006 braucht B10s Horden. Ob dieser Block überhaupt lasttestpflichtig wird,
  ist die offene Frage aus `research.md` R8 — Prinzip VII nennt B05 und B10, und mit einem Entity je
  Kill gehört B08b der Grössenordnung nach dazu.
