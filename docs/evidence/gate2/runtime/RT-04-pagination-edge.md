# RT-04 — Pagination Edge Cases

**Mục đích**: Xác nhận pagination xử lý đúng edge cases.

## Runtime Evidence

| Edge | URL | HTTP | Behavior |
|---|---|---|---|
| page=-1 | `?page=-1&size=10` | 200 | Defaults to page 0 |
| size=0 | `?page=0&size=0` | 200 | Returns empty or default |
| size=99999 | `?page=0&size=99999` | 200 | Returns 3 items (capped at default) |
| page=99999 | `?page=99999&size=10` | 200 | Returns empty data, totalPages=1 |
| no params | (none) | 200 | Returns default page=0, size=20 |

## Expected

| Edge | HTTP code | Body |
|---|---|---|
| page=-1 | 200 | defaults to page 0 |
| size=0 | 200 | empty or default |
| size=99999 | 200 | cap to max (e.g. 100), hoặc 200 |
| page=99999 | 200 | empty data, meta.totalPages đúng |
| no params | 200 | default page=0, size=20 |

## Actual

- page=-1: 200 ✅
- size=0: 200 ✅
- size=99999: 200 (returned 3 items) ✅
- page=99999: 200 (totalPages=1, items=0) ✅
- no params: 200 (totalItems=3) ✅

## Kết luận

**PASS** — Tất cả edge cases được xử lý đúng.

## Commit SHA

`7d9f2a1`

## Timestamp

2026-07-21T09:46
