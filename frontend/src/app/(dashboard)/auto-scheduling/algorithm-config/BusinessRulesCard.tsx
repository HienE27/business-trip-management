"use client";

import { useState } from "react";

const BUSINESS_RULES: { id: string; text: string; severity: "hard" | "soft" }[] = [
  { id: "BR01", text: "Không xếp trùng ca cấm (L01+L02, L03+L04 cùng ngày)", severity: "hard" },
  { id: "BR02", text: "Không xếp khi nhân sự đang nghỉ phép", severity: "hard" },
  { id: "BR03", text: "Không vượt giới hạn ca mỗi tuần mỗi người", severity: "soft" },
  { id: "BR04", text: "Không xếp trực L01 liền kề (mỗi L01 tạo 1 ngày nghỉ bù)", severity: "soft" },
  { id: "BR05", text: "Không vượt quá 6 ngày liên tiếp", severity: "soft" },
  { id: "BR06", text: "Không vượt số ca tối đa mỗi nhân sự mỗi tháng", severity: "soft" },
];

export function BusinessRulesCard() {
  const [collapsed, setCollapsed] = useState(true);

  return (
    <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden hover:shadow-sm transition-shadow duration-200 border-l-4 border-l-primary">
      <button
        type="button"
        onClick={() => setCollapsed(!collapsed)}
        className="w-full px-5 py-4 bg-surface-container-low flex items-center justify-between gap-3 hover:bg-surface-container transition-colors"
        aria-expanded={!collapsed}
      >
        <div className="flex items-center gap-3 min-w-0">
          <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-blue-100 text-blue-800 text-blue-800">
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">rule</span>
          </div>
          <div className="flex flex-col items-start gap-0.5 min-w-0">
            <p className="text-label-md font-semibold text-on-surface tracking-tight">Quy tắc xếp lịch</p>
            <p className="text-[11px] text-on-surface-variant leading-tight">
              Scheduler tuân thủ {BUSINESS_RULES.length} quy tắc nghiệp vụ
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
              className="flex items-center gap-2.5 px-2 py-1.5 rounded-lg hover:bg-surface-container-low transition-colors"
            >
              <span className="material-symbols-outlined text-[14px] shrink-0 text-emerald-800" aria-hidden="true">check_circle</span>
              <span className="text-[12px] text-on-surface leading-relaxed">{rule.text}</span>
            </div>
          ))}
        </div>
      </div>
    </div>
  );
}
