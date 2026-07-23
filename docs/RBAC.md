# RBAC Standardization — System-Wide Authorization

This document captures the complete design and the changes shipped in the
Hospital Scheduler RBAC overhaul. The end-state is a single, centralized
permission catalog on both backend and frontend with consistent enforcement
at the API, service, page, button, and form layers.

## 1. Source of Truth

| Layer | File | Purpose |
|---|---|---|
| Backend catalog | `backend/src/main/java/com/hospital/scheduler/security/Permissions.java` | All permission constants + role matrices |
| Backend JWT | `backend/src/main/java/com/hospital/scheduler/security/JwtService.java` | Embeds permissions as a JWT claim |
| Backend filter | `backend/src/main/java/com/hospital/scheduler/security/JwtAuthenticationFilter.java` | Loads permissions as `SimpleGrantedAuthority` |
| Backend service | `backend/src/main/java/com/hospital/scheduler/security/PermissionService.java` | DB-backed flattened permissions |
| Frontend catalog | `frontend/src/lib/permissions.ts` | Mirror of `Permissions.java` + role defaults |
| Frontend hook | `frontend/src/hooks/usePermissions.ts` | `can()`, `canAny()` for components |
| Frontend guard | `frontend/src/components/auth/RouteGuard.tsx` | Page-level permission gate |
| Frontend gate | `frontend/src/components/auth/PermissionGate.tsx` | Inline permission gate (hide, not disable) |

The seeder (`DataSeeder`) reads `Permissions.catalog()` /
`Permissions.allPermissions()` / `Permissions.managerPermissions()` /
`Permissions.staffPermissions()` to (re)build `app_permission` and
`role_permission` rows. Adding a new permission means: append to
`Permissions.java` → append to `permissions.ts` → re-run the seeder.

## 2. Role × Permission Matrix

| Role | Effective permissions | Notes |
|---|---|---|
| **ADMIN** | ALL | `Permissions.allPermissions()` |
| **MANAGER** | ALL − adminOnly | Cannot edit role matrix, delete audit, edit system config, run data integrity, edit auto-schedule config, edit app config |
| **STAFF** | `DASHBOARD_VIEW`, `STAFF_VIEW` (self), `SCHEDULE_VIEW` (self), `LEAVE_VIEW`, `LEAVE_CREATE`, `LEAVE_CANCEL_SELF`, `EXCHANGE_VIEW`, `EXCHANGE_CREATE`, `EXCHANGE_CANCEL_SELF`, `NOTIFICATION_VIEW`, `NOTIFICATION_MANAGE_SELF` | Self-service only |

## 3. Backend Enforcement

Every controller uses `hasAuthority('PERMISSION_NAME')` for the granular
check. Ownership-aware endpoints compose the permission check with the
existing `AuthContextService`:

```java
@PreAuthorize("hasAuthority('" + Permissions.STAFF_VIEW + "') " +
              "or @authContextService.isCurrentStaff(#id)")
```

401 (no / expired token) and 403 (authenticated but missing permission) are
standardized through `JwtAuthenticationEntryPoint`,
`JwtAccessDeniedHandler`, and `GlobalExceptionHandler`. All three return
the project's `ApiResponse<T>` envelope with UTF-8 Vietnamese messages and
the `WWW-Authenticate: Bearer ...` header on 401.

## 4. Frontend Enforcement

| Surface | Mechanism | Behaviour |
|---|---|---|
| Sidebar entries | `DashboardShell` filters by `requiredPermissions` | Entry hidden if user lacks all listed permissions |
| Page content | `<RouteGuard>` in `app/(dashboard)/layout.tsx` | Renders in-page `EmptyState` 403 if missing |
| Buttons / actions | `<PermissionGate>` or `usePermissions().can(Permission.X)` | Hidden entirely (not disabled) |
| Forms / modals | `<PermissionGate>` wrapper or `can()` guard | Not rendered; no submit possible |
| Data columns | `usePermissions().can(...)` ternary | Sensitive data is omitted from the response payload too (backend filters by ownership) |

The api-client (`lib/api-client.ts`) emits typed window events for
`403 Forbidden`, `401 AuthError`, and `NetworkError`. The `ApiToastBridge`
component subscribes to those events and surfaces a `useToast()` toast
without redirecting. A 401 that has a refresh token attempts one silent
refresh; only if the refresh fails does it kick the user to `/login`.

## 5. Permission Catalog (mirrored on backend + frontend)

```
DASHBOARD_VIEW, DASHBOARD_AGGREGATE
STAFF_VIEW, STAFF_CREATE, STAFF_UPDATE, STAFF_DELETE, STAFF_IMPORT
ROLE_VIEW, ROLE_EDIT
PERIOD_VIEW, PERIOD_CREATE, PERIOD_UPDATE, PERIOD_DELETE,
PERIOD_PUBLISH, PERIOD_ARCHIVE
SCHEDULE_VIEW, SCHEDULE_CREATE, SCHEDULE_UPDATE, SCHEDULE_DELETE,
SCHEDULE_PUBLISH, SCHEDULE_EXPORT
AUTO_SCHEDULE_VIEW, AUTO_SCHEDULE_RUN, AUTO_SCHEDULE_APPLY,
AUTO_SCHEDULE_CONFIG_VIEW, AUTO_SCHEDULE_CONFIG_EDIT
LEAVE_VIEW, LEAVE_CREATE, LEAVE_APPROVE, LEAVE_CANCEL_SELF
EXCHANGE_VIEW, EXCHANGE_CREATE, EXCHANGE_APPROVE, EXCHANGE_CANCEL_SELF
REPORT_VIEW, REPORT_EXPORT
HOLIDAY_VIEW, HOLIDAY_CREATE, HOLIDAY_UPDATE, HOLIDAY_DELETE
NOTIFICATION_VIEW, NOTIFICATION_CREATE, NOTIFICATION_BROADCAST,
NOTIFICATION_MANAGE_SELF
AUDIT_VIEW, AUDIT_DELETE
SYSTEM_LOG_VIEW
APP_CONFIG_VIEW, APP_CONFIG_EDIT
DATA_INTEGRITY_RUN
SPECIALTY_MANAGE, SHIFT_TYPE_MANAGE, SCHEDULE_TEMPLATE_MANAGE
```

## 6. How to Add a New Permission

1. Add the constant to `Permissions.java` and a description in `catalog()`.
2. Add the same constant to `frontend/src/lib/permissions.ts`.
3. If the permission belongs to a role, add it to the matching
   `*Permissions()` helper (e.g. `managerPermissions()`).
4. Use `hasAuthority('PERMISSION_X')` on the controller method.
5. Mirror on the frontend: `can(Permission.PERMISSION_X)` in a `Gate` or
   ternary.
6. Add the path to `ROUTE_PERMISSIONS` in `RouteGuard.tsx` if the entire
   page is gated.
7. Re-run the seeder (or restart the backend) so the new permission
   appears in `app_permission` and the role-permission matrix.

## 7. Tests

- `backend/src/test/.../security/PermissionsTest.java` — guard tests for
  the catalog (every constant is in the catalog, manager ⊊ all, staff
  ⊆ manager, staff never includes adminOnly).
- `frontend/src/components/auth/PermissionGate.test.tsx` — covers the
  AND/OR modes, fallback element, and missing permission.
- `frontend/src/hooks/usePermissions.test.ts` — covers the JWT-derived
  permission set, `canAny()`, and the unauthenticated case.
- `backend/src/test/.../exception/GlobalExceptionHandlerTest.java` —
  already covers 401/403/409/500 mapping through `ApiResponse`.
