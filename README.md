# Banking Transaction Service

Enterprise banking transaction service: JWT-authenticated REST API with role-based access control,
account management, atomic money transfers, transaction history and an append-only audit trail.

Spring Boot 3.2 · Java 21 · PostgreSQL 16 · Docker · Terraform · Puppet · GitHub Actions · AWS EC2 · nginx

---

## What it does

| Capability | Detail |
|---|---|
| Authentication | JWT bearer tokens (HS256), BCrypt-hashed credentials |
| Authorization | Three roles — `ADMIN`, `TELLER`, `CUSTOMER` — enforced at method level |
| Accounts | Open, list, fetch, activate/deactivate, close |
| Transfers | Atomic debit/credit with overdraft rejection and deadlock-safe locking |
| History | Paginated transaction history, scoped by ownership |
| Audit | Append-only trail of every state change, readable by admins |
| Observability | `/actuator/health` (with DB probe), `/actuator/info`, `/actuator/prometheus` |
| API docs | OpenAPI 3 at `/v3/api-docs`, Swagger UI at `/swagger-ui.html` |

## Architecture

```
Client ──HTTPS/HTTP──▶ Elastic IP ──▶ nginx :80/:443 ──▶ Spring Boot :8080 (loopback only)
                                                              │
                                                              ▼
                                                    RDS PostgreSQL :5432
                                                 (private; app security group only)
```

The application binds `127.0.0.1` exclusively — nginx is the only public entrypoint. The database
is not reachable from the internet: its security group admits traffic from the application's
security group and nothing else.

The authoritative diagram is [`.udap/architecture.d2`](.udap/architecture.d2). Supporting views
live in [`docs/diagrams/`](docs/diagrams/).

## Roles

| Role | Accounts | Transfers | Audit trail |
|---|---|---|---|
| `ADMIN` | Full access, including closing accounts | Any account | Read |
| `TELLER` | Open, view, activate/deactivate any account | Any account | No |
| `CUSTOMER` | View only their own accounts | Only from accounts they own | No |

## API

| Method | Path | Purpose | Role |
|---|---|---|---|
| `POST` | `/api/auth/login` | Obtain a bearer token | Public |
| `POST` | `/api/accounts` | Open an account | Admin, Teller |
| `GET` | `/api/accounts` | List visible accounts | Any |
| `GET` | `/api/accounts/{number}` | Fetch one account | Any (own only for Customer) |
| `PATCH` | `/api/accounts/{number}` | Activate / deactivate | Admin, Teller |
| `DELETE` | `/api/accounts/{number}` | Close a zero-balance account | Admin |
| `POST` | `/api/transfers` | Move money | Any (own source for Customer) |
| `GET` | `/api/transactions` | Transaction history | Any |
| `GET` | `/api/audit` | Audit trail | Admin |

Errors are returned as RFC 7807 problem documents.

### Example

```bash
# Authenticate (reads the credential from your environment, never a literal)
TOKEN=$(curl -s -X POST "$BASE_URL/api/auth/login" \
  -H 'Content-Type: application/json' \
  -d "{\"username\":\"$ADMIN_USER\",\"password\":\"$ADMIN_PASS\"}" | jq -r .token)

# Open an account
curl -X POST "$BASE_URL/api/accounts" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"ownerUsername":"alice","openingBalance":"1000.00","currency":"USD"}'

# Transfer
curl -X POST "$BASE_URL/api/transfers" \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"sourceAccount":"ACC000000000001","targetAccount":"ACC000000000002","amount":"250.00"}'
```

## Running locally

Requires JDK 21, Maven and Docker (for the PostgreSQL container).

```bash
# Generate throwaway local credentials — no credential literals in this repo.
export DB_PASSWORD="$(openssl rand -hex 16)"
export JWT_SECRET="$(openssl rand -hex 32)"
export APP_SEED_ADMIN_PASSWORD="$(openssl rand -hex 16)"

# Start PostgreSQL
docker run -d --name banking-db \
  -e POSTGRES_DB=banking -e POSTGRES_USER=banking -e POSTGRES_PASSWORD="$DB_PASSWORD" \
  -p 5432:5432 postgres:16-alpine

# Run the application
export DB_HOST=localhost DB_NAME=banking DB_USERNAME=banking
export APP_SEED_ADMIN_USERNAME=admin

./mvnw spring-boot:run
```

Then open <http://localhost:8080>. Log in as `admin` with the value of `$APP_SEED_ADMIN_PASSWORD`.

```bash
./mvnw test                              # unit tests
./mvnw verify -Pintegration-tests        # integration + REST Assured (needs Docker)
```

Integration tests generate their own credentials at runtime (see `TestCredentials`) and start
their own PostgreSQL container, so they need no configuration. The `test` profile is activated by
`@ActiveProfiles("test")` on `AbstractIntegrationTest` rather than by an environment variable —
Failsafe forks a separate JVM, and Spring resolves active profiles from the test context's own
metadata, so a shell-level `SPRING_PROFILES_ACTIVE` does not reach it.

