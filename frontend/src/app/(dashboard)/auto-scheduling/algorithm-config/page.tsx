"use client";

import { useEffect, useState } from "react";
import dynamic from "next/dynamic";
import { SectionCard } from "@/components/ui/SectionCard";
import { Skeleton } from "@/components/ui/Skeleton";

const ConfirmDialog = dynamic(
  () => import("@/components/ui/ConfirmDialog").then((m) => m.ConfirmDialog),
  { loading: () => null },
);
const CreateConfigModal = dynamic(
  () => import("./CreateConfigModal").then((m) => m.CreateConfigModal),
  { loading: () => null },
);
const RuntimeConfigCard = dynamic(
  () => import("./RuntimeConfigCard").then((m) => m.RuntimeConfigCard),
  { loading: () => null },
);

import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useRole } from "@/hooks/useRole";
import type { ApiResponse } from "@/types/api";

type ConfigEntry = {
  paramKey: string;
  paramValue: string;
  valueType: "STRING" | "NUMBER" | "BOOLEAN" | "JSON";
  description: string;
  updatedBy: string;
  createdAt: string;
  updatedAt: string;
};

type EditingConfig = Partial<Pick<ConfigEntry, "paramValue" | "description">>;

const PRESET_CONFIGS = [
  {
    paramKey: "max_iterations",
    paramValue: "1000",
    valueType: "NUMBER" as const,
    description: "Số lần lặp tối đa cho thuật toán Backtracking.",
  },
  {
    paramKey: "weekend_weight",
    paramValue: "2.0",
    valueType: "NUMBER" as const,
    description: "Hệ số ưu tiên ca cuối tuần khi cân bằng tải.",
  },
  {
    paramKey: "overnight_recovery_hours",
    paramValue: "24",
    valueType: "NUMBER" as const,
    description: "Số giờ nghỉ bắt buộc sau ca trực 24/24.",
  },
  {
    paramKey: "greedy_coverage_threshold",
    paramValue: "0.85",
    valueType: "NUMBER" as const,
    description: "Ngưỡng tỷ lệ phủ tối thiểu để thuật toán Greedy dừng sớm.",
  },
  {
    paramKey: "balance_score_min",
    paramValue: "0.7",
    valueType: "NUMBER" as const,
    description: "Ngưỡng balance_score tối thiểu cho phép lưu kết quả.",
  },
  {
    paramKey: "auto_compensation_enabled",
    paramValue: "true",
    valueType: "BOOLEAN" as const,
    description: "Tự động tạo ngày nghỉ bù sau khi xếp L01.",
  },
  {
    paramKey: "staff_exclusion_default",
    paramValue: "[]",
    valueType: "JSON" as const,
    description: "Danh sách ID nhân sự bị loại mặc định khỏi auto-schedule.",
  },
];

