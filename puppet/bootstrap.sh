#!/usr/bin/env bash
#
# Installs the Puppet agent on a freshly provisioned Ubuntu 22.04 host.
#
# Idempotent: if the agent is already present, this exits successfully without
# touching anything. Recovery reruns of the configure stage are therefore free.

set -euo pipefail

PUPPET_BIN="/opt/puppetlabs/bin/puppet"
RELEASE_DEB="puppet8-release-jammy.deb"
RELEASE_URL="https://apt.puppet.com/${RELEASE_DEB}"

log() {
  echo "[bootstrap] $*"
}

if [ -x "${PUPPET_BIN}" ]; then
  log "Puppet agent already installed: $(${PUPPET_BIN} --version)"
  exit 0
fi

log "Installing Puppet agent"
export DEBIAN_FRONTEND=noninteractive

# Cloud images race cloud-init's own apt work; retry rather than fail the deploy.
for attempt in $(seq 1 30); do
  if apt-get update -y; then
    break
  fi
  log "apt-get update failed, retry ${attempt}/30"
  sleep 10
done

apt-get install -y --no-install-recommends curl ca-certificates

TMP_DEB="$(mktemp -d)/${RELEASE_DEB}"
for attempt in $(seq 1 5); do
  if curl -fsSL -o "${TMP_DEB}" "${RELEASE_URL}"; then
    break
  fi
  log "download of ${RELEASE_DEB} failed, retry ${attempt}/5"
  sleep 5
done

dpkg -i "${TMP_DEB}"
apt-get update -y
apt-get install -y puppet-agent

log "Installed: $(${PUPPET_BIN} --version)"
