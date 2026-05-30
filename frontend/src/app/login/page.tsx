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
    <main className="relative grid min-h-screen place-items-center overflow-hidden p-4">
      {/* Gradient background */}
      <div className="pointer-events-none absolute inset-0 bg-gradient-to-br from-slate-900 via-indigo-950 to-slate-900" />

      {/* Animated floating orbs */}
      <div className="pointer-events-none absolute -left-32 -top-32 size-96 rounded-full bg-indigo-500/20 blur-3xl animate-pulse" />
      <div className="pointer-events-none absolute -bottom-24 -right-24 size-80 rounded-full bg-cyan-500/15 blur-3xl animate-pulse [animation-delay:1.5s]" />
      <div className="pointer-events-none absolute left-1/2 top-1/3 size-64 -translate-x-1/2 rounded-full bg-violet-500/10 blur-3xl animate-pulse [animation-delay:3s]" />

      {/* Glassmorphism card */}
      <section className="relative z-10 w-full max-w-md rounded-2xl border border-white/10 bg-white/5 p-8 shadow-2xl shadow-black/40 backdrop-blur-xl">
        {/* Header */}
        <div className="flex items-center gap-4">
          <div className="grid size-12 place-items-center rounded-xl bg-gradient-to-br from-indigo-500 to-cyan-400 text-sm font-bold text-white shadow-lg shadow-indigo-500/30">
            MS
          </div>
          <div>
            <h1 className="text-xl font-bold tracking-tight text-white">
              MedSchedule Pro
            </h1>
            <p className="text-sm text-white/50">
              Hệ thống quản lý lịch công tác
            </p>
          </div>
        </div>

        {/* Divider */}
        <div className="my-6 h-px bg-gradient-to-r from-transparent via-white/15 to-transparent" />

        {/* Form */}
        <form className="space-y-5" onSubmit={handleSubmit}>
          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-white/40">
              Tên đăng nhập
            </span>
            <input
              autoComplete="username"
              className="h-11 w-full rounded-lg border border-white/10 bg-white/5 px-4 text-sm text-white placeholder-white/25 outline-none ring-1 ring-transparent transition-all focus:border-indigo-400/50 focus:bg-white/10 focus:ring-indigo-400/20"
              id="login-username"
              onChange={(event) => setUsername(event.target.value)}
              placeholder="Nhập tên đăng nhập"
              value={username}
            />
          </label>

          <label className="block">
            <span className="mb-1.5 block text-xs font-semibold uppercase tracking-wider text-white/40">
              Mật khẩu
            </span>
            <input
              autoComplete="current-password"
              className="h-11 w-full rounded-lg border border-white/10 bg-white/5 px-4 text-sm text-white placeholder-white/25 outline-none ring-1 ring-transparent transition-all focus:border-indigo-400/50 focus:bg-white/10 focus:ring-indigo-400/20"
              id="login-password"
              onChange={(event) => setPassword(event.target.value)}
              placeholder="Nhập mật khẩu"
              type="password"
              value={password}
            />
          </label>

          {/* Error */}
          {error ? (
            <div className="flex items-start gap-2 rounded-lg border border-rose-500/30 bg-rose-500/10 px-4 py-3 text-sm text-rose-300">
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

          {/* Submit */}
          <button
            className="relative h-11 w-full overflow-hidden rounded-lg bg-gradient-to-r from-indigo-600 to-cyan-500 text-sm font-semibold text-white shadow-lg shadow-indigo-500/25 transition-all hover:shadow-xl hover:shadow-indigo-500/30 disabled:cursor-not-allowed disabled:opacity-50"
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

        {/* Footer */}
        <div className="mt-6 flex items-center justify-between text-sm">
          <span className="rounded-md border border-white/10 bg-white/5 px-3 py-1.5 text-xs text-white/40">
            Demo: admin / admin123
          </span>
          <Link
            className="font-medium text-indigo-400 transition-colors hover:text-indigo-300"
            href="/"
          >
            Về dashboard →
          </Link>
        </div>
      </section>
    </main>
  );
}
