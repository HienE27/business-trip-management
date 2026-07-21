# Changelog

Tất cả thay đổi đáng chú ý của dự án Hospital Scheduler được ghi nhận tại đây.

Định dạng dựa theo [Keep a Changelog](https://keepachangelog.com/vi/1.1.0/),
dự án tuân thủ [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

---

## [Unreleased] — Phase v11 Epic 1 (Config Engine)

### Added

- **PR-11-01** (commit `fc169dc`): `ConfigProfile` entity + repository + migration.
  - 9 query methods: `findByProfileKey`, `existsByProfileKey`, system/custom filters,
    category filter, default/favorite flags, search by name, find by tag.
  - 6 system profiles seeded: balanced, emergency, high-coverage, high-fairness,
    holiday, fast.
  - 18 repository tests (H2 embedded) — CRUD, filters, search.

### Changed

- `@Modifying(clearAutomatically = true)` on `clearAllDefaults` / `clearAllFavorites`
  to evict cached entities after bulk UPDATE.

---

## [v10.1-stable] — 2026-07-21

**Tag**: `v10.1-stable` @ `408ffb8` (branch `feature/v10-scheduling`)
**Trạng thái**: Baseline đã xác minh, sẵn sàng khởi động Phase v11.

### Added

- 18 alias endpoint (`LegacyPathAliasController`) cho khả năng tương thích ngược với frontend cũ.
- Unified `ConfigController` thay thế 4 endpoint rời rạc (`/auto-schedule/config`, `/config`, `/auto-schedule/runtime-config`, `/auto-schedule/auto-gen-config`).
- 5 mới: `GET /config/metadata`, `POST /config/validate`, `POST /config/reset`, `GET /config/diff`, `GET /config/presets`.
- Explain module: `/explain`, `/explain/algorithms`, `/explain/assignment`, `/explain/why-not`, `/explain/ranking`, `POST /explain/query`.
- AutoGenConfigService tách bạch khỏi SchedulingStateAccessor.

### Changed

- L01/L02/L03 **không còn** cross-specialty: chỉ L04 mới có `crossSpecialtyEnabled` + `crossSpecialtyRatio`.
- Config schema canonicalized: xóa 9 dead entries, deprecated `autoCompensationEnabled` (always-on).
- Frontend: xóa 6 dead components, `MetadataConfigEditor`, `FieldRendererInline`, unused API types.
- Balance strategy: deprecate `BALANCE_STRATEGY_WEIGHTED_FAIR`.
- Service: ủy quyền `AutoGenConfig` get/save cho `AutoGenConfigService`.

### Removed

- Dead helper methods `getStringListValue/getBooleanValue/getFloatValue`.
- Dead `getNonL04AllowedSpecialties` algorithm method.

### Fixed

- **Explain NPE** trong `ExplainService.explainWhyNot` / `explainCandidateRanking` khi `graph=null`.
- **`ExplainQueryRequest`** Jackson deserialize (`@Value @Builder` thiếu no-args constructor → 500).
- Production JAR fail start (BS-2) — Spring profile bootstrap.
- L01 adjacency detector: lân cận L01 qua ngày được tính chính xác khi preview ngày tiếp theo.

### Verified

| Hạng mục | Kết quả |
|---|---|
| Frontend TypeScript | 0 errors |
| `pnpm build` | SUCCESS (tất cả routes built) |
| Backend Compile | PASS |
| `mvn test` | **548 pass / 0 fail / 0 err / 3 skip** |
| Backend Runtime | VERIFIED — PID 36484, Tomcat 20.348s startup, profile `mysql` |
| Alias Compatibility | 18/18 endpoint → HTTP 200 với ADMIN JWT |
| Auto-schedule E2E | Preview 100% coverage, 0 conflicts, 35 schedules |
| Explain APIs | `/why-not`, `/assignment`, `POST /query` đều 200 |
| Fair-share runtime | 43 historical runs, 5 algorithms tracked |
| Regression Verification | RV-001 → RV-007 PASS |

### Known Limitations (forwarded to v11)

- Fairness score thấp (CV cao) khi dataset rất nhỏ (≤7 ngày) — đây là expected behavior, không phải regression.
- `/config/profiles` rỗng khi fresh DB — chưa có UI để tạo profile (Epic 1 của v11).
- Explain module cần sessionKey sandbox để graph đầy đủ — hiện trả về stub với hướng dẫn khi thiếu.

---

## [v10.0] — trước 2026-07-21

Không có release note chính thức. Toàn bộ lịch sử PR-001 → PR-005 được ghi nhận qua git history
của branch `feature/v10-scheduling`.

[unreleased]: https://github.com/tmHieu20-02/business-trip-management/compare/v10.1-stable...HEAD
[v10.1-stable]: https://github.com/tmHieu20-02/business-trip-management/releases/tag/v10.1-stable
