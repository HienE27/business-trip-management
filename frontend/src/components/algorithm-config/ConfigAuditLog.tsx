"use client";

import { useEffect, useState } from "react";
import { api } from "@/lib/api-client";

type AuditEntry = {
  id: number;
  paramKey: string;
  oldValue: string | null;
  newValue: string;
  action: string;
  changedByUsername: string | null;
  createdAt: string;
};

const ACTION_TONES: Record<string, { bg: string; text: string; label: string }> = {
  CREATE: { bg: "bg-secondary-container", text: "text-on-secondary-container", label: "Tạo" },
  UPDATE: { bg: "bg-primary-fixed", text: "text-primary", label: "Cập nhật" },
  DELETE: { bg: "bg-error-container", text: "text-on-error-container", label: "Xóa" },
  BULK_SYNC: { bg: "bg-tertiary-container", text: "text-on-tertiary-container", label: "Bulk sync" },
  BULK_UPDATE: { bg: "bg-tertiary-container", text: "text-on-tertiary-container", label: "Bulk cập nhật" },
};

export function ConfigAuditLog() {
  const [entries, setEntries] = useState<AuditEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [filterKey, setFilterKey] = useState("");

  useEffect(() => {
    let mounted = true;
    setLoading(true);
    api.getAlgorithmConfigAudit(undefined, 0, 100)
      .then(data => { if (mounted) setEntries(Array.isArray(data?.content) ? data.content : []); })
      .catch(() => { if (mounted) setEntries([]); })
      .finally(() => { if (mounted) setLoading(false); });
    return () => { mounted = false; };
  }, []);

  const filtered = entries.filter(e =>
    !filterKey || e.paramKey.toLowerCase().includes(filterKey.toLowerCase())
  );

  return (
    <div className="space-y-4">
      <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant p-5">
        <div className="flex items-center justify-between gap-3 flex-wrap mb-4">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-primary text-[20px]" aria-hidden="true">history</span>
            <p className="text-title-sm font-semibold text-on-surface">Lịch sử thay đổi</p>
            <span className="px-2 py-0.5 rounded-full text-[11px] font-semibold bg-primary-fixed text-primary border border-primary/20">
              {filtered.length} mục
            </span>
          </div>
          <div className="relative">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]" aria-hidden="true">search</span>
            <input
              type="text"
              placeholder="Lọc theo paramKey..."
              value={filterKey}
              onChange={e => setFilterKey(e.target.value)}
              className="h-9 w-64 pl-9 pr-3 rounded-xl bg-surface-container-low text-label-sm focus:bg-surface-container-lowest focus:outline-none focus:ring-2 focus:ring-primary/20 border border-transparent focus:border-primary transition-all"
            />
          </div>
        </div>

        {loading ? (
          <div className="space-y-2">
            {[1, 2, 3].map(i => (
              <div key={i} className="h-12 rounded-xl bg-surface-container-low animate-pulse" />
            ))}
          </div>
        ) : filtered.length === 0 ? (
          <div className="text-center py-12">
            <span className="material-symbols-outlined text-[48px] text-on-surface-variant">inbox</span>
            <p className="text-body-sm text-on-surface-variant mt-2">Chưa có thay đổi nào được ghi nhận</p>
          </div>
        ) : (
          <div className="overflow-x-auto rounded-xl border border-outline-variant">
            <table className="w-full text-left border-collapse">
              <thead className="bg-surface-container-low">
                <tr className="border-b border-outline-variant">
                  <th className="px-4 py-3 text-[11px] font-semibold text-on-surface-variant uppercase">Thời gian</th>
                  <th className="px-4 py-3 text-[11px] font-semibold text-on-surface-variant uppercase">Người thay đổi</th>
                  <th className="px-4 py-3 text-[11px] font-semibold text-on-surface-variant uppercase">Hành động</th>
                  <th className="px-4 py-3 text-[11px] font-semibold text-on-surface-variant uppercase">Param key</th>
                  <th className="px-4 py-3 text-[11px] font-semibold text-on-surface-variant uppercase">Giá trị cũ → mới</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-outline-variant">
                {filtered.map(e => {
                  const tone = ACTION_TONES[e.action] ?? ACTION_TONES.UPDATE;
                  return (
                    <tr key={e.id} className="hover:bg-surface-container-low/50 transition-colors">
                      <td className="px-4 py-3 text-label-sm text-on-surface tabular-nums whitespace-nowrap">
                        {new Date(e.createdAt).toLocaleString("vi-VN", {
                          day: "2-digit", month: "2-digit", year: "numeric",
                          hour: "2-digit", minute: "2-digit",
                        })}
                      </td>
                      <td className="px-4 py-3 text-label-sm text-on-surface">
                        {e.changedByUsername ?? <span className="text-on-surface-variant italic">system</span>}
                      </td>
                      <td className="px-4 py-3">
                        <span className={`inline-flex px-2.5 py-0.5 rounded-full text-[11px] font-semibold ${tone.bg} ${tone.text}`}>
                          {tone.label}
                        </span>
                      </td>
                      <td className="px-4 py-3">
                        <code className="font-mono text-[12px] text-primary bg-primary-fixed/50 px-1.5 py-0.5 rounded">{e.paramKey}</code>
                      </td>
                      <td className="px-4 py-3 max-w-md">
                        <div className="flex items-center gap-2 text-label-sm">
                          <code className="font-mono text-[11px] text-on-surface-variant bg-surface-container-low px-1.5 py-0.5 rounded truncate max-w-[180px]">
                            {e.oldValue ?? "(trống)"}
                          </code>
                          <span className="material-symbols-outlined text-[14px] text-on-surface-variant">arrow_forward</span>
                          <code className="font-mono text-[11px] text-on-surface bg-primary-fixed/30 px-1.5 py-0.5 rounded truncate max-w-[180px]">
                            {e.newValue || "(đã xóa)"}
                          </code>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  );
}