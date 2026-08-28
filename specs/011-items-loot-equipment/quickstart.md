# Quickstart · B11 · Items, Ausrüstung & Loot

**Spec:** [spec.md](./spec.md) · **Plan:** [plan.md](./plan.md) · **Verträge:**
[item-api.md](./contracts/item-api.md), [item-config.md](./contracts/item-config.md)

Drei Abschnitte, wie in jedem Block: **1** ohne Server, **2** die Wirkung der Konfiguration,
**3** auf einem echten Paper-Server. Abschnitt 3 ist der einzige, der etwas über Papers
Klassenlader, das PDC und die Sichtbarkeit je Spieler beweist — grüne Tests beweisen darüber
nichts.

---

## Abschnitt 1 · Ohne Server

```bash
./gradlew :rpg-core:test --tests 'rpg.core.item.*'
./gradlew :rpg-platform:test --tests 'rpg.platform.item.*' --tests 'rpg.platform.drop.*'
./gradlew :rpg-persistence:test --tests '*GearCondition*' --tests '*Cosmetic*' --tests '*Migration*'
./gradlew test
```

**Erwartet:** alles grün, **null übersprungene Tests**. MockBukkit meldet Nicht-Implementiertes als
*übersprungen* statt als Fehler — ein grüner Lauf, der nichts angesehen hat, ist der schlechteste
Ausgang (T110 aus B10).

### Was hier bewiesen sein muss, bevor Abschnitt 3 sinnvoll ist

| Prüfung | Anforderung |
|---|---|
| Verschleißkurve über die **ganze** Spanne, nicht nur an den Enden | FR-047, FR-048, SC-011 |
| Zustand 50 → 1,0 · 25 → 0,60 · 10 → 0,36 · 0 → 0,20 | FR-047 |
| Erlittener Schaden trifft **nur** die Rüstung, Autoattack **nur** die Waffe, Fähigkeit **keines** | FR-040, FR-041, SC-012 |
| Ein Klon nutzt gar nichts ab | FR-041a |
| Tod senkt beide, `DeathCause.ADMIN` nicht | FR-042 |
| **Der Start weist eine Konfiguration zurück, in der der Tod nicht schwerer wiegt** | FR-044, SC-013 |
| Reihum in einer Party: drei Mitglieder, neun Gegenstände, jeder genau drei | FR-026b, SC-006a |
| Abwesendes Mitglied wird übersprungen und behält seine Position | FR-026c |
| Kein Mitglied in Reichweite → zurück auf den größten Beitragenden | FR-026c |
| Beute geht an den größten Beitragenden, **nicht** an den letzten Treffer | FR-026, SC-006 |
| Ein Item trägt **nur** Vorlagen-ID und Schema-Version | FR-001, FR-004 |
| Unbekannte Vorlage → inert, nicht gelöscht, kein Startabbruch | FR-007 |
| Schema-Migration Version 1 → 2 verlustfrei | FR-005, SC-003 |
| **Kein Bezeichner einer einzelnen Vorlage im Code** | FR-013, SC-004 |
| **B07 mit `GearConditionFactor.NONE` verhält sich wie zuvor** — der Testbestand von B07 bleibt unverändert grün | Vertrag, Complexity Tracking |
| Die Rückbau-Migration bricht bei vorhandenen `item_instance`-Zeilen **ab** statt zu löschen | research.md R2 |
| Keine zweite Fassung von `BoundEquipment`, `CharacterInventory`, `EquipmentPurchase`, der Eigentumsmechanik | FR-079 |

---

## Abschnitt 2 · Die Wirkung der Konfiguration

**Ohne Server nachweisbar, mit einer geladenen `items.yml`.**

1. **Eine neue Vorlage entsteht ohne Java.** Einen vierten Trank in `templates:` eintragen, laden,
   per Befehl vergeben — er existiert mit Namen, Farbe und Wirkung (SC-002).
2. **Balancing wirkt rückwirkend.** `heal: 40.0` auf `80.0` ändern, neu laden, das **vorhandene**
   Exemplar heilt 80. Kein Inventar wurde angefasst (SC-001) — das ist die Zusage aus ADR-004,
   und der eigentliche Grund für die ganze Bauweise.
