# CR-001 — Cross-Specialty Support for L04 (PK Chuyên gia)

| Thuộc tính | Chi tiết |
| :--- | :--- |
| **Ticket** | CR-001 |
| **Severity** | P2 (Business Decision / Configuration Governance) |
| **Release impact** | NON-BLOCKING cho v1.1.0 (configuration only, no schema change) |
| **Created** | 2026-07-19 |
| **Created by** | Dev team (post-V10 config audit, see `CONFIG_ADMIN_FULL_AUDIT.md`) |
| **Status** | **AWAITING BA/PO DECISION** |
| **Decision deadline** | Trước Sprint tiếp theo / trước khi phát hành V11 (đề xuất: **2026-08-15**) |
| **Related** | `QuanLyLichCongTac_v5.md` M05, M07-F05, M07-F06 |
| **Component** | `auto_gen_l04_cross_specialty`, `auto_gen_l04_cross_specialty_ratio` |

---

## 1. Tóm tắt (TL;DR)

Phiên bản V10 đã thêm hai config keys:

```
auto_gen_l04_cross_specialty       (boolean)
auto_gen_l04_cross_specialty_ratio (double 0.0–1.0)
```

Cho phép bác sĩ khác chuyên khoa được phân công vào ca **L04 (PK Chuyên gia)** khi khoa đó thiếu chuyên gia.

**Tài liệu `QuanLyLichCongTac_v5.md` KHÔNG mô tả tính năng này** và mặc định nghiệp vụ được mô tả theo hướng **ưu tiên đúng chuyên khoa** (M05-F04, M07-F05). Workflow M07-F06 chính thức cho phép scheduler để trống ngày chưa phân công và để Manager xử lý thủ công.

**Cần BA/PO quyết định**: cross-specialty có phải nghiệp vụ mong muốn của bệnh viện hay không — vì dev không có thẩm quyền quyết định vấn đề nghiệp vụ, và tài liệu V5 không cung cấp cơ sở để suy ra.

---

## 2. Background

### 2.1 Phát hiện từ V10 Config Audit

Trong quá trình rà soát V10, đội phát triển phát hiện hai config keys mới được thêm vào subsystem auto-gen mà **không tìm thấy ticket/CR/PO approval đi kèm** trong repo:

| Config Key | Default | Ý nghĩa (theo code) |
| :--- | :--- | :--- |
| `auto_gen_l04_cross_specialty` | (cần xác nhận từ code — xem `AutoGenConfig.java`) | Bật/tắt fallback cross-specialty cho L04 |
| `auto_gen_l04_cross_specialty_ratio` | (cần xác nhận từ code) | Tỉ lệ slot L04 được phép dùng cross-specialty (0.0–1.0) |

**Reference**: `docs/CONFIG_ADMIN_FULL_AUDIT.md` (section phát hiện V10 audit).

### 2.2 Vì sao tính năng này xuất hiện

Khi chạy scheduler V10 với bộ dữ liệu thực tế:

- **Observation**: Một số khoa có **ít chuyên gia chuyên khoa** so với `requiredStaffCount` mặc định. (Reference: `docs/AUDIT_SCHEDULER_ENGINE.md` section "Coverage by specialty")
- **Observation**: Scheduler để trống nhiều slot L04 → coverage thấp. (Reference: cùng file, bảng "L04 coverage per khoa")
- **Giả thuyết của dev**: cross-specialty được thêm vào V10 như một **fallback kỹ thuật** để nâng coverage — nhưng giả thuyết này **chưa được xác nhận** trong repo (không có comment trong commit, không có ticket).

### 2.3 Vấn đề traceability

Không tìm thấy:

- ❌ Ticket yêu cầu tính năng
- ❌ Change request từ BA/PO
- ❌ Quyết định nghiệp vụ của trưởng khoa
- ❌ Tài liệu release note cho version này

---

## 3. Trích dẫn từ `QuanLyLichCongTac_v5.md`

