"use client";

import { useState } from "react";

type FABAction = {
  id: string;
  icon: string;
  label: string;
  onClick: () => void;
};

type FABProps = {
  actions?: FABAction[];
};

export function FAB({ actions = [] }: FABProps) {
  const [open, setOpen] = useState(false);

  return (
    <div className="fixed bottom-6 right-6 z-30 flex flex-col items-end gap-2">
      {/* Backdrop */}
      {open && (
        <div
          className="fixed inset-0 z-[-1]"
          onClick={() => setOpen(false)}
          aria-hidden="true"
        />
      )}

      {/* Actions */}
      {open && actions.length > 0 && (
        <div className="flex flex-col gap-2 items-end mb-2 animate-in slide-in-from-bottom-2 duration-200">
          {actions.map((action) => (
            <button
              key={action.id}
              type="button"
              onClick={() => {
                action.onClick();
                setOpen(false);
              }}
              className="flex items-center gap-3 bg-surface-container-lowest border border-outline-variant rounded-full px-4 py-2.5 shadow-lg hover:bg-surface-container-low transition-colors text-label-md text-on-surface"
            >
              <span className="text-label-md text-on-surface">{action.label}</span>
              <span className="material-symbols-outlined text-[20px] text-primary">{action.icon}</span>
            </button>
          ))}
        </div>
      )}

      {/* Main FAB */}
      <button
        type="button"
        aria-label={open ? "Đóng menu" : "Mở menu hành động"}
        onClick={() => setOpen((v) => !v)}
        className={`w-14 h-14 rounded-full flex items-center justify-center shadow-lg transition-transform duration-200 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
          open
            ? "bg-surface-container-high rotate-45 text-primary"
            : "bg-primary text-on-primary hover:bg-primary/90"
        }`}
      >
        <span className="material-symbols-outlined text-[24px]">add</span>
      </button>
    </div>
  );
}
