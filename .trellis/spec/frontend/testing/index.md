# Testing

> Quy ước test cho frontend (Vitest + Playwright).

---

## Test stack

| Loại | Tool | Command | Phạm vi |
|---|---|---|---|
| Unit / Component | **Vitest** + `@testing-library/react` | `pnpm test` | Component logic, hook, util |
| E2E | **Playwright** | `pnpm test:e2e` | Flow nghiệp vụ end-to-end |
| Visual regression | (chưa setup, P2) | — | — |

---

## Cấu trúc file test

Đặt file test cạnh file code (xem `components/operations/StaffCrudPanel.test.tsx`):

```
components/operations/
├── StaffCrudPanel.tsx
└── StaffCrudPanel.test.tsx

components/ui/
├── EmptyState.tsx
└── EmptyState.test.tsx
```

---

## Component test pattern (Vitest)

```typescript
// components/ui/EmptyState.test.tsx
import { describe, it, expect } from "vitest";
import { render, screen } from "@testing-library/react";
import { EmptyState } from "./EmptyState";

describe("EmptyState", () => {
  it("renders title and message", () => {
    render(<EmptyState title="Không có dữ liệu" message="Hãy tạo mới" />);
    expect(screen.getByText("Không có dữ liệu")).toBeInTheDocument();
    expect(screen.getByText("Hãy tạo mới")).toBeInTheDocument();
  });

  it("renders icon when provided", () => {
    render(
      <EmptyState
        icon="calendar_month"
        title="Chưa có lịch"
        message="Vui lòng tạo kỳ lịch mới"
      />
    );
    expect(screen.getByText("calendar_month")).toBeInTheDocument();
  });

  it("calls onAction when action button clicked", async () => {
    const onAction = vi.fn();
    render(
      <EmptyState
        title="..."
        message="..."
        actionLabel="Tạo mới"
        onAction={onAction}
      />
    );
    await userEvent.click(screen.getByRole("button", { name: "Tạo mới" }));
    expect(onAction).toHaveBeenCalledTimes(1);
  });
});
```

---

## Hook test pattern

```typescript
// hooks/useForm.test.ts
import { renderHook, act } from "@testing-library/react";
import { useForm } from "./useForm";

describe("useForm", () => {
  it("updates field value", () => {
    const { result } = renderHook(() => useForm({ name: "", email: "" }));

    act(() => result.current.update("name", "Nguyen Van A"));
    expect(result.current.values.name).toBe("Nguyen Van A");
  });

  it("resets to initial values", () => {
    const { result } = renderHook(() => useForm({ name: "Initial" }));
    act(() => result.current.update("name", "Changed"));
    act(() => result.current.reset());
    expect(result.current.values.name).toBe("Initial");
  });
});
```

---

## Mock API

Dùng `vi.mock` để mock `lib/api.ts`:

```typescript
import { vi } from "vitest";

vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn().mockResolvedValue([{ id: 1, fullName: "BS. A" }]),
    post: vi.fn().mockResolvedValue({ id: 1 }),
    put: vi.fn(),
    delete: vi.fn(),
  },
}));

import { api } from "@/lib/api";

it("calls API on mount", async () => {
  render(<StaffList />);
  await waitFor(() => expect(api.get).toHaveBeenCalledWith("/staff"));
});
```

---

## E2E test pattern (Playwright)

### Auth fixture (dùng env var, KHÔNG hardcode credentials)

```typescript
// tests/fixtures/auth.fixture.ts
import { test as base, type Page } from '@playwright/test';

const TEST_USERNAME = process.env.E2E_USERNAME ?? 'admin';
const TEST_PASSWORD = process.env.E2E_PASSWORD ?? 'change-me';
const LOGIN_PATH = process.env.E2E_LOGIN_PATH ?? '/login';

export async function loginAsTestUser(page: Page): Promise<void> {
  await page.goto(LOGIN_PATH);
  await page.waitForLoadState('networkidle');

  const usernameInput = page.locator('input[name="username"]');
  const passwordInput = page.locator('input[name="password"]');

  const visible = await usernameInput.isVisible({ timeout: 10_000 }).catch(() => false);
  if (!visible) return;  // No-op when login form isn't rendered

  await usernameInput.fill(TEST_USERNAME);
  await passwordInput.fill(TEST_PASSWORD);
  await page.getByRole('button', { name: /đăng nhập/i }).click();
  await page.waitForTimeout(2_000);
}

export const test = base.extend<{ loginAs: () => Promise<void> }>({
  loginAs: async ({ page }, use) => {
    await use(() => loginAsTestUser(page));
  },
});

export { expect } from '@playwright/test';
```

