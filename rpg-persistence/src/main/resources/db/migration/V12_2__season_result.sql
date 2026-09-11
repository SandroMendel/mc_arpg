-- =============================================================================
-- B12 -- Der eingefrorene Endstand einer abgeschlossenen Saison
-- =============================================================================
--
-- Nach dem Einfrieren wird diese Tabelle NUR NOCH GELESEN. Sie ist das
-- Gedaechtnis des Servers fuer eine Saison, die vorbei ist -- und ihr Inhalt
-- darf sich nicht mehr aendern, weil die Belohnungen daraus bereits vergeben
-- sind.
--
-- DIE GEWICHTUNG STEHT MIT DRIN, und das ist der eigentliche Grund fuer diese
-- Tabelle (FR-050, SC-021). Laege sie nur in statistics.yml, sortierte die
-- naechste Balancing-Aenderung eine abgeschlossene Saison rueckwirkend um --
-- unbemerkt, weil niemand eine Rangliste erneut aufruft, die er schon gesehen
-- hat. Wer den ersten Platz hatte, haette ihn dann irgendwann nicht mehr, ohne
-- dass etwas passiert waere.
--
-- Mit der Gewichtung im Datensatz traegt der Endstand seine eigene
-- Rechenvorschrift bei sich: er ist nachrechenbar, auch Jahre spaeter und auch
-- dann, wenn die Konfiguration inzwischen etwas ganz anderes sagt.

CREATE TABLE rpg.season_result (
    season_key   TEXT        NOT NULL,
    player_id    UUID        NOT NULL REFERENCES rpg.player_state (player_id) ON DELETE CASCADE,

    -- Platz in der Gesamtwertung. Gleichstaende tragen denselben (FR-034), der
    -- naechste Platz ueberspringt entsprechend.
    rank         INTEGER     NOT NULL,

    -- Die erreichte Punktzahl, ganzzahlig (ADR-046).
    score        BIGINT      NOT NULL,

    -- Die Gewichtung, MIT DER GERECHNET WURDE. Siehe oben.
    weights      JSONB       NOT NULL,

    -- Formatversion des Datensatzes, wie in jeder anderen Tabelle dieses
    -- Projekts: eine spaetere Migration bleibt moeglich.
    data_version INTEGER     NOT NULL DEFAULT 1,

    -- Wann der Abschluss lief. Nicht wann die Saison endete -- die beiden
    -- fallen auseinander, wenn der Server ueber das Enddatum hinweg aus war
    -- und der Abschluss beim Start nachgeholt wurde (FR-058).
    frozen_at    TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (season_key, player_id),

    CONSTRAINT chk_season_result_rank  CHECK (rank >= 1),
    CONSTRAINT chk_season_result_score CHECK (score >= 0)
);

COMMENT ON TABLE rpg.season_result IS
    'B12: eingefrorener Endstand einer Saison, samt der Gewichtung, mit der gerechnet wurde.
     Nach dem Einfrieren nur noch gelesen (FR-050).';

-- Die haeufigste Abfrage: "zeig mir den Endstand dieser Saison, der Reihe nach".
CREATE INDEX idx_season_result_ranking
    ON rpg.season_result (season_key, rank);

-- Das VORHANDENSEIN von Zeilen fuer einen season_key ist der Beleg, dass der
-- Abschluss gelaufen ist (FR-058). Deshalb braucht es keine eigene
-- "abgeschlossen"-Spalte: ein zweiter Zustand ueber dieselbe Tatsache waere
-- eine zweite Wahrheit, und die beiden liefen beim ersten Abbruch auseinander.
