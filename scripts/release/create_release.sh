#!/usr/bin/env bash
#
# Creates the GitHub Release for a successful deployment and attaches the SBOM.
#
# Idempotent: if the tag already exists (a re-run of the same commit) the
# existing release is updated rather than failing the stage.
#
# Usage: create_release.sh <notes-file> <reports-dir>
# Requires: GH_TOKEN

set -euo pipefail

NOTES_FILE="${1:?usage: create_release.sh <notes-file> <reports-dir>}"
REPORTS_DIR="${2:?usage: create_release.sh <notes-file> <reports-dir>}"

: "${GH_TOKEN:?GH_TOKEN must be set}"

COMMIT="${GITHUB_SHA:-}"
SHORT_COMMIT="${COMMIT:0:7}"
TAG="v$(date -u +%Y.%m.%d)-${SHORT_COMMIT}"

ASSETS=()
SBOM="${REPORTS_DIR}/sbom-cyclonedx/bom.json"
if [ -f "${SBOM}" ]; then
  ASSETS+=("${SBOM}")
else
  echo "Note: no SBOM found at ${SBOM}; releasing without it."
fi

if [ -f "${NOTES_FILE}" ]; then
  ASSETS+=("${NOTES_FILE}")
fi

if gh release view "${TAG}" >/dev/null 2>&1; then
  echo "Release ${TAG} already exists — updating its notes and assets"
  gh release edit "${TAG}" --notes-file "${NOTES_FILE}"
  if [ "${#ASSETS[@]}" -gt 0 ]; then
    gh release upload "${TAG}" "${ASSETS[@]}" --clobber
  fi
else
  echo "Creating release ${TAG}"
  if [ "${#ASSETS[@]}" -gt 0 ]; then
    gh release create "${TAG}" \
      --title "banking-transaction-service ${TAG}" \
      --notes-file "${NOTES_FILE}" \
      "${ASSETS[@]}"
  else
    gh release create "${TAG}" \
      --title "banking-transaction-service ${TAG}" \
      --notes-file "${NOTES_FILE}"
  fi
fi

echo "Release ${TAG} published"
