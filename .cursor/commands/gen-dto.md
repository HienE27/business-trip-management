# Command: /gen-dto
## Mô tả: Tạo DTO và Request classes

Tạo DTO, Request, Response classes:

## Cấu trúc DTO

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class {Entity}DTO {
    private Long id;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

## Cấu trúc Request

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class {Entity}Request {
    
    @NotBlank(message = "Field is required")
    private String fieldName;
    
    // Validation annotations
}
```

## Cấu trúc Response wrapper

```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiResponse<T> {
    private boolean success;
    private String message;
    private T data;
    private LocalDateTime timestamp;
    
    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
            .success(true)
            .data(data)
            .timestamp(LocalDateTime.now())
            .build();
    }
    
    public static <T> ApiResponse<T> error(String message) {
        return ApiResponse.<T>builder()
            .success(false)
            .message(message)
            .timestamp(LocalDateTime.now())
            .build();
    }
}
```

## Ví dụ:
- "/gen-dto Staff" → tạo StaffDTO, StaffRequest
- "/gen-dto Schedule" → tạo ScheduleDTO, ScheduleRequest
