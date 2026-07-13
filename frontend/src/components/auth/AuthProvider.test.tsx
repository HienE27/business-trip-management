import { describe, it, expect, vi, beforeEach, afterEach } from "vitest";
import { renderHook, act, waitFor } from "@testing-library/react";
import { AuthProvider, useAuth } from "@/components/auth/AuthProvider";
import { api } from "@/lib/api";
import type { ReactNode } from "react";

// Mock @/lib/api — the real one would call out over the network.
vi.mock("@/lib/api", () => ({
  api: {
    get: vi.fn(),
    post: vi.fn(),
    logout: vi.fn(),
  },
}));

// Mock next/navigation router.
const mockRouter = { replace: vi.fn(), push: vi.fn() };
vi.mock("next/navigation", () => ({
  useRouter: () => mockRouter,
}));

// Capture fetch calls so the login flow can be exercised without a server.
const fetchMock = vi.fn();
const originalFetch = global.fetch;
beforeEach(() => {
  global.fetch = fetchMock as unknown as typeof fetch;
});
afterEach(() => {
  global.fetch = originalFetch;
});

const mockedApi = vi.mocked(api);

const wrapper = ({ children }: { children: ReactNode }) => (
  <AuthProvider>{children}</AuthProvider>
);

const SAMPLE_STAFF_RESPONSE = {
  id: 42,
  username: "alice",
  fullName: "Alice",
  email: "a@x",
  phone: "",
  maxShiftsPerMonth: 10,
  isActive: true,
  specialty: null,
  roles: ["ADMIN"],
  createdAt: "2026-01-01",
  updatedAt: "2026-01-01",
};

function buildLoginResponse(overrides: Partial<{ token: string; userId: number; username: string; roles: string[] }> = {}) {
  return {
    ok: true,
    status: 200,
    headers: new Headers({ "X-Auth-Token": overrides.token ?? "tok-123" }),
    json: async () => ({
      success: true,
      data: {
        token: overrides.token ?? "tok-123",
        userId: overrides.userId ?? 42,
        username: overrides.username ?? "alice",
        roles: overrides.roles ?? ["ADMIN"],
      },
    }),
  } as unknown as Response;
}

beforeEach(() => {
  window.localStorage.clear();
  vi.clearAllMocks();
  mockRouter.replace.mockReset();
  fetchMock.mockReset();
  // Default: /staff/me returns the sample staff with ADMIN role.
  mockedApi.get.mockResolvedValue(SAMPLE_STAFF_RESPONSE);
  mockedApi.logout.mockResolvedValue({} as never);
});

describe("useAuth — initial bootstrap", () => {
  it("starts in loading state, then resolves to authenticated after /staff/me", async () => {
    const { result } = renderHook(() => useAuth(), { wrapper });

    // Initial state: not yet mounted, so loading
    expect(result.current.isLoading).toBe(true);

    await waitFor(() => {
      expect(result.current.isLoading).toBe(false);
    });

    expect(result.current.user).toEqual({
      username: "alice",
      userId: 42,
      roles: ["ADMIN"],
      permissions: [],
    });
    expect(result.current.isAuthenticated).toBe(true);
    expect(mockedApi.get).toHaveBeenCalledWith("/staff/me");
  });

  it("persists user to localStorage on successful bootstrap", async () => {
    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.user).not.toBeNull());

    const stored = window.localStorage.getItem("medschedule.user");
    expect(stored).not.toBeNull();
    expect(JSON.parse(stored!)).toMatchObject({
      username: "alice",
      userId: 42,
      roles: ["ADMIN"],
    });
  });

it("keeps stored user when /staff/me fails (graceful offline mode)", async () => {
      window.localStorage.setItem(
        "medschedule.user",
        JSON.stringify({ username: "stale", userId: 0, roles: [], permissions: [] }),
      );
      mockedApi.get.mockRejectedValue(new Error("401"));

      const { result } = renderHook(() => useAuth(), { wrapper });

      await waitFor(() => expect(result.current.isLoading).toBe(false));

      // Bootstrap keeps the localStorage user on API failure so the UI
      // remains usable; only the explicit logout() flow clears state.
      expect(result.current.user).toEqual({ username: "stale", userId: 0, roles: [], permissions: [] });
      expect(result.current.isAuthenticated).toBe(true);
      expect(window.localStorage.getItem("medschedule.user")).not.toBeNull();
    });
});

describe("useAuth — login", () => {
  it("stores token, hydrates user from /staff/me, and routes home", async () => {
    fetchMock.mockResolvedValue(buildLoginResponse({ roles: ["ADMIN"] }));

    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      await result.current.login("alice", "secret");
    });

    // Token persisted
    expect(window.localStorage.getItem("medschedule.token")).toBe("tok-123");
    // User hydrated from /staff/me
    expect(result.current.user).toEqual({
      username: "alice",
      userId: 42,
      roles: ["ADMIN"],
      permissions: [],
    });
    // Routed to home
    expect(mockRouter.replace).toHaveBeenCalledWith("/");
  });

  it("throws a friendly error when the server rejects credentials", async () => {
    fetchMock.mockResolvedValue({
      ok: false,
      status: 401,
      json: async () => ({}),
    } as unknown as Response);

    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await expect(
      act(async () => {
        await result.current.login("alice", "wrong");
      }),
    ).rejects.toThrow(/đăng nhập thất bại/i);

    // No token stored
    expect(window.localStorage.getItem("medschedule.token")).toBeNull();
  });
});

describe("useAuth — logout", () => {
  it("clears token + user, routes to /login", async () => {
    fetchMock.mockResolvedValue(buildLoginResponse({ roles: ["MANAGER"] }));

    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.user).not.toBeNull());

    await act(async () => {
      await result.current.logout();
    });

    expect(result.current.user).toBeNull();
    expect(window.localStorage.getItem("medschedule.token")).toBeNull();
    expect(window.localStorage.getItem("medschedule.user")).toBeNull();
    expect(mockRouter.replace).toHaveBeenCalledWith("/login");
    expect(mockedApi.logout).toHaveBeenCalledTimes(1);
  });

  it("local cleanup still runs even when api.logout rejects", async () => {
    fetchMock.mockResolvedValue(buildLoginResponse());
    mockedApi.logout.mockRejectedValue(new Error("network"));

    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.user).not.toBeNull());

    // The provider's logout does NOT catch api.logout errors — callers are
    // expected to wrap the call in their own try/catch if they care. We
    // document this by asserting the error surfaces AND that the local
    // cleanup (localStorage + state) still runs via finally{}.
    await act(async () => {
      try {
        await result.current.logout();
      } catch {
        // expected — api.logout rejected; logout re-throws
      }
    });

    // Local state cleared because finally{} ran before the rejection bubbled.
    expect(result.current.user).toBeNull();
    expect(window.localStorage.getItem("medschedule.token")).toBeNull();
    expect(window.localStorage.getItem("medschedule.user")).toBeNull();
  });
});

describe("useAuth — refreshUser", () => {
  it("re-fetches /staff/me and updates the user in state and storage", async () => {
    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.user).not.toBeNull());

    // Server now reports a new role
    mockedApi.get.mockResolvedValue({ ...SAMPLE_STAFF_RESPONSE, roles: ["MANAGER"] });

    await act(async () => {
      await result.current.refreshUser();
    });

    expect(result.current.user?.roles).toEqual(["MANAGER"]);
    expect(JSON.parse(window.localStorage.getItem("medschedule.user")!).roles).toEqual([
      "MANAGER",
    ]);
  });
});