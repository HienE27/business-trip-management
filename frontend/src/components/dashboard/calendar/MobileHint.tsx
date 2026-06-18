"use client";

import { useEffect, useState } from "react";

/**
 * Banner gợi ý cho mobile (chạm + phím tắt). Tự ẩn khi user đã dismiss,
 * lưu vào localStorage để không hiện lại.
 */
export function MobileHint() {
  const [dismissed, setDismissed] = useState(() => {
    if (typeof window === "undefined") return true;
    return localStorage.getItem("calendar-mobile-hint-dismissed") === "1";
  });
  if (dismissed) return null;
  return (
    <div className="lg:hidden mx-3 mt-2 flex items-start gap-2 px-3 py-2 rounded-lg bg-primary-fixed border border-primary/20" role="status">
      <span aria-hidden="true" className="material-symbols-outlined text-primary text-[18px] shrink-0 mt-0.5">touch_app</span>
      <p className="text-label-sm text-on-surface flex-1">
        Nhấn vào ngày trống để thêm lịch nhanh. Dùng phím mũi tên để di chuyển.
      </p>
      <button
        type="button"
        onClick={() => {
          localStorage.setItem("calendar-mobile-hint-dismissed", "1");
          setDismissed(true);
        }}
        className="text-on-surface-variant hover:text-on-surface p-1 -m-1 rounded focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
        aria-label="Đóng gợi ý"
      >
        <span aria-hidden="true" className="material-symbols-outlined text-[16px]">close</span>
      </button>
    </div>
  );
}
