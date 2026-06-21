"use client";

import { useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";

const VALUE_TYPE_OPTIONS = [
  { value: "STRING", label: "Chuỗi (STRING)" },
  { value: "NUMBER", label: "Số (NUMBER)" },
  { value: "BOOLEAN", label: "Đúng/Sai (BOOLEAN)" },
  { value: "JSON", label: "JSON" },
];

interface CreateConfigModalProps {
  open: boolean;
  onClose: () => void;
  onCreate: (form: { paramKey: string; paramValue: string; valueType: string; description: string }) => Promise<void>;
  creating: boolean;
  message?: { type: "success" | "error"; text: string } | null;
}

export function CreateConfigModal({ open, onClose, onCreate, creating, message }: CreateConfigModalProps) {
  const [form, setForm] = useState({ paramKey: "", paramValue: "", valueType: "STRING", description: "" });

  function handleClose() {
    setForm({ paramKey: "", paramValue: "", valueType: "STRING", description: "" });
    onClose();
  }

  async function handleSubmit() {
    if (!form.paramKey.trim() || !form.paramValue.trim()) return;
    await onCreate(form);
    handleClose();
  }

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title="Thêm cấu hình mới"
      description="Tạo thông số vận hành mới cho thuật toán auto-scheduling."
    >
      <div className="space-y-4">
        <div>
          <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="cfg-key">
            Tên thông số <span className="text-error">*</span>
          </label>
          <input
            id="cfg-key"
            className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface font-mono transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            placeholder="VD: max_iterations"
            value={form.paramKey}
            onChange={(e) => setForm((f) => ({ ...f, paramKey: e.target.value.toLowerCase().replace(/\s/g, "_") }))}
          />
        </div>
        <div>
          <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="cfg-type">
            Kiểu dữ liệu
          </label>
          <div className="relative">
            <select
              id="cfg-type"
              className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface appearance-none transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer"
              value={form.valueType}
              onChange={(e) => setForm((f) => ({ ...f, valueType: e.target.value }))}
            >
              {VALUE_TYPE_OPTIONS.map((o) => (
                <option key={o.value} value={o.value}>{o.label}</option>
              ))}
            </select>
            <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">expand_more</span>
          </div>
        </div>
        <div>
          <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="cfg-value">
            Giá trị <span className="text-error">*</span>
          </label>
          <input
            id="cfg-value"
            className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-label-md text-on-surface font-mono transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            placeholder="VD: 1000, true, 2.5, []"
            value={form.paramValue}
            onChange={(e) => setForm((f) => ({ ...f, paramValue: e.target.value }))}
          />
        </div>
        <div>
          <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="cfg-desc">
            Mô tả
          </label>
          <textarea
            id="cfg-desc"
            className="w-full resize-none rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 text-label-md text-on-surface transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            rows={2}
            placeholder="Giải thích thông số này dùng để làm gì..."
            value={form.description}
            onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
          />
        </div>
        {message && (
          <div className={`rounded-lg px-4 py-3 text-label-sm ${
            message.type === "success"
              ? "bg-secondary-container text-on-secondary-container"
              : "bg-error-container text-on-error-container"
          }`}>
            {message.text}
          </div>
        )}
      </div>
      <ModalFooter>
        <button
          type="button"
          onClick={handleClose}
          className="px-4 py-2 rounded-lg border border-outline-variant text-label-md text-on-surface hover:bg-surface-container-low transition-colors"
        >
          Hủy
        </button>
        <button
          type="button"
          onClick={handleSubmit}
          disabled={!form.paramKey.trim() || !form.paramValue.trim() || creating}
          className="inline-flex items-center gap-2 px-5 py-2 rounded-lg bg-primary text-label-md font-semibold text-on-primary hover:bg-primary/90 disabled:opacity-50 transition-colors"
        >
          {creating ? "Đang tạo..." : "Tạo cấu hình"}
        </button>
      </ModalFooter>
    </Modal>
  );
}
