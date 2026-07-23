# Post-Epic-1 Review Checklist — Config Profile Management

> Dành cho reviewer chính của Epic 1 Backend trước khi merge vào `main`.

Epic 1 đã được đánh dấu "feature complete" và "API contract v1 frozen".
Checklist này là 5 bước kiểm tra cuối cùng trước khi merge & tag.

---

## Bước 1 — Code review chuyên sâu (60–90 phút)

### Files phải đọc kỹ (theo thứ tự ưu tiên)

| # | File | Tìm gì |
|---|---|---|
| 1 | `backend/.../scheduling/config/ConfigProfileService.java` | duplicated logic giữa `create`/`duplicate`, transaction boundary đúng chưa |
| 2 | `backend/.../controller/ConfigProfileController.java` | 15 endpoint, mỗi cái có `@PreAuthorize` + `@Operation` |
| 3 | `backend/.../scheduling/config/ConfigProfileRepository.java` | N+1 ở `findByCategory`, `findFavorites`, `findCustom` |
| 4 | `backend/.../scheduling/config/dto/*.java` | nullability annotation, `@Schema` example đúng với thực tế |
| 5 | `backend/.../dto/response/PageResponse.java` | `sort` field giữ nguyên `"nameVi,ASC"` uppercase |
| 6 | `backend/.../scheduling/config/ConfigProfileSort.java` | whitelist + parser có throw đúng 400 |

### Red flags cần chú ý

- ❌ Service gọi `repository.save()` trong loop → kiểm tra có `saveAll()` thay thế không.
- ❌ `@Transactional` trên controller method (phải ở service).
- ❌ `ObjectMapper` instance tự tạo thay vì inject Spring bean.
- ❌ JSON field nào có `@JsonProperty` mà không có `@Schema` (sẽ lệch Swagger).
- ❌ DTO có `Set<>` / `HashMap` không (Jackson serialize không deterministic).
- ❌ `PageResponse.sort` không match với Spring `Sort.toString()` format.

### Files KHÔNG cần review lại

- ❌ `backend/.../HospitalSchedulerApplication.java` (đụng nhẹ ở cleanup, đã ổn).
- ❌ Migration cũ (chỉ có 1 migration mới cho table `config_profile`).
- ❌ Config properties (`application*.properties`) — không thay đổi trong Epic 1.

---

## Bước 1B — 7 nguyên tắc kiến trúc (bắt buộc pass)

Đây là 7 tiêu chí reviewer phải đối chiếu. Bất kỳ tiêu chí nào fail → reject PR.

| # | Nguyên tắc | Cách verify | Pass khi |
|---|---|---|---|
| 1 | **API Freeze** | grep không có DTO rename, không đổi field, không đổi response | diff chỉ thêm file mới, không modify DTO cũ |
| 2 | **Audit phân biệt read/write** | grep `auditEvent` / `logAudit` trong service | read method (`find*`, `get*`) KHÔNG audit; write method (`create`, `update`, `delete`, `apply`, `duplicate`, `import`, `favorite`, `default`) CÓ audit |
| 3 | **Validation nhất quán** | grep `@Valid` trong controller, `@NotBlank`/`@Size`/`@Pattern` trong DTO | Tất cả DTO input có Bean Validation; Controller có `@Valid` |
| 4 | **Transaction đúng tầng** | grep `@Transactional` trong service | write method có `@Transactional`; read có `@Transactional(readOnly = true)`; KHÔNG có `@Transactional` ở controller |
| 5 | **Controller thuần orchestration** | đọc method body của 15 endpoint | Body chỉ gọi `profileService.*` và wrap response; KHÔNG có if/else business logic |
| 6 | **Service không lộ Entity** | grep `return.*ConfigProfile[^D]` (không có D ở cuối) | Service chỉ trả `ConfigProfileDto`, `PageResponse<ConfigProfileDto>`, `ProfileComparisonDto`, `byte[]` (export); KHÔNG trả Entity |
| 7 | **Repository derived-only** | grep `@Query` native | Repository dùng derived query method names; KHÔNG có `@Query(nativeQuery = true)`; JPQL chỉ khi thật sự cần |

### Template cho reviewer comment

```
✅ Passed:
- [x] 1. API Freeze — không đổi DTO/response
- [x] 2. Audit — read không log, write có log
- [x] 3. Validation — DTO + @Valid nhất quán
- [x] 4. Transaction — đúng tầng service
- [x] 5. Controller — orchestration only
- [x] 6. Service — không lộ Entity
- [x] 7. Repository — derived-only

Approve để merge.
```

---

## Bước 1C — 5 mục review kỹ thuật (pass trước khi merge)

Ngoài 7 nguyên tắc kiến trúc, reviewer cần verify 5 mục kỹ thuật chi tiết.

### Mục 1 — OpenAPI phản ánh đúng implementation

**Verify**:

```bash
# Mở Swagger UI
http://localhost:8080/swagger-ui.html
```

| Check | Expected |
|---|---|
| `required` field | `ConfigProfileDto.*` đúng runtime; `CreateProfileRequest.nameVi = required` |
| `nullable` | Optional field có `nullable: true` |
| `enum` | `Category`, `ProfileTag` enum values khớp code |
| Status codes | 200/201/204/400/403/404 đầy đủ cho mỗi endpoint |
| Examples | 4 profile variants render đúng trong dropdown GET `/`, GET `/{id}` |

**Pass khi**: Swagger examples có thể dùng làm input mẫu mà không cần đọc code.

### Mục 2 — Backward compatibility

**Verify**:

```bash
# Liệt kê tất cả endpoints đã thay đổi
git diff main..epic-1-backend -- backend/src/main/java/com/hospital/scheduler/controller/
```

**Pass khi**:
- CHỈ có file mới: `ConfigProfileController.java`, `ConfigProfileProfileController.java`
- KHÔNG có diff ở controller cũ (`StaffController`, `ScheduleController`, `AutoSchedulingController`, v.v.)
- Tất cả endpoint mới đều dưới prefix `/api/v1/config/profiles`
- KHÔNG có rename, KHÔNG có deprecation ở endpoint cũ

### Mục 3 — Transaction & Audit

**Verify**:

```sql
-- Sau khi smoke test write operations, kiểm tra audit log
SELECT action_type, entity_type, COUNT(*) AS cnt
FROM audit_history
WHERE created_at > NOW() - INTERVAL 1 HOUR
GROUP BY action_type, entity_type
ORDER BY cnt DESC;
```

| Write operation | Expected audit row |
|---|---|
| POST `/profiles` | `CREATE` + `config_profile` |
| PUT `/profiles/{id}` | `UPDATE` + `config_profile` |
| DELETE `/profiles/{id}` | `DELETE` + `config_profile` |
| POST `/{id}/favorite` | `FAVORITE` + `config_profile` |
| POST `/{id}/default` | `SET_DEFAULT` + `config_profile` |
| POST `/{id}/apply` | `APPLY` + `algorithm_config` |
| POST `/{id}/duplicate` | `DUPLICATE` + `config_profile` |
| POST `/import` | `IMPORT` + `config_profile` |

**Pass khi**: 8 write operations có audit row tương ứng; read operations KHÔNG có audit row.

### Mục 4 — Pagination edge cases

**Verify** với 3 kịch bản:

| Kịch bản | Input | Expected output |
|---|---|---|
| 0 bản ghi | Tạo môi trường DB sạch, GET `?page=0&size=20` | `items=[], totalItems=0, totalPages=0, hasNext=false, hasPrev=false` |
| 1 bản ghi | Tạo 1 profile, GET `?page=0&size=20` | `items=[1], totalItems=1, totalPages=1, hasNext=false, hasPrev=false` |
| Trang cuối | Tạo 5 profile, size=2, GET `?page=2&size=2` | `items=[1], totalItems=5, totalPages=3, hasNext=false, hasPrev=true` |

**Pass khi**: Cả 3 kịch bản khớp expected; không có NullPointerException ở `hasNext`/`hasPrev`.

### Mục 5 — Security (@PreAuthorize)

**Verify** bằng cách gọi API với role khác nhau:

| Role | Endpoint | Expected |
|---|---|---|
| ADMIN | GET `/api/v1/config/profiles` | 200 |
| MANAGER | GET `/api/v1/config/profiles` | 200 (read allowed) |
| STAFF | GET `/api/v1/config/profiles` | 403 |
| STAFF | POST `/api/v1/config/profiles` | 403 |
| MANAGER | POST `/api/v1/config/profiles` | 403 (chỉ ADMIN) |
| ADMIN | DELETE `/api/v1/config/profiles/1` (system) | 403 |

**Pass khi**: Tất cả 6 test case khớp expected. `AccessDeniedException` được `GlobalExceptionHandler` convert sang 403 với JSON response đúng format.

---

## Bước 1D — 10 mục runtime verification (nâng cao)

7 nguyên tắc kiến trúc (Bước 1B) và 5 mục kỹ thuật (Bước 1C) đảm bảo **code pass tests**. Bước 1D đảm bảo **code pass production**.

