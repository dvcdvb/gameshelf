-- =====================================================================
-- GameShelf – Performanceanalyse (T10)
--
-- Abfrage:   Auswertung "Abschlüsse pro Monat" (AuswertungAbfrage.abschluesseProMonat),
--            exakt dasselbe SQL wie in der Anwendung, ohne Spielerfilter.
-- Parameter: von = 2026-01-01, bis = 2026-03-31 (-> bisExklusiv = 2026-04-01)
-- Aufruf:    ./scripts/performance-analyse.sh
--
-- Die Messung läuft direkt in PostgreSQL (psql) – unabhängig von der Anwendung
-- und ihrem Cache (T12). Gemessen wird die "Execution Time" aus EXPLAIN ANALYZE.
-- =====================================================================

\set ON_ERROR_STOP on
\pset footer off

-- Gleiche Bedingungen für beide Messungen: keine parallelen Worker
SET max_parallel_workers_per_gather = 0;

\echo
\echo '=================== UMGEBUNG ==================='
SELECT version();
SELECT name, setting, unit FROM pg_settings
 WHERE name IN ('shared_buffers', 'work_mem', 'random_page_cost', 'effective_cache_size');

\echo '=================== DATENMENGE ==================='
SELECT (SELECT COUNT(*) FROM eintrag)                              AS eintraege_total,
       (SELECT COUNT(*) FROM eintrag WHERE status = 'GESPIELT')    AS davon_gespielt,
       (SELECT COUNT(*) FROM eintrag
         WHERE status = 'GESPIELT'
           AND abgeschlossen_am >= DATE '2026-01-01'
           AND abgeschlossen_am <  DATE '2026-04-01')              AS treffer_im_zeitraum,
       pg_size_pretty(pg_relation_size('eintrag'))                 AS tabellengroesse;

-- Hilfsfunktion: führt eine Abfrage n-mal mit EXPLAIN ANALYZE aus und liefert die Ausführungszeiten.
-- pg_temp = existiert nur in dieser Sitzung, verändert das Schema nicht.
CREATE FUNCTION pg_temp.messen(abfrage text, warmlaeufe int, messlaeufe int)
    RETURNS TABLE (messungen int, min_ms numeric, median_ms numeric, max_ms numeric, alle_ms text)
    LANGUAGE plpgsql AS $$
DECLARE
    plan json;
    zeiten numeric[] := '{}';
BEGIN
    FOR i IN 1..warmlaeufe LOOP                      -- Cache aufwärmen, nicht gewertet
        EXECUTE 'EXPLAIN (ANALYZE, FORMAT JSON) ' || abfrage INTO plan;
    END LOOP;
    FOR i IN 1..messlaeufe LOOP
        EXECUTE 'EXPLAIN (ANALYZE, FORMAT JSON) ' || abfrage INTO plan;
        zeiten := zeiten || round((plan -> 0 ->> 'Execution Time')::numeric, 3);
    END LOOP;
    RETURN QUERY
        SELECT messlaeufe,
               (SELECT min(z) FROM unnest(zeiten) z),
               (SELECT percentile_cont(0.5) WITHIN GROUP (ORDER BY z)::numeric(10,3) FROM unnest(zeiten) z),
               (SELECT max(z) FROM unnest(zeiten) z),
               array_to_string(zeiten, ' | ');
END $$;

-- Die untersuchte Abfrage (Quartal) und eine Gegenprobe (ganzer Zeitraum = kaum selektiv)
\set abfrage_quartal '\'SELECT CAST(date_trunc(\'\'month\'\', v.abgeschlossen_am) AS date) AS monat, COUNT(*) AS anzahl, ROUND(AVG(v.spielzeit_std), 1) AS durchschnitt_spielzeit FROM v_beendeter_eintrag v WHERE v.status = \'\'GESPIELT\'\' AND v.abgeschlossen_am >= DATE \'\'2026-01-01\'\' AND v.abgeschlossen_am < DATE \'\'2026-04-01\'\' GROUP BY 1 ORDER BY 1\''
\set abfrage_alles   '\'SELECT CAST(date_trunc(\'\'month\'\', v.abgeschlossen_am) AS date) AS monat, COUNT(*) AS anzahl, ROUND(AVG(v.spielzeit_std), 1) AS durchschnitt_spielzeit FROM v_beendeter_eintrag v WHERE v.status = \'\'GESPIELT\'\' AND v.abgeschlossen_am >= DATE \'\'2019-01-01\'\' AND v.abgeschlossen_am < DATE \'\'2027-01-01\'\' GROUP BY 1 ORDER BY 1\''

