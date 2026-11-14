#!/usr/bin/env bash
# Runs the backend from WSL against the MongoDB installed on Windows.
#
# Inside WSL, "localhost" is the Linux VM, not Windows, so the default URI in
# application.properties (mongodb://localhost:27017/...) can't reach a Windows-installed MongoDB.
# This points the app at the Windows host instead, without editing application.properties.
#
#   scripts/dev-run.sh              # uses database "fyp_dev" (safe for testing)
#   MONGO_DB=fyp scripts/dev-run.sh # uses your real data
set -euo pipefail
cd "$(dirname "$0")/.."

WINDOWS_HOST="$(ip route show default | awk 'NR==1 {host=$3} END {print host}')"
MONGO_DB="${MONGO_DB:-fyp_dev}"
export SPRING_DATA_MONGODB_URI="mongodb://${WINDOWS_HOST}:27017/${MONGO_DB}"

echo "Using MongoDB at ${WINDOWS_HOST}:27017, database '${MONGO_DB}'"
exec mvn -B -q spring-boot:run
