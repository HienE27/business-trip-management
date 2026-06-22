# Staging Deployment Checklist — Hospital Scheduler

**Date:** 2026-06-22
**Persona:** DevOps / Team Lead
**Scope:** Deploying `develop` branch to a staging environment

---

## Pre-deployment Checks (local)

Run these before pushing to the staging deploy pipeline.

```bash
# Backend
cd backend
./mvnw clean verify           # 192 tests pass
./mvnw package -DskipTests    # produces .jar

# Frontend
cd frontend
pnpm exec tsc --noEmit        # 0 TypeScript errors
pnpm build                    # Compiled successfully
pnpm test                     # 300 vitest pass
```

---

## Environment Variables

### Backend (`application.properties` or env vars)

```env
SPRING_PROFILES_ACTIVE=staging
DATABASE_URL=jdbc:mysql://<STAGING_DB_HOST>:3306/hospital_scheduler
DATABASE_USERNAME=<staging_db_user>
DATABASE_PASSWORD=<staging_db_password>
JWT_SECRET=<secret_min_32_chars>
CORS_ALLOWED_ORIGINS=https://staging.hospital-scheduler.example.com

# Optional: email (SMTP for conflict alerts)
SMTP_HOST=smtp.staging.example.com
SMTP_PORT=587
SMTP_USERNAME=noreply@hospital-scheduler.example.com
SMTP_PASSWORD=<smtp_password>
```

### Frontend

```env
NEXT_PUBLIC_API_URL=https://staging-api.hospital-scheduler.example.com/api/v1
NEXT_PUBLIC_WS_URL=wss://staging-api.hospital-scheduler.example.com/ws
```

---

## Docker Deployment

> **Tip:** A dedicated `docker-compose.staging.yml` is provided in the repo root,
> plus `.env.staging.example` (copy to `.env.staging` locally, do **not** commit).
> Use that compose file instead of the inline commands below when you can.

### Backend

```bash
docker build -f backend/Dockerfile -t hospital-scheduler-backend:staging .
docker run -d \
  --name hospital-scheduler-backend \
  -p 8080:8080 \
  -e SPRING_PROFILES_ACTIVE=staging \
  -e DATABASE_URL=jdbc:mysql://<DB>:3306/hospital_scheduler \
  -e DATABASE_USERNAME=<user> \
  -e DATABASE_PASSWORD=<pass> \
  -e JWT_SECRET=<secret> \
  hospital-scheduler-backend:staging
```

### Frontend

```bash
docker build -f frontend/Dockerfile -t hospital-scheduler-frontend:staging .
docker run -d \
  --name hospital-scheduler-frontend \
  -p 3000:3000 \
  -e NEXT_PUBLIC_API_URL=https://staging-api.example.com/api/v1 \
  -e NEXT_PUBLIC_WS_URL=wss://staging-api.example.com/ws \
  hospital-scheduler-frontend:staging
```

---

## Smoke Tests (after deploy)

Use the Playwright E2E suite against the staging URL:

```bash
cd frontend
E2E_USERNAME=admin \
E2E_PASSWORD=<staging_admin_password> \
E2E_LOGIN_PATH=/login \
pnpm exec playwright test tests/e2e/smoke.spec.ts \
  --project=chromium \
  --base-url=https://staging.example.com
```

### Critical pages to verify manually

1. **Login** — can authenticate
2. **Dashboard** — calendar renders, no console errors
3. **Monthly Schedule (duty-24)** — period selector works, no 500 errors
4. **Auto Scheduling** — can load, no auth errors
5. **Notifications** — WebSocket connects (or polling works)
6. **Reports** — charts render

---

## Database Migration

If schema changes were made, run migrations before deploying:

```bash
# Verify schema is up to date
mysql -h <DB_HOST> -u <USER> -p <DB> -e "DESCRIBE schedule;"
# Expected tables: schedule, staff, schedule_period, shift_type,
#                 compensation_day, shift_requirement, notification,
#                 audit_history, system_log, schedule_exchange, leave_request
```

---

## Rollback Plan

If staging deploy fails:

1. **Frontend**: `docker stop hospital-scheduler-frontend && docker rm hospital-scheduler-frontend`
2. **Backend**: `docker stop hospital-scheduler-backend && docker rm hospital-scheduler-backend`
3. Re-tag the previous working image: `docker tag hospital-scheduler-backend:prev hospital-scheduler-backend:staging`
4. Re-run the `docker run` command with the previous tag.

---

## CI/CD Status

| Job | Status | Triggers |
|-----|--------|----------|
| Backend CI | `backend-ci.yml` | Push to `develop`, PR to `main/develop` |
| Frontend CI | `frontend-ci.yml` | Push to `develop`, PR to `main/develop` |
| E2E smoke | `frontend-ci.yml / e2e` | On frontend file changes |
| Accessibility | `frontend-ci.yml / accessibility` | On frontend file changes |
| Docker Build | `frontend-ci.yml / docker-build` | After successful build |

---

## Notes

- **Dark mode**: Auto-activated via `prefers-color-scheme`. Manual test on
  Windows (Settings > Personalize > Choose your color > Dark) and macOS
  (System Preferences > Appearance > Dark).
- **WebSocket**: Staging backend must have `spring-websocket` enabled.
  Test: open DevTools → Network → WS filter → should see `/ws` connection.
- **Conflict alerts**: Backend must have email configured for conflict
  alert emails to send.
- **Production**: After staging is stable, production deploy uses the same
  Docker images tagged with the release SHA.
