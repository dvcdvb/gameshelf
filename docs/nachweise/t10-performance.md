# T10 – Performanceanalyse und Index

Alle Werte in diesem Dokument stammen unverändert aus `docs/nachweise/t10-ausgabe.txt`, erzeugt am 04.11.2026 mit `./scripts/performance-analyse.sh`.

## 1. Untersuchte Abfrage

Auswertung **„Abschlüsse pro Monat“** (A7), `AuswertungAbfrage.abschluesseProMonat` – exakt dasselbe SQL wie in der Anwendung, ohne Spielerfilter:

```sql
SELECT CAST(date_trunc('month', v.abgeschlossen_am) AS date) AS monat,
       COUNT(*) AS anzahl,
       ROUND(AVG(v.spielzeit_std), 1) AS durchschnitt_spielzeit
  FROM v_beendeter_eintrag v
 WHERE v.status = 'GESPIELT'
   AND v.abgeschlossen_am >= :von
   AND v.abgeschlossen_am <  :bisExklusiv
 GROUP BY 1
 ORDER BY 1;
```

**Parameter:** `von = 2026-01-01`, `bisExklusiv = 2026-04-01` (ein Quartal, alle Spieler).
**Gegenprobe:** ganzer Zeitraum 2019–2026.

**Warum diese Abfrage?** Sie ist eine typische Auswertung über *alle* Daten, wird mit wechselnden Zeiträumen aufgerufen und filtert auf Spalten, für die es noch keinen Index gibt. Andere Kandidaten wurden verworfen: Die Spielerliste (A5) nutzt bereits den Index des Unique-Constraints `(spieler_id, spiel_id)`.

## 2. Umgebung und Datenmenge

| | |
|---|---|
| Datenbank | PostgreSQL 17.11 (aarch64, Alpine) in Docker (`postgres:17-alpine`) |
| Rechner | MacBook Pro, Apple M1 Pro, 16 GB RAM |
| Einstellungen | `shared_buffers = 16384` (128 MB), `work_mem = 4096 kB`, `effective_cache_size = 524288` (4 GB), `random_page_cost = 4`; parallele Worker nicht gesondert deaktiviert, aber bei diesen Plänen nicht gewählt |
| Daten | `scripts/testdaten.sql`: 158'557 Einträge, davon 72'015 `GESPIELT`, 3'535 davon im untersuchten Quartal (≈ 2 %) |
| Tabellengrösse `eintrag` | 15 MB |

**Messmethode:** `EXPLAIN (ANALYZE)` direkt in PostgreSQL, also ohne Anwendung und ohne Anwendungscache. Pro Variante 3 Warmläufe (nicht gewertet, damit beide Varianten mit warmem Datenbank-Cache verglichen werden), danach 10 Messungen; verglichen wird der **Median** (robust gegen einzelne Ausreisser). Vor jeder Messung `ANALYZE`, damit der Planer aktuelle Statistiken hat.

**Reproduzierbarer Ausgangszustand:** Das Skript entfernt den Index zuerst (`DROP INDEX IF EXISTS`) – das entspricht exakt Schema V2 – und legt ihn danach mit derselben Definition wie in `V3__index_abschluesse.sql` wieder an.

## 3. Vorher (ohne Index)

```
Sort  (cost=5422.49..5425.03 rows=1016 width=44) (actual time=16.479..16.480 rows=3 loops=1)
  Sort Key: ((date_trunc('month'::text, (e.abgeschlossen_am)::timestamp with time zone))::date)
  Sort Method: quicksort  Memory: 25kB
  Buffers: shared hit=2043
  ->  HashAggregate  (cost=5348.88..5371.74 rows=1016 width=44) (actual time=16.459..16.466 rows=3 loops=1)
        Group Key: (date_trunc('month'::text, (e.abgeschlossen_am)::timestamp with time zone))::date
        Batches: 1  Memory Usage: 73kB
        Buffers: shared hit=2043
        ->  Hash Join  (cost=196.50..5339.42 rows=1262 width=10) (actual time=1.165..15.996 rows=3535 loops=1)
              Hash Cond: (e.spiel_id = s.id)
              Buffers: shared hit=2043
              ->  Seq Scan on eintrag e  (cost=0.00..5130.14 rows=1262 width=18) (actual time=0.008..13.489 rows=3535 loops=1)
                    Filter: (((status)::text = ANY ('{GESPIELT,ABGEBROCHEN}'::text[])) AND (abgeschlossen_am >= '2026-01-01'::date) AND (abgeschlossen_am < '2026-04-01'::date) AND ((status)::text = 'GESPIELT'::text))
                    Rows Removed by Filter: 155022
                    Buffers: shared hit=1959
              ->  Hash  (cost=134.00..134.00 rows=5000 width=8) (actual time=1.118..1.118 rows=5001 loops=1)
                    Buckets: 8192  Batches: 1  Memory Usage: 260kB
                    Buffers: shared hit=84
                    ->  Seq Scan on spiel s  (cost=0.00..134.00 rows=5000 width=8) (actual time=0.002..0.573 rows=5001 loops=1)
                          Buffers: shared hit=84
Planning:
  Buffers: shared hit=164
Planning Time: 0.896 ms
Execution Time: 16.600 ms
```

