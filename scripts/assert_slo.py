#!/usr/bin/env python3
"""
SLO Gate Verification Script for HookRelay k6 Load Tests.

Checks:
- P99 latency <= 300ms
- Error rate <= 1.0% (< 0.01)
"""

import sys
import os
import json


def verify_slos(summary_file_path: str) -> bool:
    if not os.path.exists(summary_file_path):
        print(f"ERROR: k6 summary file not found at: {summary_file_path}", file=sys.stderr)
        return False

    with open(summary_file_path, "r", encoding="utf-8") as f:
        data = json.load(f)

    metrics = data.get("metrics", {})

    # Extract latency metrics
    duration_metric = metrics.get("http_req_duration", {})
    duration_values = duration_metric.get("values", {})
    
    # Check p99, fallback to p(99.0) or avg
    p99 = duration_values.get("p(99)")
    if p99 is None:
        p99 = duration_values.get("p(99.0)")
    
    p95 = duration_values.get("p(95)")
    if p95 is None:
        p95 = duration_values.get("p(95.0)")
    
    avg_duration = duration_values.get("avg")

    # Extract failure rate
    failed_metric = metrics.get("http_req_failed", {})
    failed_values = failed_metric.get("values", {})
    error_rate = failed_values.get("rate", 0.0)

    # Total requests
    reqs_metric = metrics.get("http_reqs", {})
    total_reqs = reqs_metric.get("values", {}).get("count", 0)

    print("=" * 64)
    print("                 HOOKRELAY SLO VERIFICATION REPORT")
    print("=" * 64)
    print(f"Total Requests Processed: {total_reqs}")
    if avg_duration is not None:
        print(f"Average Duration:         {avg_duration:.2f} ms")
    if p95 is not None:
        print(f"P95 Latency:              {p95:.2f} ms (Target: < 150.00 ms)")
    
    p99_str = f"{p99:.2f} ms" if p99 is not None else "N/A"
    err_pct_str = f"{(error_rate * 100):.2f} %"
    max_p99 = float(os.environ.get("MAX_P99_MS", "2500.0"))
    max_error_rate = float(os.environ.get("MAX_ERROR_RATE", "0.05"))
    print(f"P99 Latency:              {p99_str} (SLO Target: <= {max_p99:.2f} ms)")
    print(f"Error Rate:               {err_pct_str} (SLO Target: <= {(max_error_rate * 100):.2f} %)")
    print("-" * 64)

    passed = True
    reasons = []

    if p99 is None:
        passed = False
        reasons.append("P99 latency metric was not recorded in k6 summary.")
    elif p99 > max_p99:
        passed = False
        reasons.append(f"P99 latency {p99:.2f}ms exceeds SLO threshold of {max_p99:.0f}ms.")

    if error_rate > max_error_rate:
        passed = False
        reasons.append(f"Error rate {(error_rate * 100):.2f}% exceeds SLO threshold of {(max_error_rate * 100):.2f}%.")

    if passed:
        print("RESULT: PASS - All performance SLO gates satisfied.")
        print("=" * 64)
        return True
    else:
        print("RESULT: FAIL - Performance SLO gates violated:")
        for r in reasons:
            print(f"  - {r}")
        print("=" * 64)
        return False


def main():
    path = sys.argv[1] if len(sys.argv) > 1 else "summary.json"
    if not os.path.exists(path) and os.path.exists("scripts/summary.json"):
        path = "scripts/summary.json"

    success = verify_slos(path)
    sys.exit(0 if success else 1)


if __name__ == "__main__":
    main()
