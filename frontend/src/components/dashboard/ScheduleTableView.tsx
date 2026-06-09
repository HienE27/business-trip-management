"use client";

import { useState, useMemo, useCallback, useId } from "react";
import type { Schedule } from "@/types/api";

// ─── Color tokens ────────────────────────────────────────────────────────────
const TONE: Record<string, { bg: string; text: string; dot: string; label: string }> = {
  L01: { bg: "bg-blue-50",    text: "text-blue-700",   dot: "bg-blue-500",   label: "Trực 24/24"   },
  L02: { bg: "bg-emerald-50",text: "text-emerald-700",dot: "bg-emerald-500",label: "Thông tầm"      },
  L03: { bg: "bg-amber-50",  text: "text-amber-700",  dot: "bg-amber-500",  label: "Dịch vụ"      },
  L04: { bg: "bg-violet-50", text: "text-violet-700", dot: "bg-violet-500", label: "Chuyên gia"    },
};

const WEEKDAY_VN = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"] as const;

type SortKey = "workDate" | "shiftType" | "staffName" | "hasConflict";
type SortDir = "asc" | "desc";
type FilterConflict = "all" | "conflict" | "clean";
type FilterStatus = "all" | "active" | "archived";

type ScheduleTableViewProps = {
  schedules: Schedule[];
  onEdit?: (s: Schedule) => void;
  onDelete?: (s: Schedule) => void;
  onResolveConflict?: (s: Schedule) => void;
  canEdit?: boolean;
};

const PAGE_SIZE = 20;

