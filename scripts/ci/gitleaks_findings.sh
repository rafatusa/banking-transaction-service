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
# Usage: gitleaks_findings.sh <sarif-report>

set -uo pipefail

REPORT="${1:-gitleaks-report.sarif}"

if [ ! -f "${REPORT}" ]; then
  echo "No gitleaks report at ${REPORT} — nothing to summarise."
  exit 0
fi

echo "============================================================="
echo "Gitleaks findings (locations only — values remain redacted)"
echo "============================================================="

# python3 is always present on ubuntu-latest runners. Only ruleId/uri/line are
# extracted; the `snippet`/`text` fields that hold the secret are never touched.
python3 - "${REPORT}" <<'PY'
import json, sys

path = sys.argv[1]

try:
    with open(path, encoding="utf-8") as fh:
        sarif = json.load(fh)
except (OSError, ValueError) as exc:
    print(f"Could not parse {path}: {exc}")
    sys.exit(0)

count = 0
for run in sarif.get("runs", []):
    for result in run.get("results", []):
        count += 1
        rule = result.get("ruleId", "<unknown-rule>")
        for loc in result.get("locations", []):
            phys = loc.get("physicalLocation", {})
            uri = phys.get("artifactLocation", {}).get("uri", "<unknown-file>")
            line = phys.get("region", {}).get("startLine", "?")
            print(f"  [{rule}] {uri}:{line}")

if count == 0:
    print("  (no findings recorded in the report)")
else:
    print(f"\n  {count} finding(s). Review each one before allowlisting anything.")
    print("  A finding in a test fixture is still a finding: prefer generating")
    print("  the value at runtime over adding it to .gitleaksignore.")
PY

echo "============================================================="