### Spec file dùng fixture

```typescript
// tests/swap-requests.spec.ts
import { test, expect } from './fixtures/auth.fixture';

test.describe('Schedule Exchange (đổi ca)', () => {
  test.beforeEach(async ({ loginAs }) => {
    await loginAs();  // No-op nếu form không hiển thị
  });

  test('manager approves pending exchange', async ({ page }) => {
    await page.goto('/swap-requests');
    await expect(page.locator('h1')).toContainText('Yêu cầu đổi ca');

    // ... business assertions
  });
});
```

### Quy tắc E2E

- **KHÔNG** hardcode credentials trong spec → luôn qua `loginAs` fixture.
- **KHÔNG** assume form login luôn hiện → check `isVisible()` trước khi fill.
- Dùng `getByRole` / `getByText` thay vì CSS selector.
- Sleep tối đa 2-3s, dùng `waitFor` + assertion thay vì timeout cứng.
- Mỗi test nên pass standalone (không phụ thuộc test khác).

### CI secrets

Trong `.github/workflows/frontend-ci.yml`:

```yaml
e2e:
  env:
    E2E_USERNAME: ${{ secrets.E2E_USERNAME }}
    E2E_PASSWORD: ${{ secrets.E2E_PASSWORD }}
    E2E_LOGIN_PATH: ${{ vars.E2E_LOGIN_PATH || '/login' }}
```

Repository secrets `E2E_USERNAME` / `E2E_PASSWORD` set qua GitHub UI.

### Tests không cần auth

`login.spec.ts` và một số test render-only KHÔNG cần `loginAs` — import trực tiếp từ `@playwright/test`.

---

## Test coverage checklist cho mỗi module

### `monthly-schedule/`

- [ ] Hiển thị calendar đúng tháng
- [ ] Hiển thị schedule với đúng màu L01–L04
- [ ] Click vào ngày mở QuickAddModal
- [ ] Submit form tạo schedule thành công
- [ ] Submit form nhưng có conflict → hiện error
- [ ] Auto-scheduling chạy thành công
- [ ] Conflict section highlight đúng các ngày có conflict

### `swap-requests/`

- [ ] Staff thấy request của mình
- [ ] Manager thấy tất cả request
- [ ] Approve / Reject hoạt động
- [ ] Optimistic update + rollback khi lỗi

### `staff/`

- [ ] CRUD cơ bản
- [ ] Search theo keyword, specialty, role
- [ ] Filter status
- [ ] Pagination
- [ ] Import từ Excel (nếu có)

---

## Best practices

| Quy tắc | Lý do |
|---|---|
| Test behavior, không test implementation | Refactor dễ |
| Dùng `screen.getByRole` / `getByText` thay vì `getByTestId` | Accessibility-first |
| 1 test = 1 assertion concept | Dễ debug khi fail |
| Tên test mô tả rõ hành vi | "should X when Y" |
| Mock API ở unit test, dùng API thật ở E2E | Tốc độ vs độ tin cậy |
| Chạy `pnpm test` trước khi commit | Tránh regression |

---

## Anti-patterns

| ❌ KHÔNG | ✅ DÙNG |
|---|---|
| `getByTestId("button-submit")` | `getByRole("button", { name: "Lưu" })` |
| Snapshot test toàn component | Test specific behavior |
| Test internal state của component | Test output (DOM) |
| Sleep cố định (`await new Promise(r => setTimeout(r, 1000))`) | `waitFor` + assertion |
| Mỗi test phụ thuộc test khác | Mỗi test độc lập |
| Test private function | Test qua public API |
| Hardcode credentials trong test | Dùng fixture / env var |
| 100% coverage mù quáng | 100% critical path, ignore boilerplate |