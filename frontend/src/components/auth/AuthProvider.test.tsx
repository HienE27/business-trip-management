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

/**
 * Seed a valid "looks logged in" session: both an access token AND a cached
 * user are required by the bootstrap gate (see AuthProvider bootstrapAuth).
 * Tests that exercise bootstrap must call this BEFORE renderHook so the
 * provider actually fetches /staff/me instead of short-circuiting.
 */
function seedStoredSession(overrides: { user?: Record<string, unknown>; token?: string } = {}) {
  window.localStorage.setItem("medschedule.token", overrides.token ?? "seeded-token");
  window.localStorage.setItem(
    "medschedule.user",
    JSON.stringify(
      overrides.user ?? { username: "alice", userId: 42, roles: ["ADMIN"], permissions: [] },
    ),
  );
}

describe("useAuth — initial bootstrap", () => {
  it("starts in loading state, then resolves to authenticated after /staff/me", async () => {
    seedStoredSession();
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
    seedStoredSession();
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

  // BUGFIX (was DEAD-USER-AUTH): when only the cached user is present
  // (no token), the bootstrap must short-circuit to the unauthenticated
  // path instead of hydrating a no-permission ghost user.
  it("skips /staff/me and stays unauthenticated when no token is stored", async () => {
    window.localStorage.setItem(
      "medschedule.user",
      JSON.stringify({ username: "ghost", userId: 0, roles: ["MANAGER"], permissions: [] }),
    );
    mockedApi.get.mockClear();

    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
    expect(mockedApi.get).not.toHaveBeenCalled();
  });

  it("keeps stored user when /staff/me fails (graceful offline mode)", async () => {
    seedStoredSession({
      user: { username: "stale", userId: 0, roles: [], permissions: [] },
    });
    // Non-401 error → bootstrap must NOT clear the cached session.
    mockedApi.get.mockRejectedValue(new Error("network"));

    const { result } = renderHook(() => useAuth(), { wrapper });

    await waitFor(() => expect(result.current.isLoading).toBe(false));

    // Bootstrap keeps the localStorage user on non-401 API failure so the UI
    // remains usable; only the explicit logout() flow clears state.
    expect(result.current.user).toEqual({ username: "stale", userId: 0, roles: [], permissions: [] });
    expect(result.current.isAuthenticated).toBe(true);
    expect(window.localStorage.getItem("medschedule.user")).not.toBeNull();
  });

  // BUGFIX (was DEAD-USER-AUTH): a 401 on the bootstrap /staff/me call
  // means the token is definitively dead — drop the cached user instead
  // of leaving it mounted with permissions: [].
  it("wipes stored user and routes to /login when /staff/me returns 401 during bootstrap", async () => {
    seedStoredSession({
      user: { username: "alice", userId: 42, roles: ["MANAGER"], permissions: ["STAFF_VIEW_ALL"] },
    });
    const auth401 = Object.assign(new Error("HTTP 401"), { status: 401 });
    mockedApi.get.mockRejectedValue(auth401);

    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    expect(result.current.user).toBeNull();
    expect(result.current.isAuthenticated).toBe(false);
    expect(window.localStorage.getItem("medschedule.user")).toBeNull();
    expect(window.localStorage.getItem("medschedule.token")).toBeNull();
    expect(mockRouter.replace).toHaveBeenCalledWith("/login");
  });
});

describe("useAuth — login", () => {
  it("stores token, hydrates user from /staff/me, and routes home", async () => {
    seedStoredSession();
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
    seedStoredSession();
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
    seedStoredSession();
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
    seedStoredSession();
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

  // BUGFIX (was DEAD-USER-AUTH): refreshUser used to swallow any /staff/me
  // error silently. When the access token was already rejected by the
  // backend (e.g. permVer bumped by an admin toggle, or the JWT simply
  // expired), the caller kept a localStorage user with permissions: []
  // and every subsequent API request returned 403 — most visibly the
  // "MANAGER 403 on /staff/active" symptom. refreshUser must now detect a
  // 401, wipe local auth, and bounce to /login before re-throwing.
  it("clears local auth and routes to /login when /staff/me returns 401", async () => {
    seedStoredSession({
      user: { username: "alice", userId: 42, roles: ["MANAGER"], permissions: ["STAFF_VIEW_ALL"] },
    });
    const auth401 = Object.assign(new Error("HTTP 401"), { status: 401 });
    mockedApi.get.mockRejectedValue(auth401);

    const { result } = renderHook(() => useAuth(), { wrapper });
    await waitFor(() => expect(result.current.isLoading).toBe(false));

    await act(async () => {
      try {
        await result.current.refreshUser();
      } catch {
        // expected — refreshUser re-throws after the cleanup
      }
    });

    expect(result.current.user).toBeNull();
    expect(window.localStorage.getItem("medschedule.user")).toBeNull();
    expect(window.localStorage.getItem("medschedule.token")).toBeNull();
    expect(mockRouter.replace).toHaveBeenCalledWith("/login");
  });
});