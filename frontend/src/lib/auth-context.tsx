"use client";

import { createContext, useContext, useState, useEffect, useCallback, type ReactNode } from "react";
import { useRouter } from "next/navigation";
import { api } from "./api-client";
import type { AuthResponse, Staff } from "@/types/api";

interface AuthContextType {
  user: Staff | null;
  isAuthenticated: boolean;
  isLoading: boolean;
  login: (username: string, password: string) => Promise<void>;
  logout: () => void;
  hasRole: (role: string) => boolean;
}

const AuthContext = createContext<AuthContextType | undefined>(undefined);

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<Staff | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const router = useRouter();

  const logout = useCallback(() => {
    api.setToken(null);
    setUser(null);
    localStorage.removeItem("currentUser");
    router.push("/login");
  }, [router]);

  const hasRole = useCallback(
    (role: string): boolean => {
      if (!user) return false;
      return user.roles?.includes(role) ?? false;
    },
    [user]
  );

  useEffect(() => {
    const token = api.getToken();
    if (token) {
      api.getCurrentStaff()
        .then((res) => {
          if (res.success && res.data) {
            setUser(res.data);
          } else {
            logout();
          }
        })
        .catch(() => logout())
        .finally(() => setIsLoading(false));
    } else {
      setIsLoading(false);
    }
  }, [logout]);

  const login = async (username: string, password: string) => {
    const res = await api.login({ username, password });
    if (!res.success || !res.data) throw new Error(res.message || "Đăng nhập thất bại");

    const { token, roles } = res.data;
    api.setToken(token);

    const userRes = await api.getCurrentStaff();
    if (userRes.success && userRes.data) {
      const userWithRoles = { ...userRes.data, roles };
      setUser(userWithRoles);
      localStorage.setItem("currentUser", JSON.stringify(userWithRoles));
      router.push("/dashboard");
    } else {
      throw new Error("Không thể lấy thông tin người dùng");
    }
  };

  return (
    <AuthContext.Provider
      value={{
        user,
        isAuthenticated: !!user,
        isLoading,
        login,
        logout,
        hasRole,
      }}
    >
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth() {
  const context = useContext(AuthContext);
  if (context === undefined) {
    throw new Error("useAuth must be used within an AuthProvider");
  }
  return context;
}
