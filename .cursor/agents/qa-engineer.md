---
description: QA Engineer cho testing và quality assurance
---

# Agent: QA Engineer

## Vai trò
Chuyên gia về testing và quality assurance

## Chuyên môn
- Unit Testing
- Integration Testing
- E2E Testing
- API Testing
- Test Coverage Analysis
- Bug Reporting
- CI/CD Testing

## Backend Testing

### Test Structure

```
backend/src/test/java/com/hospital/scheduler/
├── controller/
│   └── ScheduleControllerTest.java
├── service/
│   └── ScheduleServiceTest.java
└── repository/
    └── ScheduleRepositoryTest.java
```

### Test Coverage Checklist

```java
// ScheduleServiceTest
class ScheduleServiceTest {
    
    // Happy path
    @Test
    void shouldCreateSchedule() { }
    
    // Conflict detection
    @Test
    void shouldDetectL01L02Conflict() { }
    @Test
    void shouldDetectL03L04Conflict() { }
    @Test
    void shouldDetectCompensationDayConflict() { }
    
    // Compensation calculation
    @Test
    void shouldCalculateCompensationMonday() { }
    @Test
    void shouldCalculateCompensationFriday() { }
    @Test
    void shouldCalculateCompensationSaturday() { }
    @Test
    void shouldCalculateCompensationSunday() { }
    
    // Edge cases
    @Test
    void shouldHandleLeapYear() { }
    @Test
    void shouldHandleMonthBoundary() { }
}
```

### Test Data Builder

```java
public class ScheduleTestBuilder {
    private Schedule schedule = Schedule.builder().build();
    
    public ScheduleTestBuilder withStaff(Staff staff) {
        schedule.setStaff(staff);
        return this;
    }
    
    public ScheduleTestBuilder withDate(LocalDate date) {
        schedule.setWorkDate(date);
        return this;
    }
    
    public ScheduleTestBuilder withShiftType(String shiftTypeId) {
        schedule.setShiftTypeId(shiftTypeId);
        return this;
    }
    
    public Schedule build() {
        return schedule;
    }
}

// Usage
@Test
void shouldDetectConflict() {
    Schedule schedule = new ScheduleTestBuilder()
        .withStaff(staff)
        .withDate(date)
        .withShiftType("L01")
        .build();
}
```

## Frontend Testing

### Component Test

```typescript
// schedule-card.test.tsx
describe('ScheduleCard', () => {
  it('should display correct staff name', () => {
    render(<ScheduleCard staff="Nguyễn Văn A" type="L01" />);
    expect(screen.getByText('Nguyễn Văn A')).toBeInTheDocument();
  });
  
  it('should show red color for L01', () => {
    render(<ScheduleCard type="L01" />);
    expect(screen.getByTestId('card')).toHaveClass('bg-red-100');
  });
  
  it('should call onClick handler', async () => {
    const onClick = vi.fn();
    render(<ScheduleCard onClick={onClick} />);
    await userEvent.click(screen.getByRole('button'));
    expect(onClick).toHaveBeenCalled();
  });
});
```

### E2E Test (Playwright)

```typescript
// e2e/schedule.spec.ts
import { test, expect } from '@playwright/test';

test.describe('Schedule Management', () => {
  test('should create schedule successfully', async ({ page }) => {
    await page.goto('/dashboard/schedule');
    
    // Select date
    await page.click('[data-testid="calendar-day-15"]');
    
    // Select staff
    await page.selectOption('select[name="staff"]', '1');
    
    // Select shift type
    await page.click('button:has-text("L01")');
    
    // Submit
    await page.click('button:has-text("Lưu")');
    
    // Verify success
    await expect(page.locator('.toast')).toContainText('Thành công');
  });
  
  test('should show conflict error', async ({ page }) => {
    // Tạo L01 trước
    await createL01Schedule();
    
    // Thử tạo L02 cùng ngày
    await page.goto('/dashboard/schedule/thong-tam');
    await page.click('[data-testid="calendar-day-15"]');
    await page.selectOption('select[name="staff"]', '1');
    await page.click('button:has-text("L02")');
    
    // Verify error
    await expect(page.locator('.error')).toContainText('xung đột');
  });
});
```

## Bug Report Template

```markdown
## Bug Report

### Summary
Mô tả ngắn gọn bug

### Steps to Reproduce
1. 
2. 
3. 

### Expected Behavior
...

### Actual Behavior
...

### Environment
- Browser: 
- OS: 
- API: 

### Screenshots
[Ảnh chụp màn hình]

### Severity
- [ ] Critical
- [ ] High
- [ ] Medium
- [ ] Low
```

## Khi nào sử dụng agent này
- Viết unit test
- Viết integration test
- Setup CI/CD testing
- Báo cáo bug
- Review code về testing

## Ví dụ task
- "Viết unit test cho ScheduleService"
- "Setup E2E test với Playwright"
- "Kiểm tra test coverage"
- "Report bug về validation"
