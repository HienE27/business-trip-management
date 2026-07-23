# RT-08 — Favorite + Default Filter

**Mục đích**: Xác nhận filter `?favorite=true` và `?isDefault=true` hoạt động đúng.

## Mô tả

- Set 1 profile làm favorite → filter trả về đúng 1 record đó
- Set 1 profile làm default → filter trả về đúng 1 record đó
- Verify chỉ có 1 default tại một thời điểm (business rule)

## Reproduce

```bash
TOKEN=<admin-token>
BASE=http://localhost:8080/api/v1/config/profiles

# 1. List all profiles
curl -s "$BASE" -H "Authorization: Bearer $TOKEN" | jq '.data | length'

# 2. Filter favorite=true (note: param name is "favorite", not "favoritesOnly")
curl -s "$BASE?favorite=true" -H "Authorization: Bearer $TOKEN" | jq '.data | length'
# Phải >= 1, chứa profile đã mark favorite

# 3. Filter isDefault=true (note: param name is "isDefault")
curl -s "$BASE?isDefault=true" -H "Authorization: Bearer $TOKEN" | jq '.data | length'
# Phải = 1, đúng profile đang là default

# 4. Verify business rule: chỉ 1 default
mysql -u root -p hospital_scheduler -se \
  "SELECT COUNT(*) FROM config_profile WHERE is_default = TRUE;"
# Phải = 1
```

## Expected

- Filter `?favorite=true` trả về đúng records đã mark
- Filter `?isDefault=true` trả về đúng 1 record (nếu có set)
- DB constraint: chỉ 1 record có `is_default=TRUE`

## Actual

(Paste output)

## Kết luận

**PASS** — `?favorite=true` → 1 item (profile 8), `?isDefault=true` → 1 item (profile 8)

## Commit SHA

`7d9f2a1` (hotfix Gate 2)

## Timestamp

2026-07-21T09:37

## Mô tả

- Set 1 profile làm favorite → filter trả về đúng 1 record đó
- Set 1 profile làm default → filter trả về đúng 1 record đó
- Verify chỉ có 1 default tại một thời điểm (business rule)

## Reproduce

```bash
TOKEN=<admin-token>
BASE=http://localhost:8080/api/v1/profiles

# 1. Mark favorite
FAV_ID=<id-của-1-profile>
curl -s -X POST $BASE/$FAV_ID/favorite -H "Authorization: Bearer $TOKEN"

# 2. Filter favorite
curl -s "$BASE?favorite=true" -H "Authorization: Bearer $TOKEN" | jq '.data | length'
# Phải >= 1, chứa FAV_ID

# 3. Mark default (1 record chỉ được default)
DEFAULT_ID=<id-của-1-profile-khác>
curl -s -X POST $BASE/$DEFAULT_ID/default -H "Authorization: Bearer $TOKEN"

# 4. Filter default
curl -s "$BASE?default=true" -H "Authorization: Bearer $TOKEN" | jq '.data | length'
# Phải = 1, đúng DEFAULT_ID

# 5. Verify business rule: chỉ 1 default
mysql -u root -p hospital_scheduler_test -se \
  "SELECT COUNT(*) FROM config_profile WHERE is_default = TRUE;"
# Phải = 1
```

## Expected

- Filter `favorite=true` trả về đúng records đã mark
- Filter `default=true` trả về đúng 1 record (nếu có set)
- DB constraint: chỉ 1 record có `is_default=TRUE`

## Actual

(Paste output)

## Kết luận

PASS / FAIL — <comment>