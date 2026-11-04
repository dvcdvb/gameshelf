# T11 – ORM-Ladeverhalten

## Grundentscheidungen im Mapping

- Alle `@ManyToOne` sind **`FetchType.LAZY`** (JPA-Standard wäre `EAGER`). Sonst würde z. B. jeder geladene Eintrag automatisch Spieler, Spiel und Genre mitladen – auch wenn nur der Status gebraucht wird.
- Es gibt **keine `@OneToMany`-Listen** (z. B. `Spiel.eintraege`): Ein beliebtes Spiel hat über 1'500 Einträge (Testdaten), die nie vollständig gebraucht werden. Listen werden gezielt und seitenweise über Abfragen geladen.
- `spring.jpa.open-in-view: false`: Lazy Loading ist nur innerhalb der Service-Transaktion möglich. Vergessene Ladeentscheidungen fallen dadurch sofort auf, statt im Controller still zusätzliche Abfragen auszulösen.

**Folge:** LAZY verhindert unnötiges Mitladen, kann aber beim Zugriff in einer Schleife zum **N+1-Problem** führen (1 Abfrage für die Liste + 1 Abfrage pro Element). Deshalb wird pro Lesezugriff entschieden, was mitgeladen wird.

## Messmethode

Automatisierter Test `LadeverhaltenTest` gegen PostgreSQL (Testcontainers). Er zählt mit den **Hibernate Statistics** die ausgeführten SQL-Statements (`getPrepareStatementCount`) und die geladenen Entities (`getEntityLoadCount`) für **5, 20 und 50** Elemente. Jedes Spiel hat ein eigenes Genre (schlimmster Fall). Jeder Zugriff läuft in einer eigenen Lese-Transaktion wie im Service.

```bash
./mvnw test -Dtest=LadeverhaltenTest                                        # Tabellen
./mvnw test -Dtest=LadeverhaltenTest -Dlogging.level.org.hibernate.SQL=debug # zusätzlich jedes SQL
```

## Untersuchung 1: Spielsuche (`GET /api/spiele`) – Spiel → Genre

**Datenbedarf:** Die Antwort enthält pro Spiel den Genrenamen (`SpielResponse.genreName`). Das Genre wird also für **jedes** Spiel gebraucht.

| Spiele | ohne `@EntityGraph` (vorher) | mit `@EntityGraph` (umgesetzt) |
|---|---|---|
| 5 | 6 SQL / 10 Entities | 1 SQL / 10 Entities |
| 20 | 21 SQL / 40 Entities | 1 SQL / 40 Entities |
| 50 | 51 SQL / 100 Entities | 1 SQL / 100 Entities |

**Problem (vorher):** `findAll(pageable)` lädt nur die Spiele; das Genre ist ein LAZY-Proxy. Beim Zugriff auf `getGenre().getName()` führt Hibernate **pro Spiel ein eigenes** `select … from genre where id = ?` aus → **1 + N** Abfragen, linear wachsend.

**Verbesserung:** `@EntityGraph(attributePaths = "genre")` auf `SpielRepository.findByTitelContainingIgnoreCase` → Hibernate lädt Spiele und Genres in **einer** Abfrage mit `JOIN`, unabhängig von der Listengrösse.

**Warum `@EntityGraph` und nicht `EAGER`?** Mit `EAGER` würde das Genre überall mitgeladen, auch wo es nicht gebraucht wird (z. B. das Genre beim Statuswechsel, der es gar nicht braucht). Der EntityGraph gilt nur für diesen Anwendungsfall. `JOIN` ist hier unproblematisch, weil `ManyToOne` keine Zeilen vervielfacht und die Pagination deshalb weiterhin in der Datenbank stattfindet.

## Untersuchung 2: Liste eines Spielers (A5) – Eintrag → Spiel → Genre

**Datenbedarf:** 10 Felder für eine reine Anzeige (Spieltitel, Genrename, Status, Rating, Daten …). Nichts davon wird verändert; der Bewertungstext (bis 2'000 Zeichen) wird in der Liste nicht gebraucht.

| Einträge | Entities naiv | Entities mit `JOIN FETCH` | DTO-Projektion (umgesetzt) |
|---|---|---|---|
| 5 | 11 SQL / 15 Entities | 1 SQL / 15 Entities | 3 SQL / 0 Entities |
| 20 | 41 SQL / 60 Entities | 1 SQL / 60 Entities | 3 SQL / 0 Entities |
| 50 | 101 SQL / 150 Entities | 1 SQL / 150 Entities | 3 SQL / 0 Entities |

**Naiv:** Pro Eintrag wird das Spiel und pro Spiel das Genre einzeln nachgeladen → **1 + 2N** Abfragen.

**`JOIN FETCH`:** löst das N+1-Problem (1 Abfrage), lädt aber **ganze Entities** inkl. aller Spalten (auch `bewertungstext`) und hält sie im Persistence Context (Änderungsverfolgung) – unnötig für eine reine Anzeige.

**DTO-Projektion (umgesetzt, `EintragListeAbfrage`):** `select new EintragListenElement(…)` liest nur die 10 benötigten Spalten in einer Abfrage, **ohne Entities**. Die 3 Statements sind konstant: Prüfung, ob der Spieler existiert, die Seite selbst und die `count`-Abfrage für die Seitenzahl.

## Entscheidung und Begründung

| Lesezugriff | Ladeentscheidung | Begründung aus dem Datenbedarf |
|---|---|---|
| Spielsuche / Spielübersicht | Entity + `@EntityGraph("genre")` | Genre wird für jedes Element gebraucht; Antwort entspricht dem Spiel-Objekt |
| Liste eines Spielers | DTO-Projektion | Reine Anzeige über 3 Tabellen, nur Teil der Spalten nötig, keine Änderung |
| Einzelner Eintrag (`GET /eintraege/{id}`) | Entity, Spiel LAZY nachladen | 1 Element → höchstens 1 zusätzliche Abfrage, kein N+1 |
| Statuswechsel | Entity, Spiel LAZY | Für die Statistik genügt `getSpiel().getId()` (lädt den Proxy nicht); erst die Antwort braucht den Titel → genau 1 zusätzliche Abfrage |

**Alternative, bewusst nicht gewählt:** `hibernate.default_batch_fetch_size` würde N+1 abschwächen (Nachladen in Gruppen, z. B. 1 + N/16 Abfragen), bleibt aber abhängig von der Listengrösse und lädt weiterhin ganze Entities.
