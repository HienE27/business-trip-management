# UAT Sign-Off Checklist — v1.0.0-rc1

**Release**: Hospital Scheduler v1.0.0-rc1
**Sign-off date**: __________
**Sign-off location**: __________
**Build/commit**: __________

---

## Scope of Sign-Off

This checklist confirms that the named UAT items have been **verified by an authorized human tester on a real browser session against the deployed v1.0.0-rc1 build**, not simulated or assumed.

A box is considered ✅ only when the action has been observed to succeed end-to-end (UI interaction + expected server response + visible side effect on DB or UI state).

If any item cannot be checked, do **not** sign off — open a defect instead.

---

## Functional Verification

| # | Area | Item | ☑ |
|---|---|---|---|
| 1 | **Authentication** | Login with valid credentials (admin / manager / staff) | ☐ |
| 2 | **Authentication** | Invalid credentials are rejected with clear error | ☐ |
| 3 | **Authentication** | JWT expiry triggers re-authentication flow | ☐ |
| 4 | **Dashboard** | Dashboard loads with correct KPI tiles (total shifts, coverage, balance) | ☐ |
| 5 | **Schedule Generation** | Auto Schedule Preview runs end-to-end for a period with valid config | ☐ |
| 6 | **Schedule Generation** | Auto Schedule Apply persists schedules and updates period status | ☐ |
| 7 | **Schedule Generation** | Compensation days are created automatically for every L01 shift | ☐ |
| 8 | **Schedule Generation** | L01 ↔ L02 conflict is rejected (BR-01) | ☐ |
| 9 | **Schedule Generation** | L03 ↔ L04 conflict is rejected (BR-02) | ☐ |
| 10 | **Schedule Exchange** | Manager approves a valid schedule exchange request | ☐ |
| 11 | **Schedule Exchange** | Staff cancels a pending self-initiated exchange | ☐ |
| 12 | **Leave Request** | Staff submits a leave request and sees it pending | ☐ |
| 13 | **Leave Request** | Manager approves a leave request, conflict detection updates | ☐ |
| 14 | **Notification** | In-app notification appears for relevant user after key events | ☐ |
| 15 | **Runtime Config** | Algorithm Configuration page loads and renders all parameter groups | ☐ |
| 16 | **Reports** | Feasibility report renders staff shortage, recommendations, eligibility breakdown | ☐ |
| 17 | **Audit Log** | Audit history records CREATE / UPDATE / DELETE on key entities | ☐ |
| 18 | **Admin** | Admin can manage users, roles, permissions (RBAC enforced) | ☐ |

---

## Known Limitations (Acknowledged, Not Blocking)

The following are accepted technical debt and **do not** block this sign-off. Reviewers acknowledge their presence:

- **LIM-UI-001** — Legacy `auto_compensation_enabled` toggle remains visible in Algorithm Config UI but has no functional effect (runtime scheduler does not consume this field). Cleanup planned for v1.1.0.
- **BR-04 inconsistency** — Greedy treats adjacent L01 as HARD block; V10 treats it as SOFT penalty. Both valid.
- **ScheduleScorer stub** — Benchmark scores are not meaningful in v1.0.
- **V10 incremental re-solve** — Not supported; full re-solve is performed.
- **RC-003 migration pending** — V18/V19 Flyway migrations are present in code but not executed in current deployment profile (JPA `ddl-auto=update` manages schema). Manual cleanup recommended for v1.1.0.

Reference: `docs/RELEASE_NOTES_v1.0.0.md` §5 Known Limitations.

---

## Open Investigations (Tracked Separately)

- **BUG-NEW-001** — `POST /api/v1/auto-schedule/preview` returns HTTP 400 for some valid input combinations (discovered during UI-001 impact test, 2026-07-18). Root cause unknown. Independent of RC-001. Tracking ticket: TBD.

Reference: `docs/RC_v1.0.0_FIXLIST.md` §"Open Investigations".

---

## Browser / Device Compatibility (Optional but Recommended)

| Browser | Version | Tested | Result |
|---|---|---|---|
| Chrome | latest | ☐ | __________ |
| Edge | latest | ☐ | __________ |
| Firefox | latest | ☐ | __________ |
| Mobile Safari | iOS 16+ | ☐ | __________ |
| Mobile Chrome | Android 13+ | ☐ | __________ |

---

## Performance Smoke Check (Optional but Recommended)

| Scenario | Threshold | Observed | Pass |
|---|---|---|---|
| Dashboard initial load | < 2s | ____ s | ☐ |
| Auto Schedule Preview (30-day period, 25 staff) | < 30s | ____ s | ☐ |
| Calendar month render (200 events) | < 1s | ____ s | ☐ |

---

## Sign-Off

By signing below, each role confirms that the verification items above have been **executed by an authorized person on a real environment**, and that the release is approved within the scope of their responsibility.

| Role | Name | Signature | Date |
|---|---|---|---|
| **Product Owner (PO)** | __________________ | __________________ | __________ |
| **Tech Lead** | __________________ | __________________ | __________ |
| **QA Lead** | __________________ | __________________ | __________ |
| **Release Manager** | __________________ | __________________ | __________ |

---

## Tag Procedure (after sign-off)

1. All four signatures above must be present.
2. Tag `v1.0.0-rc1` in git from the verified commit.
3. Trigger UAT cycle (this checklist).
4. After UAT pass, tag `v1.0.0` and deploy.

---

## Reference Documents

- `docs/RC_v1.0.0_FIXLIST.md` — RC items status
- `docs/RELEASE_NOTES_v1.0.0.md` — Release notes with known limitations
- `docs/QA_UI_001_IMPACT_CLASSIFICATION.md` — UI-001 evidence and classification
- `docs/CONFIG_ADMIN_DEEP_AUDIT_v2.md` — Configuration system audit
- `docs/UAT_CHECKLIST.md` — Full UAT test plan (broader scope, 50+ scenarios)

---

**END OF SIGN-OFF CHECKLIST**
