#!/usr/bin/env bash
#
# Fails the stage if the named gate recorded a non-zero exit code.
# Runs after the report-upload step so evidence is published either way.
#
# Reprints the failing tool's captured output so the reason for the failure is
# visible on THIS step — the one CI marks red — rather than only in the earlier
# step that deliberately exited 0.
#
# PRINT ORDER: ANALYSIS FIRST *AND* LAST, RAW LOG IN BETWEEN
# ----------------------------------------------------------
# This is the fix for a bug that recurred three times, each time in a new
# disguise:
#
#   run #3 — the reporter step was placed AFTER this script, which exits the
#            job, so it never executed at all.
#   run #4 — the reporter ran, but printed only to its own SUCCESSFUL step, and
#            `gh run view --log-failed` returns only the FAILING step.
#   run #5 — the reporter appended to the END of the gate log, and this script
#            printed head-400 + tail-200 of a 600+ line log, so the analysis
#            landed in the OMITTED MIDDLE.
#
# All three are the same lesson: a diagnosis is only useful where the red step
# actually prints it. Reporters now write a dedicated `<gate>.analysis` file,
# which this script prints UNWINDOWED at BOTH ENDS of the failure block.
#
# Printing it twice is deliberate, not sloppiness. Log readers truncate from
# different ends: the GitHub Actions logs API drops roughly the first 8KB, while
# a tail-based viewer drops everything before the last N lines. A block printed
# only at the top is lost to the former; only at the bottom, to the latter. The
# file is a few dozen lines, so duplicating it is cheap insurance against ever
# spending another CI round trip on log plumbing.
#
# WHY BOTH ENDS OF THE RAW LOG
# ----------------------------
# The raw window used to be `tail -n 200`. That is the right window for a
# linter, whose verdict is the last thing it writes, and the WRONG window for a
# test suite. When a Spring ApplicationContext fails to load, JUnit reports the
# real cause ONCE — near the top — and then emits
#
#   IllegalStateException: ApplicationContext failure threshold (1) exceeded
#
# for every remaining test in the class. With 17 tests the tail is 17 copies of
# the cascade and the actual `Caused by` has scrolled out of the window.
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
ANALYSIS_FILE="${GATE_DIR}/${GATE_NAME}.analysis"

HEAD_LINES=400
TAIL_LINES=200

# Print the distilled diagnosis, unwindowed. Called at both ends of the block.
print_analysis() {
  if [ -f "${ANALYSIS_FILE}" ]; then
    echo >&2
    echo "### ${GATE_NAME}: DIAGNOSIS ($1) ###" >&2
    cat "${ANALYSIS_FILE}" >&2
    echo "### end ${GATE_NAME} diagnosis ###" >&2
    echo >&2
  fi
}

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

  # ---- ANALYSIS (top copy — survives tail-based truncation) ------------------
  print_analysis "distilled — read this first"

  # ---- RAW LOG --------------------------------------------------------------
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

  # ---- ANALYSIS (bottom copy — survives head-based truncation) ---------------
  print_analysis "repeated — the raw log above may be truncated"

  echo "The full report is also published as this stage's artifact." >&2
  exit "${RC}"
fi

echo "gate ${GATE_NAME}: passed"
