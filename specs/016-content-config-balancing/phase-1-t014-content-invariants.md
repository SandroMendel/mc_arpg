# B16 Phase 3 · T014 Cross-Domain-Referenzen

Status: **implementiert und geprüft** (2026-09-16).

## Verantwortungsgrenze

`B16ContentReferenceValidator` adaptiert die bereits gebundenen Core-Modelle auf die serverfreien
T012-Invarianten. Es gibt keinen zweiten YAML-Parser und keine Bukkit-/Paper-Abhängigkeit. Die
Prüfung erzeugt vollständige `ContentReference`-Metadaten erst aus der geprüften Generation; sie
entscheidet keine offenen Balance- oder Textbeziehungen.

## Geprüfte Beziehungen

| Invariante | Quelle | Ziel |
|---|---|---|
| `CLASSES_TO_ABILITIES` | Klassen-Fähigkeitsbindung | Ability-ID |
| `MOB_HORDES_TO_ZONES` | Horde-Zonen-ID | Zone-ID |
| `MOB_HORDES_TO_SPAWN_AREAS` | Horde-Area-ID | Spawn-Area im selben Gebiet |
| `MOB_HORDES_TO_KINDS` | Horde-/Boss-Kind-ID | Mob-Kind, beim Boss mit Capability `boss` |
| `ITEM_LOOT_TO_TEMPLATES` | Loot-Eintrag | Item-Template-ID |
| `ITEM_LOOT_TO_ZONES` | zonenbasierte Loot-Tabelle | Zone-ID |
| `ITEM_LOOT_TO_KINDS` | artbasierte Loot-Tabelle | Mob-Kind-ID |
| `ITEM_VENDORS_TO_ZONES` | Händler-Registry | Zone-ID |
| `ITEM_VENDORS_TO_TEMPLATES` | Händlerbestand | Item-Template-ID |

Jede Ablehnung enthält Quelldomäne/-ID, Zieldomäne/-ID, exakten YAML-Pfad und die Anforderung.
Scope- und Capability-Prüfungen werden über die typisierten Registry-Verträge ausgewertet.
Formelwerte, Vanilla-keyed Currency-Drops, globale Cooldowns, Casting-Zeiten, Raritäten und Affixe
bleiben außerhalb, weil dafür im aktuellen B16-Datenmodell kein beschlossener typed relation
contract existiert.

## Prüfung

```text
gradlew.bat --no-daemon :rpg-content:test --tests rpg.content.validation.B16ContentReferenceValidatorTest
gradlew.bat --no-daemon :rpg-content:check
```

Die fokussierten Tests weisen unbekannte Ability-, Zonen- und Template-Ziele mit vollständigem
Kontext nach. Der vollständige `rpg-content`-Check lief nach der T014/T015-Anbindung erfolgreich.
