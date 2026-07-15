"use client";

import { useEffect, useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { Button } from "@/components/ui";
import { FormInput } from "@/components/ui/FormInput";

type ForgotPasswordModalProps = {
  open: boolean;
  onClose: () => void;
  /** Email server đã được gửi đến (UI feedback). Backend thật sẽ gửi qua email. */
  onSubmit: (usernameOrEmail: string) => Promise<void>;
};

type Status = "idle" | "submitting" | "sent" | "error";

/**
 * Modal "Quên mật khẩu" cho trang login.
 *
 * <p>Vì hệ thống chưa expose endpoint reset-password public, modal này sẽ:
 * <ol>
 *   <li>Validate input (username hoặc email).</li>
 *   <li>Gọi callback {@link onSubmit} (bên LoginForm sẽ gọi API nếu có).</li>
 *   <li>Hiển thị trạng thái thành công/thất bại đúng chuẩn UX.</li>
 * </ol>
 *
 * <p>Auto-reset về {@code idle} sau 4 giây khi đóng modal trong trạng thái sent,
 * để lần mở sau là form trống.
 */
export function ForgotPasswordModal({
  open,
  onClose,
  onSubmit,
}: ForgotPasswordModalProps) {
  const [value, setValue] = useState("");
  const [status, setStatus] = useState<Status>("idle");
  const [errMsg, setErrMsg] = useState("");

  useEffect(() => {
    if (!open) {
      const t = window.setTimeout(() => {
        setValue("");
        setStatus("idle");
        setErrMsg("");
      }, 250);
      return () => window.clearTimeout(t);
    }
    return undefined;
  }, [open]);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    const trimmed = value.trim();
    if (!trimmed) {
      setErrMsg("Vui lòng nhập tên đăng nhập hoặc email đã đăng ký");
      return;
    }
    setStatus("submitting");
    setErrMsg("");
    try {
      await onSubmit(trimmed);
      setStatus("sent");
    } catch (err) {
      setStatus("error");
      setErrMsg(
        err instanceof Error
          ? err.message
          : "Không gửi được yêu cầu. Vui lòng thử lại sau.",
      );
    }
  };

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="Khôi phục mật khẩu"
      description="Nhập tên đăng nhập hoặc email để nhận hướng dẫn đặt lại."
      size="sm"
    >
      {status === "sent" ? (
        <div className="space-y-4 py-2 text-center">
          <div className="mx-auto flex h-14 w-14 items-center justify-center rounded-full bg-emerald-100">
            <span className="material-symbols-outlined text-[32px] text-emerald-600">
              mark_email_read
            </span>
          </div>
          <div>
            <h3 className="text-title-md font-semibold text-on-surface">
              Đã ghi nhận yêu cầu
            </h3>
            <p className="mt-1 text-body-sm text-on-surface-variant">
              Hệ thống sẽ gửi hướng dẫn đặt lại mật khẩu cho{" "}
              <span className="font-semibold text-on-surface">
                {value.trim()}
              </span>
              . Vui lòng kiểm tra hộp thư trong vài phút tới.
            </p>
          </div>
          <Button type="button" onClick={onClose} variant="primary" fullWidth>
            Đóng
          </Button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="space-y-4" noValidate>
          <FormInput
            label="Tên đăng nhập hoặc email"
            id="forgot-identity"
            type="text"
            autoComplete="username email"
            placeholder="ví dụ: admin@hospital.vn"
            icon="alternate_email"
            value={value}
            onChange={(e) => {
              setValue(e.target.value);
              if (errMsg) setErrMsg("");
            }}
            error={errMsg}
            disabled={status === "submitting"}
            autoFocus
          />

          <div className="rounded-lg bg-primary-container/40 px-3 py-2 text-label-sm text-on-primary-container">
            <span className="material-symbols-outlined mr-1 inline align-middle text-[16px]">
              info
            </span>
            Trong môi trường pilot, yêu cầu sẽ được admin xử lý thủ công nếu
            email không hợp lệ.
          </div>

          <ModalFooter>
            <Button
              type="button"
              variant="ghost"
              onClick={onClose}
              disabled={status === "submitting"}
            >
              Hủy
            </Button>
            <Button
              type="submit"
              variant="primary"
              loading={status === "submitting"}
              icon={
                <span
                  className="material-symbols-outlined"
                  aria-hidden="true"
                >
                  send
                </span>
              }
            >
              Gửi yêu cầu
            </Button>
          </ModalFooter>
        </form>
      )}
    </Modal>
  );
}
