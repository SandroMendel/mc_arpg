# Quickstart: B03 · Spieler-Session & Datenlebenszyklus validieren

Diese Anleitung prüft, ob die B03-Implementierung die Anforderungen aus `spec.md` erfüllt.
Schnittstellen: siehe `contracts/`. Entitäten und Zustandsübergänge: siehe `data-model.md`.

## Voraussetzungen

- JDK 25 und der Gradle-Wrapper aus B01.
- **Docker mit Linux-Containern** für die Persistenztests (wie in B02).
- Für die Abschnitte 6 bis 8: ein laufender Paper-Server mit dem gebauten Plugin.

## 1. Lebenszyklusregeln ohne Server (FR-004, FR-011 bis FR-015, FR-017 bis FR-021)

```bash
./gradlew :rpg-core:test --tests '*session*'
```

**Erwartet**: Läuft ohne Docker und ohne Server durch. Muss mindestens abdecken:

- Solange eine Sitzung nicht `READY` ist, liefert die Registry nichts — **keine** Standardwerte
- Der Übergang `FAILED → entfernt` löst **keinen** Schreibvorgang aus
- Der Übergang `LOADING → UNLOADING` (Spieler trennt während des Ladens) löst ebenfalls keinen aus
- Ein zweiter Ladevorgang für denselben Spieler wird abgelehnt, nicht überschrieben
- Der aktive Charakter lässt sich nach dem Erzeugen der Sitzung nicht mehr ändern
- Der Abgleich entfernt eine Sitzung ohne verbundenen Spieler

Die beiden Übergänge im zweiten und dritten Punkt sind die wichtigsten Tests des ganzen Blocks:
Ein Schreibvorgang aus `FAILED` heraus ist genau der Fehler, der bestehenden Fortschritt zerstört.

## 2. Charakter-Migration und Eindeutigkeit (FR-017, FR-020, SC-006)

```bash
./gradlew :rpg-persistence:test --tests '*Character*'
```

**Erwartet**:

1. Die Migration `V3_1` legt `rpg.character` an, ohne die Tabellen aus B02 zu verändern.
2. Ein zweiter Charakter derselben Klasse für denselben Account wird von der **Datenbank**
   abgelehnt — nicht erst von Anwendungscode.
3. Ein erneuter Migrationslauf wendet null Migrationen an.
4. Das Anonymisieren eines Accounts aus B02 entfernt über den Fremdschlüssel auch dessen Charaktere.

Punkt 2 ist der eigentliche Nachweis: Die Regel steckt im Schlüssel, nicht in einer Prüfung, die
ein späterer Block umgehen könnte.

## 3. Sammelladen in einer Datenbankrunde (FR-005, SC-005)

```bash
./gradlew :rpg-persistence:test --tests '*SessionBundle*'
```

**Erwartet**: Das Laden einer Sitzung mit drei Charakteren und mehreren Item-Instanzen entnimmt dem
Login-Pool **eine** Verbindung, nicht drei. Nachzuweisen über die Zählung der Verbindungsentnahmen,
nicht über die Laufzeit.

## 4. Überführung älterer Standfassungen (FR-025 bis FR-027, SC-009)

```bash
./gradlew :rpg-persistence:test --tests '*StateVersion*'
```

**Erwartet**:

1. Ein Charakter in einer älteren Fassung wird beim Laden überführt, der Fortschritt bleibt.
2. Der überführte Stand wird in der aktuellen Fassung gespeichert und beim nächsten Laden nicht
   erneut überführt (FR-026) — nachzuweisen über `data_version` in der Zeile, nicht nur über das
   Verhalten.
3. Ein Stand in einer **unbekannten** Fassung führt zur Abweisung, nicht zu einer Fehlinterpretation.

## 5. Ereignisreihenfolge und sicherer Zustand (FR-002, FR-003, FR-006)

```bash
./gradlew :rpg-platform:test --tests '*Session*'
```

**Erwartet** (MockBukkit):

- Ein Spieler mit vorgeladener Sitzung ist beim Betreten sofort freigegeben; der sichere Zustand
  wird nicht aktiv.
- Fehlt die vorgeladene Sitzung, greift die Rückfallebene: bewegungsgesperrt und schadensimmun, bis
  nachgeladen ist.
- Nach 5 Sekunden ohne Ergebnis wird die Anmeldung abgebrochen.
- Der `PlayerMoveEvent`-Handler kehrt sofort zurück, wenn niemand lädt.

**Wichtig**: Bei diesen und allen Testcontainers-Tests die Zahl der **übersprungenen** Tests
mitprüfen, nicht nur die der fehlgeschlagenen. In B01 hat MockBukkit drei Tests still als „skipped"
gemeldet — das sah wie Abdeckung aus und war keine.

## 6. Sitzungsende auf allen drei Wegen (FR-007, SC-004)

Auf einem laufenden Testserver:

1. Fortschritt erzeugen, dann regulär verlassen → erneut verbinden, Fortschritt prüfen.
2. Dasselbe, aber den Spieler kicken.
3. Dasselbe, aber die Verbindung hart trennen (Client beenden, Netzwerk kappen).

**Erwartet**: In allen drei Fällen ist der Fortschritt beim erneuten Verbinden vorhanden.

## 7. Schnelles Wiederverbinden (FR-013, FR-014, SC-006)

1. Fortschritt erzeugen, verlassen und **innerhalb einer Sekunde** erneut verbinden.
2. **Erwartet**: Der aktuelle Stand ist da, nicht der vorherige; im Log erscheint zu keinem
   Zeitpunkt eine zweite Sitzung für denselben Spieler.
3. Zwanzigmal wiederholen — das Zeitfenster ist eng, ein einzelner Durchlauf beweist wenig.

## 8. Ladefehler und Speicherverhalten (FR-011, FR-012, SC-007, SC-008)

1. Die Datenbank anhalten, dann einen Spieler verbinden lassen.
2. **Erwartet**: Abweisung mit klarer Meldung, **bevor** der Spieler die Welt betritt. Der
   gespeicherte Datensatz ist danach unverändert — nachzuweisen durch Vergleich der Revision vor
   und nach dem Versuch.
3. Datenbank wieder starten, denselben Spieler verbinden lassen: Der Fortschritt ist unverändert da.
4. 200 Verbindungen und Trennungen durchlaufen lassen und die Zahl gehaltener Sitzungsobjekte
   prüfen.
5. **Erwartet**: Sie entspricht der Zahl der verbundenen Spieler.

## Abnahme

Erfüllt, wenn alle acht Abschnitte ohne Abweichung durchlaufen — das deckt SC-001 bis SC-010 aus
`spec.md` vollständig ab.

Die Abschnitte 1 bis 5 laufen vollständig automatisiert; nur 6 bis 8 brauchen einen laufenden
Paper-Server. Wie bei B02 empfiehlt es sich, die serverabhängigen Abschnitte gemeinsam mit den noch
offenen Punkten aus B02 (T085–T087) in einem Durchgang zu prüfen.
