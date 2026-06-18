"use client";

import { useState } from "react";
import { useAuth } from "@/components/auth/AuthProvider";
import { FormInput, FormCheckbox, Button } from "@/components/ui";
import { getErrorMessage } from "@/lib/errors";

export function LoginForm() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<{ username?: string; password?: string }>({});
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const { login } = useAuth();

  const validate = () => {
    const errors: typeof fieldErrors = {};
    if (!username.trim()) errors.username = "Tên đăng nhập không được để trống";
    if (!password.trim()) errors.password = "Mật khẩu không được để trống";
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!validate()) return;

    setIsLoading(true);
    try {
      await login(username, password);
    } catch (err) {
      setError(getErrorMessage(err, "Đăng nhập thất bại"));
    } finally {
      setIsLoading(false);
    }
  };

  return (
    <div className="flex min-h-screen w-full bg-surface">
      {/* Left — Form Panel */}
      <section className="flex w-1/2 flex-col justify-center p-16">
        {/* Logo + Brand */}
        <div className="mb-10 text-center md:text-left">
          <div className="mb-6 flex items-center justify-center md:justify-start gap-3">
            <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-primary text-on-primary shadow-sm">
              <span aria-hidden="true" className="material-symbols-outlined text-xl text-on-primary">
                health_and_safety
              </span>
            </div>
            <h1 className="font-title-lg text-title-lg text-primary tracking-tight">
              Quản Lý Lịch
            </h1>
          </div>
          <h2 className="font-display-lg text-display-lg text-on-surface mb-2">
            Đăng nhập hệ thống
          </h2>
          <p className="font-body-md text-body-md text-on-surface-variant">
            Hệ thống Quản Lý Lịch Công Tác
          </p>
        </div>

        {/* Form */}
        <form className="space-y-5" onSubmit={handleSubmit} noValidate>
          {error && (
            <div
              className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-body-sm text-error flex items-center gap-2"
              role="alert"
            >
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">error</span>
              {error}
            </div>
          )}

          <FormInput
            label="Tên đăng nhập"
            id="username"
            type="text"
            autoComplete="username"
            placeholder="Nhập tên đăng nhập"
            value={username}
            onChange={(e) => {
              setUsername(e.target.value);
              if (fieldErrors.username) setFieldErrors((f) => ({ ...f, username: undefined }));
            }}
            error={fieldErrors.username}
            icon="person"
            required
            disabled={isLoading}
            autoFocus
          />

          <FormInput
            label="Mật khẩu"
            id="password"
            type={showPassword ? "text" : "password"}
            autoComplete="current-password"
            placeholder="Nhập mật khẩu"
            value={password}
            onChange={(e) => {
              setPassword(e.target.value);
              if (fieldErrors.password) setFieldErrors((f) => ({ ...f, password: undefined }));
            }}
            error={fieldErrors.password}
            icon="lock"
            trailingAction={
              <button
                type="button"
                onClick={() => setShowPassword((v) => !v)}
                className="flex h-7 w-7 items-center justify-center rounded text-outline hover:text-on-surface transition-colors"
                aria-label={showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"}
              >
                <span className="material-symbols-outlined text-[20px]">
                  {showPassword ? "visibility_off" : "visibility"}
                </span>
              </button>
            }
            required
            disabled={isLoading}
          />

          <div className="flex items-center justify-between">
            <FormCheckbox
              label="Ghi nhớ đăng nhập"
              checked={remember}
              onChange={(e) => setRemember(e.target.checked)}
              disabled={isLoading}
            />
            <button
              className="font-label-md text-primary hover:text-on-primary-fixed transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 rounded"
              type="button"
            >
              Quên mật khẩu?
            </button>
          </div>

          <div className="mt-6">
            <Button
              type="submit"
              variant="primary"
              size="md"
              fullWidth
              loading={isLoading}
              icon={<span className="material-symbols-outlined" aria-hidden="true">login</span>}
            >
              Đăng nhập
            </Button>
          </div>
        </form>

        <p className="mt-8 text-center font-body-sm text-outline">
          Quản lý Lịch Công Tác — Nhóm 4
        </p>
      </section>

      {/* Right — Illustration */}
      <section className="relative block w-1/2 overflow-hidden" aria-hidden="true">
        <div
          className="absolute inset-0 z-10 mix-blend-multiply"
          style={{ background: "linear-gradient(to bottom right, rgba(0,74,198,0.1), transparent 60%)" }}
        />
        <div className="absolute top-[-10%] left-[-10%] z-0 h-[40%] w-[40%] rounded-full bg-primary-container/5 blur-3xl pointer-events-none" />
        <div className="absolute bottom-[-10%] right-[-10%] z-0 h-[30%] w-[30%] rounded-full bg-secondary-container/10 blur-3xl" />

        <div className="absolute bottom-12 right-12 z-20 max-w-sm rounded-xl border border-surface-container-highest bg-surface-container-lowest/80 p-6 shadow-lg backdrop-blur-md">
          <div className="mb-4 flex items-center gap-4">
            <div className="flex h-12 w-12 items-center justify-center rounded-full bg-secondary-container">
              <span className="material-symbols-outlined text-[20px] text-secondary fill">schedule</span>
            </div>
            <div>
              <h3 className="font-title-lg text-title-lg text-on-surface">Hiệu Suất Tối Đa</h3>
              <p className="font-body-sm text-body-sm text-on-surface-variant">Điều phối nhân sự 24/7</p>
            </div>
          </div>
          <p className="font-body-sm text-body-sm text-on-surface-variant leading-relaxed">
            Tối ưu hóa phân bổ ca trực, giảm xung đột và tăng hiệu quả làm việc của đội ngũ y tế.
          </p>
        </div>
      </section>
    </div>
  );
}
