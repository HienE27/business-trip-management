"use client";

import { useEffect, useState } from "react";
import { Button, IconButton } from "@/components/ui";
import { FormSelect } from "@/components/ui/FormSelect";

type Props = {
  open: boolean;
  onClose: () => void;
  onCreate: (form: { paramKey: string; paramValue: string; valueType: string; description: string }) => Promise<void>;
  creating: boolean;
  message?: { type: "success" | "error"; text: string } | null;
};

type FormState = {
  paramKey: string;
  paramValue: string;
  valueType: "STRING" | "NUMBER" | "BOOLEAN" | "JSON";
  description: string;
};

const VALUE_TYPES = [
  { value: "STRING", label: "Chuỗi" },
  { value: "NUMBER", label: "Số" },
  { value: "BOOLEAN", label: "Đúng/Sai" },
  { value: "JSON", label: "JSON" },
];

const PRESET_PARAMS = [
  { label: "Chọn thông số...", value: "" },
  { label: "weekend_weight — Hệ số nhân cuối tuần", value: "weekend_weight" },
  { label: "greedy_coverage_threshold — Ngưỡng phủ lịch (Greedy)", value: "greedy_coverage_threshold" },
  { label: "balance_score_min — Ngưỡng cân bằng tải", value: "balance_score_min" },
  { label: "overnight_recovery_hours — Giờ nghỉ giữa các ca", value: "overnight_recovery_hours" },
  { label: "staff_preference_weight — Trọng số ưu tiên nhân sự", value: "staff_preference_weight" },
  { label: "specialty_match_weight — Trọng số chuyên khoa", value: "specialty_match_weight" },
  { label: "min_staff_per_shift — Tối thiểu nhân sự/ca", value: "min_staff_per_shift" },
  { label: "max_staff_per_shift — Tối đa nhân sự/ca", value: "max_staff_per_shift" },
  { label: "conflict_penalty — Điểm phạt xung đột", value: "conflict_penalty" },
  { label: "timeout_seconds — Thời gian chờ tối đa", value: "timeout_seconds" },
];

const PRESET_VALUES: Record<string, { value: string; type: FormState["valueType"]; description: string }> = {
  weekend_weight: { value: "2.0", type: "NUMBER", description: "Hệ số nhân penalty cuối tuần (T7/CN)" },
  greedy_coverage_threshold: { value: "0.85", type: "NUMBER", description: "Ngưỡng phủ lịch để Greedy dừng sớm" },
  balance_score_min: { value: "0.70", type: "NUMBER", description: "Ngưỡng cân bằng tải tối thiểu" },
  overnight_recovery_hours: { value: "24", type: "NUMBER", description: "Số giờ nghỉ bắt buộc giữa hai ca trực 24/24" },
  staff_preference_weight: { value: "1.5", type: "NUMBER", description: "Trọng số cho sở thích ca trực của nhân sự" },
  specialty_match_weight: { value: "2.0", type: "NUMBER", description: "Trọng số cho việc khớp chuyên khoa" },
  min_staff_per_shift: { value: "1", type: "NUMBER", description: "Số nhân sự tối thiểu cần thiết mỗi ca" },
  max_staff_per_shift: { value: "5", type: "NUMBER", description: "Số nhân sự tối đa mỗi ca" },
  conflict_penalty: { value: "100", type: "NUMBER", description: "Điểm phạt khi phát hiện xung đột lịch" },
  timeout_seconds: { value: "300", type: "NUMBER", description: "Thời gian chờ tối đa cho mỗi lần chạy (giây)" },
};

const EMPTY_FORM: FormState = { paramKey: "", paramValue: "", valueType: "STRING", description: "" };

