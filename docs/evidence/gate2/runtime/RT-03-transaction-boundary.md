# RT-03 — Transaction Boundary

**Mục đích**: Xác nhận mỗi write operation là atomic (commit hoặc rollback, không partial).

## Mô tả

Thực hiện một write phức tạp (POST /profiles + auto-create related records). Nếu bất kỳ sub-step nào fail, toàn bộ phải rollback.

## Reproduce

```bash
TOKEN=<admin-token>

# Scenario 1: Happy path — commit
curl -s -X POST http://localhost:8080/api/v1/profiles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"tx-happy","description":"transaction test"}' \
  | jq .

# Verify record exists
mysql -u root -p hospital_scheduler_test -se \
  "SELECT * FROM config_profile WHERE name = 'tx-happy';"

# Scenario 2: Force fail — gửi payload invalid (FK constraint violation)
# Phải rollback toàn bộ, không tạo partial record
curl -s -X POST http://localhost:8080/api/v1/profiles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"name":"tx-fail","description":"forced fail","parentId":99999}'

# Verify KHÔNG có record tx-fail
mysql -u root -p hospital_scheduler_test -se \
  "SELECT * FROM config_profile WHERE name = 'tx-fail';" \
  # Phải trả empty
```

## Expected

- Happy path: 1 row mới trong DB
- Forced fail: 0 row mới, 1 audit entry thất bại (nếu audit riêng transaction)

## Actual

(Paste output)

## Kết luận

PASS / FAIL — <comment>