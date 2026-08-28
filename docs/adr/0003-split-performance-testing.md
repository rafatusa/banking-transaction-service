# 3. Performance testing split between deploy and a scheduled workflow

Date: 2026-08-28

## Status

Accepted

## Context

The requirement asked for a k6 load test verifying 200 concurrent users over 15 minutes with
p95 latency below 400 ms and an error rate below 1%, positioned inside the deploy pipeline.

Two problems with running that in the deploy path:

1. **Every deploy would cost at least 17 minutes of load testing** (ramp plus sustained load),
   sitting in front of the release and rollback stages. Routine changes would wait on it.
2. **The target is a single `t3.small` sharing a box with nginx.** At 200 concurrent users, a
   p95 under 400 ms is a statement about instance size rather than about the code. The gate would
   fail, and the only fix available would be "make the instance bigger" — repeatedly.

The user confirmed the split and confirmed `t3.small` as a deliberate demo sizing.

## Decision

Performance testing is split in two.

**`perf/smoke.js`** runs inside the deploy pipeline: a short ramp to 10 VUs for about 90 seconds.
Its thresholds (p95 < 400 ms, error rate < 1%) are **enforced** — a breach fails the stage and
blocks the release. It is a regression guard, not a capacity measurement.

**`perf/soak.js`** runs as its own `soak` workflow, dispatched manually or on a schedule: the full
200 VUs for 15 minutes. Its thresholds are **declared and reported but not enforced**
(`abortOnFail` is not set), because on a `t3.small` they will legitimately be exceeded.

Both produce an HTML report published as a workflow artifact.

## Consequences

**Positive**

- Deploys stay fast; a performance regression is still caught before release.
- The full benchmark still exists, with real numbers and reports, and can run as often as wanted
  without taxing delivery.
- No nightly red build reporting a failure that everyone already knows about and no one will act
  on.

**Negative**

- The full 200-VU behaviour is not verified before a release reaches production. A regression that
  only manifests under heavy concurrency would be caught by the soak run afterwards, not before.
- Two k6 scripts to keep in step. They share the same scenario shape deliberately, so a change to
  the exercised endpoints must be applied to both.

## Revisit when

The instance is sized for real traffic. At that point the soak thresholds become meaningful as
gates, and the soak workflow can be made to fail hard — the numbers would then represent a
regression rather than a known ceiling.