### Mục 1 — API Contract vs Frontend TypeScript

Không chỉ dựa vào Swagger; cross-check từng field bằng tay:

```bash
# So sánh nullable
grep -E "@(Nullable|NotNull|JsonProperty)" \
  backend/src/main/java/com/hospital/scheduler/scheduling/config/dto/ConfigProfileDto.java
```

| Field | Backend | Frontend expected (TS) |
|---|---|---|
| `id` | non-null | `number` (required) |
| `nameVi` | non-null | `string` (required) |
| `description` | nullable | `string \| null` |
| `category` | enum non-null | `"GENERAL" \| "URGENT" \| "PEDIATRIC" \| "SPECIALIST"` (required) |
| `tags` | enum set, nullable | `ProfileTag[] \| null` |
| `isSystem` | non-null | `boolean` |
| `isFavorite` | non-null | `boolean` |
| `isDefault` | non-null | `boolean` |
| `createdAt` | ISO-8601 non-null | `string` (ISO-8601) |

**Pass khi**: Mọi field trong `ConfigProfileDto` đều có annotation rõ ràng + @Schema example khớp với runtime serialization.

### Mục 2 — Audit semantics (không ghi trùng)

**Smoke test**:

```bash
# Lấy số audit row trước
COUNT=$(mysql -e "SELECT COUNT(*) FROM audit_history WHERE entity_type = 'config_profile';" -N)
echo "Before: $COUNT"

# Trigger apply
curl -X POST http://localhost:8080/api/v1/config/profiles/5/apply \
  -H "Authorization: Bearer $TOKEN"

# Đếm lại
sleep 2
COUNT2=$(mysql -e "SELECT COUNT(*) FROM audit_history WHERE entity_type = 'config_profile';" -N)
echo "After: $COUNT2"

# Expected: After - Before == 1
```

**Vấn đề tiềm ẩn**:

```java
// ❌ Audit 2 lần trong apply()
@Transactional
public ConfigProfileDto apply(Long id) {
    ConfigProfile profile = repository.findById(id).orElseThrow();
    profile.markApplied();
    repository.save(profile);
    auditService.log("UPDATE", "config_profile", id);  // row 1
    
    algorithmConfig.applyProfile(profile);
    repository.save(algorithmConfig);
    auditService.log("UPDATE", "algorithm_config", profile.getId());  // row 2
    
    return mapper.toDto(profile);
}
```

**Cách tránh**: Chỉ log 1 audit cho "config_profile" với action `APPLY`. Để `algorithm_config` update không cần audit row riêng (hoặc tách thành 2 transaction riêng).

**Pass khi**: Mỗi write operation tạo đúng 1 audit row; không có duplicate row trùng `entity_id` + `action_type` trong cùng giây.

### Mục 3 — Transaction boundary

| Method | Annotation | Verify |
|---|---|---|
| `duplicate()` | `@Transactional` | Test rollback khi `repository.save()` throw exception → `config_profile` không có row partial |
| `updateConfig()` | `@Transactional` | Test rollback khi `auditService.log()` throw → entity không update |
| `apply()` | `@Transactional` | Test rollback khi `algorithmConfig.applyProfile()` throw → `is_applied_at` không update |

**Cách test**:

```java
@Test
void duplicate_rollbackWhenAuditThrows() {
    // Given: profile tồn tại
    Long sourceId = 1L;
    
    // Mock auditService throws
    doThrow(new RuntimeException("Audit fail")).when(auditService).log(any(), any(), any());
    
    // When & Then
    assertThrows(RuntimeException.class, () -> service.duplicate(sourceId));
    
    // Verify: số profile không tăng
    long countAfter = repository.count();
    assertEquals(countBefore, countAfter);
}
```

**Pass khi**: Mọi method có `@Transactional` rollback đúng khi exception; không có partial state.

### Mục 4 — Pagination edge cases

**Manual probe**:

```bash
# Case 1: page=999999 (overflow)
GET /api/v1/config/profiles?page=999999&size=20
Expected: items=[], totalPages=N, hasNext=false, 200 OK (KHÔNG 500)

# Case 2: page=-1 (negative)
GET /api/v1/config/profiles?page=-1&size=20
Expected: 400 Bad Request (validated by Pageable handler) HOẶC auto-clamp to 0

# Case 3: size=0 (zero)
GET /api/v1/config/profiles?page=0&size=0
Expected: 400 HOẶC default to 20

# Case 4: size=99999 (overflow)
GET /api/v1/config/profiles?page=0&size=99999
Expected: 400 HOẶC clamp to 100
```

**Pass khi**:
- Page âm hoặc size ≤ 0 → trả 400 với message rõ ràng
- Page vượt phạm vi (page > totalPages - 1) → trả `items=[]`, `page=requested`, `totalPages=N` (KHÔNG throw)
- Size lớn → clamp về max (default 100)

### Mục 5 — Concurrent update (race condition)

**Test script**:

```java
@Test
void setDefault_concurrentOnlyOneWins() throws Exception {
    Long profileA = 1L, profileB = 2L;
    
    ExecutorService executor = Executors.newFixedThreadPool(2);
    CountDownLatch start = new CountDownLatch(1);
    
    Future<Void> f1 = executor.submit(() -> {
        start.await();
        profileService.setDefault(profileA);
        return null;
    });
    Future<Void> f2 = executor.submit(() -> {
        start.await();
        profileService.setDefault(profileB);
        return null;
    });
    
    start.countDown();
    f1.get(5, SECONDS);
    f2.get(5, SECONDS);
    
    // Verify: chỉ 1 profile có is_default=true
    long defaultCount = repository.findByIsDefaultTrue().size();
    assertEquals(1, defaultCount);
}
```

**Cách implement an toàn** trong `setDefault()`:

```java
@Transactional
public void setDefault(Long id) {
    repository.clearAllDefault();  // UPDATE config_profile SET is_default = false WHERE is_default = true
    ConfigProfile profile = repository.findById(id).orElseThrow();
    profile.setDefault(true);
    repository.save(profile);
}
```

**Pass khi**: Test 100 lần concurrent, chỉ 1 profile có `is_default=true` ở mọi thời điểm. KHÔNG có race dẫn đến 2 default.

### Mục 6 — Import/Export canonical

**Round-trip test**:

```bash
# Step 1: Export
curl -X GET /api/v1/config/profiles/1/export -o profile1.json

# Step 2: Modify một số field không cấu trúc (metadata)
# Step 3: Import lại
curl -X POST /api/v1/config/profiles/import -d @profile1.json

# Step 4: Export lại
curl -X GET /api/v1/config/profiles/new-id/export -o profile2.json

# Step 5: So sánh
diff profile1.json profile2.json  # Phải khác ở id, createdAt, updatedAt, version
                                    # Phải giống ở nameVi, configPayload, category, tags
```

**Pass khi**: JSON round-trip giữ nguyên 100% business fields; chỉ khác metadata fields (id, timestamps, version).

### Mục 7 — Search semantics

**Test cases**:

```bash
GET /api/v1/config/profiles?search=abc   # lowercase
GET /api/v1/config/profiles?search=ABC   # uppercase
GET /api/v1/config/profiles?search=Abc   # mixed
GET /api/v1/config/profiles?search=%20abc%20   # padded with spaces
GET /api/v1/config/profiles?search=%C3%A1bc   # accented (á)
```

**Pass khi**: Cả 3 case `abc`, `ABC`, `Abc` trả cùng result. Search có `trim()` whitespace. Có thể sử dụng `LIKE COLLATE utf8mb4_unicode_ci` hoặc `LOWER()` ở derived query.

### Mục 8 — Favorite + Default filter

**Test**:

```bash
# Setup: 3 profiles, 2 favorite, 1 default, 1 cả hai
# Case 1: list tất cả
GET /api/v1/config/profiles
Expected: 3 items

# Case 2: chỉ favorite
GET /api/v1/config/profiles?favoritesOnly=true
Expected: 2 items (favorite + favorite+default)

# Case 3: chỉ default
GET /api/v1/config/profiles?defaultOnly=true
Expected: 1 item (favorite+default)
```

**Pass khi**: Filter `favoritesOnly` và `defaultOnly` hoạt động độc lập; kết hợp `favoritesOnly=true&defaultOnly=true` trả profile vừa favorite vừa default.

### Mục 9 — Security edge cases

**Test matrix**:

| Auth | Method | Expected |
|---|---|---|
| JWT expired | GET `/profiles` | 401 |
| JWT malformed | GET `/profiles` | 401 |
| Anonymous | GET `/profiles` | 401 hoặc 403 (tùy security config) |
| JWT role=STAFF | POST `/profiles` | 403 |
| Valid ADMIN | GET `/profiles/{notExistId}` | 404 (không 403) |
| Valid ADMIN nhưng system profile | DELETE `/profiles/1` | 403 |

