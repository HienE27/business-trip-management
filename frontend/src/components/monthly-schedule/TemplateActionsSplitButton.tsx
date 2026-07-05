"use client";

import { useState, useRef, useEffect } from "react";
import { Button } from "@/components/ui/Button";

type Props = {
  onApplyTemplate: () => void;
  onSaveTemplate?: () => void;
};

/**
 * Split-button cho template actions:
 * - Nhấn phần chính → Áp dụng mẫu
 * - Nhấn mũi tên → Menu dropdown (Áp dụng / Lưu)
 */
export function TemplateActionsSplitButton({ onApplyTemplate, onSaveTemplate }: Props) {
  const [menuOpen, setMenuOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement | null>(null);

  useEffect(() => {
    if (!menuOpen) return;
    const onClick = (e: MouseEvent) => {
      if (menuRef.current && !menuRef.current.contains(e.target as Node)) {
        setMenuOpen(false);
      }
    };
    document.addEventListener("mousedown", onClick);
    return () => document.removeEventListener("mousedown", onClick);
  }, [menuOpen]);

  return (
    <div className="relative" ref={menuRef}>
      <div className="flex rounded-lg border border-outline-variant overflow-hidden">
        <Button
          variant="ghost"
          size="sm"
          onClick={onApplyTemplate}
          icon={<span className="material-symbols-outlined text-[16px]">download</span>}
          className="rounded-none border-r border-outline-variant"
        >
          Áp dụng mẫu
        </Button>
        <button
          type="button"
          onClick={() => setMenuOpen(!menuOpen)}
          className="px-2 hover:bg-surface-container-low active:scale-95 transition-all cursor-pointer"
          aria-label="Mở menu mẫu"
          aria-expanded={menuOpen}
        >
          <span className="material-symbols-outlined text-[16px] text-on-surface-variant">expand_more</span>
        </button>
      </div>
      {menuOpen && (
        <div className="absolute left-0 top-full z-50 mt-2 w-56 rounded-xl border border-outline-variant bg-surface-container-lowest p-2 shadow-lg">
          <button
            type="button"
            onClick={() => { setMenuOpen(false); onApplyTemplate(); }}
            className="w-full flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-surface-container-low text-left cursor-pointer"
          >
            <span className="material-symbols-outlined text-[16px] text-primary">download</span>
            <div>
              <p className="text-label-sm font-medium text-on-surface">Áp dụng mẫu có sẵn</p>
              <p className="text-[10px] text-on-surface-variant">Dùng template đã lưu</p>
            </div>
          </button>
          {onSaveTemplate && (
            <button
              type="button"
              onClick={() => { setMenuOpen(false); onSaveTemplate(); }}
              className="w-full flex items-center gap-2 px-3 py-2 rounded-lg hover:bg-surface-container-low text-left cursor-pointer"
            >
              <span className="material-symbols-outlined text-[16px] text-secondary">bookmark_add</span>
              <div>
                <p className="text-label-sm font-medium text-on-surface">Lưu mẫu mới</p>
                <p className="text-[10px] text-on-surface-variant">Lưu preview làm template</p>
              </div>
            </button>
          )}
        </div>
      )}
    </div>
  );
}