"use client";

import { useEffect } from "react";
import { useToast } from "@/hooks/useToast";
import { API_EVENTS, type ApiEventDetail } from "@/lib/api-client";

/**
 * Wires the api-client's window-level events to the toast system.
 *
 * Mounted once near the root of the app (next to the ToastProvider). It
 * subscribes to:
 *
 *   - {@link API_EVENTS.Forbidden}    → error toast (no redirect)
 *   - {@link API_EVENTS.AuthError}    → error toast (the api-client also
 *                                        kicks the user to /login, so this
 *                                        is informational only)
 *   - {@link API_EVENTS.NetworkError} → error toast
 *
 * Multiple identical toasts within 1s are de-duplicated to avoid the
 * "toast storm" when a page fires N parallel requests against a forbidden
 * endpoint on mount.
 */
export function ApiToastBridge() {
  const toast = useToast();

  useEffect(() => {
    const seen = new Map<string, number>();
    const DEBOUNCE_MS = 1000;

    function shouldEmit(key: string): boolean {
      const now = Date.now();
      const last = seen.get(key) ?? 0;
      if (now - last < DEBOUNCE_MS) return false;
      seen.set(key, now);
      return true;
    }

    function onForbidden(e: Event) {
      const detail = (e as CustomEvent<ApiEventDetail>).detail;
      const msg = detail?.message ?? "Bạn không có quyền thực hiện thao tác này.";
      if (shouldEmit("forbidden:" + msg)) {
        toast.error(msg, 5000);
      }
    }
    function onAuth(e: Event) {
      const detail = (e as CustomEvent<ApiEventDetail>).detail;
      const msg = detail?.message ?? "Phiên đăng nhập đã hết hạn.";
      if (shouldEmit("auth:" + msg)) {
        toast.warning(msg, 4000);
      }
    }
    function onNetwork(e: Event) {
      const detail = (e as CustomEvent<ApiEventDetail>).detail;
      const msg = detail?.message ?? "Mất kết nối tới máy chủ.";
      if (shouldEmit("network:" + msg)) {
        toast.error(msg, 4000);
      }
    }

    window.addEventListener(API_EVENTS.Forbidden, onForbidden);
    window.addEventListener(API_EVENTS.AuthError, onAuth);
    window.addEventListener(API_EVENTS.NetworkError, onNetwork);
    return () => {
      window.removeEventListener(API_EVENTS.Forbidden, onForbidden);
      window.removeEventListener(API_EVENTS.AuthError, onAuth);
      window.removeEventListener(API_EVENTS.NetworkError, onNetwork);
    };
  }, [toast]);

  return null;
}