3. **Fail-Fast, jeder Fall einzeln.** Unbekanntes Material; `chance: 1.5`; ein `CONSUMABLE` ohne
   Wirkung; eine Beutetabelle, die eine Ausrüstungskennung nennt; `per-death: 0.5` gegen
   `per-damage-taken: 0.01` und `death-factor-min: 100`. Jedes Mal Abbruch mit **Datei, Schlüssel
   und Grund** (FR-012, FR-044).
4. **Nachladen tauscht im Ganzen**, wie `MobModule`.

---

## Abschnitt 3 · Auf einem echten Paper-Server

**Grüne Tests beweisen nichts über Papers `libraries:`-Klassenlader, nichts über das PDC im echten
Serialisierungsweg und nichts über `showEntity`.** Zwei Fehler dieses Projekts sind erst hier
aufgefallen, beide bei vollständig grünen Tests. B10 fand hier vier weitere (ADR-035 bis ADR-038).

**Vorbereitung:** Jar bauen und deployen. **`items.yml` muss mit** — Bukkit überschreibt vorhandene
Konfigurationen nicht, das Jar allein deployt die YAML-Änderungen **nicht** mit.

### Das Item als Datenobjekt

1. Server startet, `items.yml` wird geladen, keine Warnung im Log
2. Trank per Befehl vergeben — Name, Raritätsfarbe und Lore stimmen
3. Ausloggen, einloggen — das Item ist unverändert da
4. Serverneustart — dito
5. **`heal` in der Konfiguration ändern, neu laden, denselben Trank trinken** — die neue Wirkung
   greift, ohne dass das Inventar angefasst wurde *(die Kernzusage, SC-001)*
6. Charakter wechseln — der Trank des ersten Charakters ist beim zweiten **nicht** da
7. PDC von Hand manipulieren, Item aufnehmen — wird abgelehnt (FR-008)
8. Vorlage aus der Konfiguration entfernen, neu laden — das Exemplar bleibt, ist inert, der Server
   läuft (FR-007)

### Beute und Eigentum — der Teil, den nur zwei Spieler prüfen können

9. Mob töten, Beute fällt gemäß Tabelle
10. Mob durch Sturz oder Sonne sterben lassen — **nichts** fällt (FR-019)
11. Zwei Arten auf derselben Vanilla-Basis töten — unterschiedliche Ausbeute (FR-020)
12. Boss töten — seine eigene Tabelle, nicht die der Region (FR-023)
13. **Zweiter Spieler:** einer tötet, der andere sieht die Beute **nicht** (FR-028)
14. Der andere läuft über die Stelle — er hebt **nichts** auf (FR-029)
15. **Eigentümer loggt aus und wieder ein** — er sieht seine Beute wieder und kann sie aufheben
    *(die Falle aus `CoinPileRegistry`: `showEntity` hängt an der Verbindung)*
16. Eigentümer wechselt den Charakter — der neue sieht sie **nicht** (FR-027)
17. Zwei Spieler lassen gleichartige Beute nebeneinander fallen — sie **verschmelzen nicht** (FR-031)
18. Beute liegen lassen — sie verfällt nach Vanillas Frist, ohne für andere sichtbar zu werden
19. Volles Inventar, Mob töten — Warnung als Title plus Sound, nichts still verworfen (FR-075)

### Party — die Frage, die diesen Block umgebaut hat

20. Party aus drei Spielern, gemeinsam kämpfen: **jeder bekommt reihum** einen Gegenstand
21. Zwanzig Kills, vier Gegenstände — die **vier** verteilen sich reihum, nicht die Kills (FR-026b)
22. Ein Mitglied entfernt sich — es wird übersprungen und behält seine Position (FR-026c)
23. Ein Mitglied verlässt die Party mitten im Kampf — es ist nicht mehr in der Runde
24. Party auflösen, während Beute liegt — der Anspruch bleibt bestehen
25. Einzelspieler neben einer Party — er ist ein eigener Beitragender

