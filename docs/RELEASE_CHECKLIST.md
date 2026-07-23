# Release Checklist — v1.0.0-rc1

**Audience**: Release Manager
**Release**: Hospital Scheduler v1.0.0-rc1
**Target tag**: `v1.0.0-rc1`

> **Note**: This checklist is for the Release Manager's operational steps.
> It is **distinct** from `UAT_SIGNOFF_CHECKLIST.md` (which records functional verification) and `RELEASE_APPROVAL.md` (which records governance approval).
> All three documents together form the complete release record.

---

## 1. Pre-Release

Each item below must be ✅ before the tag may be created.

### 1.1 Build & Test

| # | Item | Owner | ☑ |
|---|---|---|---|
| 1 | Build SUCCESS (`mvn clean install` on backend) | Backend | ☐ |
| 2 | Build SUCCESS (`pnpm build` on frontend) | Frontend | ☐ |
| 3 | Unit Test SUCCESS (535 passed / 0 failed / 0 errors / 3 skipped) | Backend | ☐ |
| 4 | Integration Test SUCCESS | Backend | ☐ |
| 5 | Lint SUCCESS (no new errors) | Backend + Frontend | ☐ |
| 6 | Docker image built successfully (`docker build` on backend) | DevOps | ☐ |
| 7 | Docker compose stack up (`docker compose up` on local) | DevOps | ☐ |

### 1.2 UAT & Sign-off

| # | Item | Owner | ☑ |
|---|---|---|---|
| 8 | UAT executed against deployed build | QA Lead | ☐ |
| 9 | UAT Sign-off Checklist completed (`docs/UAT_SIGNOFF_CHECKLIST.md`) | QA Lead | ☐ |
| 10 | PO signature captured | PO | ☐ |
| 11 | Tech Lead signature captured | Tech Lead | ☐ |
| 12 | QA Lead signature captured | QA Lead | ☐ |
| 13 | Release Manager signature captured | Release Manager | ☐ |
| 14 | `docs/RELEASE_APPROVAL.md` finalized | Release Manager | ☐ |

### 1.3 Documentation Review

| # | Item | Owner | ☑ |
|---|---|---|---|
| 15 | `docs/RELEASE_NOTES_v1.0.0.md` reviewed for accuracy | Tech Lead | ☐ |
| 16 | Known Limitations reviewed and accepted | PO + Tech Lead | ☐ |
| 17 | Technical Debt items (UI-001) reviewed and accepted | PO + Tech Lead | ☐ |
| 18 | Open Investigations (BUG-NEW-001) noted but not blocking | PO | ☐ |
| 19 | Migration notes reviewed (V18/V19 Flyway pending — manual cleanup noted) | Backend | ☐ |

### 1.4 Deployment Readiness

| # | Item | Owner | ☑ |
|---|---|---|---|
| 20 | DB backup completed (`mysqldump` snapshot of current state) | DevOps | ☐ |
| 21 | Rollback plan reviewed (`docs/ROLLBACK_PLAN.md`) | DevOps | ☐ |
| 22 | Rollback artifact available (previous tag `v0.9.x` Docker image) | DevOps | ☐ |
| 23 | Rollback procedure dry-run completed | DevOps | ☐ |
| 24 | Staging environment available and clean | DevOps | ☐ |
| 25 | Smoke test environment ready | DevOps | ☐ |

### 1.5 Communication

| # | Item | Owner | ☑ |
|---|---|---|---|
| 26 | Release announcement draft ready | Release Manager | ☐ |
| 27 | Known Limitations linked in announcement | Release Manager | ☐ |
| 28 | Stakeholders notified of pending release window | Release Manager | ☐ |

---

## 2. Release

Once all 28 items above are ✅, the Release Manager proceeds with:

| # | Action | Command / Tool | Result | ☑ |
|---|---|---|---|---|
| 1 | Verify HEAD commit SHA | `git rev-parse HEAD` | SHA recorded | ☐ |
| 2 | Verify clean working tree | `git status` | "nothing to commit" | ☐ |
| 3 | Create RC tag | `git tag -a v1.0.0-rc1 -m "Release v1.0.0-rc1"` | Tag created | ☐ |
| 4 | Push tag | `git push origin v1.0.0-rc1` | Tag pushed | ☐ |
| 5 | Verify tag exists | `git tag -l v1.0.0-rc1` | Visible | ☐ |
| 6 | Build Docker image for tag | `docker build -t hospital-scheduler:v1.0.0-rc1 .` | Image built | ☐ |
| 7 | Tag image | `docker tag hospital-scheduler:v1.0.0-rc1 hospital-scheduler:v1.0.0-rc1` | Confirmed | ☐ |
| 8 | Publish artifact to registry | Push to internal registry | Published | ☐ |
| 9 | Deploy to staging | `docker compose up -d` on staging | Running | ☐ |
| 10 | Smoke test on staging | Run smoke test suite | All pass | ☐ |
| 11 | Notify stakeholders | Send announcement | Sent | ☐ |

---

## 3. Post-Release

Within 24 hours of deployment:

| # | Item | Owner | ☑ |
|---|---|---|---|
| 1 | Monitor application logs for errors | DevOps | ☐ |
| 2 | Monitor health endpoints (`/actuator/health`) | DevOps | ☐ |
| 3 | Monitor scheduler metrics (Preview success rate, Compensation creation rate) | DevOps | ☐ |
| 4 | Verify scheduled jobs run on time (if any cron configured) | DevOps | ☐ |
| 5 | Verify notifications are delivered (if applicable) | DevOps | ☐ |
| 6 | Open tickets for any post-release findings | Release Manager | ☐ |
| 7 | Schedule post-release retrospective | Tech Lead + PO | ☐ |
| 8 | Create post-release report (`docs/POST_RELEASE_REPORT_v1.0.0.md`) | Release Manager | ☐ |
| 9 | File BUG-NEW-001 investigation ticket (independent of this release) | Release Manager | ☐ |
| 10 | Confirm rollback artifact is retained for at least 14 days | DevOps | ☐ |

---

## Rollback Decision

If any of the following are observed after release, **immediately** initiate rollback per `docs/ROLLBACK_PLAN.md`:

- 🔴 Application startup failure
- 🔴 Authentication / login failure for any role
- 🔴 Schedule Preview endpoint returns HTTP 5xx (note: HTTP 400 may be BUG-NEW-001, not a rollback trigger)
- 🔴 Data corruption in `schedule` or `compensation_day` tables
- 🔴 DB migration failure (V18/V19 if Flyway is enabled during this release)
- 🔴 Smoke test fails on staging

Other issues found post-release should be triaged separately, not treated as rollback triggers.

---

## Sign-Off

| Role | Name | Signature | Date |
|---|---|---|---|
| **Release Manager** | __________________ | __________________ | __________ |
| **Tech Lead** (verification) | __________________ | __________________ | __________ |

---

## Reference Documents

- `docs/UAT_SIGNOFF_CHECKLIST.md` — Functional verification record
- `docs/RELEASE_APPROVAL.md` — Governance approval record
- `docs/RELEASE_NOTES_v1.0.0.md` — Release notes
- `docs/RC_v1.0.0_FIXLIST.md` — RC items status
- `docs/QA_UI_001_IMPACT_CLASSIFICATION.md` — UI-001 evidence
- `docs/ROLLBACK_PLAN.md` — Rollback procedure

---

**END OF RELEASE CHECKLIST**