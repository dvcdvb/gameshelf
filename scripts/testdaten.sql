-- =====================================================================
-- GameShelf – reproduzierbare Testdaten für die Performanceanalyse (T9)
--
-- ACHTUNG: Löscht ALLE vorhandenen Daten und erzeugt sie neu.
-- Aufruf:  ./scripts/testdaten-erzeugen.sh
--
-- Reproduzierbar: setseed() legt den Zufallsgenerator fest -> jeder Lauf
-- erzeugt exakt dieselben Daten. Keine echten Personendaten: alle Namen sind
-- künstlich ("spieler_0001", "Spiel 00001").
-- =====================================================================

\timing on
\set ON_ERROR_STOP on

-- Paralleles Ausführen würde die Reihenfolge der random()-Aufrufe und damit
-- die Daten verändern -> für Reproduzierbarkeit abschalten (nur in dieser Session).
SET max_parallel_workers_per_gather = 0;

BEGIN;

TRUNCATE statuswechsel, eintrag, spiel, spieler, genre RESTART IDENTITY CASCADE;

SELECT setseed(0.42);

-- ---------------------------------------------------------------------
-- 12 Genres
-- ---------------------------------------------------------------------
INSERT INTO genre (name)
SELECT unnest(ARRAY['Action', 'Adventure', 'RPG', 'Strategie', 'Shooter', 'Sport',
                    'Rennspiel', 'Puzzle', 'Simulation', 'Horror', 'Plattformer', 'Roguelike']);

-- ---------------------------------------------------------------------
-- 5'000 Spiele, ungleich auf Genres verteilt (Action/RPG häufig, Puzzle selten)
-- ---------------------------------------------------------------------
INSERT INTO spiel (genre_id, titel, erscheinungsjahr, aktiv)
SELECT 1 + floor(12 * power(random(), 1.4))::int,          -- kleine genre_id häufiger
       'Spiel ' || lpad(g::text, 5, '0'),
       1990 + floor(random() * 37)::int,                     -- 1990..2026
       random() > 0.02                                       -- ca. 2 % deaktiviert
  FROM generate_series(1, 5000) AS g;

-- ---------------------------------------------------------------------
-- 2'000 Spieler
-- ---------------------------------------------------------------------
INSERT INTO spieler (benutzername, erstellt_am)
SELECT 'spieler_' || lpad(g::text, 4, '0'),
       TIMESTAMPTZ '2019-01-01' + random() * INTERVAL '2400 days'
  FROM generate_series(1, 2000) AS g;

-- ---------------------------------------------------------------------
-- Einträge
--   * Spieler-Aktivität ungleich: wenige Vielspieler, viele Gelegenheitsspieler
--   * Spiel-Beliebtheit ungleich ("long tail"): wenige Spiele sehr oft
--   * doppelte (spieler, spiel)-Paare fallen weg (ON CONFLICT DO NOTHING)
-- ---------------------------------------------------------------------
CREATE TEMP TABLE kandidat ON COMMIT DROP AS
SELECT 1 + floor(2000 * power(random(), 1.6))::int AS spieler_id,
       1 + floor(5000 * power(random(), 2.2))::int AS spiel_id,
       random() AS z_status,
       random() AS z_rating,
       random() AS z_datum,
       random() AS z_dauer,
       random() AS z_zeit
  FROM generate_series(1, 165000);

-- Status abhängig vom Genre: Horror/Roguelike/Strategie werden öfter abgebrochen
CREATE TEMP TABLE basis ON COMMIT DROP AS
SELECT k.*,
       CASE
           WHEN k.z_status < 0.30 THEN 'GEPLANT'
           WHEN k.z_status < 0.40 THEN 'AM_SPIELEN'
           WHEN k.z_status < 0.40 + 0.60 * (CASE s.genre_id WHEN 10 THEN 0.45
                                                             WHEN 12 THEN 0.40
                                                             WHEN 4  THEN 0.35
                                                             ELSE 0.20 END)
                THEN 'ABGEBROCHEN'
           ELSE 'GESPIELT'
       END AS status
  FROM kandidat k
  JOIN spiel s ON s.id = k.spiel_id;

INSERT INTO eintrag (spieler_id, spiel_id, status, rating, spielzeit_std,
                     gestartet_am, abgeschlossen_am, aktualisiert_am)
