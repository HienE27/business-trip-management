import type {
  ApiResponse,
  LoginRequest,
} from "@/types/api";

// ── Re-export domain types kept here for backward compatibility ──────────
export type { ScheduleExportFilters } from "./api/scheduleApi";

// ── Domain modules ──────────────────────────────────────────────────────
import * as staffMethods from "./api/staffApi";
import * as scheduleMethods from "./api/scheduleApi";
import * as periodMethods from "./api/periodApi";
import * as holidayMethods from "./api/holidayApi";
import * as leaveRequestMethods from "./api/leaveRequestApi";
import * as exchangeMethods from "./api/exchangeApi";
import * as notificationMethods from "./api/notificationApi";
import * as auditMethods from "./api/auditApi";
import * as autoScheduleMethods from "./api/autoScheduleApi";
import * as templateMethods from "./api/templateApi";
import * as dashboardMethods from "./api/dashboardApi";
import * as configMethods from "./api/configApi";

// ── Constants ───────────────────────────────────────────────────────────
const API_BASE = process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1";
const LOGIN_PATH = "/login";
const TOKEN_STORAGE_KEY = "medschedule.token";
const REFRESH_TOKEN_STORAGE_KEY = "medschedule.refreshToken";

// ── Storage helpers ─────────────────────────────────────────────────────
function getStoredToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_STORAGE_KEY);
}

function getStoredRefreshToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);
}

// ── Event bus ───────────────────────────────────────────────────────────
/**
 * Global event bus so the api-client can ask the React tree to surface a
 * toast (e.g. for 403 "Bạn không có quyền…" or for network failures) without
 * importing React / hooks directly. ApiClient → window.dispatchEvent →
 * ToastBridge (in app/layout.tsx) → useToast.
 */
export const API_EVENTS = {
  Forbidden: "medschedule:api:forbidden",
  AuthError: "medschedule:api:auth-error",
  NetworkError: "medschedule:api:network-error",
} as const;

export type ApiEventDetail = {
  status?: number;
  message: string;
  path?: string;
};

function emit(name: string, detail: ApiEventDetail) {
  if (typeof window === "undefined") return;
  window.dispatchEvent(new CustomEvent(name, { detail }));
}

// ── Base API client ─────────────────────────────────────────────────────
export class ApiClient {
  private refreshing: Promise<string | null> | null = null;

