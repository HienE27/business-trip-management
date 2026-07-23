# PE-02 — Apply Idempotency

**Mục đích**: Xác nhận gọi `apply` 2 lần cùng config cho cùng state giống nhau (idempotent).

## Mô tả

Apply profile X → state Y. Apply lại profile X → state vẫn là Y (không thay đổi, không có side effect).

## Reproduce

```bash
TOKEN=<admin-token>
PROFILE_ID=<id-của-1-profile>

# Snapshot state trước apply
curl -s http://localhost:8080/api/v1/state -H "Authorization: Bearer $TOKEN" > /tmp/pe-02-before.json

# Apply lần 1
curl -s -X POST http://localhost:8080/api/v1/profiles/$PROFILE_ID/apply \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}' | jq .

# Snapshot sau apply 1
curl -s http://localhost:8080/api/v1/state -H "Authorization: Bearer $TOKEN" > /tmp/pe-02-after-1.json

# Apply lần 2 (cùng config)
curl -s -X POST http://localhost:8080/api/v1/profiles/$PROFILE_ID/apply \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{}' | jq .

# Snapshot sau apply 2
curl -s http://localhost:8080/api/v1/state -H "Authorization: Bearer $TOKEN" > /tmp/pe-02-after-2.json

# So sánh
diff /tmp/pe-02-after-1.json /tmp/pe-02-after-2.json
# Phải không có diff (state giống nhau)
```

## Expected

- Apply lần 1: thành công, state thay đổi
- Apply lần 2: thành công hoặc trả về "no-op", state KHÔNG thay đổi
- `diff` giữa `after-1` và `after-2` = empty

## Actual

(Paste output + diff result)

## Kết luận

PASS / FAIL — <comment>