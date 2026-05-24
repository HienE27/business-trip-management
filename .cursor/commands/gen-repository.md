# Command: /gen-repository
## Mô tả: Tạo JPA Repository

Tạo Repository interface:

## Cấu trúc Repository

```java
@Repository
public interface {Entity}Repository extends JpaRepository<{Entity}, Long> {
    
    // Custom queries
    Optional<{Entity}> findByField(String field);
    
    // Multiple conditions
    List<{Entity}> findByField1AndField2(String field1, String field2);
    
    // Custom JPQL
    @Query("SELECT e FROM {Entity} e WHERE e.field = :value")
    List<{Entity}> findByCustomQuery(@Param("value") String value);
    
    // Native query
    @Query(value = "SELECT * FROM table WHERE column = :value", nativeQuery = true)
    List<{Entity}> findByNativeQuery(@Param("value") String value);
    
    // Count
    long countByField(String field);
    
    // Exists
    boolean existsByField(String field);
    
    // Delete
    void deleteByField(String field);
}
```

## Ví dụ:
- "/gen-repository Staff" → tạo StaffRepository
- "/gen-repository Schedule" → tạo ScheduleRepository
