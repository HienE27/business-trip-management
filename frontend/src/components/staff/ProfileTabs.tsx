"use client";

import { useState } from "react";

const TABS = [
  { id: "info", label: "Thong tin chung" },
  { id: "schedule", label: "Lich cong tac gan day" },
  { id: "stats", label: "Thong ke workload" },
  { id: "history", label: "Nhat ky thay doi" },
];

type ProfileTabsProps = {
  activeTab?: string;
  onTabChange?: (tab: string) => void;
  children: React.ReactNode;
};

export function ProfileTabs({ activeTab = "info", onTabChange, children }: ProfileTabsProps) {
  return (
    <div className="bg-surface-container-lowest rounded-xl shadow-[0_1px_3px_0_rgba(0,0,0,0.1),_0_1px_2px_-1px_rgba(0,0,0,0.1)] overflow-hidden flex-1 flex flex-col">
      <div className="border-b border-outline-variant flex overflow-x-auto bg-surface-container-low px-4">
        {TABS.map((tab) => (
          <button
            key={tab.id}
            className={`px-4 py-3 font-label-md text-label-md border-b-2 whitespace-nowrap transition-colors ${
              activeTab === tab.id
                ? "text-primary border-primary"
                : "text-on-surface-variant border-transparent hover:text-on-surface hover:bg-surface-container-high"
            }`}
            onClick={() => onTabChange?.(tab.id)}
            type="button"
          >
            {tab.label}
          </button>
        ))}
      </div>
      <div className="p-6">
        {children}
      </div>
    </div>
  );
}
