# PR2 — Discovery & Tooling Checklist

> **Definition of Done cho PR2.** Mỗi mục dưới đây phải được tick trước khi
> merge. Nếu không tick được → PR chưa sẵn sàng, dù code chạy được.

## Cấu trúc PR

- [ ] Branch: `chore/pr2-discovery-tooling` đã checkout từ `main` sạch
- [ ] Working tree sạch trước khi bắt đầu tạo file
- [ ] PR chỉ chứa các file thuộc 2 thư mục:
  - `scripts/discovery/`
  - `docs/pr/` (bao gồm subfolder `templates/`)

## Files

- [ ] `scripts/discovery/static-analysis.sh` đã tạo
- [ ] `scripts/discovery/repo-analysis.sh` đã tạo
- [ ] `scripts/discovery/context-analysis.sh` đã tạo
- [ ] `docs/pr/templates/pr-discovery-report.md` đã tạo
- [ ] `docs/pr/PR2_README.md` đã tạo
- [ ] `docs/pr/pr2-checklist.md` (file này) đã tạo
- [ ] **Không** có file nào ngoài 6 file trên trong PR
- [ ] **Không** file nào trong 6 file trên bị trùng tên với file đã có

## Scripts — chất lượng

- [ ] Tất cả script đều có `#!/usr/bin/env bash` ở dòng đầu
- [ ] Tất cả script đều có `set -u` và `set -o pipefail`
- [ ] Tất cả script đều có comment header giải thích mục đích, cách dùng, đầu ra, exit code
- [ ] Tất cả script đều xử lý THIẾU argument TARGET_ROOT (dùng `$(cd ... && pwd)` làm default)
- [ ] Tất cả script đều kiểm tra TARGET_ROOT có tồn tại (exit 1 nếu không)
- [ ] Tất cả script đều xuất JSON ra stdout (không ghi file trừ khi user pipe `>` ra ngoài)
- [ ] Tất cả script đều có fallback khi thiếu `jq` (warn ra stderr, in JSON literal)
- [ ] Tất cả script đều bỏ qua: `target/`, `build/`, `dist/`, `out/`, `node_modules/`, `.git/`, `.cursor/`, `.trellis/`
- [ ] Exit code: `0` = thành công (kể cả khi output rỗng), `1` = lỗi nghiêm trọng

### `static-analysis.sh`

- [ ] Đếm được file Java (main + test) trong `backend/src/`
- [ ] Đếm được file TS/TSX/JS/JSX trong `frontend/src/`
- [ ] Trả về `hotspots_top10` (top 10 file dài nhất)
- [ ] Quét được TODO/FIXME/XXX/HACK và đưa vào `todos[]` (kèm path, line, text)

### `repo-analysis.sh`

- [ ] Snapshot được: branch hiện tại, HEAD SHA, tracking, ahead/behind
- [ ] Liệt kê được recent commits (15)
- [ ] Liệt kê được tất cả local branches + last commit date + subject
- [ ] Liệt kê được `backend/src/main/java/com/hospital/scheduler/*` packages
- [ ] Liệt kê được `frontend/src/app/**` routes
- [ ] Có `dead_hints[]` — heuristic file java không được grep reference

### `context-analysis.sh`

- [ ] Đếm tổng số file `.md`/`.txt` trong `docs/` và tổng bytes
- [ ] Trả về `top10_largest` (10 file docs lớn nhất)
- [ ] Phân loại được: SPEC, AUDIT, CONTRACT, RELEASE files
- [ ] Liệt kê được docs cập nhật trong 30 ngày qua (`recent_30d[]`)
- [ ] Trả về 20 migration Flyway gần nhất (nếu có thư mục `db/migration/`)
- [ ] Phát hiện được file OpenAPI (nếu có) — `openapi` field

## Template

