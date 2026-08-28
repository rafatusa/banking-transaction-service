# 6. Two reviewed false positives are allowlisted in `.gitleaksignore`

Date: 2026-08-28

## Status

Accepted (user-approved). Applies to `.gitleaksignore` at the repository root.

## Context

The `gitleaks` stage failed with:

```
WRN leaks found: 2
```

`gitleaks detect --redact` deliberately withholds the matched values, and — less
helpfully — also withheld the file, rule and line, so for four CI runs the findings
could not be identified at all. `scripts/ci/gitleaks_findings.sh` was added to read the
SARIF report and print only the non-sensitive locators. It named them:

```
[generic-api-key]   .env.example:19
[curl-auth-header]  scripts/smoke/jwt_check.sh:27
```

Both lines were then read directly.

**`.env.example:19`** is `JWT_SECRET=` — the value is **empty**. The file is a
local-development template whose header instructs the reader to generate the value with
`openssl rand -hex 32`, and `.env` itself is gitignored. The `generic-api-key` rule
matched the variable *name*; there is no value present to leak.

**`scripts/smoke/jwt_check.sh:27`** is
`-H 'Authorization: Bearer forged.token.value'`. This is step 2/4 of the deployed-service
smoke check, which asserts that a protected endpoint answers **401** to a forged
credential. `forged.token.value` is not a JWT, has never been valid, and grants nothing.
The `curl-auth-header` rule matches the *shape* of the header, not the validity of the
token. The real credentials in that script are never literals — they arrive as
`$SMOKE_USER` / `$SMOKE_PASSWORD` and are JSON-escaped through `python3` so they never
appear on a command line or in the process table.

Neither finding is a credential. Both are true positives for the *pattern* and false
positives for the *risk*.

## Decision

Add exactly two entries to `.gitleaksignore`, each written as a **fingerprint** of the
form `<file>:<rule-id>:<line>` — the narrowest scope gitleaks supports:

```
.env.example:generic-api-key:19
scripts/smoke/jwt_check.sh:curl-auth-header:27
```

Each entry carries an inline justification and the alternative that was rejected. The
file records a **review date of 2026-02-28** (six months).

No rule is disabled globally and no path is excluded from scanning. Because a
fingerprint pins the line number, inserting a line above either match, or the same
pattern appearing anywhere else in the repository, produces a *new* fingerprint and
fails the gate. The allowlist cannot silently widen.

This decision was escalated to the user rather than taken by the agent: allowlisting a
security finding is a judgement about accepted risk, not a build fix.

## Alternatives considered

**Change the code so nothing matches.** For `.env.example`, deleting the `JWT_SECRET=`
line would remove documentation of a variable the application *requires* to boot — it
refuses to start without at least 32 characters of HS256 key material — trading a false
positive for a real onboarding failure. For `jwt_check.sh`, assembling the forged token
at runtime (e.g. from concatenated fragments) would hide the fixture from a human
reading the test without changing a single byte sent over the wire: obfuscation to
satisfy a scanner, at the cost of reviewability. Both were rejected, though the first is
a reasonable choice for a team that prefers an empty allowlist.

**Add a `[[rules.allowlist]]` block to a gitleaks config.** Broader than needed: it would
suppress the rule by regex or path rather than at one specific line, and would keep
suppressing after the line changed.

**Disable the `generic-api-key` / `curl-auth-header` rules.** Rejected outright. Both
rules are load-bearing — `generic-api-key` is the catch-all for exactly the kind of
credential this service must never commit.

**Remove the `gitleaks` stage.** Rejected outright; it is the primary secret gate, and
after ADR 0004 amendment 2 it is the *only* stage scanning the working tree for secrets.

## Consequences

Positive:

- The gate passes on evidence rather than on a weakened rule set: two findings were
  read, judged, and documented.
- Any *new* secret, anywhere — including a real value later typed into
  `.env.example:19` — still fails the build, because the fingerprint would change.

Negative / accepted:

- `.gitleaksignore` is now a file that must be re-read when either referenced line
  moves. The six-month review date exists to force that.
- A future editor could add a fingerprint without the same scrutiny. The file's header
  states the standard (read the line, justify it, date it); code review enforces it.
