# Phase 0 — Recherche: B06 · Progression

**Datum**: 2026-08-20 | **Plan**: [plan.md](./plan.md) | **Spec**: [spec.md](./spec.md)

Acht Entscheidungen, die vor dem Entwurf geklärt sein mussten. Die Balancing-Fragen (Höhe der
Kurve, XP je Mob, Bonushöhe) stehen ausdrücklich **nicht** hier — sie sind Konfiguration und werden
beim Füllen von `progression.yml` gesetzt, nicht beim Entwerfen des Blocks.

---

## 1. Levelwachstum: `BaseStatContributor`, nicht `ModifierSet`

**Entscheidung**: Das Attributwachstum je Level wird über
`StatEngine.registerBaseStatContributor` beigetragen. `StatEngine.apply` mit einer `ModifierSet`
unter `SourceKind.LEVEL` wird **nicht** verwendet.

**Begründung**: Zwei Gründe, einer davon zwingend.

Der zwingende ist rechnerisch. `StatCalculator.compute` addiert den Beitrag eines
`BaseStatContributor` auf `definition.base()` und bildet daraus den **effektiven** Basiswert. Das
Modifikatorband wird um genau diesen effektiven Wert gelegt:

```java
double effectiveBase = definition.base() + baseBonus[i];
double raw = (effectiveBase + flat[i]) * (1.0 + percent[i]);
raw = clamp(raw, definition.bandFloor(effectiveBase), definition.bandCeiling(effectiveBase));
```

Käme das Levelwachstum stattdessen als FLAT-Modifikator, landete es in `flat[i]` — **innerhalb** der
Klammer, die um den unveränderten Level-1-Basiswert gelegt wird. Das Band „plus/minus 30 %" würde
mit jedem Level relativ enger, und die Ausrüstungsbeiträge aus B11 wären auf Level 60 messbar falsch
geklammert. `AttributeDefinition.bandFloor` nimmt den Basiswert genau deshalb als Parameter; die
Javadoc dort nennt B06 und B07 beim Namen: *„The band has to move with the base it belongs to - a
band anchored to the configured value would tighten as a character levels, which is not what 'plus or
minus 30 percent' means to anyone."*

Der zweite Grund ist, dass die Entscheidung schon getroffen war. ADR-013 schreibt beim Abschluss von
B04: *„Basiswerte kommen über `BaseStatContributor` (B06 Level, B07 Klasse), Beiträge über
`StatEngine.apply` mit einer `SourceId` (B08 Buffs, B09 Zonen, B11 Ausrüstung)."* B06 folgt dem,
statt es neu zu verhandeln.

**Geprüfte Alternativen**:

- **`ModifierSet` unter `SourceKind.LEVEL`**: verworfen aus dem Bandgrund oben. Der Aufzählungswert
  wirkt auf den ersten Blick wie eine Einladung, aber er ist keine.
- **Direkt in `StatConfig` je Level eine eigene Definition**: verworfen. Das wären 60 Attributsätze
  in der Konfiguration statt einer Wachstumsrate, und jede Balancing-Änderung müsste 60 Blöcke
  anfassen.

**Nebenbefund, bewusst so gelassen**: `SourceKind.LEVEL` bleibt damit **unbenutzt**. Das ist ein
echter Widerspruch in B04 — der Aufzählungswert ist dokumentiert als „The character's level (B06)",
während ADR-013 dasselbe dem `BaseStatContributor` zuweist. B06 löst ihn nicht durch Anpassen von
B04 auf, weil der Wert seine Berechtigung behält: ein *Modifikator*, der aus dem Level folgt, ohne
den Basiswert zu heben — etwa ein Meilensteinbonus alle zehn Level — gehört genau dorthin. B06
braucht so etwas nicht. Der Wert wird also nicht entfernt, sondern bleibt für den Fall reserviert,
und der Grund steht hier, damit niemand ihn später für ein Versehen hält.

---

## 2. XP-Kurve: Kartenfeld mit prüfender Bindefunktion

**Entscheidung**: `xp-curve` ist ein Feld vom Typ `MAP` im Schema. Die Bindefunktion prüft
Lückenlosigkeit von 2 bis zum höchsten Level, positive ganzzahlige Werte und strenge Monotonie und
bricht mit dem **ersten** beanstandeten Level ab. Das Maximallevel ist der höchste Schlüssel der
Karte.

**Begründung**: 59 einzelne `builder.required("xp-curve.2", INTEGER)`-Zeilen könnten die drei
eigentlichen Zusagen gar nicht ausdrücken. Lückenlosigkeit wäre erzwungen, Monotonie nicht — und
genau die ist der Fehler, der ohne Prüfung Spieler dauerhaft auf einem Level festhält. `mobs.by-type`
in B05 hat dieselbe Form; zwei Dateien nebeneinander sollen sich gleich lesen.

**Geprüfte Alternativen**:

- **59 Pflichtfelder**: verworfen, siehe oben. Zusätzlich unlesbar im Schema.
- **Liste statt Karte** (`xp-curve: [100, 220, ...]`, Index = Level − 2): verworfen. Kompakt, aber
  eine verschobene Zeile verschiebt stillschweigend alle Level danach. Explizite Schlüssel machen
  einen Tippfehler zu einem Startfehler statt zu einem Balancingfehler.
