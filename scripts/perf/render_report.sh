#!/usr/bin/env bash
#
# Renders a k6 JSON summary into a self-contained HTML report published as a
# CI artifact.
#
# Usage: render_report.sh <summary.json> <output.html> [title]

set -euo pipefail

SUMMARY="${1:?usage: render_report.sh <summary.json> <output.html> [title]}"
OUTPUT="${2:?usage: render_report.sh <summary.json> <output.html> [title]}"
TITLE="${3:-Performance report}"

if [ ! -f "${SUMMARY}" ]; then
  echo "No k6 summary at ${SUMMARY} — the load test did not produce results." >&2
  cat > "${OUTPUT}" <<HTML
<!DOCTYPE html>
<html lang="en"><head><meta charset="utf-8"><title>${TITLE}</title></head>
<body><h1>${TITLE}</h1>
<p>No summary was produced: the k6 run did not complete. See the stage log.</p>
</body></html>
HTML
  exit 0
fi

python3 - "${SUMMARY}" "${OUTPUT}" "${TITLE}" <<'PYTHON'
import json
import sys
from datetime import datetime, timezone

summary_path, output_path, title = sys.argv[1], sys.argv[2], sys.argv[3]

with open(summary_path) as fh:
    data = json.load(fh)

metrics = data.get("metrics", {})


def metric(name, field, default=None):
    entry = metrics.get(name)
    if not isinstance(entry, dict):
        return default
    value = entry.get(field)
    return default if value is None else value


def fmt_ms(value):
    return "n/a" if value is None else f"{value:,.1f} ms"


def fmt_pct(value):
    return "n/a" if value is None else f"{value * 100:,.2f}%"


def fmt_num(value):
    return "n/a" if value is None else f"{value:,.2f}"


duration = metrics.get("http_req_duration", {})
p95 = duration.get("p(95)")
p99 = duration.get("p(99)")
failed_rate = metric("http_req_failed", "rate")
business_error_rate = metric("business_errors", "rate")
throughput = metric("http_reqs", "rate")
total_requests = metric("http_reqs", "count")
iterations = metric("iterations", "count")
max_vus = metric("vus_max", "max") or metric("vus_max", "value")

# Threshold verdicts as k6 reported them.
threshold_rows = []
for metric_name, entry in metrics.items():
    if not isinstance(entry, dict):
        continue
    thresholds = entry.get("thresholds")
    if not isinstance(thresholds, dict):
        continue
    for expression, result in thresholds.items():
        # k6 reports either a bool or {"ok": bool}
        ok = result.get("ok") if isinstance(result, dict) else bool(result)
        threshold_rows.append((metric_name, expression, ok))

targets = [
    ("p95 latency", fmt_ms(p95), "< 400 ms", p95 is not None and p95 < 400),
    ("Error rate", fmt_pct(failed_rate), "< 1%",
     failed_rate is not None and failed_rate < 0.01),
    ("Business error rate", fmt_pct(business_error_rate), "< 1%",
     business_error_rate is not None and business_error_rate < 0.01),
]

rows_html = "".join(
    f"<tr><td>{name}</td><td class='v'>{value}</td><td>{target}</td>"
    f"<td class='{'ok' if passed else 'bad'}'>{'PASS' if passed else 'FAIL'}</td></tr>"
    for name, value, target, passed in targets
)

threshold_html = "".join(
    f"<tr><td>{m}</td><td><code>{e}</code></td>"
    f"<td class='{'ok' if ok else 'bad'}'>{'PASS' if ok else 'FAIL'}</td></tr>"
    for m, e, ok in sorted(threshold_rows)
) or "<tr><td colspan='3'>No thresholds were declared.</td></tr>"

generated = datetime.now(timezone.utc).strftime("%Y-%m-%d %H:%M:%S UTC")