export function CreateConfigModal({ open, onClose, onCreate, creating, message }: Props) {
  const [form, setForm] = useState<FormState>(EMPTY_FORM);

  useEffect(() => {
    if (open) setForm(EMPTY_FORM);
  }, [open]);

  function handlePresetChange(key: string) {
    if (!key) { setForm(EMPTY_FORM); return; }
    const preset = PRESET_VALUES[key];
    if (preset) {
      setForm({ paramKey: key, paramValue: preset.value, valueType: preset.type, description: preset.description });
    } else {
      setForm(prev => ({ ...prev, paramKey: key }));
    }
  }

  if (!open) return null;

  async function handleSubmit() {
    if (!form.paramKey.trim() || !form.paramValue.trim()) return;
    await onCreate(form);
    onClose();
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4" role="dialog" aria-modal="true" aria-labelledby="create-config-title">
      <div className="absolute inset-0 bg-black/40 animate-fade-in" onClick={onClose} aria-hidden="true" />
      <div className="relative w-full max-w-md rounded-2xl border border-outline-variant bg-surface-container-lowest shadow-2xl overflow-hidden animate-scale-in">
        <div className="px-6 py-5 border-b border-outline-variant bg-surface-container-low flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="flex h-9 w-9 shrink-0 items-center justify-center rounded-xl bg-primary-fixed text-primary">
              <span className="material-symbols-outlined text-[18px]" aria-hidden="true">add</span>
            </div>
            <div>
              <h2 id="create-config-title" className="text-title-md font-semibold text-on-surface">Thêm cấu hình mới</h2>
              <p className="text-label-xs text-on-surface-variant">Tạo thông số vận hành cho thuật toán</p>
            </div>
          </div>
          <IconButton label="Đóng" variant="ghost" size="sm" onClick={onClose} className="text-on-surface-variant">
            <span className="material-symbols-outlined text-[18px]" aria-hidden="true">close</span>
          </IconButton>
        </div>

        <div className="p-6 space-y-4">
          <div>
            <FormSelect
              id="cfg-key"
              label="Tên thông số"
              required
              value={form.paramKey}
              onChange={(e) => handlePresetChange(e.target.value)}
              options={PRESET_PARAMS.map((p) => ({ value: p.value, label: p.label }))}
              className="!font-mono !text-label-md"
            />
            <p className="text-[11px] text-outline mt-1">Chọn từ danh sách hoặc nhập tên tùy ý</p>
          </div>

          <div className="grid grid-cols-2 gap-4">
            <FormSelect
              id="cfg-type"
              label="Kiểu dữ liệu"
              value={form.valueType}
              onChange={(e) => setForm((f) => ({ ...f, valueType: e.target.value as FormState["valueType"] }))}
              options={VALUE_TYPES.map((t) => ({ value: t.value, label: `${t.label} (${t.value})` }))}
              className="!text-label-md"
            />
            <div>
              <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="cfg-value">Giá trị <span className="text-error">*</span></label>
              <input
                id="cfg-value"
                className="h-10 w-full rounded-xl border border-outline-variant bg-surface-container-low px-3 text-label-md font-mono text-on-surface transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                placeholder="VD: 1000, true, 2.5"
                value={form.paramValue}
                onChange={e => setForm(f => ({ ...f, paramValue: e.target.value }))}
              />
            </div>
          </div>

          <div>
            <label className="text-label-sm text-on-surface-variant block mb-1.5" htmlFor="cfg-desc">Mô tả</label>
            <textarea
              id="cfg-desc"
              className="w-full resize-none rounded-xl border border-outline-variant bg-surface-container-low px-3 py-2.5 text-label-md text-on-surface transition-all focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
              rows={2}
              placeholder="Giải thích thông số này dùng để làm gì..."
              value={form.description}
              onChange={e => setForm(f => ({ ...f, description: e.target.value }))}
            />
          </div>

          {message && (
            <div className={`rounded-lg px-4 py-3 text-label-sm ${message.type === "success" ? "bg-secondary-container text-secondary" : "bg-error-container text-error"}`} role="status">
              {message.text}
            </div>
          )}
        </div>

        <div className="px-6 py-4 border-t border-outline-variant bg-surface-container-low flex justify-end gap-2">
          <Button variant="secondary" size="md" onClick={onClose}>Hủy</Button>
          <Button
            variant="primary"
            size="md"
            onClick={handleSubmit}
            disabled={!form.paramKey.trim() || !form.paramValue.trim() || creating}
            loading={creating}
            icon={!creating ? <span className="material-symbols-outlined text-[16px]" aria-hidden="true">add</span> : undefined}
          >
            Tạo cấu hình
          </Button>
        </div>
      </div>
    </div>
  );
}