#!/usr/bin/env bash
#
# Verifies nginx is the public entrypoint and the application port is not
# directly exposed.
#
# Usage: nginx_check.sh <base-url>

set -euo pipefail

BASE_URL="${1:?usage: nginx_check.sh <base-url>}"
HOST="$(echo "${BASE_URL}" | sed -E 's#^https?://##' | cut -d/ -f1 | cut -d: -f1)"

echo "1/3 response must be served by nginx"
HEADERS="$(curl --fail --silent --show-error -I --retry 5 --retry-delay 5 "${BASE_URL}/actuator/health")"
echo "${HEADERS}"
echo "${HEADERS}" | grep -qi '^server:.*nginx' \
  || { echo "NGINX CHECK FAILED: Server header is not nginx" >&2; exit 1; }
echo "    -> served by nginx"

echo "2/3 security headers must be present"
for header in "x-content-type-options" "x-frame-options"; do
  echo "${HEADERS}" | grep -qi "^${header}:" \
    || { echo "NGINX CHECK FAILED: missing ${header} header" >&2; exit 1; }
done
echo "    -> security headers present"

echo "3/3 application port 8080 must NOT be reachable from the internet"
if curl --silent --max-time 10 -o /dev/null "http://${HOST}:8080/actuator/health" 2>/dev/null; then
  echo "NGINX CHECK FAILED: the application port is publicly reachable." >&2
  echo "The app must bind 127.0.0.1 only, with nginx as the sole entrypoint." >&2
  exit 1
fi
echo "    -> port 8080 correctly closed"

echo "nginx reverse proxy verified"
