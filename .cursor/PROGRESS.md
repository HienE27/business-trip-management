# Hospital Scheduler - Development Progress

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

## MCP Setup
| Tool | Trạng thái | Ghi chú |
|------|------------|---------|
| Figma | ✅ Hoạt động | Thiết kế UI |
| GitLens | ✅ Hoạt động | Git history |
| GitHub | ⚠️ Cần token | PR, issues, repo management |
| Filesystem | ⚠️ Cần npx | File operations |
| Git | ⚠️ Cần uvx | Git operations |
| Fetch | ⚠️ Cần npx | Web fetching |
| Memory | ⚠️ Cần npx | Knowledge graph |
| Time | ⚠️ Cần npx | Time utilities |
| Stitch | ⏳ Chờ MCP | https://stitch.withgoogle.com - chưa có MCP |

## MCP Config
```json
// .mcp.json - nằm trong project root
{
  "mcpServers": {
    "github": { "command": "npx", "args": ["@modelcontextprotocol/server-github"] },
    "filesystem": { "command": "npx", "args": ["@modelcontextprotocol/server-filesystem"] },
    "git": { "command": "uvx", "args": ["mcp-server-git"] },
    "fetch": { "command": "npx", "args": ["@modelcontextprotocol/server-fetch"] },
    "memory": { "command": "npx", "args": ["@modelcontextprotocol/server-memory"] },
    "time": { "command": "npx", "args": ["@modelcontextprotocol/server-time"] }
  }
}
```

## TODO
- [x] Setup MCP servers config (.mcp.json)
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
