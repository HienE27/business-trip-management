"use client";

import { useState } from "react";
import { useAuth } from "@/components/auth/AuthProvider";

export function LoginForm() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(false);
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    setIsLoading(true);
    try {
      await login(username, password);
    } catch (err) {
      setError(err instanceof Error ? err.message : "Dang nhap that bai");
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen w-full bg-surface">
      {/* Left side — Login Form */}
      <section className="flex w-1/2 flex-col justify-center p-16">
        {/* Logo + Brand */}
        <div className="mb-10 text-center md:text-left">
          <div className="mb-6 flex items-center justify-center md:justify-start gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary text-white shadow-sm">
              <span aria-hidden="true" className="material-symbols-outlined text-xl">
                health_and_safety
              </span>
            </div>
            <h1 className="font-headline-md text-headline-md text-primary tracking-tight">
              Quan Ly Lich
            </h1>
          </div>
          <h2 className="font-display-lg text-display-lg text-on-surface mb-2">
            Dang nhap he thong
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant">
            He thong Quan Ly Lich Cong Tac
          </p>
        </div>

        {/* Form */}
        <form className="space-y-5" onSubmit={handleSubmit}>
          {error && (
            <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 font-body-sm text-error">
              {error}
            </div>
          )}

          <div className="space-y-1.5">
            <label className="block font-label-md text-label-md text-on-surface" htmlFor="username">
              Ten dang nhap
            </label>
            <div className="relative">
              <span
                aria-hidden="true"
                className="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-outline"
              >
                person
              </span>
              <input
                autoComplete="username"
                className="block h-12 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-4 pl-12 pr-4 font-body-md text-body-md text-on-surface outline-none transition-colors placeholder:text-outline focus:border-primary focus:ring-2 focus:ring-primary/20 shadow-sm"
                id="username"
                name="username"
                onChange={(e) => setUsername(e.target.value)}
                placeholder="Nhap ten dang nhap"
                required
                spellCheck={false}
                type="text"
                value={username}
              />
            </div>
          </div>

          <div className="space-y-1.5">
            <label className="block font-label-md text-label-md text-on-surface" htmlFor="password">
              Mat khau
            </label>
            <div className="relative">
              <span
                aria-hidden="true"
                className="material-symbols-outlined absolute left-4 top-1/2 -translate-y-1/2 text-outline"
              >
                lock
              </span>
              <input
                autoComplete="current-password"
                className="block h-12 w-full rounded-lg border border-outline-variant bg-surface-container-lowest px-4 pl-12 pr-4 font-body-md text-body-md text-on-surface outline-none transition-colors placeholder:text-outline focus:border-primary focus:ring-2 focus:ring-primary/20 shadow-sm"
                id="password"
                name="password"
                onChange={(e) => setPassword(e.target.value)}
                placeholder="Nhap mat khau"
                required
                type="password"
                value={password}
              />
            </div>
          </div>

          <div className="flex items-center justify-between pt-2">
            <label className="flex items-center gap-2 cursor-pointer select-none">
              <div className="relative flex items-center">
                <input
                  className="peer appearance-none h-4 w-4 border border-outline-variant rounded bg-surface-container-lowest transition-colors cursor-pointer focus:ring-2 focus:ring-primary/20 focus:outline-none"
                  onChange={(e) => setRemember(e.target.checked)}
                  type="checkbox"
                  checked={remember}
                />
                <span className="material-symbols-outlined absolute text-[12px] text-on-primary opacity-0 peer-checked:opacity-100 pointer-events-none left-1/2 top-1/2 -translate-x-1/2 -translate-y-1/2 font-bold">
                  check
                </span>
              </div>
              <span className="font-label-md text-label-md text-on-surface-variant">Ghi nho dang nhap</span>
            </label>
            <button
              className="font-label-md text-label-md text-primary hover:text-on-primary-fixed transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 rounded"
              type="button"
            >
              Quen mat khau?
            </button>
          </div>

          <button
            className="mt-6 flex h-10 w-full items-center justify-center gap-2 rounded-lg bg-primary px-4 font-label-md text-label-md text-white shadow-sm hover:opacity-90 active:scale-[0.98] transition-all focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
            disabled={isLoading}
            type="submit"
          >
            {isLoading ? (
              <>
                <svg
                  aria-hidden="true"
                  className="h-5 w-5 animate-spin"
                  fill="none"
                  viewBox="0 0 24 24"
                >
                  <circle
                    className="opacity-25"
                    cx="12"
                    cy="12"
                    r="10"
                    stroke="currentColor"
                    strokeWidth="4"
                  />
                  <path
                    className="opacity-75"
                    d="M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4z"
                    fill="currentColor"
                  />
                </svg>
                <span>Dang nhap...</span>
              </>
            ) : (
              <>
                <span aria-hidden="true" className="material-symbols-outlined text-[20px]">
                  login
                </span>
                <span>Dang nhap</span>
              </>
            )}
          </button>
        </form>

        <p className="mt-8 text-center font-body-sm text-body-sm text-outline">
          Quan ly Lich Cong Tac — Nhom 4
        </p>
      </section>

      {/* Right side — Illustration */}
      <section className="relative block w-1/2 overflow-hidden">
        {/* Gradient overlay */}
        <div
          aria-hidden="true"
          className="absolute inset-0 z-10 mix-blend-multiply"
          style={{
            background: "linear-gradient(to bottom right, rgba(0,74,198,0.1), transparent 60%)",
          }}
        />

        {/* Decorative background circles */}
        <div
          aria-hidden="true"
          className="absolute top-[-10%] left-[-10%] z-0 h-[40%] w-[40%] rounded-full bg-primary-container/5 blur-3xl pointer-events-none"
        />
        <div
          aria-hidden="true"
          className="absolute bottom-[-10%] right-[-10%] z-0 h-[30%] w-[30%] rounded-full bg-secondary-container/10 blur-3xl pointer-events-none"
        />

        {/* Info card */}
        <div className="absolute bottom-12 right-12 z-20 max-w-sm rounded-xl border border-surface-container-highest bg-surface-container-lowest/80 p-6 shadow-lg backdrop-blur-md">
          <div className="mb-4 flex items-center gap-4">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-secondary-container">
              <span className="material-symbols-outlined text-[20px] text-secondary fill">
                schedule
              </span>
            </div>
            <div>
              <h3 className="font-title-lg text-title-lg text-on-surface">Hieu Suat Toi Da</h3>
              <p className="font-body-sm text-body-sm text-on-surface-variant">
                Dieu phoi nhan su 24/7
              </p>
            </div>
          </div>
          <p className="font-body-sm text-body-sm text-on-surface-variant leading-relaxed">
            Toi uu hoa phan bo ca truc, giam xung dot va tang hieu qua lam viec cua doi ngu y te.
          </p>
        </div>
      </section>
    </div>
  );
}
