# Command: /gen-entity
## Mô tả: Tạo JPA Entity

Tạo JPA Entity theo chuẩn Spring Boot:

## Cấu trúc Entity

```java
package com.hospital.scheduler.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.LocalDateTime;

@Entity
@Table(name = "{table_name}")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class {Entity} {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    // Columns here
    
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
```

## Associations

```java
// Many-to-One
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "staff_id")
private Staff staff;

// One-to-Many
@OneToMany(mappedBy = "{fieldName}", cascade = CascadeType.ALL)
private List<Schedule> schedules;
```

## Ví dụ:
- "/gen-entity Staff" → tạo Staff entity
- "/gen-entity Schedule" → tạo Schedule entity
