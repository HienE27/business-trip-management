# Release Approval — v1.0.0-rc1

**Release**: Hospital Scheduler v1.0.0-rc1
**Approval date**: __________
**Approved by**: __________
**Build / commit SHA**: __________
**Tag to be applied**: `v1.0.0-rc1`

---

## Final Status at Time of Approval

| Dimension | Status | Evidence |
|---|---|---|
| **Engineering** | ✅ Complete | Code merged, unit tests pass (`535 passed / 0 failed / 0 errors / 3 skipped`) |
| **Code Review** | ✅ Complete | PR reviewed and approved by Tech Lead |
| **QA Investigation** | ✅ Complete | `docs/QA_UI_001_IMPACT_CLASSIFICATION.md` |
| **Browser Verification** | ✅ Complete | Live browser sessions against deployed build |
| **Regression** | ✅ Complete | Existing test suite green; no new regressions |
| **Documentation** | ✅ Complete | Audit + FixList + ReleaseNotes + UAT Checklist |
| **Release Notes** | ✅ Complete | `docs/RELEASE_NOTES_v1.0.0.md` |
| **UAT Execution** | ✅ Complete | All 18 functional items checked; 4-role sign-off captured in `docs/UAT_SIGNOFF_CHECKLIST.md` |
| **Tag** | ⏳ Applied by Release Manager after this approval is signed | — |

---

## Accepted Technical Debt (Tracked for v1.1.0)

The following items were knowingly accepted for this release. They do **not** block release but are tracked as commitments for v1.1.0 cleanup:

### UI-001 — Legacy `auto_compensation_enabled` Toggle (P2)

- The Algorithm Configuration admin page still displays the `auto_compensation_enabled` toggle.
- The runtime scheduler does **not** consume this field, so the toggle has no behavioral effect on schedule generation or compensation day creation.
- Auto-compensation is always-on per the RC-001 decision (Option A: mandatory).
- **Cleanup target**: v1.1.0 — hide or migrate the toggle.

Reference: `docs/RC_v1.0.0_FIXLIST.md` UI-001, `docs/RELEASE_NOTES_v1.0.0.md` §5.4, `docs/QA_UI_001_IMPACT_CLASSIFICATION.md`.

---

## Open Investigations (Tracked Separately, Not Blocking Release)

The following items were identified during the RC cycle and require follow-up work that does **not** impact v1.0.0-rc1:

### BUG-NEW-001 — Auto Schedule Preview Returns HTTP 400 (Priority TBD)

- `POST /api/v1/auto-schedule/preview` returns HTTP 400 for some input combinations.
- Discovered during UI-001 impact testing (2026-07-18).
- Independent of RC-001 and UI-001.
- Root cause unknown; backend investigation required.
- **Tracking ticket**: TBD (to be opened by Release Manager post-tag).

Reference: `docs/RC_v1.0.0_FIXLIST.md` §"Open Investigations".

### RC-003 Migration Pending Execution

- V18 / V19 Flyway migration files are present in code.
- Flyway is not enabled in the current deployment profile (JPA `ddl-auto=update` manages schema).
- Legacy config rows may still exist in `algorithm_config` table on existing deployments.
- **Resolution target**: v1.1.0 — enable Flyway OR run manual cleanup SQL.

Reference: `docs/RELEASE_NOTES_v1.0.0.md` §5.5.

---

## Approval Statements

By signing below, each role confirms that the release has been approved within the scope of their responsibility and that all known technical debt and open investigations have been reviewed and accepted.

### Product Owner (PO)

> Confirms that the release meets the product requirements and that the accepted technical debt aligns with the product roadmap.

| Name | Signature | Date |
|---|---|---|
| __________________ | __________________ | __________ |

### Tech Lead

> Confirms that the engineering work, code review, and technical documentation meet the release engineering standard.

| Name | Signature | Date |
|---|---|---|
| __________________ | __________________ | __________ |

### QA Lead

> Confirms that the QA investigation, browser verification, and UAT execution have been completed and that all findings have been classified and documented.

| Name | Signature | Date |
|---|---|---|
| __________________ | __________________ | __________ |

### Release Manager

> Confirms that the release package is complete, all dependencies and migration notes have been reviewed, and the tag procedure may proceed.

| Name | Signature | Date |
|---|---|---|
| __________________ | __________________ | __________ |

---

## Tag and Release Date

Once all four signatures above are present, the Release Manager proceeds with:

| Action | Value | Executed at | Executed by |
|---|---|---|---|
| Git tag | `v1.0.0-rc1` | __________ | __________ |
| Tag commit | __________ | __________ | __________ |
| Build artifact published | __________ | __________ | __________ |
| Deployment to staging | __________ | __________ | __________ |
| Final release date | `v1.0.0` after UAT cycle pass | __________ | __________ |

---

## Reference Documents

- `docs/RC_v1.0.0_FIXLIST.md` — RC items status, gate tracking
- `docs/RELEASE_NOTES_v1.0.0.md` — Release notes with known limitations
- `docs/QA_UI_001_IMPACT_CLASSIFICATION.md` — UI-001 evidence and classification
- `docs/UAT_SIGNOFF_CHECKLIST.md` — UAT verification record and signatures
- `docs/CONFIG_ADMIN_DEEP_AUDIT_v2.md` — Configuration system audit

---

**END OF RELEASE APPROVAL**
