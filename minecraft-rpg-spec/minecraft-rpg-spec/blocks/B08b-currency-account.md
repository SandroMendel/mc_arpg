# B08b · Währung & Konto

| | |
|---|---|
| **Schicht** | 1 — Regel-Engine |
| **Status** | Entwurf *(2026-08-22)* — eingeschoben durch ADR-027, bereit für `/specify` |
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

## Offene Fragen

- [ ] **Startguthaben** bei Charaktererstellung: null oder ein Betrag? Null heisst, die erste
      Ausrüstungsstufe muss ohne Coins erreichbar sein — sie ist es heute (`cost: {}` auf Stufe 1)
- [ ] **Wieviel wirft ein Mob ab?** Content, bei `/specify` B10 oder B11 auszuarbeiten. Hier zählt
      nur, dass es eine Quelle gibt
- [ ] **Sichtbarkeit**: Wo sieht ein Spieler seinen Stand? Das ist B13, aber die Schnittstelle
      entsteht hier
- [ ] **Verlust beim Tod?** ADR-017 sagt: kein Item- und kein XP-Verlust, aber Haltbarkeitsschaden.
      Ob Coins davon betroffen sind, ist offen

## Akzeptanzkriterien (Entwurf)

- Ein Charakter kann nicht mehr ausgeben, als er hat — auch nicht bei zwei Buchungen im selben Tick.
- Der Stand übersteht Relogin und Serverneustart.
- Zwei Charaktere desselben Spielers haben getrennte Stände.
- Eine Buchung ohne Grund ist nicht möglich.
- Keine Buchung erzeugt einen Datenbankzugriff im Spielereignis.
