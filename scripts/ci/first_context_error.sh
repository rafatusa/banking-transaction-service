#!/usr/bin/env bash
#
# Extracts the FIRST ApplicationContext load failure from the Failsafe XML
# reports and prints it, root cause first.
#
# WHY THIS EXISTS
# ---------------
# When a Spring test context fails to initialise, JUnit reports the genuine
# stack trace ONCE — for the first test that tried to load it — and then, for
# every remaining test in the class, prints only:
#
#   java.lang.IllegalStateException: ApplicationContext failure threshold (1)
#   exceeded: skipping repeated attempt to load context for [...]
#
# With 17 integration tests that means 16 copies of a message which contains no
# diagnostic information at all, each carrying a ~40-line WebMergedContext
# dump. The real `Caused by:` is buried near the TOP of the output.
#
# Both log-reading paths fail on this shape:
#   * a log TAIL shows only the cascade;
#   * the GitHub Actions logs API truncates from the TOP, removing the cause.
#
# WHERE THE OUTPUT GOES
# ---------------------
# To a DEDICATED file, `.ci-gates/<gate>.analysis`, which assert_gate.sh prints
# FIRST — above any raw log window — on the step CI marks red.
#
# Earlier versions of this script appended to `.ci-gates/<gate>.log` instead.
# That failed for a subtle reason: assert_gate.sh windows the raw log as
# head-400 + tail-200, and on a 600+ line Maven run the appended analysis landed
# in the OMITTED MIDDLE. Before that, printing only to this step's own stdout
# failed because `gh run view --log-failed` returns just the FAILING step, and
# this step succeeds by design. A separate file, printed first and unwindowed,
# closes both holes.
#
# It never fails the build — the verdict belongs to assert_gate.sh — it only
# makes that verdict explainable.
#
# Usage: first_context_error.sh <failsafe-reports-dir> [gate-name]

set -uo pipefail

REPORT_DIR="${1:-target/failsafe-reports}"
GATE_NAME="${2:-integration-tests}"
GATE_DIR=".ci-gates"
ANALYSIS_FILE="${GATE_DIR}/${GATE_NAME}.analysis"

mkdir -p "${GATE_DIR}"

analyse() {
  echo "============================================================="
  echo "Integration test failure analysis (root cause first)"
  echo "============================================================="

  if [ ! -d "${REPORT_DIR}" ]; then
    echo "  no Failsafe reports at ${REPORT_DIR} — nothing to summarise"
    echo "  (the suite failed BEFORE producing reports: check the raw log below"
    echo "   for a compilation error or a plugin/JVM startup failure)"
    echo "============================================================="
    return 0
  fi

  # .txt reports are the human-readable dumps; they carry the same traces as the
  # XML without needing an XML parser on the runner.
  local reports
  mapfile -t reports < <(find "${REPORT_DIR}" -name '*.txt' -type f | sort)

  if [ "${#reports[@]}" -eq 0 ]; then
    echo "  no Failsafe .txt reports in ${REPORT_DIR} — nothing to summarise"
    echo "============================================================="
    return 0
  fi

  local found_cause=0

  for report in "${reports[@]}"; do
    # Skip reports with no failures at all.
    if ! grep -qE 'Failures: [1-9]|Errors: [1-9]' "${report}"; then
      continue
    fi

    echo
    echo "--- ${report} ---"
    grep -m1 -E 'Tests run:' "${report}" || true

    # The chain of causes is the point: print every exception header in order,
    # excluding the threshold cascade and the NoClassDefFound follow-ons that
    # every subsequent test emits. The FIRST remaining entry is the root.
    echo
    echo "Cause chain (first entry is the root cause):"
    if grep -nE '^(Caused by:|[a-z0-9.]+\.[A-Za-z]+(Exception|Error):)' "${report}" \
        | grep -v 'ApplicationContext failure threshold' \
        | grep -v 'Could not initialize class' \
        | head -n 25; then
      found_cause=1
    fi

    # Spring prints a targeted diagnostic block for the commonest causes
    # (missing bean, unsatisfied dependency, bad property, failed datasource).
    echo
    echo "Spring diagnostics, if any:"
    grep -m 20 -E 'APPLICATION FAILED TO START|Description:|Action:|Parameter [0-9]+ of|required a bean|Failed to (configure|determine)|Error creating bean with name|Unable to (start|obtain)|Connection to .* refused|Could not (open|obtain)|Docker environment|Testcontainers|Flyway|Migration .* failed|must be at least' \
      "${report}" || echo "  (none)"
  done

  if [ "${found_cause}" -eq 0 ]; then
    echo
    echo "  No root-cause line matched. Download the 'rest-assured-report'"
    echo "  artifact for the complete Failsafe output."
  fi

  echo
  echo "============================================================="
}

# Write the distilled analysis to its own file (assert_gate.sh prints it first)
# AND echo it here for anyone reading this step directly.
analyse | tee "${ANALYSIS_FILE}"

exit 0
