# 04 · Nichtfunktionale Anforderungen

## Performance

| Kennzahl | Zielwert |
|---|---|
| TPS | ≥ 19,5 im Mittel bei 150 Spielern unter Kampflast |
| MSPT (Tick-Dauer) | p95 < 40 ms, p99 < 50 ms |
| Aktive Custom-Mobs gleichzeitig | ≥ 800 serverweit ohne TPS-Verlust |
| Latenz Fähigkeitsauslösung → Effekt | < 1 Tick (kein async Umweg) |
| Login-Dauer (Datenladen) | p95 < 500 ms |

**Tick-Budget-Vorgabe:** Kein einzelnes Plugin-Subsystem darf im Normalbetrieb
mehr als 5 ms pro Tick verbrauchen. Wird in B15 gemessen und durchgesetzt.

**Verbotene Muster:**
- Wiederkehrende Tasks pro Spieler (Cooldowns/Mana werden zeitstempelbasiert
  lazy berechnet, nicht pro Tick heruntergezählt)
- Attributneuberechnung pro Tick statt bei Änderung
- Lineare Suche über alle Zonen/Entities im Tick-Pfad
- Blockierende Aufrufe (DB, Datei, Netzwerk) im Server-Tick
- Reflection/NMS ohne dokumentierte Begründung

## Datenhaltung

- Alle Schreibzugriffe auf PostgreSQL erfolgen **asynchron**.
- Kein DB-Zugriff pro Spielereignis. Änderungen werden gepuffert und gebatcht.
- Autosave-Intervall: 30–60 s (konfigurierbar), zusätzlich bei Quit und Shutdown.
- **Kein Datenverlust bei Crash** über das Autosave-Intervall hinaus.
- Schemaänderungen ausschließlich über versionierte Migrationen.
- Verbindungspool dimensioniert für 100–200 Spieler ohne Wartezeiten im
  Login-Pfad.

## Betrieb

- Konfigurationsfehler führen zu **Fail-Fast beim Start** mit klarer Meldung,
  nicht zu stillem Fehlverhalten zur Laufzeit.
- Content-Änderungen (Zonen, Mobs, Items, Fähigkeitswerte) ohne Serverneustart
  nachladbar.
- Strukturiertes Logging; Fehler im Gameplay-Pfad dürfen keinen Spieler in einen
  inkonsistenten Zustand versetzen.
- Metriken exportierbar für externes Monitoring.

## Sicherheit & Fairness

- Serverseitige Autorität für alle Werte. Keine Client-Eingabe wird ungeprüft
  übernommen.
- Rate-Limits auf Fähigkeitsauslösung und Interaktionsereignisse.
- Admin-Eingriffe an Spielerdaten werden protokolliert.

## Internationalisierung

- Spielsprache Englisch. Sämtliche Spielertexte laufen über ein zentrales
  Message-System mit Schlüsseln — keine hartcodierten Strings im Code.
- Struktur für weitere Sprachen vorbereitet, aber nicht befüllt.
