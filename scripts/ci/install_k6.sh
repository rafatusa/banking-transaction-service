#!/usr/bin/env bash
#
# Installs k6 on a bare GitHub Actions runner.
#
# Uses the official release tarball rather than the apt repository: no GPG
# keyserver round-trip, so it does not fail when keyserver.ubuntu.com is slow
# or unreachable.

set -euo pipefail

K6_VERSION="${K6_VERSION:-0.55.0}"

if command -v k6 >/dev/null 2>&1; then
  echo "k6 already installed: $(k6 version)"
  exit 0
fi

TMP_DIR="$(mktemp -d)"
TARBALL="k6-v${K6_VERSION}-linux-amd64.tar.gz"
URL="https://github.com/grafana/k6/releases/download/v${K6_VERSION}/${TARBALL}"

echo "Downloading k6 v${K6_VERSION}"
for attempt in $(seq 1 3); do
  if curl -fsSL -o "${TMP_DIR}/${TARBALL}" "${URL}"; then
    break
  fi
  echo "download failed, retry ${attempt}/3"
  sleep 5
done

tar -xzf "${TMP_DIR}/${TARBALL}" -C "${TMP_DIR}"
sudo install -m 0755 "${TMP_DIR}/k6-v${K6_VERSION}-linux-amd64/k6" /usr/local/bin/k6

echo "Installed: $(k6 version)"