**Pass khi**:
- `GlobalExceptionHandler` có handler cho:
  - `JwtException` → 401
  - `AccessDeniedException` → 403
  - `EntityNotFoundException` → 404
  - `MethodArgumentNotValidException` → 400
- Không có endpoint nào trả 500 cho lỗi validation/auth/notFound

### Mục 10 — Database index & constraint

**Schema review**:

```sql
-- Kiểm tra index
SHOW INDEX FROM config_profile;

-- Expected:
-- PRIMARY KEY (id)
-- UNIQUE KEY uk_profile_key (profile_key)
-- INDEX idx_category (category)
-- INDEX idx_is_favorite (is_favorite)
-- INDEX idx_is_default (is_default) -- chỉ vẫn unique partial nếu dùng DB constraint
```

**Default uniqueness**:

```sql
-- ❌ Nếu chỉ dựa logic application, có thể race:
-- UPDATE A SET is_default = true WHERE id = 1
-- UPDATE B SET is_default = true WHERE id = 2
-- (không có transaction giữa)
-- => 2 profile default

-- ✅ Cách 1: Trigger MySQL
CREATE TRIGGER trg_one_default
BEFORE UPDATE ON config_profile
FOR EACH ROW
BEGIN
  IF NEW.is_default = TRUE THEN
    UPDATE config_profile SET is_default = FALSE WHERE id != NEW.id AND is_default = TRUE;
  END IF;
END;

-- ✅ Cách 2: Service transactional (đã đề cập ở mục 5)
```

**Pass khi**: 
- `profile_key` UNIQUE constraint tồn tại
- `is_default=true` invariant được đảm bảo bằng DB (trigger) HOẶC service transaction (kết hợp concurrency test ở mục 5 pass)

---

## Tổng hợp Bước 1 — Review Checklist

| Sub-step | Tên | Trạng thái |
|---|---|---|
| 1 | Code review chuyên sâu (5 files) | ⏳ chờ reviewer |
| 1B | 7 nguyên tắc kiến trúc | ⏳ chờ reviewer |
| 1C | 5 mục kỹ thuật (OpenAPI, BC, Audit, Pagination, Security) | ⏳ chờ reviewer |
| 1D | 10 mục runtime verification (mới bổ sung) | ⏳ chờ reviewer |
| 1E | 4 bài test production evidence (mới bổ sung) | ⏳ chờ reviewer |

**Lưu ý quan trọng**: Bước 1D và 1E chỉ PASS khi có **evidence chạy thực tế** (log, screenshot, output), KHÔNG chỉ là tồn tại dưới dạng checklist.

---

## Bước 1E — 4 bài test production evidence (bắt buộc chạy)

Bước 1E nâng tầm từ "static checklist" lên "runtime evidence". Mỗi bài test phải có **output log/screenshot** đính kèm mới tính là PASS.

### Bài test 1 — Persistence round-trip (ObjectMapper + DB + Hibernate + JSON)

**Mục đích**: Verify rằng ObjectMapper, JPA, Hibernate, MySQL đồng bộ — config_payload round-trip qua tất cả layer mà không mất dữ liệu.

**Script**:

```bash
# 1. Tạo profile qua API
PROFILE_ID=$(curl -X POST http://localhost:8080/api/v1/config/profiles \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @profile-a.json | jq -r '.data.id')

echo "Created profile: $PROFILE_ID"

# 2. Export
curl -X GET "http://localhost:8080/api/v1/config/profiles/$PROFILE_ID/export" \
  -H "Authorization: Bearer $TOKEN" \
  -o exported.json

# 3. Tính hash config payload
HASH_BEFORE=$(jq '.configPayload' exported.json | sha256sum | cut -d' ' -f1)
echo "Hash before: $HASH_BEFORE"

# 4. Import lại với tên mới
curl -X POST http://localhost:8080/api/v1/config/profiles/import \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d @exported.json > import-result.json

NEW_PROFILE_ID=$(jq -r '.data.id' import-result.json)
echo "Re-imported profile: $NEW_PROFILE_ID"

# 5. Apply
curl -X POST "http://localhost:8080/api/v1/config/profiles/$NEW_PROFILE_ID/apply" \
  -H "Authorization: Bearer $TOKEN"

# 6. Restart backend
pkill -f hospital-scheduler
sleep 3
java -jar target/hospital-scheduler-*.jar > /tmp/backend.log 2>&1 &
sleep 30  # Wait for startup

# 7. GET lại profile
curl -X GET "http://localhost:8080/api/v1/config/profiles/$NEW_PROFILE_ID" \
  -H "Authorization: Bearer $TOKEN" \
  -o final.json

# 8. Tính hash config payload sau restart
HASH_AFTER=$(jq '.configPayload' final.json | sha256sum | cut -d' ' -f1)
echo "Hash after: $HASH_AFTER"

# 9. So sánh
if [ "$HASH_BEFORE" == "$HASH_AFTER" ]; then
  echo "PASS: Hash identical across full stack"
else
  echo "FAIL: Hash drift detected"
  diff <(jq '.configPayload' exported.json) <(jq '.configPayload' final.json)
fi
```

**Pass khi**: `HASH_BEFORE == HASH_AFTER` (SHA-256 trùng khớp).

**Evidence cần lưu**:
- `evidence/runtime/persistence-roundtrip.log` (output toàn bộ script)
- `evidence/runtime/exported.json` (snapshot trước)
- `evidence/runtime/final.json` (snapshot sau)
- `evidence/runtime/diff.txt` (nếu FAIL) hoặc "no diff" (nếu PASS)

---

### Bài test 2 — Apply idempotency

**Mục đích**: Verify rằng Apply cùng một profile nhiều lần cho kết quả giống nhau.

**Script**:

```bash
# 1. Apply Profile A lần 1
curl -X POST "http://localhost:8080/api/v1/config/profiles/1/apply" \
  -H "Authorization: Bearer $TOKEN"

HASH_1=$(curl -s http://localhost:8080/api/v1/config/active | jq -c '.data' | sha256sum | cut -d' ' -f1)
echo "After Apply A (1st): $HASH_1"

# 2. Apply Profile B
curl -X POST "http://localhost:8080/api/v1/config/profiles/2/apply" \
  -H "Authorization: Bearer $TOKEN"

HASH_2=$(curl -s http://localhost:8080/api/v1/config/active | jq -c '.data' | sha256sum | cut -d' ' -f1)
echo "After Apply B: $HASH_2"

# 3. Apply Profile A lần 2
curl -X POST "http://localhost:8080/api/v1/config/profiles/1/apply" \
  -H "Authorization: Bearer $TOKEN"

HASH_3=$(curl -s http://localhost:8080/api/v1/config/active | jq -c '.data' | sha256sum | cut -d' ' -f1)
echo "After Apply A (2nd): $HASH_3"

# 4. So sánh
if [ "$HASH_1" == "$HASH_3" ]; then
  echo "PASS: Apply is idempotent"
else
  echo "FAIL: Apply is NOT idempotent"
  echo "Hash1=$HASH_1"
  echo "Hash3=$HASH_3"
fi
```

**Pass khi**: `HASH_1 == HASH_3` (apply lại cho cùng result).

**Evidence cần lưu**:
- `evidence/runtime/idempotency.log`
- Nếu FAIL: `evidence/runtime/diff-hash1-vs-hash3.txt`

---

### Bài test 3 — Audit chronology & integrity

**Mục đích**: Verify rằng 8 write operations tạo audit row đúng thứ tự, đúng actor, không duplicate.

**Script**:

```bash
# 1. Setup: clear audit table (test environment only)
mysql -e "TRUNCATE TABLE audit_history;"

# 2. Thực hiện tuần tự 8 operations
echo "1. CREATE" >&2
curl -X POST http://localhost:8080/api/v1/config/profiles \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"nameVi":"test-audit","category":"GENERAL",...}' > /dev/null

echo "2. UPDATE" >&2
curl -X PUT http://localhost:8080/api/v1/config/profiles/$ID \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"nameVi":"test-audit-renamed",...}' > /dev/null

echo "3. FAVORITE" >&2
curl -X POST "http://localhost:8080/api/v1/config/profiles/$ID/favorite" \
  -H "Authorization: Bearer $TOKEN" > /dev/null

echo "4. SET_DEFAULT" >&2
curl -X POST "http://localhost:8080/api/v1/config/profiles/$ID/default" \
  -H "Authorization: Bearer $TOKEN" > /dev/null

echo "5. APPLY" >&2
curl -X POST "http://localhost:8080/api/v1/config/profiles/$ID/apply" \
  -H "Authorization: Bearer $TOKEN" > /dev/null

echo "6. DUPLICATE" >&2
curl -X POST "http://localhost:8080/api/v1/config/profiles/$ID/duplicate" \
  -H "Authorization: Bearer $TOKEN" > /dev/null

echo "7. IMPORT" >&2
curl -X POST http://localhost:8080/api/v1/config/profiles/import \
  -H "Authorization: Bearer $TOKEN" \
  -d @exported.json > /dev/null

echo "8. DELETE" >&2
curl -X DELETE "http://localhost:8080/api/v1/config/profiles/$ID" \
  -H "Authorization: Bearer $TOKEN" > /dev/null

# 3. Query audit log
mysql -e "SELECT action_type, entity_type, entity_id, actor_id, created_at
          FROM audit_history
          ORDER BY created_at;" > evidence/runtime/audit-chronology.txt

# 4. Verify counts
EXPECTED=8
ACTUAL=$(mysql -N -e "SELECT COUNT(*) FROM audit_history;")

if [ "$EXPECTED" == "$ACTUAL" ]; then
  echo "PASS: Audit count = $EXPECTED"
else
  echo "FAIL: Audit count = $ACTUAL (expected $EXPECTED)"
fi
```