  private async attemptRefresh(): Promise<string | null> {
    const refresh = getStoredRefreshToken();
    if (!refresh) return null;
    if (!this.refreshing) {
      this.refreshing = (async () => {
        try {
          const res = await fetch(`${API_BASE}/auth/refresh`, {
            method: "POST",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ refreshToken: refresh }),
          });
          if (!res.ok) return null;
          const payload = (await res.json()) as {
            data?: { token?: string; refreshToken?: string };
          };
          const next = payload.data?.token;
          const nextRefresh = payload.data?.refreshToken;
          if (next && typeof window !== "undefined") {
            window.localStorage.setItem(TOKEN_STORAGE_KEY, next);
            if (nextRefresh) {
              window.localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, nextRefresh);
            }
          }
          return next ?? null;
        } catch {
          return null;
        } finally {
          this.refreshing = null;
        }
      })();
    }
    return this.refreshing;
  }

  private clearAuthAndRedirect() {
    if (typeof window === "undefined") return;
    window.localStorage.removeItem("medschedule.user");
    window.localStorage.removeItem(TOKEN_STORAGE_KEY);
    window.localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
    const currentPath = window.location.pathname;
    if (currentPath !== LOGIN_PATH) {
      window.location.replace(LOGIN_PATH);
    }
  }

	  async request<T>(
	    endpoint: string,
	    options: RequestInit & { timeout?: number; _retried?: boolean; cancelSignal?: AbortSignal } = {},
	  ): Promise<ApiResponse<T>> {
	    const headers: Record<string, string> = {
	      "Content-Type": "application/json",
	      ...(options.headers as Record<string, string>),
	    };

	    const token = getStoredToken();
	    if (token) {
	      headers["Authorization"] = `Bearer ${token}`;
	    }

	    const timeout = options.timeout ?? 60000;
	    const controller = new AbortController();
	    const timeoutId = setTimeout(() => controller.abort(), timeout);

	    // If caller provided an external cancel signal, abort when it fires
	    const externalSignal = options.cancelSignal;
	    if (externalSignal) {
	      if (externalSignal.aborted) {
	        controller.abort();
	      } else {
	        externalSignal.addEventListener("abort", () => controller.abort(), { once: true });
	      }
	    }

	    let response: Response;
	    try {
	      response = await fetch(`${API_BASE}${endpoint}`, {
	        ...options,
	        headers,
	        credentials: "include",
	        signal: controller.signal,
	      });
    } catch (error) {
      clearTimeout(timeoutId);
      if (error instanceof Error && error.name === "AbortError") {
        throw Object.assign(new Error(`Yêu cầu hết thời gian chờ (${Math.round(timeout / 1000)}s). Thuật toán có thể đang chạy quá lâu.`), { name: "AbortError" });
      }
      emit(API_EVENTS.NetworkError, { message: "Mất kết nối tới máy chủ. Vui lòng thử lại.", path: endpoint });
      throw error;
    }
    clearTimeout(timeoutId);

    if (!response.ok) {
      const errorData = await response.json().catch(() => ({} as { message?: string }));

      // 401 + we have a refresh token + haven't retried yet → try to refresh
      // once. If refresh succeeds, replay the request; otherwise force a
      // full re-login. This avoids the "kick to /login on a single expired
      // access token" loop.
      if (
        response.status === 401 &&
        !options._retried &&
        getStoredRefreshToken() &&
        endpoint !== "/auth/refresh" &&
        endpoint !== "/auth/login"
      ) {
        const newToken = await this.attemptRefresh();
        if (newToken) {
          return this.request<T>(endpoint, { ...options, _retried: true });
        }
      }

      if (response.status === 401) {
        emit(API_EVENTS.AuthError, {
          status: 401,
          message: errorData.message || "Phiên đăng nhập đã hết hạn. Vui lòng đăng nhập lại.",
          path: endpoint,
        });
        this.clearAuthAndRedirect();
        throw new Error(errorData.message || `HTTP 401 — Phiên đăng nhập hết hạn`);
      }

      if (response.status === 403) {
        // 403 = authenticated but missing permission. We DO NOT redirect —
        // the user is logged in, they just can't see this thing. Toast it
        // and let the page render whatever it has.
        emit(API_EVENTS.Forbidden, {
          status: 403,
          message:
            errorData.message ||
            "Bạn không có quyền thực hiện thao tác này. Liên hệ quản trị viên nếu bạn cho rằng đây là nhầm lẫn.",
          path: endpoint,
        });
        throw new Error(errorData.message || `HTTP 403 — Không có quyền truy cập`);
      }

      throw new Error(errorData.message || `HTTP ${response.status}`);
    }

    return response.json().catch(() => ({ success: true, data: null, message: "Thành công" }));
  }

  // Generic HTTP methods
  async get<T>(endpoint: string, params?: Record<string, string | number | boolean>, requestInit?: Omit<RequestInit, "method" | "body">): Promise<T> {
    let url = endpoint;
    if (params) {
      const qs = new URLSearchParams();
      for (const [k, v] of Object.entries(params)) {
        if (v === undefined || v === null || v === "") continue;
        qs.set(k, String(v));
      }
      url += (url.includes("?") ? "&" : "?") + qs.toString();
    }
    const res = await this.request<T>(url, { method: "GET", ...requestInit });
    // Handle both ApiResponse wrapper and direct array/object responses
    if (res.data !== undefined && res.data !== null) {
      // Check if it's a Page object (Spring Data Page structure with content array)
      if (Array.isArray(res.data)) {
        return res.data;
      }
      // Handle Page object - extract content array
      if (typeof res.data === 'object' && res.data !== null && 'content' in res.data) {
        return (res.data as { content: T[] }).content as T;
      }
      return res.data;
    }
    // If backend returns array directly (without ApiResponse wrapper)
    if (Array.isArray(res) || typeof res === 'object') {
      return res as T;
    }
    return res as T;
  }

  async post<T>(endpoint: string, body: unknown): Promise<T> {
    const res = await this.request<T>(endpoint, {
      method: "POST",
      body: JSON.stringify(body),
    });
    return res.data;
  }

  async put<T>(endpoint: string, body: unknown, params?: Record<string, string | number | boolean>): Promise<T> {
    let url = endpoint;
    if (params) {
      const qs = new URLSearchParams();
      for (const [k, v] of Object.entries(params)) {
        if (v === undefined || v === null || v === "") continue;
        qs.set(k, String(v));
      }
      url += `?${qs.toString()}`;
    }
    const res = await this.request<T>(url, {
      method: "PUT",
      body: body === undefined ? undefined : JSON.stringify(body),
    });
    return res.data;
  }

  async delete<T>(endpoint: string): Promise<T> {
    const res = await this.request<T>(endpoint, { method: "DELETE" });
    return res.data;
  }

  /**
   * Returns a Spring {@link Page} envelope with full pagination metadata
   * (totalElements, totalPages, number, size, first, last, empty, content).
   *
   * Use this on /paginated endpoints. The generic {@link ApiClient#get} drops
   * the metadata by extracting `.content` (so callers don't get page count),
   * which is the opposite of what `<Pagination>` needs.
   */
  async getPage<T>(
    endpoint: string,
    params?: Record<string, string | number | boolean>,
    requestInit?: Omit<RequestInit, "method" | "body">,
  ): Promise<import("@/types/api").Page<T>> {
    let url = endpoint;
    if (params) {
      const qs = new URLSearchParams();
      for (const [k, v] of Object.entries(params)) {
        if (v === undefined || v === null || v === "") continue;
        qs.set(k, String(v));
      }
      url += (url.includes("?") ? "&" : "?") + qs.toString();
    }
    const res = await this.request<import("@/types/api").Page<T>>(url, { method: "GET", ...requestInit });
    if (res?.data && typeof res.data === "object" && "content" in res.data) {
      return res.data as import("@/types/api").Page<T>;
    }
    // Backward-compat: backend returning a bare `Page<T>` without the
    // ApiResponse wrapper (shouldn't happen with current controllers, but be safe).
    if (res && typeof res === "object" && "content" in (res as object)) {
      return res as unknown as import("@/types/api").Page<T>;
    }
    return { content: [], totalElements: 0, totalPages: 0, number: 0, size: 0, first: true, last: true, empty: true };
  }

  /**
   * Raw fetch with automatic Authorization header. Use this for endpoints
   * that return non-JSON payloads (Blob for file downloads, etc.) or accept
   * FormData (file uploads).
   */
  async fetchWithAuth(endpoint: string, init?: RequestInit): Promise<Response> {
    const token = getStoredToken();
    const headers: Record<string, string> = {};
    if (token) headers["Authorization"] = `Bearer ${token}`;
    return fetch(`${API_BASE}${endpoint}`, {
      ...init,
      headers: { ...headers, ...((init?.headers as Record<string, string>) || {}) },
      credentials: "include",
    });
  }

  // Auth
  async login(data: LoginRequest): Promise<ApiResponse<import("@/types/api").AuthResponse>> {
    return this.request<import("@/types/api").AuthResponse>("/auth/login", {
      method: "POST",
      body: JSON.stringify(data),
    });
  }

  async logout(): Promise<ApiResponse<void>> {
    return this.request<void>("/auth/logout", {
      method: "POST",
    });
  }
}

