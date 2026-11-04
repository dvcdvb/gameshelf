#!/usr/bin/env bash
# Erzeugt die reproduzierbaren Testdaten (T9) in der lokalen Docker-Datenbank.
# Voraussetzung: "docker compose up -d" läuft und die Anwendung wurde einmal gestartet
# (damit Flyway das Schema angelegt hat).
# ACHTUNG: Löscht alle vorhandenen Daten in der Datenbank "gameshelf".
set -euo pipefail
cd "$(dirname "$0")/.."

CONTAINER="${CONTAINER:-gameshelf-db}"

docker exec -i "$CONTAINER" psql -U gameshelf -d gameshelf -v ON_ERROR_STOP=1 < scripts/testdaten.sql
