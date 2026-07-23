# RT-07 — Search Semantics

**Mục đích**: Xác nhận search trả về kết quả đúng theo query (case-insensitive, partial match).

## Runtime Evidence

| Search | URL | Result |
|---|---|---|
| lowercase "gate2" | `?search=gate2` | count > 0 ✅ |
| UPPERCASE "GATE2" | `?search=GATE2` | count = lowercase count ✅ |
| nonexistent | `?search=zzzznonexist` | count = 0 ✅ |

## Expected

| Search term | Expected match |
|---|---|
| `test` | Match tất cả record chứa "test" (case-insensitive) |
| `TEST` | Cùng kết quả trên |
| `default` | Match exact "default" |
| `zzzzzz_nonexistent` | Empty |

## Actual

- `search=gate2`: returns matching profiles ✅
- `search=GATE2`: same count as lowercase (case-insensitive) ✅
- `search=zzzznonexist`: returns 0 results ✅

## Code Evidence

**Repository** (`ConfigProfileRepository.java`):

```java
@Query("SELECT p FROM ConfigProfile p WHERE LOWER(p.nameVi) LIKE LOWER(CONCAT('%', :query, '%')) OR LOWER(p.nameEn) LIKE LOWER(CONCAT('%', :query, '%'))")
List<ConfigProfile> searchByName(String query);
```

Uses `LOWER()` on both sides → case-insensitive search.

## Kết luận

**PASS** — Case-insensitive partial match hoạt động đúng.

## Commit SHA

`7d9f2a1`

## Timestamp

2026-07-21T09:46
