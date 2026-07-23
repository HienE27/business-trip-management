# Gate 2 — Runtime Evidence

> Ngày 1 (Gate 2): Chứng minh backend hoạt động đúng trong môi trường gần production.

## Mục lục

| Thư mục | Mục đích | Số file |
|---|---|---|
| `runtime/` | 10 mục Runtime Verification (Bước 1D) | 10 |
| `production/` | 4 bài test Production Evidence (Bước 1E) | 4 |
| `smoke/` | Smoke test 1 happy-path | 1 |
| `audit/` | Audit chronology verification | 1 |
| `transaction/` | Transaction rollback verification | 3 |
| `pagination/` | Pagination edge cases | 1 |

## Quy tắc evidence

1. Mỗi evidence file phải có:
   - Header: test case ID, mô tả, người chạy, timestamp, commit SHA
   - Command/curl đầy đủ (copy-paste được)
   - Output raw (không edit)
   - Kết luận PASS/FAIL có comment

2. Không tự ý sửa output. Nếu FAIL, dán nguyên output + chú thích.

3. Mỗi bug phát hiện:
   - Tạo file `bug-NNN.md` trong `runtime/` hoặc `production/`
   - Mô tả: reproduction steps, expected, actual, severity
   - Sau khi fix: update evidence với output mới + commit SHA fix

## Tổng hợp

Xem `gate2-summary.md` sau khi hoàn thành.

## Status

**Current**: ⏳ PENDING — Chờ Owner xác nhận điều kiện Ngày 1.

Xem chi tiết tại: `../POST_EPIC1_REVIEW_CHECKLIST.md` section "Ngày 1 — Gate 2".