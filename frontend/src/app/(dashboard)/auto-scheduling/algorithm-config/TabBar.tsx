"use client";

import type { TabKey } from "./types";

type TabDef = { key: TabKey; label: string; icon: string };

type Props = {
  active: TabKey;
  onChange: (tab: TabKey) => void;
};

const BASE_TABS: TabDef[] = [
  { key: "config", label: "Cau hinh", icon: "tune" },
  { key: "history", label: "Lich su", icon: "history" },
];

export function TabBar({ active, onChange }: Props) {
  return (
    <div
      className="inline-flex items-center gap-1 p-1 bg-surface-container-low rounded-xl border border-outline-variant"
      role="tablist"
      aria-label="Tabs cau hinh thuat toan"
    >
      {BASE_TABS.map(tab => {
        const isActive = active === tab.key;
        return (
          <button
            key={tab.key}
            type="button"
            role="tab"
            aria-selected={isActive}
            onClick={() => onChange(tab.key)}
            className={`flex items-center gap-2 px-3.5 py-2 rounded-lg text-label-md font-medium transition-all cursor-pointer ${
              isActive
                ? "bg-surface-container-lowest text-primary shadow-sm"
                : "text-on-surface-variant hover:text-on-surface hover:bg-surface-container-lowest/50"
            }`}
          >
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">{tab.icon}</span>
            <span>{tab.label}</span>
          </button>
        );
      })}
    </div>
  );
}
