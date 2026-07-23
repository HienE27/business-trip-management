# PE-03 — Audit Chronology

**Mục đích**: Xác nhận 8 write operations được log theo đúng thứ tự thời gian, không bị reorder.

## Mục mô tả

Thực hiện 8 write operations liên tiếp (POST, PUT, DELETE xen kẽ). Kiểm tra audit_history table:
- 8 entries mới
- `created_at` tăng dần đúng theo thứ tự thực hiện
- Mỗi entry có đúng `action`, `entity_type`, `entity_id`, `user_id`

## Reproduce

```bash
TOKEN=<admin-token>

# Snapshot số audit entry trước
BEFORE=$(mysql -u root -p hospital_scheduler_test -se "SELECT COUNT(*) FROM audit_history;")
echo "Before: $BEFORE"

# 8 write operations
for i in 1 2 3 4; do
  # Create
  ID=$(curl -s -X POST http://localhost:8080/api/v1/profiles \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"pe-03-$i\"}" | jq -r '.data.id')
  echo "Created: $ID"
  sleep 1
  
  # Update
  curl -s -X PUT http://localhost:8080/api/v1/profiles/$ID \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d "{\"name\":\"pe-03-$i-updated\"}" > /dev/null
  sleep 1
  
  echo "---"
done

# 8 entries mới (4 create + 4 update)
mysql -u root -p hospital_scheduler_test -e \
  "SELECT id, action, entity_type, entity_id, user_id, created_at 
   FROM audit_history 
   ORDER BY id DESC LIMIT 8;"
```

## Expected

- 8 entries mới (4 CREATE + 4 UPDATE)
- `created_at` tăng dần theo thứ tự thực hiện (1 giây gap)
- Action enum đúng: CREATE_PROFILE, UPDATE_PROFILE
- user_id đúng = admin user

## Actual

(Paste output)

## Kết luận

PASS / FAIL — <comment>