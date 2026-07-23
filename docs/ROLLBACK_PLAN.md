# Rollback Plan — v1.0.0-rc1

**Release**: Hospital Scheduler v1.0.0-rc1
**Audience**: Release Manager, DevOps, Tech Lead
**Last updated**: 2026-07-18

---

## Purpose

This document defines when and how to roll back `v1.0.0-rc1` if a critical issue is discovered after deployment. It exists so that the rollback decision and execution can be made quickly, by an on-call person, without having to reason from first principles under time pressure.

> **Rule of thumb**: If in doubt, roll back. The cost of an unnecessary rollback is roughly 10–15 minutes of downtime. The cost of *not* rolling back a critical regression can be much higher.

---

## Rollback Triggers

### 🔴 P0 — Immediate rollback (no further diagnosis needed)

These conditions are themselves evidence that the release is broken. Stop, roll back, then investigate the cause.

| Trigger | Detection | Example |
|---|---|---|
| Application startup failure | Container restarts repeatedly; logs show `ApplicationContext failed to load` | `UnsatisfiedDependencyException` for a critical bean |
| Authentication / login failure for any role | Any successful login returns 401/403 OR all logins fail | JWT signing key mismatch, role table missing |
| Schedule Preview returns HTTP 5xx (NOT 4xx) | `/api/v1/auto-schedule/preview` returns 500/502/503 | NullPointerException in scheduler |
| Data corruption in `schedule` table | Schedules with invalid `work_date` (NULL, far-future, or duplicate `uk_schedule_unique` violations) | DB constraint violation |
| Data corruption in `compensation_day` table | Compensation days without matching L01 schedule (orphaned rows) | Broken FK or scheduler bug |
| DB migration failure | Flyway reports V18/V19 migration error (if Flyway is enabled) | Schema mismatch |
| Smoke test fails on staging | Any of the 18 UAT items in `UAT_SIGNOFF_CHECKLIST.md` fails on the deployed build | — |

### 🟡 P1 — Investigate before rolling back

These conditions need triage. Roll back if root cause is unclear within 30 minutes.

| Trigger | Action |
|---|---|
| HTTP 4xx responses on core endpoints | Check BUG-NEW-001 first — it is known and not a rollback trigger. Otherwise, investigate request payload vs contract. |
| Performance degradation (>2x slower than v0.9.x) | Capture metrics, compare to baseline, decide based on trend |
| UI rendering glitches on specific pages | Capture screenshot, log browser console, decide based on severity |
| One specific shift type fails (e.g., L04 only) | Likely config-related; investigate first |

### 🟢 P2 — Do NOT roll back

These are tracked as post-release defects, not rollback triggers.

| Issue | Action |
|---|---|
| BUG-NEW-001 (HTTP 400 on Preview) | File ticket, track separately. Not a rollback trigger. |
| UI-001 (legacy toggle cosmetic issue) | Already accepted as Tech Debt. Not a rollback trigger. |
| Documentation typos | Fix in v1.0.1. |
| Minor UI bugs on non-critical screens | File ticket, fix in v1.0.1. |

---

## Rollback Procedure

### Phase 1 — Decision (≤5 minutes)

1. Release Manager or on-call DevOps **confirms** at least one P0 trigger is present.
2. Notify in `#release-incidents` channel: "Initiating rollback for v1.0.0-rc1 — trigger: [P0 condition]".
3. Update status page: "Service is being restored to a previous version".

### Phase 2 — Stop Application (≤5 minutes)

```bash
# On staging / production host
cd /opt/hospital-scheduler
docker compose down
```

Expected result: containers stop, port 8080 (backend) and 3000 (frontend) become unreachable.

### Phase 3 — Restore Database (≤30 minutes)

> **Always restore DB before redeploying app**, to ensure the new app sees a consistent schema.

```bash
# 1. Locate the pre-release backup
ls -la /opt/backups/hospital-scheduler/pre_v1.0.0-rc1_*.sql.gz

# 2. Stop MySQL
docker compose stop mysql

# 3. Restore backup
gunzip -c /opt/backups/hospital-scheduler/pre_v1.0.0-rc1_*.sql.gz \
  | docker exec -i mysql-container mysql -u root -p$MYSQL_ROOT_PASSWORD hospital_scheduler

# 4. Start MySQL
docker compose start mysql

# 5. Verify schema
docker exec -i mysql-container mysql -u root -p$MYSQL_ROOT_PASSWORD hospital_scheduler \
  -e "SHOW TABLES; SELECT COUNT(*) FROM algorithm_config;"
```

