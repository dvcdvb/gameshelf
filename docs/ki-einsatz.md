# KI-Einsatz: Kennzeichnung und Herkunftsangaben

Gemäss *HFTM-KI-Richtlinie für Studierende, Version 1.1 (02.02.2026)*: Co-Creation von Code mit Kennzeichnung ist erlaubt (Ziff. 4.2), unreflektierte Übernahme ist es nicht (Ziff. 4.3). Dieses Dokument hält fest, **wo** KI mitgewirkt hat, **wie stark**, und **welche Stellen auf Kursmaterial zurückgehen** (Herkunftsangabe laut Auftrag).

Siehe auch [`hilfsmittelverzeichnis.md`](hilfsmittelverzeichnis.md) (Werkzeuge).

## Einstufung

**Art der Mitwirkung:**
- **Eigenständig** — ohne KI geschrieben.
- **Co-Creation** — mit Claude Code erarbeitet, von Fabio geprüft, verstanden und bei Bedarf angepasst; kann in der Verteidigung ohne KI erklärt werden.

**KI-Anteil innerhalb von Co-Creation** (wie viel vom ersten Entwurf bereits von Claude Code kam, bevor Fabio geprüft/angepasst hat — betrifft nie, OB geprüft wurde, nur WIE VIEL vorher schon da war):
- **Hoch** — grosser Teil des ersten Entwurfs kam von Claude Code; danach gelesen, verstanden und bei Bedarf angepasst.
- **Mittel** / **Mittel-Hoch** — Grundgerüst oder Idee von Claude Code, wesentliche Teile selbst ausformuliert oder umgebaut.
- **Niedrig** — nur punktuell Vorschläge genutzt, grösstenteils selbst geschrieben.

**Herkunft Kursmaterial** — Struktur/Idee stammt (auch) aus dem Unterricht, mit Angabe wo.

## Je Bereich

| Bereich / Dateien | Einstufung    | Herkunft Kursmaterial? | Bemerkung                                                                                                                                                                                                                      |
|---|---------------|------------------------|--------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `domain/` (Entities, `Status`-Enum) | *Mittel-Hoch* | *Nein*                 |                                                                                                                                                                                                                                |
| `dto/` (Records) | *Hoch*        | *Nein*                 |                                                                                                                                                                                                                                |
| `repository/` (JPQL-Projektion, JDBC-Auswertung) | *Mittel*      | *Nein*                 | T6: JDBC-Client-Muster – Herkunft prüfen                                                                                                                                                                                       |
| `service/` (Transaktion T4, Nebenläufigkeit T5, Cache T12) | *Mittel*      | *Nein*                 | zentrale Entscheidung – in Verteidigung ohne KI erklärbar sein                                                                                                                                                                 |
| `web/` (Controller, `GlobalExceptionHandler`) | *Hoch*        | *Nein*                 | ProblemDetail/RFC 9457 – Herkunft prüfen                                                                                                                                                                                       |
| `db/migration/` (V1–V3) | *Hoch*        | *Nein*                 |                                                                                                                                                                                                                                |
| `src/test/java/` (41 Tests, Testcontainers-Konfiguration) | *Hoch*        | *Nein*                 | T8: Testcontainers-Setup – häufig Kursmaterial, prüfen                                                                                                                                                                         |
| `scripts/` (Testdatengenerator T9, Performance-Skript T10) | *Hoch*        | *Nein*                 |                                                                                                                                                                                                                                |
| `docs/nachweise/*` (ausser den unten gelisteten) | *Niedrig*     | *Nein*                 |                                                                                                                                                                                                                                |
| `README.md` | *Niedrig*     | *Nein*                 | Korrekturen (Testanzahl, T9–T12-Status, T7-SQL-Log-Ausschnitt, defekter Link) von Claude Code vorgeschlagen; Werte stammen aus tatsächlich ausgeführten Befehlen (`./mvnw test`, App mit Profil `sqllog`), von Fabio zu prüfen |
| `docs/nachweise/t2-migrationen.md` | *Niedrig*     | *Nein*                 | Text von Claude Code verfasst, Inhalte (Log-Ausschnitte) aus echtem `MigrationTest`-Lauf übernommen, nicht erfunden                                                                                                            |
| `docs/hilfsmittelverzeichnis.md`, `docs/ki-einsatz.md`, `docs/eigenstaendigkeitserklaerung.md` | *Niedrig*     | *Nein*                 | Struktur/Tabellen von Claude Code angelegt; **inhaltlich auszufüllen und die Eigenständigkeitserklärung persönlich zu unterschreiben ist Fabios Aufgabe**, nicht delegierbar                                                   |

## Arbeitsjournal (fortlaufend ergänzen)

| Datum | Was                                                | KI beteiligt? |
|---|----------------------------------------------------|---------------|
| 25.10.2026 | Erstellung und aufsetzung des Projektes            | Nein          |
| 28.10.2026 | T1 - T6                                            | Claude        |
| 01.11.2026 | Bearbeiung von T1 - T6 und mit T7 - T11 angefangen | Claude Code   |
| 04.11.2026 | Finalisierung und T12                              | Claude Code   |

