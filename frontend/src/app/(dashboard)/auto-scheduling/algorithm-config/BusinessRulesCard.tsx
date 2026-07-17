"use client";

import { useState } from "react";

const BUSINESS_RULES = [
  {
    id: "BR01",
    label: "BR-01",
    text: "Không trực L01 (24/24) và L02 (Thông tầm) cùng ngày",
    severity: "hard" as const,
  },
  {
    id: "BR02",
    label: "BR-02",
    text: "Không trực L03 (PK Dịch vụ) và L04 (PK Chuyên gia) cùng ngày",
    severity: "hard" as const,
  },
  {
    id: "BR03",
    label: "BR-03",
    text: "Không trực quá 6 ngày liên tiếp",
    severity: "soft" as const,
  },
  {
    id: "BR04",
    label: "BR-04",
    text: "Không xếp trực L01 liền kề (vì mỗi L01 tạo 1 ngày nghỉ bù)",
    severity: "soft" as const,
  },
  {
    id: "BR05",
    label: "BR-05",
    text: "Không xếp lịch cho nhân sự đang nghỉ phép",
    severity: "hard" as const,
  },
  {
    id: "BR06",
    label: "BR-06",
    text: "Không vượt quá số ca tối đa mỗi nhân sự mỗi tháng",
    severity: "soft" as const,
  },
];

export function BusinessRulesCard() {
  const [collapsed, setCollapsed] = useState(true);

  const hardCount = BUSINESS_RULES.filter((r) => r.severity === "hard").length;
  const softCount = BUSINESS_RULES.filter((r) => r.severity === "soft").length;

  return (
    <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow duration-200 border-l-4 border-l-primary">
      <button
        type="button"
        onClick={() => setCollapsed(!collapsed)}
        className="w-full px-5 py-4 bg-surface-container-low flex items-center justify-between gap-3 hover:bg-surface-container transition-colors"
        aria-expanded={!collapsed}
      >
        <div className="flex items-center gap-3 min-w-0">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary-fixed text-primary">
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">rule</span>
          </div>
          <div className="flex flex-col items-start gap-0.5 min-w-0">
            <div className="flex items-center gap-2">
              <p className="text-label-md font-semibold text-on-surface tracking-tight">Ràng buộc nghiệp vụ</p>
              <div className="flex items-center gap-1">
                <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-error-container text-on-error-container border border-on-error-container/20">
                  {hardCount} HARD
                </span>
                <span className="px-1.5 py-0.5 rounded text-[10px] font-bold bg-secondary-container text-on-secondary-container border border-on-secondary-container/20">
                  {softCount} SOFT
                </span>
              </div>
            </div>
            <p className="text-[11px] text-on-surface-variant leading-tight">
              Các quy tắc hệ thống tuân thủ khi xếp lịch tự động
            </p>
          </div>
        </div>
        <span
          className="material-symbols-outlined text-[20px] text-on-surface-variant transition-transform duration-200"
          style={{ transform: collapsed ? "" : "rotate(180deg)" }}
          aria-hidden="true"
        >
          expand_more
        </span>
      </button>

      <div
        className="overflow-hidden transition-all duration-300"
        style={{ maxHeight: collapsed ? 0 : 600 }}
      >
        <div className="p-5 border-t border-outline-variant/50 space-y-2">
          {BUSINESS_RULES.map((rule) => (
            <div
              key={rule.id}
              className="flex items-start gap-3 px-3 py-2 rounded-lg hover:bg-surface-container-low transition-colors"
            >
              <span
                className={`mt-0.5 px-1.5 py-0.5 rounded text-[10px] font-bold shrink-0 border ${
                  rule.severity === "hard"
                    ? "bg-error-container text-on-error-container border-on-error-container/20"
                    : "bg-secondary-container text-on-secondary-container border-on-secondary-container/20"
                }`}
              >
                {rule.label}
              </span>
              <span className="text-[12px] text-on-surface leading-relaxed">{rule.text}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