### 3.1 Mặc định nghiệp vụ — ưu tiên đúng chuyên khoa

| Mục | Trích dẫn | Diễn giải |
| :--- | :--- | :--- |
| 1.2 L04 | "Chuyên gia phụ trách ca khám chuyên sâu" | L04 dành cho chuyên gia có chuyên môn sâu |
| M05-F01 | "Chọn chuyên gia và ngày phụ trách" | Không đề cập chọn BS khác khoa |
| M05-F04 | "Lọc lịch theo chuyên khoa: Ngoại, Nội, Sản, Nhi, Mắt, Răng" | Mỗi khoa có chuyên gia riêng |
| M07-F05 | "Tự gán **chuyên gia phù hợp chuyên khoa** vào từng ngày" | Ràng buộc rõ ràng về đúng chuyên khoa |

### 3.2 Workflow cho phép scheduler để trống (Fact)

> **M07-F06 — Báo cáo ngày chưa phân công được**
>
> Liệt kê các ngày chưa đủ nhân sự hợp lệ để phân công; quản lý xử lý thủ công phần còn lại.

**Trích nguyên văn từ tài liệu** (xem `QuanLyLichCongTac_v5.md` phần M07-F06). Mục này không diễn giải — chỉ ghi nhận fact.

### 3.3 Analysis: Những gì M07-F06 ngụ ý (Suy luận)

Phần này là **suy luận của dev** dựa trên M07-F06, không phải trích dẫn trực tiếp. BA/PO có thể đồng ý hoặc không với cách diễn giải này.

- **Suy luận 1**: M07-F06 liệt kê "ngày chưa phân công" như một trạng thái hợp lệ — không phải lỗi. Điều này có thể ngụ ý rằng scheduler không bắt buộc đạt 100% coverage L04.
- **Suy luận 2**: Tồn tại workflow "quản lý xử lý thủ công" cho các ngày chưa phân công. Đây là kênh xử lý thay thế hợp lệ, không chỉ là biện pháp cuối cùng.
- **Suy luận 3**: Việc thêm cross-specialty không nhất thiết phải là giải pháp kỹ thuật — vì tài liệu đã mô tả một workflow chấp nhận coverage < 100%.

### 3.4 Tài liệu không cấm cross-specialty (Fact + Analysis)

**Fact**: tìm kiếm trong `QuanLyLichCongTac_v5.md` (cả 5 mục 1.x và 2.Mxx) không cho thấy câu nào **cấm** BS khác khoa tham gia L04.

**Analysis** (suy luận của dev, không phải kết luận nghiệp vụ):

- "Không có trong tài liệu" **không đồng nghĩa** với "không được phép". Ví dụ: tài liệu cũng không đề cập cache, transaction, optimistic locking — nhưng đây là các kỹ thuật hợp lệ.
- Cross-specialty có thể được xem là **enhancement** nếu BA/PO xác nhận.
- Tuy nhiên, **không có cơ sở tài liệu** để khẳng định cross-specialty là nghiệp vụ mặc định — fact là tài liệu mô tả workflow **không cần** cross.
- Kết luận nghiệp vụ: cần BA/PO quyết định, không thể suy ra từ tài liệu.

---

## 4. Current Behavior

Phần này mô tả hành vi hiện tại của scheduler engine, dựa trên code ở V10. Mọi flow bên dưới đều có thể truy ngược từ code (xem References section 11).

### 4.1 Khi `crossSpecialty = OFF` (giả định default hiện tại)

```text
L04 Nội (1 ngày, cần 4 BS Nội)
       ↓
Pool eligibility: chỉ BS có specialty = Nội
       ↓
Đủ người → phân công đúng chuyên khoa
Thiếu người → để trống + báo cáo M07-F06
```

**Code reference** (xem `backend/src/main/java/com/hospital/scheduler/algorithm/StaffEligibilityFilter.java`):

- Eligibility filter chỉ chấp nhận staff có `specialty_id == required_specialty_id`.
- Nếu pool rỗng → trả về empty assignment, không fallback.

