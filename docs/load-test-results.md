# Local Load-Test Results

This is an observed local development run, not a performance guarantee or production benchmark.

## Environment

- Date: 2026-08-26
- Stack: `docker compose -f docker-compose.full.yml up --build -d`
- Workload: 100 `SIMULATED` jobs, `MEDIUM` priority, `durationMs: 20`, no failures
- Submission: generated idempotency key per request
- Completion: polled through the Chronos API

## Observed results

| Measurement | Result |
| --- | --- |
| Submission time | 6.19 seconds |
| Submission throughput | 16.16 jobs/second |
| Terminal completion observation | 1.89 seconds |
| Successful jobs | 100 |
| Failed, cancelled, or DLQ jobs | 0 |
| Prometheus submissions | 100 |
| Prometheus completions | 100 |
| Active workers after completion | 0 |
| Queue depth after completion | 0 |

Prometheus scraped both `chronos-api` and `chronos-worker` successfully. No retry or DLQ metric series appeared because this run had no such events. Repeat this run after changing host resources, worker concurrency, payload duration, or failure mode; do not compare results across environments without recording those differences.
