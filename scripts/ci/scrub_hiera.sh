#!/usr/bin/env bash
#
# Removes the rendered Hiera file (which contains credentials) from both the
# host and the CI runner once Puppet has applied it.
#
# Best-effort by design: a failure to scrub must not fail an otherwise
# successful deploy, but it is reported loudly.
#
# Usage: scrub_hiera.sh <ssh-target>

set -uo pipefail

SSH_TARGET="${1:?usage: scrub_hiera.sh <ssh-target>}"
KEY="${HOME}/.ssh/deploy_key"

echo "Scrubbing rendered Hiera data from the host"
if ssh -i "${KEY}" -o BatchMode=yes -o ConnectTimeout=15 "${SSH_TARGET}" \
    'sudo rm -rf /tmp/puppet/data/deploy.yaml /tmp/puppet' 2>/dev/null; then
  echo "Host copy removed"
else
  echo "WARNING: could not remove the Puppet payload from the host." >&2
  echo "Remove /tmp/puppet manually: it contains the rendered credentials." >&2
fi

echo "Scrubbing rendered Hiera data from the runner"
rm -f puppet/data/deploy.yaml
echo "Runner copy removed"
