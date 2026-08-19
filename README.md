# Minecraft RPG Server — Spezifikations-Repository

Dieses Verzeichnis enthält die Vorab-Spezifikation für ein Minecraft-RPG-Plugin
(Paper-Spigot, MC 26.2). Es ist die Grundlage für die weitere Arbeit mit
**Claude Code + Spec-Kit**.

## Struktur

```
.
├── README.md                         Diese Datei
├── constitution.md                   → nach .specify/memory/constitution.md kopieren
├── docs/
│   ├── 00-vision-scope.md            Produktvision, Scope, Nicht-Ziele
│   ├── 01-architecture.md            Schichtenmodell, Blockübersicht, Abhängigkeiten
│   ├── 02-decisions.md               Entscheidungs-Log (ADRs)
│   ├── 03-glossary.md                Domänenglossar DE/EN
│   ├── 04-nonfunctional-requirements.md  Performance-, Daten-, Betriebsziele
│   ├── 05-roadmap-speckit-workflow.md    Reihenfolge + konkreter Spec-Kit-Ablauf
│   └── 06-open-questions.md          Zentrale Sammlung offener Punkte
└── blocks/
    └── B01 … B17                     Ein Steckbrief je Architekturblock
```

## Verwendung mit Spec-Kit

1. Repository initialisieren: `specify init --here --ai claude`
2. Inhalt von `constitution.md` als Basis für `/constitution` verwenden.
3. Pro Block eine Spec erzeugen: `/specify` mit dem Inhalt der jeweiligen
   `blocks/BXX-*.md` als Eingabe.
4. Reihenfolge und Meilensteine siehe `docs/05-roadmap-speckit-workflow.md`.

## Status der Dokumente

Alle Dateien sind **Entwürfe**. Jeder Blocksteckbrief hat einen Abschnitt
„Offene Fragen", der vor `/specify` für diesen Block beantwortet werden muss.
Was bereits verbindlich entschieden ist, steht in `docs/02-decisions.md`.

## Sprachkonvention

- Dokumentation und Diskussion: **Deutsch**
- Code, Bezeichner, Commit-Messages, Config-Keys, Spielertexte: **Englisch**
