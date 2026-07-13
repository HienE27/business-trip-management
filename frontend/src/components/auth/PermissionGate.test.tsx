import { describe, it, expect, vi, beforeEach } from "vitest";
import { render, screen } from "@testing-library/react";
import { PermissionGate } from "./PermissionGate";
import { usePermissions } from "@/hooks/usePermissions";

vi.mock("@/hooks/usePermissions", () => ({
  usePermissions: vi.fn(),
}));

const mockUsePermissions = vi.mocked(usePermissions);

function setup(permissions: Set<string>) {
  mockUsePermissions.mockReturnValue({
    permissions,
    can: (p: string | string[]) => {
      const list = Array.isArray(p) ? p : [p];
      return list.every((x) => permissions.has(x));
    },
    canAny: (p: string[]) => p.some((x) => permissions.has(x)),
    hasPermission: (p: string) => permissions.has(p),
  });
}

describe("PermissionGate", () => {
  beforeEach(() => {
    vi.clearAllMocks();
  });

  it("renders children when user has the required permission", () => {
    setup(new Set(["STAFF_VIEW"]));
    render(
      <PermissionGate required="STAFF_VIEW">
        <button>Delete</button>
      </PermissionGate>
    );
    expect(screen.getByText("Delete")).toBeTruthy();
  });

  it("hides children when user lacks the required permission", () => {
    setup(new Set([]));
    render(
      <PermissionGate required="STAFF_VIEW">
        <button>Delete</button>
      </PermissionGate>
    );
    expect(screen.queryByText("Delete")).toBeNull();
  });

  it("requires ALL permissions by default", () => {
    setup(new Set(["A"]));
    render(
      <PermissionGate required={["A", "B"]}>
        <button>Both</button>
      </PermissionGate>
    );
    expect(screen.queryByText("Both")).toBeNull();
  });

  it("passes when requireAll=false and user has any of the permissions", () => {
    setup(new Set(["A"]));
    render(
      <PermissionGate required={["A", "B"]} requireAll={false}>
        <button>Either</button>
      </PermissionGate>
    );
    expect(screen.getByText("Either")).toBeTruthy();
  });

  it("renders fallback when user lacks permission", () => {
    setup(new Set([]));
    render(
      <PermissionGate required="X" fallback={<span>locked</span>}>
        <button>hidden</button>
      </PermissionGate>
    );
    expect(screen.queryByText("hidden")).toBeNull();
    expect(screen.getByText("locked")).toBeTruthy();
  });
});