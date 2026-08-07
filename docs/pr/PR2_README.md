# PR2 — Discovery & Tooling (README)

> **Trạng thái PR**: Mở — chưa chạy discovery lần nào.
> **Branch**: `chore/pr2-discovery-tooling`
> **Base**: `main`
> **Mục đích**: Chuẩn bị công cụ (tooling) và template để từ đây trở đi có thể
> chạy Discovery một cách tái lập được (reproducible). **PR này không sửa
> source code, không xoá dead code, không chạy discovery.**

## Tại sao PR2 tồn tại

Trước PR3, việc khảo sát codebase được làm thủ công — đọc file, grep, đoán.
Sau PR3, ta nhận ra:

- Cần một cách **tái lập** (re-runnable) để chạy Discovery bất cứ lúc nào.
- Cần một cách **có cấu trúc** (structured) để output của Discovery trở thành
  input cho buổi Candidate Review.
- Cần tách bạch "công cụ" (tooling) khỏi "hành động" (cleanup batches). Một
  PR chỉ chứa công cụ; các hành động cleanup sẽ là Batch A/B/C/D — mỗi cái
  là một PR riêng.

PR2 sinh ra để giải quyết đúng 3 nhu cầu trên. Sau khi merge PR2, sequence
tiếp theo sẽ là:

```
Run Discovery
        ↓
Generate Report (theo template)
        ↓
Candidate Review (dùng report quyết định)
        ↓
Batch A
Batch B
Batch C
Batch D
```

## Phạm vi (Scope) của PR2

### Có

- **3 discovery scripts** trong `scripts/discovery/`:
  - `static-analysis.sh` — đếm dòng, tìm hot spots, quét TODO/FIXME.
  - `repo-analysis.sh` — git snapshot, layout, dead-code hint (heuristic).
  - `context-analysis.sh` — inventory docs, migrations, openapi.
- **1 template báo cáo** ở `docs/pr/templates/pr-discovery-report.md`.
- **README này** — hướng dẫn chạy scripts, đọc output.
- **Checklist** ở `docs/pr/pr2-checklist.md` — Definition of Done cho PR2.

### KHÔNG có

- ❌ Không sửa source code backend (`backend/src/**`).
- ❌ Không sửa source code frontend (`frontend/src/**`).
- ❌ Không xoá dead code, dù là 1 file.
- ❌ Không refactor business logic.
- ❌ Không chạy discovery lần nào để generate report.
- ❌ Không tạo file discovery output thật (`/tmp/*.json`).

## Cách chạy

### Yêu cầu

| Tool | Version | Bắt buộc? |
|---|---|---|
| `bash` | 4+ | Bắt buộc (Git Bash trên Windows OK) |
| `jq` | 1.6+ | Khuyến nghị — có fallback nếu thiếu nhưng output kém an toàn |
| `git` | 2.x | Bắt buộc (cho `repo-analysis.sh`) |
| `find`, `grep`, `xargs`, `wc` | POSIX | Bắt buộc (có sẵn trên Git Bash / macOS / Linux) |

### Cài jq (khuyến nghị)

```bash
# macOS
brew install jq

# Ubuntu / Debian
sudo apt-get install -y jq

# Windows (Git Bash)
winget install jqlang.jq
# hoặc tải binary từ https://stedolan.github.io/jq/download/
```

### Chạy từng script

```bash
# Từ repo root
./scripts/discovery/static-analysis.sh > /tmp/static.json
./scripts/discovery/repo-analysis.sh    > /tmp/repo.json
./scripts/discovery/context-analysis.sh > /tmp/context.json

# Xem output
jq . /tmp/static.json    # JSON đẹp, có highlight
jq . /tmp/repo.json
jq . /tmp/context.json
```

### Chạy cả 3 trong một shot

```bash
mkdir -p build/discovery
./scripts/discovery/static-analysis.sh > build/discovery/static.json
./scripts/discovery/repo-analysis.sh    > build/discovery/repo.json
./scripts/discovery/context-analysis.sh > build/discovery/context.json
```

### Tạo discovery report từ output

1. Copy template: `cp docs/pr/templates/pr-discovery-report.md docs/pr/pr2-discovery-report.md`
2. Mở file mới, paste output từ 3 JSON vào các phần tương ứng (section 1–8).
3. Điền section 9 (Tóm tắt) và section 10 (Quyết định) bằng tay.

## Output mẫu (rút gọn)

Đây là shape của mỗi file JSON. **Không phải output thật** — chỉ là khung để
reviewer biết cần đọc gì.