- **Formel mit Parametern**: durch `/clarify` in Runde 1 ausgeschlossen.

---

## 3. Fortschrittsbündelung: das Muster von `DamageAggregator`

**Entscheidung**: `ProgressAggregator` hält je Charakter einen Eimer mit Öffnungszeitpunkt und
Summe. Das Fenster wird geschlossen, wenn (a) das nächste XP-Ereignis nach Ablauf der Fensterdauer
eintrifft, (b) ein Levelaufstieg eintritt oder (c) die Sitzung endet. **Keine Aufgabe**, kein Timer.

**Begründung**: B05 hat dieselbe Frage für Schadenszahlen beantwortet und die Antwort steht in
`DamageAggregator`: *„the window is closed on the next hit or on death, never by a task (Principle
II). A window that nobody touches again simply stops."* Eine zweite Antwort auf dieselbe Frage wäre
eine zweite Sache, die kaputtgehen kann, und ein zweites Muster, das jemand lernen muss.

Fall (b) ist der Unterschied zu B05 und folgt aus FR-023c: ein offenes Bündel muss **vor** dem
Aufstiegsereignis raus, sonst kann der Fortschrittsbalken rückwärts springen.

**Geprüfte Alternativen**:

- **Wiederkehrende Aufgabe je Spieler, die Fenster einsammelt**: verworfen — Prinzip II verbietet
  genau das, und SC-012 prüft es.
- **Eine einzige Serveraufgabe, die alle offenen Fenster einsammelt**: zulässig nach Prinzip II
  (eine Systemaufgabe, nicht je Spieler), aber verworfen: sie wäre die einzige Aufgabe in B06 und
  liefe auch dann, wenn niemand spielt. Ein Fenster, das nie geschlossen wird, kostet nichts —
  einen Eimer im Speicher, den das Sitzungsende aufräumt.
- **Kein Fenster, jedes Ereignis einzeln**: durch `/clarify` ausgeschlossen (FR-062).

---

## 4. Eigene Tabelle statt Spalten an `character`

**Entscheidung**: `rpg.character_progress` mit `character_id` als Primärschlüssel und
Fremdschlüssel auf `rpg.character` mit `ON DELETE CASCADE`. Migration `V6_1`. Nutzdaten: `level` und
`xp_in_level`. Dazu `data_version`, `revision`, `updated_at` wie in den bestehenden Tabellen.

**Begründung**: Übernommen aus `V4_1__character_stats.sql`, wo dasselbe für B04 entschieden und
begründet wurde: eine gemeinsame Zeile bedeutet einen gemeinsamen Schreiber und einen gemeinsamen
Revisionszähler zwischen zwei Blöcken. Jede Änderung am Fortschritt wäre dann eine Änderung am
Schreibpfad von B03, und die Blockgrenze aus Prinzip III stünde nur auf dem Papier. Ein Besitzer, ein
Schreiber, eine Position in der Flush-Reihenfolge.

`ON DELETE CASCADE` erledigt zugleich die Anonymisierung: B02s Löschpfad entfernt den Charakter, die
Fortschrittszeile geht mit, und B02 muss nicht wissen, dass B06 existiert.

**Geprüfte Alternativen**:

- **Zwei Spalten an `rpg.character`**: verworfen, siehe oben.
- **Fortschritt in `character_stats` mit unterbringen**: verworfen. Näher dran als `character`, aber
  dasselbe Problem — B04 wäre plötzlich Schreiber für B06-Daten.

---

## 5. Speicherform: Level und XP im Level

**Entscheidung**: Gespeichert werden `level` (1 bis Maximallevel) und `xp_in_level` (0 bis unter der
Schwelle des nächsten Levels). Eine Gesamt-XP-Zahl wird nirgends gespeichert und nirgends abgeleitet.

**Begründung**: Aus `/clarify` Runde 1. Eine Gesamt-XP-Zahl macht das Level zu einer Funktion der
*aktuellen* Kurve — eine später erhöhte Kurve senkt bestehende Charaktere rückwirkend im Level und
nimmt ihnen Zonenzugang und Fähigkeiten. FR-024 verbietet genau das. Es ist derselbe Gedanke, mit dem
ADR-004 bei Items nur Template-ID und Roll-Werte speichert: Balancing darf bestehende Spielerdaten
nicht anfassen.

**Geprüfte Alternativen**:

- **Nur Gesamt-XP**: verworfen, siehe oben. Ein Feld weniger, ein Vertragsbruch mehr.
- **Beides speichern und beim Laden abgleichen**: verworfen. Zwei Quellen für eine Wahrheit, plus die
  Frage, welche gewinnt.

**Randbedingung, die daraus folgt**: Wird die Kurve **gesenkt**, kann `xp_in_level` über der neuen
Schwelle liegen. Das ist kein Fehler — der Überschuss wird beim nächsten Laden regulär in
Levelaufstiege umgesetzt, mit demselben Code, der auch einen normalen Aufstieg verarbeitet. Steigen
statt sinken bleibt damit die einzige Richtung.

