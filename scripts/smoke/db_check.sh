#!/usr/bin/env bash
#
# Verifies PostgreSQL connectivity through the application's readiness probe.
#
# Checking the app's own view of the database is the meaningful test: it proves
# the security group, credentials, JDBC URL and connection pool all work
# together, which a direct psql from the runner would not (the runner has no
# route to the private database).
#
# Usage: db_check.sh <base-url>

set -euo pipefail

BASE_URL="${1:?usage: db_check.sh <base-url>}"

echo "Checking database connectivity via /actuator/health"
BODY="$(curl --fail --silent --show-error --retry 10 --retry-delay 10 --retry-all-errors \
  "${BASE_URL}/actuator/health")"

echo "${BODY}"

# The health aggregate must be UP.
echo "${BODY}" | grep -q '"status":"UP"' \
  || { echo "DB CHECK FAILED: health endpoint is not UP" >&2; exit 1; }

# And the db component specifically must be present and UP: an aggregate UP
# with the database component missing would mean the datasource never
# initialised.
echo "${BODY}" | python3 -c '
import json, sys
health = json.load(sys.stdin)
components = health.get("components", {})
db = components.get("db")
if db is None:
    print("DB CHECK FAILED: no db component in the health report", file=sys.stderr)
    sys.exit(1)
if db.get("status") != "UP":
    print(f"DB CHECK FAILED: db component is {db.get(\"status\")}", file=sys.stderr)
    sys.exit(1)
database = db.get("details", {}).get("database", "unknown")
print(f"Database component UP ({database})")
'

echo "PostgreSQL connectivity verified"
