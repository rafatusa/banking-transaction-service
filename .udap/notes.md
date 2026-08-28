# banking-transaction-service — working notes

## What this is
Enterprise Banking Transaction Service. Spring Boot 3.2 / Java 21 / PostgreSQL (RDS) on AWS EC2,
nginx reverse proxy, **Puppet** config management, GitHub Actions CI/CD with an extensive
quality/security gate suite.

## Session history
- Seeded with blueprint `fastapi-ec2@1.0.0` (Python/FastAPI) — **deselected**, stack mismatch.
  `search_marketplace` java/spring-boot/ec2/postgres → **no match**. Custom, scaffold-first.
- Meta rejected once, interrupted once, **APPROVED 3rd attempt**. Design APPROVED. Plan APPROVED.

## Decisions locked with the user
| Topic | Decision |
|---|---|
| SonarQube | DROPPED (no server/token) — ADR 0002 |
| Pact | DROPPED (single service, no consumer) — ADR 0002 |
| Config mgmt | **Puppet ONLY, no Ansible** — ADR 0001 |
| k6 | SPLIT: smoke in deploy (ENFORCED), 200VU/15min soak separate workflow (REPORTED) — ADR 0003 |
| Instance | t3.small |
| DB | RDS PostgreSQL 16.4, single-AZ, private to app SG |
| Tier | 2 |
| Spotless | hygiene rules only (no googleJavaFormat) — user chose option (c) |
| Coverage | **(b) merge unit+integration THEN (a) add controller tests** — user chose |

## GOTCHAS HIT (do not repeat)
1. **Spec forbids `if:` on steps.** SOLUTION: `scripts/ci/run_gate.sh <name> <cmd...>`
   (always exits 0, records rc in `.ci-gates/<name>.rc`) → upload step →
   `scripts/ci/assert_gate.sh <name>` fails the stage AND reprints the captured log.
2. **Scaffold shipped Spring Boot 4.1.1, not 3.2.** Pinned **3.2.11**, rewrote dependencies.
3. **Scaffold Dockerfile `${PORT:-8080}` in exec-form CMD** — no shell expansion. Rewrote.
4. **`низ` corruption crept into pom.xml** during a write. RE-READ large generated files.
5. **Fabricated a sha256 digest.** Use version tags unless a REAL digest is known.
6. **Secret scan flags credential-shaped literals in TESTS.** Fixed via runtime-generated
   `TestCredentials`.
7. **Checkstyle config was structurally invalid** — `LineLength` must be under `Checker`,
   not `TreeWalker` (Checkstyle 8.24+). The gate was failing to INITIALIZE, inspecting nothing.
8. **maven-pmd-plugin 3.21.2 bundles PMD 6.55.0** which rejects `targetJdk=21`.
   Upgraded to **3.26.0** (PMD 7.x) + updated ruleset category paths for PMD 7.
9. **A Docker-dependent test named `*Tests` is picked up by SUREFIRE**, not Failsafe.
   Integration tests must be `*IT`. Renamed.
10. **Mockito `UnfinishedStubbing`**: never build+stub a mock INSIDE another `when(...)`
    argument list. Build it on its own line first (`JwtAuthenticationFilterTest.claims()`).
11. **A mocked `OncePerRequestFilter` never calls `filterChain.doFilter`** and silently
    swallows every request. In `@WebMvcTest` import the REAL `JwtAuthenticationFilter` and
    mock only its `JwtService` collaborator (see `WebMvcTestSupport`).
12. **AssertJ `containsExactly` on `Collection<? extends GrantedAuthority>`** fails to
    compile with a `SimpleGrantedAuthority` vararg (wildcard capture). Map to
    `GrantedAuthority::getAuthority` and assert on strings.

## COVERAGE — RESOLVED (was the last blocker)
Problem: gate measured the UNIT stage only → 53% line / 52% branch vs required 90/85.
Controllers + exception handler were covered only by REST Assured in a SEPARATE stage
that ran with `-Djacoco.skip=true`.

**(b) Measurement fixed** — pom.xml:
  - `prepare-agent` → `target/jacoco-ut.exec`; `prepare-agent-integration` → `jacoco-it.exec`
  - `merge-coverage` execution merges `target/*.exec` → `jacoco-merged.exec`
  - `merged-report` + `coverage-gate` both read ONLY `jacoco-merged.exec`
  pipeline.yaml:
  - unit_tests / integration_tests each upload their `.exec` as an artifact; neither gates
  - NEW `coverage` stage (needs both) downloads both execs, merges, reports, enforces 90/85
  - `docker_build` now needs `coverage` (replaced unit_tests + integration_tests)
  - integration_tests runs with `-Djacoco.skip=false`

**(a) Controller tests added** — `src/test/java/.../web/`:
  `WebMvcTestSupport` (imports REAL SecurityConfig + JwtAuthenticationFilter, mocks JwtService),
  `AccountControllerTest` (16), `TransferControllerTest` (10), `AuthControllerTest` (5),
  `TransactionControllerTest` (8), `AuditControllerTest` (8), `GlobalExceptionHandlerTest` (9),
  plus `security/JwtAuthenticationFilterTest` (11 — closed the last branch gap).

**RESULT: `All coverage checks have been met.` 117 unit tests, 0 failures.**
Achieved from UNIT data alone — integration coverage is pure headroom on top in CI.

## Key contracts honoured
- terraform: EMPTY backend `backend "s3" {}`; init flags bucket/key/region + `-reconfigure`
- all terraform-touching stages carry AWS creds
- self-sufficient job rule: configure/verify/perf re-init + `terraform output -raw`.
  NEVER thread infra values via needs.outputs (PROJECT_NAME is a secret).
- RDS SG ingress references app SG id, never a CIDR. IMDSv2 required. AMI via data source.
- `depends_on = [aws_iam_instance_profile.app]` — IAM propagation race.

## Secrets to set AFTER create_repo_and_push, BEFORE deploy
DB_USERNAME, DB_PASSWORD, JWT_SECRET, SMOKE_USER, SMOKE_PASSWORD. (NVD_API_KEY optional.)
App FAILS FAST if JWT_SECRET absent (no default in application.yml — deliberate).
Seed admin = SMOKE_USER/SMOKE_PASSWORD via app.seed.* → APP_SEED_ADMIN_* env.

## KNOWN SANDBOX GAP (not a defect — do not "fix")
`test_project` cannot run the `*IT` integration tests: `AbstractIntegrationTest` starts a
Testcontainers Postgres in its static initializer and the UDAP sandbox has NO DOCKER
(`Could not initialize class ...AbstractIntegrationTest` → 17 errors).
Constitution rule 9: do NOT degrade the project to satisfy the sandbox. CI runs on
ubuntu-latest where Docker is present. Everything else in the rehearsal is GREEN.

## Status
- [x] validate_project **PASS** (116 files)
- [x] test_project: build, spotless, checkstyle, pmd, cpd, spotbugs, 117 unit tests, coverage gate
      ALL GREEN. Only the Docker-dependent IT stage fails (sandbox gap above).
- [ ] push → secrets → deploy