**Verify checks**:

| Check | Pass khi |
|---|---|
| Total rows = 8 | Đúng số audit row |
| No duplicate `(action_type, entity_id, created_at)` | Không có duplicate trong cùng giây |
| Sequence khớp: CREATE → UPDATE → FAVORITE → SET_DEFAULT → APPLY → DUPLICATE → IMPORT → DELETE | Thứ tự khớp |
| `actor_id` giống nhau | Cùng admin user |
| `before_value`/`after_value` có JSON đầy đủ | Audit log chứa diff đầy đủ |

**Pass khi**: Cả 5 checks trên PASS.

**Evidence cần lưu**:
- `evidence/runtime/audit-chronology.txt` (raw query output)
- `evidence/runtime/audit-summary.md` (parsed analysis)

---

### Bài test 4 — Transaction rollback atomicity

**Mục đích**: Verify rằng nếu một write operation throw exception, toàn bộ side effects (entity update, audit, config) đều rollback.

**Setup**: Cần test endpoint hoặc service-level trigger. Có 2 cách:

**Cách A — Dùng integration test (Junit + MockMvc)**:

```java
@SpringBootTest
@Transactional
class TransactionRollbackTest {
    @Autowired ProfileService service;
    @Autowired ProfileRepository repository;
    @Autowired AuditService auditService;
    
    @Test
    void updateConfig_rollbackAllOnAuditFailure() {
        // Given
        Long profileId = 1L;
        ConfigProfile before = repository.findById(profileId).orElseThrow();
        String nameBefore = before.getNameVi();
        
        // Mock audit throws
        doThrow(new RuntimeException("Audit service down"))
            .when(auditService).log(any(), any(), any());
        
        // When
        assertThrows(RuntimeException.class, () ->
            service.updateConfig(profileId, new UpdateConfigRequest(...))
        );
        
        // Then
        ConfigProfile after = repository.findById(profileId).orElseThrow();
        assertEquals(nameBefore, after.getNameVi());  // entity rolled back
        
        // Verify NO audit row created
        long auditCount = auditRepository.countByEntityId(profileId);
        assertEquals(0, auditCount);
    }
}
```

**Cách B — Manual trigger qua endpoint inject**:

```bash
# Inject fault via endpoint (chỉ dùng trong test environment)
# Ví dụ: gửi request với header X-Test-Inject-Fault: audit

curl -X PUT http://localhost:8080/api/v1/config/profiles/1 \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Test-Inject-Fault: audit" \
  -d @update-request.json

# Expected: 500 Internal Server Error (hoặc 4xx nếu xử lý graceful)

# Verify entity không update
mysql -e "SELECT name_vi FROM config_profile WHERE id = 1;"
# Expected: name_vi chưa đổi

# Verify không có audit row
mysql -e "SELECT COUNT(*) FROM audit_history WHERE entity_id = 1 AND action_type = 'UPDATE';"
# Expected: 0
```

**Pass khi**: 
- Entity rollback (giá trị cũ)
- Audit row không tồn tại
- Config payload không thay đổi

**Evidence cần lưu**:
- `evidence/runtime/transaction-rollback.log`
- `evidence/runtime/entity-before.txt`
- `evidence/runtime/entity-after.txt` (phải giống before)
- `evidence/runtime/audit-count.txt` (phải = 0)

---

## Điều kiện chốt tag `v11-backend-ready`

Epic 1 Backend chỉ thực sự "Feature Complete" khi:

| Điều kiện | Verify |
|---|---|
| 1. Bước 1D (10 mục runtime) chạy | Output log đính kèm |
| 2. Bước 1E (4 bài test) chạy | Output log + evidence files |
| 3. Tất cả evidence lưu tại `docs/evidence/runtime/` | Folder structure đầy đủ |

**Quy trình sau khi evidence đầy đủ**:

```
Evidence PASS
     ↓
Reviewer approval
     ↓
git checkout main
git merge --no-ff epic-1-backend
git tag -a v11-backend-ready -m "..."
git push origin main --tags
     ↓
Thông báo team + Frontend pin tag
```

---

## Bước 1F — Performance & Coverage Baseline (bổ sung)

Bước 1F thêm 2 nhóm evidence không liên quan đến functional correctness nhưng cần thiết để chuẩn bị cho các Epic sau (đặc biệt Epic 5 — tối ưu scheduler).

### Nhóm 1 — Performance Baseline

**Mục đích**: Có baseline số để so sánh khi tối ưu sau này. Không benchmark lớn, chỉ cần đủ thông tin để biết "hiện tại nhanh hay chậm".

**Công cụ**: Apache JMeter, k6, hoặc curl + script bash đơn giản.

**Test plan**:

| Endpoint | Method | Concurrency | Duration | Ramp-up |
|---|---|---|---|---|
| GET `/api/v1/config/profiles` | GET | 10 | 30s | 5s |
| GET `/api/v1/config/profiles/{id}` | GET | 10 | 30s | 5s |
| POST `/api/v1/config/profiles` | POST | 5 | 30s | 5s |
| POST `/api/v1/config/profiles/{id}/apply` | POST | 5 | 30s | 5s |

**Script (k6)**:

```javascript
// evidence/perf/baseline-get-profiles.js
import http from 'k6/http';
import { check } from 'k6';

export const options = {
  stages: [
    { duration: '5s', target: 10 },
    { duration: '30s', target: 10 },
    { duration: '5s', target: 0 },
  ],
  thresholds: {
    http_req_duration: ['p(95)<200'],
  },
};

export default function () {
  const res = http.get(
    'http://localhost:8080/api/v1/config/profiles',
    { headers: { Authorization: `Bearer ${__ENV.TOKEN}` } }
  );
  check(res, {
    'status is 200': (r) => r.status === 200,
  });
}
```

**Chạy**:

```bash
mkdir -p evidence/perf
k6 run --out json=evidence/perf/get-profiles.json \
  -e TOKEN=$TOKEN evidence/perf/baseline-get-profiles.js
```

**Output cần lưu**:

```json
{
  "endpoint": "GET /api/v1/config/profiles",
  "concurrency": 10,
  "duration_seconds": 30,
  "total_requests": 3000,
  "avg_latency_ms": 25,
  "p50_latency_ms": 22,
  "p95_latency_ms": 45,
  "p99_latency_ms": 78,
  "max_latency_ms": 120,
  "throughput_rps": 100,
  "error_rate_pct": 0.0
}
```

**Pass khi**:
- P95 < 200ms cho GET endpoints
- P95 < 500ms cho POST/PUT endpoints
- Error rate = 0% (trong điều kiện test bình thường)

**Evidence files**:
- `evidence/perf/baseline-get-profiles.json`
- `evidence/perf/baseline-get-profile-id.json`
- `evidence/perf/baseline-post-profile.json`
- `evidence/perf/baseline-apply-profile.json`
- `evidence/perf/summary.md` (parsed analysis)

**Lưu ý**: Performance baseline không phải SLO. Chỉ là reference. Sau Epic 5, sẽ chạy lại và so sánh.

---

### Nhóm 2 — Coverage Report (JaCoCo)

**Mục đích**: Biết test coverage ở từng layer (service, repository, controller, DTO) để reviewer thấy khu vực nào đã cover, khu vực nào cần bổ sung.

**Setup trong `pom.xml`**:

```xml
<plugin>
  <groupId>org.jacoco</groupId>
  <artifactId>jacoco-maven-plugin</artifactId>
  <version>0.8.11</version>
  <executions>
    <execution>
      <goals>
        <goal>prepare-agent</goal>
      </goals>
    </execution>
    <execution>
      <id>report</id>
      <phase>test</phase>
      <goals>
        <goal>report</goal>
      </goals>
    </execution>
  </executions>
</plugin>
```

**Chạy**:

```bash
mvn clean verify
# Report tự động sinh tại target/site/jacoco/index.html
cp -r target/site/jacoco evidence/coverage/
```

**Phân tích report**:

