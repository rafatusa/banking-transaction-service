#!/usr/bin/env bash
#
# Post-deploy smoke test: exercises the real business flow against the live
# service — log in, open two accounts, transfer money, verify the balances
# moved, confirm the transaction appears in the history, and confirm the audit
# trail recorded it.
#
# This is what proves the deployment actually works, as opposed to merely
# answering health checks.
#
# Usage: smoke.sh <base-url>
# Requires: SMOKE_USER, SMOKE_PASSWORD (an ADMIN account)

set -euo pipefail

BASE_URL="${1:?usage: smoke.sh <base-url>}"
: "${SMOKE_USER:?SMOKE_USER must be set}"
: "${SMOKE_PASSWORD:?SMOKE_PASSWORD must be set}"

WORK_DIR="$(mktemp -d)"
trap 'rm -rf "${WORK_DIR}"' EXIT

fail() {
  echo "SMOKE TEST FAILED: $*" >&2
  exit 1
}

jqp() {
  # Extract a value from a JSON file using python (jq may not be installed).
  python3 -c "
import json, sys
data = json.load(open(sys.argv[1]))
expr = sys.argv[2]
for part in expr.split('.'):
    if part.isdigit():
        data = data[int(part)]
    else:
        data = data[part]
print(data)
" "$1" "$2"
}

echo "==> 1/6 authenticate"
python3 -c '
import json, os, sys
with open(sys.argv[1], "w") as fh:
    json.dump({"username": os.environ["SMOKE_USER"],
               "password": os.environ["SMOKE_PASSWORD"]}, fh)
' "${WORK_DIR}/login.json"

curl --fail --silent --show-error -o "${WORK_DIR}/token.json" \
  -X POST "${BASE_URL}/api/auth/login" \
  -H 'Content-Type: application/json' \
  --data-binary "@${WORK_DIR}/login.json" \
  || fail "login request failed"

TOKEN="$(jqp "${WORK_DIR}/token.json" token)"
AUTH="Authorization: Bearer ${TOKEN}"
echo "    authenticated as ${SMOKE_USER}"

echo "==> 2/6 open the source account"
cat > "${WORK_DIR}/acc1.json" <<'JSON'
{"ownerUsername":"smoke-source","openingBalance":"1000.00","currency":"USD"}
JSON
curl --fail --silent --show-error -o "${WORK_DIR}/acc1-response.json" \
  -X POST "${BASE_URL}/api/accounts" \
  -H "${AUTH}" -H 'Content-Type: application/json' \
  --data-binary "@${WORK_DIR}/acc1.json" \
  || fail "could not open the source account"
SOURCE="$(jqp "${WORK_DIR}/acc1-response.json" accountNumber)"
echo "    source account ${SOURCE} opened with 1000.00"

echo "==> 3/6 open the target account"
cat > "${WORK_DIR}/acc2.json" <<'JSON'
{"ownerUsername":"smoke-target","openingBalance":"0.00","currency":"USD"}
JSON
curl --fail --silent --show-error -o "${WORK_DIR}/acc2-response.json" \
  -X POST "${BASE_URL}/api/accounts" \
  -H "${AUTH}" -H 'Content-Type: application/json' \
  --data-binary "@${WORK_DIR}/acc2.json" \
  || fail "could not open the target account"
TARGET="$(jqp "${WORK_DIR}/acc2-response.json" accountNumber)"
echo "    target account ${TARGET} opened with 0.00"

echo "==> 4/6 transfer 250.00"
python3 -c "
import json, sys
with open(sys.argv[1], 'w') as fh:
    json.dump({'sourceAccount': sys.argv[2],
               'targetAccount': sys.argv[3],
               'amount': '250.00',
               'description': 'post-deploy smoke test'}, fh)
" "${WORK_DIR}/transfer.json" "${SOURCE}" "${TARGET}"

curl --fail --silent --show-error -o "${WORK_DIR}/transfer-response.json" \
  -X POST "${BASE_URL}/api/transfers" \
  -H "${AUTH}" -H 'Content-Type: application/json' \
  --data-binary "@${WORK_DIR}/transfer.json" \
  || fail "the transfer request failed"

STATUS="$(jqp "${WORK_DIR}/transfer-response.json" status)"
[ "${STATUS}" = "COMPLETED" ] || fail "transfer status was ${STATUS}, expected COMPLETED"
echo "    transfer COMPLETED"

echo "==> 5/6 verify the balances moved"
curl --fail --silent --show-error -o "${WORK_DIR}/source-after.json" \
  -H "${AUTH}" "${BASE_URL}/api/accounts/${SOURCE}" || fail "could not read the source account"
curl --fail --silent --show-error -o "${WORK_DIR}/target-after.json" \
  -H "${AUTH}" "${BASE_URL}/api/accounts/${TARGET}" || fail "could not read the target account"

SOURCE_BALANCE="$(jqp "${WORK_DIR}/source-after.json" balance)"
TARGET_BALANCE="$(jqp "${WORK_DIR}/target-after.json" balance)"

python3 -c "
import sys
from decimal import Decimal
src, tgt = Decimal(sys.argv[1]), Decimal(sys.argv[2])
if src != Decimal('750.00'):
    print(f'SMOKE TEST FAILED: source balance is {src}, expected 750.00', file=sys.stderr)
    sys.exit(1)
if tgt != Decimal('250.00'):
    print(f'SMOKE TEST FAILED: target balance is {tgt}, expected 250.00', file=sys.stderr)
    sys.exit(1)
print(f'    balances correct: source={src} target={tgt}')
" "${SOURCE_BALANCE}" "${TARGET_BALANCE}"

echo "==> 6/6 verify history and audit trail"
curl --fail --silent --show-error -o "${WORK_DIR}/history.json" \
  -H "${AUTH}" "${BASE_URL}/api/transactions?accountNumber=${SOURCE}" \
  || fail "could not read the transaction history"

HISTORY_STATUS="$(jqp "${WORK_DIR}/history.json" content.0.status)"
[ "${HISTORY_STATUS}" = "COMPLETED" ] || fail "history does not show the completed transfer"
echo "    transaction appears in the history"

curl --fail --silent --show-error -o "${WORK_DIR}/audit.json" \
  -H "${AUTH}" "${BASE_URL}/api/audit" \
  || fail "could not read the audit trail"

python3 -c "
import json, sys
events = json.load(open(sys.argv[1])).get('content', [])
if not events:
    print('SMOKE TEST FAILED: the audit trail is empty', file=sys.stderr)
    sys.exit(1)
actions = {e['action'] for e in events}
if 'TRANSFER' not in actions:
    print(f'SMOKE TEST FAILED: no TRANSFER in the audit trail (saw {actions})', file=sys.stderr)
    sys.exit(1)
print(f'    audit trail recorded {len(events)} event(s)')
" "${WORK_DIR}/audit.json"

echo
echo "SMOKE TEST PASSED — authentication, account CRUD, transfer, history and audit all verified"
