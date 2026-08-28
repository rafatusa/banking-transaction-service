#!/usr/bin/env bash
#
# Installs Trivy on a GitHub-hosted Linux runner.
#
# Why this script exists
# ---------------------
# Three stages (dependency_scan, trivy_fs, trivy_image) need Trivy. They
# previously each carried an identical inline install block pinned to
# v0.58.1 — a version that does NOT exist. Every one of them failed with:
#
#   curl: (22) The requested URL returned error: 404
#
# The asset NAMING was never wrong (trivy_<ver>_Linux-64bit.tar.gz is correct);
# the pinned VERSION was invented. Hardcoding a release version in three places
# is the underlying defect: it is a liability that rots and it rots in triplicate.
#
# This script resolves the current release from the GitHub API instead, with an
# explicit fallback so a transient API failure cannot silently break the build.
# TRIVY_VERSION may be set in the environment to pin deliberately.

set -euo pipefail

FALLBACK_VERSION="0.74.0"

resolve_version() {
  # Unauthenticated API calls are rate-limited per runner IP. When GITHUB_TOKEN
  # is present (it always is in Actions) use it to avoid 403s under load.
  local auth=()
  if [ -n "${GITHUB_TOKEN:-}" ]; then
    auth=(-H "Authorization: Bearer ${GITHUB_TOKEN}")
  fi

  curl -sSfL "${auth[@]}" \
    -H "Accept: application/vnd.github+json" \
    "https://api.github.com/repos/aquasecurity/trivy/releases/latest" \
    | sed -n 's/.*"tag_name"[[:space:]]*:[[:space:]]*"v\{0,1\}\([^"]*\)".*/\1/p' \
    | head -n 1
}

VERSION="${TRIVY_VERSION:-}"

if [ -z "${VERSION}" ]; then
  VERSION="$(resolve_version || true)"
fi

if [ -z "${VERSION}" ]; then
  echo "Could not resolve the latest Trivy release; falling back to ${FALLBACK_VERSION}" >&2
  VERSION="${FALLBACK_VERSION}"
fi

echo "Installing Trivy ${VERSION}"

URL="https://github.com/aquasecurity/trivy/releases/download/v${VERSION}/trivy_${VERSION}_Linux-64bit.tar.gz"

# --fail makes curl exit non-zero on a 404 instead of writing an HTML error page
# into the tarball and failing confusingly at the tar step.
curl -sSfL -o /tmp/trivy.tar.gz "${URL}"
tar -xzf /tmp/trivy.tar.gz -C /tmp trivy
sudo install -m 0755 /tmp/trivy /usr/local/bin/trivy

trivy --version
