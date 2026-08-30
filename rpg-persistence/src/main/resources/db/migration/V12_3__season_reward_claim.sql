-- =============================================================================
-- B12 -- Der Belohnungsanspruch aus einer abgeschlossenen Saison
-- =============================================================================
--
-- EIN ANSPRUCH VERFAELLT NICHT (FR-053a, ADR-045). Deshalb gibt es hier KEINE
-- Ablaufspalte -- nicht, weil sie vergessen wurde, sondern weil es keine Frist
-- gibt, die sie tragen koennte.
--
-- Der Grund ist einfach: ein Spieler, der waehrend des Saisonendes nicht online
-- war, hat nichts falsch gemacht. Eine Frist bestrafte Urlaub, Krankheit und
-- Zeitverschiebung -- und zwar denjenigen, der drei Monate lang gespielt hat.
-- Wer diese Tabelle spaeter um ein "expires_at" erweitern will, aendert damit
-- eine Zusage und nicht ein Schema; ClaimNeverExpiresTest haelt das fest.
--
-- DER RIEGEL GEGEN DOPPELTE EINLOESUNG ist ein bedingtes Update (R2, FR-053):
--
--   UPDATE ... SET claimed_at = now(), claimed_by_character = ?
--    WHERE season_key = ? AND player_id = ? AND claimed_at IS NULL
--
-- Erst wenn GENAU EINE Zeile betroffen war, wird gutgeschrieben. Zwei
-- gleichzeitige Einloesungen sehen beide die offene Zeile, aber nur eine
-- gewinnt das Update -- die andere bekommt null betroffene Zeilen und weiss
-- damit, dass sie zu spaet war.
--
-- REIHENFOLGE: markieren, dann gutschreiben. Ein Absturz dazwischen kostet
-- hoechstens eine Belohnung und verdoppelt keine. Andersherum -- erst
-- gutschreiben, dann markieren -- verdoppelte sie beim naechsten Versuch, und
-- das ist der teurere Fehler.

CREATE TABLE rpg.season_reward_claim (
    season_key           TEXT        NOT NULL,
    player_id            UUID        NOT NULL
                                     REFERENCES rpg.player_state (player_id) ON DELETE CASCADE,

    -- Der belohnte Platz. Steht hier und nicht nur in season_result, weil ein
    -- Anspruch auch dann lesbar bleiben muss, wenn jemand die Belohnungen der
    -- Konfiguration inzwischen geaendert hat.
    rank                 INTEGER     NOT NULL,

    -- Was es gibt: Coins und/oder Vorlagen-IDs mit Stueckzahl. Als JSONB, weil
    -- der Inhalt aus der Konfiguration stammt und mit ihr waechst -- und weil
    -- er, wie die Gewichtung im Endstand, zum Zeitpunkt der Vergabe
    -- festgehalten gehoert.
    reward               JSONB       NOT NULL,

    -- NULL heisst OFFEN. Das ist die ganze Zustandsmaschine.
    claimed_at           TIMESTAMPTZ NULL,

    -- Welcher Charakter eingeloest hat. Der Anspruch gehoert dem KONTO
    -- (FR-005), eingeloest wird er von einer Figur -- und welche es war,
    -- gehoert ins Protokoll.
    claimed_by_character UUID        NULL,

    data_version         INTEGER     NOT NULL DEFAULT 1,
    created_at           TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (season_key, player_id),

    CONSTRAINT chk_season_claim_rank CHECK (rank >= 1),

    -- Eingeloest heisst: BEIDES gesetzt. Ein Zeitstempel ohne Charakter waere
    -- eine Einloesung ohne Einloeser, ein Charakter ohne Zeitstempel eine
    -- halbe Markierung -- beides Zustaende, die es nicht geben darf.
    CONSTRAINT chk_season_claim_consistent
        CHECK ((claimed_at IS NULL AND claimed_by_character IS NULL)
            OR (claimed_at IS NOT NULL AND claimed_by_character IS NOT NULL))
);

COMMENT ON TABLE rpg.season_reward_claim IS
    'B12: Belohnungsanspruch aus einer abgeschlossenen Saison. claimed_at NULL heisst offen;
     es gibt keine Ablaufspalte, weil ein Anspruch nicht verfaellt (FR-053a).';

-- Die Abfrage beim Anmelden: "hat dieses Konto noch etwas offen?" (FR-052)
CREATE INDEX idx_season_claim_open
    ON rpg.season_reward_claim (player_id)
    WHERE claimed_at IS NULL;