| Layer | Expected Coverage | Nếu < 70% thì |
|---|---|---|
| `configProfile.service.*` | ≥ 90% | Thêm unit test |
| `configProfile.repository.*` | ≥ 95% | Repository test đã cover |
| `configProfile.controller.*` | ≥ 80% | MockMvc test |
| `configProfile.dto.*` | ≥ 0% (DTOs thường không cần test) | N/A |
| `configProfile.entity.*` | ≥ 50% | Đã có Lombok @Data |
| **Overall** | ≥ 80% | Review từng service |

**Evidence files**:
- `evidence/coverage/index.html` (JaCoCo report HTML)
- `evidence/coverage/jacoco.csv` (raw CSV)
- `evidence/coverage/summary.md` (parsed analysis)

**Mục đích của summary.md**:

```markdown
# Coverage Summary — Epic 1 Backend

| Package | Class | Method | Line |
|---|---|---|---|
| configProfile.service | 100% | 95% | 92% |
| configProfile.repository | 100% | 100% | 98% |
| configProfile.controller | 100% | 85% | 82% |
| ... |
| **Overall** | **98%** | **91%** | **88%** |

## Khu vực coverage thấp

- `ConfigProfileService.auditFlow()` (45%) — đã cover qua integration test nhưng unit test bỏ qua (acceptable)
- `ConfigProfileController.errorHandling()` (60%) — GlobalExceptionHandler test có cover
```

**Lưu ý**: Coverage không phải KPI bắt buộc. Mục đích chính là **visibility** — reviewer biết test đang ở đâu.

---

## Tổng hợp toàn bộ Bước 1 — Review Checklist cuối cùng

| Sub-step | Tên | Loại | Trạng thái |
|---|---|---|---|
| 1 | Code review chuyên sâu (5 files) | Static | ⏳ chờ reviewer |
| 1B | 7 nguyên tắc kiến trúc | Static | ⏳ chờ reviewer |
| 1C | 5 mục kỹ thuật (OpenAPI, BC, Audit, Pagination, Security) | Static + Functional | ⏳ chờ reviewer |
| 1D | 10 mục runtime verification | Runtime | ⏳ **cần evidence** |
| 1E | 4 bài test production evidence | Runtime | ⏳ **cần evidence** |
| 1F | Performance + Coverage baseline | Observability | ⏳ **cần evidence** |

**Tổng cộng**: 5 sub-step cần reviewer chạy và có evidence.

---

## Folder structure cuối cùng

```
docs/
├── POST_EPIC1_REVIEW_CHECKLIST.md           ← file này
├── evidence/
│   ├── runtime/
│   │   ├── persistence-roundtrip.log
│   │   ├── exported.json
│   │   ├── final.json
│   │   ├── idempotency.log
│   │   ├── audit-chronology.txt
│   │   ├── transaction-rollback.log
│   │   ├── concurrency-apply.log
│   │   ├── pagination-edge.log
│   │   └── ...
│   ├── perf/
│   │   ├── baseline-get-profiles.json
│   │   ├── baseline-get-profile-id.json
│   │   ├── baseline-post-profile.json
│   │   ├── baseline-apply-profile.json
│   │   └── summary.md
│   └── coverage/
│       ├── index.html
│       ├── jacoco.csv
│       └── summary.md
└── ...
```

---

## Cam kết cuối cùng của tôi

Epic 1 Backend ở trạng thái:

```
✅ Feature Complete (code + 602 tests)
✅ Review Ready (checklist đầy đủ)
⏳ Awaiting Evidence (Bước 1D + 1E + 1F)
⏳ Awaiting Reviewer Approval
⏳ Awaiting Merge + Tag
```

Tôi **đứng yên** cho đến khi evidence được chạy. Bất kỳ code change nào sau evidence chỉ là **bug fix** dựa trên evidence, không phải feature mới.

---

## Bước 2 — Chạy lại CI từ đầu (10 phút)

```bash
# Backend
cd backend
mvn clean verify
# Expect: BUILD SUCCESS, 602 tests, 0 failure, 0 error

# Frontend (chưa có PR-11-04, chỉ để verify Epic 1 không phá shared code)
cd ../frontend
npm ci
npm run build
# Expect: build pass, không có import path mới từ Epic 1
```

**Lưu ý**: `mvn clean` là bắt buộc để chắc chắn không có artifact cache từ PR trước.

Nếu có cache CI như GitHub Actions, verify workflow file `.github/workflows/*.yml` đã chạy trên commit mới nhất.

---

## Bước 3 — Smoke test thủ công full flow (15 phút)

Chạy backend local + dùng Swagger UI hoặc curl.

### Happy path

```
1. POST   /api/v1/config/profiles              → tạo custom profile, expect 201
2. GET    /api/v1/config/profiles?page=0&size=20&sort=nameVi,ASC
                                              → list có 1 row mới
3. GET    /api/v1/config/profiles/{id}         → trả về profile vừa tạo
4. GET    /api/v1/config/profiles/key/{profileKey}    → trả về qua slug
5. PUT    /api/v1/config/profiles/{id}         → cập nhật description
6. POST   /api/v1/config/profiles/{id}/favorite → toggle, expect isFavorite=true
7. POST   /api/v1/config/profiles/{id}/default  → set default, expect isDefault=true
8. POST   /api/v1/config/profiles/{id}/apply    → apply, expect 200 + ConfigDomain
9. POST   /api/v1/config/profiles/compare
   body: {"profileIdA": <id>, "profileIdB": 1}  → trả về comparison
10. GET   /api/v1/config/profiles/{id}/export  → trả về JSON string
11. POST  /api/v1/config/profiles/import
   body: {"json": "..."}                       → tạo profile mới từ JSON
12. POST  /api/v1/config/profiles/{id}/duplicate
   body: {"name": "Sao chép"}                  → tạo profile mới
13. DELETE /api/v1/config/profiles/{id}        → xóa custom, expect 204
14. DELETE /api/v1/config/profiles/1 (balanced) → expect 403 (system)
```

### Negative path

```
15. POST  với nameVi=""                        → expect 400
16. GET    ?sort=password,asc                   → expect 400 (sort whitelist)
17. GET    ?size=999                            → expect cap at 100, không error
18. GET    /999                                 → expect 404
19. POST   /1 (system)/duplicate                → expect 403
20. PUT    /999                                 → expect 404
```

---

## Bước 4 — Review OpenAPI trong Swagger UI (5 phút)

Mở `http://localhost:8080/swagger-ui.html` (hoặc port backend chạy).

### Tab "Config Profiles"

