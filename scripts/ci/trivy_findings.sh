#!/usr/bin/env bash
#
# Prints WHICH Trivy checks fired, from the SARIF report.
#
# Why this exists
# ---------------
# The gating Trivy step writes SARIF (machine-readable, for the artifact) and a
# sibling step writes a human table. Both land in steps that SUCCEED, because
# `run_gate.sh` records the verdict rather than failing in place. GitHub's
# `gh run view --log-failed` returns ONLY the failing step, and the full `--log`
# view is truncated from the top by checkout noise — roughly 8KB of it.
#
# The result: a real, correct finding was completely unreadable, and identifying
# it cost several diagnostic round trips. This is the same lesson already
# learned for gitleaks and the integration tests, applied to the third gate that
# needed it.
#
# This script reads the SARIF and prints, per result: rule id, severity, file,
# line and message. It writes to `.ci-gates/<gate>.analysis`, which
# assert_gate.sh prints UNWINDOWED at BOTH ENDS of the failing step — so it
# survives truncation from either direction.
#
# Nothing here is sensitive: Trivy misconfiguration findings are statements
# about committed IaC, not secrets. (This script is NOT used for secret
# scanning — gitleaks_findings.sh deliberately prints locators only.)
#
# Usage: trivy_findings.sh <sarif-report> [gate-name]

set -uo pipefail

REPORT="${1:-trivy-fs-report.sarif}"
GATE_NAME="${2:-trivy-fs}"
GATE_DIR=".ci-gates"
ANALYSIS_FILE="${GATE_DIR}/${GATE_NAME}.analysis"

mkdir -p "${GATE_DIR}"

summarise() {
  echo "============================================================="
  echo "Trivy findings for gate '${GATE_NAME}'"
  echo "============================================================="

  if [ ! -f "${REPORT}" ]; then
    echo "  No SARIF report at ${REPORT} — nothing to summarise."
    echo "  (Trivy may have aborted before writing a report; see the raw log.)"
    echo "============================================================="
    return 0
  fi

  # python3 is always present on ubuntu-latest runners.
  python3 - "${REPORT}" <<'PY'
import json, sys

path = sys.argv[1]

try:
    with open(path, encoding="utf-8") as fh:
        sarif = json.load(fh)
except (OSError, ValueError) as exc:
    print(f"  Could not parse {path}: {exc}")
    sys.exit(0)

# Rule metadata (severity, name, help text) lives in the driver's rule table;
# results reference it by ruleId.
rules = {}
for run in sarif.get("runs", []):
    driver = run.get("tool", {}).get("driver", {})
    for rule in driver.get("rules", []):
        rid = rule.get("id")
        if not rid:
            continue
        props = rule.get("properties", {}) or {}
        tags = props.get("tags", []) or []
        severity = props.get("security-severity") or ""
        # Trivy also encodes severity as a tag (e.g. "CRITICAL").
        for tag in tags:
            if str(tag).upper() in ("CRITICAL", "HIGH", "MEDIUM", "LOW", "UNKNOWN"):
                severity = str(tag).upper()
                break
        rules[rid] = {
            "name": rule.get("name") or rule.get("shortDescription", {}).get("text", ""),
            "severity": severity or "?",
            "help": (rule.get("help", {}) or {}).get("text", ""),
        }

count = 0
for run in sarif.get("runs", []):
    for result in run.get("results", []):
        count += 1
        rid = result.get("ruleId", "<unknown-rule>")
        meta = rules.get(rid, {})
        sev = meta.get("severity", "?")
        name = meta.get("name", "")
        msg = (result.get("message", {}) or {}).get("text", "").strip()

        locs = result.get("locations", []) or [{}]
        for loc in locs:
            phys = loc.get("physicalLocation", {})
            uri = phys.get("artifactLocation", {}).get("uri", "<unknown-file>")
            line = phys.get("region", {}).get("startLine", "?")
            print(f"  [{sev}] {rid}  {uri}:{line}")
            if name:
                print(f"        {name}")
            if msg:
                print(f"        {msg}")
            print()

if count == 0:
    print("  (no findings recorded in the report)")
    print("  If the gate still failed, Trivy exited non-zero for another")
    print("  reason — a scan error rather than a finding. Read the raw log.")
else:
    print(f"  {count} finding(s) at the gating severity.")
    print("  Fix the IaC. Only add a .trivyignore entry when the risk is")
    print("  genuinely accepted, with a written justification and a review date.")
PY

  echo "============================================================="
}

summarise | tee "${ANALYSIS_FILE}"

exit 0
