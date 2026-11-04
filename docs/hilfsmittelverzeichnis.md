# Hilfsmittelverzeichnis

Gemäss *HFTM-KI-Richtlinie für Studierende, Version 1.1 (02.02.2026)*. Jeder Einsatz eines KI-Werkzeugs in dieser Projektarbeit wird hier mit Zweck, betroffenen Stellen/Dateien und Zeitpunkt festgehalten. Siehe auch [`prompt-verzeichnis.md`](prompt-verzeichnis.md) (konkrete Prompts) und [`ki-einsatz.md`](ki-einsatz.md) (Kennzeichnung im Code/Journal).


| Datum        | Werkzeug | Version                     | Zweck | Betroffene Stellen/Dateien |
|--------------|---|-----------------------------|---|---|
| *25.10.2026* | claude.ai (Chat, Web) | *Opus 5.5*          | Planung: Auftrag analysieren, fachliche Anforderungen (A1–A7) und technische Pflichtanforderungen (T1–T12) strukturieren, Umsetzung in 12 Schritten entwerfen | `CLAUDE.md` (Zusammenfassung der Planung), Projektskizze |
| *28.10.2026* | Claude Code (CLI) | *Opus 5.5*                  | Co-Creation: Datenmodell, Migrationen, Schichten (`domain`, `dto`, `repository`, `service`, `web`), Integrationstests | gesamter `src/`-Baum (siehe [`ki-einsatz.md`](ki-einsatz.md) für Details je Datei) |
| *01.11.2026* | Claude Code (CLI) | *Opus 5.5*                  | Testdatengenerator (T9) und Performance-Analyse-Skript (T10) | `scripts/testdaten.sql`, `scripts/testdaten-erzeugen.sh`, `scripts/performance-analyse.sql`, `scripts/performance-analyse.sh` |
| *04.11.2026* | Claude Code (CLI) | Sonnet 5 (`claude-sonnet-5`) | Abgabevorbereitung: Soll-/Ist-Abgleich gegen `CLAUDE.md` Abschnitt 10, README auf aktuellen Stand gebracht (Testanzahl, T9–T12-Status, T7-SQL-Log, defekter Link), Nachweis `docs/nachweise/t2-migrationen.md` erstellt (Daten aus `./mvnw test -Dtest=MigrationTest` und manuellem SQL-Log-Mitschnitt, nicht erfunden), Gerüst der vier KI-Pflichtdokumente angelegt | `README.md`, `docs/nachweise/t2-migrationen.md`, `docs/hilfsmittelverzeichnis.md`, `docs/prompt-verzeichnis.md`, `docs/ki-einsatz.md`, `docs/eigenstaendigkeitserklaerung.md` |
