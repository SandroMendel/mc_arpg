# 05 · Roadmap & Spec-Kit-Workflow

## Reihenfolge

Die Blöcke werden nicht nach Attraktivität, sondern nach Abhängigkeit
abgearbeitet. B04 und B05 sind die Verträge, gegen die fast alles andere
entwickelt wird — dort steckt die meiste Spezifikationsarbeit.

```
M1 Fundament        B01 → B02 → B03
M2 Regelkern        B04 → B05
M3 Charakter        B06 → B07 → B08 → B08b
M4 Welt & Content   B09 → B10 → B11
M5 Meta             B12 → B13 → B14
quer                B15, B16, B17 ab M1 mitlaufend
```

## Meilensteine

### M1 — Fundament
**Ziel:** Server startet, Spielerdaten werden geladen und gespeichert.
**Nachweis:** 200 simulierte Joins ohne Fehler; nach `kill -9` gehen höchstens
ein Autosave-Intervall an Daten verloren.

### M2 — Regelkern
**Ziel:** Ein Spieler hat acht Attribute und kann mit eigenem Schadensmodell
Schaden nehmen und austeilen; die Herzleiste ist Prozentanzeige.
**Nachweis:** Alle Formeln unit-getestet; kein Vanilla-Schaden gelangt
ungefiltert durch; erster Lasttest.

### M3 — Charakter
**Ziel:** Klassenwahl, Level-Aufstieg, aktive und passive Fähigkeiten inkl.
Unique Class Ability sind spielbar.
**Nachweis:** Eine neue Fähigkeit entsteht rein per Konfiguration.

### M4 — Welt & Content
**Ziel:** Mehrere Zonen mit Monsterhorden und Loot; Ausrüstung wirkt auf
Attribute.
**Nachweis:** 800 aktive Mobs bei 150 Spielern halten p95 MSPT < 40 ms.

### M5 — Meta
**Ziel:** Statistiken, Leaderboards, vollständiges HUD, Admin-Werkzeuge.
**Nachweis:** Leaderboard-Aufruf durch 50 Spieler erzeugt höchstens eine
Datenbankabfrage.

## Spec-Kit-Ablauf

### Einmalig

```bash
specify init --here --ai claude
```

Anschließend `/constitution` mit dem Inhalt von `constitution.md` ausführen.

### Je Block

```
/specify    Eingabe: Inhalt von blocks/BXX-*.md
            plus die Antworten auf dessen offene Fragen
/clarify    offene Punkte auflösen, bevor geplant wird
/plan       technischer Plan; Constitution wird geprüft
/tasks      Aufgabenliste
/implement  Umsetzung
```

### Regeln für die Arbeit mit Claude Code

1. **Ein Block = eine Spec = ein Branch.** Keine blockübergreifenden Specs.
2. **Offene Fragen zuerst.** Die Liste „Offene Fragen" im Blocksteckbrief muss
   beantwortet sein, bevor `/specify` läuft. Unbeantwortete Fragen erzeugen
   erfundene Annahmen.
3. **Constitution vor Plan.** Verstößt ein Plan gegen die Constitution, wird der
   Plan geändert — nicht die Constitution.
4. **Entscheidungen wandern zurück.** Jede in der Umsetzung getroffene
   Architekturentscheidung wird als ADR in `docs/02-decisions.md` ergänzt.
5. **Kein Vorgriff.** Ein Block implementiert nichts, was zu einem späteren Block
   gehört — stattdessen wird die Schnittstelle definiert.

## Empfohlener nächster Schritt

*(Stand 2026-08-22)* B01 bis B08 sind implementiert und verdrahtet. Offen sind
dort nur noch Validierungsläufe und Lasttests, die einen echten Paper-Server
brauchen — kein Code.

Als nächstes **`/specify` für B08b (Währung & Konto)**. Der Block ist durch
ADR-027 neu entstanden und eingeschoben: Coins stehen seit dem 19.08. in der
Vision, drei Blöcke setzen sie voraus, und keiner besass sie. B07 reicht heute
`cost: { coins: 500 }` undurchsichtig durch, B08s Rangaufstieg kostet nichts,
und B11 könnte weder verkaufen noch reparieren. Beide fertigen Blöcke haben die
Lücke benannt statt gefüllt (Regel 5) — jetzt wird sie gefüllt.

B08b hängt von B02, B03 und B06 ab, **nicht** von B09, B10 oder B11. Er ist
damit sofort umsetzbar und schliesst zwei bereits ausgelieferte Blöcke ab.

**Danach B11**, dessen Neuzuschnitt mit ADR-027 abgeschlossen ist: keine offene
Frage mehr im Steckbrief. Raritätsstufen bleiben als reines Etikett, der
Roll-Mechanismus entfällt — jedes Item hat feste Attributwerte —, und der
NPC-Händler gehört hierher.

**B09/B10 schulden B08 drei Verhaltensweisen** (Aggro auf den Klon, Mobs wenden
sich von Unsichtbaren ab, Zonen für Zweites Leben). Die Schnittstellen stehen
und werden gerufen; sie antworten heute mit „nichts passiert".
