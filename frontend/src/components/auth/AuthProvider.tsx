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

type AuthUser = {
  username: string;
  roles: string[];
};

type AuthState = {
  token: string | null;
  user: AuthUser | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
};

type LoginResponse = {
  success?: boolean;
  message?: string;
  data?: {
    token?: string;
    username?: string;
    roles?: string[];
  };
};

const AuthContext = createContext<AuthState | null>(null);
const API_BASE_URL =
  process.env.NEXT_PUBLIC_API_BASE_URL ?? "http://localhost:8080/api/v1";

export function AuthProvider({ children }: { children: ReactNode }) {
  const [mounted, setMounted] = useState(false);
  const [token, setToken] = useState<string | null>(null);
  const [user, setUser] = useState<AuthUser | null>(null);

  useEffect(() => {
    setMounted(true);
    const savedToken = window.localStorage.getItem("medschedule.token");
    const savedUser = window.localStorage.getItem("medschedule.user");
    if (savedToken) setToken(savedToken);
    if (savedUser) {
      try {
        setUser(JSON.parse(savedUser) as AuthUser);
      } catch {
        window.localStorage.removeItem("medschedule.user");
      }
    }
  }, []);

  const login = useCallback(async (username: string, password: string) => {
    const response = await fetch(`${API_BASE_URL}/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username, password }),
    });

    if (!response.ok) {
      throw new Error("Đăng nhập thất bại. Kiểm tra backend hoặc tài khoản.");
    }

    const payload = (await response.json()) as LoginResponse;
    const nextToken = payload.data?.token;

    if (!nextToken) {
      throw new Error(payload.message ?? "Backend không trả về JWT token.");
    }

    const nextUser = {
      username: payload.data?.username ?? username,
      roles: payload.data?.roles ?? [],
    };

    window.localStorage.setItem("medschedule.token", nextToken);
    window.localStorage.setItem("medschedule.user", JSON.stringify(nextUser));
    setToken(nextToken);
    setUser(nextUser);
  }, []);

  const logout = useCallback(() => {
    window.localStorage.removeItem("medschedule.token");
    window.localStorage.removeItem("medschedule.user");
    setToken(null);
    setUser(null);
  }, []);

  const value = useMemo(
    () => ({
      token,
      user,
      isAuthenticated: Boolean(mounted && token),
      isLoading: !mounted,
      login,
      logout,
    }),
    [login, logout, mounted, token, user],
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
