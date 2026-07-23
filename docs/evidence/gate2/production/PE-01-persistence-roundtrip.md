# PE-01 — Persistence Round-trip

**Mục đích**: Xác nhận dữ liệu lưu vào DB khớp với dữ liệu trả ra qua API (không mất field, không sai type).

## Reproduce

```bash
TOKEN=<admin-token>

# 1. Tạo profile với data đầy đủ
PAYLOAD='{
  "name": "pe-01-test",
  "description": "Persistence round-trip test",
  "isFavorite": false,
  "tags": ["test", "pe-01"],
  "metadata": {"key1": "value1", "key2": 42}
}'

CREATE_RESP=$(curl -s -X POST http://localhost:8080/api/v1/profiles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d "$PAYLOAD")

ID=$(echo $CREATE_RESP | jq -r '.data.id')
echo "Created ID: $ID"

# 2. Đọc lại qua API
GET_RESP=$(curl -s http://localhost:8080/api/v1/profiles/$ID \
  -H "Authorization: Bearer $TOKEN")
echo "$GET_RESP" > /tmp/pe-01-get.json

# 3. Đọc trực tiếp từ DB
mysql -u root -p hospital_scheduler_test -e \
  "SELECT * FROM config_profile WHERE id = $ID\\G" > /tmp/pe-01-db.txt

# 4. So sánh
echo "=== API response ==="
cat /tmp/pe-01-get.json | jq .

echo "=== DB row ==="
cat /tmp/pe-01-db.txt
```

## Expected

- Tất cả field khớp giữa API response và DB row
- JSON field `tags` được lưu dưới dạng JSON column, khôi phục đúng
- JSON field `metadata` tương tự
- Date format: DB lưu DATETIME, API trả ISO 8601

## Actual

(Paste output + diff)

## Kết luận

PASS / FAIL — <comment>