function ConfigRow({
  config,
  onSave,
  onDelete,
  saving,
}: {
  config: ConfigEntry;
  onSave: (updated: EditingConfig) => void;
  onDelete: () => void;
  saving: boolean;
}) {
  const [editing, setEditing] = useState(false);
  const [form, setForm] = useState<EditingConfig>({
    paramValue: config.paramValue,
    description: config.description,
  });
  const [saveMsg, setSaveMsg] = useState<{ type: "success" | "error"; text: string } | null>(null);
  const [confirmOpen, setConfirmOpen] = useState(false);

  const handleSave = async () => {
    if (form.paramValue === config.paramValue && form.description === config.description) {
      setEditing(false);
      return;
    }
    setSaveMsg(null);
    try {
      await api.updateAlgorithmConfig(config.paramKey, {
        paramValue: form.paramValue ?? config.paramValue,
        description: form.description ?? config.description,
      });
      setSaveMsg({ type: "success", text: "Đã lưu thay đổi." });
      setEditing(false);
      setTimeout(() => setSaveMsg(null), 3000);
      onSave(form);
    } catch (err) {
      setSaveMsg({ type: "error", text: getErrorMessage(err, "Lưu thất bại.") });
    }
  };

  const handleDeleteConfirm = async () => {
    try {
      await api.deleteAlgorithmConfig(config.paramKey);
      onDelete();
    } catch (err) {
      alert(getErrorMessage(err, "Xóa thất bại."));
    }
  };

  return (
    <div className="rounded-xl border border-outline-variant bg-surface-container-lowest p-4 hover:border-primary/40 transition-colors">
      <div className="flex items-start justify-between gap-4">
        <div className="flex-1 min-w-0 space-y-2">
          <div className="flex items-center gap-2 flex-wrap">
            <code className="text-label-md font-mono font-semibold text-primary bg-primary-fixed/20 px-2 py-0.5 rounded">
              {config.paramKey}
            </code>
            <span className="px-2 py-0.5 bg-surface-container-low text-on-surface-variant text-[10px] font-semibold rounded uppercase">
              {config.valueType}
            </span>
          </div>
          {editing ? (
            <input
              className="h-9 w-full rounded-lg border border-primary bg-surface px-3 text-label-md text-on-surface transition-all focus:outline-none focus:ring-2 focus:ring-primary/20"
              value={form.paramValue}
              onChange={(e) => setForm((f) => ({ ...f, paramValue: e.target.value }))}
            />
          ) : (
            <p className="text-body-sm text-on-surface font-mono bg-surface-container-low px-3 py-1.5 rounded-lg border border-outline-variant/30 inline-block max-w-full truncate">
              {config.paramValue}
            </p>
          )}
          {editing ? (
            <textarea
              className="w-full resize-none rounded-lg border border-primary bg-surface px-3 py-2 text-label-md text-on-surface transition-all focus:outline-none focus:ring-2 focus:ring-primary/20"
              rows={2}
              value={form.description ?? ""}
              onChange={(e) => setForm((f) => ({ ...f, description: e.target.value }))}
              placeholder="Mô tả thông số..."
            />
          ) : (
            <p className="text-label-sm text-on-surface-variant">{config.description}</p>
          )}
          <p className="text-label-xs text-outline">
            Cập nhật bởi <strong>{config.updatedBy || "—"}</strong> · {new Date(config.updatedAt).toLocaleDateString("vi-VN")}
          </p>
        </div>

        <div className="flex items-center gap-1 shrink-0">
          {saveMsg && (
            <span className={`text-label-xs mr-2 ${saveMsg.type === "success" ? "text-secondary" : "text-error"}`}>
              {saveMsg.text}
            </span>
          )}
          {editing ? (
            <>
              <button
                type="button"
                onClick={() => { setEditing(false); setForm({ paramValue: config.paramValue, description: config.description }); }}
                className="p-2 rounded-lg hover:bg-surface-container-low transition-colors text-label-sm text-on-surface-variant"
              >
                Hủy
              </button>
              <button
                type="button"
                onClick={() => void handleSave()}
                disabled={saving}
                className="px-3 py-1.5 rounded-lg bg-primary text-on-primary text-label-sm font-semibold hover:bg-primary/90 disabled:opacity-50 transition-colors"
              >
                Lưu
              </button>
            </>
          ) : (
            <>
              <button
                type="button"
                onClick={() => setEditing(true)}
                className="p-2 rounded-lg hover:bg-surface-container-low transition-colors"
                title="Chỉnh sửa"
                aria-label="Chỉnh sửa"
              >
                <span className="material-symbols-outlined text-[18px] text-on-surface-variant" aria-hidden="true">edit</span>
              </button>
              <button
                type="button"
                onClick={() => setConfirmOpen(true)}
                className="p-2 rounded-lg hover:bg-error-container transition-colors"
                title="Xóa"
                aria-label="Xóa"
              >
                <span className="material-symbols-outlined text-[18px] text-error" aria-hidden="true">delete</span>
              </button>
            </>
          )}
        </div>
      </div>
      <ConfirmDialog
        open={confirmOpen}
        onClose={() => setConfirmOpen(false)}
        onConfirm={handleDeleteConfirm}
        title={`Xóa cấu hình "${config.paramKey}"?`}
        description="Hành động này không thể hoàn tác."
        confirmLabel="Xóa"
        variant="danger"
      />
    </div>
  );
}

export default function AlgorithmConfigPage() {
  return <AlgorithmConfigContent />;
}

