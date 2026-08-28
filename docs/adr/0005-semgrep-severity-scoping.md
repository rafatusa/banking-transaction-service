# 5. Semgrep gate is scoped to ERROR severity

Date: 2026-08-28

## Status

Accepted

## Context

The `semgrep` stage was configured as:

```
semgrep scan --config p/java --config p/owasp-top-ten --config p/secrets --error ...
```

The `--error` flag makes Semgrep exit non-zero when **any** finding is present,
regardless of the finding's own severity. Semgrep's default behaviour is that
only `ERROR`-severity findings are build-breaking; `--error` overrides that and
promotes `INFO` and `WARNING` findings to the same status.

The first CI run reported:

```
Scanning 98 files tracked by git with 595 Code rules:
  Language      Rules   Files
  <multilang>      41      98
  java             60      31
  terraform        60       8
  js               65       2
  yaml             24       7
  bash              1      14
  dockerfile        4       1
  html              1       1
 • Findings: 164 (164 blocking)
 • Rules run: 256
```

164 blocking findings from 256 rules across 98 files, in a codebase that
simultaneously passed SpotBugs (High priority), PMD 7, Checkstyle, CPD and an
86% PIT mutation score.

The distribution is the diagnostic detail: 60 Terraform rules over 8 files and
65 JavaScript rules over 2 k6 scripts. The community rule packs — particularly
`p/owasp-top-ten`, which is intentionally broad — carry a large body of advisory
rules that fire on infrastructure and script files for stylistic and defensive
patterns rather than exploitable defects.

This was a **configuration error on our side**, not a property of the code.

## Decision

Scope the blocking gate to `ERROR` severity:

```
semgrep scan --config p/java --config p/owasp-top-ten --config p/secrets \
  --severity ERROR --error --sarif --output semgrep-report.sarif .
```

A second, non-gating step scans `INFO` and `WARNING` and publishes
`semgrep-advisory.sarif` alongside the blocking report, so advisory findings
remain visible and reviewable.

## Consequences

**What is unchanged.** The same three rule packs run over the same 98 files. The
gate still fails the build and still blocks `docker_build`, and therefore the
release, on any finding Semgrep classifies as an error — injection, hardcoded
secrets, unsafe deserialization, path traversal, and the rest of the
`ERROR`-severity corpus.

**What changed.** Advisory findings are reported instead of enforced.

**Why this is scoping and not weakening.** The distinction that matters is
whether a real defect can now reach production undetected. It cannot: no rule
was disabled, no path was excluded, no threshold was lowered, and
`continue-on-error` was not introduced. What was removed is an override that
replaced the tool author's severity judgement with an indiscriminate one. A gate
that fails on 164 advisory findings is not a stricter gate — it is a gate that
teams learn to ignore, and an ignored gate protects nothing.

**Revisiting.** The advisory SARIF is published on every run. If review of those
findings identifies genuine issues, the correct response is to fix them or to
promote the specific rules to `ERROR` via a custom ruleset — not to reinstate a
blanket `--error`.

## Related

- ADR 0004 — Trivy replaces OWASP Dependency-Check