### 4.2 Khi `crossSpecialty = ON` + ratio > 0

```text
L04 Nội (1 ngày, cần 4 BS Nội)
       ↓
Pool eligibility: BS Nội + ratio × BS khác khoa
       ↓
Thuật toán có thể gán BS Ngoại vào L04 Nội
       ↓
Slot L04 Nội được lấp đầy (improved coverage)
```

**Code reference** (xem `backend/src/main/java/com/hospital/scheduler/algorithm/AutoSchedulingService.java` và `CspSearchEngine.java`):

- V10 đã thêm nhánh code cho phép mở rộng pool eligibility khi `crossSpecialty = true`.
- `auto_gen_l04_cross_specialty_ratio` được dùng làm cap phần trăm slot cross trong một period.

### 4.3 Vấn đề tiềm ẩn (Observation từ dev, cần BA xác nhận)

- **Nghiệp vụ**: BS Ngoại có đủ năng lực khám chuyên sâu Nội không? (BA cần xác nhận dựa trên quy định bệnh viện, không có tài liệu V5 đề cập)
- **Tuân thủ**: bệnh viện có cho phép BS khám ngoài chuyên khoa đăng ký không? (BA cần xác nhận)
- **Audit**: code hiện tại ghi log assignment nhưng **không có flag riêng** cho cross-specialty — đây là gap kỹ thuật, không phải gap nghiệp vụ.

---

## 5. Business Questions

Phần này đặt câu hỏi trung lập cho BA/PO. Mỗi câu hỏi đứng độc lập — không có câu nào gợi ý đáp án hoặc ngụ ý "Option nào đúng".

### Q1 — Phạm vi phân công L04

> Trong trường hợp thiếu chuyên gia đúng chuyên khoa, scheduler có được phép sử dụng bác sĩ khác chuyên khoa để phân công L04 không?

- [ ] Có, không giới hạn điều kiện.
- [ ] Có, có điều kiện (BA/PO ghi rõ điều kiện kèm theo).
- [ ] Không, chỉ chuyên gia đúng chuyên khoa.

### Q2 — Điều kiện áp dụng (nếu Q1 = "Có có điều kiện")

> Nếu cho phép, điều kiện nào được áp dụng?

- [ ] Chỉ khi thiếu người (fallback khi pool cùng khoa rỗng).
- [ ] Chỉ một số khoa cụ thể (BA/PO liệt kê: _______________).
- [ ] Chỉ bác sĩ đã được đào tạo / chứng nhận (cần thêm bảng `staff_cross_specialty_cert`).
- [ ] Điều kiện khác (BA/PO mô tả): _______________.

### Q3 — Phạm vi theo khoa

> Có cần quy định khác nhau giữa các cặp khoa không?

Ví dụ minh họa (BA/PO không bắt buộc theo):

```text
Ngoại ← Nội     : tùy quy định bệnh viện
Ngoại ← Sản      : tùy quy định bệnh viện
Răng  ← Mắt      : tùy quy định bệnh viện
```

- [ ] Có, cần bảng ma trận `cross_specialty_matrix` để BA/PO cấu hình từng cặp khoa.
- [ ] Không, dùng ratio chung cho tất cả cặp khoa.
- [ ] Không, cấm hoàn toàn (Q1 = "Không").

### Q4 — Audit trail

> Khi scheduler sử dụng bác sĩ khác chuyên khoa, có cần ghi lại lý do vào audit log không?

- [ ] Có, ghi rõ trong `audit_history` (ví dụ: action = `CROSS_SPECIALTY_ASSIGN`, lý do, specialty gốc, specialty thực tế).
- [ ] Không, không cần audit riêng.

### Q5 — Default value

> Nếu giữ 2 config keys `auto_gen_l04_cross_specialty` và `auto_gen_l04_cross_specialty_ratio`, BA/PO chọn giá trị default nào?