html = f"""<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<title>{title}</title>
<style>
 body {{ font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, sans-serif;
        margin: 0; background: #0d1117; color: #e6edf3; line-height: 1.6; }}
 .wrap {{ max-width: 900px; margin: 0 auto; padding: 40px 24px; }}
 h1 {{ margin: 0 0 4px; font-size: 26px; }}
 .sub {{ color: #8b949e; margin: 0 0 28px; }}
 h2 {{ font-size: 13px; text-transform: uppercase; letter-spacing: .06em;
       color: #8b949e; margin: 28px 0 10px; }}
 table {{ width: 100%; border-collapse: collapse; font-size: 14px;
          background: #161b22; border: 1px solid #30363d; border-radius: 8px; overflow: hidden; }}
 th, td {{ text-align: left; padding: 10px 14px; border-bottom: 1px solid #30363d; }}
 th {{ color: #8b949e; font-size: 12px; text-transform: uppercase; letter-spacing: .04em; }}
 tr:last-child td {{ border-bottom: none; }}
 .v {{ font-family: ui-monospace, SFMono-Regular, Menlo, monospace; }}
 .ok {{ color: #3fb950; font-weight: 600; }}
 .bad {{ color: #f85149; font-weight: 600; }}
 code {{ background: rgba(110,118,129,.15); padding: 1px 6px; border-radius: 4px;
         font-family: ui-monospace, SFMono-Regular, Menlo, monospace; font-size: 13px; }}
 .grid {{ display: grid; grid-template-columns: repeat(auto-fit, minmax(180px,1fr)); gap: 12px; }}
 .card {{ background: #161b22; border: 1px solid #30363d; border-radius: 8px; padding: 14px 16px; }}
 .card .k {{ color: #8b949e; font-size: 12px; text-transform: uppercase; letter-spacing: .04em; }}
 .card .n {{ font-size: 22px; font-family: ui-monospace, Menlo, monospace; margin-top: 4px; }}
 footer {{ margin-top: 32px; color: #8b949e; font-size: 13px; }}
</style>
</head>
<body><div class="wrap">
<h1>{title}</h1>
<p class="sub">banking-transaction-service &middot; generated {generated}</p>

<h2>Headline</h2>
<div class="grid">
  <div class="card"><div class="k">Throughput</div><div class="n">{fmt_num(throughput)}/s</div></div>
  <div class="card"><div class="k">Requests</div><div class="n">{fmt_num(total_requests)}</div></div>
  <div class="card"><div class="k">Iterations</div><div class="n">{fmt_num(iterations)}</div></div>
  <div class="card"><div class="k">Peak VUs</div><div class="n">{fmt_num(max_vus)}</div></div>
</div>

<h2>Targets</h2>
<table>
<thead><tr><th>Measure</th><th>Observed</th><th>Target</th><th>Verdict</th></tr></thead>
<tbody>{rows_html}</tbody>
</table>

<h2>Latency distribution</h2>
<table>
<thead><tr><th>Statistic</th><th>Value</th></tr></thead>
<tbody>
<tr><td>Average</td><td class="v">{fmt_ms(duration.get('avg'))}</td></tr>
<tr><td>Median</td><td class="v">{fmt_ms(duration.get('med'))}</td></tr>
<tr><td>p90</td><td class="v">{fmt_ms(duration.get('p(90)'))}</td></tr>
<tr><td>p95</td><td class="v">{fmt_ms(p95)}</td></tr>
<tr><td>p99</td><td class="v">{fmt_ms(p99)}</td></tr>
<tr><td>Max</td><td class="v">{fmt_ms(duration.get('max'))}</td></tr>
</tbody>
</table>

<h2>k6 thresholds</h2>
<table>
<thead><tr><th>Metric</th><th>Expression</th><th>Verdict</th></tr></thead>
<tbody>{threshold_html}</tbody>
</table>

<footer>Generated from the k6 JSON summary by scripts/perf/render_report.sh.</footer>
</div></body></html>
"""

with open(output_path, "w") as fh:
    fh.write(html)

print(f"Wrote {output_path}")
PYTHON
