# Hospital Scheduler - Development Progress

## Trellis Workflow

Repo này dùng [Trellis](https://github.com/mindfold-ai/trellis) cho task management + spec library. Đọc `.cursor/rules/TRELLIS_WORKFLOW.mdc` để biết chi tiết.

Workflow 3 pha: **Plan** → **Execute** → **Finish**.

```bash
# Tạo task mới
python ./.trellis/scripts/task.py create "Tên task" --slug ten-task

# Bắt đầu implement (sau khi review prd.md)
python ./.trellis/scripts/task.py start .trellis/tasks/MM-DD-ten-task

# Hoàn thành
python ./.trellis/scripts/task.py archive .trellis/tasks/MM-DD-ten-task
```

Spec library tại `.trellis/spec/`: mỗi agent (trellis-implement, trellis-check) tự load spec tương ứng với package.

## Branch Convention
```
main (production)
 └── develop (tích hợp)
      ├── backend/
      │    ├── leave-request       → API nghỉ phép
      │    ├── schedule-exchange  → API đổi ca
      │    └── compensation       → API nghỉ bù
      └── frontend/
           ├── auth-ui            → Login/logout UI
           ├── schedule-calendar  → Calendar view
           └── dashboard          → Dashboard UI
```

## Backend Team (5 người)
| Module | Branch | Trạng thái | Người phụ trách |
|--------|--------|------------|-----------------|
| Auth, Staff, Specialty, ShiftType, Schedule | `develop` | ✅ Hoàn thành | Hieu |
| Leave Request | `backend/leave-request` | ⏳ Chưa làm | - |
| Schedule Exchange | `backend/schedule-exchange` | ⏳ Chưa làm | - |
| Compensation Day | `backend/compensation` | ⏳ Chưa làm | - |
| Notification | - | ⏳ Chưa tạo branch | - |
| Dashboard Stats | - | ⏳ Chưa tạo branch | - |
| Auto Scheduling | - | ⏳ Chưa tạo branch | - |

## Frontend Team (5 người)
| Module | Branch | Trạng thái | Người phụ trách |
|--------|--------|------------|-----------------|
| Setup Next.js | - | ⏳ Chưa làm | - |
| Auth UI | `frontend/auth-ui` | ⏳ Chưa làm | - |
| Schedule Calendar | `frontend/schedule-calendar` | ⏳ Chưa làm | - |
| Dashboard UI | `frontend/dashboard` | ⏳ Chưa làm | - |

## Credentials
| Username | Password | Roles |
|----------|----------|-------|
| admin | admin123 | ADMIN, MANAGER |
| staff1 | 123456 | STAFF |

## API Base URL
```
http://localhost:8080/api/v1
```

## Swagger
```
http://localhost:8080/swagger-ui/index.html
```

## MCP Setup - Cursor (KHÔNG dùng .mcp.json)

### Cách thêm MCP trong Cursor
1. Mở **Cursor Settings** → tìm **MCP Servers**
2. Hoặc **Ctrl + Shift + P** → gõ `MCP Servers`

### MCP Servers khả dụng
| MCP | Package | Cài qua |
|-----|---------|---------|
| GitHub | `@modelcontextprotocol/server-github` | Settings → MCP Servers |
| Filesystem | `@modelcontextprotocol/server-filesystem` | Settings → MCP Servers |
| Git | `mcp-server-git` (uvx) | Settings → MCP Servers |
| Fetch | `@modelcontextprotocol/server-fetch` | Settings → MCP Servers |
| Memory | `@modelcontextprotocol/server-memory` | Settings → MCP Servers |
| Time | `@modelcontextprotocol/server-time` | Settings → MCP Servers |

### MCP có sẵn trong project
| MCP | Trạng thái |
|-----|------------|
| Figma | ✅ Extension |
| GitLens | ✅ Extension |
| Browser | ✅ Built-in |

### Stitch
| Tool | Trạng thái |
|------|------------|
| Stitch | ⏳ Chưa có MCP | https://stitch.withgoogle.com |

## TODO
- [x] Document MCP setup guide
- [ ] Setup GitHub MCP (cần token)
- [ ] Setup frontend Next.js project
- [ ] Backend: Leave Request API
- [ ] Backend: Schedule Exchange API
- [ ] Backend: Notification API
- [ ] Frontend: Auth UI (login/logout)
- [ ] Frontend: Schedule Calendar view
- [ ] Frontend: Dashboard
- [ ] Auto Scheduling algorithm
- [ ] Write unit tests
- [ ] CI/CD pipeline
