#!/usr/bin/env bash
# Führt die Performanceanalyse (T10) gegen die lokale Docker-Datenbank aus und speichert die Ausgabe.
# Voraussetzung: Testdaten erzeugt (./scripts/testdaten-erzeugen.sh).
set -euo pipefail
cd "$(dirname "$0")/.."

CONTAINER="${CONTAINER:-gameshelf-db}"
mkdir -p docs/nachweise

docker exec -i "$CONTAINER" psql -U gameshelf -d gameshelf < scripts/performance-analyse.sql \
    | tee docs/nachweise/t10-ausgabe.txt

echo
echo "Ausgabe gespeichert in docs/nachweise/t10-ausgabe.txt"