// ── Build the combined API object ───────────────────────────────────────
const client = new ApiClient();

/**
 * Maps a domain module's exported functions to new functions with the
 * `client` parameter pre-filled, preserving full signature types (no
 * index-signature so generic class methods like `getPage<T>` are not
 * shadowed).
 */
type BoundModule<M> = {
  [K in keyof M]: M[K] extends (c: ApiClient, ...args: infer A) => infer R
    ? (...args: A) => R
    : never;
};

/* eslint-disable @typescript-eslint/no-explicit-any */
function bindModule<M extends Record<string, (c: ApiClient, ...args: any[]) => any>>(
  mod: M,
): BoundModule<M> {
  const result = {} as Record<string, any>;
  for (const [name, fn] of Object.entries(mod)) {
    result[name] = (...args: any[]) => fn(client, ...args);
  }
  return result as BoundModule<M>;
}

/**
 * The single `api` object consumed by every component.  It carries the base
 * HTTP helpers (request, get, post, put, delete, getPage, login, logout,
 * fetchWithAuth) **plus** every domain-specific method bound to this client.
 */
export const api: ApiClient &
  BoundModule<typeof staffMethods> &
  BoundModule<typeof scheduleMethods> &
  BoundModule<typeof periodMethods> &
  BoundModule<typeof holidayMethods> &
  BoundModule<typeof leaveRequestMethods> &
  BoundModule<typeof exchangeMethods> &
  BoundModule<typeof notificationMethods> &
  BoundModule<typeof auditMethods> &
  BoundModule<typeof autoScheduleMethods> &
  BoundModule<typeof templateMethods> &
  BoundModule<typeof dashboardMethods> &
  BoundModule<typeof configMethods> =
  Object.assign(
    client,
    bindModule(staffMethods),
    bindModule(scheduleMethods),
    bindModule(periodMethods),
    bindModule(holidayMethods),
    bindModule(leaveRequestMethods),
    bindModule(exchangeMethods),
    bindModule(notificationMethods),
    bindModule(auditMethods),
    bindModule(autoScheduleMethods),
    bindModule(templateMethods),
    bindModule(dashboardMethods),
    bindModule(configMethods),
  );

export default api;