- [ ] `crossSpecialty = false`.
- [ ] `crossSpecialty = true`.
- [ ] Xóa hẳn 2 config keys khỏi hệ thống.

### Q6 — Quyết định cuối cùng

> Sau khi xem xét Q1–Q5, BA/PO chọn:

- [ ] Option A — Strict Specialty (xem section 6.1).
- [ ] Option B — Configurable (xem section 6.2).
- [ ] Option C — Always ON (xem section 6.3 — option này đi ngược mô tả mặc định trong V5).
- [ ] Option khác (BA/PO mô tả): _______________.

---

## 6. Options

Phần này liệt kê 3 phương án khả thi. Bảng ưu/nhược dựa trên **quan sát kỹ thuật** của dev, không phải đánh giá nghiệp vụ — BA/PO quyết định phương án nào phù hợp.

### 6.1 Option A — Strict Specialty

**Cấu hình minh họa**:

```yaml
auto_gen_l04_cross_specialty: false
auto_gen_l04_cross_specialty_ratio: 0.0
```

| Đặc điểm | Mô tả |
| :--- | :--- |
| Mức độ phù hợp với mặc định nghiệp vụ V5 | Cao — chỉ chuyên gia đúng chuyên khoa được phân công L04. |
| Thay đổi code | Tối thiểu — giữ `StaffEligibilityFilter` như hiện tại, có thể xóa nhánh code cross-specialty. |
| Coverage khi thiếu chuyên gia | Thấp — các slot L04 không đủ người sẽ xuất hiện trong M07-F06 report. |
| Độ phức tạp cho Manager | Trung bình — Manager xử lý thủ công các ngày thiếu (đúng workflow M07-F06). |

**Áp dụng khi BA/PO xác nhận**: "Không cho phép cross-specialty trong bất kỳ trường hợp nào".

---

### 6.2 Option B — Configurable

**Cấu hình minh họa** (giá trị default đề xuất từ dev, BA/PO có thể điều chỉnh):

```yaml
auto_gen_l04_cross_specialty: false   # default OFF — phù hợp V5 mặc định
auto_gen_l04_cross_specialty_ratio: ???   # BA/PO quyết định bound (gợi ý dev: 0.0–0.3)
```

| Đặc điểm | Mô tả |
| :--- | :--- |
| Mức độ phù hợp với mặc định nghiệp vụ V5 | Tùy default — nếu default = `false` thì phù hợp, nếu `true` thì không. |
| Thay đổi code | Giữ nguyên V10 — không cần refactor. Chỉ cần audit log + documentation. |
| Coverage khi thiếu chuyên gia | Có thể cải thiện nếu Manager bật `crossSpecialty = true`. |
| Độ phức tạp cho Manager | Phụ thuộc BA/PO — nếu cho phép bật, cần UI review badge "Cross". |

**Áp dụng khi BA/PO xác nhận**: "Cho phép linh hoạt — Manager được bật/tắt theo tình huống".

---

### 6.3 Option C — Always ON

**Cấu hình minh họa**:

```yaml
auto_gen_l04_cross_specialty: true
auto_gen_l04_cross_specialty_ratio: ???   # BA/PO quyết định
```

| Đặc điểm | Mô tả |
| :--- | :--- |
| Mức độ phù hợp với mặc định nghiệp vụ V5 | Thấp — tài liệu V5 không mô tả trường hợp này. |
| Thay đổi code | Tối thiểu — chỉ đổi default value. |
| Coverage khi thiếu chuyên gia | Cao nhất trong 3 Option. |
| Độ phức tạp cho Manager | Thấp — không cần review cross assignments. |

**Áp dụng khi BA/PO xác nhận**: "Cho phép cross-specialty là hành vi bình thường, không cần audit đặc biệt".

**Ghi nhận từ dev**: Option C đi ngược mô tả mặc định trong tài liệu V5. Nếu BA/PO chọn Option này, cần CR riêng để cập nhật tài liệu nghiệp vụ cho phù hợp.

---

## 7. Risk Analysis