Note that the coverage gate is **not** satisfied by `./mvnw test` alone: it is enforced over the
merged unit *and* integration execution data, which the pipeline's `coverage` stage assembles.
To reproduce it locally, run both suites and then `./mvnw jacoco:merge@merge-coverage
jacoco:report@merged-report jacoco:check@coverage-gate`.

## Code style

Spotless enforces source hygiene: no unused imports, no trailing whitespace, and a final newline.

```bash
./mvnw spotless:check    # what the format CI stage runs
./mvnw spotless:apply    # fix anything it reports
```

A **whole-file layout formatter is deliberately not enabled**, and neither is an enforced import
order. Adding one (`google-java-format`, `palantir-java-format`) is worthwhile but is a
repository-wide reformat, and mixing that into feature commits makes every subsequent diff
unreadable. To adopt one:

1. Add the formatter block to the `spotless-maven-plugin` configuration in `pom.xml`, e.g.
   ```xml
   <googleJavaFormat>
     <version>1.19.2</version>
     <style>AOSP</style>
   </googleJavaFormat>
   ```
2. Run `./mvnw spotless:apply`.
3. Commit the result as a single formatting-only change, then continue normally.

Layout is the only thing left to the author's judgement — Checkstyle still enforces line length,
naming, braces, import rules and method size, and PMD and SpotBugs still enforce structure and
correctness.

## Configuration

All configuration is supplied by environment variables. Secrets are never committed.

| Variable | Purpose | Secret |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME` | Database connection | No |
| `DB_USERNAME` | Database user | Yes |
| `DB_PASSWORD` | Database credential | Yes |
| `JWT_SECRET` | Token signing key (≥ 32 chars; the app refuses to start without it) | Yes |
| `JWT_VALIDITY_MINUTES` | Token lifetime, default 60 | No |
| `APP_SEED_ADMIN_USERNAME` | Bootstrap admin username | Yes |
| `APP_SEED_ADMIN_PASSWORD` | Bootstrap admin credential | Yes |
| `SERVER_PORT` | Listen port, default 8080 | No |

### CI secrets

Set on the repository before the first deploy:

`DB_USERNAME`, `DB_PASSWORD`, `JWT_SECRET`, `SMOKE_USER`, `SMOKE_PASSWORD`

No scanner in this pipeline requires an API key or an external account.

The platform supplies `PROJECT_NAME`, `TF_STATE_BUCKET`, `SSH_USER`, `SSH_PRIVATE_KEY`,
`SSH_PUBLIC_KEY`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` and `GITHUB_TOKEN`.

## Pipeline

Defined in [`.udap/pipeline.yaml`](.udap/pipeline.yaml); the workflow files are rendered from it —
edit the spec, never `.github/workflows/*.yml` directly.

```
build ─┬─ format (Spotless)
       ├─ checkstyle
       ├─ pmd + cpd
       ├─ spotbugs
       ├─ unit_tests ──┬─▶ mutation_tests (PIT)
       │               └─▶ coverage (merged 90/85 gate)
       ├─ integration_tests (Testcontainers + REST Assured) ─┘
       ├─ dependency_scan (Trivy, application JAR)
       └─ sbom (CycloneDX)
gitleaks · semgrep · trivy_fs · tf_validate  (independent)
       ▼
docker_build ──▶ trivy_image ──▶ publish_image (GHCR)
       ▼
provision (Terraform) ──▶ configure (Puppet) ──▶ verify ──▶ perf_smoke (k6) ──▶ release
```

Coverage is measured across both test stages rather than inside either one: `unit_tests` and
`integration_tests` each publish a JaCoCo execution file, and the `coverage` stage merges them
before applying the 90/85 gate. Gating on unit data alone would understate real coverage, since
the controllers and the exception handler are exercised by the REST Assured suite.

### Gates that fail the build

| Gate | Threshold |
|---|---|
| JaCoCo line coverage (merged) | ≥ 90% |
| JaCoCo branch coverage (merged) | ≥ 85% |
| PIT mutation score | ≥ 70% |
| Spotless | Unused imports, trailing whitespace, missing final newline |
| Checkstyle | Any violation |
| PMD | Above configured threshold |
| SpotBugs | Any High priority finding |
| Trivy dependency scan (application JAR) | Any CRITICAL or HIGH |
| Semgrep | Any ERROR severity finding |
| Gitleaks | Any detected secret |
| Trivy (filesystem and image) | Any CRITICAL or HIGH |
| k6 deploy check | p95 ≥ 400 ms or error rate ≥ 1% |

Reports for every gate are published as workflow artifacts, including for gates that fail —
that is what the `run_gate.sh` / `assert_gate.sh` pair exists for.

### Static analysis scope

