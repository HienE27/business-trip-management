"use client";

import type { TabKey } from "./types";

type TabDef = { key: TabKey; label: string; icon: string; count?: number };

type Props = {
  active: TabKey;
  counts?: Partial<Record<TabKey, number>>;
  onChange: (tab: TabKey) => void;
};

const BASE_TABS: TabDef[] = [
  { key: "config", label: "Cấu hình", icon: "tune" },
  { key: "history", label: "Lịch sử chạy", icon: "history" },
  { key: "audit", label: "Nhật ký thay đổi", icon: "manage_history" },
];

export function TabBar({ active, counts, onChange }: Props) {
  return (
    <div
      className="inline-flex items-center gap-1 p-1 bg-surface-container-low rounded-xl border border-outline-variant"
      role="tablist"
      aria-label="Tabs cấu hình thuật toán"
    >
      {BASE_TABS.map(tab => {
        const isActive = active === tab.key;
        const count = counts?.[tab.key];
        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange(tab.key)}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-lg text-label-md font-medium transition-all cursor-pointer ${
              isActive
                ? "bg-surface-container-lowest text-blue-800 shadow-sm"
                : "text-on-surface-variant hover:text-on-surface hover:bg-surface-container-lowest/50"
            }`}
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">{tab.icon}</span>
            <span>{tab.label}</span>
            {count !== undefined && count > 0 && (
              <span className={`inline-flex items-center justify-center min-w-[20px] h-5 px-1.5 rounded-full text-[10px] font-bold ${
                isActive ? "bg-blue-100 text-blue-800" : "bg-surface-container text-on-surface-variant"
              }`}>
                {count > 99 ? "99+" : count}
              </span>
            )}
          </button>
        );
      })}
    </div>
  );
}