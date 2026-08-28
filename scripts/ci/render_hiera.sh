#!/usr/bin/env bash
#
# Renders the Puppet Hiera deployment data from terraform outputs and CI secrets.
#
# Written on the runner, copied to the host by the configure stage, and removed
# from both by scrub_hiera.sh. It is never committed: .gitignore excludes it.
#
# Usage: render_hiera.sh <output-path>
#
# Required environment:
#   DB_HOST DB_USERNAME DB_PASSWORD JWT_SECRET GHCR_TOKEN GHCR_ACTOR
#   IMAGE_REPO IMAGE_SHA APP_HOST APP_LOG_GROUP SYSTEM_LOG_GROUP AWS_REGION
#   SEED_USERNAME SEED_PASSWORD

set -euo pipefail

OUT_PATH="${1:?usage: render_hiera.sh <output-path>}"

require() {
  local name="$1"
  if [ -z "${!name:-}" ]; then
    echo "render_hiera.sh: required environment variable ${name} is empty" >&2
    exit 1
  fi
}

for var in DB_HOST DB_USERNAME DB_PASSWORD JWT_SECRET GHCR_TOKEN GHCR_ACTOR \
           IMAGE_REPO IMAGE_SHA APP_HOST APP_LOG_GROUP SYSTEM_LOG_GROUP \
           AWS_REGION SEED_USERNAME SEED_PASSWORD; do
  require "$var"
done

REPO_LC="$(echo "${IMAGE_REPO}" | tr '[:upper:]' '[:lower:]')"

mkdir -p "$(dirname "${OUT_PATH}")"

# Values are single-quoted YAML scalars; any embedded single quote is doubled.
yaml_escape() {
  printf "%s" "$1" | sed "s/'/''/g"
}

cat > "${OUT_PATH}" <<EOF
---
# Rendered by the deploy pipeline. NEVER committed — contains credentials.
banking::db_host: '$(yaml_escape "${DB_HOST}")'
banking::db_username: '$(yaml_escape "${DB_USERNAME}")'
banking::db_password: '$(yaml_escape "${DB_PASSWORD}")'
banking::jwt_secret: '$(yaml_escape "${JWT_SECRET}")'
banking::image: 'ghcr.io/$(yaml_escape "${REPO_LC}"):$(yaml_escape "${IMAGE_SHA}")'
banking::ghcr_user: '$(yaml_escape "${GHCR_ACTOR}")'
banking::ghcr_token: '$(yaml_escape "${GHCR_TOKEN}")'
banking::server_name: '$(yaml_escape "${APP_HOST}")'
banking::app_log_group: '$(yaml_escape "${APP_LOG_GROUP}")'
banking::system_log_group: '$(yaml_escape "${SYSTEM_LOG_GROUP}")'
banking::region: '$(yaml_escape "${AWS_REGION}")'
banking::seed_username: '$(yaml_escape "${SEED_USERNAME}")'
banking::seed_password: '$(yaml_escape "${SEED_PASSWORD}")'
EOF

chmod 600 "${OUT_PATH}"
echo "Rendered Hiera deployment data to ${OUT_PATH} (values redacted)"