### Verschleiß

26. Schaden einstecken — **nur** der Rüstungszustand sinkt
27. Mit Autoattack zuschlagen — **nur** der Waffenzustand sinkt
28. Mit einer Fähigkeit Schaden machen — **kein** Zustandswert ändert sich (FR-041)
29. Klon beschwören, ihn kämpfen lassen — **kein** Zustandswert ändert sich (FR-041a)
30. Sterben — beide sinken, deutlich stärker als durch den ganzen Kampf davor (FR-043)
31. `/kill` — **kein** Verschleiß (FR-042)
32. Zustand unter 50 drücken — die Werte sinken messbar, und die Ausrüstung **zerbricht nicht**
33. Zustand auf 0 — noch 20 % Beitrag, Item weiterhin tragbar (FR-038, SC-011)
34. Warnschwelle unterschreiten — Meldung erscheint, **einmal**, nicht bei jedem Treffer (FR-051)
35. Sterben mit vollem Inventar und voller Ausrüstung — **kein Item, keine Erfahrung** verloren
    (FR-046, SC-010)

### NPC, Reparatur, Kosmetik

36. Sechs Safe-Cores anfliegen — in jedem steht ein NPC (FR-057)
37. Zwei NPCs vergleichen — unterschiedlicher Bestand (FR-058)
38. NPC angreifen — unverwundbar; er zählt **nicht** gegen B10s Budget (FR-052)
39. Verkaufen — Coins gebucht unter `VENDOR_SALE`, Item weg (FR-060)
40. **Klassenrüstung verkaufen wollen** — abgelehnt (FR-063, SC-008)
41. Dieselbe in Enderchest legen, in den Mülleimer geben, wegwerfen — jedes Mal abgelehnt (SC-008)
42. Kaufen mit vollem Inventar — abgelehnt **vor** der Buchung (FR-064)
43. Kaufen mit zu wenig Coins — abgelehnt, Kontostand unverändert
44. **Stufe kaufen** mit ausreichendem Level und Coins — Leiter steigt um genau eine Stufe, neues
    Aussehen sofort (FR-061)
45. Stufe kaufen mit zu niedrigem Level — abgelehnt, **keine** Coins bewegt (FR-062)
46. Reparieren — nur der bezahlte Slot, gebucht unter `REPAIR` (FR-052)
47. Reparatur bei Zustand 99 gegen eine bei Zustand 5 — der Preis unterscheidet sich (FR-053)
48. Reparatur ohne Verschleiß — abgelehnt, ohne zu buchen (FR-054)
49. Aufsteigen mit verschlissener Ausrüstung — der Zustand bleibt, wie er war (FR-055)
50. **Amboss, Zauberpult, Schleifstein** mit gebundener Ausrüstung — gesperrt (FR-056)
51. Trimfarbe kaufen auf Stufe 3, anwenden wollen — abgelehnt, Besitz bleibt (FR-069)
52. Auf Höchststufe anwenden — Aussehen ändert sich, **kein Wert** ändert sich (FR-070, SC-014)
53. Zweite Farbe anwenden — ersetzt die erste (FR-071)
54. Ausloggen, einloggen — die Farbe ist noch da (FR-072)
55. NPC-Fenster offen, Zone verlassen — Fenster schließt, nichts halb gebucht (FR-065)

### Abbruch mitten im Vorgang

56. Während eines Kaufs ausloggen — **entweder ganz oder gar nicht** gebucht (FR-065, SC-009)
57. Server während eines Verkaufs stoppen — dito

**Schritte 1–57 sind das Bestehenskriterium.** Ein Lasttest gehört nach ADR-031 zu B15 und hält
diesen Block nicht offen.

---

## Was hier absichtlich nicht geprüft wird

- **Volllast** (150 Spieler, 800 Kreaturen) — B15, ADR-031
- **Die Beutetabellen als Inhalt** — welcher Trank in welcher Region wie oft fällt, ist eine
  Balancing-Frage für B16, keine Frage der Mechanik
- **Mehrsprachigkeit** — die Message-Schlüssel sind gelegt, B13 füllt sie
