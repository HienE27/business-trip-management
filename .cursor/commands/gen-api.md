# Command: /gen-api
## Mô tả: Tạo REST API Controller

Tạo REST API Controller theo chuẩn Spring Boot:

## Cấu trúc Controller

```java
@RestController
@RequestMapping("/api/v1/{resource}")
@RequiredArgsConstructor
public class {Resource}Controller {
    
    private final {Resource}Service {resource}Service;
    
    // GET all
    @GetMapping
    public ResponseEntity<ApiResponse<List<{Resource}DTO>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success({resource}Service.findAll()));
    }
    
    // GET by ID
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<{Resource}DTO>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success({resource}Service.findById(id)));
    }
    
    // POST create
    @PostMapping
    public ResponseEntity<ApiResponse<{Resource}DTO>> create(@Valid @RequestBody {Resource}Request request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.success({resource}Service.create(request)));
    }
    
    // PUT update
    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<{Resource}DTO>> update(
            @PathVariable Long id, 
            @Valid @RequestBody {Resource}Request request) {
        return ResponseEntity.ok(ApiResponse.success({resource}Service.update(id, request)));
    }
    
    // DELETE
    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable Long id) {
        {resource}Service.delete(id);
        return ResponseEntity.ok(ApiResponse.success(null, "Deleted successfully"));
    }
}
```

## Ví dụ:
- "/gen-api staff" → tạo StaffController
- "/gen-api schedule" → tạo ScheduleController
