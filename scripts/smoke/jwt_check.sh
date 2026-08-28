#!/usr/bin/env bash
#
# Verifies JWT authentication end to end against the deployed service:
#   1. a protected endpoint refuses an unauthenticated caller (401)
#   2. a protected endpoint refuses a forged token (401)
#   3. valid credentials yield a token
#   4. that token opens the protected endpoint (200)
#
# Usage: jwt_check.sh <base-url>
# Requires: SMOKE_USER, SMOKE_PASSWORD

set -euo pipefail

BASE_URL="${1:?usage: jwt_check.sh <base-url>}"
: "${SMOKE_USER:?SMOKE_USER must be set}"
: "${SMOKE_PASSWORD:?SMOKE_PASSWORD must be set}"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

fail() {
  echo "JWT CHECK FAILED: $*" >&2
  exit 1
}

echo "1/4 unauthenticated request must be refused"
STATUS="$(curl -s -o /dev/null -w '%{http_code}' "${BASE_URL}/api/accounts")"
[ "${STATUS}" = "401" ] || fail "expected 401 without a token, got ${STATUS}"
echo "    -> 401 as expected"

echo "2/4 forged token must be refused"
STATUS="$(curl -s -o /dev/null -w '%{http_code}' \
  -H 'Authorization: Bearer forged.token.value' \
  "${BASE_URL}/api/accounts")"
[ "${STATUS}" = "401" ] || fail "expected 401 with a forged token, got ${STATUS}"
echo "    -> 401 as expected"

echo "3/4 valid credentials must yield a token"
# Build the payload with python so the password is JSON-escaped correctly and
# never appears on a command line.
LOGIN_PAYLOAD="${WORK_DIR}/login.json"
python3 -c '
import json, os, sys
with open(sys.argv[1], "w") as fh:
    json.dump({"username": os.environ["SMOKE_USER"],
               "password": os.environ["SMOKE_PASSWORD"]}, fh)
' "${LOGIN_PAYLOAD}"

LOGIN_BODY="${WORK_DIR}/login-response.json"
STATUS="$(curl -s -o "${LOGIN_BODY}" -w '%{http_code}' \
  -X POST "${BASE_URL}/api/auth/login" \
  -H 'Content-Type: application/json' \
  --data-binary "@${LOGIN_PAYLOAD}")"
[ "${STATUS}" = "200" ] || fail "login returned ${STATUS} (expected 200)"

TOKEN="$(python3 -c 'import json,sys; print(json.load(open(sys.argv[1]))["token"])' "${LOGIN_BODY}")"
[ -n "${TOKEN}" ] || fail "login succeeded but returned no token"
echo "    -> token issued"

echo "4/4 issued token must open the protected endpoint"
STATUS="$(curl -s -o /dev/null -w '%{http_code}' \
  -H "Authorization: Bearer ${TOKEN}" \
  "${BASE_URL}/api/accounts")"
[ "${STATUS}" = "200" ] || fail "expected 200 with a valid token, got ${STATUS}"
echo "    -> 200 as expected"

echo "JWT authentication verified"
