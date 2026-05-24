# Command: /gen-service
## Mô tả: Tạo Service Layer cho Spring Boot

Tạo Service class theo chuẩn Spring Boot:

## Cấu trúc Service

```java
@Service
@RequiredArgsConstructor
@Transactional
public class {Entity}Service {
    
    private final {Entity}Repository {entity}Repository;
    
    // Find all
    public List<{Entity}DTO> findAll() {
        return {entity}Repository.findAll().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    // Find by ID
    public {Entity}DTO findById(Long id) {
        {Entity} entity = {entity}Repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("{Entity} not found with id: " + id));
        return toDTO(entity);
    }
    
    // Create
    public {Entity}DTO create({Entity}Request request) {
        {Entity} entity = {Entity}.builder()
            // Map fields from request
            .build();
        return toDTO({entity}Repository.save(entity));
    }
    
    // Update
    public {Entity}DTO update(Long id, {Entity}Request request) {
        {Entity} entity = {entity}Repository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("{Entity} not found"));
        
        // Update fields
        return toDTO({entity}Repository.save(entity));
    }
    
    // Delete
    public void delete(Long id) {
        if (!{entity}Repository.existsById(id)) {
            throw new ResourceNotFoundException("{Entity} not found");
        }
        {entity}Repository.deleteById(id);
    }
    
    // Map to DTO
    private {Entity}DTO toDTO({Entity} entity) {
        return {Entity}DTO.builder()
            // Map fields
            .build();
    }
}
```

## Ví dụ:
- "/gen-service Staff" → tạo StaffService
- "/gen-service Schedule" → tạo ScheduleService
