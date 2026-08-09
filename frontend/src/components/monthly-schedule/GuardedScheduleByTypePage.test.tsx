import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen, waitFor } from "@testing-library/react";
import type { ReactNode } from "react";
import { GuardedScheduleByTypePage } from "@/components/monthly-schedule/GuardedScheduleByTypePage";
import type { ScheduleTypeConfig } from "@/components/monthly-schedule/ScheduleByTypePage";

// Mock useAuth with controllable user.
const mockedAuthState: { user: { username: string; userId: number; roles: string[] } | null } = {
  user: null,
};

vi.mock("@/components/auth/AuthProvider", () => ({
  useAuth: () => ({ user: mockedAuthState.user }),
}));

// Mock ScheduleByTypePage so we don't pull in the full schedule tree
// (calendar, modals, workflow stepper) into this guard-only test.
vi.mock("@/components/monthly-schedule/ScheduleByTypePage", () => ({
  ScheduleByTypePage: ({ config }: { config: ScheduleTypeConfig }) => (
    <div data-testid="schedule-by-type-page" data-active-section={config.activeSection}>
      {config.title}
    </div>
  ),
}));

// DashboardShell -> DashboardHeader -> useNotifications() requires a
// NotificationProvider. Wrap every render with one. We also provide a
// stub ToastProvider so the inner shell can mount cleanly.
vi.mock("@/components/ui/NotificationContext", () => ({
  NotificationProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
  useNotifications: () => ({ unreadCount: 0, refreshCount: vi.fn(), notifications: [] }),
}));
vi.mock("@/components/ui/ToastProvider", () => ({
  ToastProvider: ({ children }: { children: ReactNode }) => <>{children}</>,
  useToast: () => ({ success: vi.fn(), error: vi.fn(), warning: vi.fn(), info: vi.fn() }),
}));

// DashboardHeader uses useRouter/useSearchParams which require a Next
// App Router context. Provide no-op stubs.
vi.mock("next/navigation", () => ({
  useRouter: () => ({ push: vi.fn(), replace: vi.fn(), back: vi.fn(), forward: vi.fn(), refresh: vi.fn() }),
  useSearchParams: () => new URLSearchParams(),
  usePathname: () => "/duty-24",
}));

const TEST_CONFIG: ScheduleTypeConfig = {
  activeSection: "duty-24",
  shiftTypeId: "L01",
  title: "Lịch trực 24/24",
  description: "Test description",
  emptyMessage: "Empty",
  emptyIcon: "emergency",
  ctaIcon: "add",
  ctaLabel: "Thêm",
  totalShiftLabel: "Tổng",
  totalShiftAccent: "bg-shift-24/30",
  staffAccent: "bg-shift-all-day/20",
  fetchErrorMessage: "Lỗi",
  compDescription: "",
};

describe("GuardedScheduleByTypePage — default ADMIN+MANAGER access", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedAuthState.user = null;
  });

  it("renders the schedule page for ADMIN", async () => {
    mockedAuthState.user = { username: "admin", userId: 1, roles: ["ADMIN"] };
    render(<GuardedScheduleByTypePage config={TEST_CONFIG} />);

    await waitFor(() => {
      expect(screen.getByTestId("schedule-by-type-page")).toBeInTheDocument();
    });
    expect(
      screen.getByTestId("schedule-by-type-page").textContent,
    ).toContain("Lịch trực 24/24");
  });

  it("renders the schedule page for MANAGER", async () => {
    mockedAuthState.user = { username: "manager", userId: 2, roles: ["MANAGER"] };
    render(<GuardedScheduleByTypePage config={TEST_CONFIG} />);

    await waitFor(() => {
      expect(screen.getByTestId("schedule-by-type-page")).toBeInTheDocument();
    });
  });

  it("shows the read-only StaffScheduleView for STAFF (default allow list)", async () => {
    mockedAuthState.user = { username: "alice", userId: 3, roles: ["STAFF"] };
    render(<GuardedScheduleByTypePage config={TEST_CONFIG} />);

    // STAFF is in the default allow list, so they get the read-only
    // personal-schedule view (StaffScheduleView), NOT the denied state.
    // M01-F05: every role can view schedules — only the depth differs.
    await waitFor(() => {
      expect(screen.getByText(/cá nhân/i)).toBeInTheDocument();
    });
    expect(screen.queryByTestId("schedule-by-type-page")).not.toBeInTheDocument();
  });

  it("shows the denied state for unauthenticated users", async () => {
    mockedAuthState.user = null;
    render(<GuardedScheduleByTypePage config={TEST_CONFIG} />);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: /không có quyền/i }),
      ).toBeInTheDocument();
    });
  });
});

describe("GuardedScheduleByTypePage — custom allow list", () => {
  beforeEach(() => {
    vi.clearAllMocks();
    mockedAuthState.user = null;
  });

  it("ADMIN-only allow list blocks MANAGER", async () => {
    mockedAuthState.user = { username: "manager", userId: 2, roles: ["MANAGER"] };
    render(<GuardedScheduleByTypePage config={TEST_CONFIG} allow={["ADMIN"]} />);

    await waitFor(() => {
      expect(
        screen.getByRole("heading", { name: /không có quyền/i }),
      ).toBeInTheDocument();
    });
  });

  it("ADMIN-only allow list lets ADMIN through", async () => {
    mockedAuthState.user = { username: "admin", userId: 1, roles: ["ADMIN"] };
    render(<GuardedScheduleByTypePage config={TEST_CONFIG} allow={["ADMIN"]} />);

    await waitFor(() => {
      expect(screen.getByTestId("schedule-by-type-page")).toBeInTheDocument();
    });
  });

  it("ALL_ROLES allow list lets STAFF through to StaffScheduleView", async () => {
    mockedAuthState.user = { username: "alice", userId: 3, roles: ["STAFF"] };
    render(
      <GuardedScheduleByTypePage
        config={TEST_CONFIG}
        allow={["ADMIN", "MANAGER", "STAFF"]}
      />,
    );

    // STAFF-only role lands on StaffScheduleView (read-only personal
    // schedule) — the testid "schedule-by-type-page" is only rendered
    // for ADMIN/MANAGER. M01-F05: STAFF still has access; they just
    // see a different (read-only) component.
    await waitFor(() => {
      expect(screen.getByText(/cá nhân/i)).toBeInTheDocument();
    });
  });
});

describe("GuardedScheduleByTypePage — config passthrough", () => {
  beforeEach(() => {
    mockedAuthState.user = { username: "admin", userId: 1, roles: ["ADMIN"] };
  });

  it("passes the config through to ScheduleByTypePage (activeSection)", async () => {
    render(<GuardedScheduleByTypePage config={TEST_CONFIG} />);

    await waitFor(() => {
      const el = screen.getByTestId("schedule-by-type-page");
      expect(el.getAttribute("data-active-section")).toBe("duty-24");
    });
  });
});