function AlgorithmConfigContent() {
  const role = useRole();
  const isAdmin = role === "ADMIN";
  const [configs, setConfigs] = useState<ConfigEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [createModalOpen, setCreateModalOpen] = useState(false);
  const [creating, setCreating] = useState(false);
  const [createMsg, setCreateMsg] = useState<{ type: "success" | "error"; text: string } | null>(null);

  const loadConfigs = async () => {
    let ignore = false;
    setLoading(true);
    api.getAllAlgorithmConfigs()
      .then((data) => {
        if (!ignore) setConfigs((data as ApiResponse<ConfigEntry[]>)?.data ?? []);
      })
      .catch(() => {
        if (!ignore) setConfigs([]);
      })
      .finally(() => {
        if (!ignore) setLoading(false);
      });
    return () => { ignore = true; };
  };

  useEffect(() => { void loadConfigs(); }, []);

  const handleCreate = async (form: { paramKey: string; paramValue: string; valueType: string; description: string }) => {
    setCreating(true);
    try {
      await api.createAlgorithmConfig({
        paramKey: form.paramKey.trim(),
        paramValue: form.paramValue.trim(),
        valueType: form.valueType,
        description: form.description.trim(),
      });
      setCreateMsg({ type: "success", text: "Đã tạo cấu hình mới." });
      await loadConfigs();
    } catch (err) {
      setCreateMsg({ type: "error", text: getErrorMessage(err, "Tạo thất bại.") });
    } finally {
      setCreating(false);
    }
  };

  const handleAddPreset = () => {
    setCreateModalOpen(true);
  };

  if (!isAdmin) {
    return (
      <>
        <div className="rounded-xl border border-tertiary-container bg-tertiary-container/20 p-8 flex flex-col items-center gap-3 text-center">
          <span className="material-symbols-outlined text-tertiary text-[40px]">lock</span>
          <h2 className="text-title-lg font-semibold text-on-surface">Không có quyền truy cập</h2>
          <p className="text-body-sm text-on-surface-variant max-w-md">
            Chỉ <strong>Quản lý lịch</strong> mới có quyền quản lý cấu hình thuật toán.
          </p>
        </div>
      </>
    );
  }

  return (
    <>
      <div className="space-y-6">
        {/* Runtime Config Card - All main parameters in one place */}
        <RuntimeConfigCard onConfigSaved={() => void loadConfigs()} />

        {/* Header */}
        <div className="flex items-center justify-between flex-wrap gap-3">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-[22px]">tune</span>
            <p className="text-body-sm text-on-surface-variant">
              {configs.length} cấu hình đang áp dụng. Thay đổi sẽ có hiệu lực cho lần chạy tiếp theo.
            </p>
          </div>
          <div className="flex items-center gap-2">
            <div className="relative group">
              <button
                type="button"
                className="px-3 py-2 rounded-lg border border-outline-variant bg-surface text-label-sm text-on-surface hover:bg-surface-container-low transition-colors flex items-center gap-1.5"
              >
                <span className="material-symbols-outlined text-[16px]">bolt</span>
                Thêm nhanh
              </button>
              <div className="absolute right-0 top-full mt-1 z-50 hidden group-hover:block w-72 bg-surface-container-lowest border border-outline-variant rounded-xl shadow-lg overflow-hidden">
                <div className="px-3 py-2 border-b border-outline-variant">
                  <p className="text-label-xs text-on-surface-variant uppercase tracking-wider font-semibold">Mẫu có sẵn</p>
                </div>
                {PRESET_CONFIGS.map((p) => (
                  <button
                    key={p.paramKey}
                    type="button"
                    className="w-full text-left px-3 py-2 hover:bg-primary-fixed/20 transition-colors"
                    onClick={() => handleAddPreset()}
                  >
                    <p className="text-label-sm font-semibold text-on-surface font-mono">{p.paramKey}</p>
                    <p className="text-label-xs text-on-surface-variant truncate">{p.description}</p>
                  </button>
                ))}
              </div>
            </div>
            <button
              type="button"
              onClick={() => setCreateModalOpen(true)}
              className="inline-flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md font-semibold text-on-primary hover:bg-primary/90 transition-colors"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
              Thêm cấu hình
            </button>
          </div>
        </div>

        {/* List */}
        {loading ? (
          <div className="space-y-3">
            <Skeleton className="h-24 rounded-xl" />
            <Skeleton className="h-24 rounded-xl" />
            <Skeleton className="h-24 rounded-xl" />
          </div>
        ) : configs.length === 0 ? (
          <div className="rounded-xl border border-dashed border-outline-variant p-12 text-center">
            <span className="material-symbols-outlined text-outline text-[40px]">tune</span>
            <p className="mt-3 text-body-sm text-on-surface-variant">Chưa có cấu hình nào.</p>
            <p className="text-label-sm text-outline">Dùng &ldquo;Thêm nhanh&rdquo; để tạo từ mẫu có sẵn.</p>
          </div>
        ) : (
          <div className="space-y-3">
            {configs.map((config) => (
              <ConfigRow
                key={config.paramKey}
                config={config}
                onSave={(updated) => setConfigs((prev) => prev.map((c) => c.paramKey === config.paramKey ? { ...c, ...updated } : c))}
                onDelete={() => setConfigs((prev) => prev.filter((c) => c.paramKey !== config.paramKey))}
                saving={false}
              />
            ))}
          </div>
        )}

        {/* Info card */}
        <SectionCard title="Về cấu hình thuật toán" description="Thông tin tham khảo">
          <div className="space-y-3 p-4">
            {[
              { key: "max_iterations", label: "max_iterations", desc: "Chỉ áp dụng cho Backtracking. Tăng để tìm lời giải tốt hơn nhưng chậm hơn." },
              { key: "weekend_weight", label: "weekend_weight", desc: "Hệ số > 1 ưu tiên chia đều ca cuối tuần. Đặt = 1 để tắt ưu tiên." },
              { key: "auto_compensation_enabled", label: "auto_compensation_enabled", desc: "Khi bật, hệ thống tự động tạo ngày nghỉ bù sau mỗi ca L01." },
              { key: "greedy_coverage_threshold", label: "greedy_coverage_threshold", desc: "Greedy dừng sớm khi đạt ngưỡng phủ. Giảm để chạy nhanh hơn, tăng để phủ kỹ hơn." },
            ].map((info) => (
              <div key={info.key} className="flex items-start gap-3 p-2 rounded-lg hover:bg-surface-container-low transition-colors">
                <code className="text-label-sm font-mono font-semibold text-primary bg-primary-fixed/20 px-2 py-0.5 rounded shrink-0 mt-0.5">
                  {info.label}
                </code>
                <p className="text-label-sm text-on-surface-variant">{info.desc}</p>
              </div>
            ))}
          </div>
        </SectionCard>
      </div>

      {/* Create Modal */}
      <CreateConfigModal
        open={createModalOpen}
        onClose={() => { setCreateModalOpen(false); setCreateMsg(null); }}
        onCreate={handleCreate}
        creating={creating}
        message={createMsg}
      />
    </>
  );
}