Phần này giúp BA/PO và reviewer đánh giá rủi ro khi chọn từng Option.

| Option | Loại rủi ro | Mô tả | Xác suất | Tác động | Mitigation |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **A** | Coverage | Khi thiếu chuyên gia, nhiều slot L04 sẽ bị bỏ trống → M07-F06 phát sinh nhiều "unassigned days" | Cao | Trung bình — tăng tải cho Manager | Cải thiện M07-F06 report UI; đào tạo Manager xử lý thủ công |
| **A** | Nghiệp vụ | Không phát sinh rủi ro mới — tuân theo workflow đã mô tả trong tài liệu V5 (M07-F06) | — | — | — |
| **B** | Phân công sai chuyên khoa | Nếu cấu hình ratio quá cao hoặc thiếu audit, có thể BS không đủ năng lực khám chuyên sâu bị gán vào L04 | Trung bình | Cao — ảnh hưởng chất lượng khám | Audit log bắt buộc; giới hạn ratio do BA/PO quyết định; UI badge "Cross" để Manager review |
| **B** | Tuân thủ | Bệnh viện có quy định cấm BS khám ngoài chuyên khoa đăng ký không? (cần BA xác nhận) | Thấp–Trung bình | Cao nếu vi phạm | Cần BA confirm trước khi bật |
| **B** | Truy vết | Thiếu log "tại sao cross được chọn" sẽ khó audit sau này | Thấp | Trung bình | Bắt buộc ghi `audit_history.action = "CROSS_SPECIALTY_ASSIGN"` |
| **C** | Nghiệp vụ | Tài liệu V5 không mô tả trường hợp này — cần CR riêng để cập nhật tài liệu nếu BA/PO chọn Option C | Cao | Cao | BA/PO cần tạo CR riêng cho việc cập nhật tài liệu V5 |

### 7.1 Rủi ro chung (mọi Option)

| Rủi ro | Mô tả | Mitigation |
| :--- | :--- | :--- |
| Traceability | Nếu không có CR được approve, V10.x đã shipped một tính năng không có nghiệp vụ rõ ràng | CR này là biện pháp khắc phục |
| Test coverage | Test case cross-specialty có thể chưa đủ (mặc dù engine có hỗ trợ) | Bổ sung test trước khi V11 release |
| UI clarity | Manager có thể không hiểu "Cross" badge là gì | Cần tooltip giải thích rõ |

---

## 8. Out of Scope

CR này **KHÔNG** bao gồm các thay đổi sau. Nếu BA/PO muốn mở rộng, cần tạo CR riêng:

- ❌ Thay đổi thuật toán fairness (Round Robin, distribution balancing).
- ❌ Thay đổi logic phân công L01 (Trực 24/24) và L02 (Thông tầm).
- ❌ Thay đổi logic phân công L03 (PK Dịch vụ).
- ❌ Thay đổi bảng `shift_requirement` (cấu hình yêu cầu ca trực).
- ❌ Thay đổi logic compensation day cho L01.
- ❌ Refactor scheduler engine hoàn toàn (chỉ giới hạn ở eligibility filter L04).
- ❌ Thêm bảng `cross_specialty_matrix` per-khoa (chỉ khi BA chọn Q3 = "Có").
- ❌ Thay đổi UI dashboard hoặc navigation.
- ❌ Thay đổi database schema ngoài 2 config keys trên.

### Phạm vi duy nhất của CR này

✅ Quyết định: **giữ / bỏ / cấu hình** tính năng `auto_gen_l04_cross_specialty`.
✅ Cấu hình: default value + bounds (ratio) cho 2 config keys.
✅ Audit: nếu giữ, thêm log khi cross được sử dụng.
✅ Tài liệu: cập nhật release note + traceability cho V10/V11.

---

## 9. Acceptance Criteria

Mỗi acceptance criterion được viết theo format **Given / When / Then** để QA có thể test tự động hoặc thủ công.

