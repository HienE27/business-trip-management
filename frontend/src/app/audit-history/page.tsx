"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { AuditHistory } from "@/types/api";

export default function AuditHistoryPage() {
  const [records, setRecords] = useState<AuditHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [moduleFilter, setModuleFilter] = useState("");
  const [actionFilter, setActionFilter] = useState("");
  const [dateFilter, setDateFilter] = useState("");
  const [message, setMessage] = useState("");

  const fetchRecords = useCallback(async () => {
    try {
      setLoading(true);
      setMessage("");
      const res = await api.get<AuditHistory[]>("/audit-history");
      setRecords(res ?? []);
    } catch (err) {
      setRecords([]);
      setMessage(getErrorMessage(err, "Không thể tải nhật ký thao tác."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchRecords();
  }, [fetchRecords]);

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

  const summary = useMemo(() => {
    return [
      ["Tổng sự kiện", String(records.length)],
      ["Cập nhật", String(records.filter((r) => r.action === "UPDATE").length)],
      ["Tạo mới", String(records.filter((r) => r.action === "CREATE").length)],
      ["Xóa", String(records.filter((r) => r.action === "DELETE").length)],
    ];
  }, [records]);

  const tableNameOptions = useMemo(() => {
    return Array.from(new Set(records.map((r) => r.tableName)));
  }, [records]);

  function getActionBadgeClass(action: string) {
    switch (action) {
      case "UPDATE": return "bg-primary-fixed/30 text-primary border border-primary/20";
      case "CREATE": return "bg-secondary-container text-secondary border border-secondary/20";
      case "DELETE": return "bg-error-container text-error border border-error/20";
      default: return "bg-surface-container-high text-on-surface-variant border border-outline/10";
    }
  }

  function getSummaryAccent(label: string) {
    if (label === "Xóa") return "border-l-4 border-l-error";
    if (label === "Tạo mới") return "border-l-4 border-l-secondary";
    if (label === "Cập nhật") return "border-l-4 border-l-primary";
    return "border-l-4 border-l-outline";
  }

  function formatDate(dateStr: string) {
    try {
      return new Date(dateStr).toLocaleString("vi-VN");
    } catch {
      return dateStr;
    }
  }

  return (
    <DashboardShell
      activeCode="M06-AUDIT"
      description="Theo dõi lịch sử thay đổi trên toàn hệ thống."
      title="Nhật ký thao tác"
    >
      <div className="space-y-6">
        {/* Header */}
        <section className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <p className="text-label-sm text-on-surface-variant uppercase tracking-widest">Nhật ký thao tác</p>
            <p className="mt-1 font-body-sm text-on-surface-variant">
              Theo dõi lịch sử thay đổi trên toàn hệ thống.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <button
              className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-label-md text-primary shadow-sm transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
              onClick={fetchRecords}
              type="button"
            >
              <span className="material-symbols-outlined text-[18px]">refresh</span>
              Làm mới
            </button>
          </div>
        </section>

        {message && (
          <div className="rounded-lg border border-error/20 bg-error-container px-4 py-3 text-sm text-error">
            {message}
          </div>
        )}

        {/* Summary Cards */}
        <section className="grid gap-4 md:grid-cols-4">
          {summary.map((item) => {
            const [label, value] = item;
            return (
              <div
                className={`rounded-lg border-t border-r border-b border-outline-variant bg-surface-container-lowest p-5 shadow-sm hover:bg-surface-container-low ${getSummaryAccent(label)}`}
                key={label}
              >
                <p className="text-label-sm text-on-surface-variant uppercase tracking-wider">{label}</p>
                <p className="mt-3 text-display-lg text-on-surface">{value}</p>
              </div>
            );
          })}
        </section>

        {/* Filter bar */}
        <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
          <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
            <div className="relative">
              <label className="mb-1 block text-label-sm text-on-surface-variant uppercase tracking-wider" htmlFor="audit-search">Tìm kiếm</label>
              <div className="relative">
                <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[18px]">search</span>
                <input
                  autoComplete="off"
                  className="w-full rounded-lg border border-outline-variant bg-surface py-2 pl-9 pr-3 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                  id="audit-search"
                  placeholder="Tìm theo người thao tác, đối tượng..."
                  type="text"
                  value={searchKeyword}
                  onChange={(e) => setSearchKeyword(e.target.value)}
                />
              </div>
            </div>
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant uppercase tracking-wider" htmlFor="audit-module">Module</label>
              <select
                id="audit-module"
                className="w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                value={moduleFilter}
                onChange={(e) => setModuleFilter(e.target.value)}
              >
                <option value="">Tất cả Module</option>
                {tableNameOptions.map((t) => (
                  <option key={t} value={t}>{t}</option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant uppercase tracking-wider" htmlFor="audit-action">Hành động</label>
              <select
                id="audit-action"
                className="w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                value={actionFilter}
                onChange={(e) => setActionFilter(e.target.value)}
              >
                <option value="">Tất cả Hành động</option>
                <option value="CREATE">Tạo mới</option>
                <option value="UPDATE">Cập nhật</option>
                <option value="DELETE">Xóa</option>
              </select>
            </div>
            <div>
              <label className="mb-1 block text-label-sm text-on-surface-variant uppercase tracking-wider" htmlFor="audit-date">Ngày</label>
              <input
                className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2 font-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                id="audit-date"
                type="date"
                value={dateFilter}
                onChange={(e) => setDateFilter(e.target.value)}
              />
            </div>
          </div>
        </section>

        {/* Audit Table */}
        <section className="overflow-hidden rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm">
          {loading ? (
            <div className="flex items-center justify-center py-16">
              <div className="size-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
            </div>
          ) : filtered.length === 0 ? (
            <div className="text-center py-16">
              <span className="material-symbols-outlined text-5xl text-outline">history</span>
              <p className="mt-4 text-on-surface-variant">Chưa có nhật ký thao tác nào.</p>
            </div>
          ) : (
            <div className="overflow-x-auto">
              <table className="w-full border-collapse text-left">
                <thead>
                  <tr className="border-b border-outline-variant bg-surface-container-low">
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Thời gian</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Người thao tác</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Hành động</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Module</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Đối tượng (ID)</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">Mô tả thay đổi</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant font-body-sm">
                  {filtered.map((row) => (
                    <tr className="transition-colors hover:bg-surface-container-low group" key={row.id}>
                      <td className="px-5 py-3 text-on-surface">{formatDate(row.createdAt)}</td>
                      <td className="px-5 py-3 font-medium text-on-surface">{row.userName ?? row.userId ?? "Hệ thống"}</td>
                      <td className="px-5 py-3">
                        <span className={`inline-flex items-center gap-1.5 rounded-full px-3 py-1 text-[11px] font-bold ${getActionBadgeClass(row.action)}`}>
                          {row.action === "UPDATE" ? "Cập nhật" : row.action === "CREATE" ? "Tạo mới" : row.action === "DELETE" ? "Xóa" : row.action}
                        </span>
                      </td>
                      <td className="px-5 py-3 text-on-surface-variant">{row.tableName}</td>
                      <td className="px-5 py-3 text-on-surface">{row.recordId}</td>
                      <td className="max-w-xs truncate px-5 py-3 text-on-surface-variant" title={row.newValues ?? row.oldValues ?? ""}>
                        {row.newValues ? "Cập nhật dữ liệu mới" : row.oldValues ? "Xóa dữ liệu cũ" : "—"}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
        </section>
      </div>
    </DashboardShell>
  );
}
