"use client";

import { useEffect, useState } from "react";
import { IconButton } from "@/components/ui";
import type { AuditHistory } from "@/types/api";
import { getAction, fmtDateShort, fmtTime } from "./auditUtils";
import { SyntaxHighlight } from "./SyntaxHighlight";
import { JsonDiffTable } from "./JsonDiffTable";

// ─── Detail Modal ──────────────────────────────────────────────────────────────

type DetailTab = "diff" | "old" | "new" | "raw";

export function AuditDetailModal({ record, onClose }: { record: AuditHistory; onClose: () => void }) {
  const [tab, setDetailTab] = useState<DetailTab>("diff");

  useEffect(() => {
    const handler = (e: KeyboardEvent) => { if (e.key === "Escape") onClose(); };
    document.addEventListener("keydown", handler);
    return () => document.removeEventListener("keydown", handler);
  }, [onClose]);

  const st = getAction(record.action);

  const TABS: { id: DetailTab; label: string }[] = [
    { id: "diff", label: "So sánh" },
    { id: "old",  label: "Dữ liệu cũ" },
    { id: "new",  label: "Dữ liệu mới" },
    { id: "raw",  label: "JSON" },
  ];

  const noData = !record.oldData && !record.newData;

  return (
    <div className="fixed inset-0 z-50 flex items-stretch justify-end" onClick={onClose}>
      <div className="absolute inset-0 bg-black/20 backdrop-blur-[2px]" />
      <div
        className="relative bg-surface-container-lowest border-l border-outline-variant shadow-2xl flex flex-col overflow-hidden"
        style={{ width: "min(520px, 100vw)" }}
        onClick={(e) => e.stopPropagation()}
      >
        {/* Header */}
        <div className="flex items-center gap-3 px-4 py-3 border-b border-outline-variant bg-surface shrink-0">
          <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-lg ${st.iconBg}`}>
            <span className="material-symbols-outlined text-[16px]">{st.icon}</span>
          </div>
          <div className="flex flex-col min-w-0">
            <div className="flex items-center gap-2 flex-wrap">
              <span className={`text-[12px] font-bold ${st.chipColor}`}>{st.label}</span>
              <span className="text-[13px] font-semibold text-on-surface truncate">{record.tableName}</span>
              <span className="text-[12px] text-outline">#{record.recordId}</span>
            </div>
            <p className="text-[11px] text-on-surface-variant mt-0.5">
              {fmtDateShort(record.createdAt.split("T")[0])} · {fmtTime(record.createdAt)}
            </p>
          </div>
          <div className="flex-1" />
          <IconButton
            label="Đóng"
            variant="ghost"
            size="sm"
            onClick={onClose}
            className="shrink-0 text-on-surface-variant"
          >
            <span className="material-symbols-outlined text-[16px]" aria-hidden="true">close</span>
          </IconButton>
        </div>

        {/* Meta row */}
        <div className="flex items-center gap-4 px-4 py-2.5 border-b border-outline-variant bg-surface shrink-0 text-[12px]">
          <span className="text-on-surface-variant">
            Người thực hiện:{" "}
            <strong className="font-semibold text-on-surface">
              {record.userName ?? (record.userId != null && record.userId > 0 ? `#${record.userId}` : <span className="text-outline italic">\u2014</span>)}
            </strong>
          </span>
          {record.ipAddress && (
            <span className="text-on-surface-variant">
              IP: <strong className="font-semibold text-on-surface">{record.ipAddress}</strong>
            </span>
          )}
        </div>

        {/* Tabs */}
        <div className="flex items-end gap-1 px-4 pt-3 bg-surface border-b border-outline-variant shrink-0">
          {TABS.map((t) => {
            const disabled = noData && (t.id === "old" || t.id === "new" || t.id === "raw");
            return (
              <button
                key={t.id}
                className={`px-3 py-1.5 text-[12px] font-medium rounded-t-lg border transition-all ${
                  tab === t.id
                    ? "bg-surface-container-lowest border-outline-variant border-b-transparent text-on-surface"
                    : disabled
                    ? "border-transparent text-outline/30 cursor-not-allowed"
                    : "border-transparent text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                }`}
                onClick={() => !disabled && setDetailTab(t.id)}
                disabled={disabled}
                type="button"
                role="tab"
                aria-selected={tab === t.id}
              >
                {t.label}
              </button>
            );
          })}
        </div>

        {/* Body */}
        <div className="flex-1 overflow-y-auto p-4">
          {tab === "diff" && (
            <JsonDiffTable oldJson={record.oldData} newJson={record.newData} />
          )}
          {tab === "old" && (
            record.oldData
              ? <SyntaxHighlight json={record.oldData} />
              : <p className="text-[13px] text-on-surface-variant italic py-4">Không có dữ liệu cũ.</p>
          )}
          {tab === "new" && (
            record.newData
              ? <SyntaxHighlight json={record.newData} />
              : <p className="text-[13px] text-on-surface-variant italic py-4">Không có dữ liệu mới.</p>
          )}
          {tab === "raw" && (
            <div className="space-y-4">
              {record.oldData && (
                <div>
                  <p className="text-[11px] font-semibold text-error uppercase tracking-wide mb-2">Dữ liệu cũ</p>
                  <SyntaxHighlight json={record.oldData} />
                </div>
              )}
              {record.newData && (
                <div>
                  <p className="text-[11px] font-semibold text-secondary uppercase tracking-wide mb-2">Dữ liệu mới</p>
                  <SyntaxHighlight json={record.newData} />
                </div>
              )}
              {noData && <p className="text-[13px] text-on-surface-variant italic">Không có dữ liệu chi tiết.</p>}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
