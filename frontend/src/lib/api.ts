const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

const TOKEN_KEY = "medschedule.token";

function getToken(): string | null {
  if (typeof window === "undefined") return null;
  return window.localStorage.getItem(TOKEN_KEY);
}

function clearAuth() {
  if (typeof window === "undefined") return;
  window.localStorage.removeItem(TOKEN_KEY);
  window.localStorage.removeItem("medschedule.user");
  window.location.href = "/login";
}

type ApiEnvelope<T> = {
  success: boolean;
  message?: string;
  data: T;
};

export async function apiFetch<T = unknown>(
  path: string,
  options: RequestInit = {},
): Promise<T> {
  const token = getToken();
  const headers: Record<string, string> = {
    ...(options.headers as Record<string, string>),
  };

  if (!(options.body instanceof FormData)) {
    headers["Content-Type"] = "application/json";
  }

  if (token) {
    headers["Authorization"] = `Bearer ${token}`;
  }

  const response = await fetch(`${API_BASE_URL}${path}`, {
    ...options,
    headers,
  });

  if (response.status === 401) {
    clearAuth();
    throw new Error("Phiên đăng nhập hết hạn. Vui lòng đăng nhập lại.");
  }

  if (!response.ok) {
    const errorBody = await response.json().catch(() => null);
    if (errorBody && errorBody.fieldErrors && typeof errorBody.fieldErrors === "object") {
      const fieldMsgs = Object.entries(errorBody.fieldErrors)
        .map(([_, msg]) => `• ${msg}`)
        .join("\n");
      const err = new Error(`Thông tin nhập chưa hợp lệ:\n${fieldMsgs}`);
      (err as any).fieldErrors = errorBody.fieldErrors;
      throw err;
    }
    throw new Error(
      errorBody?.message ?? `Request failed: ${response.status} ${response.statusText}`,
    );
  }

  // Handle 204 No Content
  if (response.status === 204) {
    return undefined as T;
  }

  const envelope = (await response.json()) as ApiEnvelope<T>;
  return envelope.data;
}

// ── Convenience methods ──────────────────────────────────────

export const api = {
  get<T = unknown>(path: string) {
    return apiFetch<T>(path, { method: "GET" });
  },

  post<T = unknown>(path: string, body?: unknown) {
    return apiFetch<T>(path, {
      method: "POST",
      body: body instanceof FormData ? body : (body != null ? JSON.stringify(body) : undefined),
    });
  },

  put<T = unknown>(path: string, body?: unknown) {
    return apiFetch<T>(path, {
      method: "PUT",
      body: body instanceof FormData ? body : (body != null ? JSON.stringify(body) : undefined),
    });
  },

  delete<T = unknown>(path: string) {
    return apiFetch<T>(path, { method: "DELETE" });
  },
};
