-- B11: der Verschleisszustand der beiden Ausruestungsleitern eines Charakters (FR-039).
--
-- Version space: V11_1 hat item_instance zurueckgebaut (ADR-039, research.md R2), das hier ist die
-- zweite Migration dieses Blocks. Flyway laeuft ohne outOfOrder, deshalb steht der Rueckbau vorne
-- und nicht, wie zuerst geplant, am Ende.
--
-- Warum ZWEI Spalten und nicht vier:
-- B07 vergibt Helm, Brust, Hose und Schuhe als EINE Stufe. Vier getrennte Zustaende waeren vier
-- Reparaturrechnungen fuer eine Entscheidung, die der Spieler einmal getroffen hat - und jede
-- Balancing-Frage danach muesste vier Mal beantwortet werden.
--
-- Warum eine eigene Tabelle und nicht zwei Spalten an character_class_progress:
-- dasselbe Argument, das V7_1 fuer sich selbst aufgeschrieben hat. Eine geteilte Zeile hiesse ein
-- geteilter Schreiber und ein geteilter revision-Zaehler, und die Blockgrenze aus Prinzip III
-- staende nur noch auf dem Papier. Hier kommt hinzu, dass sich diese Zeile bei JEDEM Treffer
-- aendert und die Stufe fast nie - sie im selben Datensatz zu fuehren hiesse, den ganzen
-- Fortschritt eines Charakters bei jedem Schlag als schmutzig zu markieren.
--
-- Warum NUMERIC und nicht INTEGER:
-- der Zustand faellt in Bruchteilen. Bei 0,01 Zustandspunkten je Schadenspunkt waere ein Treffer
-- ueber 40 Punkte als Ganzzahl null - und der Verschleiss existierte nur auf dem Papier.

CREATE TABLE rpg.character_gear_condition (
    character_id     UUID    PRIMARY KEY
                             REFERENCES rpg.character (character_id) ON DELETE CASCADE,

    -- [0, 100]. Ein frischer Charakter traegt beide Leitern voll.
    armor_condition  NUMERIC NOT NULL DEFAULT 100,
    -- Unabhaengig von der Ruestung: wer viel einsteckt, repariert Ruestung; wer viel austeilt,
    -- repariert die Waffe (FR-040, FR-041).
    weapon_condition NUMERIC NOT NULL DEFAULT 100,

    data_version     INTEGER NOT NULL DEFAULT 1,
    revision         BIGINT  NOT NULL DEFAULT 0,
    updated_at       TIMESTAMPTZ NOT NULL DEFAULT now(),

    -- Hier erzwungen und nicht der Anwendung geglaubt. Ein Zustand ausserhalb der Spanne ist keine
    -- Balancing-Entscheidung, sondern ein Fehler - und ein negativer Zustand wuerde als Faktor
    -- unterhalb des Restanteils landen, also als eine Ausruestung, die schlechter ist als keine.
    CONSTRAINT chk_gear_condition_armor  CHECK (armor_condition  BETWEEN 0 AND 100),
    CONSTRAINT chk_gear_condition_weapon CHECK (weapon_condition BETWEEN 0 AND 100)
);

-- ON DELETE CASCADE oben regelt zugleich die Anonymisierung: B02s Loeschpfad entfernt den Charakter,
-- und diese Zeile geht mit, ohne dass B02 von B11 wissen muss.

COMMENT ON TABLE rpg.character_gear_condition IS
    'Verschleisszustand je Ausruestungsleiter (B11). Ausruestung zerbricht nie - siehe FR-038.';