export function ScheduleTableView({
  schedules,
  onEdit,
  onDelete,
  onResolveConflict,
  canEdit = false,
}: ScheduleTableViewProps) {
  const uid = useId();

  // ── Filters ───────────────────────────────────────────────────────────────
  const [search, setSearch] = useState("");
  const [filterType, setFilterType] = useState<string>("all");
  const [filterStaff, setFilterStaff] = useState<string>("all");
  const [filterConflict, setFilterConflict] = useState<FilterConflict>("all");
  const [filterStatus, setFilterStatus] = useState<FilterStatus>("all");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");

  // ── Sort ─────────────────────────────────────────────────────────────────
  const [sortKey, setSortKey] = useState<SortKey>("workDate");
  const [sortDir, setSortDir] = useState<SortDir>("asc");

  // ── Pagination ────────────────────────────────────────────────────────────
  const [page, setPage] = useState(1);

  // ── Unique values for filter dropdowns ───────────────────────────────────
  const allStaff = useMemo(() => {
    const map = new Map<number, string>();
    schedules.forEach((s) => map.set(s.staff.id, s.staff.fullName));
    return Array.from(map.entries()).sort((a, b) => a[1].localeCompare(b[1]));
  }, [schedules]);

  const allDates = useMemo(() => {
    const dates = new Set(schedules.map((s) => s.workDate.split("T")[0]));
    return Array.from(dates).sort();
  }, [schedules]);

  // ── Filter + Sort ────────────────────────────────────────────────────────
  const filtered = useMemo(() => {
    let result = schedules;

    if (search.trim()) {
      const q = search.toLowerCase();
      result = result.filter(
        (s) =>
          s.staff.fullName.toLowerCase().includes(q) ||
          s.shiftType.name.toLowerCase().includes(q) ||
          String(s.id).includes(q)
      );
    }

    if (filterType !== "all") result = result.filter((s) => s.shiftType.id === filterType);
    if (filterStaff !== "all") result = result.filter((s) => s.staff.id === Number(filterStaff));
    if (filterConflict === "conflict") result = result.filter((s) => s.hasConflict);
    if (filterConflict === "clean")    result = result.filter((s) => !s.hasConflict);
    if (dateFrom) result = result.filter((s) => s.workDate >= dateFrom);
    if (dateTo)   result = result.filter((s) => s.workDate <= dateTo);

    return [...result].sort((a, b) => {
      let cmp = 0;
      switch (sortKey) {
        case "workDate":
          cmp = a.workDate.localeCompare(b.workDate);
          break;
        case "shiftType":
          cmp = a.shiftType.id.localeCompare(b.shiftType.id);
          break;
        case "staffName":
          cmp = a.staff.fullName.localeCompare(b.staff.fullName);
          break;
        case "hasConflict":
          cmp = (a.hasConflict ? 1 : 0) - (b.hasConflict ? 1 : 0);
          break;
      }
      return sortDir === "asc" ? cmp : -cmp;
    });
  }, [schedules, search, filterType, filterStaff, filterConflict, dateFrom, dateTo, sortKey, sortDir]);

  const totalPages = Math.max(1, Math.ceil(filtered.length / PAGE_SIZE));
  const safePage = Math.min(page, totalPages);
  const pageData = filtered.slice((safePage - 1) * PAGE_SIZE, safePage * PAGE_SIZE);

  const handleSort = useCallback((key: SortKey) => {
    setSortKey((prev) => {
      if (prev === key) {
        setSortDir((d) => (d === "asc" ? "desc" : "asc"));
        return key;
      }
      setSortDir("asc");
      return key;
    });
  }, []);

  const SortIcon = ({ col }: { col: SortKey }) =>
    sortKey === col ? (
      <span className="material-symbols-outlined text-[12px]">{sortDir === "asc" ? "expand_less" : "expand_more"}</span>
    ) : (
      <span className="material-symbols-outlined text-[12px] opacity-0 group-hover:opacity-40">unfold_more</span>
    );

  const conflictCount = filtered.filter((s) => s.hasConflict).length;

  return (
    <div className="flex flex-col h-full">
      {/* ── Toolbar ─────────────────────────────────────────────────── */}
      <div className="px-4 py-3 border-b border-outline-variant bg-surface-container-low shrink-0 space-y-3">
        {/* Search row */}
        <div className="flex items-center gap-3">
          <div className="relative flex-1 max-w-sm">
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-[18px] text-on-surface-variant">search</span>
            <input
              id={`${uid}-search`}
              type="text"
              value={search}
              onChange={(e) => { setSearch(e.target.value); setPage(1); }}
              placeholder="Tìm theo tên nhân sự, loại ca, mã lịch..."
              className="w-full h-9 pl-9 pr-3 rounded-lg border border-outline-variant bg-surface text-label-md text-on-surface placeholder:text-on-surface-variant/50 outline-none transition-colors focus:border-primary focus:ring-2 focus:ring-primary/20"
            />
            {search && (
              <button
                type="button"
                onClick={() => { setSearch(""); setPage(1); }}
                className="absolute right-2 top-1/2 -translate-y-1/2 p-1 rounded hover:bg-surface-container-high"
                aria-label="Xóa tìm kiếm"
              >
                <span className="material-symbols-outlined text-[16px] text-on-surface-variant">close</span>
              </button>
            )}
          </div>
          <span className="text-label-sm text-on-surface-variant shrink-0">
            {filtered.length} kết quả
          </span>
          {conflictCount > 0 && (
            <span className="shrink-0 px-2 py-0.5 rounded-full bg-red-50 border border-red-200 text-error text-[11px] font-semibold">
              {conflictCount} xung đột
            </span>
          )}
        </div>

        {/* Filter row */}
        <div className="flex flex-wrap items-center gap-2">
          {/* Date range */}
          <div className="flex items-center gap-1.5">
            <label htmlFor={`${uid}-from`} className="text-label-sm text-on-surface-variant shrink-0">Từ:</label>
            <input
              id={`${uid}-from`}
              type="date"
              value={dateFrom}
              onChange={(e) => { setDateFrom(e.target.value); setPage(1); }}
              className="h-8 pl-2 pr-1 rounded-lg border border-outline-variant bg-surface text-label-sm text-on-surface outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 cursor-pointer"
            />
          </div>
          <div className="flex items-center gap-1.5">
            <label htmlFor={`${uid}-to`} className="text-label-sm text-on-surface-variant shrink-0">Đến:</label>
            <input
              id={`${uid}-to`}
              type="date"
              value={dateTo}
              onChange={(e) => { setDateTo(e.target.value); setPage(1); }}
              className="h-8 pl-2 pr-1 rounded-lg border border-outline-variant bg-surface text-label-sm text-on-surface outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 cursor-pointer"
            />
          </div>

          <div className="w-px h-5 bg-outline-variant mx-1 hidden sm:block" />

          {/* Shift type */}
          <select
            value={filterType}
            onChange={(e) => { setFilterType(e.target.value); setPage(1); }}
            className="h-8 pl-2 pr-7 appearance-none rounded-lg border border-outline-variant bg-surface text-label-sm text-on-surface outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 cursor-pointer"
            style={{ backgroundImage: "none" }}
            aria-label="Lọc theo loại ca"
          >
            <option value="all">Tất cả loại ca</option>
            <option value="L01">Trực 24/24</option>
            <option value="L02">Thông tầm</option>
            <option value="L03">Dịch vụ</option>
            <option value="L04">Chuyên gia</option>
          </select>

          {/* Staff */}
          <select
            value={filterStaff}
            onChange={(e) => { setFilterStaff(e.target.value); setPage(1); }}
            className="h-8 pl-2 pr-7 appearance-none rounded-lg border border-outline-variant bg-surface text-label-sm text-on-surface outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 cursor-pointer max-w-36"
            style={{ backgroundImage: "none" }}
            aria-label="Lọc theo nhân sự"
          >
            <option value="all">Tất cả nhân sự</option>
            {allStaff.map(([id, name]) => (
              <option key={id} value={id}>{name}</option>
            ))}
          </select>

          {/* Conflict */}
          <select
            value={filterConflict}
            onChange={(e) => { setFilterConflict(e.target.value as FilterConflict); setPage(1); }}
            className="h-8 pl-2 pr-7 appearance-none rounded-lg border border-outline-variant bg-surface text-label-sm text-on-surface outline-none focus:border-primary focus:ring-1 focus:ring-primary/20 cursor-pointer"
            style={{ backgroundImage: "none" }}
            aria-label="Lọc theo xung đột"
          >
            <option value="all">Tất cả trạng thái</option>
            <option value="conflict">Có xung đột</option>
            <option value="clean">Không xung đột</option>
          </select>

          {/* Reset */}
          {(search || filterType !== "all" || filterStaff !== "all" || filterConflict !== "all" || dateFrom || dateTo) && (
            <button
              type="button"
              onClick={() => { setSearch(""); setFilterType("all"); setFilterStaff("all"); setFilterConflict("all"); setDateFrom(""); setDateTo(""); setPage(1); }}
              className="h-8 px-3 rounded-lg border border-outline-variant bg-surface text-label-sm text-error hover:bg-red-50 transition-colors shrink-0"
            >
              Xóa lọc
            </button>
          )}
        </div>
      </div>

      {/* ── Table ────────────────────────────────────────────────────── */}
      <div className="flex-1 overflow-auto">
        <table className="w-full border-collapse text-label-sm">
          <thead className="sticky top-0 z-10 bg-surface-container-low shadow-[0_1px_0_0_var(--color-outline-variant)]">
            <tr>
              {([
                { key: "workDate",    label: "Ngày",         className: "w-24" },
                { key: null,          label: "Thứ",           className: "w-16" },
                { key: "shiftType",   label: "Loại ca",       className: "w-28" },
                { key: "staffName",   label: "Nhân sự",       className: "w-44" },
                { key: null,          label: "Xung đột",      className: "w-20" },
                { key: null,          label: "Ghi chú",       className: "" },
                { key: null,          label: "Hành động",     className: "w-28" },
              ] as const).map(({ key, label, className }) => (
                <th
                  key={label}
                  className={`px-3 py-2.5 text-left text-label-sm font-semibold text-on-surface-variant whitespace-nowrap border-b border-r border-outline-variant last:border-r-0 ${className} ${key ? "cursor-pointer hover:bg-surface-container-high group select-none" : ""}`}
                  onClick={() => key && handleSort(key as SortKey)}
                >
                  <div className="flex items-center gap-1">
                    {label}
                    {key && <SortIcon col={key as SortKey} />}
                  </div>
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {pageData.length === 0 ? (
              <tr>
                <td colSpan={7} className="text-center py-16 text-on-surface-variant">
                  <div className="flex flex-col items-center gap-3">
                    <span className="material-symbols-outlined text-[48px] opacity-40">search_off</span>
                    <p className="text-label-md">Không có lịch nào phù hợp</p>
                  </div>
                </td>
              </tr>
            ) : (
              pageData.map((s, idx) => {
                const tone = TONE[s.shiftType.id] ?? { bg: "bg-gray-50", text: "text-gray-700", dot: "bg-gray-500", label: s.shiftType.name };
                const dateObj = new Date(s.workDate + "T00:00:00");
                const dow = WEEKDAY_VN[dateObj.getDay()];

                return (
                  <tr
                    key={s.id}
                    className={`border-b border-outline-variant/40 hover:bg-surface-container-low transition-colors ${idx % 2 === 0 ? "bg-surface-container-lowest" : ""}`}
                  >
                    {/* Date */}
                    <td className="px-3 py-2.5 whitespace-nowrap border-r border-outline-variant/40">
                      <span className="text-label-sm font-medium text-on-surface">
                        {dateObj.toLocaleDateString("vi-VN")}
                      </span>
                    </td>

                    {/* Weekday */}
                    <td className="px-3 py-2.5 whitespace-nowrap border-r border-outline-variant/40">
                      <span className={`text-label-sm font-semibold ${dow === "CN" || dow === "T7" ? "text-red-500" : "text-on-surface"}`}>
                        {dow}
                      </span>
                    </td>

                    {/* Shift type */}
                    <td className="px-3 py-2.5 border-r border-outline-variant/40">
                      <div className="flex items-center gap-1.5">
                        <span className={`w-2 h-2 rounded-full shrink-0 ${tone.dot}`} />
                        <span className={`px-1.5 py-0.5 rounded text-label-sm font-medium ${tone.bg} ${tone.text}`}>
                          {tone.label}
                        </span>
                      </div>
                    </td>

                    {/* Staff */}
                    <td className="px-3 py-2.5 border-r border-outline-variant/40">
                      <div className="flex items-center gap-2">
                        <div className={`w-7 h-7 rounded-full shrink-0 flex items-center justify-center ${tone.bg}`}>
                          <span className={`text-[10px] font-bold ${tone.text}`}>
                            {s.staff.fullName.trim().split(/\s+/).slice(-1)[0]?.slice(0, 2).toUpperCase()}
                          </span>
                        </div>
                        <span className="text-label-sm text-on-surface font-medium truncate" title={s.staff.fullName}>
                          {s.staff.fullName}
                        </span>
                      </div>
                    </td>

                    {/* Conflict */}
                    <td className="px-3 py-2.5 border-r border-outline-variant/40">
                      {s.hasConflict ? (
                        <button
                          type="button"
                          onClick={() => onResolveConflict?.(s)}
                          className="flex items-center gap-1 px-1.5 py-0.5 rounded bg-red-50 border border-red-200 text-error text-label-sm font-semibold hover:bg-red-100 transition-colors"
                          title="Có xung đột - Click để xử lý"
                        >
                          <span className="material-symbols-outlined text-[12px]">warning</span>
                          Xung đột
                        </button>
                      ) : (
                        <div className="flex items-center gap-1 text-label-sm text-emerald-600">
                          <span className="material-symbols-outlined text-[14px]">check_circle</span>
                          OK
                        </div>
                      )}
                    </td>

                    {/* Notes */}
                    <td className="px-3 py-2.5 border-r border-outline-variant/40">
                      <span className="text-label-sm text-on-surface-variant truncate block max-w-xs" title={s.notes ?? ""}>
                        {s.notes ?? "—"}
                      </span>
                    </td>

                    {/* Actions */}
                    <td className="px-3 py-2.5">
                      <div className="flex items-center gap-1">
                        {canEdit && (
                          <>
                            <button
                              type="button"
                              onClick={() => onEdit?.(s)}
                              className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant hover:text-primary transition-colors"
                              title="Chỉnh sửa"
                            >
                              <span className="material-symbols-outlined text-[16px]">edit</span>
                            </button>
                            <button
                              type="button"
                              onClick={() => onDelete?.(s)}
                              className="p-1.5 rounded-lg hover:bg-red-50 text-on-surface-variant hover:text-error transition-colors"
                              title="Xóa"
                            >
                              <span className="material-symbols-outlined text-[16px]">delete</span>
                            </button>
                          </>
                        )}
                        {!canEdit && (
                          <span className="text-label-sm text-on-surface-variant/40">—</span>
                        )}
                      </div>
                    </td>
                  </tr>
                );
              })
            )}
          </tbody>
        </table>
      </div>

      {/* ── Pagination ─────────────────────────────────────────────────── */}
      {totalPages > 1 && (
        <div className="px-4 py-3 border-t border-outline-variant bg-surface-container-low flex items-center justify-between shrink-0">
          <span className="text-label-sm text-on-surface-variant">
            Hiển {(safePage - 1) * PAGE_SIZE + 1}–{Math.min(safePage * PAGE_SIZE, filtered.length)} / {filtered.length} lịch
          </span>
          <div className="flex items-center gap-1">
            <button
              type="button"
              onClick={() => setPage(1)}
              disabled={safePage === 1}
              className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
              aria-label="Trang đầu"
            >
              <span className="material-symbols-outlined text-[18px]">first_page</span>
            </button>
            <button
              type="button"
              onClick={() => setPage((p) => Math.max(1, p - 1))}
              disabled={safePage === 1}
              className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
              aria-label="Trang trước"
            >
              <span className="material-symbols-outlined text-[18px]">chevron_left</span>
            </button>
            {Array.from({ length: Math.min(totalPages, 7) }, (_, i) => {
              let p: number;
              if (totalPages <= 7) {
                p = i + 1;
              } else if (safePage <= 4) {
                p = i + 1;
              } else if (safePage >= totalPages - 3) {
                p = totalPages - 6 + i;
              } else {
                p = safePage - 3 + i;
              }
              return (
                <button
                  key={p}
                  type="button"
                  onClick={() => setPage(p)}
                  className={`w-8 h-8 rounded-lg text-label-sm font-medium transition-colors ${
                    safePage === p
                      ? "bg-primary text-on-primary"
                      : "hover:bg-surface-container-high text-on-surface"
                  }`}
                >
                  {p}
                </button>
              );
            })}
            <button
              type="button"
              onClick={() => setPage((p) => Math.min(totalPages, p + 1))}
              disabled={safePage === totalPages}
              className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
              aria-label="Trang sau"
            >
              <span className="material-symbols-outlined text-[18px]">chevron_right</span>
            </button>
            <button
              type="button"
              onClick={() => setPage(totalPages)}
              disabled={safePage === totalPages}
              className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant transition-colors disabled:opacity-30 disabled:cursor-not-allowed"
              aria-label="Trang cuối"
            >
              <span className="material-symbols-outlined text-[18px]">last_page</span>
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
