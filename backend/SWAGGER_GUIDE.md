# Swagger UI - Hướng dẫn sử dụng

## 🚀 Chạy ứng dụng

```bash
cd backend
./mvnw spring-boot:run
```

## 📖 Truy cập Swagger UI

| Môi trường | URL |
|------------|-----|
| **Local** | http://localhost:8080/swagger-ui.html |
| **API JSON** | http://localhost:8080/v3/api-docs |

## 🔐 Đăng nhập và test API

### Bước 1: Login để lấy Token

```bash
# Request
POST http://localhost:8080/api/v1/auth/login
Content-Type: application/json

{
  "username": "admin",
  "password": "admin123"
}
```

**Response:**
```json
{
  "success": true,
  "message": "Login successful",
  "data": {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "tokenType": "Bearer",
    "expiresIn": 86400000,
    "userId": 1,
    "username": "admin",
    "roles": ["ADMIN", "MANAGER"]
  },
  "timestamp": "2026-05-24T10:00:00"
}
```

### Bước 2: Copy Token

Copy giá trị `token` từ response (phần sau "Bearer ")

### Bước 3: Authorize trong Swagger

1. Mở http://localhost:8080/swagger-ui.html
2. Click **Authorize** button 🔓
3. Nhập: `Bearer <paste_token_của_bạn>`
4. Click **Authorize** → **Close**

### Bước 4: Test các API

Giờ bạn có thể test tất cả các API đã được bảo vệ!

## 📁 Cấu trúc Files đã tạo

```
backend/
├── pom.xml                          # Thêm springdoc + JWT dependencies
├── src/main/resources/
│   └── application.yml             # Cấu hình JWT & Swagger
└── src/main/java/com/hospital/scheduler/
    ├── HospitalSchedulerApplication.java
    ├── config/
    │   ├── OpenApiConfig.java      # Swagger configuration
    │   └── SecurityConfig.java      # Security + CORS
    ├── controller/
    │   └── AuthController.java      # Login API
    ├── dto/
    │   ├── ApiResponse.java         # Response wrapper
    │   ├── AuthResponse.java        # Login response
    │   ├── LoginRequest.java        # Login request
    │   └── ErrorResponse.java       # Error response
    ├── exception/
    │   ├── BadRequestException.java
    │   ├── ConflictException.java
    │   ├── ResourceNotFoundException.java
    │   └── GlobalExceptionHandler.java
    ├── security/
    │   ├── JwtService.java         # JWT operations
    │   ├── JwtAuthenticationFilter.java
    │   └── JwtAuthenticationEntryPoint.java
    └── service/
        └── AuthService.java        # Demo auth service
```

## 🔧 Cấu hình JWT

```yaml
# application.yml
jwt:
  secret: Ym9va2NhbXBzZWNyZXRrZXlmb3Jqd3R0b2tlbndpdGhBdExlYXN0MzJjaGFycw==
  expiration: 86400000  # 24 hours
```

## ⚠️ Lưu ý

1. **Demo User**: Hiện tại chỉ có user demo:
   - Username: `admin`
   - Password: `admin123`

2. **Public Endpoints** (không cần token):
   - `/api/v1/auth/login`
   - `/swagger-ui/**`
   - `/v3/api-docs/**`

3. **Khi restart server**: Token cũ sẽ không hoạt động, cần login lại
