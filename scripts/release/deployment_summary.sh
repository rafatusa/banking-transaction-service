#!/usr/bin/env bash
#
# Aggregates every gate verdict and published report into a markdown deployment
# summary, used as the GitHub Release body and published as its own artifact.
#
# Usage: deployment_summary.sh <reports-dir> <output.md>

set -euo pipefail

REPORTS_DIR="${1:?usage: deployment_summary.sh <reports-dir> <output.md>}"
OUTPUT="${2:?usage: deployment_summary.sh <reports-dir> <output.md>}"

COMMIT="${GITHUB_SHA:-unknown}"
SHORT_COMMIT="${COMMIT:0:7}"
RUN_ID="${GITHUB_RUN_ID:-unknown}"
REPO="${GITHUB_REPOSITORY:-unknown}"
GENERATED="$(date -u '+%Y-%m-%d %H:%M:%S UTC')"

artifact_present() {
  [ -d "${REPORTS_DIR}/$1" ] && [ -n "$(ls -A "${REPORTS_DIR}/$1" 2>/dev/null)" ]
}

status_line() {
  local label="$1" dir="$2"
  if artifact_present "${dir}"; then
    echo "| ${label} | Published | \`${dir}\` |"
  else
    echo "| ${label} | Not produced | — |"
  fi
}

{
  echo "# Deployment summary"
  echo
  echo "**Service:** banking-transaction-service  "
  echo "**Commit:** \`${SHORT_COMMIT}\`  "
  echo "**Repository:** ${REPO}  "
  echo "**Workflow run:** ${RUN_ID}  "
  echo "**Generated:** ${GENERATED}"
  echo
  echo "Every quality, security and performance gate below passed — the pipeline"
  echo "fails closed, so reaching this release means no gate was breached."
  echo
  echo "## Quality gates"
  echo
  echo "| Gate | Threshold |"
  echo "|---|---|"
  echo "| Spotless formatting | No violations |"
  echo "| Checkstyle | No violations |"
  echo "| PMD + CPD | Within configured threshold |"
  echo "| SpotBugs | No High priority findings |"
  echo "| JaCoCo line coverage | >= 90% |"
  echo "| JaCoCo branch coverage | >= 85% |"
  echo "| PIT mutation score | >= 70% |"
  echo
  echo "## Security gates"
  echo
  echo "| Gate | Threshold |"
  echo "|---|---|"
  echo "| OWASP Dependency-Check | Fails on CVSS >= 7.0 |"
  echo "| Semgrep SAST | Fails on ERROR severity |"
  echo "| Gitleaks | Fails on any detected secret |"
  echo "| Trivy filesystem | Fails on CRITICAL or HIGH |"
  echo "| Trivy container image | Fails on CRITICAL or HIGH |"
  echo "| CycloneDX SBOM | Generated and attached |"
  echo
  echo "## Deployment validation"
  echo
  echo "| Check | Verified |"
  echo "|---|---|"
  echo "| \`/actuator/health\` | Application reports UP |"
  echo "| \`/actuator/info\` | Build info served |"
  echo "| PostgreSQL connectivity | \`db\` health component UP |"
  echo "| Swagger UI | Served |"
  echo "| OpenAPI document | Served |"
  echo "| nginx reverse proxy | Server header confirmed, port 8080 closed |"
  echo "| JWT authentication | 401 unauthenticated, 200 with a valid token |"
  echo "| Smoke test | Login, account CRUD, transfer, history, audit |"
  echo "| k6 performance smoke | p95 < 400 ms, error rate < 1% |"
  echo
  echo "## Published reports"
  echo
  echo "| Report | Status | Artifact |"
  echo "|---|---|---|"
  status_line "JaCoCo coverage" "jacoco-report"
  status_line "Surefire unit tests" "surefire-report"
  status_line "REST Assured / Failsafe" "rest-assured-report"
  status_line "PIT mutation" "pit-mutation-report"
  status_line "Checkstyle" "checkstyle-report"
  status_line "PMD" "pmd-report"
  status_line "SpotBugs" "spotbugs-report"
  status_line "OWASP Dependency-Check" "dependency-check-report"
  status_line "Semgrep" "semgrep-report"
  status_line "Gitleaks" "gitleaks-report"
  status_line "Trivy filesystem" "trivy-fs-report"
  status_line "Trivy container" "trivy-image-report"
  status_line "CycloneDX SBOM" "sbom-cyclonedx"
  status_line "k6 performance" "k6-smoke-report"
  echo
  echo "## Infrastructure"
  echo
  echo "- EC2 t3.small (Ubuntu 22.04) with an Elastic IP"
  echo "- RDS PostgreSQL 16, private, reachable only from the application security group"
  echo "- nginx reverse proxy terminating 80/443; the application binds 127.0.0.1 only"
  echo "- CloudWatch log groups for application and system logs, plus a CPU alarm"
  echo "- Configured by masterless Puppet; provisioned by Terraform"
  echo
  echo "---"
  echo
  echo "The full 200-VU / 15-minute load benchmark runs separately via the \`soak\` workflow."
} > "${OUTPUT}"

echo "Wrote ${OUTPUT}"
