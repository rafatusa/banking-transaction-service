#!/usr/bin/env bash
#
# Fails the stage if the named gate recorded a non-zero exit code.
# Runs after the report-upload step so evidence is published either way.
#
# Reprints the failing tool's captured output so the reason for the failure is
# visible on THIS step — the one CI marks red — rather than only in the earlier
# step that deliberately exited 0.
#
# Usage: assert_gate.sh <gate-name>

set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "usage: assert_gate.sh <gate-name>" >&2
  exit 2
fi

GATE_NAME="$1"
GATE_DIR=".ci-gates"
RC_FILE="${GATE_DIR}/${GATE_NAME}.rc"
LOG_FILE="${GATE_DIR}/${GATE_NAME}.log"

if [ ! -f "${RC_FILE}" ]; then
  echo "gate ${GATE_NAME}: no verdict recorded at ${RC_FILE}" >&2
  echo "The gate step did not run. Failing closed rather than assuming success." >&2
  exit 1
fi

RC="$(cat "${RC_FILE}")"

if [ "${RC}" -ne 0 ]; then
  echo "=============================================================" >&2
  echo "gate ${GATE_NAME} FAILED with exit code ${RC}" >&2
  echo "=============================================================" >&2
  if [ -f "${LOG_FILE}" ]; then
    echo "--- captured ${GATE_NAME} output ---" >&2
    tail -n 200 "${LOG_FILE}" >&2
    echo "--- end ${GATE_NAME} output ---" >&2
  fi
  echo "The full report is also published as this stage's artifact." >&2
  exit "${RC}"
fi

echo "gate ${GATE_NAME}: passed"
