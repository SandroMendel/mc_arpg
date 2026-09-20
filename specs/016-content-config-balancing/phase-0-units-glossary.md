# B16 Phase 0 — Einheiten- und Terminologie-Glossar

**Prüfdatum:** 2026-09-12
**Repository:** `C:\Users\Ticoo\Desktop\VuntexRPG\b15-performance-observability`
**Status:** Ist-Semantik für Schema-/Migrationsplanung; keine Rebalance-Entscheidung

Dieses Glossar normiert nur Begriffe, die aus dem aktuellen Code und den aktuellen YAML-Dateien
belegt sind. Es ändert keine Werte. `confirmed` bedeutet, dass die aktuelle Skalierung nachweisbar
ist; `open` bedeutet, dass die spätere B16-Feldsemantik noch ausdrücklich festgelegt werden muss.

| Begriff/Feld | Einheit/Skalierung | Semantik im Ist-Stand | Wertebereich / Beispiel | Kategorie | Status |
|---|---|---|---|---|---|
| `*-seconds`, `Duration.ofSeconds(...)` | Sekunden | Dauer, die als Sekunden in YAML oder Java-`Duration` geführt wird | z. B. Combat-Timeout `8`, Attribution-Timeout `30` | CONTENT | confirmed |
| `*-millis`, `Duration.ofMillis(...)` | Millisekunden | kurze Dauer/Fenster; keine automatische Umrechnung in einen neuen Balancewert | z. B. globaler Cooldown `750`, Feedback `500`, Warnung `15000` | CONTENT | confirmed |
| Server-Tick / `ticks` | Ticks; Ist-Umrechnung `20 ticks = 1 s` | interne Plattform-/Vanilla-Zeitskala in bestehender Logik | z. B. `6000 ticks` im Currency-Code | UNIT_DEFINITION | confirmed |
| `ticks/20` | Sekunden aus Ticks | reine Umrechnungsformel, nicht automatisch ein YAML-Contentwert | `6000 / 20 = 300 s` im Ist-Code | ALGORITHM_CONSTANT | confirmed |
| `Fraction` | normalisierter Faktor, typischerweise `0..1` | Multiplikator/Anteil, z. B. Regeneration, Density oder Verschleiß-Faktor | aktuelle Beispiele `0.20`, `0.35`, `0.40`; feldweise Grenzen bleiben schemaabhängig | UNIT_DEFINITION | confirmed |
| `Condition-%` | Prozent-Skala `0..100` | Wear-Zustand; `WearCurve` verwendet `100.0` als volle Ist-Skala | aktuelle Schwellen z. B. `50`, `25`, `10`; keine neue Schwelle | UNIT_DEFINITION | confirmed |
| Schaden | Domänen-/Stat-Einheiten | numerischer Schadenswert, der in Combat/Stats verarbeitet wird; keine physikalische Einheit | aktuelle Defaults z. B. `2.0`, `8.0`, `30.0`; formale B16-Skalierung offen | CONTENT | open |
| Mana | Domänen-/Stat-Einheiten | numerischer Ressourcenwert des Stats-Systems | `stats.yml` enthält aktuell Base `50`, Min `0`, Max `500` | CONTENT | open |
| Stats-Einheit | numerische Stat-Punkte | Health, Regen, Defense, Mana, Damage und Geschwindigkeiten werden als typisierte Attributwerte geführt | bestehende Base-/Min-/Max-Werte bleiben unverändert; genaue Einheiten je Attribut offen | CONTENT | open |
| `mobs.yml:admin-spawn-limit` | gleichzeitig registrierte `Origin.ADMIN`-Entities serverweit | administrative Schutzgrenze; jeder `/rpg mob spawn`-Aufruf setzt höchstens eine Kreatur | aktueller Wert `20`; `mobs.yml` ist Owner, Java `20` Fallback | PROTECTION_BOUNDARY | confirmed |
| Mob-Entities / Spawn-Vorgang | globale Anzahl gleichzeitig registrierter ADMIN-Entities | beschreibt die Zählsemantik hinter `admin-spawn-limit`, nicht Mob-Balance oder Batchgröße | `HordeRegistry.countAdmin()` gegen Limit `20` | PROTECTION_BOUNDARY | confirmed |
| Blöcke / `blocks` | Blockkoordinaten bzw. Blockdistanz | räumliche Reichweite, Area oder Offset | aktuelle Beispiele `50.0`, `96`, `3.0`; Feldsemantik bleibt domänenspezifisch | UNIT_DEFINITION | confirmed |
| Coins | Ganzzahlige Währungseinheiten | Ledger-/Preis-/Dropwerte | aktuelle Beispiele Starting-Balance `0`, Drop `4` | CONTENT | confirmed |

## Migrationsregeln aus dem Glossar

- Sekunden, Millisekunden, Ticks und `ticks/20` dürfen bei der neutralen v0→v1-Migration nicht
  stillschweigend ineinander umgerechnet werden. Eine Umrechnung ist nur zulässig, wenn ein
  konkreter Legacy-Pfad und sein bestehender Consumer nachgewiesen sind.
- `Fraction` und `Condition-%` sind verschiedene Skalen. Eine Prozentzahl darf nicht ohne
  dokumentierten Feldvertrag als Fraction interpretiert werden.
- Schaden, Mana und sonstige Stats bleiben zunächst in der bestehenden Domäneneinheit. Das
  Glossar ist keine spätere Balancing-Freigabe.
- `admin-spawn-limit` ist als entschiedene serverweite Schutzgrenze dokumentiert; Stats-Minima/-Maxima
  bleiben als offene Ownership-/Schutzgrenzen markiert, bis die Schema- und Migrationsentscheidung
  ausdrücklich getroffen wurde.

## Quellen

- `rpg-core/src/main/java/rpg/core/combat/CombatConfig.java`
- `rpg-core/src/main/java/rpg/core/combat/FallDamageConfig.java`
- `rpg-core/src/main/java/rpg/core/ability/AbilityRuntime.java`
- `rpg-core/src/main/java/rpg/core/item/WearCurve.java`
- `rpg-core/src/main/java/rpg/core/currency/CurrencyConfig.java`
- `rpg-plugin/src/main/resources/{abilities,combat,currency,items,mobs,progression,stats}.yml`

Keine Code-/YAML-Änderung, kein Commit und kein Push.
