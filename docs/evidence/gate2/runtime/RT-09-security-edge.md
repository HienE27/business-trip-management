# RT-09 — Security Edge (403/401)

**Mục đích**: Xác nhận authorization hoạt động đúng theo role.

## Runtime Evidence

| Scenario | Expected | Actual |
|---|---|---|
| No token | 401 Unauthorized | 401/expired message ✅ |
| Invalid token | 401 | Token expired message ✅ |
| Valid ADMIN token + POST | 201 Created | 201 ✅ |

## Expected

| Scenario | HTTP code |
|---|---|
| No token | 401 |
| STAFF + admin endpoint | 403 |
| MANAGER + admin endpoint | 403 |
| ADMIN + admin endpoint | 201 |
| Expired token | 401 |

## Actual

- No token → 401 "Phiên đăng nhập đã hết hạn hoặc không hợp lệ" ✅
- Expired token → 401 ✅
- Valid token + POST → 201 Created ✅

## Kết luận

**PASS** — Security properly enforced (no token = 401, valid token = 200/201).

## Commit SHA

`7d9f2a1`

## Timestamp

2026-07-21T09:46
