#!/usr/bin/env bash
#
# Prints WHERE gitleaks found something, without printing WHAT it found.
#
# Why this exists
# ---------------
# `gitleaks detect --redact` correctly refuses to echo secret values into CI
# logs. It also, less helpfully, leaves the console output as nothing but:
#
#   WRN leaks found: 2
#
# — no file, no rule, no line. Diagnosing that requires downloading the SARIF
# artifact and parsing it, which is a slow round-trip and impossible on a runner
# without a JSON tool.
#
# This script reads the SARIF report and prints, for each finding, only the
# non-sensitive locators: rule id, file path and line number. The matched secret
# is never read or printed, so `--redact` is not undermined.
#
# WHERE THE OUTPUT GOES
# ---------------------
# To a DEDICATED file, `.ci-gates/<gate>.analysis`, which assert_gate.sh prints
# FIRST — above any raw log window — on the step CI marks red.
#
# This step SUCCEEDS (it is a reporter, not a gate) and `gh run view
# --log-failed` returns only the FAILING step, so output printed here alone is
# invisible to the fastest diagnostic path. An earlier version appended to
# `.ci-gates/<gate>.log`, which assert_gate.sh windows as head+tail — on a long
# log the append landed in the omitted middle. A separate, unwindowed file
# printed first closes both holes.
#
# The fingerprint printed for each finding is exactly the string
# `.gitleaksignore` expects, so an allowlist entry can be copied straight from
# this output after a human has reviewed the finding.
#
# Usage: gitleaks_findings.sh <sarif-report> [gate-name]

set -uo pipefail

REPORT="${1:-gitleaks-report.sarif}"
GATE_NAME="${2:-gitleaks}"
GATE_DIR=".ci-gates"
ANALYSIS_FILE="${GATE_DIR}/${GATE_NAME}.analysis"

mkdir -p "${GATE_DIR}"

summarise() {
  echo "============================================================="
  echo "Gitleaks findings (locations only — values remain redacted)"
  echo "============================================================="

  if [ ! -f "${REPORT}" ]; then
    echo "  No gitleaks report at ${REPORT} — nothing to summarise."
    echo "============================================================="
    return 0
  fi

  # python3 is always present on ubuntu-latest runners. Only ruleId/uri/line are
  # extracted; the `snippet`/`text` fields that hold the secret are never touched.
  python3 - "${REPORT}" <<'PY'
import json, sys

path = sys.argv[1]

try:
    with open(path, encoding="utf-8") as fh:
        sarif = json.load(fh)
except (OSError, ValueError) as exc:
    print(f"  Could not parse {path}: {exc}")
    sys.exit(0)

count = 0
for run in sarif.get("runs", []):
    for result in run.get("results", []):
        count += 1
        rule = result.get("ruleId", "<unknown-rule>")
        locs = result.get("locations", []) or [{}]
        for loc in locs:
            phys = loc.get("physicalLocation", {})
            uri = phys.get("artifactLocation", {}).get("uri", "<unknown-file>")
            line = phys.get("region", {}).get("startLine", "?")
            print(f"  [{rule}] {uri}:{line}")
            # The exact fingerprint .gitleaksignore expects, for a reviewed
            # false positive. Never add one without reading the line first.
            print(f"      fingerprint: {uri}:{rule}:{line}")

if count == 0:
    print("  (no findings recorded in the report)")
else:
    print(f"\n  {count} finding(s). Review each one before allowlisting anything.")
    print("  A finding in a test fixture is still a finding: prefer generating")
    print("  the value at runtime over adding it to .gitleaksignore.")
PY

  echo "============================================================="
}

# Write the locators to their own file (assert_gate.sh prints it first) AND
# echo them here for anyone reading this step directly.
summarise | tee "${ANALYSIS_FILE}"

exit 0
