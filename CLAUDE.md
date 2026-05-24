# CLAUDE.md - Hướng dẫn làm việc với dự án

## Thông tin dự án

- **Tên**: Quản lý Lịch Công Tác (Hospital Scheduler)
- **Phiên bản**: 1.1
- **Nhóm**: Nhóm 4
- **Giảng viên hướng dẫn**: ThS. Văn Minh Hoàng Quân

## Công nghệ sử dụng

- **Backend**: Java Spring Boot + MySQL + REST API
- **Frontend**: React/Vue (tương lai)
- **Database**: MySQL với charset utf8mb4
- **Repository**: https://github.com/tmHieu20-02/business-trip-management

## Cấu trúc dự án

```
business-trip-management/
├── backend/              # Spring Boot project
├── frontend/             # React/Vue project (future)
├── .claude/              # AI configuration
│   ├── settings.json     # Cấu hình project
│   ├── commands/         # Custom slash commands
│   ├── skills/           # Auto-invoked workflows
│   └── agents/           # Subagent personas
├── .cursor/              # Cursor-specific rules
│   └── rules/            # Cursor AI rules
├── SPEC.md               # Tài liệu đặc tả chi tiết
├── hospital_scheduler_business_final.sql  # Database schema
└── QuanLyLichCongTac_v5.txt  # Tài liệu gốc
```

## Luôn nhớ khi làm việc

### 1. Các loại lịch
- **L01**: Lịch trực 24/24 (7h30 ngày N → 7h30 ngày N+1)
- **L02**: Lịch thông tầm
- **L03**: Lịch phòng khám dịch vụ
- **L04**: Lịch phòng khám chuyên gia

### 2. Ràng buộc quan trọng
- L01 + L02 cùng ngày = KHÔNG ĐƯỢC
- L03 + L04 cùng ngày = KHÔNG ĐƯỢC
- Ngày nghỉ bù = KHÔNG ĐƯỢC xếp lịch

### 3. Quy tắc nghỉ bù
| Trực | Nghỉ bù |
|-------|----------|
| T2-T5 | Ngày hôm sau |
| T6-T7 | T3 tuần sau |
| CN | T2 tuần sau |

### 4. Quyền hệ thống
- **ADMIN**: Toàn quyền
- **MANAGER**: Xếp lịch, duyệt đổi ca
- **STAFF**: Xem lịch, gửi yêu cầu

## Commands có sẵn

| Command | Mô tả |
|---------|--------|
| `/check-conflict` | Kiểm tra xung đột lịch |
| `/gen-api` | Tạo REST API controller |
| `/gen-entity` | Tạo JPA entity |
| `/gen-service` | Tạo service layer |
| `/gen-dto` | Tạo DTO class |
| `/validate-schedule` | Validate lịch theo business rules |
| `/audit-log` | Ghi audit log |
| `/status` | Kiểm tra tiến độ dự án |

## Qui ước coding

### Java (Backend)
- Package: `com.hospital.scheduler`
- Entity: PascalCase, có suffix `Entity`
- Repository: Interface với `JpaRepository`
- Service: PascalCase, có suffix `Service`
- Controller: PascalCase, có suffix `Controller`
- DTO: PascalCase, có suffix `DTO`

### Database
- Table names: snake_case
- Columns: snake_case
- Primary key: `id INT AUTO_INCREMENT`
- Foreign key: `fk_` prefix

### Git
- Branch: `feature/xxx`, `fix/xxx`, `docs/xxx`
- Commit message: `[M01-F01] Add feature description`

## Tiến độ dự án

- [x] Database schema design
- [x] SPEC.md documentation
- [ ] Backend API development
- [ ] Frontend development
- [ ] Testing
- [ ] Deployment

## Liên hệ

- GitHub: https://github.com/tmHieu20-02/business-trip-management
