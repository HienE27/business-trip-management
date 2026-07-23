# v10.1-stable — Release Note

> **Ngày phát hành**: 2026-07-21
> **Tag**: [`v10.1-stable`](https://github.com/tmHieu20-02/business-trip-management/releases/tag/v10.1-stable)
> **Commit**: `408ffb8`
> **Branch**: `feature/v10-scheduling`

## Tóm tắt

Baseline ổn định sau 5 cleanup PR (PR-001 → PR-005) và 2 bug-fix (BS-1, BS-2). Toàn bộ
pipeline (frontend typecheck → backend compile → backend tests → backend runtime →
end-to-end regression) đều xanh. Sẵn sàng làm điểm khởi đầu cho **Phase v11**.

---

## Số liệu tổng hợp

| Chỉ số | Giá trị |
|---|---|
| Frontend routes | 30+ (Static + Dynamic) |
| Backend Java sources | 700+ |
| Backend Test sources | 66 |
| Backend Test cases | 548 |
| Backend Test pass rate | 100% (0 fail / 0 err) |
| Skipped | 3 (archived sources không thể load under Surefire) |
| Frontend TypeScript errors | 0 |
| Alias endpoints | 18 |
| Specialties active | 6 (Ngoại, Nội, Sản, Nhi, Mắt, Răng) |
| Staff seeded | 43 |
| Shift types | 4 (L01, L02, L03, L04) |
| Algorithms tracked | 5 (GREEDY, FAIR_GREEDY, CSP_MRV_FC, ROUND_ROBIN, GENETIC) |

---

## Cleanup highlights

### PR-001 — Alias compatibility
- Bổ sung `LegacyPathAliasController` map 18 endpoint legacy về canonical mới.
- Frontend cũ chạy được không cần đổi URL.

### PR-002 — Config refactor
- `ConfigController` unified 4 endpoint.
- Bỏ 9 dead entries trong metadata.
- Canonicalize string constants (LOGS, ERROR, ...).
- AutoGenConfigService tách khỏi SchedulingStateAccessor.

### PR-003 — Frontend hygiene
- Xóa 6 dead components.
- Bỏ `MetadataConfigEditor`, `FieldRendererInline`, các type không dùng.

### PR-004 — Backend hygiene
- Xóa dead helper methods `getStringListValue/getBooleanValue/getFloatValue`.
- Drop dead `getNonL04AllowedSpecialties`.

### PR-005 — L04 Cross-specialty
- L01/L02/L03: **không có** cross-specialty (chỉ một specialty pool duy nhất).
- L04: vẫn cho phép cross-specialty khi bật `l04CrossSpecialtyEnabled`.
- `l04CrossSpecialtyRatio` (mặc định 0.3) + `l04BalanceStrategy` (FAIR_DISTRIBUTE mặc định).

### BS-1, BS-2 — Bug fix
- BS-1: Stale test stubs.
- BS-2: Production JAR không start được.
- BS-3 (in this release): Explain NPE + Jackson deserialize.

---

## Architecture snapshot

### Backend (Spring Boot 3.x)

```
com.hospital.scheduler
├── algorithm/                # CSP, Greedy, FairShare, BalanceScore
├── benchmark/                # ConstraintCoverageTracker, StressTest
├── config/                   # RateLimitingFilter, WebMvcConfig
├── controller/               # REST endpoints (40+)
├── digital/
│   └── sandbox/              # DecisionGraph, Replay, Timeline, Promotion
├── dto/                      # Request + Response DTOs
├── entity/                   # JPA entities (15+ tables)
├── exception/                # GlobalExceptionHandler
├── explain/                  # ExplainService, NaturalLanguageFormatter
├── governance/               # ApprovalRequest, AuditEvent, ConfigVersion
├── monitoring/               # SchedulerMetrics, SchedulingHealthIndicator
├── repository/               # Spring Data JPA
├── scheduling/
│   ├── config/               # AlgorithmConfig
│   ├── score/                # ScoreSnapshot
│   └── ...                   # Lock, State accessor, Eligibility
└── service/                  # Business logic (50+ services)
```

### Frontend (Next.js 16 + pnpm)

```
frontend/src/
├── app/(dashboard)/          # Protected routes (30+ pages)
├── components/               # ui/, auto-scheduling/, monthly-schedule/
├── features/config/          # ConfigContext, ProfileSelector
├── hooks/                    # Custom React hooks
├── lib/                      # api-client, utils
└── types/                    # TypeScript types
```

---

## Cách rollback nếu Phase v11 phát sinh regression

```bash
# Xem danh sách tag
git tag --list

# Rollback về v10.1-stable
git checkout v10.1-stable

# Tạo branch tạm từ baseline
git checkout -b hotfix/from-v10.1-stable
```

---

## Bước tiếp theo

- **Phase v11 Epic 1 — Config Engine**: profile-based config CRUD, version history, diff UI.
- Sau đó: Metadata Engine → Explain Engine → Governance → Scheduler Optimization → Digital Twin.

Mỗi Epic chia thành PR 300–800 dòng diff, review-friendly.