-- ---------------------------------------------------------------------
-- VORHER: reproduzierbarer Ausgangszustand = Schema V2 (ohne Index aus V3)
-- ---------------------------------------------------------------------
DROP INDEX IF EXISTS ix_eintrag_gespielt_abschluss;
ANALYZE eintrag;

\echo
\echo '=================== VORHER: Plan (Quartal) ==================='
EXPLAIN (ANALYZE, BUFFERS)
SELECT CAST(date_trunc('month', v.abgeschlossen_am) AS date) AS monat,
       COUNT(*) AS anzahl, ROUND(AVG(v.spielzeit_std), 1) AS durchschnitt_spielzeit
  FROM v_beendeter_eintrag v
 WHERE v.status = 'GESPIELT'
   AND v.abgeschlossen_am >= DATE '2026-01-01'
   AND v.abgeschlossen_am <  DATE '2026-04-01'
 GROUP BY 1 ORDER BY 1;

\echo '=================== VORHER: Messung (3 Warmläufe, 10 Messungen) ==================='
SELECT 'Quartal' AS abfrage, * FROM pg_temp.messen(:abfrage_quartal, 3, 10)
UNION ALL
SELECT 'ganzer Zeitraum', * FROM pg_temp.messen(:abfrage_alles, 3, 10);

-- ---------------------------------------------------------------------
-- OPTIMIERUNG: Index aus V3 (identische Definition)
-- ---------------------------------------------------------------------
CREATE INDEX IF NOT EXISTS ix_eintrag_gespielt_abschluss ON eintrag (abgeschlossen_am) WHERE status = 'GESPIELT';
ANALYZE eintrag;

\echo
\echo '=================== NACHHER: Plan (Quartal) ==================='
EXPLAIN (ANALYZE, BUFFERS)
SELECT CAST(date_trunc('month', v.abgeschlossen_am) AS date) AS monat,
       COUNT(*) AS anzahl, ROUND(AVG(v.spielzeit_std), 1) AS durchschnitt_spielzeit
  FROM v_beendeter_eintrag v
 WHERE v.status = 'GESPIELT'
   AND v.abgeschlossen_am >= DATE '2026-01-01'
   AND v.abgeschlossen_am <  DATE '2026-04-01'
 GROUP BY 1 ORDER BY 1;

\echo '=================== NACHHER: Messung (3 Warmläufe, 10 Messungen) ==================='
SELECT 'Quartal' AS abfrage, * FROM pg_temp.messen(:abfrage_quartal, 3, 10)
UNION ALL
SELECT 'ganzer Zeitraum', * FROM pg_temp.messen(:abfrage_alles, 3, 10);

\echo
\echo '=================== NACHHER: Plan (ganzer Zeitraum, Gegenprobe) ==================='
EXPLAIN (ANALYZE, BUFFERS)
SELECT CAST(date_trunc('month', v.abgeschlossen_am) AS date) AS monat,
       COUNT(*) AS anzahl, ROUND(AVG(v.spielzeit_std), 1) AS durchschnitt_spielzeit
  FROM v_beendeter_eintrag v
 WHERE v.status = 'GESPIELT'
   AND v.abgeschlossen_am >= DATE '2019-01-01'
   AND v.abgeschlossen_am <  DATE '2027-01-01'
 GROUP BY 1 ORDER BY 1;

\echo '=================== KOSTEN DES INDEX ==================='
SELECT indexrelname                                 AS index,
       pg_size_pretty(pg_relation_size(indexrelid)) AS groesse
  FROM pg_stat_user_indexes
 WHERE relname = 'eintrag'
 ORDER BY pg_relation_size(indexrelid) DESC;
