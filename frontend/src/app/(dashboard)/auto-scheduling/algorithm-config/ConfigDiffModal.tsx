"use client";

import { Button } from "@/components/ui";
import { Modal, ModalFooter } from "@/components/ui/Modal";
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
  const changes = getChangedKeys(config, form);

  return (
    <Modal
      open={open}
      onClose={onClose}
      title="So sánh thay đổi"
      description={`${changes.length} thông số đã thay đổi`}
      size="lg"
      icon={<span className="material-symbols-outlined text-[18px]" aria-hidden="true">difference</span>}
      iconClassName="bg-amber-100 text-amber-800"
    >
      <div className="border border-outline-variant rounded-lg overflow-hidden max-h-80 overflow-y-auto">
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
                    <code className="font-mono text-[12px] font-semibold text-blue-800">{key}</code>
                  </td>
                  <td className="px-3 py-2.5">
                    <span className="inline-block px-2 py-1 rounded-md bg-red-100 text-red-800 line-through font-mono text-[12px] tabular-nums">
                      {String(config[key])}
                    </span>
                  </td>
                  <td className="px-3 py-2.5">
                    <span className="inline-block px-2 py-1 rounded-md bg-emerald-100 text-emerald-800 font-mono text-[12px] tabular-nums font-bold">
                      {String(form[key])}
                    </span>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        )}
      </div>
      <ModalFooter>
        <Button variant="secondary" size="md" onClick={onClose}>Đóng</Button>
        <Button variant="primary" size="md" onClick={onApply}>Áp dụng thay đổi</Button>
      </ModalFooter>
    </Modal>
  );
}