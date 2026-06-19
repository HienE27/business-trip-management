# Hướng dẫn Trellis cho team Hospital Scheduler

> Hướng dẫn sử dụng [Trellis](https://github.com/mindfold-ai/trellis) trong dự án **Hospital Scheduler** — dành cho thành viên mới và người muốn hiểu workflow.

---

## 1. Trellis là gì?

Trellis là một framework "AI coding workflow" giúp:

- **Chuẩn hóa quy trình**: 3 pha `Plan → Execute → Finish` cho mọi tác vụ
- **Tự động inject quy ước team**: AI agents luôn biết convention của dự án mà không cần paste
- **Task tracking + journal**: lịch sử làm việc được lưu trong repo
- **Phối hợp team**: mỗi người có workspace riêng, task có owner rõ ràng

Xem thêm: [Trellis GitHub](https://github.com/mindfold-ai/trellis) | [Docs chính thức](https://docs.trytrellis.app/)

---

## 2. Đã cài đặt trong dự án này

| Thành phần | Trạng thái | Vị trí |
|---|---|---|
| Trellis CLI | ✅ Cài global | `trellis --version` → `0.6.0` |
| Trellis core | ✅ Khởi tạo | `.trellis/` |
| Cursor integration | ✅ Tự động | `.cursor/commands/trellis-*.md`, `.cursor/agents/trellis-*.md`, `.cursor/skills/trellis-*/`, `.cursor/hooks/`, `.cursor/hooks.json` |
| Spec library (13 layers) | ✅ Custom cho dự án | `.trellis/spec/backend/`, `.trellis/spec/frontend/`, `.trellis/spec/guides/` |
| Workflow customized | ✅ Vietnamese + domain-specific | `.trellis/workflow.md` |
| Monorepo packages | ✅ `backend` + `frontend` | `.trellis/config.yaml` |
| Cursor rule | ✅ `TRELLIS_WORKFLOW.mdc` | `.cursor/rules/` |

Tài liệu dự án tùy biến:
- `.cursor/rules/PROJECT_CONTEXT.mdc` — context dự án (đã có sẵn)
- `.cursor/rules/FRONTEND_UI_SYSTEM.mdc` — design system (đã có sẵn)
- `.cursor/rules/TRELLIS_WORKFLOW.mdc` — hướng dẫn Trellis (mới thêm)
- `.cursor/agents/` — 6 domain agent + 3 Trellis agent
- `.cursor/skills/` — 7 domain skill + 5 Trellis skill
- `.cursor/commands/` — 14 domain command + 2 Trellis command

---

## 3. Cài đặt Trellis (nếu chưa có)

### Yêu cầu

| Tool | Version | Kiểm tra |
|---|---|---|
| Node.js | ≥ 18 | `node --version` |
| Python | ≥ 3.9 | `python --version` (Windows) hoặc `python3 --version` |
| Git | bất kỳ | `git --version` |

### Cài CLI

```bash
npm install -g @mindfoldhq/trellis@latest
```

Sau khi cài, kiểm tra:

```bash
trellis --version
# 0.6.0 (hoặc mới hơn)
```

### Khởi tạo identity (chỉ làm 1 lần trên máy của bạn)

Nếu là thành viên mới clone repo này về:

```bash
cd /path/to/business-trip-management
trellis init -u <tên-bạn> --cursor -y
```

Ví dụ: `trellis init -u hien --cursor -y`.

CLI sẽ tự detect repo đã có `.trellis/`, hỏi "Add AI platform(s)?" → chọn Cursor nếu cần.

> Nếu bạn KHÔNG dùng Cursor (VD: dùng Claude Code), thêm flag tương ứng: `--claude`, `--codex`, `--copilot`, … (xem `trellis init --help`).

### Khởi động lại Cursor

Sau khi cài Trellis, restart Cursor để các hook load đúng. Kiểm tra:

`Ctrl+Shift+P` → `MCP Servers` (không liên quan Trellis trực tiếp)  
`Ctrl+Shift+P` → `Reload Window`

---

## 4. Workflow cho thành viên mới

### Bước 1: Đọc context

Trước khi bắt đầu, đọc:

- `README.md` (root) — tổng quan dự án
- `.cursor/rules/PROJECT_CONTEXT.mdc` — business rules
- `.cursor/rules/TRELLIS_WORKFLOW.mdc` — workflow Trellis
- `.cursor/PROGRESS.md` — tiến độ module

### Bước 2: Chọn task

Mở Cursor, gõ:

```
/trellis-start
```

Cursor sẽ load context Trellis + danh sách task. Bạn có thể:

- Tạo task mới (nếu biết cần làm gì)
- Tiếp tục task đang dở
- Xem danh sách task của mình

### Bước 3: Tạo task mới (nếu cần)

Ví dụ: Bạn muốn thêm API "Approve Leave Request".

Nói với AI:

```
Tôi cần thêm API duyệt LeaveRequest cho manager.
```

AI sẽ:

1. Hỏi: "Bạn muốn tôi tạo Trellis task cho việc này không?" → Bạn: "Có"
2. Chạy `python ./.trellis/scripts/task.py create "API duyệt LeaveRequest" --slug api-approve-leave`
3. Load `trellis-brainstorm` skill, hỏi 1 câu mỗi lượt để hiểu rõ yêu cầu
4. Ghi vào `.trellis/tasks/MM-DD-api-approve-leave/prd.md`
5. (Nếu phức tạp) tạo thêm `design.md`, `implement.md`
6. Hỏi review → Bạn OK → chạy `task.py start` → status = `in_progress`
7. Dispatch `trellis-implement` agent viết code theo prd + spec
8. Dispatch `trellis-check` agent review + self-fix
9. Bạn review code → commit
10. Chạy `/trellis-finish-work` để archive + ghi journal

### Bước 4: Nếu là task nhỏ (< 30 phút)

Có thể làm inline mà không cần tạo Trellis task. Ví dụ:

- Sửa typo
- Đổi text label
- Fix bug 1 dòng
- Thêm field vào DTO

AI sẽ tự hỏi "Task này có cần tạo Trellis task không?" — bạn có thể nói "Không, làm luôn".

### Bước 5: Commit + Finish

Sau khi code xong, bạn commit bình thường (qua git). Sau đó chạy:

```
/trellis-finish-work
```

Lệnh này sẽ:

- Archive task vào `.trellis/tasks/archive/YYYY-MM/`
- Ghi nhật ký vào `.trellis/workspace/<tên-bạn>/journal-N.md`

---

## 5. Cấu trúc thư mục Trellis

```
.trellis/
├── workflow.md              # Workflow contract (Plan/Execute/Finish)
├── config.yaml              # Monorepo packages, session config
├── .developer               # Gitignored: tên dev hiện tại
├── .version                 # Version Trellis
├── spec/                    # Quy ước team (auto-injected)
│   ├── guides/
│   ├── backend/
│   └── frontend/
├── tasks/                   # Tất cả task
│   ├── MM-DD-task-name/
│   │   ├── task.json
│   │   ├── prd.md
│   │   ├── design.md        (optional)
│   │   ├── implement.md     (optional)
│   │   ├── implement.jsonl  (spec manifest cho implement agent)
│   │   ├── check.jsonl      (spec manifest cho check agent)
│   │   └── research/        (optional)
│   └── archive/
│       └── YYYY-MM/
├── workspace/               # Journal cá nhân
│   ├── index.md
│   └── <tên-bạn>/
│       ├── index.md
│       └── journal-N.md
└── scripts/                 # CLI scripts (Python)
    ├── task.py              # Task management
    ├── get_context.py       # Session context
    └── ...
```

---

## 6. Tích hợp với Cursor

### Hooks tự động

Cursor sẽ tự động:

- **SessionStart**: Inject context Trellis (developer, git state, active task, spec indexes)
- **PreToolUse (Task/Subagent)**: Inject spec + task artifacts vào `trellis-implement`, `trellis-check`, `trellis-research`

### Skills có sẵn

Trong `.cursor/skills/`:

| Skill | Mục đích | Trigger |
|---|---|---|
| `trellis-brainstorm` | Hỏi requirement từng câu một | User mô tả feature mới |
| `trellis-before-dev` | Checklist trước khi code | Sắp edit code trong active task |
| `trellis-check` | Self-verify sau khi code | Vừa implement xong |
| `trellis-update-spec` | Cập nhật spec với kiến thức mới | Phát hiện pattern mới |
| `trellis-break-loop` | Phân tích bug khó | Cùng bug bị fix nhiều lần |
| `trellis-spec-bootstrap` | (advanced) Bootstrap spec từ codebase | Tạo spec cho dự án mới |
| `trellis-session-insight` | (advanced) Tìm kiếm qua nhiều session | Tìm context cũ |
| `trellis-channel` | (advanced) Điều phối nhiều agent | Tác vụ parallel |
| `trellis-meta` | Tùy biến Trellis | Sửa workflow/spec |

### Agents có sẵn

Trong `.cursor/agents/`:

| Agent | Vai trò |
|---|---|
| `trellis-implement` | Viết code theo prd + spec (KHÔNG commit) |
| `trellis-check` | Review code, self-fix (KHÔNG commit) |
| `trellis-research` | Tìm research, ghi vào `research/` (KHÔNG sửa code) |
| `backend-developer` | Spring Boot / Java |
| `frontend-developer` | Next.js / React |
| `database-admin` | MySQL / schema |
| `algorithm-specialist` | M07 Auto scheduling |
| `qa-engineer` | Testing / quality |
| `devops-engineer` | Docker / CI/CD |

### Commands có sẵn

Trong `.cursor/commands/`:

| Command | Mục đích |
|---|---|
| `/trellis-start` | Bắt đầu task / session |
| `/trellis-continue` | Tiếp tục task đang dở |
| `/trellis-finish-work` | Archive + journal |
| `/gen-api` | Generate REST controller |
| `/gen-entity` | Generate JPA entity |
| `/gen-service` | Generate service |
| `/gen-dto` | Generate DTO |
| `/gen-repository` | Generate repository |
| `/gen-api-client` | Generate FE API client |
| `/gen-component` | Generate React component |
| `/new-page` | Generate Next.js page |
| `/check-conflict` | Check schedule conflict |
| `/validate-schedule` | Validate theo rules |
| `/test-api` | Test API |
| `/api-docs` | Setup Swagger |
| `/security-setup` | Setup JWT |
| `/docker-setup` | Setup Docker |
| `/ci-cd` | Setup GitHub Actions |

---

## 7. Convention cho team

### Commit message

Dùng [Conventional Commits](https://www.conventionalcommits.org/) tiếng Việt/Anh:

```
feat(schedule): thêm API duyệt LeaveRequest
fix(compensation): sửa tính năng lùi ngày nghỉ bù thứ 6
docs(readme): cập nhật hướng dẫn Trellis
refactor(staff): tách StaffService.create thành validate + persist
test(schedule): thêm test conflict L01 vs L02
chore(deps): cập nhật springdoc lên 3.0.3
```

**Scope**: `schedule`, `staff`, `auth`, `leave`, `exchange`, `compensation`, `holiday`, `auto-scheduling`, `notification`, `audit`, `docs`, `config`, `deps`.

### Branch

```
main
 └── develop
      ├── backend/leave-request-api
      ├── backend/compensation-fix
      ├── frontend/auth-ui
      ├── frontend/swap-requests
      └── docs/trellis-setup
```

### Task ownership

Mỗi task trong `.trellis/tasks/` có:

- `creator`: người tạo
- `assignee`: người thực hiện (mặc định = creator)

Set assignee rõ ràng để team biết ai đang làm gì:

```bash
python ./.trellis/scripts/task.py set-scope MM-DD-task-name schedule
# (sửa task.json trực tiếp để set assignee nếu cần)
```

### Khi nào KHÔNG tạo task

- Sửa typo
- Cập nhật comment
- Format code
- Đổi env var
- Task < 15 phút

Cứ commit trực tiếp, không cần Trellis overhead.

### Khi nào NÊN tạo task

- Tác vụ ảnh hưởng > 1 file
- Có business rule
- Có thay đổi API / DB
- Cần test riêng
- Task > 30 phút

---

## 8. Cập nhật Trellis

Khi Trellis phát hành version mới:

```bash
npm install -g @mindfoldhq@trellis@latest
cd /path/to/business-trip-management
trellis update
trellis update --migrate  # nếu có breaking change
```

Xem [changelog](https://docs.trytrellis.app/changelog/).

---

## 9. Troubleshooting

### Trellis hook không fire

**Triệu chứng**: Cursor không tự load context Trellis, sub-agent không nhận spec.

**Fix**:
1. Kiểm tra `.cursor/hooks.json` tồn tại
2. Restart Cursor (`Ctrl+Shift+P` → Reload Window)
3. Kiểm tra Python: `python ./.trellis/scripts/get_context.py` chạy OK không
4. Xem log Cursor: `View` → `Output` → `Cursor` channel

### Spec không được inject

**Triệu chứng**: Sub-agent viết code không theo convention.

**Fix**:
1. Kiểm tra `implement.jsonl` / `check.jsonl` có entry không
2. Dùng `python ./.trellis/scripts/task.py validate <task-name>` để check
3. Nếu jsonl chỉ có seed `_example` row → chưa curate, cần thêm entry thật

### Task `start` báo lỗi session identity

**Triệu chứng**: `task.py start` fail với "no context key".

**Fix**:
1. Mở Cursor session (không phải CLI bash trực tiếp)
2. Restart Cursor, mở lại project
3. Hoặc set env: `TRELLIS_CONTEXT_ID=<session-id>`

### Muốn tắt Trellis tạm thời

Set env: `TRELLIS_HOOKS=0` hoặc `TRELLIS_DISABLE_HOOKS=1`. Hooks sẽ skip injection.

---

## 10. Tài liệu tham khảo

- **Trellis GitHub**: https://github.com/mindfold-ai/trellis
- **Trellis Docs**: https://docs.trytrellis.app/
- **Workflow contract**: `.trellis/workflow.md` (đã customize)
- **Spec library**: `.trellis/spec/` (đã customize)
- **Cursor rule mới**: `.cursor/rules/TRELLIS_WORKFLOW.mdc`
- **Project context**: `.cursor/rules/PROJECT_CONTEXT.mdc`
- **Frontend design system**: `.cursor/rules/FRONTEND_UI_SYSTEM.mdc`

---

## 11. Câu hỏi thường gặp

**Q: Tôi có cần dùng Trellis cho mọi task không?**
A: Không. Task nhỏ (< 30 phút) cứ làm inline. Task lớn hoặc có business rule mới → dùng Trellis.

**Q: Trellis có thay thế git không?**
A: Không. Trellis bổ sung cho git, không thay thế. Bạn vẫn commit qua git bình thường.

**Q: Tôi có cần tạo task cho mỗi PR?**
A: Không bắt buộc, nhưng khuyến nghị cho task > 30 phút. PR vẫn tạo bình thường qua GitHub.

**Q: Ai có thể đọc journal của tôi?**
A: Mọi người trong team đều thấy. KHÔNG ghi thông tin nhạy cảm vào journal.

**Q: Spec có được review không?**
A: Có — bất kỳ thay đổi nào trong `.trellis/spec/` đều phải qua PR review.

**Q: Tôi dùng VS Code thay vì Cursor?**
A: Cài Trellis vẫn được, nhưng hooks Cursor-specific sẽ không fire. Có thể dùng `.agents/skills/` (chuẩn agentskills.io) — xem [Trellis docs](https://docs.trytrellis.app/advanced/multi-platform).

**Q: Tôi dùng Claude Code?**
A: `trellis init -u <tên> --claude` để setup.

---

**Tạo bởi**: Setup tự động qua `trellis init`  
**Ngày**: 2026-06-17  
**Version**: Trellis 0.6.0  
**Ngôn ngữ**: Tiếng Việt