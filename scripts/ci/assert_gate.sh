#!/usr/bin/env bash
#
# Fails the stage if the named gate recorded a non-zero exit code.
# Runs after the report-upload step so evidence is published either way.
#
# Reprints the failing tool's captured output so the reason for the failure is
# visible on THIS step — the one CI marks red — rather than only in the earlier
# step that deliberately exited 0.
#
# WHY BOTH ENDS OF THE LOG
# ------------------------
# This script used to print `tail -n 200`. That is the right window for a
# linter, whose verdict is the last thing it writes, and the WRONG window for a
# test suite. When a Spring ApplicationContext fails to load, JUnit reports the
# real cause ONCE — near the top — and then emits
#
#   IllegalStateException: ApplicationContext failure threshold (1) exceeded
#
# for every remaining test in the class. With 17 tests the tail is 17 copies of
# the cascade and the actual `Caused by` has scrolled out of the window. The
# GitHub logs API truncates from the top as well, so the first error became
# unreachable from every direction and cost a full diagnostic round trip.
#
# Printing the HEAD as well as the TAIL makes any gate self-diagnosing: the
# first error and the final verdict are both on the red step.
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

HEAD_LINES=400
TAIL_LINES=200

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
    TOTAL="$(wc -l < "${LOG_FILE}")"

    # The first error wins: later errors normally cascade from it.
    echo "--- ${GATE_NAME}: FIRST ${HEAD_LINES} lines (the original error) ---" >&2
    head -n "${HEAD_LINES}" "${LOG_FILE}" >&2

    if [ "${TOTAL}" -gt $((HEAD_LINES + TAIL_LINES)) ]; then
      echo "--- ${GATE_NAME}: [ $((TOTAL - HEAD_LINES - TAIL_LINES)) lines omitted ] ---" >&2
    fi

    if [ "${TOTAL}" -gt "${HEAD_LINES}" ]; then
      echo "--- ${GATE_NAME}: LAST ${TAIL_LINES} lines (the verdict) ---" >&2
      tail -n "${TAIL_LINES}" "${LOG_FILE}" >&2
    fi

    echo "--- end ${GATE_NAME} output ---" >&2
  fi

  echo "The full report is also published as this stage's artifact." >&2
  exit "${RC}"
fi

echo "gate ${GATE_NAME}: passed"
