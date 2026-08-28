# Operations guide

Day-to-day procedures for running the Banking Transaction Service.

For failure diagnosis see [troubleshooting.md](troubleshooting.md).

---

## Deployment topology

| Component | Where | Managed by |
|---|---|---|
| Application container | EC2 `t3.small`, systemd unit `banking-app` | Puppet |
| Reverse proxy | nginx on the same instance, ports 80/443 | Puppet |
| Database | RDS PostgreSQL 16, private subnet | Terraform |
| Image registry | GHCR, tagged by commit SHA | CI |
| Logs | CloudWatch `/<project>/application` and `/<project>/system` | Terraform + CloudWatch agent |
| Alarm | CPU > 80% for 10 minutes | Terraform |

The application listens on `127.0.0.1:8080` only. All public traffic arrives through nginx.

## Routine procedures

### Check service health

```bash
curl -s http://<elastic-ip>/actuator/health | jq
```

`status: UP` with a `db` component also `UP` means the application and its database connection
are both healthy. Anything else: see troubleshooting.

### Inspect logs

```bash
sudo journalctl -u banking-app -n 200 --no-pager    # systemd unit
sudo docker logs banking-app --tail 200             # application stdout
sudo tail -n 200 /var/log/nginx/banking-access.log  # request log
sudo tail -n 200 /var/log/nginx/banking-error.log   # proxy errors
```

Logs also reach CloudWatch; use the console when the instance is unreachable.

### Restart the application

```bash
sudo systemctl restart banking-app
sudo systemctl status banking-app
```

systemd owns the container lifecycle. Never `docker run` the image by hand — the unit will
conflict with it on the next restart.

### Reload nginx after a configuration change

```bash
sudo nginx -t && sudo systemctl reload nginx
```

Always test first. A reload with a broken config takes the site down.

### Deploy a new version

Changes ship through the pipeline, never by hand:

1. Commit the change.
2. The deploy pipeline builds, gates, publishes the image, applies Terraform, runs Puppet, and
   verifies the result.
3. The `release` stage publishes a GitHub Release with the deployment summary and SBOM.

Do not `docker pull` a new tag on the instance directly: Puppet would revert it on the next
apply, and the change would be invisible to the audit trail.

### Roll back

Use the platform's rollback: it reverts the repository to the last green deploy and redeploys,
applying the previous Terraform configuration as well. That is the infrastructure rollback too.

### Run the load benchmark

The `soak` workflow (200 VUs, 15 minutes) is dispatched manually. It reports thresholds rather
than enforcing them — see the README for why.

## Database

### Connect

The database is not publicly reachable. Connect from the application instance:

```bash
sudo apt-get install -y postgresql-client
psql "host=<db-endpoint> port=5432 dbname=banking user=<db-user>"
```

The endpoint comes from `terraform output -raw db_address`; the credentials are CI secrets.

### Backups

Automated backups retain 7 days, taken in the 03:00–04:00 UTC window. Restores are performed
through the RDS console or CLI as point-in-time recovery to a new instance; the application's
`DB_HOST` then needs to be repointed via a Puppet run.

Backups are not the same as a tested restore. Exercise one before relying on it.

### Migrations

Flyway runs automatically at application startup and owns the schema; Hibernate is configured
`ddl-auto: validate` and never mutates it. Add a new versioned migration under
`src/main/resources/db/migration/` — never edit a migration that has already been applied.

## Credential rotation

| Credential | Where it lives | Rotation |
|---|---|---|
| `DB_PASSWORD` | Repo secret → Hiera → container env | Change the RDS master password, update the secret, redeploy |
| `JWT_SECRET` | Repo secret → Hiera → container env | Update the secret and redeploy; all existing tokens become invalid immediately |
| `SMOKE_USER` / `SMOKE_PASSWORD` | Repo secret → seeded admin user | Update the secret; the existing user is not re-seeded, so change it in the database too |
| SSH keypair | Platform-managed | Rotate from the platform's Integrations page |

Rendered Hiera data containing these values is deleted from both the runner and the host at the
end of every configure stage.

## Monitoring

- **CPU alarm** — fires above 80% average for 10 minutes.
- **Metrics** — `/actuator/prometheus` is exposed on the application but blocked at nginx and
  restricted to `ADMIN`. Point a scraper at it from inside the VPC if you add one.
- **Memory and disk** — reported by the CloudWatch agent.

There is no alert routing configured. Add an SNS topic to the alarm before relying on it to
page anyone.

## Capacity

The `t3.small` is a demo sizing. Before real traffic:

- Raise the instance size (the load benchmark will tell you how much).
- Consider Multi-AZ RDS.
- Consider an ALB with a certificate for real HTTPS on a domain.

Current spend is roughly $45–60/month.