SELECT b.spieler_id,
       b.spiel_id,
       b.status,
       -- Rating: gespielt eher hoch (5..10), abgebrochen eher tief (1..6), sonst teils leer
       CASE b.status
           WHEN 'GESPIELT'    THEN 5 + floor(6 * power(b.z_rating, 0.7))::int
           WHEN 'ABGEBROCHEN' THEN 1 + floor(6 * power(b.z_rating, 1.5))::int
           WHEN 'AM_SPIELEN'  THEN CASE WHEN b.z_rating < 0.5 THEN 3 + floor(8 * b.z_rating * 2)::int END
           ELSE NULL
       END,
       -- Spielzeit in Stunden (eine Nachkommastelle)
       CASE b.status
           WHEN 'GESPIELT'    THEN round((5 + 115 * power(b.z_dauer, 2))::numeric, 1)
           WHEN 'ABGEBROCHEN' THEN round((0.5 + 15 * b.z_dauer)::numeric, 1)
           WHEN 'AM_SPIELEN'  THEN round((1 + 40 * b.z_dauer)::numeric, 1)
       END,
       -- Startdatum: bei allen ausser GEPLANT
       CASE WHEN b.status <> 'GEPLANT'
            THEN DATE '2019-01-01' + floor(power(b.z_datum, 0.6) * 2850)::int
                 - (10 + floor(80 * b.z_dauer))::int
       END,
       -- Abschlussdatum: nur gespielt/abgebrochen, 2019 bis Ende Okt. 2026, neuere Jahre häufiger
       CASE WHEN b.status IN ('GESPIELT', 'ABGEBROCHEN')
            THEN DATE '2019-01-01' + floor(power(b.z_datum, 0.6) * 2850)::int
       END,
       TIMESTAMPTZ '2019-01-01' + power(b.z_zeit, 0.5) * INTERVAL '2850 days'
  FROM basis b
ON CONFLICT (spieler_id, spiel_id) DO NOTHING;

-- ---------------------------------------------------------------------
-- Verlauf: jeder Eintrag hat mind. einen Statuswechsel (wie in der Anwendung)
-- ---------------------------------------------------------------------
INSERT INTO statuswechsel (eintrag_id, alter_status, neuer_status, geaendert_am)
SELECT id,
       NULL,
       CASE WHEN status IN ('GESPIELT', 'ABGEBROCHEN') THEN 'AM_SPIELEN' ELSE status END,
       COALESCE(gestartet_am::timestamptz, aktualisiert_am)
  FROM eintrag;

INSERT INTO statuswechsel (eintrag_id, alter_status, neuer_status, geaendert_am)
SELECT id, 'AM_SPIELEN', status, abgeschlossen_am::timestamptz
  FROM eintrag
 WHERE status IN ('GESPIELT', 'ABGEBROCHEN');

-- ---------------------------------------------------------------------
-- Redundante Statistik aus den Einträgen berechnen (bleibt so konsistent)
-- ---------------------------------------------------------------------
UPDATE spiel s
   SET anzahl_bewertungen = a.anzahl,
       rating_summe       = a.summe
  FROM (SELECT spiel_id, COUNT(*) AS anzahl, SUM(rating) AS summe
          FROM v_beendeter_eintrag
         GROUP BY spiel_id) a
 WHERE a.spiel_id = s.id;

COMMIT;

-- Statistiken für den Query-Planer aktualisieren (wichtig für EXPLAIN ANALYZE, T10)
ANALYZE;

-- ---------------------------------------------------------------------
-- Kontrolle
-- ---------------------------------------------------------------------
\echo
\echo '=== Datensätze pro Tabelle ==='
SELECT 'genre' AS tabelle, COUNT(*) FROM genre
UNION ALL SELECT 'spiel', COUNT(*) FROM spiel
UNION ALL SELECT 'spieler', COUNT(*) FROM spieler
UNION ALL SELECT 'eintrag', COUNT(*) FROM eintrag
UNION ALL SELECT 'statuswechsel', COUNT(*) FROM statuswechsel;

\echo '=== Verteilung nach Status ==='
SELECT status, COUNT(*) AS anzahl, ROUND(100.0 * COUNT(*) / SUM(COUNT(*)) OVER (), 1) AS prozent
  FROM eintrag GROUP BY status ORDER BY anzahl DESC;

\echo '=== Einträge pro Spieler (Min / Median / Max) ==='
SELECT MIN(n), percentile_disc(0.5) WITHIN GROUP (ORDER BY n) AS median, MAX(n)
  FROM (SELECT COUNT(*) AS n FROM eintrag GROUP BY spieler_id) x;

\echo '=== Einträge pro Spiel (Min / Median / Max) ==='
SELECT MIN(n), percentile_disc(0.5) WITHIN GROUP (ORDER BY n) AS median, MAX(n)
  FROM (SELECT COUNT(*) AS n FROM eintrag GROUP BY spiel_id) x;

\echo '=== Abschlüsse pro Jahr ==='
SELECT EXTRACT(YEAR FROM abgeschlossen_am)::int AS jahr, COUNT(*)
  FROM eintrag WHERE abgeschlossen_am IS NOT NULL GROUP BY 1 ORDER BY 1;

\echo '=== Prüfsumme (muss bei jedem Lauf gleich sein) ==='
SELECT md5(string_agg(spieler_id || ':' || spiel_id || ':' || status || ':' || COALESCE(rating::text, '-'),
                      ',' ORDER BY id)) AS pruefsumme
  FROM eintrag;
