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
| GitHub | ✅ Sẵn sàng | Quản lý PR, issues |
| Stitch | ⏳ Chờ MCP | https://stitch.withgoogle.com - AI design tool, chưa có MCP |

## TODO
- [ ] Setup GitHub MCP
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