- [ ] `docs/pr/templates/pr-discovery-report.md` có 11 section theo đúng thứ tự
- [ ] Có Metadata table (PR number, generated_at, ...)
- [ ] Có chỗ dán JSON output từ 3 scripts (section 1–8)
- [ ] Có 2 section reviewer phải điền bằng tay: Tóm tắt (section 9) và Quyết định (section 10)
- [ ] Section 10 dùng checkbox cho Batch A/B/C/D (mỗi batch = 1 PR tương lai)
- [ ] Có Appendix hướng dẫn chạy lại scripts

## README

- [ ] `docs/pr/PR2_README.md` giải thích TẠI SAO PR2 tồn tại (3 lý do)
- [ ] Có mục "Phạm vi" — liệt kê rõ CÓ và KHÔNG CÓ (scope guard)
- [ ] Có hướng dẫn cài `jq` (macOS / Linux / Windows)
- [ ] Có ví dụ chạy từng script và chạy cả 3 trong một shot
- [ ] Có output mẫu (rút gọn) cho mỗi JSON
- [ ] Có mục "Phạm vi bỏ qua cố định" để review biết scripts không scan những gì
- [ ] Có mục Troubleshooting (5 triệu chứng phổ biến)
- [ ] Có mục "Sau khi merge" — workflow tiếp theo

## Tính đúng đắn (smoke test thủ công)

Chạy từng lệnh sau trong Git Bash, repo root. Phải thành công với exit code 0:

```bash
# Smoke test 1: script tồn tại và executable
test -x scripts/discovery/static-analysis.sh && echo OK1
test -x scripts/discovery/repo-analysis.sh && echo OK2
test -x scripts/discovery/context-analysis.sh && echo OK3
```

```bash
# Smoke test 2: chạy static-analysis, output phải là JSON hợp lệ (nếu có jq)
./scripts/discovery/static-analysis.sh | jq -e '.counts' > /dev/null && echo "STATIC_OK"
```

```bash
# Smoke test 3: chạy repo-analysis, output phải có field `.git`
./scripts/discovery/repo-analysis.sh | jq -e '.git.branch' > /dev/null && echo "REPO_OK"
```

```bash
# Smoke test 4: chạy context-analysis, output phải có field `.docs`
./scripts/discovery/context-analysis.sh | jq -e '.docs.total_files' > /dev/null && echo "CONTEXT_OK"
```

```bash
# Smoke test 5: chạy với TARGET_ROOT trỏ vào thư mục KHÔNG tồn tại
./scripts/discovery/static-analysis.sh /nonexistent; echo "exit=$?"
# Kỳ vọng: "ERROR: ..." trên stderr + exit=1
```

- [ ] Smoke test 1 PASS
- [ ] Smoke test 2 PASS (nếu có jq)
- [ ] Smoke test 3 PASS (nếu có jq)
- [ ] Smoke test 4 PASS (nếu có jq)
- [ ] Smoke test 5 PASS (exit code = 1)

## Git hygiene

- [ ] Tất cả file mới được `git add` đúng cách
- [ ] Commit message theo convention (Conventional Commits):
  - `chore(discovery): add 3 discovery scripts`
  - `docs(pr): add discovery report template`
  - `docs(pr2): add README and checklist`
- [ ] `git status` clean sau commit
- [ ] PR description link tới file này (`docs/pr/pr2-checklist.md`)
- [ ] PR description giải thích "KHÔNG merge nếu checklist chưa tick hết"
- [ ] Đã chạy `mvnw compile` (backend) và `tsc --noEmit` (frontend) — dù PR không đụng source — để xác minh working tree không bị hỏng từ lần trước

## Quyết định cuối (sign-off)

- [ ] Reviewer đã đọc 6 file trong PR
- [ ] Reviewer đã chạy smoke test 1–5
- [ ] Reviewer đồng ý rằng PR này KHÔNG thực hiện cleanup — chỉ chuẩn bị tooling
- [ ] Reviewer đồng ý rằng 3 scripts đủ dùng cho buổi Discovery kế tiếp
- [ ] Merge vào `main` bằng `--no-ff` (giữ lại merge commit cho audit trail)