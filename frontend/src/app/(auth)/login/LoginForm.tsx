"use client";

import { useCallback, useRef, useState } from "react";
import { useAuth } from "@/components/auth/AuthProvider";
import { FormInput, FormCheckbox, Button } from "@/components/ui";
import { getErrorMessage } from "@/lib/errors";
import { ScheduleMockup } from "./ScheduleMockup";
import { DemoAccounts, DEMO_ACCOUNTS, type DemoAccount } from "./DemoAccounts";
import { ForgotPasswordModal } from "./ForgotPasswordModal";

const ACCOUNTS_BANNER_DISMISSED_KEY = "login.demoBanner.dismissed.v1";
const CAPTCHA_FAIL_THRESHOLD = 2;

export function LoginForm() {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [remember, setRemember] = useState(false);
  const [showPassword, setShowPassword] = useState(false);
  const [fieldErrors, setFieldErrors] = useState<{
    username?: string;
    password?: string;
  }>({});
  const [error, setError] = useState("");
  const [isLoading, setIsLoading] = useState(false);
  const [forgotOpen, setForgotOpen] = useState(false);
  const { login } = useAuth();

  // Track số lần đăng nhập thất bại để hiện thông báo "đã vượt ngưỡng — kiểm
  // tra Caps Lock / mạng". Không thay thế captcha thật; chỉ UX nudge.
  const failedAttemptsRef = useRef(0);

  const validate = () => {
    const errors: typeof fieldErrors = {};
    if (!username.trim()) errors.username = "Tên đăng nhập không được để trống";
    if (!password.trim()) errors.password = "Mật khẩu không được để trống";
    setFieldErrors(errors);
    return Object.keys(errors).length === 0;
  };

  const performLogin = useCallback(
    async (u: string, p: string) => {
      try {
        await login(u, p);
        failedAttemptsRef.current = 0;
      } catch (err) {
        failedAttemptsRef.current += 1;
        const baseMsg = getErrorMessage(err, "Đăng nhập thất bại");
        const warn =
          failedAttemptsRef.current >= CAPTCHA_FAIL_THRESHOLD
            ? " — Kiểm tra Caps Lock, kết nối mạng hoặc liên hệ quản trị viên."
            : "";
        setError(`${baseMsg}${warn}`);
        throw err;
      }
    },
    [login],
  );

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError("");
    if (!validate()) return;
    setIsLoading(true);
    try {
      await performLogin(username, password);
    } finally {
      setIsLoading(false);
    }
  };

  const handleDemoPick = useCallback(
    async (acc: DemoAccount) => {
      setUsername(acc.username);
      setPassword(acc.password);
      setFieldErrors({});
      setError("");
      setIsLoading(true);
      try {
        await performLogin(acc.username, acc.password);
      } catch {
        // Lỗi đã set trong performLogin; chỉ cần reset loading.
      } finally {
        setIsLoading(false);
      }
    },
    [performLogin],
  );

  return (
    <div className="relative flex min-h-screen w-full bg-surface">
      {/* ============================================================
          Left Panel — Form đăng nhập
          ============================================================ */}
      <section className="flex w-full flex-col justify-between p-8 md:w-1/2 md:p-12 lg:p-16">
        <div className="mx-auto flex w-full max-w-md flex-1 flex-col justify-center">
          {/* Brand */}
          <div className="mb-8 text-center md:text-left">
            <div className="mb-5 flex items-center justify-center gap-3 md:justify-start">
              <div className="relative flex h-12 w-12 items-center justify-center rounded-xl bg-gradient-to-br from-primary to-primary-fixed text-on-primary shadow-lg shadow-primary/30">
                <span
                  aria-hidden="true"
                  className="material-symbols-outlined text-2xl"
                >
                  health_and_safety
                </span>
                <span className="absolute -bottom-1 -right-1 flex h-4 w-4 items-center justify-center rounded-full bg-emerald-500 text-[10px] font-bold text-white ring-2 ring-surface">
                  <span className="material-symbols-outlined text-[10px]">
                    check
                  </span>
                </span>
              </div>
              <div>
                <h1 className="font-title-lg text-title-lg font-bold tracking-tight text-primary">
                  MedSchedule
                </h1>
                <p className="text-label-sm font-medium text-on-surface-variant">
                  Quản Lý Lịch Công Tác
                </p>
              </div>
            </div>
            <h2 className="text-headline-md font-bold leading-tight text-on-surface">
              Chào mừng trở lại 👋
            </h2>
            <p className="mt-1.5 text-body-md text-on-surface-variant">
              Đăng nhập để tiếp tục xếp lịch và theo dõi nhân sự y tế.
            </p>
          </div>

          {/* Form */}
          <form className="space-y-4" onSubmit={handleSubmit} noValidate>
            {error && (
              <div
                className="flex items-start gap-2.5 rounded-lg border border-error/30 bg-error-container/80 px-4 py-3 text-body-sm text-on-error-container animate-in fade-in slide-in-from-top-1 duration-200"
                role="alert"
                aria-live="assertive"
              >
                <span
                  className="material-symbols-outlined mt-0.5 text-[18px] shrink-0"
                  aria-hidden="true"
                >
                  error
                </span>
                <span>{error}</span>
              </div>
            )}

            <FormInput
              label="Tên đăng nhập"
              id="username"
              type="text"
              autoComplete="username"
              placeholder="admin@hospital.vn"
              value={username}
              onChange={(e) => {
                setUsername(e.target.value);
                if (fieldErrors.username)
                  setFieldErrors((f) => ({ ...f, username: undefined }));
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
              placeholder="••••••••"
              value={password}
              onChange={(e) => {
                setPassword(e.target.value);
                if (fieldErrors.password)
                  setFieldErrors((f) => ({ ...f, password: undefined }));
              }}
              error={fieldErrors.password}
              icon="lock"
              trailingAction={
                <button
                  type="button"
                  onClick={() => setShowPassword((v) => !v)}
                  className="flex h-7 w-7 items-center justify-center rounded text-outline transition-colors hover:bg-surface-container-high hover:text-on-surface"
                  aria-label={
                    showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"
                  }
                  title={
                    showPassword ? "Ẩn mật khẩu" : "Hiện mật khẩu"
                  }
                >
                  <span className="material-symbols-outlined text-[20px]">
                    {showPassword ? "visibility_off" : "visibility"}
                  </span>
                </button>
              }
              required
              disabled={isLoading}
            />

            <div className="flex flex-wrap items-center justify-between gap-2">
              <FormCheckbox
                label="Ghi nhớ đăng nhập"
                checked={remember}
                onChange={(e) => setRemember(e.target.checked)}
                disabled={isLoading}
              />
              <button
                type="button"
                onClick={() => setForgotOpen(true)}
                className="rounded text-label-md font-medium text-primary transition-colors hover:text-on-primary-fixed focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30 focus-visible:ring-offset-1"
              >
                Quên mật khẩu?
              </button>
            </div>

            <Button
              type="submit"
              variant="primary"
              size="md"
              fullWidth
              loading={isLoading}
              icon={
                <span
                  className="material-symbols-outlined"
                  aria-hidden="true"
                >
                  login
                </span>
              }
              className="shadow-md shadow-primary/20"
            >
              Đăng nhập
            </Button>
          </form>

          {/* Divider */}
          <div className="my-6 flex items-center gap-3 text-label-sm uppercase tracking-wider text-on-surface-variant">
            <div className="h-px flex-1 bg-outline-variant" />
            <span>hoặc</span>
            <div className="h-px flex-1 bg-outline-variant" />
          </div>

          {/* Demo accounts quick-fill */}
          <DemoAccounts onPick={handleDemoPick} disabled={isLoading} />
        </div>

        {/* Footer */}
        <div className="mx-auto mt-8 flex w-full max-w-md flex-col gap-1 text-center md:flex-row md:justify-between md:text-left">
          <p className="text-label-sm text-outline">
            © 2026 MedSchedule — Nhóm 4 DACN
          </p>
          <div className="flex items-center justify-center gap-3 text-label-sm text-on-surface-variant md:justify-end">
            <a className="hover:text-primary" href="#">
              Hỗ trợ
            </a>
            <span>·</span>
            <a className="hover:text-primary" href="#">
              Điều khoản
            </a>
          </div>
        </div>
      </section>

      {/* ============================================================
          Right Panel — Mockup ma trận lịch (desktop only)
          ============================================================ */}
      <aside
        className="relative hidden md:block md:w-1/2"
        aria-hidden="true"
      >
        <ScheduleMockup />
      </aside>

      {/* Forgot Password Modal */}
      <ForgotPasswordModal
        open={forgotOpen}
        onClose={() => setForgotOpen(false)}
        onSubmit={async (_identity) => {
          // Hệ thống pilot chưa có endpoint reset-password public. Mô phỏng
          // gửi thành công sau 600ms để UX-flow được xác thực. Production sẽ
          // thay bằng POST /api/v1/auth/forgot-password.
          await new Promise((r) => setTimeout(r, 600));
          // Tài khoản demo phổ biến: nếu identity trùng username/email
          // của một trong 3 demo accounts thì vẫn hợp lệ; nếu không thì
          // vẫn báo "đã ghi nhận" nhưng tuỳ backend xử lý tiếp.
          const known = DEMO_ACCOUNTS.some(
            (a) =>
              a.username.toLowerCase() === _identity.toLowerCase() ||
              `${a.username}@hospital.vn` === _identity.toLowerCase(),
          );
          if (!known) {
            // Vẫn trả ok để tránh leak thông tin account enumeration
            return;
          }
        }}
      />
    </div>
  );
}
