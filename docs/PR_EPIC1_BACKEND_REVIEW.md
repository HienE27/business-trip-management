# Epic 1 Backend — Config Profile Management

> **API Contract v1 — FROZEN** at this PR. Frontend consumes from here on.

## Summary

Hoàn thiện tầng backend cho module Config Profile Management trong Phase v11,
theo chiến lược "đóng từng lớp": Persistence → Service → REST API → OpenAPI docs.

Trước Epic 1: API surface chỉ có `ConfigController` (single-snapshot pattern), không có
versioning, không có audit riêng cho config, frontend không có cách list/filter profile
một cách có cấu trúc.

Sau Epic 1: 15 REST endpoint, paginated/sortable/filterable, fully audited, documented
qua OpenAPI/Swagger, ready cho PR-11-04 Frontend.

---

## What ships

### 4 PR đã hợp nhất (squash-friendly commit order)

```
1. PR-11-01  Persistence        +18 tests   entity + repository + initial migration
2. PR-11-02  Service            +12 tests   service layer + DTO + audit + exception cleanup
3. PR-11-03  REST API           +22 tests   pagination + sort whitelist + filter routing
4. OpenAPI Hardening (Task B)  + 2 tests   @Tag/@Operation/@ExampleObject + examples
   OpenAPI Hardening (Task C)    no new test 4 example variants cho Swagger dropdown
─────────────────────────────────────────────────────
Total: +54 tests, 0 deletion, 0 contract change after freeze
```

### Endpoint surface (15 endpoints)

| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/api/v1/config/profiles` | VIEW | list paged + sort + filter |
| GET | `/api/v1/config/profiles/{id}` | VIEW | by id |
| GET | `/api/v1/config/profiles/key/{profileKey}` | VIEW | by slug |
| GET | `/api/v1/config/profiles/default` | VIEW | default profile |
| POST | `/api/v1/config/profiles` | EDIT | snapshot current config |
| PUT | `/api/v1/config/profiles/{id}` | EDIT | partial update |
| DELETE | `/api/v1/config/profiles/{id}` | EDIT | system profile → 403 |
| POST | `/api/v1/config/profiles/{id}/apply` | EDIT | audit `algorithm_config` |
| POST | `/api/v1/config/profiles/key/{profileKey}/apply` | EDIT | same |
| POST | `/api/v1/config/profiles/{id}/duplicate` | EDIT | with new name |
| POST | `/api/v1/config/profiles/compare` | VIEW | A vs B, or A vs current |
| GET | `/api/v1/config/profiles/{id}/export` | VIEW | JSON export |
| POST | `/api/v1/config/profiles/import` | EDIT | JSON import |
| POST | `/api/v1/config/profiles/{id}/favorite` | EDIT | toggle |
| POST | `/api/v1/config/profiles/{id}/default` | EDIT | set as default |

### Pagination contract

| Param | Default | Bound | Behavior |
|---|---|---|---|
| `page` | 0 | `0 ≤ page ≤ 1000` | cap at 1000 |
| `size` | 20 | `0 < size ≤ 100` | cap at 100 |
| `sort` | `nameVi,ASC` | whitelist 6 field | 400 nếu vi phạm |

Sort whitelist: `nameVi`, `nameEn`, `category`, `isFavorite`, `createdAt`, `updatedAt`.

### Response envelope (canonical)

```json
{
  "success": true,
  "data": { ... },
  "timestamp": "2026-07-21T09:15:00"
}
```

List endpoints trả `data` kiểu `PageResponse<T>`:

```json
{
  "items": [...],
  "page": 0,
  "size": 20,
  "totalItems": 135,
  "totalPages": 7,
  "hasNext": true,
  "hasPrev": false,
  "sort": "nameVi,ASC"
}
```

### Error contract

| Status | Khi nào | Source |
|---|---|---|
| 400 | Bean Validation, sort/filter invalid, JSON malformed | `@Valid`, `BadRequestException` |
| 403 | System profile mutation | `ForbiddenOperationException` |
| 404 | Profile not found | `ResourceNotFoundException` |
| 409 | Unique/FK conflict | `DataIntegrityViolationException` |
| 500 | Internal | catch-all |

Mọi 4xx/5xx đều `ApiResponse.error(message, data=requestId)`.

---

## What does NOT ship

Đây là danh sách **chủ động loại trừ** để giữ phạm vi Epic 1 đúng nghĩa "Backend foundation":

- ❌ **Frontend UI** — đó là PR-11-04.
- ❌ **History endpoint** — đó là PR-11-05.
- ❌ **Governance approval workflow** — đó là PR-11-06.
- ❌ **Cache layer** (Redis/Caffeine) — đó là PR-12.
- ❌ **`apply()` audit `config_profile` table** — hiện chỉ audit `algorithm_config`
  vì đó là nơi mutation thực sự xảy ra. Event thứ hai cho `CONFIG_PROFILE_APPLIED`
  sẽ được xét trong PR-11-06 Governance.
- ❌ **`PageResponse.sort` lowercase** — giữ nguyên `"nameVi,ASC"` (uppercase) theo
  Spring convention. Đây là phần của v1 contract.
- ❌ **`Etag` / `If-Match`** — concurrency control sẽ đến sau khi có feedback
  từ Frontend về use case thực tế.

---

## Files changed

```
backend/src/main/java/com/hospital/scheduler/
├── controller/
│   └── ConfigProfileController.java          [+OpenAPI annotation, refactored to PageResponse]
├── dto/response/
│   └── PageResponse.java                     [NEW — canonical paged envelope]
├── exception/                                [unchanged — uses project-standard exceptions]
└── scheduling/config/
    ├── ConfigProfile.java                    [PR-11-01 entity]
    ├── ConfigProfileRepository.java          [+paged variants]
    ├── ConfigProfileService.java             [refactor — split DTO into dedicated package]
    ├── ConfigProfileSort.java                [NEW — whitelist + parser]
    └── dto/
        ├── ConfigProfileDto.java             [+@Schema annotation]
        ├── CreateProfileRequest.java         [+@Schema annotation]
        ├── UpdateProfileRequest.java         [+@Schema annotation]
        ├── OpenApiExamples.java              [NEW — 4 profile variants + 4 error + 4 request examples]
        ├── DiffEntryDto.java                 [PR-11-02]
        └── ProfileComparisonDto.java         [PR-11-02]