### `static.json`

```json
{
  "generated_at": "2026-08-08T...",
  "target_root": "E:/DACN/business-trip-management",
  "counts": {
    "backend_main_java_files": 0,
    "backend_main_java_lines": 0,
    "backend_test_java_files": 0,
    "backend_test_java_lines": 0,
    "frontend_src_files": 0,
    "frontend_src_lines": 0
  },
  "hotspots_top10": [
    { "path": "backend/src/.../BigFile.java", "lines": 1234 }
  ],
  "todos": [
    { "path": "...", "line": 42, "text": "TODO: ..." }
  ]
}
```

### `repo.json`

```json
{
  "generated_at": "...",
  "git": {
    "branch": "main",
    "head": "...",
    "head_short": "...",
    "tracking": "origin/main",
    "ahead": 0,
    "behind": 0,
    "working_tree_status": [],
    "recent_commits": [...],
    "branches": [...],
    "remotes": [...]
  },
  "layout": {
    "top_level": [...],
    "backend_packages": [...],
    "frontend_routes": [...]
  },
  "dead_hints": []
}
```

### `context.json`

```json
{
  "generated_at": "...",
  "docs": {
    "total_files": 0,
    "total_bytes": 0,
    "top10_largest": [...],
    "subdirs": [...],
    "recent_30d": [...],
    "spec_files": [...],
    "audit_files": [...],
    "contract_files": [...],
    "release_files": [...]
  },
  "migrations": { "backend_recent_20": [...] },
  "openapi": null
}
```

## Phạm vi bỏ qua (cố định)

Mọi discovery script đều bỏ qua:

- `target/`, `build/`, `dist/`, `out/`, `node_modules/`
- `.git/`, `.cursor/`, `.trellis/`
- `*.lock`, `*.min.js`, `*.min.css`
- File nhị phân: `*.png`, `*.jpg`, `*.jpeg`, `*.gif`, `*.webp`, `*.ico`,
  `*.pdf`, `*.docx`, `*.pptx`, `*.xlsx`

Nếu sau này cần scan thêm thư mục (vd: `frontend/public/`), sửa từng script —
đừng sửa chỗ này rồi quên sửa chỗ khác.

## Thoát code (exit codes)

Mọi script:

- `0` = thành công (kể cả khi phát hiện 0 TODO, 0 hot spot, v.v.).
- `1` = lỗi nghiêm trọng (TARGET_ROOT không tồn tại, không phải git repo, jq không có khi strict mode).

Hiện tại các script KHÔNG có strict mode cho jq — chúng chỉ warn. Nếu muốn
CI/CD fail khi thiếu jq, chỉnh `have_jq` thành `exit 1` thay vì `warn`.

## Troubleshooting

| Triệu chứng | Nguyên nhân | Cách xử |
|---|---|---|
| `find: paths must precede expression` | Phiên bản `find` cũ không hỗ trợ `-regextype posix-extended` | Bỏ flag đó trong script hoặc nâng cấp find (GNU findutils) |
| Output JSON parse fail | Thiếu `jq` và fallback gặp ký tự đặc biệt | Cài `jq` rồi chạy lại |
| `working_tree_status` rỗng dù có file modified | Không phải git repo | `cd` vào repo root rồi chạy lại |
| `dead_hints` liệt kê cả entry point (HospitalSchedulerApplication, ...) | Heuristic chỉ loại trừ một số well-known, không phải tất cả | Bổ sung case vào nhánh `case "$base" in` của `repo-analysis.sh` |
| Số dòng file `*.java` = 0 dù có file | Shell glob mismatch giữa PowerShell và Git Bash | Luôn chạy script qua Git Bash trên Windows, không qua PowerShell |

## Sau khi merge

Sau khi PR2 được merge vào main:

1. Tạo PR mới `chore/pr2-discovery-run` (hoặc đặt tên khác) chỉ chứa:
   - Output JSON thật từ 3 scripts (commit vào `build/discovery/` hoặc attach vào PR).
   - File `docs/pr/pr2-discovery-report.md` đã điền (theo template).
2. Reviewer chạy buổi Candidate Review dựa trên report.
3. Quyết định các batch (A/B/C/D).
4. Mỗi batch là một PR riêng, scope nhỏ, dễ review.

## Liên kết

- [Discovery Report Template](./templates/pr-discovery-report.md)
- [PR2 Checklist](./pr2-checklist.md)
- [Project Context](../../PROJECT_CONTEXT.mdc) (workspace rule)