### 9.1 Option A (Strict Specialty) — Acceptance

```gherkin
Given crossSpecialty = false (default)
When  generate schedule for any period
Then  không có L04 nào được gán cho staff có specialty khác specialty requirement.
And   các slot L04 thiếu người sẽ xuất hiện trong M07-F06 report.
And   metric `cross_specialty_used_count = 0` cho cả period.
```

### 9.2 Option B (Configurable, Default OFF) — Acceptance

```gherkin
Given crossSpecialty = false (default)
When  generate schedule
Then  hành vi giống Option A — không có L04 cross-specialty.

Given crossSpecialty = true AND ratio = R (R do BA/PO quyết định trong CR)
When  generate schedule cho khoa thiếu chuyên gia
Then  scheduler được phép dùng cross-specialty cho tối đa R% slot L04 (trong đó R là ratio được BA/PO chấp thuận).
And   mỗi assignment cross có audit log ghi:
      - staff_id (BS được gán)
      - required_specialty_id (L04 yêu cầu)
      - actual_specialty_id (BS thực tế)
      - reason = "CROSS_SPECIALTY_FALLBACK"
      - timestamp
And   UI hiển thị badge "Cross" trên slot cross-specialty để Manager dễ nhận biết.

Given crossSpecialty = true
When  ratio vượt bound do BA/PO quyết định (cố tình cấu hình sai)
Then  Scheduler cảnh báo (warning) — bound quyết định do BA/PO.
And   log warning vào audit_history với reason = "HIGH_CROSS_RATIO".
```

### 9.3 Option C (Always ON) — Acceptance

```gherkin
Given crossSpecialty = true (default, hard-coded)
When  generate schedule bất kỳ
Then  scheduler luôn cho phép cross-specialty.
And   Lưu ý: Option C yêu cầu tài liệu V5 được cập nhật — nếu chọn Option này, cần CR riêng.
```

### 9.4 Tiêu chí chung (mọi Option)

```gherkin
# Documentation
Given CR-001 được approve
When  release V11 được tag
Then  release note V11 phải đề cập 2 config keys: auto_gen_l04_cross_specialty + ratio.
And   CHANGELOG.md ghi rõ Option được chọn và lý do.

# Test
Given code hiện tại
When  chạy full test suite
Then  test coverage cho scheduler engine ≥ 80%.
And   có ít nhất 1 test case cho mỗi Option (A/B/C).

# Performance
Given dataset 20 nhân sự, 4 khoa, 30 ngày
When  generate schedule
Then  thời gian generate < 30 giây.
And   memory usage không vượt baseline + 20%.
```

---

## 10. Recommendation

### Technical perspective (từ dev — không phải khuyến nghị nghiệp vụ)

> **Technical view (chỉ mang tính kỹ thuật, không thay thế quyết định nghiệp vụ):**
>
> - Option A yêu cầu ít code change nhất nhưng **xóa code V10 đã ship**, có thể ảnh hưởng test coverage và rollback plan.
> - Option B giữ nguyên code V10, không phá backward compatibility, dễ rollback — nhưng **chưa có tài liệu V5 hỗ trợ** mặc định `crossSpecialty = true`.
> - Option C chỉ đổi default value nhưng **đi ngược mô tả trong V5**, đòi hỏi cập nhật tài liệu.
>
> Lưu ý: quan sát này chỉ mang tính kỹ thuật. BA/PO có thể chọn bất kỳ Option nào dựa trên quy định bệnh viện — không có Option nào "đúng" hay "sai" về mặt kỹ thuật.

### Business perspective (quyết định của BA/PO)

> **Business decision required:**
>
> BA/PO quyết định giữa Option A và Option B (hoặc Option C) dựa trên:
>
> - Quy định bệnh viện về phạm vi hành nghề của BS.
> - Chính sách nguồn nhân lực cho các khoa thiếu chuyên gia.
> - Mức độ chấp nhận rủi ro khi BS khám ngoài chuyên khoa đăng ký.
>
> Dev không đủ thẩm quyền để quyết định nghiệp vụ — chỉ hỗ trợ phân tích kỹ thuật.

