"use client";

import { Button, IconButton } from "@/components/ui";
import type { RuntimeConfig } from "./types";
import { getChangedKeys } from "./diff";

type Props = {
  open: boolean;
  onClose: () => void;
  config: RuntimeConfig;
  form: RuntimeConfig;
  onApply: () => void;
};

export function ConfigDiffModal({ open, onClose, config, form, onApply }: Props) {
  if (!open) return null;

  const changes = getChangedKeys(config, form);

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="config-diff-title">
      <div className="absolute inset-0 bg-black/40 animate-fade-in" onClick={onClose} aria-hidden="true" />
      <div className="relative w-full max-w-2xl max-h-[80vh] rounded-2xl border border-outline-variant bg-surface-container-lowest shadow-2xl flex flex-col overflow-hidden animate-scale-in">
        <div className="px-6 py-4 border-b border-outline-variant bg-surface-container-low flex items-center justify-between">
          <div className="flex items-center gap-3">
            <span className="flex h-9 w-9 items-center justify-center rounded-xl bg-tertiary-container text-tertiary">
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">difference</span>
            </span>
            <div>
              <h2 id="config-diff-title" className="text-title-md font-semibold text-on-surface">So sánh thay đổi</h2>
              <p className="text-label-xs text-on-surface-variant">{changes.length} thông số đã thay đổi</p>
            </div>
          </div>
          <IconButton label="Đóng" variant="ghost" size="sm" onClick={onClose} className="text-on-surface-variant">
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">close</span>
          </IconButton>
        </div>
        <div className="flex-1 overflow-y-auto p-4">
          {changes.length === 0 ? (
            <p className="text-center text-on-surface-variant py-8">Không có thay đổi</p>
          ) : (
            <table className="w-full text-left">
              <thead className="bg-surface-container-low sticky top-0">
                <tr>
                  <th className="px-3 py-2 text-[11px] font-semibold uppercase text-on-surface-variant">Thông số</th>
                  <th className="px-3 py-2 text-[11px] font-semibold uppercase text-on-surface-variant">Giá trị cũ</th>
                  <th className="px-3 py-2 text-[11px] font-semibold uppercase text-on-surface-variant">Giá trị mới</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant/50">
                {changes.map(key => (
                  <tr key={key} className="hover:bg-surface-container-low transition-colors">
                    <td className="px-3 py-2.5">
                      <code className="font-mono text-[12px] font-semibold text-primary">{key}</code>
                    </td>
                    <td className="px-3 py-2.5">
                      <span className="inline-block px-2 py-1 rounded-md bg-error-container/30 text-error line-through font-mono text-[12px] tabular-nums">
                        {String(config[key])}
                      </span>
                    </td>
                    <td className="px-3 py-2.5">
                      <span className="inline-block px-2 py-1 rounded-md bg-secondary-container/30 text-secondary font-mono text-[12px] tabular-nums font-bold">
                        {String(form[key])}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}
        </div>
        <div className="px-6 py-3 border-t border-outline-variant bg-surface-container-low flex justify-end gap-2">
          <Button variant="secondary" size="sm" onClick={onClose}>Đóng</Button>
          <Button variant="primary" size="sm" onClick={onApply}>Áp dụng thay đổi</Button>
        </div>
      </div>
    </div>
  );
}