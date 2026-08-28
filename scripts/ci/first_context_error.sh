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
# So this step reads the machine-readable Failsafe report instead of the console
# log, and prints the first real failure. It never fails the build — the
# verdict belongs to assert_gate.sh — it only makes that verdict explainable.
#
# Usage: first_context_error.sh <failsafe-reports-dir>

set -uo pipefail

REPORT_DIR="${1:-target/failsafe-reports}"

if [ ! -d "${REPORT_DIR}" ]; then
  echo "no Failsafe reports at ${REPORT_DIR} — nothing to summarise"
  exit 0
fi

# .txt reports are the human-readable dumps; they carry the same traces as the
# XML without needing an XML parser on the runner.
mapfile -t REPORTS < <(find "${REPORT_DIR}" -name '*.txt' -type f | sort)

if [ "${#REPORTS[@]}" -eq 0 ]; then
  echo "no Failsafe .txt reports in ${REPORT_DIR} — nothing to summarise"
  exit 0
fi

echo "============================================================="
echo "Integration test failure analysis"
echo "============================================================="

FOUND_CAUSE=0

for report in "${REPORTS[@]}"; do
  # Skip reports with no failures at all.
  if ! grep -qE 'Failures: [1-9]|Errors: [1-9]' "${report}"; then
    continue
  fi

  echo
  echo "--- ${report} ---"

  # The header line carries the run/failure/error counts.
  grep -m1 -E 'Tests run:' "${report}" || true

  # The chain of causes is the whole point: print every `Caused by:` line, in
  # order, plus the frame immediately after it. The FIRST one is the root.
  echo
  echo "Cause chain (first entry is the root cause):"
  if grep -nE '^(Caused by:|[a-z0-9.]+\.[A-Za-z]+(Exception|Error):)' "${report}" \
      | grep -v 'ApplicationContext failure threshold' \
      | head -n 25; then
    FOUND_CAUSE=1
  fi

  # Spring prints a targeted diagnostic block for the commonest causes
  # (missing bean, unsatisfied dependency, bad property, failed datasource).
  echo
  echo "Spring diagnostics, if any:"
  grep -m 20 -E 'APPLICATION FAILED TO START|Description:|Action:|Parameter [0-9]+ of|required a bean|Failed to (configure|determine)|Error creating bean with name|Unable to (start|obtain)|Connection to .* refused|Caused by: org\.postgresql|Could not (open|obtain)' \
    "${report}" || echo "  (none)"
done

if [ "${FOUND_CAUSE}" -eq 0 ]; then
  echo
  echo "No root-cause line matched. Download the 'rest-assured-report' artifact"
  echo "for the complete Failsafe output."
fi

echo
echo "============================================================="
exit 0
