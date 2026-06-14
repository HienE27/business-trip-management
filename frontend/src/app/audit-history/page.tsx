"use client";

import { useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { AuditHistory } from "@/types/api";

type ActionFilter = "" | "CREATE" | "UPDATE" | "DELETE";

export default function AuditHistoryPage() {
  const [records, setRecords] = useState<AuditHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [moduleFilter, setModuleFilter] = useState("");
  const [actionFilter, setActionFilter] = useState<ActionFilter>("");
  const [message, setMessage] = useState("");
  const [dateFilter, setDateFilter] = useState("");
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());

  useEffect(() => {
    let cancelled = false;

    const load = async () => {
      setLoading(true);
      setMessage("");

      const res = await api.get<AuditHistory[]>("/audit-history").catch((err) => {
        if (!cancelled) {
          setRecords([]);
          setMessage(getErrorMessage(err, "Không thể tải nhật ký thao tác."));
        }
        return null;
      });

      if (cancelled) return;
      if (res) setRecords(res);
      setLoading(false);
    };

    void load();

    return () => {
      cancelled = true;
    };
  }, []);

  const filtered = useMemo(() => {
    return records.filter((r) => {
      const keyword = searchKeyword.trim().toLowerCase();
      const matchesKeyword = !keyword
        || (r.userName ?? "").toLowerCase().includes(keyword)
        || r.tableName.toLowerCase().includes(keyword)
        || r.action.toLowerCase().includes(keyword);
      const matchesModule = !moduleFilter || r.tableName === moduleFilter;
      const matchesAction = !actionFilter || r.action === actionFilter;
      const matchesDate = !dateFilter || r.createdAt.startsWith(dateFilter);
      return matchesKeyword && matchesModule && matchesAction && matchesDate;
    });
  }, [records, searchKeyword, moduleFilter, actionFilter, dateFilter]);

  // Group records by date
  const groupedByDate = useMemo(() => {
    const groups: Record<string, AuditHistory[]> = {};
    for (const r of filtered) {
      const dateKey = r.createdAt.split("T")[0];
      if (!groups[dateKey]) groups[dateKey] = [];
      groups[dateKey].push(r);
    }
    return Object.entries(groups).sort(([a], [b]) => b.localeCompare(a));
  }, [filtered]);

  const summary = useMemo(() => ({
    total: filtered.length,
    create: filtered.filter((r) => r.action === "CREATE").length,
    update: filtered.filter((r) => r.action === "UPDATE").length,
    delete: filtered.filter((r) => r.action === "DELETE").length,
  }), [filtered]);

  const tableNameOptions = useMemo(() => {
    return Array.from(new Set(records.map((r) => r.tableName)));
  }, [records]);

  function getActionBadgeClass(action: string) {
    switch (action) {
      case "CREATE": return "bg-secondary-container text-secondary border border-secondary/20";
      case "UPDATE": return "bg-primary-fixed/30 text-primary border border-primary/20";
      case "DELETE": return "bg-error-container text-error border border-error/20";
      default: return "bg-surface-container-high text-on-surface-variant border border-outline/10";
    }
  }

  function getActionLabel(action: string) {
    switch (action) {
      case "CREATE": return "Tạo mới";
      case "UPDATE": return "Cập nhật";
      case "DELETE": return "Xóa";
      default: return action;
    }
  }

  function getActionIcon(action: string) {
    switch (action) {
      case "CREATE": return "add_circle";
      case "UPDATE": return "edit";
      case "DELETE": return "delete";
      default: return "info";
    }
  }

  function formatDate(dateStr: string) {
    try {
      const d = new Date(dateStr);
      return d.toLocaleDateString("vi-VN", {
        weekday: "long",
        year: "numeric",
        month: "long",
        day: "numeric",
      });
    } catch {
      return dateStr;
    }
  }

  function formatDateShort(dateStr: string) {
    try {
      return new Date(dateStr).toLocaleDateString("vi-VN");
    } catch {
      return dateStr;
    }
  }

  function formatTime(dateStr: string) {
    try {
      return new Date(dateStr).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
    } catch {
      return dateStr;
    }
  }

  function toggleExpand(id: number) {
    setExpandedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  return (
    <DashboardShell
      activeSection="audit-history"
      description="Theo dõi lịch sử thay đổi trên toàn hệ thống."
      title="Nhật ký thao tác"
    >
      <div className="space-y-6">
        {message && (
          <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
            {message}
          </div>
        )}

        {/* Summary Cards */}
        <section className="grid gap-4 md:grid-cols-4">
          {[
            { label: "Tổng sự kiện", value: summary.total, accent: "border-l-outline" },
            { label: "Tạo mới", value: summary.create, accent: "border-l-secondary" },
            { label: "Cập nhật", value: summary.update, accent: "border-l-primary" },
            { label: "Xóa", value: summary.delete, accent: "border-l-error" },
          ].map((item) => (
            <div
              className={`rounded-lg border-t border-r border-b border-outline-variant bg-surface-container-lowest p-5 shadow-sm hover:bg-surface-container-low transition-colors ${item.accent}`}
              key={item.label}
            >
              <p className="text-label-sm text-on-surface-variant">{item.label}</p>
              <p className="mt-3 text-display-lg text-on-surface font-bold">{loading ? "\u2014" : item.value}</p>
            </div>
          ))}
        </section>

        {/* Filter bar */}
        <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant" htmlFor="audit-search">
                Tìm kiếm
              </label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">search</span>
                <input
                  autoComplete="off"
                  className="w-full rounded-lg border border-outline-variant bg-surface py-2 pl-9 pr-3 text-[13px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                  id="audit-search"
                  placeholder="Người thao tác, đối tượng..."
                  type="text"
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                />
              </div>
            </div>
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant" htmlFor="audit-module">
                Module
              </label>
              <div className="relative">
                <select
                  className="w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 text-[13px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer pr-8"
                  id="audit-module"
                  value={moduleFilter}
                  onChange={(e) => setModuleFilter(e.target.value)}
                >
                  <option value="">Tất cả Module</option>
                  {tableNameOptions.map((t) => (
                    <option key={t} value={t}>{t}</option>
                  ))}
                </select>
                <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">expand_more</span>
              </div>
            </div>
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant" htmlFor="audit-action">
                Hành động
              </label>
              <div className="relative">
                <select
                  className="w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 text-[13px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer pr-8"
                  id="audit-action"
                  value={actionFilter}
                  onChange={(e) => setActionFilter(e.target.value as ActionFilter)}
                >
                  <option value="">Tất cả Hành động</option>
                  <option value="CREATE">Tạo mới</option>
                  <option value="UPDATE">Cập nhật</option>
                  <option value="DELETE">Xóa</option>
                </select>
                <span className="material-symbols-outlined absolute right-3 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">expand_more</span>
              </div>
            </div>
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant" htmlFor="audit-date">
                Ngày
              </label>
              <input
                className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2 text-[13px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                id="audit-date"
                type="date"
                value={dateFilter}
                onChange={(e) => setDateFilter(e.target.value)}
              />
            </div>
          </div>
        </section>

        {/* Audit Timeline */}
        <section className="overflow-hidden rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm">
          {loading ? (
            <div className="flex items-center justify-center py-16">
              <div className="size-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : groupedByDate.length === 0 ? (
            <div className="py-20 text-center">
              <span className="material-symbols-outlined text-5xl text-outline">history</span>
              <p className="mt-4 text-on-surface-variant">Chưa có nhật ký thao tác nào.</p>
            </div>
          ) : (
            <div className="divide-y divide-outline-variant/50">
              {groupedByDate.map(([dateKey, dayRecords]) => (
                <div key={dateKey}>
                  {/* Date header */}
                  <div className="flex items-center gap-3 bg-surface-container-low px-5 py-3 border-b border-outline-variant">
                    <span className="material-symbols-outlined text-[18px] text-outline">calendar_today</span>
                    <span className="text-[13px] font-semibold text-on-surface">{formatDate(dateKey + "T00:00:00")}</span>
                    <span className="ml-auto text-[12px] text-outline">
                      {dayRecords.length} sự kiện
                    </span>
                  </div>

                  {/* Records for this date */}
                  {dayRecords.map((row) => {
                    const isExpanded = expandedIds.has(row.id);
                    return (
                      <div key={row.id}>
                        <button
                          className="w-full flex items-center gap-4 px-5 py-3.5 text-left transition-colors hover:bg-surface-container-low group"
                          onClick={() => toggleExpand(row.id)}
                          type="button"
                        >
                          {/* Action icon */}
                          <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full ${
                            row.action === "CREATE" ? "bg-secondary-container text-secondary" :
                            row.action === "DELETE" ? "bg-error-container text-error" :
                            "bg-primary-fixed text-primary"
                          }`}>
                            <span className="material-symbols-outlined text-[16px]">{getActionIcon(row.action)}</span>
                          </div>

                          {/* Main info */}
                          <div className="flex-1 min-w-0">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className={`inline-flex items-center gap-1 rounded-full px-2.5 py-0.5 text-[11px] font-bold ${getActionBadgeClass(row.action)}`}>
                                {getActionLabel(row.action)}
                              </span>
                              <span className="text-[13px] font-medium text-on-surface truncate">{row.tableName}</span>
                              <span className="text-[12px] text-outline">#{row.recordId}</span>
                            </div>
                            <p className="text-[12px] text-on-surface-variant mt-0.5">
                              {row.userName ?? row.userId ?? "Hệ thống"}
                              {row.ipAddress ? ` \u2022 ${row.ipAddress}` : ""}
                            </p>
                          </div>

                          {/* Time + expand icon */}
                          <div className="flex items-center gap-3 shrink-0">
                            <span className="text-[12px] text-outline font-medium">{formatTime(row.createdAt)}</span>
                            <span className={`material-symbols-outlined text-[18px] text-outline transition-transform ${isExpanded ? "rotate-180" : ""}`}>
                              expand_more
                            </span>
                          </div>
                        </button>

                        {/* Expanded detail */}
                        {isExpanded && (
                          <div className="px-5 pb-4 bg-surface">
                            <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4 space-y-3">
                              {row.oldData && (
                                    <div>
                                      <p className="text-label-sm text-error mb-1">Dữ liệu cũ</p>
                                      <pre className="text-[12px] text-on-surface-variant whitespace-pre-wrap break-all bg-surface-container-low p-2 rounded">
                                        {row.oldData}
                                      </pre>
                                    </div>
                                  )}
                                  {row.newData && (
                                    <div>
                                      <p className="text-label-sm text-secondary mb-1">Dữ liệu mới</p>
                                      <pre className="text-[12px] text-on-surface-variant whitespace-pre-wrap break-all bg-surface-container-low p-2 rounded">
                                        {row.newData}
                                      </pre>
                                    </div>
                                  )}
                                  {!row.oldData && !row.newData && (
                                <p className="text-[12px] text-outline italic">Không có chi tiết dữ liệu.</p>
                              )}
                              <div className="flex items-center gap-4 text-[11px] text-outline pt-1">
                                <span>ID bản ghi: {row.recordId}</span>
                                {row.ipAddress && <span>IP: {row.ipAddress}</span>}
                              </div>
                            </div>
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              ))}
            </div>
          )}
        </section>

        {/* Footer */}
        {!loading && groupedByDate.length > 0 && (
          <div className="flex items-center justify-between px-1">
            <p className="text-[12px] text-outline">
              Hiển thị {filtered.length} / {records.length} sự kiện
            </p>
          </div>
        )}
      </div>
    </DashboardShell>
  );
}