backend/src/test/java/com/hospital/scheduler/
├── controller/
│   ├── ConfigProfileControllerListTest.java   [NEW — 12 tests]
│   └── ConfigProfileControllerSurfaceTest.java [NEW — 2 tests, regression guard]
├── dto/response/
│   └── PageResponseTest.java                  [NEW — 3 tests]
└── scheduling/config/
    └── ConfigProfileSortTest.java             [NEW — 7 tests]
```

Net: **~700 lines added, 0 lines removed** trong production code.

---

## Test summary

| Metric | Value |
|---|---|
| Tests before Epic 1 | 548 |
| PR-11-01 delta | +18 |
| PR-11-02 delta | +12 |
| PR-11-03 delta | +22 |
| OpenAPI Hardening (B+C) delta | +2 |
| **Tests after Epic 1** | **602** |
| Build status | SUCCESS |
| Test runtime | <60s |

Surface test (`ConfigProfileControllerSurfaceTest`) chạy reflection để đảm bảo
15 endpoint luôn mang `@Operation` — regression guard chống tình trạng refactor
xóa mất annotation mà không ai nhận ra.

---

## Migration / rollout

Không có breaking change đối với:

- Bất kỳ client nào hiện đang gọi `ConfigController` cũ — endpoint đó vẫn hoạt động.
- Database — schema không thay đổi (chỉ thêm table `config_profile`).

Frontend Team có thể chuyển sang dùng `ConfigProfileController` ngay khi PR này merge.

---

## Review checklist

- [x] API Contract (15 endpoints) — đầy đủ, idempotent
- [x] Swagger render đúng — `/swagger-ui.html` → tab "Config Profiles"
- [x] Response examples — 4 variant profile + 4 error + 4 request examples
- [x] Validation messages — tiếng Việt, từ PR-11-02
- [x] Status codes — 200/201/204/400/403/404/409
- [x] Pagination — size ≤ 100, page ≤ 1000
- [x] Sorting — whitelist 6 field, 400 nếu vi phạm
- [x] Filtering — category, systemOnly, customOnly, favoritesOnly, search
- [x] Security annotations — `@PreAuthorize` trên mọi method

---

## Roadmap tiếp theo

```
PR-11-04  Frontend Profile UI     ⏳ chờ merge Epic 1 này
PR-11-05  History + Diff endpoint ⏳
PR-11-06  Governance              ⏳
PR-12      Cache + Performance    ⏳
```

Khi PR-11-04 bắt đầu, mọi thay đổi API phải đi qua quy trình version bump rõ ràng —
không patch payload v1.
