import { describe, it, expect, vi, beforeEach } from "vitest";
import { renderHook } from "@testing-library/react";
import { useRole, canManage, canApprove, canEditSchedule, canDeleteSchedule, canViewAuditLog, type UserRole } from "@/hooks/useRole";

// Mock AuthProvider with a controllable user.
const mockedAuthState: { user: { username: string; userId: number; roles: string[] } | null } = {
  user: null,
};

vi.mock("@/components/auth/AuthProvider", () => ({
  useAuth: () => ({ user: mockedAuthState.user }),
}));

describe("useRole", () => {
  beforeEach(() => {
    mockedAuthState.user = null;
  });

  it("returns STAFF when no user is logged in", () => {
    const { result } = renderHook(() => useRole());
    expect(result.current).toBe("STAFF");
  });

  it("returns STAFF when user has no roles", () => {
    mockedAuthState.user = { username: "alice", userId: 1, roles: [] };
    const { result } = renderHook(() => useRole());
    expect(result.current).toBe("STAFF");
  });

  it("returns STAFF when user has only STAFF role", () => {
    mockedAuthState.user = { username: "alice", userId: 1, roles: ["STAFF"] };
    const { result } = renderHook(() => useRole());
    expect(result.current).toBe("STAFF");
  });

  it("returns MANAGER when user has MANAGER role", () => {
    mockedAuthState.user = { username: "alice", userId: 1, roles: ["MANAGER"] };
    const { result } = renderHook(() => useRole());
    expect(result.current).toBe("MANAGER");
  });

  it("returns ADMIN when user has ADMIN role (even with other roles)", () => {
    mockedAuthState.user = { username: "alice", userId: 1, roles: ["MANAGER", "ADMIN"] };
    const { result } = renderHook(() => useRole());
    expect(result.current).toBe("ADMIN");
  });

  it("ignores unknown role strings and falls back to STAFF", () => {
    mockedAuthState.user = { username: "alice", userId: 1, roles: ["GHOST", "POLTERGEIST"] };
    const { result } = renderHook(() => useRole());
    expect(result.current).toBe("STAFF");
  });
});

describe("role helpers", () => {
  const cases: Array<{ fn: (r: UserRole) => boolean; admin: boolean; manager: boolean; staff: boolean }> = [
    { fn: canManage, admin: true, manager: true, staff: false },
    { fn: canApprove, admin: true, manager: true, staff: false },
    { fn: canEditSchedule, admin: true, manager: true, staff: false },
    { fn: canDeleteSchedule, admin: true, manager: false, staff: false },
    { fn: canViewAuditLog, admin: true, manager: true, staff: false },
  ];

  for (const c of cases) {
    it(`${c.fn.name}: ADMIN=${c.admin} MANAGER=${c.manager} STAFF=${c.staff}`, () => {
      expect(c.fn("ADMIN")).toBe(c.admin);
      expect(c.fn("MANAGER")).toBe(c.manager);
      expect(c.fn("STAFF")).toBe(c.staff);
    });
  }
});