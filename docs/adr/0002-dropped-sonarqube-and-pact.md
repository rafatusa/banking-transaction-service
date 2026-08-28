# 2. SonarQube and Pact contract testing dropped

Date: 2026-08-28

## Status

Accepted

## Context

The requested pipeline included a SonarQube analysis stage and Pact contract tests.

**SonarQube** requires a server — SonarCloud or self-hosted — plus a `SONAR_TOKEN` credential and
a configured project key. Neither exists in this environment. A stage referencing an unconfigured
token fails at every run, and a pipeline that always fails at the same step trains people to
ignore it.

The analysis SonarQube would perform is already covered:

| SonarQube capability | Covered by |
|---|---|
| Static bug detection | SpotBugs (High threshold) |
| Code smells and style | Checkstyle, PMD |
| Security hotspots | Semgrep (`p/java`, `p/owasp-top-ten`, `p/secrets`) |
| Coverage measurement and gating | JaCoCo (90% line, 85% branch) |
| Duplication | PMD CPD |

**Pact** verifies a contract between a consumer and a provider. This system has one service and
no named consumer. A Pact test written now would define a contract against an imagined consumer
and then verify the provider satisfies it — a test that cannot fail for any reason that matters,
because both halves are authored together from the same assumptions.

## Decision

Neither SonarQube nor Pact is included.

The API surface is covered by REST Assured tests running against a real PostgreSQL container,
which exercise authentication, authorization denial, transfers, history and the audit trail.

## Consequences

**Positive**

- No permanently red stage caused by missing infrastructure.
- No test that provides false assurance.
- The gates that remain all fail for real reasons.

**Negative**

- No aggregated quality dashboard or historical trend across runs. The individual tool reports
  are published per run as artifacts, but nothing joins them over time.
- No contract enforcement if a consumer appears later.

## Revisit when

- **SonarQube**: a SonarCloud organisation or self-hosted server exists and a `SONAR_TOKEN` can
  be set. Adding the stage back is a small change — the analysis it needs is already produced.
- **Pact**: a second service consumes this API. At that point the contract has two independent
  parties and the test can genuinely fail, which is what makes it worth running.