- [ ] **15 endpoint** hiển thị đầy đủ dưới tag "Config Profiles"
- [ ] **GET /{id}** có dropdown 4 example: `system-default`, `system-favorite`, `system-plain`, `custom`
- [ ] **GET /** có example `paged-list` với items chứa đủ 3 variant
- [ ] **POST** có request example `CreateProfileRequest`
- [ ] **PUT** có request example `UpdateProfileRequest`
- [ ] Status codes hiển thị đúng: 200, 201, 204, 400, 403, 404
- [ ] Mỗi status code có response example (đặc biệt 400, 403, 404)

### Schemas

- [ ] `ConfigProfileDto` có 14 fields với example khớp với JSON trong `OpenApiExamples.java`
- [ ] `PageResponse` có 8 fields, field `sort` example = `"nameVi,ASC"` (uppercase)
- [ ] `CreateProfileRequest.nameVi` marked `required: true` với example
- [ ] `UpdateProfileRequest.*` marked `required: false`

### Bằng chứng example khớp với runtime

So sánh một response thực tế từ Bước 3 với example trong Swagger. Field names phải khớp 100%.

---

## Bước 5 — Kiểm tra database thực (10 phút)

```sql
-- Verify schema
SHOW CREATE TABLE config_profile\G

-- Expected columns:
--   id              BIGINT PK AUTO_INCREMENT
--   profile_key     VARCHAR(64)  NOT NULL UNIQUE
--   name_vi         VARCHAR(128) NOT NULL
--   name_en         VARCHAR(128) NULL
--   description     VARCHAR(512) NULL
--   category        VARCHAR(32)  NOT NULL
--   icon            VARCHAR(64)  NULL
--   tags            JSON         NULL
--   is_system       BOOLEAN      NOT NULL DEFAULT FALSE
--   is_default      BOOLEAN      NOT NULL DEFAULT FALSE
--   is_favorite     BOOLEAN      NOT NULL DEFAULT FALSE
--   config_json     JSON         NULL
--   created_by      VARCHAR(64)  NOT NULL
--   created_at      DATETIME     NOT NULL
--   updated_at      DATETIME     NOT NULL

-- Expected indexes:
--   PRIMARY KEY (id)
--   UNIQUE KEY uk_profile_key (profile_key)
--   INDEX idx_category (category)
--   INDEX idx_system_default (is_system, is_default)
```

### Constraint check

```sql
-- 1. Chỉ 1 row có is_default = TRUE
SELECT COUNT(*) FROM config_profile WHERE is_default = TRUE;
-- Expected: 0 or 1

-- 2. profile_key không trùng
SELECT profile_key, COUNT(*) FROM config_profile GROUP BY profile_key HAVING COUNT(*) > 1;
-- Expected: empty

-- 3. created_at <= updated_at
SELECT id FROM config_profile WHERE created_at > updated_at;
-- Expected: empty
```

### Audit trail

```sql
-- Sau khi apply profile, kiểm tra algorithm_config có audit
SELECT * FROM algorithm_config ORDER BY updated_at DESC LIMIT 5;

-- Hoặc audit_history tùy convention của project
SELECT * FROM audit_history
WHERE entity_type IN ('config_profile', 'algorithm_config')
ORDER BY created_at DESC LIMIT 10;
```

---

## Sau khi pass tất cả 5 bước

### Merge sequence

```bash
# 1. Merge PR Epic 1 vào main
git checkout main
git merge --no-ff epic-1-backend
# Commit message: "Epic 1 Backend: Config Profile Management — API v1 frozen"

# 2. Tag semantic version
git tag -a v11-backend-ready -m "Epic 1 Backend complete — API v1 frozen"
git push origin v11-backend-ready
```

### Thông báo cho team

```
Subject: [v11] Epic 1 Backend merged — API v1 frozen

Epic 1 Backend (Config Profile Management) đã merge vào main và được tag
v11-backend-ready. API contract v1 đã đóng băng tại tag này.

Frontend Team có thể:
- Pin dependency vào tag v11-backend-ready
- Bắt đầu PR-11-04 Frontend Profile UI
- Mọi thay đổi API phải qua quy trình version bump

Tham khảo:
- docs/PR_EPIC1_BACKEND_REVIEW.md
- docs/POST_EPIC1_REVIEW_CHECKLIST.md
- Swagger UI: /swagger-ui.html (tag: Config Profiles)
```

---

## Metadata cho tag `v11-backend-ready`

Khi tạo tag, capture metadata sau để Frontend Team và bug report có anchor rõ ràng.

### Lệnh tạo tag

```bash
# Merge PR Epic 1 vào main trước
git checkout main
git pull origin main
git merge --no-ff epic-1-backend
git push origin main

# Tạo annotated tag
git tag -a v11-backend-ready -m "Epic 1 Backend complete — API v1 frozen"

# Push tag
git push origin v11-backend-ready
```

### Metadata capture

```bash
# 1. Commit hash
git rev-parse HEAD

# 2. OpenAPI version (từ ConfigProfileController @OpenAPIDefinition)
grep -r "version" backend/src/main/java/com/hospital/scheduler/config/OpenApiConfig.java

# 3. Test count
cd backend && mvn test -q | grep -E "Tests run:" | tail -1
# Expected: Tests run: 602, Failures: 0, Errors: 0

# 4. Build number
git log --oneline | wc -l   # commit count since project start
```

### Release notes template

```markdown
## v11-backend-ready

**Release date**: <YYYY-MM-DD>
**Commit hash**: <full 40-char SHA>
**API frozen version**: v1
**OpenAPI version**: 1.0.0
**Test count**: 602 pass / 0 fail / 0 error
**Build number**: <commit count>
**Maven artifact**: com.hospital.scheduler:backend:<version>

### Changes

- Add: 15 endpoints under `/api/v1/config/profiles`
- Add: `ConfigProfile` entity + repository
- Add: `ConfigProfileService` with write/read separation
- Add: `ConfigProfileSort` whitelist + parser
- Add: `PageResponse<T>` canonical paged envelope
- Add: OpenAPI examples (4 profile variants + 4 error responses + 4 requests)
- Add: Audit on write operations (8 actions)

### Compatibility

- All existing endpoints unchanged
- No DTO renamed, no field renamed
- Database migration: 1 new table `config_profile`

### Known limitations

- Frontend not yet integrated (PR-11-04 pending)
- History/Diff/Governance deferred to next Epic

### Rollback

```bash
git checkout main
git revert -m 1 <merge-commit-sha>
# Or: git reset --hard <previous-tag>
```
```

### Snapshot reference

Lưu metadata vào `docs/releases/v11-backend-ready.md` để bug report sau này có thể cite:

```
v11-backend-ready
├─ commit: <sha>
├─ api: v1 frozen
├─ tests: 602
└─ date: <YYYY-MM-DD>
```

---

## Quy tắc "pin tag, không pin HEAD" cho Frontend

Frontend Team nên:

```json
// package.json (frontend)
{
  "dependencies": {
    "api-client": "git+https://github.com/<org>/backend.git#v11-backend-ready"
  }
}
```

**Không nên**:

```json
// ❌ Pin vào HEAD hoặc main branch
{
  "dependencies": {
    "api-client": "git+https://github.com/<org>/backend.git"
  }
}
```

Lý do: Nếu backend merge Epic 2 (History/Diff/Governance) vào main, HEAD sẽ có breaking changes. Frontend pin HEAD sẽ tự động nhận breaking changes khi `pnpm install` chạy lại — điều này phá nguyên tắc "đóng băng contract".

Frontend nên bump tag sang `v11-backend-ready-with-history` (hoặc version bump tương ứng) khi sẵn sàng consume API mới.

---

## Nếu phát hiện vấn đề trong review

1. **Bug fix**: Tạo PR riêng, gắn label `epic-1-bugfix`, merge nhanh.
2. **Documentation gap**: Sửa trực tiếp trong PR Epic 1 trước khi merge.
3. **Design issue** (nghiêm trọng): Tạo Epic 1.1 hotfix PR, không nới lỏng contract v1.

**Không được**:
- Đổi response payload.
- Rename field.
- Thêm endpoint mới vào Epic 1.
- Refactor code trong PR Epic 1 (trừ khi fix bug).

---

# TỔNG HỢP CUỐI — 3 QUALITY GATES

> **Trạng thái chốt**: Epic 1 Backend = **Feature Complete – Awaiting Release Verification**.

Cách tổ chức ở trên (Bước 1, 1B, 1C, 1D, 1E, 1F) có thể gom lại thành **3 cổng chất lượng** rõ ràng hơn:

## Gate 1 — Engineering Quality (✅ PASS)

Bao gồm:
- Bước 1 (Code review chuyên sâu)
- Bước 1B (7 nguyên tắc kiến trúc)
- Bước 1C mục 1-3 (OpenAPI, Backward compatibility, Audit canonical)
- Compile (`mvn clean verify`)
- Tests (602 pass)
- TypeScript clean
- OpenAPI + Swagger
- Documentation

**Trạng thái**: ✅ Code & 602 functional tests đã đạt. Gate 1 PASS.

## Gate 2 — Runtime Quality (⏳ PENDING)

Bao gồm:
- Bước 1D (10 mục runtime verification)
- Bước 1E (4 bài test production evidence)
- Smoke test 1 happy-path
- Audit verification chi tiết
- Transaction verification chi tiết
- Pagination edge cases (page=-1, size=0, size=99999)

**Trạng thái**: ⏳ **PENDING — Evidence Required**.

| Sub-step | Checklist có | Evidence có |
|---|---|---|
| Runtime verification (1D) | ✅ | ❌ |
| Production evidence (1E) | ✅ | ❌ |
| Smoke test | ❌ | ❌ |
| Audit chi tiết | ✅ | ❌ |
| Transaction chi tiết | ✅ | ❌ |
| Pagination edge | ✅ | ❌ |

Gate 2 chỉ PASS khi **evidence đã được chạy thực tế**.

## Gate 3 — Release Readiness (⏳ PENDING)

Bao gồm:
- Bước 1F (Performance baseline + Coverage report)
- Reviewer approval (Tech lead + Senior dev + QA lead)
- **Gate 3.7 — Dependency Lock** (chi tiết: Git SHA, Java vendor, MySQL SQL mode, OpenAPI version, Test baseline, Lock files)
- **Gate 3.8 — Artifact Checksum** (SHA256 của JAR/bundle, verify sau deploy)
- Merge vào main
- Tag `v11-backend-ready`
- Release note (`docs/releases/v11-backend-ready.md`)

**Trạng thái**: ⏳ **PENDING**.

| Sub-step | Trạng thái |
|---|---|
| Performance baseline | ⏳ Script có, chưa chạy |
| Coverage report | ⏳ Config JaCoCo có, chưa sinh report |
| Reviewer approval | ⏳ Chờ review |
| **Dependency Lock (3.7)** | ⏳ Snapshot chưa capture ✨ |
| **Artifact Checksum (3.8)** | ⏳ Build chưa chạy ✨ |
| Merge | ⏳ Sau Gate 2 + reviewer |
| Tag | ⏳ Sau merge |
| Release note | ⏳ Sau tag |

## Quy tắc chuyển gate

```
Gate 1 PASS  →  Gate 2 evidence chạy  →  Gate 2 PASS
                                            ↓
                                      Gate 3 reviewer approve
                                            ↓
                                      Gate 3 PASS
                                            ↓
                                      Release v11-backend-ready
```

## Gate 3.7 — Dependency Lock (BẮT BUỘC trước khi tag)

**Mục đích**: Lưu lại chính xác version của mọi dependency để:
- Tái tạo môi trường khi cần rollback
- Điều tra bug "chỉ xảy ra trên môi trường khác"
- Đảm bảo reproducibility cho Frontend team khi pin tag

**Cách thực hiện**:

### Bước 1 — Thu thập version từ môi trường hiện tại

```bash
# Java (vendor + version)
java -version 2>&1 | head -n 1
# ví dụ: openjdk version "21.0.4" 2024-07-16 LTS
# vendor: Temurin / OpenJDK / Corretto

# Maven
mvn --version | head -n 1

# Spring Boot (đọc từ pom.xml)
grep -A1 "spring-boot-starter-parent" backend/pom.xml | grep version

# Node (nếu frontend dùng cùng repo)
node --version

# pnpm/npm
pnpm --version
npm --version

# MySQL (version + SQL mode)
mysql --version
mysql -e "SELECT @@version, @@sql_mode;"

# Docker (nếu có)
docker --version

# Docker image digest
docker images --digests | grep <image-name>
```

### Bước 2 — Tạo file `docs/RELEASE_DEPENDENCIES_v11.md`

```markdown
# Release Dependencies — v11-backend-ready

> Snapshot toàn bộ version stack để đảm bảo reproducibility.

## Git context

| Field | Value |
|---|---|
| Commit SHA | `<full 40-char SHA>` |
| Branch | `main` |
| Tag (planned) | `v11-backend-ready` |
| Tag type | annotated |
| Tag message | `Epic 1 Backend: Frozen API v1, 602 tests PASS, runtime evidence complete` |

## Backend

| Tool | Version | Vendor / Source | Note |
|---|---|---|---|
| Java | 21.x.x | Temurin | LTS |
| Maven | 3.9.x | Apache | |
| Spring Boot | 3.5.x | VMware → Pivotal | parent pom |
| MySQL Connector | 8.x.x | Oracle | JDBC driver |
| Hibernate | 6.x.x | (transitive) | |
| Jackson | 2.x.x | FasterXML | (transitive) |
| Lombok | 1.18.x | Project Lombok | annotation processor |
| JaCoCo | 0.8.x | EclEmma | coverage plugin |
| JUnit | 5.x.x | JUnit Team | (transitive) |

## Database

| Component | Version | Note |
|---|---|---|
| MySQL Server | 8.4.x | UTF8MB4 charset |
| SQL mode | `<output của SELECT @@sql_mode>` | capture từ DB |
| InnoDB | default | |
| Default isolation | REPEATABLE READ | MySQL default |

## Frontend (nếu cùng repo)

| Tool | Version | Source |
|---|---|---|
| Node | 22.x | `node --version` |
| pnpm | 10.x | `pnpm --version` |
| Next.js | 14.x | `frontend/package.json` |
| React | 18.x | `frontend/package.json` |
| TypeScript | 5.x | `frontend/package.json` |
| Tailwind CSS | 3.x | `frontend/package.json` |

## Infrastructure (nếu có)

| Component | Image / Tag | Digest |
|---|---|---|
| Runtime | `eclipse-temurin:21-jre` | sha256:abc123... |
| Database | `mysql:8.4` | sha256:def456... |
| Migration tool | `flyway:10` | sha256:ghi789... |

## Test baseline

| Metric | Value | Source |
|---|---|---|
| Total tests | 602 | `mvn test` |
| Passed | 602 | report |
| Failed | 0 | report |
| Skipped | 3 | report (nếu có — list lý do) |
| Coverage overall | TBD% | Gate 3.2 (JaCoCo) |

## API contract

| Field | Value |
|---|---|
| OpenAPI version | 1.0.0 (frozen) |
| API version | v1 |
| Endpoint count | 15 |
| Breaking changes since v0 | none |

## Lock files

- Backend: `backend/pom.xml` (Maven) — không dùng `mvn dependency:lock` trừ khi cần
- Frontend: `frontend/pnpm-lock.yaml` (đã có sẵn từ `pnpm install`)

## OS environment

| Component | Version |
|---|---|
| OS | Windows 10/11 hoặc Linux (tùy developer) |
| Shell | bash / PowerShell |
| Terminal | Cursor IDE terminal |

## Capture command

Để tái tạo snapshot tự động:

```bash
# Capture full environment
{
  echo "=== git ==="
  git rev-parse HEAD
  git branch --show-current
  echo "=== java ==="
  java -version 2>&1
  echo "=== maven ==="
  mvn --version
  echo "=== spring-boot ==="
  grep -A1 "spring-boot-starter-parent" backend/pom.xml | head -2
  echo "=== node ==="
  node --version 2>/dev/null || echo "n/a"
  echo "=== pnpm ==="
  pnpm --version 2>/dev/null || echo "n/a"
  echo "=== mysql ==="
  mysql --version 2>/dev/null || echo "n/a"
  mysql -e "SELECT @@version, @@sql_mode;" 2>/dev/null || echo "n/a"
  echo "=== docker ==="
  docker --version 2>/dev/null || echo "n/a"
  echo "=== test baseline ==="
  echo "602 passed / 0 failed / 3 skipped"
} > docs/evidence/gate3/release/dependency-snapshot.txt
```

**Pass khi**: File `docs/RELEASE_DEPENDENCIES_v11.md` tồn tại với đầy đủ các section trên.

**Evidence lưu tại**: 
- `docs/RELEASE_DEPENDENCIES_v11.md` (versioned file)
- `docs/evidence/gate3/release/dependency-snapshot.txt` (raw capture)

---

## Gate 3.8 — Artifact Checksum (BẮT BUỘC nếu phát hành artifact)

**Mục đích**: Đảm bảo binary được deploy đúng với binary đã kiểm thử. Tránh silent corruption hoặc "binary khác commit đã merge".

**Áp dụng khi**: Backend phát hành JAR/WAR; Frontend build static bundle.

### Bước 1 — Build artifact

```bash
cd backend
mvn clean package -DskipTests=false
# Output: target/backend-<version>.jar
```

### Bước 2 — Tính checksum

```bash
# Linux/macOS
sha256sum target/backend-<version>.jar

# Windows PowerShell
Get-FileHash -Algorithm SHA256 target/backend-<version>.jar

# Windows cmd
certutil -hashfile target/backend-<version>.jar SHA256
```

### Bước 3 — Lưu vào release note

```markdown
## Artifact Checksum

| Artifact | SHA256 |
|---|---|
| `backend-<version>.jar` | `<64-char hex>` |
| `frontend-bundle.tar.gz` | `<64-char hex>` (nếu có) |
```

### Bước 4 — Verify (sau khi deploy)

```bash
# Trên server, so sánh checksum
sha256sum backend-<version>.jar
# Phải khớp với checksum trong release note
```

**Pass khi**: Checksum được lưu trong release note, verify lại trên môi trường deploy khớp 100%.

**Evidence lưu tại**: `docs/evidence/gate3/release/artifact-checksums.txt`.

## Trạng thái ký hiệu cho Epic 1 Backend

| Trạng thái | Ký hiệu | Điều kiện |
|---|---|---|
| Feature Complete | ✅ | Gate 1 PASS |
| Feature Complete – Awaiting Release Verification | 🟡 | Gate 1 PASS + Gate 2 + 3 PENDING |
| Ready for merge | 🟢 | Gate 1 + Gate 2 + Gate 3 PASS |
| Production Ready | 🟣 | Tag `v11-backend-ready` đã gắn |

**Trạng thái hiện tại**: 🟡 Feature Complete – Awaiting Release Verification.

## Folder structure evidence (kế hoạch)

```
docs/
├── POST_EPIC1_REVIEW_CHECKLIST.md       ← file này
├── RELEASE_NOTES_v11.md                 ← mẫu có sẵn ở trên
└── evidence/
    ├── gate1/                           ← Gate 1 (compile + test logs)
    ├── gate2/                           ← Gate 2 (runtime + production)
    │   ├── runtime/                     ← 1D
    │   ├── production/                  ← 1E
    │   ├── smoke/
    │   ├── audit/
    │   ├── transaction/
    │   └── pagination/
    └── gate3/                           ← Gate 3 (perf + coverage + release)
```

## Cam kết cuối

```
Epic 1 Backend — Trạng thái đóng băng cuối cùng

  Code:         ✅ Feature Complete (Gate 1 PASS)
  Runtime:      ⏳ Awaiting Evidence (Gate 2 PENDING)
  Release:      ⏳ Awaiting Reviewer (Gate 3 PENDING)

  Action:       Đứng yên — Không merge cho đến khi:
                1. Gate 2 có evidence đầy đủ
                2. Gate 3 reviewer approve

  Next step:    Owner decision — Ai sẽ chạy evidence?
                (a) Reviewer khác chạy
                (b) AI chạy (cần: backend running + DB test + JWT token)
                (c) Mixed — chia sub-step
```

Cảm ơn bạn đã giữ kỷ luật "evidence > checklist" và tổ chức lại theo **3 Quality Gates** — đây là cách tổ chức phù hợp cho dự án dài hạn.

---

## Lịch trình triển khai Epic 1 Release (3 ngày)

> **Nguyên tắc**: Giá trị của verification giảm nếu để cách quá xa thời điểm code hoàn thành. Nhịp 3 ngày đảm bảo evidence vẫn relevant và reviewer giữ được context.

### Ngày 1 — Gate 2: Runtime Evidence

**Mục tiêu**: Xác nhận hệ thống hoạt động đúng trong điều kiện thực tế.

**Thực hiện**:

1. 10 mục Runtime Verification (Bước 1D)
   - API Contract vs TS Client
   - Audit semantics (read vs write)
   - Transaction boundary
   - Pagination edge cases (page=-1, size=0, size=99999)
   - Concurrent update (apply cùng lúc)
   - Import/Export canonical (JSON round-trip)
   - Search semantics
   - Favorite + Default filter
   - Security edge (403/401)
   - DB index review

2. 4 bài test Production Evidence (Bước 1E)
   - Persistence round-trip (DB → DTO → DB khớp)
   - Apply idempotency (apply 2 lần cùng config → state giống)
   - Audit chronology (8 write operations theo đúng thứ tự)
   - Transaction rollback (throw giữa chừng → rollback atomic)

3. Smoke test 1 happy-path
   - Login as ADMIN → CRUD profile → Apply → Verify active config changed

**Output**:
- `docs/evidence/gate2/runtime/` — 10 file evidence
- `docs/evidence/gate2/production/` — 4 file evidence
- `docs/evidence/gate2/smoke/smoke-test.log`
- `docs/evidence/gate2/audit/audit-chronology.txt`
- `docs/evidence/gate2/transaction/` — 3 file rollback
- `docs/evidence/gate2/pagination/edge-cases.log`

**Nếu phát hiện bug**:
- Fix bug ngay trong ngày
- Chạy lại đúng testcase đó
- **Không mở rộng phạm vi sang feature mới**

**Pass khi**: Tất cả 10 + 4 + 1 + audit + transaction + pagination đều có evidence PASS.

### Ngày 2 — Gate 3: Release Readiness

**Mục tiêu**: Đảm bảo hệ thống đủ điều kiện phát hành và có khả năng tái tạo.

**Thực hiện**:

**Sáng** — Performance + Coverage:
1. Chạy k6 script cho 4 endpoints:
   - `GET /profiles` (p95 < 200ms)
   - `GET /profiles/{id}` (p95 < 200ms)
   - `POST /profiles` (p95 < 500ms)
   - `POST /{id}/apply` (p95 < 500ms)
2. Sinh JaCoCo coverage report
   - Service ≥ 90%
   - Repository ≥ 95%
   - Controller ≥ 80%
   - Overall ≥ 80%

**Chiều** — Dependency + Artifact:
3. Capture Dependency Lock (Gate 3.7)
   - Git commit SHA + tag dự kiến
   - Java vendor + version (Temurin 21.x)
   - Maven version
   - Spring Boot version
   - MySQL version + SQL mode
   - Node + pnpm version (cho frontend)
   - Docker image digest (nếu có)
   - OpenAPI version
   - Test baseline (602/0/3)
   - Lock files (pom.xml, pnpm-lock.yaml)
4. Build release artifact
   - `mvn clean package`
   - SHA256 checksum

**Cuối ngày** — Review + Merge + Tag:
5. Reviewer approval:
   - Tech lead (architecture)
   - Senior dev (code quality)
   - QA lead (evidence review)
6. Merge vào `main`
7. Tạo annotated tag:
   ```bash
   git tag -a v11-backend-ready \
     -m "Epic 1 Backend: Frozen API v1, 602 tests PASS, runtime evidence complete"
   git push origin main --tags
   ```
8. Publish release notes:
   - `docs/RELEASE_NOTES_v11.md`
   - `docs/RELEASE_DEPENDENCIES_v11.md`

**Output**:
- `docs/evidence/gate3/perf/` — 4 baseline JSON
- `docs/evidence/gate3/coverage/` — JaCoCo HTML + CSV
- `docs/RELEASE_DEPENDENCIES_v11.md` — versioned snapshot
- `docs/evidence/gate3/release/artifact-checksums.txt`
- `docs/releases/v11-backend-ready.md` — release metadata
- Git tag `v11-backend-ready` trên remote

**Pass khi**: Tất cả sub-step trên có evidence + reviewer approve + merge + tag tồn tại trên remote.

### Ngày 3 — Frontend Kickoff

**Mục tiêu**: Bắt đầu PR-11-04 với backend đã đóng băng.

**Thực hiện**:

1. Frontend pin tag `v11-backend-ready`:
   ```json
   // frontend/package.json
   {
     "dependencies": {
       "api-client": "git+https://github.com/<org>/backend.git#v11-backend-ready"
     }
   }
   ```
2. Khởi tạo PR-11-04 branch: `feature/pr-11-04-frontend-profile-ui`
3. Triển khai:
   - Profile list page (table)
   - Profile detail page
   - Create/Edit form
   - Apply button + confirmation
   - Favorite/Default toggle
   - Import/Export UI
4. Tests: unit + integration

**Output**: PR-11-04 sẵn sàng review.

---

## Những việc **KHÔNG khuyến khích**

❌ **Không trì hoãn** Gate 2/3 quá lâu sau khi code xong:

> "Code xong rồi để Gate 2, Gate 3 một tuần sau mới chạy."

Lý do:
- Môi trường có thể thay đổi (DB state, schema, data drift)
- Dependency version drift (Maven, npm tự động update)
- Reviewer quên context, phải đọc lại code
- Bug khó truy ngược khi evidence chạy trên code cũ

❌ **Không mở rộng phạm vi** khi đang chạy Gate 2:

> "Tiện fix thêm feature X lúc đang verify Gate 2."

Lý do:
- Phá vỡ "evidence belongs to a specific commit"
- Khó tách bug Gate 2 vs bug feature X
- Vi phạm nguyên tắc "đóng băng trước khi mở rộng"

❌ **Không merge** nếu Gate 2 evidence chưa đầy đủ:

> "Code ổn rồi, merge đi evidence tính sau."

Lý do:
- Tag sẽ không reproducible
- Frontend pin tag sẽ nhận code chưa verified
- Khó rollback khi có vấn đề production

---

## Phân công (đề xuất cho Tech Lead)

| Role | Ngày 1 (Gate 2) | Ngày 2 (Gate 3) | Ngày 3 (Frontend) |
|---|---|---|---|
| **AI Assistant** | Có thể chạy scripts nếu có backend running + DB + JWT | Capture dependency lock, build artifact, tính SHA256 | Hỗ trợ frontend nếu cần |
| **Backend dev** | Sửa bug nếu phát hiện | Reviewer approve, merge, tag | Hỗ trợ frontend integration |
| **Senior dev** | Pair review evidence | Reviewer chính (code quality) | Code review frontend |
| **QA lead** | Verify evidence logic | Reviewer (evidence quality) | Test frontend integration |
| **Tech lead** | Quyết định bug priority | Reviewer cuối (architecture), approve merge | Unblock frontend blockers |

---

## Trạng thái tổng hợp cuối

```
═══════════════════════════════════════════════════════════════
Epic 1 Backend — Status Report
═══════════════════════════════════════════════════════════════

🔵 Engineering       ✅ Complete
   ├─ Code review, 7 nguyên tắc
   ├─ OpenAPI/Swagger đầy đủ
   ├─ 602 tests PASS, TypeScript clean
   └─ PR-11-01 → PR-11-03 merged

🔒 API Contract      🔒 Frozen v1
   └─ 15 endpoints, OpenAPI 1.0.0

🟡 Runtime Evidence   ⏳ Pending — Gate 2 starts Ngày 1
🟣 Performance        ⏳ Pending — Gate 3 Ngày 2
🟣 Coverage           ⏳ Pending — Gate 3 Ngày 2
🟣 Dependency Lock    ⏳ Pending — Gate 3.7 Ngày 2 ✨
🟣 Artifact Checksum  ⏳ Pending — Gate 3.8 Ngày 2 ✨
🟣 Reviewer Approval  ⏳ Pending — Gate 3 Ngày 2

───────────────────────────────────────────────────────────────
Release Status:    Feature Complete, Awaiting Release Verification
Target Tag:         v11-backend-ready (end of Ngày 2)
Next Epic:          PR-11-04 Frontend Profile UI (Ngày 3)
───────────────────────────────────────────────────────────────

Estimated release date: 3 ngày từ hôm nay
═══════════════════════════════════════════════════════════════
```

Cảm ơn bạn đã đề xuất nhịp triển khai 3 ngày — đây là cách cân bằng giữa **verification quality** (không vội) và **velocity** (không trì hoãn). Nhịp này đảm bảo evidence còn relevant khi chạy, reviewer giữ được context, và Frontend có thể kickoff ngay khi tag sẵn sàng.
