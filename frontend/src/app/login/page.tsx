"use client";

import Link from "next/link";
import { FormEvent, useState } from "react";
import { useRouter } from "next/navigation";
import { useAuth } from "@/components/auth/AuthProvider";

export default function LoginPage() {
  const router = useRouter();
  const { login } = useAuth();
  const [username, setUsername] = useState("admin");
  const [password, setPassword] = useState("admin123");
  const [error, setError] = useState("");
  const [loading, setLoading] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError("");
    setLoading(true);

    try {
      await login(username, password);
      router.push("/");
    } catch (err) {
      setError(err instanceof Error ? err.message : "Không thể đăng nhập.");
    } finally {
      setLoading(false);
    }
  }

  return (
    <main className="grid min-h-screen place-items-center bg-[#111418] p-4 text-white">
      <section className="w-full max-w-md rounded-lg border border-white/10 bg-[#171c22] p-8 shadow-[0_8px_24px_rgba(0,0,0,0.24)]">
        <div className="flex items-center gap-3">
          <div className="grid size-11 place-items-center rounded-lg bg-white text-sm font-bold text-[#111418] shadow-[0_1px_2px_rgba(0,0,0,0.18)]">
            MS
          </div>
          <div>
            <h1 className="text-xl font-semibold leading-6 tracking-normal text-white">
              MedSchedule Pro
            </h1>
            <p className="text-sm leading-5 text-white/50">
              Hệ thống quản lý lịch công tác
            </p>
          </div>
        </div>

        <div className="my-6 h-px bg-white/10" />

        <form className="space-y-4" onSubmit={handleSubmit}>
          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold uppercase leading-4 text-white/45">
              Tên đăng nhập
            </span>
            <input
              autoComplete="username"
              className="h-11 w-full rounded-lg border border-white/10 bg-white/6 px-4 text-sm text-white placeholder-white/30 outline-none focus:border-white/24 focus:bg-white/10"
              id="login-username"
              onChange={(event) => setUsername(event.target.value)}
              placeholder="Nhập tên đăng nhập"
              value={username}
            />
          </label>

          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold uppercase leading-4 text-white/45">
              Mật khẩu
            </span>
            <input
              autoComplete="current-password"
              className="h-11 w-full rounded-lg border border-white/10 bg-white/6 px-4 text-sm text-white placeholder-white/30 outline-none focus:border-white/24 focus:bg-white/10"
              id="login-password"
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Nhập mật khẩu"
              type="password"
              value={password}
            />
          </label>

          {error ? (
            <div className="flex items-start gap-2 rounded-lg border border-rose-300/30 bg-rose-400/10 px-4 py-3 text-sm leading-5 text-rose-200">
              <svg
                className="mt-0.5 size-4 shrink-0"
                fill="none"
                stroke="currentColor"
                strokeWidth={2}
                viewBox="0 0 24 24"
              >
                <circle cx={12} cy={12} r={10} />
                <line x1={12} x2={12} y1={8} y2={12} />
                <line x1={12} x2={12.01} y1={16} y2={16} />
              </svg>
              {error}
            </div>
          ) : null}

          <button
            className="h-11 w-full rounded-lg bg-white text-sm font-semibold text-[#111418] shadow-[0_1px_2px_rgba(0,0,0,0.18)] disabled:opacity-50"
            disabled={loading}
            id="login-submit"
            type="submit"
          >
            {loading ? (
              <span className="flex items-center justify-center gap-2">
                <svg
                  className="size-4 animate-spin"
                  fill="none"
                  viewBox="0 0 24 24"
                >
                  <circle
                    className="opacity-25"
                    cx={12}
                    cy={12}
                    r={10}
                    stroke="currentColor"
                    strokeWidth={4}
                  />
                  <path
                    className="opacity-75"
                    d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z"
                    fill="currentColor"
                  />
                </svg>
                Đang đăng nhập…
              </span>
            ) : (
              "Đăng nhập"
            )}
          </button>
        </form>

        <div className="mt-6 flex items-center justify-between text-sm">
          <span className="rounded-lg border border-white/10 bg-white/6 px-3 py-1.5 text-xs leading-4 text-white/45">
            Demo: admin / admin123
          </span>
          <Link
            className="font-medium text-white/72 hover:text-white"
            href="/"
          >
            Về dashboard →
          </Link>
        </div>
      </section>
    </main>
  );
}
