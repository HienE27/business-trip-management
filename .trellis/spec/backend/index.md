# Backend Development Guidelines

> Quy ước phát triển backend Spring Boot 4 / Java 17 / MySQL cho **Hospital Scheduler**.

---

## Overview

Tài liệu này mô tả cách backend **thực sự** được viết trong repo này. Mọi thay đổi của `trellis-implement` và `trellis-check` đều phải tuân theo.

Package root: `com.hospital.scheduler` (artifact `backend`, Spring Boot `4.0.6`, Java 17).

---

## Tech Stack (đã chốt)

| Layer | Library | Ghi chú |
|---|---|---|
| Framework | Spring Boot `4.0.6` | Parent POM từ `spring-boot-starter-parent` |
| Persistence | Spring Data JPA + Hibernate | `spring-boot-starter-data-jpa` |
| Auth | Spring Security + JWT (jjwt `0.12.3`) | `JwtAuthenticationFilter` tự viết |
| API docs | springdoc-openapi `3.0.3` | `/swagger-ui.html` |
| Validation | Jakarta Validation | `@Valid` trên controller |
| Lombok | ✅ enabled | `@Getter @Setter @Builder @RequiredArgsConstructor` |
| Build | Maven Wrapper (`mvnw` / `mvnw.cmd`) | KHÔNG cài Maven global |

---

## Guidelines Index

| Layer | File | Mô tả | Trạng thái |
|---|---|---|---|
| Directory Structure | [directory-structure/index.md](./directory-structure/index.md) | Tổ chức package `com.hospital.scheduler.*` | ✅ Filled |
| Database | [database/index.md](./database/index.md) | JPA, indexes, migrations, query patterns | ✅ Filled |
| Errors | [errors/index.md](./errors/index.md) | Exception classes, `ApiResponse`, status codes | ✅ Filled |
| Quality | [quality/index.md](./quality/index.md) | Lint, test, code review, anti-patterns | ✅ Filled |
| Logging | [logging/index.md](./logging/index.md) | SLF4J, format, audit history | ✅ Filled |
| Business Rules | [business-rules/index.md](./business-rules/index.md) | L01–L04, conflict, compensation | ✅ Filled (CRITICAL) |

---

## Quy trình thay đổi spec

| Khi nào | Hành động |
|---|---|
| Phát hiện convention mới chưa document | Thêm vào file spec tương ứng + cập nhật `index.md` nếu cần |
| Phát hiện code vi phạm spec hiện tại | Tạo task fix riêng; KHÔNG tự sửa trong task khác |
| Quyết định kỹ thuật mới (thư viện, pattern) | Update spec trước, code sau |
| Thay đổi business rule (L01–L04, nghỉ bù) | BẮT BUỘC update `business-rules/index.md` + viết unit test |

---

## Lưu ý cho AI agents

- **Tuân thủ spec tuyệt đối** khi viết code mới. Không "sáng tạo" pattern mới.
- **KHÔNG** dùng `Optional.orElseThrow()` trong controller — dùng custom exception.
- **KHÔNG** commit message tiếng Anh dài dòng. Team dùng Conventional Commits tiếng Việt/Anh ngắn gọn.
- **Mọi API mới** phải có `@Operation` (Swagger), `@PreAuthorize`, và viết test trong `service` layer.
- **L01 schedule PHẢI trigger tạo `CompensationDay`** — gọi `CompensationDateCalculator`.
- **Conflict check** LUÔN chạy trước khi save — gọi `ConflictDetectionService`.
- **Audit log** PHẢI ghi cho mọi CREATE/UPDATE/DELETE qua `AuditHistoryService`.

---

**Ngôn ngữ tài liệu**: Tiếng Việt (giữ nguyên thuật ngữ kỹ thuật).