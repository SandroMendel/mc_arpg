-- =============================================================================
-- B12 -- Ranglisten: die abgeleitete Sicht auf rpg.player_statistic_daily
-- =============================================================================
--
-- Die Metrik ist eine SPALTE, kein Sichtname (research.md R1). Damit waechst die
-- Zahl der Sichten NICHT mit der Zahl der Metriken -- eine neue Metrik erscheint
-- von allein auf dem Brett, ohne dass jemand eine Sicht anlegt (FR-032a).
--
-- DREI Sichten, nicht vier. Die vierte -- mv_stat_season -- laesst sich nicht
-- bauen: Saisongrenzen stehen in statistics.yml, und eine Materialized View
-- kennt keine Parameter. Sie in die Datenbank zu spiegeln waere eine zweite
-- Wahrheit ueber den Kalender, und liefe die Spiegelung einmal nicht, waeren
-- die Saisonranglisten still falsch. Der Saisonstand entsteht deshalb aus einer
-- parametrisierten Abfrage je Auffrischung -- ebenfalls EINE Abfrage, siehe
-- ADR-049 und JdbcLeaderboardSource.
--
-- Gruppiert wird nach dem VOLLSTAENDIGEN Metrikschluessel, also einschliesslich
-- Dimension (mob_kills.rotling). Die Zusammenfassung je Familie und die Trennung
-- von Boss- und Mob-Kills passieren beim Fuellen des Speicherstands, nicht hier:
-- welche Art ein Boss ist, steht in mobs.yml und nicht in der Datenbank
-- (FR-009a). Wuerde die Sicht schon je Familie verdichten, waere die
-- Aufschluesselung fuers eigene Profil verloren -- und die braucht FR-038.
--
-- Jede Sicht traegt einen EINDEUTIGEN Index ueber ihren vollstaendigen
-- Schluessel. Ohne ihn ist REFRESH MATERIALIZED VIEW CONCURRENTLY nicht
-- moeglich, und die Auffrischung wuerde die Sicht sperren, waehrend Spieler
-- sie lesen (FR-031).
--
-- Der Verbund mit rpg.player_state ist der Anonymisierungsfilter (FR-039):
-- beim Anonymisieren werden die Statistikzeilen auf eine Ersatzkennung
-- umgehaengt und die player_state-Zeile geloescht. Ein INNER JOIN laesst genau
-- diese Zeilen herausfallen -- die Zahlen bleiben gezaehlt, das Konto erscheint
-- nirgends. Der Filter sitzt damit an der frueheste moeglichen Stelle: eine
-- Ansicht, die filtern muesste, ist eine Ansicht, die es vergessen kann.

-- --- Allzeit -----------------------------------------------------------------

CREATE MATERIALIZED VIEW rpg.mv_stat_alltime AS
SELECT
    d.metric                                     AS metric,
    d.player_id                                  AS player_id,
    CASE
        WHEN split_part(d.metric, '.', 1) IN ('damage_max') THEN MAX(d.value)
        ELSE SUM(d.value)
    END                                          AS value
FROM rpg.player_statistic_daily d
JOIN rpg.player_state ps
  ON ps.player_id = d.player_id
 AND ps.anonymized = FALSE
GROUP BY d.metric, d.player_id;

CREATE UNIQUE INDEX mv_stat_alltime_key
    ON rpg.mv_stat_alltime (metric, player_id);

COMMENT ON MATERIALIZED VIEW rpg.mv_stat_alltime IS
    'B12: Allzeitstand je Metrikschluessel und Konto. Anonymisierte Konten fehlen (FR-039).';

-- --- Woche (ISO, UTC) --------------------------------------------------------

CREATE MATERIALIZED VIEW rpg.mv_stat_week AS
SELECT
    EXTRACT(ISOYEAR FROM d.day)::INTEGER         AS iso_year,
    EXTRACT(WEEK    FROM d.day)::INTEGER         AS iso_week,
    d.metric                                     AS metric,
    d.player_id                                  AS player_id,
    CASE
        WHEN split_part(d.metric, '.', 1) IN ('damage_max') THEN MAX(d.value)
        ELSE SUM(d.value)
    END                                          AS value
FROM rpg.player_statistic_daily d
JOIN rpg.player_state ps
  ON ps.player_id = d.player_id
 AND ps.anonymized = FALSE
GROUP BY 1, 2, d.metric, d.player_id;

CREATE UNIQUE INDEX mv_stat_week_key
    ON rpg.mv_stat_week (iso_year, iso_week, metric, player_id);

COMMENT ON MATERIALIZED VIEW rpg.mv_stat_week IS
    'B12: Wochenstand. ISO-Woche ab Montag, in UTC -- wie die gespeicherte Tagesangabe (FR-026).';

-- --- Tag ---------------------------------------------------------------------

CREATE MATERIALIZED VIEW rpg.mv_stat_day AS
SELECT
    d.day                                        AS day,
    d.metric                                     AS metric,
    d.player_id                                  AS player_id,
    d.value                                      AS value
FROM rpg.player_statistic_daily d
JOIN rpg.player_state ps
  ON ps.player_id = d.player_id
 AND ps.anonymized = FALSE;

CREATE UNIQUE INDEX mv_stat_day_key
    ON rpg.mv_stat_day (day, metric, player_id);

COMMENT ON MATERIALIZED VIEW rpg.mv_stat_day IS
    'B12: Tagesstand. Keine Aggregation noetig -- die Tabelle ist bereits tagesgenau; die Sicht
     traegt nur den Anonymisierungsfilter und den Index fuer die Rangabfrage.';
