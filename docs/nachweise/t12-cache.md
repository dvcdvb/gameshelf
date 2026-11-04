# T12 – Cache und Aktualität

## Gecachter Anwendungsfall

**Auswertung „Top-Spiele“** (`GET /api/auswertungen/top-spiele`, `AuswertungService.topSpiele`).

**Warum dieser Fall?**
- Die Abfrage aggregiert über alle beendeten Einträge (mit den Testdaten über 95'000 Zeilen, JOIN mit Genre, `GROUP BY`, `HAVING`, Sortierung) – teuer im Vergleich zu einer Einzelabfrage.
- Sie wird häufig gelesen (z. B. Startseite einer App), aber die Daten ändern sich im Verhältnis selten: Eine einzelne neue Bewertung verschiebt einen Durchschnitt nur minimal.
- Andere Lesezugriffe eignen sich weniger: Die Spielerliste (A5) ist pro Spieler und Filter verschieden und muss nach jeder eigenen Änderung sofort stimmen; die Spielübersicht (A6) ist dank der redundanten Spalten bereits eine einfache Primärschlüssel-Abfrage.

## Was wird unter welchem Schlüssel gespeichert?

| | |
|---|---|
| Cache | `topSpiele` (Caffeine, im Arbeitsspeicher der Anwendung) |
| Wert | unveränderliche `List<TopSpielResponse>` (Records) |
| Schlüssel | `(minBewertungen, genreId, limit)` – genau die Parameter, die das Ergebnis bestimmen |
| Normalisierung | `limit` wird im Controller auf 1–100 begrenzt, damit gleiche Ergebnisse denselben Schlüssel haben |
| Grösse | max. 500 Einträge (verschiedene Parameter-Kombinationen) |
| Nicht gecacht | ungültige Anfragen (Exception, z. B. `minBewertungen < 1`) |

## Aktualitätsregel

> **Änderungen über die API, die eine Bewertung oder die angezeigten Spieldaten betreffen, sind beim nächsten Abruf sofort sichtbar. Änderungen an der Anwendung vorbei (z. B. Testdaten-Skript, manuelles SQL) sind spätestens nach 10 Minuten sichtbar.**

**Begründung aus fachlicher Sicht:** Wer ein Spiel bewertet, erwartet, dass seine Bewertung in der Rangliste berücksichtigt ist – deshalb Leeren bei jeder relevanten Änderung. Für Änderungen, die die Anwendung nicht mitbekommt, ist eine kurze Verzögerung bei einer Rangliste unkritisch; die TTL ist dafür das Sicherheitsnetz.

## Wie werden Änderungen berücksichtigt?

| Änderung | Wirkung auf den Cache | Wo |
|---|---|---|
| Neuer Eintrag GESPIELT/ABGEBROCHEN | ganzer Cache wird geleert | `EintragService.statistikAnpassen` |
| Rating eines beendeten Eintrags geändert | geleert | `EintragService.statistikAnpassen` |
| Statuswechsel von/zu GESPIELT/ABGEBROCHEN | geleert | `EintragService.statistikAnpassen` |
| Beendeter Eintrag gelöscht | geleert | `EintragService.statistikAnpassen` |
| Titel oder Genre eines Spiels geändert | geleert | `@CacheEvict` auf `SpielService.aendern` |
| GEPLANT ↔ AM_SPIELEN, Bewertungstext, Spielzeit | **nicht** geleert (kein Einfluss auf die Top-Liste) | – |
| Änderung direkt in der DB | nicht erkannt → spätestens nach 10 min (TTL) | `CacheConfig` |

**Warum den ganzen Cache leeren statt einzelner Schlüssel?** Eine neue Bewertung für ein Spiel kann jede Top-Liste verändern (alle Genres, verschiedene Mindestanzahlen, Platzierungen anderer Spiele). Die betroffenen Schlüssel einzeln zu bestimmen wäre fehleranfällig; der Cache ist klein und schnell neu gefüllt.

**Transaktionsbewusst (`TransactionAwareCacheManagerProxy`):** Der Cache wird erst **nach dem Commit** geleert. Würde er vorher geleert, könnte ein gleichzeitiger Lesezugriff noch den alten Datenbankstand laden und wieder in den Cache schreiben. Bei einem **Rollback** wird gar nicht geleert – die Daten haben sich ja nicht geändert.

## Nachweis

Automatisiert in `CacheTest` (Caffeine-Statistik `recordStats()`: Treffer und Fehlzugriffe):

| Test | Nachweis |
|---|---|
| `ersterZugriffUndTreffer` | 1. Zugriff: 1 Fehlzugriff (DB), 2. Zugriff: 1 Treffer, identisches Objekt |
| `trefferKommtNichtAusDerDatenbank` | DB direkt geändert → Treffer liefert weiterhin den gespeicherten Stand (Beweis, dass keine Abfrage stattfand; zeigt die TTL-Regel) |
| `neueBewertungLeertCache` | neue Bewertung über die API → nächster Zugriff lädt neu, Durchschnitt sofort 9.00 statt 8.00 |
| `statuswechselLeertCache` | Statuswechsel mit Rating → Top-Liste sofort aktuell |
| `titelaenderungLeertCache` | geänderter Spieltitel sofort sichtbar |
| `irrelevanteAenderungBehaeltCache` | GEPLANT-Eintrag → weiterhin Treffer |
| `schluesselProParameter` | 3 verschiedene Parameter = 3 Einträge, Wiederholung = Treffer |
| `rollbackLeertCacheNicht` | gescheiterter Statuswechsel → Cache bleibt gefüllt |

**Manuelle Vorführung** mit `http/schritt11.http` und dem Profil `cachelog`, aufgenommen am 04.11.2026:

```bash
./mvnw spring-boot:run -Dspring-boot.run.profiles=cachelog
```

```
--- 1. ERSTER ZUGRIFF: GET /auswertungen/top-spiele?genreId=14 ---
CacheInterceptor : Computed cache key '[1, 14, 10]' for operation ... topSpiele(...)
CacheInterceptor : No cache entry for key '[1, 14, 10]' in cache(s) [topSpiele]
JdbcTemplate      : Executing prepared SQL query
JdbcTemplate      : Executing prepared SQL statement [SELECT v.spiel_id, v.spiel_titel, g.name AS genre_name,
       COUNT(*) AS anzahl_bewertungen, ROUND(AVG(v.rating), 2) AS durchschnitt
  FROM v_beendeter_eintrag v JOIN genre g ON g.id = v.genre_id
 WHERE v.genre_id = ? GROUP BY v.spiel_id, v.spiel_titel, g.name
HAVING COUNT(*) >= ? ORDER BY durchschnitt DESC, anzahl_bewertungen DESC, v.spiel_id LIMIT ?]
CacheInterceptor : Creating cache entry for key '[1, 14, 10]' in cache(s) [topSpiele]
-- Antwort: anzahlBewertungen=1, durchschnittsRating=8.00

--- 2. WIEDERHOLTER ZUGRIFF: gleiche Anfrage ---
CacheInterceptor : Computed cache key '[1, 14, 10]' for operation ... topSpiele(...)
CacheInterceptor : Cache entry for key '[1, 14, 10]' found in cache(s) [topSpiele]
-- kein JdbcTemplate-Log, also kein SQL; Antwort identisch: Ø 8.00

--- 3. RELEVANTE ÄNDERUNG: zweite Bewertung (10) für dasselbe Spiel ---

--- 4. ZUGRIFF NACH DER ÄNDERUNG: gleiche Anfrage ---
CacheInterceptor : Computed cache key '[1, 14, 10]' for operation ... topSpiele(...)
CacheInterceptor : No cache entry for key '[1, 14, 10]' in cache(s) [topSpiele]
JdbcTemplate      : Executing prepared SQL query
JdbcTemplate      : Executing prepared SQL statement [SELECT v.spiel_id, ... ]
CacheInterceptor : Creating cache entry for key '[1, 14, 10]' in cache(s) [topSpiele]
-- Antwort sofort aktuell: anzahlBewertungen=2, durchschnittsRating=9.00
```

Derselbe Schlüssel `[1, 14, 10]` (minBewertungen=1, genreId=14, limit=10) liefert vor der Änderung einen Treffer ohne SQL und nach der Änderung einen Fehlzugriff mit neuem SQL — genau das belegt sowohl den Cache-Treffer als auch die Aktualitätsregel „Leeren bei relevanter Änderung“.

**Abgrenzung zu T10:** Die Performancemessung in T10 lief direkt in PostgreSQL, also ohne diesen Anwendungscache.

## Einschränkungen

- Der Cache liegt im Speicher **einer** Anwendungsinstanz. Bei mehreren Instanzen müsste ein gemeinsamer Cache (z. B. Redis) oder eine Benachrichtigung zwischen den Instanzen verwendet werden.
- Nach dem Testdaten-Skript bei laufender Anwendung: bis zu 10 Minuten alte Top-Listen (oder Anwendung neu starten).