### Phân chia trách nhiệm

```text
Dev (kỹ thuật)     →  Phân tích ưu/nhược kỹ thuật của từng Option
                       (đã làm trong section 6, 7, 9 của CR này)

BA (nghiệp vụ)     →  Trả lời 5 câu hỏi ở section 5
                       Chọn Option phù hợp với quy định bệnh viện

PO (sản phẩm)      →  Đánh giá trade-off giữa coverage vs chất lượng
                       Phê duyệt Option cuối cùng

QA                  →  Verify Acceptance Criteria ở section 9
```

Mỗi bên có một góc nhìn; CR này cung cấp đủ thông tin để mỗi bên tự đánh giá theo phạm vi của mình.

---

## 11. References

### Tài liệu dự án

- `QuanLyLichCongTac_v5.md` — tài liệu mô tả chức năng (phiên bản 1.1, 05/2026)
  - Mục 1.2: định nghĩa L04
  - Mục 1.4: ràng buộc nghiệp vụ cốt lõi
  - Mục 2.M05: chức năng L04
  - Mục 2.M07: auto-scheduling
- `docs/CONFIG_ADMIN_FULL_AUDIT.md` — audit config V10
- `docs/AUDIT_SCHEDULER_ENGINE.md` — audit scheduler engine
- `docs/SPEC_M07_ANALYSIS.md` — phân tích M07

### Code liên quan

- `backend/src/main/java/com/hospital/scheduler/algorithm/` — scheduler engine
- `frontend/src/app/(dashboard)/auto-scheduling/algorithm-config/AutoCalculateDialog.tsx` — UI cấu hình
- Config keys: `auto_gen_l04_cross_specialty`, `auto_gen_l04_cross_specialty_ratio`

### Workflow nghiệp vụ

- M07-F05: "Tự gán chuyên gia phù hợp chuyên khoa vào từng ngày"
- M07-F06: "Báo cáo ngày chưa phân công được"

---

## 12. Decision Log

| Date | Decision | By | Reference |
| :--- | :--- | :--- | :--- |
| 2026-07-19 | Tạo CR-001, AWAITING BA/PO review | Dev team | `docs/backlog/CR-001_L04_CROSS_SPECIALTY.md` |
| _chờ điền_ | _BA/PO chọn Option A / B / C hoặc Other_ | _BA/PO name_ | _link meeting note hoặc ticket approval_ |

---

## 13. Sau CR — bước tiếp theo

Sau khi BA/PO quyết định Option, lần lượt xử lý:

1. **Nếu Option A**:
   - Xóa code cross-specialty khỏi `StaffEligibilityFilter` và `AutoSchedulingService`.
   - Cleanup 2 config keys khỏi database/config UI.
   - Cập nhật `release note V11` để ghi nhận việc xóa.
   - Migration: xóa dữ liệu audit log cũ liên quan cross-specialty (nếu có).

2. **Nếu Option B**:
   - Bổ sung audit log cho cross assignments (nếu chưa có).
   - Cập nhật `release note V11` để mô tả 2 config keys + bound ratio.
   - Sau khi Option B được xác nhận, mới tiến hành phân tích số BS theo từng khoa để xác định khoa nào cần bật cross (nếu có).
   - Bổ sung test case cho cross ON/OFF + ratio bounds.

3. **Nếu Option C**:
   - Tạo CR riêng để cập nhật tài liệu nghiệp vụ V5 (vì Option C đi ngược mô tả hiện tại).
   - Cập nhật `release note V11` với giải thích lý do đổi mặc định.
   - Cập nhật scheduler specification cho phù hợp.
   - Bổ sung test case cho default = true.

4. **Nếu BA/PO từ chối cả 3 Option**:
   - Tạo CR mới với phương án do BA/PO đề xuất.
   - Đóng CR-001 này với status = SUPERSEDED.