package com.hospital.scheduler.config;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer",
        description = "Enter JWT token"
)
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("🏥 Hospital Scheduler API")
                        .version("1.0.0")
                        .description("""
                                ## Hospital Scheduler - Quản lý Lịch Công Tác

                                Hệ thống quản lý lịch công tác cho bệnh viện với các tính năng:
                                - Quản lý nhân sự (20 người)
                                - 4 loại lịch: Lịch trực 24/24, Lịch thông tầm, Lịch phòng khám dịch vụ, Lịch phòng khám chuyên gia
                                - Kiểm tra xung đột tự động
                                - Tự động tính ngày nghỉ bù
                                - Auto scheduling với thuật toán

                                ## Authentication
                                1. Đăng nhập với `/api/v1/auth/login` để lấy JWT token
                                2. Click **Authorize** button và nhập: `Bearer <token>`
                                """)
                        .contact(new Contact()
                                .name("Hospital IT Team")
                                .email("it@hospital.com")
                                .url("https://hospital.com"))
                        .license(new License()
                                .name("MIT License")
                                .url("https://opensource.org/licenses/MIT")))
                .servers(List.of(
                        new Server().url("http://localhost:8080").description("Development"),
                        new Server().url("https://api.hospital.com").description("Production")
                ));
    }
}
