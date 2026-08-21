# Vertrag: `PartyRegistry`

**Block**: B06 | **Paket**: `rpg.core.progression` | **Stand**: 2026-08-20

Die Party-Verwaltung. B14 baut darauf seine Befehle, B13 seine Anzeige. B06 selbst hat **keine**
Befehle und **keine** Anzeige (FR-037).

Alles hier ist Laufzeitzustand. Es gibt kein Repository, keine Tabelle und keine Migration — ein
Serverneustart löscht jede Party, und das ist die Zusage, nicht der Nebeneffekt (FR-029).

---

## Gründen, einladen, beitreten

```java
/** Gründet eine Party mit dem Aufrufer als Anführer (FR-029a). */
PartyResult create(UUID playerId);

/** Der Anführer lädt ein. Nur er darf das (FR-029b, FR-030). */
PartyResult invite(UUID leaderId, UUID targetId);

/** Der Eingeladene nimmt an. Die Mitgliedschaft entsteht erst hier (FR-030). */
PartyResult accept(UUID targetId);

/** Der Eingeladene lehnt ab. */
PartyResult decline(UUID targetId);
```

- `invite` von einem Mitglied ohne Anführerrolle → `NOT_LEADER`.
- `invite` auf sich selbst → `SELF_INVITE`.
- `invite` auf einen Spieler ohne bereite Sitzung → `TARGET_NOT_READY`.
- `accept`, wenn der Aufrufer schon in einer Party ist → `ALREADY_IN_PARTY` (FR-032).
- `accept` in eine volle Party → `PARTY_FULL` (FR-033).
- `accept` nach Ablauf der Frist → `INVITE_EXPIRED`. Die Frist wird **beim Zugriff** geprüft, nicht
  von einer Aufgabe (FR-031, FR-061).

**Ohne bestehende Party**: `invite` von einem Spieler, der in keiner Party ist, gründet implizit
eine. Sonst müsste jeder Ablauf in B14 aus zwei Befehlen bestehen, und ein Spieler, der `/party
invite` tippt, will offensichtlich eine Party.

---

## Verlassen und entfernen

```java
/** Jedes Mitglied darf selbst gehen (FR-029b). */
PartyResult leave(UUID playerId);

/** Nur der Anführer darf entfernen (FR-029b). */
PartyResult remove(UUID leaderId, UUID memberId);
```

- Scheidet der Anführer aus, geht die Rolle an das **dienstälteste verbleibende** Mitglied
  (FR-029c). Eine Party ist zu keinem Zeitpunkt ohne Anführer.
- Verlässt das letzte Mitglied, existiert die Party nicht mehr, und es bleibt kein Zustand zurück
  (FR-035).
- Eine Party mit einem einzigen Mitglied ist zulässig und verhält sich in der XP-Verteilung wie keine
  Party.

---

## Abfrage

```java
/** Die Party eines Spielers, falls er in einer ist. */
Optional<PartyView> partyOf(UUID playerId);

/** Nur die Mitglieder, ohne Zwischenobjekt — für den Verteilungspfad. */
int membersOf(UUID playerId, UUID[] out);

/** Anzahl bestehender Partys. Für Lecktests. */
int partyCount();
```

```java
record PartyView(UUID partyId, UUID leader, List<UUID> members) {}
```

`PartyView` ist eine Kopie zum Anzeigen. `membersOf` schreibt in ein übergebenes Feld und erzeugt
nichts — es läuft bei jedem Kill einer Party. Das Feld MUSS mindestens `party.max-size` Plätze
haben; ein zu kleines wäre stille Kürzung im Kampfpfad, also lehnt `membersOf` es mit
`IllegalArgumentException` ab, statt zu kürzen. Aufrufer halten ein Feld dieser Grösse vor und
verwenden es wieder.

---

## Sitzungskopplung

```java
/** Von B03 beim Sitzungsende gerufen: das Mitglied verlässt die Party (FR-034). */
void onSessionEnded(UUID playerId);
```

Das ist der einzige Weg, auf dem eine Party durch etwas anderes als eine Spielerhandlung schrumpft.
Er löst dieselben Übergänge aus wie `leave` — einschliesslich der Rollenübergabe, wenn es den
Anführer traf.

---

## Ergebnis

```java
record PartyResult(boolean success, PartyRejection rejection, UUID partyId) {}
```

`PartyRejection`: `NONE`, `ALREADY_IN_PARTY`, `PARTY_FULL`, `NOT_LEADER`, `INVITE_EXPIRED`,
`INVITE_UNKNOWN`, `TARGET_NOT_READY`, `SELF_INVITE`, `NOT_A_MEMBER`.

**Warum eine Aufzählung und kein Text**: B06 darf keinen Spielertext enthalten (FR-038). B14 bildet
die Werte auf Message-Schlüssel ab und entscheidet über die Formulierung.

---

## Ereignis

Jede Änderung der Mitgliedschaft veröffentlicht `PartyChangedEvent` (FR-036) — Aufbau in
[events.md](./events.md).

---

## Was ausdrücklich fehlt

| Nicht hier | Wo stattdessen |
|---|---|
| `/party invite`, `/party kick`, `/party leave` | B14 |
| Party-Anzeige, Mitgliederliste im HUD | B13 |
| Party-Chat | nicht vorgesehen |
| Party über Sitzungsgrenzen hinweg | ausgeschlossen — würde Speicherung erfordern |
| Beuteverteilung in der Party | B11; B05 hat Loot beim höchsten Beitrag festgelegt |
