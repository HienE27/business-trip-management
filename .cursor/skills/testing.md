---
name: testing
description: Viết unit test và integration test
---

# Skill: Testing

## Backend - Spring Boot Testing

### Test Dependencies (pom.xml)

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>test</scope>
</dependency>
```

### Integration Test

```java
@SpringBootTest
@AutoConfigureMockMvc
class ScheduleControllerIntegrationTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @Autowired
    private ObjectMapper objectMapper;
    
    @Test
    void shouldCreateSchedule() throws Exception {
        ScheduleRequest request = ScheduleRequest.builder()
            .workDate(LocalDate.of(2026, 6, 15))
            .staffId(1L)
            .shiftTypeId("L01")
            .build();
        
        mockMvc.perform(post("/api/v1/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true));
    }
    
    @Test
    void shouldRejectConflictSchedule() throws Exception {
        // Tạo L01 schedule trước
        createSchedule(staffId, date, "L01");
        
        // Thử tạo L02 cùng ngày → phải bị reject
        ScheduleRequest request = ScheduleRequest.builder()
            .workDate(date)
            .staffId(staffId)
            .shiftTypeId("L02")
            .build();
        
        mockMvc.perform(post("/api/v1/schedules")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest());
    }
}
```

### Service Test với Mockito

```java
@ExtendWith(MockitoExtension.class)
class ScheduleServiceTest {
    
    @Mock
    private ScheduleRepository scheduleRepository;
    
    @InjectMocks
    private ScheduleService scheduleService;
    
    @Test
    void shouldDetectL01L02Conflict() {
        // Given
        Long staffId = 1L;
        LocalDate date = LocalDate.of(2026, 6, 15);
        
        Schedule existingSchedule = Schedule.builder()
            .staffId(staffId)
            .workDate(date)
            .shiftTypeId("L01")
            .build();
        
        when(scheduleRepository.findByStaffIdAndWorkDateAndShiftTypeId(
            staffId, date, "L02")).thenReturn(Optional.of(existingSchedule));
        
        // When
        boolean hasConflict = scheduleService.hasConflict(staffId, date, "L02");
        
        // Then
        assertTrue(hasConflict);
    }
    
    @Test
    void shouldCalculateCompensationForFriday() {
        // Trực thứ 6 → nghỉ bù thứ 3 tuần sau
        LocalDate friday = LocalDate.of(2026, 6, 19); // Thứ 6
        
        LocalDate compensation = scheduleService.calculateCompensation(friday);
        
        assertEquals(LocalDate.of(2026, 6, 23), compensation); // Thứ 3
    }
}
```

## Frontend - React Testing

### Test Dependencies

```bash
pnpm add -D vitest @testing-library/react @testing-library/jest-dom jsdom
```

### Component Test

```typescript
// components/__tests__/ScheduleCard.test.tsx
import { render, screen, fireEvent } from '@testing-library/react';
import { ScheduleCard } from '../ScheduleCard';

describe('ScheduleCard', () => {
  it('should display staff name', () => {
    render(<ScheduleCard staffName="Nguyễn Văn A" shiftType="L01" />);
    
    expect(screen.getByText('Nguyễn Văn A')).toBeInTheDocument();
  });
  
  it('should show correct color for L01', () => {
    render(<ScheduleCard shiftType="L01" />);
    
    const card = screen.getByTestId('schedule-card');
    expect(card).toHaveClass('bg-red-100');
  });
  
  it('should call onClick when clicked', () => {
    const onClick = vi.fn();
    render(<ScheduleCard onClick={onClick} />);
    
    fireEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalled();
  });
});
```

### Hook Test

```typescript
// hooks/__tests__/useSchedule.test.ts
import { renderHook, waitFor } from '@testing-library/react';
import { useSchedules } from '../useSchedules';

describe('useSchedules', () => {
  it('should fetch schedules', async () => {
    global.fetch = vi.fn().mockResolvedValueOnce({
      ok: true,
      json: async () => ({ data: [] }),
    });
    
    const { result } = renderHook(() => useSchedules(1));
    
    await waitFor(() => expect(result.current.isLoading).toBe(false));
    
    expect(result.current.schedules).toEqual([]);
  });
});
```

## Coverage

```bash
# Backend
mvn test jacoco:report

# Frontend
pnpm test --coverage
```

## Khi nào trigger
- Viết unit test cho service
- Viết integration test cho controller
- Viết component test cho React
- Setup CI/CD với test
