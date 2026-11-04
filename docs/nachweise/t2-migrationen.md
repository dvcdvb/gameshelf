# T2 – Migrationen: Neuaufbau und Schemaentwicklung

## Migrationen

| Version | Datei | Inhalt |
|---|---|---|
| V1 | `V1__initial_schema.sql` | Tabellen `genre`, `spiel`, `spieler`, `eintrag`, `statuswechsel` inkl. aller Constraints |
| V2 | `V2__auswertung_view.sql` | View `v_beendeter_eintrag` |
| V3 | `V3__index_abschluesse.sql` | Partieller Index `ix_eintrag_gespielt_abschluss` (Grundlage für T10) |

Alle drei Migrationen sind bereits angewendet und werden **nicht mehr verändert**; eine Änderung würde Flyway (`checksum mismatch`) beim nächsten Start sofort verhindern. Jede weitere Schemaänderung kommt als neue `V4__…`-Datei dazu.

## Nachweis 1: Neuaufbau ab leerer Datenbank

Automatisiert in `MigrationTest.neuaufbauAbLeererDatenbank`: Ein frischer, leerer Testcontainer wird direkt (ohne Spring Boot, nur mit der Flyway-API) migriert.

```
Schema history table "public"."flyway_schema_history" does not exist yet
Successfully validated 3 migrations (execution time 00:00.002s)
Current version of schema "public": << Empty Schema >>
Migrating schema "public" to version "1 - initial schema"
Migrating schema "public" to version "2 - auswertung view"
Migrating schema "public" to version "3 - index abschluesse"
Successfully applied 3 migrations to schema "public", now at version v3 (execution time 00:00.022s)
```

Der Test prüft danach zusätzlich, dass keine Migration mehr aussteht (`flyway.info().pending()` ist leer) und dass sowohl die Tabelle `eintrag` als auch die View `v_beendeter_eintrag` existieren.

## Nachweis 2: Übergang vom alten (V1) zum neuen Schema

Automatisiert in `MigrationTest.uebergangVonV1`: Dieselbe leere Datenbank wird zuerst **nur bis V1** migriert (`.target("1")`) – das entspricht dem Stand vor den späteren Erweiterungen:

```
Current version of schema "public": << Empty Schema >>
Migrating schema "public" to version "1 - initial schema"
Successfully applied 1 migration to schema "public", now at version v1 (execution time 00:00.016s)
```

Danach werden auf diesem **alten Schema mit Daten** (ein Genre, ein Spiel, ein Spieler, ein beendeter Eintrag) die restlichen Migrationen nachgezogen:

```
Successfully validated 3 migrations (execution time 00:00.004s)
Current version of schema "public": 1
Migrating schema "public" to version "2 - auswertung view"
Migrating schema "public" to version "3 - index abschluesse"
Successfully applied 2 migrations to schema "public", now at version v3 (execution time 00:00.006s)
```

Der Test prüft, dass die vor der Migration angelegten Daten erhalten bleiben und über die **neue** View `v_beendeter_eintrag` korrekt sichtbar sind (`rating = 7` für `'Alt-Spiel'`).

**Manueller Nachweis derselben Sache** über die Anwendung selbst (nicht nur im Test):

```bash
docker compose down -v && docker compose up -d
./mvnw spring-boot:run -Dspring-boot.run.arguments=--spring.flyway.target=1   # nur bis V1
# … Anwendung stoppen, Daten bleiben im Volume …
./mvnw spring-boot:run                                                       # migriert automatisch auf V3
```

## Reproduzierbarkeit

Beide Testfälle legen pro Lauf eine **eigene, frische Datenbank** im Testcontainer an (`CREATE DATABASE migration_<nanoTime>`), damit sie unabhängig von anderen Tests und beliebig oft wiederholbar sind.

```bash
./mvnw test -Dtest=MigrationTest
```

Ausführung am 04.11.2026: `Tests run: 2, Failures: 0, Errors: 0, Skipped: 0`.
