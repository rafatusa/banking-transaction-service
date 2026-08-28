# Troubleshooting

Symptom-indexed. Each entry: verify the symptom, mitigate to restore service, then diagnose.
Mitigation comes before diagnosis on purpose — restore service first, find the cause second.

---

## The site returns 502 Bad Gateway

**Verify**
```bash
curl -s -o /dev/null -w '%{http_code}\n' http://<elastic-ip>/actuator/health
```

**Mitigate**
```bash
sudo systemctl restart banking-app
```

**Diagnose** — 502 means nginx is running but the application behind it is not answering.

```bash
sudo systemctl status banking-app          # is the unit running?
sudo docker logs banking-app --tail 200    # why did the container stop?
sudo ss -tlnp | grep 8080                  # is anything listening?
```

Common causes:

| Cause | Signature in the logs |
|---|---|
| `JWT_SECRET` missing or shorter than 32 chars | `app.jwt.secret must be at least 32 characters` at startup |
| Database unreachable | `Connection to ... refused` / HikariPool timeout |
| Flyway migration failure | `Migration ... failed` — the app exits |
| Out of memory | Container killed; `docker logs` truncated, `dmesg` shows an OOM kill |

## The application will not start

**Diagnose**
```bash
sudo journalctl -u banking-app -n 200 --no-pager
sudo docker logs banking-app --tail 200
```

Startup fails fast and loudly by design. Read the first error, not the last — later messages are
usually consequences.

| Error | Meaning | Fix |
|---|---|---|
| `app.jwt.secret must be at least 32 characters` | `JWT_SECRET` unset or too short | Set the repo secret, redeploy |
| `Schema-validation: missing table` | Flyway did not run or the schema drifted | Check the `flyway_schema_history` table |
| `password authentication failed` | Wrong `DB_PASSWORD` | Update the secret, redeploy |
| `UnknownHostException` on the DB host | Wrong endpoint in Hiera | Re-run configure so it re-reads terraform outputs |

## Health is UP but the database component is DOWN

**Verify**
```bash
curl -s http://<elastic-ip>/actuator/health | jq '.components.db'
```

**Diagnose** — the application is running but cannot reach PostgreSQL.

1. **Security group** — the database SG must admit port 5432 from the *application* SG. If
   someone replaced that rule with a CIDR, or the app instance moved to a different SG, this
   breaks. Check in the console.
2. **Endpoint** — `terraform output -raw db_address` and compare with what the container has:
   `sudo docker exec banking-app env | grep DB_HOST`.
3. **Credentials** — a rotated RDS password that was not propagated to the repo secret.
4. **RDS state** — the instance may be rebooting or in maintenance.

## SSH: Permission denied (publickey)

Work through these in order — do not rotate keys speculatively.

1. **Check the login user first.** If the denial lists `gssapi` methods, the host is an
   Amazon Linux/RHEL family image and expects `ec2-user`; Ubuntu offers plain `publickey` and
   expects `ubuntu`. A user/AMI mismatch looks exactly like a bad key. This deployment uses
   Ubuntu 22.04, so `SSH_USER` must be `ubuntu`.
2. **Compare the key material.**
   ```bash
   ssh-keygen -y -f ~/.ssh/deploy_key
   ```
   Compare with the `SSH_PUBLIC_KEY` secret. If they match, the instance has the wrong
   `authorized_keys` — it was launched with a different key pair and must be replaced, since
   `authorized_keys` is seeded at launch and never updated afterwards.
3. **If they do not match**, the platform key store is inconsistent. That is account level:
   rotate the project keys from the Integrations page rather than experimenting.

## Puppet apply fails

**Diagnose** — the configure stage log shows the failing resource.

| Failure | Cause | Fix |
|---|---|---|
| `Could not find declared class banking::x` | `--modulepath` wrong or the copy was incomplete | Check the scp step succeeded |
| `Function lookup() did not find a value for banking::y` | A Hiera key the render script did not write | Add it to `scripts/ci/render_hiera.sh` |
| `Execution of '/usr/bin/apt-get' returned 100` | Stale apt index or a lock held by cloud-init | The manifests retry; if it persists, the mirror is down |
| `docker: permission denied` | Docker service not running | `sudo systemctl status docker` |
| `unable to pull image` | GHCR login failed or the image tag does not exist | Check the publish stage actually pushed |

Puppet exit code 2 means "changes applied successfully" — the configure stage treats it as
success. Only exit codes 4 and 6 indicate failure.

## A transfer is rejected unexpectedly

Every rejection is written to the audit trail with its reason:

```bash
curl -s -H "Authorization: Bearer $TOKEN" \
  'http://<elastic-ip>/api/audit?actor=<username>' | jq '.content[] | select(.outcome=="REJECTED")'
```

| Reason in the audit detail | Meaning |
|---|---|
| `insufficient funds` | Source balance below the amount |
| `caller does not own the source account` | Customer attempted a transfer from another user's account |
| `an involved account is inactive` | One account is deactivated |
| `currency mismatch` | Cross-currency transfers are not supported |
| `source and target are the same account` | Self-transfer |
| `non-positive amount` | Amount ≤ 0 |

These are correct behaviour, not faults.

## A CI gate fails

Every gate publishes its report as an artifact **even when it fails** — download the artifact from
the workflow run rather than reading the log.

| Gate | Artifact | Common first cause |
|---|---|---|
| Checkstyle | `checkstyle-report` | Unused import, line over 140 chars |
| PMD | `pmd-report` | Unused variable, over-complex method |
| SpotBugs | `spotbugs-report` | Possible null dereference |
| JaCoCo | `jacoco-report` | New code without tests dropped coverage below 90/85 |
| PIT | `pit-mutation-report` | Tests execute code but assert nothing meaningful |
| Dependency-Check | `dependency-check-report` | A dependency picked up a new CVE — upgrade it |
| Semgrep / Gitleaks / Trivy | Corresponding SARIF | Read the finding; do not suppress without justification |

Never fix a gate by weakening it. A suppression needs a written reason and a review date —
see `config/owasp/suppressions.xml` for the required format.

## Disk is filling up

**Verify**
```bash
df -h /
sudo du -sh /var/log/* | sort -h | tail
```

**Mitigate**
```bash
sudo logrotate -f /etc/logrotate.d/banking-nginx
sudo docker system prune -af --volumes
```

Log rotation is configured, and container logs are capped at 3 × 10 MB. If the disk still fills,
the usual culprit is accumulated unused Docker images from repeated deploys.

## Escalation

Escalate to the account owner when the cause is account level, because no amount of retrying
fixes these: service quota exhaustion, IAM permission denials, billing suspension, or region
unavailability. Include the exact API error.