Expected result: Tables and row counts match pre-release snapshot.

> **Important**: If `algorithm_config.auto_compensation_enabled` was modified by the legacy admin UI during the release (UI-001 effect), the restored value should be `true` (the pre-release default). Verify this explicitly.

### Phase 4 — Redeploy Previous Artifact (≤10 minutes)

```bash
# 1. Pull previous Docker image
docker pull hospital-scheduler:v0.9.x

# 2. Update image tag in docker compose / deployment config
# Edit docker-compose.yml or k8s manifest to pin to v0.9.x

# 3. Start stack
docker compose up -d
```

Expected result: Containers start, health endpoint returns `UP`.

### Phase 5 — Verify Smoke Test (≤10 minutes)

Run the 18 items from `docs/UAT_SIGNOFF_CHECKLIST.md` against the restored build. All must pass.

If any fail → repeat from Phase 2 (the previous release is also broken, escalate to Tech Lead).

### Phase 6 — Notify Resolution (≤5 minutes)

```bash
# 1. Update status page
# Status: Restored to v0.9.x — incident closed

# 2. Post in #release-incidents
# "Rollback to v0.9.x complete. Smoke test PASS. Status page updated."

# 3. Email stakeholders
# Subject: [Resolved] v1.0.0-rc1 rolled back to v0.9.x
```

Total rollback time: **~65 minutes** (best case) to **~120 minutes** (worst case with DB restore delay).

---

## Backup Requirements

For this rollback plan to work, the following must be in place **before** any release:

| Backup | Location | Retention | Owner |
|---|---|---|---|
| Pre-release MySQL dump | `/opt/backups/hospital-scheduler/pre_v1.0.0-rc1_*.sql.gz` | 30 days | DevOps |
| Previous Docker image | Registry / `hospital-scheduler:v0.9.x` | Always | DevOps |
| Previous docker-compose config | Git tag `v0.9.x` | Always | DevOps |
| Environment config (env vars, secrets reference) | Git tag `v0.9.x` | Always | DevOps |

> **Verification before release**: Release Manager must confirm all 4 backup artifacts exist before tagging `v1.0.0-rc1`. See `docs/RELEASE_CHECKLIST.md` §1.4.

---

## What This Plan Does NOT Cover

This plan addresses **release-level rollback**. It does NOT cover:

- **Data-level rollback** for individual user errors (handled via audit + manual correction)
- **Schema rollback** for non-migration changes (each V18/V19 migration must have its own reverse migration if needed)
- **Disaster recovery** (DB corruption from disk failure, datacenter outage — separate DR plan)
- **Security incident response** (compromised credentials, data breach — separate IR plan)

---

## Rollback Decision Authority

| Decision | Authority |
|---|---|
| Initiate P0 rollback | Release Manager OR DevOps on-call (any single person) |
| Initiate P1 rollback | Release Manager + Tech Lead agreement |
| Postpone rollback for further investigation | Tech Lead OR PO |
| Declare rollback complete | Release Manager |

In emergencies (off-hours, no on-call available), any DevOps engineer may initiate P0 rollback unilaterally. Document the decision in the post-release report.

---

## Post-Rollback Activities

After a rollback is complete:

1. **Hold the release** — Do NOT attempt to re-deploy `v1.0.0-rc1` without root cause analysis.
2. **Open incident ticket** — Capture timeline, triggers observed, decision points.
3. **Root cause analysis** — Tech Lead investigates within 48 hours.
4. **Fix forward plan** — Decide between (a) fix and re-release v1.0.0-rc1, (b) skip to v1.0.0-rc2 with fix included, (c) rollback fully and release v1.0.1.
5. **Update this plan** — Add the trigger observed if it was not already covered.
6. **Post-mortem** — Tech Lead + PO + Release Manager, within 1 week.

---

## Reference Documents

- `docs/RELEASE_CHECKLIST.md` — Pre-release verification
- `docs/RELEASE_APPROVAL.md` — Governance approval
- `docs/RELEASE_NOTES_v1.0.0.md` — Known limitations
- `docs/RC_v1.0.0_FIXLIST.md` — RC items status
- `docs/UAT_SIGNOFF_CHECKLIST.md` — Smoke test reference

---

**END OF ROLLBACK PLAN**