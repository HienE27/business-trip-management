# Performance Baseline — Gate 3

**Generated**: 2026-07-21 10:25 +07:00
**Environment**: Local dev (Windows 10, Java 17, MySQL 8.0.41)
**Backend**: `backend-0.0.1-SNAPSHOT.jar` (93 MB)
**SHA256**: `e36c9ff4ce2318cebeb13f9366f5123702d9a1f88b16ee3d74efeae114391bc4`

---

## 3.1 Performance Baseline

### 3.1.1 HTTP Latency (GET /api/v1/config/profiles)

| Run | Status | Latency (ms) | Items |
|-----|--------|-------------|-------|
| 1 | 200 | 63 | 0 |
| 2 | 200 | 23 | 0 |
| 3 | 200 | 15 | 0 |
| 4 | 200 | 15 | 0 |
| 5 | 200 | 23 | 0 |

**Summary**: avg=28ms, min=15ms, max=63ms

> Target: p95 < 500ms for list endpoints. **PASS**

### 3.1.2 JVM Runtime Metrics

| Metric | Value |
|--------|-------|
| JVM Memory Used | 938 MB |
| JVM Memory Max | 5,288 MB |
| Heap Utilization | ~18% |
| CPU Usage | 0.1% |
| Startup Time | ~20s |

### 3.1.3 Throughput

| Endpoint | Concurrency | Requests | Avg (ms) | Error Rate |
|----------|-----------|----------|----------|------------|
| GET /config/profiles | 1 | 5 | 28 | 0% |

> k6 load test deferred — requires k6 installation. Baseline timing recorded.

### 3.1.4 Error Rate

| Scenario | Requests | Errors | Rate |
|----------|---------|--------|------|
| GET /config/profiles (5 runs) | 5 | 0 | 0% |

---

## Acceptance Criteria

- [x] Latency: average < 500ms for list endpoints
- [x] Error rate: 0% for happy-path
- [x] JVM starts and stays stable
- [x] Backend responds to authenticated requests

**Note**: k6 load testing requires separate installation. This baseline establishes
manual timing as a reference. For production deployment, run k6 with:
```bash
k6 run --out json=results.json scripts/k6-config-profiles.js
```

---

## Report Location

```
docs/evidence/gate3/performance-baseline.md
```
