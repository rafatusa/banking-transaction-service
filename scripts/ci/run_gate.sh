#!/usr/bin/env bash
#
# Runs a quality gate and records its verdict WITHOUT failing the step.
#
# Why this exists: the pipeline spec does not allow `if:` on steps, so a report
# upload placed after a failing tool would never run. Instead the tool runs
# here, its exit code is recorded, and this step always succeeds; the artifact
# upload then runs normally and `assert_gate.sh` fails the stage afterwards.
#
# The gate still fails the build — it just fails AFTER the evidence has been
# published, which is the whole point of publishing evidence.
#
# The tool's own output is streamed live AND captured, so a failure is
# diagnosable from the stage log without downloading the artifact.
#
# Usage: run_gate.sh <gate-name> <command> [args...]

set -uo pipefail

if [ "$#" -lt 2 ]; then
  echo "usage: run_gate.sh <gate-name> <command> [args...]" >&2
  exit 2
fi

GATE_NAME="$1"
shift

GATE_DIR=".ci-gates"
mkdir -p "${GATE_DIR}"

LOG_FILE="${GATE_DIR}/${GATE_NAME}.log"

echo "::group::gate ${GATE_NAME}: $*"
set +e
"$@" 2>&1 | tee "${LOG_FILE}"
RC=${PIPESTATUS[0]}
set -e
echo "::endgroup::"

echo "${RC}" > "${GATE_DIR}/${GATE_NAME}.rc"

if [ "${RC}" -eq 0 ]; then
  echo "gate ${GATE_NAME}: PASSED"
else
  echo "gate ${GATE_NAME}: FAILED (exit ${RC})"
  echo "--- last 60 lines of ${GATE_NAME} output ---"
  tail -n 60 "${LOG_FILE}"
  echo "--- end ${GATE_NAME} output ---"
  echo "Reports are still uploaded; the stage fails at the assert step."
fi

# Always succeed: the verdict is carried in the .rc file.
exit 0
