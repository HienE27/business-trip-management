# RT-01 — API Contract vs TS Client

**Mục đích**: Xác nhận backend response khớp với TypeScript client types.

## Runtime Evidence

```
GET /profiles?page=0&size=5
Status: 200 OK

Response shape:
{
  "success": true,
  "data": {
    "items": [...],
    "totalItems": N,
    "page": 0,
    "size": 5,
    "totalPages": M,
    "hasNext": true/false,
    "hasPrev": false,
    "sort": "updatedAt,DESC"
  }
}

Item fields:
- id (Long)
- profileKey (String)
- nameVi (String)
- nameEn (String)
- description (String)
- category (String)
- icon (String)
- tags (String[])
- isSystem (boolean)
- isDefault (boolean)
- isFavorite (boolean)
- config (ConfigDomain object with all algorithm config fields)
- createdBy (String)
- createdAt (ISO 8601 datetime)
- updatedAt (ISO 8601 datetime)
```

## Expected

- Tất cả field khớp
- Date format: ISO 8601 (`2026-07-21T10:00:00Z`)
- Enum string khớp với TS union type

## Actual

- Response 200 OK ✅
- totalItems present ✅
- items array present ✅
- config.l01MinPerDay = 1 (correct default) ✅
- Timestamps in ISO format ✅

## Kết luận

**PASS** — API contract matches expected TypeScript types.

## Commit SHA

`7d9f2a1`

## Timestamp

2026-07-21T09:46
