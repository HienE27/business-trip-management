# Release Notes — v11-backend-ready

**Version**: v11-backend-ready
**Release Date**: 2026-07-21
**Epic**: Epic 1 Backend — Config Engine
**Status**: Release Candidate

---

## Executive Summary

This release delivers the **Config Engine** (Epic 1 Backend) for the Hospital Scheduler system. It introduces a managed configuration profile system with version history, comparison, and audit trails, built on top of the existing `algorithm_config` infrastructure.

---

## What's New

### Config Profile Management

- **CRUD API** for configuration profiles (`/api/v1/config/profiles`)
- **Apply profile** to activate a configuration snapshot (`POST /config/profiles/{id}/apply`)
- **Toggle favorite** and **set default** profiles
- **Pagination + sort** with whitelist (nameVi, updatedAt, createdAt)
- **Filter** by category, system/custom, favorites, default
- **Version history** for each profile (`/config/profiles/{id}/history`)
- **Diff** between versions (`/config/profiles/{id}/diff`)
- **Health check** per profile (`/config/profiles/{id}/health`)
- **Import/Export** profiles as JSON

### Config Engine Core

- **`ConfigDomain`** — canonical in-memory model for all algorithm config fields
- **`ConfigDefaults`** — typed constants for default values (replaces scattered `getInt("key", default)`)
- **`ConfigValidator`** — 3-tier validation (field, group, cross-field)
  - Handles all field types: `Integer`, `Long`, `Double`, `Boolean`, `Enum`, `String[]` (chip_group)
- **`ConfigMapper`** — bidirectional map between `algorithm_config` rows and `ConfigDomain`

### API Consistency

- **Pagination** wrapped in `PageResponse<T>` → `ApiResponse<PageResponse<T>>`
- **Sort whitelist** enforced server-side
- **Page/size capping** (page ≤ 1000, size ≤ 100)
- **OpenAPI v3** annotations on all endpoints

---

## Bug Fixes in This Release

| ID | Title | Severity | Status |
|----|-------|----------|--------|
| Bug #001 | Apply profile fails with 422 — `l0xMinPerDay=0` | High | ✅ Closed |
| Bug #002 | `ConfigValidator.validateSingleField()` fails on `String[]` | High | ✅ Closed |
| RT-08 | `?favorite=true` and `?isDefault=true` filter params not working | Medium | ✅ Closed |

---

## API Endpoints

### Config Profiles

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/v1/config/profiles` | List profiles (paginated) |
| GET | `/api/v1/config/profiles/{id}` | Get profile by ID |
| GET | `/api/v1/config/profiles/by-key/{key}` | Get profile by key |
| GET | `/api/v1/config/profiles/default` | Get default profile |
| POST | `/api/v1/config/profiles` | Create profile |
| PUT | `/api/v1/config/profiles/{id}` | Update profile |
| DELETE | `/api/v1/config/profiles/{id}` | Delete profile |
| POST | `/api/v1/config/profiles/{id}/apply` | Apply profile |
| PUT | `/api/v1/config/profiles/{id}/favorite` | Toggle favorite |
| PUT | `/api/v1/config/profiles/{id}/default` | Set as default |
| GET | `/api/v1/config/profiles/{id}/history` | Version history |
| GET | `/api/v1/config/profiles/{id}/diff` | Diff between versions |
| GET | `/api/v1/config/profiles/{id}/health` | Profile health check |
| POST | `/api/v1/config/profiles/import` | Import profile |
| GET | `/api/v1/config/profiles/export/{id}` | Export profile |

---

## Known Limitations

1. **No concurrent edit conflict detection** — last-write-wins on simultaneous updates
2. **No profile locking** — profile can be applied while being edited
3. **Import/Export** uses manual JSON serialization — no schema validation on import
4. **Diff endpoint** shows structural diff only — not human-readable field-level explanations
5. **Health check** is advisory only — does not block apply

---

## Dependencies

| Dependency | Version | Notes |
|------------|---------|-------|
| Java | 17.0.15 | Temurin |
| Spring Boot | 3.5.5 | |
| Maven | 3.9.11 | |
| MySQL | 8.0.41 | |
| JJWT | 0.12.3 | Pinned for security |
| Springdoc | 2.6.0 | OpenAPI |

---

## Rollback Instructions

### If rollback needed before frontend integration:

```bash
# 1. Revert to previous commit
git revert HEAD

# 2. Rebuild
cd backend && mvn clean package -DskipTests

# 3. Restart
java -jar target/backend-0.0.1-SNAPSHOT.jar
```

### If rollback needed after frontend integration (breaking change):

```bash
# 1. Deploy previous JAR
# SHA256 of last stable: <previous stable SHA>

# 2. Database migration is NOT required — config_profile table can remain

# 3. Roll forward preferred over roll back
# Add new migration to fix rather than revert data
```

---

## Test Baseline

| Metric | Value |
|--------|-------|
| Total tests | 631 |
| Passed | 631 |
| Failures | 0 |
| Errors | 0 |
| Skipped | 3 |
| Build | SUCCESS |
| JaCoCo coverage — `scheduling.config` package (Epic 1 target) | **54%** |
| JaCoCo coverage — `scheduling.constraint` (business rules) | **94%** |
| JaCoCo coverage — `entity` | **96%** |
| JaCoCo coverage — overall | **23%** |

> Overall coverage (23%) is typical for projects with large algorithmic/sandbox modules not exercised by unit tests. The Epic 1 target package (`scheduling.config`) exceeds the 50% threshold at 54%.

---

## Artifacts

- **JAR**: `backend-0.0.1-SNAPSHOT.jar`
- **SHA256**: `e36c9ff4ce2318cebeb13f9366f5123702d9a1f88b16ee3d74efeae114391bc4`
- **OpenAPI**: Available at `/swagger-ui.html` after startup

---

## Next Steps

1. ✅ Backend merge to `main`
2. ⏳ Tag `v11-backend-ready`
3. ⏳ Frontend pin to `v11-backend-ready`
4. ⏳ PR-11-04 Frontend Profile UI
5. ⏳ PR-11-05 History/Diff UI
6. ⏳ PR-11-06 Governance integration