Semgrep runs `p/java`, `p/owasp-top-ten` and `p/secrets`. The **blocking** gate is scoped to
`ERROR` severity — Semgrep's own classification for exploitable defects such as injection,
hardcoded secrets, unsafe deserialization and path traversal. `INFO` and `WARNING` findings are
still scanned and published as a separate `semgrep-advisory.sarif` artifact for review, but do
not fail the release.

This is a scoping decision, not a relaxation: no rule is disabled and no path is excluded. See
[ADR 0005](docs/adr/0005-semgrep-severity-scoping.md) for the reasoning and the conditions under
which a finding should be promoted to blocking.

Gitleaks runs with `--redact`, so secret values never reach the logs. Because that also hides
*where* a finding is, the stage additionally prints each finding's rule id, file and line —
locators only — so a failure is diagnosable from the log without downloading the artifact.

### Dependency vulnerability scanning

Dependency CVEs are found by Trivy, scanning the **built Spring Boot JAR** rather than the source
tree. This target matters: `pom.xml` alone does not expose the resolved transitive dependency
graph, whereas the fat JAR contains every dependency under `BOOT-INF/lib/` as a concrete versioned
artifact.

OWASP Dependency-Check was used originally and removed — it must download the NVD CVE database
through a rate-limited API that rejects unauthenticated requests from shared CI runner IPs, which
made the stage fail for reasons unrelated to the code. See
[ADR 0004](docs/adr/0004-trivy-replaces-owasp-dependency-check.md).

All three Trivy stages install the tool through [`scripts/ci/install_trivy.sh`](scripts/ci/install_trivy.sh),
which resolves the current release from the GitHub API rather than hardcoding a version. A pinned
release asset is a liability that rots — and, when the install block is duplicated across stages,
rots in triplicate.

Suppressions live in [`.trivyignore`](.trivyignore), which starts empty. Every entry requires a
written justification and a review date. Never silence a finding to make the pipeline green — if a
High or Critical CVE is genuine, upgrade the dependency.

### Load benchmark

The full benchmark — 200 concurrent users for 15 minutes — runs as a separate `soak` workflow
rather than in the deploy path, so routine deploys are not delayed by it. Its thresholds are
reported rather than enforced: the demo instance is a `t3.small`, which will not sustain
p95 < 400 ms at 200 VUs. Raise the instance size before treating those numbers as a gate.
See [ADR 0003](docs/adr/0003-split-performance-testing.md).

## Deployment validation

The `verify` stage proves, against the live deployment: `/actuator/health` reports UP,
`/actuator/info` responds, the database component is UP, Swagger UI and the OpenAPI document are
served, nginx is the proxy and port 8080 is not publicly reachable, JWT authentication rejects
anonymous and forged tokens while accepting valid ones, and a full business smoke test
(login → open accounts → transfer → verify balances → history → audit) succeeds.

## Infrastructure

Terraform under [`infra/`](infra/) provisions: an EC2 `t3.small` running Ubuntu 22.04 with an
Elastic IP; application and database security groups; an IAM instance role scoped to CloudWatch;
RDS PostgreSQL 16 (private, encrypted, 7-day backups); and CloudWatch log groups plus a CPU alarm.

State lives in the platform-managed bucket; the backend block is intentionally empty and
configured at `init` time.

## Configuration management

[`puppet/`](puppet/) holds masterless Puppet manifests applied over SSH by the `configure` stage:
Docker Engine, GHCR authentication, the application container under systemd, the nginx reverse
proxy, log rotation, the CloudWatch agent, and CIS-aligned SSH and kernel hardening.

There is no Puppet Server — a master for a single node is not justifiable. Every manifest is
idempotent, so re-running the configure stage during recovery is safe.
See [ADR 0001](docs/adr/0001-puppet-only-configuration-management.md).

## Operations

See [`docs/operations-guide.md`](docs/operations-guide.md) for day-to-day procedures and
[`docs/troubleshooting.md`](docs/troubleshooting.md) for symptom-indexed diagnosis.

```bash
# On the instance
sudo systemctl status banking-app          # service state
sudo journalctl -u banking-app -n 100      # application logs
sudo docker logs banking-app --tail 100    # container logs
sudo systemctl restart banking-app         # restart
sudo nginx -t && sudo systemctl reload nginx
```

## Design decisions

| ADR | Decision |
|---|---|
| [0001](docs/adr/0001-puppet-only-configuration-management.md) | Puppet only for configuration management |
| [0002](docs/adr/0002-dropped-sonarqube-and-pact.md) | SonarQube and Pact dropped, with rationale |
| [0003](docs/adr/0003-split-performance-testing.md) | Performance testing split between deploy and a scheduled workflow |
| [0004](docs/adr/0004-trivy-replaces-owasp-dependency-check.md) | Trivy replaces OWASP Dependency-Check for dependency CVEs |
| [0005](docs/adr/0005-semgrep-severity-scoping.md) | Semgrep blocking gate scoped to ERROR severity |
