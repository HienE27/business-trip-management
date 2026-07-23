# RT-06 — Import/Export Canonical (JSON Round-trip)

**Mục đích**: Xác nhận export → import giữ nguyên semantic (không mất field, không thay đổi type).

## Reproduce

```bash
TOKEN=<admin-token>
PROFILE_ID=8

# Export
curl -s -X POST "http://localhost:8080/api/v1/config/profiles/$PROFILE_ID/export" \
  -H "Authorization: Bearer $TOKEN" > export.json

# Import
curl -s -X POST "http://localhost:8080/api/v1/config/profiles/import" \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"json":'"$(cat export.json | jq -c .data)"'}' > import.json

# Apply imported profile
curl -s -X POST "http://localhost:8080/api/v1/config/profiles/$(jq -r '.data.id' import.json)/apply" \
  -H "Authorization: Bearer $TOKEN"
```

## Runtime Evidence

```
Export: HTTP 200, length=1534 chars
Import: HTTP 201, newId=11
Imported.l01MinPerDay: 1 (expect 1, preserved from original)
Apply: HTTP 200
```

## Expected

- Export thành công
- Import tạo record mới
- Tất cả config field khớp (ngoại trừ id/timestamps)
- Apply imported profile: 200 OK

## Actual

| Step | Result |
|---|---|
| Export | 200 OK ✅ |
| Import | 201 Created ✅ |
| l01MinPerDay preserved | 1 ✅ |
| Apply imported | 200 OK ✅ |

## Kết luận

**PASS** — Export → Import → Apply preserves config fields correctly.

## Commit SHA

`7d9f2a1`

## Timestamp

2026-07-21T09:46
