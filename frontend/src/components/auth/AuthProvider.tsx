"use client";

import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from "react";
import { useRouter } from "next/navigation";
import { api } from "@/lib/api";
import type { Staff } from "@/types/api";

const AUTH_STORAGE_KEY = "medschedule.user";

type AuthUser = {
  username: string;
  userId: number;
  roles: string[];
};

type AuthState = {
  token: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => Promise<void>;
  refreshUser: () => Promise<void>;
};

type LoginResponse = {
  success?: boolean;
  message?: string;
  data?: {
    token?: string;
    userId?: number;
    username?: string;
    roles?: string[];
  };
};

const TOKEN_STORAGE_KEY = "medschedule.token";

const AuthContext = createContext<AuthState | null>(null);
const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

function toAuthUser(staff: Staff): AuthUser {
  return {
    username: staff.username,
    userId: staff.id,
    roles: staff.roles ?? [],
  };
}

function persistAuthUser(user: AuthUser | null) {
  if (typeof window === "undefined") {
    return;
  }

  if (user) {
    window.localStorage.setItem(AUTH_STORAGE_KEY, JSON.stringify(user));
    return;
  }

  window.localStorage.removeItem(AUTH_STORAGE_KEY);
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [mounted, setMounted] = useState(false);
  const [user, setUser] = useState<AuthUser | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();

  const refreshUser = useCallback(async () => {
    const currentStaff = await api.get<Staff>("/staff/me");
    const nextUser = toAuthUser(currentStaff);
    persistAuthUser(nextUser);
    setUser(nextUser);
  }, []);

  useEffect(() => {
    let active = true;

    const bootstrapAuth = async () => {
      setMounted(true);
      const savedUser = window.localStorage.getItem(AUTH_STORAGE_KEY);
      if (savedUser) {
        try {
          setUser(JSON.parse(savedUser) as AuthUser);
        } catch {
          persistAuthUser(null);
        }
      }

      try {
        const currentStaff = await api.get<Staff>("/staff/me");
        if (!active) return;
        const nextUser = toAuthUser(currentStaff);
        persistAuthUser(nextUser);
        setUser(nextUser);
      } catch {
        if (!active) return;
        persistAuthUser(null);
        setUser(null);
      } finally {
        if (active) {
          setIsLoading(false);
        }
      }
    };

    void bootstrapAuth();

    return () => {
      active = false;
    };
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      credentials: "include",
      body: JSON.stringify({ username, password }),
    });

    if (!response.ok) {
      throw new Error("Đăng nhập thất bại. Kiểm tra backend hoặc tài khoản.");
    }

    // Parse body ONCE to extract token and user data
    const payload = (await response.json()) as LoginResponse;
    const token =
      response.headers.get("X-Auth-Token") ?? payload.data?.token;

    const fallbackUser = {
      username: payload.data?.username ?? username,
      userId: payload.data?.userId ?? 0,
      roles: payload.data?.roles ?? [],
    };

    if (token) {
      window.localStorage.setItem(TOKEN_STORAGE_KEY, token);
    }
    persistAuthUser(fallbackUser);
    setUser(fallbackUser);
    setIsLoading(true);

    try {
      await refreshUser();
      router.replace("/");
    } finally {
      setIsLoading(false);
    }
  }, [refreshUser, router]);

  const logout = useCallback(async () => {
    try {
      await api.logout();
    } finally {
      window.localStorage.removeItem(TOKEN_STORAGE_KEY);
      persistAuthUser(null);
      setUser(null);
      setIsLoading(false);
      router.replace("/login");
    }
  }, [router]);

  const value = useMemo(
    () => ({
      token: null,
      user,
      isAuthenticated: Boolean(mounted && user),
      isLoading: !mounted || isLoading,
      login,
      logout,
      refreshUser,
    }),
    [isLoading, login, logout, mounted, refreshUser, user],
  );

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth() {
  const value = useContext(AuthContext);

  if (!value) {
    throw new Error("useAuth must be used inside AuthProvider");
  }

  return value;
}