---

## 6. Reichweite: Erweiterungspunkt mit sicherem Standardverhalten

**Entscheidung**: `ProximityCheck` ist eine Schnittstelle in `rpg-core`, implementiert von
`PaperProximityCheck` in `rpg-platform`. Sie beantwortet: welche der übergebenen Spieler sind vom Ort
des gestorbenen Gegners nicht weiter als die konfigurierte Reichweite entfernt und in derselben Welt.
Ist **kein** Anbieter registriert, gilt allein der Beitragende selbst als in Reichweite.

**Begründung**: `rpg-core` darf keine Bukkit-Abhängigkeit haben (Prinzip III) und kann daher keine
Entfernung messen. Das Standardverhalten ist die eigentliche Entscheidung: es lässt die Party-Teilung
auf das Verhalten ohne Party zurückfallen, statt XP zu verschenken (alle gelten als nah) oder zu
verschlucken (niemand gilt als nah). Dasselbe Muster wie `DamageFeedback` in B05, wo eine fehlende
Registrierung schlicht nichts tut.

**Geprüfte Alternativen**:

- **Standard „alle in Reichweite"**: verworfen. Eine vergessene Registrierung würde Party-Slots
  stillschweigend zu passivem XP-Einkommen machen — ein Balancingfehler, der wie ein Feature aussieht.
- **Standard „niemand in Reichweite"**: verworfen. Eine vergessene Registrierung würde die XP einer
  Party vollständig verschlucken, was als Datenverlust auffällt, aber unnötig hart ist.
- **Koordinaten in `rpg-core` selbst führen**: verworfen. Das hiesse, Spielerpositionen zu spiegeln —
  ein zweiter Weltzustand, der veralten kann.

---

## 7. XP-Beträge je Mob-Art: austauschbarer Anbieter mit Standardbetrag

**Entscheidung**: `MobXpProvider` liefert den XP-Betrag zu einem Mob-Typschlüssel.
`ConfigMobXpProvider` liest ihn aus `progression.yml` in der Form `mob-xp.default` plus
`mob-xp.by-type`, geschlüsselt über den Bukkit-Typnamen in Grossbuchstaben. B10 ersetzt den Anbieter
später über dieselbe Schnittstelle.

**Begründung**: Genau das Muster, das B05 mit `MobStatProvider` und `mobs.default` / `mobs.by-type`
bereits benutzt — inklusive des Standardbetrags, damit ein von Mojang neu hinzugefügter Mob nicht
stillschweigend wertlos ist. Aus `/clarify` Runde 2 bestätigt. Zwei Dateien nebeneinander sollen sich
nicht unterschiedlich lesen.

**Geprüfte Alternativen**:

- **Null XP bei fehlendem Eintrag**: verworfen in `/clarify` Runde 2. Wäre von B05 abgewichen und
  hätte jeden neuen Mob wertlos gemacht.
- **XP aus dem Lebenswert des Mobs ableiten**: verworfen. Klingt elegant, koppelt aber Balancing von
  Schwierigkeit und Belohnung fest zusammen — ein zäher Mob mit wenig Belohnung wäre nicht mehr
  baubar.

---

## 8. Vollheilung beim Aufstieg: Reihenfolge ist Teil der Entscheidung

**Entscheidung**: Nach einem Aufstieg wird in dieser Reihenfolge gearbeitet: Fortschrittsstand
setzen → Wertestand neu berechnen (der `BaseStatContributor` liest das neue Level) → Leben und Mana
über `StatEngine.restoreResources` auf `ResourcePool.full(neuesMaxLeben, neuesMaxMana)` setzen →
Bündel ausliefern → Aufstiegsereignis veröffentlichen. Genau einmal je Aufstieg, auch bei mehreren
Leveln auf einmal.

**Begründung**: Aus `/clarify` Runde 2 (Option D, vollständig auffüllen). Die Reihenfolge ist nicht
beliebig: würde vor der Neuberechnung aufgefüllt, füllte `ResourcePool.full` gegen das **alte**
Maximum. Der Fehler wäre klein — bei einem Aufstieg um wenige Prozent daneben — und deshalb sehr
lange unentdeckt geblieben. `ResourcePool.full` und `ResourcePool.clampedTo` aus B04 leisten beides
bereits; B06 baut nichts eigenes.

**Bewusst in Kauf genommen**: Weil es keine Levelabstands-Skalierung gibt, lässt sich ein
bevorstehender Aufstieg in einen Bosskampf hinein aufsparen und wirkt dort als planbare Vollheilung.
Selbstbegrenzend — jedes Level steigt genau einmal, insgesamt 59 Mal je Charakter, und auf
Maximallevel entfällt es.

**Für den gesenkten Fall**: Ein Verwaltungseingriff, der das Level senkt, füllt **nicht** auf. Liegt
der aktuelle Wert über dem neuen Maximum, wird er über `ResourcePool.clampedTo` darauf begrenzt
(FR-024c).
