import { describe, it, expect, vi } from "vitest";
import { renderHook } from "@testing-library/react";

const mockAuth = vi.hoisted(() => ({
  user: {
    username: "alice",
    userId: 1,
    roles: ["ADMIN"],
    permissions: ["DASHBOARD_VIEW", "STAFF_VIEW"],
  } as {
    username: string;
    userId: number;
    roles: string[];
    permissions: string[];
  } | null,
}));

vi.mock("@/components/auth/AuthProvider", () => ({
  useAuth: () => ({ user: mockAuth.user }),
}));

import { usePermissions } from "./usePermissions";

describe("usePermissions", () => {
  it("can() returns true when permission is granted", () => {
    const { result } = renderHook(() => usePermissions());
    expect(result.current.can("DASHBOARD_VIEW")).toBe(true);
  });

  it("can() returns false when permission is missing", () => {
    const { result } = renderHook(() => usePermissions());
    expect(result.current.can("AUDIT_DELETE")).toBe(false);
  });

  it("can() supports an array argument (AND by default)", () => {
    const { result } = renderHook(() => usePermissions());
    expect(result.current.can(["DASHBOARD_VIEW", "STAFF_VIEW"])).toBe(true);
    expect(result.current.can(["DASHBOARD_VIEW", "AUDIT_DELETE"])).toBe(false);
  });

  it("canAny() returns true when any permission is present", () => {
    const { result } = renderHook(() => usePermissions());
    expect(result.current.canAny(["AUDIT_DELETE", "STAFF_VIEW"])).toBe(true);
  });

  it("returns an empty permission set for an unauthenticated user", () => {
    const previous = mockAuth.user;
    mockAuth.user = null;
    try {
      const { result } = renderHook(() => usePermissions());
      expect(result.current.permissions.size).toBe(0);
      expect(result.current.can("DASHBOARD_VIEW")).toBe(false);
    } finally {
      mockAuth.user = previous;
    }
  });
});