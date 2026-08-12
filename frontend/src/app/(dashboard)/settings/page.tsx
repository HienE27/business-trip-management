"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { useToast } from "@/hooks/useToast";
import { ROLE_LABELS } from "@/lib/roleLabels";
import { Button } from "@/components/ui";
import { BackButton } from "@/components/ui/BackButton";

type ThemeMode = "light" | "dark" | "system";
type DensityMode = "compact" | "comfortable" | "spacious";

export default function SettingsPage() {
  return <SettingsContent />;
}

function SettingsContent() {
  const toast = useToast();
  const toastRef = useRef(toast);
  useEffect(() => { toastRef.current = toast; });
  // Email section
  const [emailEnabled, setEmailEnabled] = useState(false);
  const [conflictEmailEnabled, setConflictEmailEnabled] = useState(false);
  const [savingEmail, setSavingEmail] = useState(false);
  const [emailMsg, setEmailMsg] = useState<{ type: "success" | "error"; text: string } | null>(null);
  const [loadingEmail, setLoadingEmail] = useState(true);

  // UI Preferences section
  const [theme, setTheme] = useState<ThemeMode>("system");
  const [density, setDensity] = useState<DensityMode>("comfortable");
  const [savingPrefs, setSavingPrefs] = useState(false);

  // Account section
  const [currentStaff, setCurrentStaff] = useState<{
    fullName: string;
    email: string;
    phone: string;
    username: string;
    specialtyName: string | null;
    roles: string[];
  } | null>(null);
  const [loadingAccount, setLoadingAccount] = useState(true);
  const [passwordForm, setPasswordForm] = useState({ current: "", next: "", confirm: "" });
  const [savingPassword, setSavingPassword] = useState(false);
  const [passwordMsg, setPasswordMsg] = useState<{ type: "success" | "error"; text: string } | null>(null);

  // Load email config
  const loadEmailConfig = useCallback(async () => {
    try {
      const data = await api.get<{ emailEnabled: boolean; conflictEmailEnabled: boolean }>("/app-config/email");
      setEmailEnabled(data.emailEnabled ?? false);
      setConflictEmailEnabled(data.conflictEmailEnabled ?? false);
    } catch {
      toastRef.current.error("Không thể tải cấu hình email. Hiển thị giá trị mặc định.");
    } finally {
      setLoadingEmail(false);
    }
  }, []);

  // Load staff profile
  const loadProfile = useCallback(async () => {
    try {
      const res = await api.get<{ fullName: string; email: string; phone: string; username: string; specialtyName: string | null; roles: string[] }>("/staff/me");
      setCurrentStaff(res);
    } catch {
      // Not available
    } finally {
      setLoadingAccount(false);
    }
  }, []);

  // Load UI preferences from localStorage
  useEffect(() => {
    const savedTheme = localStorage.getItem("medschedule.theme") as ThemeMode | null;
    const savedDensity = localStorage.getItem("medschedule.density") as DensityMode | null;
    if (savedTheme) setTheme(savedTheme);
    if (savedDensity) setDensity(savedDensity);
  }, []);

  useEffect(() => { void loadEmailConfig(); }, [loadEmailConfig]);
  useEffect(() => { void loadProfile(); }, [loadProfile]);

  const handleSaveEmail = async () => {
    setSavingEmail(true);
    setEmailMsg(null);
    try {
      await api.put("/app-config/email", undefined, {
        emailEnabled,
        conflictEmailEnabled,
      });
      setEmailMsg({ type: "success", text: "Đã lưu cấu hình email thành công." });
    } catch {
      setEmailMsg({ type: "error", text: "Lưu cấu hình thất bại. Vui lòng thử lại." });
    } finally {
      setSavingEmail(false);
    }
  };

  const handleSaveTheme = async (t: ThemeMode) => {
    setTheme(t);
    localStorage.setItem("medschedule.theme", t);
    if (t === "dark") {
      document.documentElement.classList.add("dark");
    } else if (t === "light") {
      document.documentElement.classList.remove("dark");
    } else {
      if (window.matchMedia("(prefers-color-scheme: dark)").matches) {
        document.documentElement.classList.add("dark");
      } else {
        document.documentElement.classList.remove("dark");
      }
    }
    setSavingPrefs(true);
    setTimeout(() => setSavingPrefs(false), 500);
  };

  const handleSaveDensity = async (d: DensityMode) => {
    setDensity(d);
    localStorage.setItem("medschedule.density", d);
    document.documentElement.setAttribute("data-density", d);
    setSavingPrefs(true);
    setTimeout(() => setSavingPrefs(false), 500);
  };

  const handleChangePassword = async (e: React.FormEvent) => {
    e.preventDefault();
    if (passwordForm.next !== passwordForm.confirm) {
      setPasswordMsg({ type: "error", text: "Mật khẩu mới và xác nhận không khớp." });
      return;
    }
    if (passwordForm.next.length < 8) {
      setPasswordMsg({ type: "error", text: "Mật khẩu mới phải có ít nhất 8 ký tự." });
      return;
    }
    setSavingPassword(true);
    setPasswordMsg(null);
    try {
      await api.put("/staff/change-password", {
        currentPassword: passwordForm.current,
        newPassword: passwordForm.next,
      });
      setPasswordMsg({ type: "success", text: "Đổi mật khẩu thành công." });
      setPasswordForm({ current: "", next: "", confirm: "" });
    } catch {
      setPasswordMsg({ type: "error", text: "Đổi mật khẩu thất bại. Kiểm tra lại mật khẩu hiện tại." });
    } finally {
      setSavingPassword(false);
    }
  };

  const roleLabel = (r: string) => ROLE_LABELS[r as keyof typeof ROLE_LABELS] ?? "Nhân viên";

  return (
    <>
      <div className="flex flex-col gap-4 pb-6">
        <BackButton href="/dashboard" variant="full" label="Quay lại" className="mb-2" />

        {/* ── Email Notification Settings ──────────────────────────────── */}
        <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
          <div className="flex items-center gap-2.5 mb-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-100 text-blue-800">
              <span className="material-symbols-outlined text-[18px]">mail</span>
            </div>
            <div>
              <h2 className="text-title-lg font-semibold text-on-surface leading-tight">Thông báo Email</h2>
              <p className="text-[11px] text-on-surface-variant">Bật/tắt thông báo qua email.</p>
            </div>
          </div>

          {loadingEmail ? (
            <div className="space-y-3">
              <div className="h-12 rounded-lg bg-surface-container-low animate-pulse" />
              <div className="h-12 rounded-lg bg-surface-container-low animate-pulse" />
            </div>
          ) : (
            <div className="space-y-4">
              <label className="flex items-center justify-between rounded-xl border border-outline-variant bg-surface p-4 cursor-pointer hover:bg-surface-container-low transition-colors">
                <div className="flex items-center gap-3">
                  <span className="material-symbols-outlined text-on-surface-variant text-[20px]">mark_email_unread</span>
                  <div>
                    <p className="text-label-md font-medium text-on-surface">Bật thông báo Email</p>
                    <p className="text-label-sm text-on-surface-variant">Kích hoạt hệ thống gửi email từ ứng dụng.</p>
                  </div>
                </div>
                <ToggleSwitch
                  id="email-toggle"
                  label="Bật thông báo Email"
                  checked={emailEnabled}
                  onChange={setEmailEnabled}
                />
              </label>

              <label className={`flex items-center justify-between rounded-xl border p-4 cursor-pointer transition-colors ${
                emailEnabled
                  ? "border-outline-variant bg-surface hover:bg-surface-container-low"
                  : "border-surface-container-high bg-surface-container-low opacity-60 cursor-not-allowed"
              }`}>
                <div className="flex items-center gap-3">
                  <span className="material-symbols-outlined text-on-surface-variant text-[20px]">warning</span>
                  <div>
                    <p className="text-label-md font-medium text-on-surface">Thông báo xung đột lịch</p>
                    <p className="text-label-sm text-on-surface-variant">Gửi email cảnh báo khi phát hiện xung đột lịch trực.</p>
                  </div>
                </div>
                <ToggleSwitch
                  id="conflict-email-toggle"
                  label="Thông báo xung đột lịch"
                  checked={conflictEmailEnabled}
                  onChange={setConflictEmailEnabled}
                  disabled={!emailEnabled}
                />
              </label>

              {emailMsg && (
                <div className={`rounded-lg px-4 py-3 text-label-sm ${
                  emailMsg.type === "success"
                    ? "bg-emerald-100 text-emerald-800 border border-emerald-300"
                    : "bg-red-100 text-red-800 border border-red-300"
                }`}>
                  {emailMsg.text}
                </div>
              )}

              <div className="flex justify-end pt-2">
                <Button
                  variant="primary"
                  size="md"
                  onClick={() => void handleSaveEmail()}
                  disabled={savingEmail}
                  loading={savingEmail}
                  icon={!savingEmail ? <span className="material-symbols-outlined text-[18px]" aria-hidden="true">save</span> : undefined}
                >
                  Lưu cấu hình
                </Button>
              </div>
            </div>
          )}
        </section>

        {/* ── UI Preferences ───────────────────────────────────────────── */}
        <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
          <div className="flex items-center gap-2.5 mb-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-100 text-blue-800">
              <span className="material-symbols-outlined text-[18px]">palette</span>
            </div>
            <div>
              <h2 className="text-title-lg font-semibold text-on-surface leading-tight">Tùy chọn giao diện</h2>
              <p className="text-[11px] text-on-surface-variant">Cá nhân hóa hiển thị.</p>
            </div>
          </div>

          <div className="space-y-3">
            {/* Theme */}
            <div className="flex items-center justify-between rounded-lg border border-outline-variant bg-surface p-3">
              <div className="flex items-center gap-2.5">
                <span className="material-symbols-outlined text-on-surface-variant text-[18px]">contrast</span>
                <div>
                  <p className="text-[13px] font-medium text-on-surface">Chế độ giao diện</p>
                  <p className="text-[11px] text-on-surface-variant">Sáng, tối hoặc theo hệ thống.</p>
                </div>
              </div>
              <div className="flex gap-1 bg-surface-container-low rounded-lg p-0.5">
                {(["light", "dark", "system"] as ThemeMode[]).map((t) => (
                  <button
                    key={t}
                    type="button"
                    onClick={() => void handleSaveTheme(t)}
                    className={`flex items-center gap-1 px-2.5 py-1 rounded-md text-[11px] font-medium transition-colors ${
                        theme === t
                          ? "bg-surface-container-lowest text-on-surface shadow-sm"
                          : "text-on-surface-variant hover:text-on-surface"
                    }`}
                  >
                    <span className="material-symbols-outlined text-[14px]">
                      {t === "light" ? "light_mode" : t === "dark" ? "dark_mode" : "brightness_auto"}
                    </span>
                    {t === "light" ? "Sáng" : t === "dark" ? "Tối" : "Hệ thống"}
                  </button>
                ))}
              </div>
            </div>

            {/* Density */}
            <div className="flex items-center justify-between rounded-lg border border-outline-variant bg-surface p-3">
              <div className="flex items-center gap-2.5">
                <span className="material-symbols-outlined text-on-surface-variant text-[18px]">view_compact</span>
                <div>
                  <p className="text-[13px] font-medium text-on-surface">Mật độ hiển thị</p>
                  <p className="text-[11px] text-on-surface-variant">Điều chỉnh khoảng cách phần tử.</p>
                </div>
              </div>
              <div className="flex gap-1 bg-surface-container-low rounded-lg p-0.5">
                {(["compact", "comfortable", "spacious"] as DensityMode[]).map((d) => (
                  <button
                    key={d}
                    type="button"
                    onClick={() => void handleSaveDensity(d)}
                    className={`flex items-center gap-1 px-2.5 py-1 rounded-md text-[11px] font-medium transition-colors ${
                        density === d
                          ? "bg-surface-container-lowest text-on-surface shadow-sm"
                          : "text-on-surface-variant hover:text-on-surface"
                    }`}
                  >
                    {d === "compact" ? "Thu gọn" : d === "comfortable" ? "Bình thường" : "Rộng rãi"}
                  </button>
                ))}
              </div>
            </div>

            {savingPrefs && (
              <div className="rounded-lg px-3 py-2 text-[12px] bg-emerald-100 text-emerald-800 border border-emerald-300">
                Đã lưu tùy chọn hiển thị.
              </div>
            )}
          </div>
        </section>

        {/* ── Role & Permission Management ──────────────────────────────── */}
        <section
          className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm cursor-pointer hover:bg-surface-container-low transition-colors group"
          onClick={() => window.location.href = "/settings/roles"}
          role="link"
          tabIndex={0}
          onKeyDown={(e) => { if (e.key === "Enter") window.location.href = "/settings/roles"; }}
        >
          <div className="flex items-center justify-between gap-2.5 mb-3">
            <div className="flex items-center gap-2.5">
              <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-red-100 text-red-800 bg-red-100 text-red-800">
                <span className="material-symbols-outlined text-[18px]">shield</span>
              </div>
              <div>
                <h2 className="text-title-lg font-semibold text-on-surface leading-tight">Phân quyền hệ thống</h2>
                <p className="text-[11px] text-on-surface-variant">Quản lý quyền hệ thống cho từng vai trò.</p>
              </div>
            </div>
            <span className="material-symbols-outlined text-on-surface-variant group-hover:text-blue-800 transition-colors">chevron_right</span>
          </div>
        </section>

        {/* ── Account Settings ──────────────────────────────────────────── */}
        <section className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
          <div className="flex items-center gap-2.5 mb-3">
            <div className="flex h-9 w-9 items-center justify-center rounded-lg bg-blue-100 text-blue-800">
              <span className="material-symbols-outlined text-[18px]">manage_accounts</span>
            </div>
            <div>
              <h2 className="text-title-lg font-semibold text-on-surface leading-tight">Thiết lập tài khoản</h2>
              <p className="text-[11px] text-on-surface-variant">Quản lý thông tin và bảo mật.</p>
            </div>
          </div>

          <div className="space-y-4">
            {/* Profile info */}
            {loadingAccount ? (
              <div className="space-y-2">
                <div className="h-14 rounded-lg bg-surface-container-low animate-pulse" />
                <div className="h-14 rounded-lg bg-surface-container-low animate-pulse" />
              </div>
            ) : currentStaff ? (
              <div className="rounded-lg border border-outline-variant bg-surface p-3 space-y-2">
                <div className="flex justify-between">
                  <span className="text-label-sm text-on-surface-variant">Họ tên</span>
                  <span className="text-label-md text-on-surface font-medium">{currentStaff.fullName}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-label-sm text-on-surface-variant">Tài khoản</span>
                  <span className="text-label-md text-on-surface font-medium">{currentStaff.username}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-label-sm text-on-surface-variant">Email</span>
                  <span className="text-label-md text-on-surface font-medium">{currentStaff.email || "—"}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-label-sm text-on-surface-variant">Điện thoại</span>
                  <span className="text-label-md text-on-surface font-medium">{currentStaff.phone || "—"}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-label-sm text-on-surface-variant">Khoa/Phòng</span>
                  <span className="text-label-md text-on-surface font-medium">{currentStaff.specialtyName || "—"}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-label-sm text-on-surface-variant">Vai trò</span>
                  <span className="text-label-md text-on-surface font-medium flex gap-1.5">
                    {(currentStaff.roles ?? []).map((r) => (
                      <span key={r} className="inline-flex items-center px-2 py-0.5 rounded-full bg-amber-100 text-amber-800 text-[11px] font-semibold">
                        {roleLabel(r)}
                      </span>
                    ))}
                  </span>
                </div>
              </div>
            ) : null}

            {/* Change password */}
            <form onSubmit={(e) => void handleChangePassword(e)} className="rounded-xl border border-outline-variant bg-surface p-4 space-y-4">
              <div className="flex items-center gap-2 mb-1">
                <span className="material-symbols-outlined text-on-surface-variant text-[18px]">lock</span>
                <h3 className="text-label-md font-semibold text-on-surface">Đổi mật khẩu</h3>
              </div>

              <div className="grid grid-cols-1 gap-4 sm:grid-cols-3">
                <div>
                  <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="current-password">
                    Mật khẩu hiện tại
                  </label>
                  <input
                    id="current-password"
                    type="password"
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface transition-all focus:border-blue-300 focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-blue-300"
                    value={passwordForm.current}
                    onChange={(e) => setPasswordForm((f) => ({ ...f, current: e.target.value }))}
                    required
                  />
                </div>
                <div>
                  <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="new-password">
                    Mật khẩu mới
                  </label>
                  <input
                    id="new-password"
                    type="password"
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface transition-all focus:border-blue-300 focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-blue-300"
                    value={passwordForm.next}
                    onChange={(e) => setPasswordForm((f) => ({ ...f, next: e.target.value }))}
                    required
                    minLength={8}
                  />
                </div>
                <div>
                  <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="confirm-password">
                    Xác nhận mật khẩu mới
                  </label>
                  <input
                    id="confirm-password"
                    type="password"
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface transition-all focus:border-blue-300 focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-blue-300"
                    value={passwordForm.confirm}
                    onChange={(e) => setPasswordForm((f) => ({ ...f, confirm: e.target.value }))}
                    required
                    minLength={8}
                  />
                </div>
              </div>

              {passwordMsg && (
                <div className={`rounded-lg px-4 py-3 text-label-sm ${
                  passwordMsg.type === "success"
                    ? "bg-emerald-100 text-emerald-800 border border-emerald-300"
                    : "bg-red-100 text-red-800 border border-red-300"
                }`}>
                  {passwordMsg.text}
                </div>
              )}

              <div className="flex justify-end">
                <Button
                  type="submit"
                  variant="primary"
                  size="md"
                  disabled={savingPassword}
                  loading={savingPassword}
                  icon={!savingPassword ? <span className="material-symbols-outlined text-[18px]" aria-hidden="true">lock_reset</span> : undefined}
                >
                  Đổi mật khẩu
                </Button>
              </div>
            </form>
          </div>
        </section>

      </div>
    </>
  );
}

function ToggleSwitch({ id, checked, onChange, disabled, label }: {
  id: string;
  checked: boolean;
  onChange: (v: boolean) => void;
  disabled?: boolean;
  label?: string;
}) {
  return (
    <button
      type="button"
      role="switch"
      aria-checked={checked}
      id={id}
      aria-label={label}
      disabled={disabled}
      onClick={() => !disabled && onChange(!checked)}
      onKeyDown={(e) => {
        if (!disabled && (e.key === " " || e.key === "Enter")) {
          e.preventDefault();
          onChange(!checked);
        }
      }}
      className={`relative inline-flex h-6 w-11 shrink-0 items-center rounded-full border-2 border-transparent transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary focus-visible:ring-offset-2 ${disabled ? "cursor-not-allowed opacity-50" : "cursor-pointer"} ${checked ? "bg-blue-100" : "bg-surface-container-high"}`}
    >
      <span
        aria-hidden="true"
        className={`pointer-events-none inline-block h-5 w-5 transform rounded-full bg-[var(--color-surface-container-lowest)] border border-[var(--color-outline-variant)] shadow-sm transition-transform ${checked ? "translate-x-5" : "translate-x-0"}`}
      />
    </button>
  );
}
