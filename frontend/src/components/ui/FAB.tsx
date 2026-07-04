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
    <div className="fixed bottom-4 right-4 sm:bottom-6 sm:right-6 z-30 flex flex-col items-end gap-2">
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
              className="inline-flex items-center gap-3 bg-surface-container-lowest border border-outline-variant rounded-full px-4 py-2.5 shadow-lg hover:bg-surface-container-low transition-colors text-label-md text-on-surface cursor-pointer focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary"
            >
              <span className="text-label-md text-on-surface">{action.label}</span>
              <span className="material-symbols-outlined text-[20px] text-primary">{action.icon}</span>
            </button>
          ))}
        </div>
      )}

      {/* Main FAB — kept as raw button because IconButton doesn't support the rotated "+→×" icon affordance + round shape */}
      <button
        type="button"
        aria-label={open ? "Đóng menu" : "Mở menu hành động"}
        aria-expanded={open}
        onClick={() => setOpen((v) => !v)}
        className="w-14 h-14 rounded-full flex items-center justify-center bg-primary text-on-primary shadow-lg hover:opacity-90 hover:shadow-xl transition-all duration-200 ease-out focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary animate-slide-up"
      >
        <span className="material-symbols-outlined text-[24px] transition-transform duration-200" style={open ? { transform: "rotate(45deg)" } : {}}>add</span>
      </button>
    </div>
  );
}