**Interpretation:** PostgreSQL liest mit einem **Seq Scan** die ganze Tabelle `eintrag` (1'959 Seiten) und prüft die Bedingung für jede Zeile: **155'022** Zeilen werden verworfen (*Rows Removed by Filter*), nur 3'535 (ca. 2 %) bleiben übrig. Fast die ganze Zeit (13.5 von 16.6 ms) entfällt auf diesen Scan.

## 4. Optimierung

```sql
CREATE INDEX ix_eintrag_gespielt_abschluss
    ON eintrag (abgeschlossen_am)
    WHERE status = 'GESPIELT';
```

**Begründung:**
- Die Abfrage filtert auf einen **Bereich** von `abgeschlossen_am` → B-Tree-Index auf genau dieser Spalte.
- Sie filtert immer auf `status = 'GESPIELT'` → **partieller Index**: Er enthält nur diese ca. 45 % der Zeilen. PostgreSQL erkennt, dass die Bedingung der Abfrage die Index-Bedingung erfüllt.
- Ein zusammengesetzter Index `(status, abgeschlossen_am)` wäre ebenfalls möglich, aber grösser und müsste auch bei Änderungen an geplanten Einträgen gepflegt werden.

## 5. Nachher (mit Index)

```
Sort  (cost=2415.88..2418.45 rows=1027 width=44) (actual time=4.608..4.609 rows=3 loops=1)
  Sort Key: ((date_trunc('month'::text, (e.abgeschlossen_am)::timestamp with time zone))::date)
  Sort Method: quicksort  Memory: 25kB
  Buffers: shared hit=1722 read=5
  ->  HashAggregate  (cost=2341.41..2364.51 rows=1027 width=44) (actual time=4.588..4.593 rows=3 loops=1)
        Group Key: (date_trunc('month'::text, (e.abgeschlossen_am)::timestamp with time zone))::date
        Batches: 1  Memory Usage: 73kB
        Buffers: shared hit=1722 read=5
        ->  Hash Join  (cost=230.46..2331.81 rows=1279 width=10) (actual time=1.376..4.138 rows=3535 loops=1)
              Hash Cond: (e.spiel_id = s.id)
              Buffers: shared hit=1722 read=5
              ->  Bitmap Heap Scan on eintrag e  (cost=33.96..2122.36 rows=1279 width=18) (actual time=0.418..1.968 rows=3535 loops=1)
                    Recheck Cond: ((abgeschlossen_am >= '2026-01-01'::date) AND (abgeschlossen_am < '2026-04-01'::date) AND ((status)::text = 'GESPIELT'::text))
                    Heap Blocks: exact=1638
                    Buffers: shared hit=1638 read=5
                    ->  Bitmap Index Scan on ix_eintrag_gespielt_abschluss  (cost=0.00..33.64 rows=2135 width=0) (actual time=0.272..0.272 rows=3535 loops=1)
                          Index Cond: ((abgeschlossen_am >= '2026-01-01'::date) AND (abgeschlossen_am < '2026-04-01'::date))
                          Buffers: shared read=5
              ->  Hash  (cost=134.00..134.00 rows=5000 width=8) (actual time=0.932..0.932 rows=5001 loops=1)
                    Buckets: 8192  Batches: 1  Memory Usage: 260kB
                    Buffers: shared hit=84
                    ->  Seq Scan on spiel s  (cost=0.00..134.00 rows=5000 width=8) (actual time=0.004..0.470 rows=5001 loops=1)
                          Buffers: shared hit=84
Planning:
  Buffers: shared hit=40 read=1
Planning Time: 0.388 ms
Execution Time: 4.714 ms
```

**Interpretation:** Statt des Seq Scans liest ein **Bitmap Index Scan** nur die passenden Einträge aus dem Index (5 gelesene Indexseiten) und holt danach gezielt die betroffenen Tabellenseiten (*Bitmap Heap Scan*, 1'638 Heap Blocks statt 1'959 beim Seq Scan). Es werden keine Zeilen mehr per Filter verworfen – `Rows Removed by Filter` fehlt im Plan ganz.

## 6. Messungen

| Abfrage | Median vorher | Median nachher | Faktor |
|---|---|---|---|
| Quartal (≈ 2 % der Zeilen) | 15.95 ms | 3.02 ms | ≈ 5.3× |
| ganzer Zeitraum (≈ 45 % der Zeilen) | 44.75 ms | 37.40 ms | ≈ 1.2× |

Min/Max je 10 Messungen: Quartal vorher 15.09–16.69 ms, nachher 2.97–3.52 ms; ganzer Zeitraum vorher 42.66–47.24 ms, nachher 34.89–39.98 ms. Einzelwerte aller Läufe: [`t10-ausgabe.txt`](t10-ausgabe.txt).

## 7. Erklärung des Ergebnisses

- **Quartal – deutlich schneller:** Der Index ist **selektiv**: Er führt direkt zu den ca. 2 % relevanten Zeilen, statt alle 158'557 Zeilen zu prüfen.
- **Ganzer Zeitraum – kaum Verbesserung:** Hier passen 72'013 Zeilen (alle gespielten, in der Gegenprobe). Der Bitmap Heap Scan muss trotzdem praktisch **alle** Tabellenseiten lesen (1'959 Heap Blocks laut Gegenprobe-Plan), weil die Einträge nicht nach Datum sortiert gespeichert sind. Der Index spart nur das Prüfen der Bedingung, nicht das Lesen. Ein Index lohnt sich also nur bei selektiven Abfragen – bei grossen Zeiträumen könnte der Planer auch wieder einen Seq Scan wählen.
- **Was die restliche Zeit kostet:** In beiden Plänen entfällt ein grosser Teil auf den **Hash Join mit `spiel`**, den die View enthält (alle 5'000 Spiele werden gelesen), obwohl diese Auswertung keine Spieldaten braucht. Eine weitere Optimierung wäre eine Abfrage direkt auf `eintrag` ohne View – bewusst nicht umgesetzt, weil die View die fachliche Regel „nur beendete Einträge“ zentral hält.
- **Untersuchte Alternative – Covering Index** `... INCLUDE (spiel_id, spielzeit_std)`: ermöglicht einen *Index Only Scan* (Tabelle muss nicht gelesen werden), war in der Ausführungszeit aber kaum schneller (der Join mit `spiel` dominiert ohnehin), ist deutlich grösser und funktioniert nur gut, wenn `VACUUM` die Visibility Map aktuell hält. → nicht übernommen.

## 8. Nutzen und Kosten des Index

| Nutzen | Kosten |
|---|---|
| Auswertung über kurze Zeiträume ca. 5.3× schneller (Quartal) | Speicher: 560 kB (zum Vergleich: Tabelle `eintrag` 15 MB, Unique-Index `uq_eintrag_spieler_spiel` 6'256 kB, Primärschlüssel 3'504 kB) |
| Auch die Variante mit Spielerfilter kombiniert ihn mit dem Unique-Index (*BitmapAnd*) | Jedes INSERT/UPDATE eines Eintrags mit Status `GESPIELT` muss den Index pflegen (z. B. beim Statuswechsel) |
| Klein, weil partiell (nur ~45 % der Zeilen, nur relevante Spalte) | Bei nicht selektiven Abfragen (ganzer Zeitraum) kaum Nutzen |

**Entscheidung:** Index übernehmen (Migration V3). GameShelf wird deutlich häufiger gelesen als geschrieben, ein Statuswechsel betrifft nur eine Zeile, und der Index ist dank `WHERE status = 'GESPIELT'` mit 560 kB klein.
