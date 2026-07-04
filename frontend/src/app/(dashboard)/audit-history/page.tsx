"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Button, IconButton } from "@/components/ui";
import { Skeleton } from "@/components/ui/Skeleton";
import { EmptyState } from "@/components/ui/EmptyState";
import { api } from "@/lib/api";
import { BackButton } from "@/components/ui/BackButton";
import { useToast } from "@/hooks/useToast";
import { getErrorMessage } from "@/lib/errors";
import { ConfirmDialog } from "@/components/ui";
import type { AuditHistory, AuditHistoryPage, AuditHistorySummary } from "@/types/api";

type ActionFilter = "" | "CREATE" | "UPDATE" | "DELETE";
type DateRange = "today" | "yesterday" | "7d" | "30d" | "custom";

// ─── Style constants ───────────────────────────────────────────────────────────

const ACTION_STYLE: Record<string, {
  label: string;
  icon: string;
  iconBg: string;
  chipBg: string;
  chipColor: string;
}> = {
  CREATE: {
    label: "Tạo mới",
    icon: "add_circle",
    iconBg: "bg-secondary-container text-secondary",
    chipBg: "bg-secondary-container text-secondary",
    chipColor: "text-secondary",
  },
  UPDATE: {
    label: "Cập nhật",
    icon: "edit",
    iconBg: "bg-primary-fixed text-primary",
    chipBg: "bg-primary-fixed text-primary",
    chipColor: "text-primary",
  },
  DELETE: {
    label: "Xóa",
    icon: "delete",
    iconBg: "bg-error-container text-error",
    chipBg: "bg-error-container text-error",
    chipColor: "text-error",
  },
};

function getAction(action: string) {
  return ACTION_STYLE[action] ?? {
    label: action,
    icon: "info",
    iconBg: "bg-surface-container-high text-on-surface-variant",
    chipBg: "bg-surface-container-high text-on-surface-variant",
    chipColor: "text-on-surface-variant",
  };
}

// ─── Helpers ──────────────────────────────────────────────────────────────────

function fmtTime(dateStr: string) {
  try {
    return new Date(dateStr).toLocaleTimeString("vi-VN", { hour: "2-digit", minute: "2-digit" });
  } catch { return dateStr; }
}

const VI_DAY_SHORT = ["CN", "T2", "T3", "T4", "T5", "T6", "T7"];

function fmtDateShort(dateKey: string) {
  const d = new Date(dateKey + "T12:00:00");
  return `${VI_DAY_SHORT[d.getDay()]}, ${d.toLocaleDateString("vi-VN")}`;
}

function getDateRange(range: DateRange): { from?: string; to?: string } {
  // Use local date (not UTC) so it matches the backend's Asia/Ho_Chi_Minh timezone
  // that audit_history.created_at is stored in.
  const now = new Date();
  const yyyy = now.getFullYear();
  const mm = String(now.getMonth() + 1).padStart(2, "0");
  const dd = String(now.getDate()).padStart(2, "0");
  const to = `${yyyy}-${mm}-${dd}`;
  switch (range) {
    case "today":    return { from: to, to };
    case "yesterday": {
      const y = new Date(now);
      y.setDate(y.getDate() - 1);
      const yy = y.getFullYear();
      const ym = String(y.getMonth() + 1).padStart(2, "0");
      const yd = String(y.getDate()).padStart(2, "0");
      return { from: `${yy}-${ym}-${yd}`, to: `${yy}-${ym}-${yd}` };
    }
    case "7d":  {
      const s = new Date(now);
      s.setDate(s.getDate() - 7);
      const sy = s.getFullYear();
      const sm = String(s.getMonth() + 1).padStart(2, "0");
      const sd = String(s.getDate()).padStart(2, "0");
      return { from: `${sy}-${sm}-${sd}`, to };
    }
    case "30d": {
      const s = new Date(now);
      s.setDate(s.getDate() - 30);
      const sy = s.getFullYear();
      const sm = String(s.getMonth() + 1).padStart(2, "0");
      const sd = String(s.getDate()).padStart(2, "0");
      return { from: `${sy}-${sm}-${sd}`, to };
    }
    default: return {};
  }
}

function subDateStr(days: number) {
  const d = new Date();
  d.setDate(d.getDate() - days);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
}

const todayStr = (() => {
  const d = new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  return `${yyyy}-${mm}-${dd}`;
})();

function isToday(dateKey: string) {
  const d = new Date();
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const todayLocal = `${yyyy}-${mm}-${dd}`;
  return dateKey === todayLocal;
}

function isYesterday(dateKey: string) {
  const d = new Date();
  d.setDate(d.getDate() - 1);
  const yyyy = d.getFullYear();
  const mm = String(d.getMonth() + 1).padStart(2, "0");
  const dd = String(d.getDate()).padStart(2, "0");
  const yesterdayLocal = `${yyyy}-${mm}-${dd}`;
  return dateKey === yesterdayLocal;
}

// ─── JSON utils ───────────────────────────────────────────────────────────────

const META_KEYS = new Set([
  "id", "Id", "ID",
  "createdAt", "created_at", "createAt", "create_at", "createDate", "createdDate",
  "updatedAt", "updated_at", "modifiedAt", "modified_at",
  "deletedAt", "deleted_at", "lastModified", "lastModifiedAt",
  "createdBy", "created_by", "updatedBy", "updated_by",
  "version", "uuid", "Uuid", "UID",
  "notificationType", "isRead", "readAt",
  "oldData", "newData",
]);

function isMetaKey(k: string): boolean {
  return META_KEYS.has(k) ||
    /^(id|_id|.*[Ii]d$|.*[Tt]imestamp$|.*[Dd]ate$|.*[Bb]y$|.*[Uu]ser|.*[Uu]serId|.*[Uu]ser_Id)/.test(k) ||
    /(content|message|description|details|metadata|payload|params|data)$/i.test(k);
}

function parseJson(raw?: string): Record<string, unknown> | null {
  if (!raw) return null;
  try {
    const p = JSON.parse(raw);
    return typeof p === "object" && p !== null ? p as Record<string, unknown> : null;
  } catch { return null; }
}

function prettyKey(k: string) {
  return k.replace(/_/g, " ").replace(/([a-z])([A-Z])/g, "$1 $2").replace(/^\w/, (c) => c.toUpperCase());
}

function fmtVal(v: unknown): string {
  if (v == null) return "—";
  if (typeof v === "boolean") return v ? "Có" : "Không";
  if (typeof v === "number") return v.toLocaleString("vi");
  if (typeof v === "object") {
    const e = Object.entries(v as Record<string, unknown>);
    if (!e.length) return "—";
    const n = e.find(([k]) => /(name|title|label)/i.test(k));
    return n ? fmtVal(n[1]) : `${e.length} trường`;
  }
  if (typeof v === "string") return v.length > 120 ? v.slice(0, 120) + "…" : v;
  return String(v);
}

// Pretty-print JSON (ensure multi-line), then syntax-highlight
function SyntaxHighlight({ json }: { json: string }) {
  let formatted = json;
  try {
    formatted = JSON.stringify(JSON.parse(json), null, 2);
  } catch { /* keep raw if invalid */ }
  const lines = formatted.split("\n");
  return (
    <div className="rounded bg-[#0d1117] text-[12px] overflow-x-auto">
      <table className="w-full" aria-label="Page Table">
        <tbody>
          {lines.map((line, i) => {
            const highlighted = line
              .replace(/("(?:[^"\\]|\\.)*")\s*:/g, '<span class="text-[#79c0ff]">$1</span>:')
              .replace(/:\s*("(?:[^"\\]|\\.)*")/g, ': <span class="text-[#a5d6ff]">$1</span>')
              .replace(/:\s*(true|false)/g, ': <span class="text-[#ff7b72]">$1</span>')
              .replace(/:\s*(null)/g, ': <span class="text-[#ff7b72]">$1</span>')
              .replace(/:\s*(-?\d+(?:\.\d+)?)/g, ': <span class="text-[#79c0ff]">$1</span>');
            return (
              <tr key={i} className="leading-5">
                <td className="pr-3 text-right text-[#484f58] select-none w-7 shrink-0 pl-2">{i + 1}</td>
                <td className="text-[#c9d1d9] whitespace-pre" dangerouslySetInnerHTML={{ __html: highlighted || "&nbsp;" }} />
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

// ─── JSON Diff Table ───────────────────────────────────────────────────────────

function JsonDiffTable({ oldJson, newJson }: { oldJson?: string; newJson?: string }) {
  const m1 = parseJson(oldJson ?? "") ?? {};
  const m2 = parseJson(newJson ?? "") ?? {};
  const allKeys = Array.from(new Set([...Object.keys(m1), ...Object.keys(m2)])).filter((k) => !isMetaKey(k));
  const changed = allKeys.filter((k) => JSON.stringify(m1[k]) !== JSON.stringify(m2[k]));
  const added = allKeys.filter((k) => !(k in m1) && k in m2);
  const removed = allKeys.filter((k) => k in m1 && !(k in m2));

  if (!changed.length && !added.length && !removed.length) {
    return (
      <div className="flex items-center gap-2 text-[13px] text-secondary py-3">
        <span className="material-symbols-outlined text-[16px]">check_circle</span>
        Không có thay đổi dữ liệu.
      </div>
    );
  }

  return (
    <div className="space-y-1.5">
      {changed.length > 0 && (
        <>
          <p className="text-[11px] font-semibold text-on-surface-variant uppercase tracking-wide mb-1.5">
            {changed.length} thay đổi
          </p>
          {changed.map((k) => (
            <div key={k} className="grid grid-cols-2 rounded-lg overflow-hidden border border-outline-variant text-[12px]">
              <div className="bg-surface-container-low border-r border-outline-variant px-3 py-2">
                <p className="text-[10px] text-error font-medium mb-0.5 leading-none uppercase tracking-wide">{prettyKey(k)}</p>
                <p className="text-on-surface font-medium leading-snug mt-0.5">{fmtVal(m1[k])}</p>
              </div>
              <div className="bg-surface-container-lowest px-3 py-2">
                <p className="text-[10px] text-secondary font-medium mb-0.5 leading-none uppercase tracking-wide">{prettyKey(k)}</p>
                <p className="text-on-surface font-medium leading-snug mt-0.5">{fmtVal(m2[k])}</p>
              </div>
            </div>
          ))}
        </>
      )}
      {added.length > 0 && (
        <>
          <p className="text-[11px] font-semibold text-secondary mt-3 mb-1.5 uppercase tracking-wide">{added.length} mới thêm</p>
          {added.map((k) => (
            <div key={k} className="flex items-center gap-3 px-3 py-2 rounded-lg bg-secondary-container border border-secondary/20 text-[12px]">
              <span className="material-symbols-outlined text-[14px] text-secondary shrink-0">add</span>
              <span className="text-secondary font-medium w-36 shrink-0">{prettyKey(k)}</span>
              <span className="text-on-surface font-medium">{fmtVal(m2[k])}</span>
            </div>
          ))}
        </>
      )}
      {removed.length > 0 && (
        <>
          <p className="text-[11px] font-semibold text-error mt-3 mb-1.5 uppercase tracking-wide">{removed.length} đã xóa</p>
          {removed.map((k) => (
            <div key={k} className="flex items-center gap-3 px-3 py-2 rounded-lg bg-error-container border border-error/20 text-[12px]">
              <span className="material-symbols-outlined text-[14px] text-error shrink-0">remove</span>
              <span className="text-error font-medium w-36 shrink-0">{prettyKey(k)}</span>
              <span className="text-on-surface font-medium">{fmtVal(m1[k])}</span>
            </div>
          ))}
        </>
      )}
    </div>
  );
}

// ─── Detail Modal ──────────────────────────────────────────────────────────────

type DetailTab = "diff" | "old" | "new" | "raw";

function DetailModal({ record, onClose }: { record: AuditHistory; onClose: () => void }) {
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
              {record.userName ?? (record.userId != null && record.userId > 0 ? `#${record.userId}` : <span className="text-outline italic">—</span>)}
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

// ─── Main page ─────────────────────────────────────────────────────────────────

export default function AuditHistoryPage() {
  const [pageData, setPageData] = useState<AuditHistoryPage | null>(null);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  // KPI summary is fetched separately from /audit-history/summary so it reflects
  // the entire DB (or the active date range), not just the current page slice.
  const [summaryData, setSummaryData] = useState<AuditHistorySummary | null>(null);
  const [search, setSearch] = useState("");
  const [module, setModule] = useState("");
  const [action, setAction] = useState<ActionFilter>("");
  const [dateRange, setDateRange] = useState<DateRange>("30d");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(50);
  const [selected, setSelected] = useState<AuditHistory | null>(null);
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());
  const searchRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const toast = useToast();

  // selected items for bulk delete
  const [selectedIds, setSelectedIds] = useState<Set<number>>(new Set());
  const [deleteDialogType, setDeleteDialogType] = useState<"single" | "bulk" | "date-range" | "all" | null>(null);
  const [deleting, setDeleting] = useState(false);
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);
  const [deleteTargetName, setDeleteTargetName] = useState("");
  const [deleteOpen, setDeleteOpen] = useState(false);
  const [deleteDateFrom, setDeleteDateFrom] = useState("");
  const [deleteDateTo, setDeleteDateTo] = useState("");
  // Typed-confirm state for "Xóa tất cả" — user must type this exact string to unlock the button.
  const [deleteAllConfirmText, setDeleteAllConfirmText] = useState("");
  const DELETE_ALL_CONFIRM_PHRASE = "XÓA TẤT CẢ";

  const fetchData = useCallback(async (pageNum: number, size: number, refresh = false) => {
    if (refresh) setRefreshing(true); else setLoading(true);
    try {
      const data = await api.getAuditHistory(pageNum, size);
      setPageData(data ?? null);
    } catch (err) {
      toast.error(getErrorMessage(err, "Không thể tải nhật ký."));
      setPageData(null);
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  }, [toast]);

  // Computed date-range — declared BEFORE fetchSummary so the callback can
  // depend on df.from / df.to without tripping React's "used before defined"
  // hook order check.
  const df = useMemo(() =>
    dateRange === "custom" ? { from: dateFrom, to: dateTo } : getDateRange(dateRange),
    [dateRange, dateFrom, dateTo]);

  // KPI summary is independent of pagination. The summary must mirror every
  // filter on the page (date range + module + action + search) so the tiles
  // stay accurate as the user narrows the result set.
  //
  // Search input is debounced via a ref-held timer so each keystroke does not
  // fire a separate request — only the value after ~300ms of idle is fetched.
  const summarySearchRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const [summarySearch, setSummarySearch] = useState("");

  // Push the current `search` text into `summarySearch` with a debounce so
  // typing "Nguyễn" only fires one summary request, not seven.
  useEffect(() => {
    if (summarySearchRef.current) clearTimeout(summarySearchRef.current);
    summarySearchRef.current = setTimeout(() => setSummarySearch(search), 300);
    return () => {
      if (summarySearchRef.current) clearTimeout(summarySearchRef.current);
    };
  }, [search]);

  const fetchSummary = useCallback(async () => {
    try {
      const params: {
        startDate?: string;
        endDate?: string;
        module?: string;
        action?: string;
        search?: string;
      } = {};

      if (df.from && df.to) {
        // Backend treats endDate as exclusive — pass the day after so the
        // inclusive end date still shows up in the totals.
        const [y, m, d] = df.to.split("-").map(Number);
        const endDate = new Date(y!, (m ?? 1) - 1, (d ?? 1) + 1);
        const endIso = `${endDate.getFullYear()}-${String(endDate.getMonth() + 1).padStart(2, "0")}-${String(endDate.getDate()).padStart(2, "0")}`;
        params.startDate = `${df.from}T00:00:00`;
        params.endDate = `${endIso}T00:00:00`;
      }
      if (module.trim()) params.module = module.trim();
      if (action)        params.action = action;
      if (summarySearch.trim()) params.search = summarySearch.trim();

      const data = await api.getAuditHistorySummaryFiltered(params);
      setSummaryData(data);
    } catch (err) {
      // Don't toast — KPI is non-critical. Just zero out so the UI doesn't lie.
      setSummaryData(null);
    }
  }, [df.from, df.to, module, action, summarySearch]);

  useEffect(() => {
    fetchData(page, pageSize);
  }, [page, pageSize, fetchData]);

useEffect(() => {
    fetchSummary();
  }, [fetchSummary]);

  const records = pageData?.content ?? [];
  const filtered = useMemo(() => {
    const kw = search.trim().toLowerCase();
    return records.filter((r) => {
      if (kw) {
        const match = (r.userName ?? "").toLowerCase().includes(kw)
          || String(r.userId ?? "").includes(kw)
          || r.tableName.toLowerCase().includes(kw)
          || r.action.toLowerCase().includes(kw)
          || String(r.recordId).includes(kw);
        if (!match) return false;
      }
      if (module && r.tableName !== module) return false;
      if (action && r.action !== action) return false;
      const dk = r.createdAt.split("T")[0];
      if (df.from && dk < df.from) return false;
      if (df.to && dk > df.to) return false;
      return true;
    });
  }, [records, search, module, action, df]);

  const grouped = useMemo(() => {
    const g: Record<string, AuditHistory[]> = {};
    for (const r of filtered) {
      const dk = r.createdAt.split("T")[0];
      (g[dk] ??= []).push(r);
    }
    return Object.entries(g).sort(([a], [b]) => b.localeCompare(a));
  }, [filtered]);

  // Build a per-page date-grouped view from the *current* page only.
  // `records` is already paginated by the backend (page * size).
  // No need to slice again — backend returns exactly `size` items for this page.
  const pagedGroups = useMemo(() => {
    const dateMap = new Map<string, AuditHistory[]>();
    for (const r of filtered) {
      const dk = r.createdAt.split("T")[0];
      if (!dateMap.has(dk)) dateMap.set(dk, []);
      dateMap.get(dk)!.push(r);
    }
    return Array.from(dateMap.entries())
      .sort(([a], [b]) => b.localeCompare(a));
  }, [filtered]);

  const totalPages = useMemo(() => pageData ? Math.max(1, pageData.totalPages) : 1, [pageData?.totalPages]);

  // Summary reflects the entire DB (or active date range), not the current page slice.
  // Values come from the dedicated /audit-history/summary endpoint so that the
  // CREATE / UPDATE / DELETE totals stay accurate across all 44 pages.
  const summary = useMemo(() => ({
    total:  summaryData?.total ?? 0,
    create: summaryData?.create ?? 0,
    update: summaryData?.update ?? 0,
    delete: summaryData?.delete ?? 0,
  }), [summaryData]);

  const modules = useMemo(() => Array.from(new Set(records.map((r) => r.tableName))), [records]);
  const hasFilters = !!(search || module || action || dateRange !== "30d");

  function clearFilters() {
    setSearch(""); setModule(""); setAction(""); setDateRange("30d");
    setDateFrom(""); setDateTo(""); setPage(0);
  }

  function onSearch(val: string) {
    setSearch(val);
    if (searchRef.current) clearTimeout(searchRef.current);
    searchRef.current = setTimeout(() => setPage(0), 300);
  }

  function toggleGroup(dateKey: string) {
    setCollapsedGroups((prev) => {
      const next = new Set(prev);
      if (next.has(dateKey)) next.delete(dateKey); else next.add(dateKey);
      return next;
    });
  }

  function exportJSON() {
    const b = new Blob([JSON.stringify(filtered, null, 2)], { type: "application/json" });
    const u = URL.createObjectURL(b);
    const a = document.createElement("a"); a.href = u; a.download = `audit-${new Date().toISOString().split("T")[0]}.json`; a.click();
    URL.revokeObjectURL(u);
  }

  function requestDelete(id: number, name: string) {
    setDeleteTargetId(id);
    setDeleteTargetName(name);
    setDeleteDialogType("single");
  }

  async function handleConfirmDelete() {
    if (!deleteDialogType || deleting) return;
    setDeleting(true);
    try {
      if (deleteDialogType === "single" && deleteTargetId !== null) {
        await api.deleteAuditHistory(deleteTargetId);
        toast.success(`Đã xóa bản ghi "${deleteTargetName}".`);
        setDeleteDialogType(null);
        setDeleteTargetId(null);
        await fetchData(page, pageSize);
      } else if (deleteDialogType === "bulk") {
        const ids = Array.from(selectedIds);
        const count = await api.deleteMultipleAuditHistory(ids);
        toast.success(`Đã xóa ${count} bản ghi.`);
        setDeleteDialogType(null);
        setSelectedIds(new Set());
        await fetchData(page, pageSize);
      } else if (deleteDialogType === "date-range") {
        const count = await api.deleteAuditHistoryByDateRange(deleteDateFrom, deleteDateTo);
        toast.success(`Đã xóa ${count} bản ghi nhật ký.`);
        setDeleteDialogType(null);
        setDeleteDateFrom("");
        setDeleteDateTo("");
        await fetchData(page, pageSize);
      } else if (deleteDialogType === "all") {
        const count = await api.deleteAllAuditHistory();
        toast.success(`Đã xóa toàn bộ ${count} bản ghi nhật ký.`);
        setDeleteDialogType(null);
        setDeleteAllConfirmText("");
        setSelectedIds(new Set());
        await fetchData(page, pageSize);
        await fetchSummary();
      }
    } catch (err) {
      toast.error(getErrorMessage(err, "Lỗi xóa nhật ký."));
    } finally {
      setDeleting(false);
    }
  }

  function toggleSelect(id: number) {
    setSelectedIds((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id); else next.add(id);
      return next;
    });
  }

  function selectAll() {
    const allIds = (pageData?.content ?? []).map((r) => r.id);
    setSelectedIds(new Set(allIds));
  }

  function clearSelection() {
    setSelectedIds(new Set());
  }

  useEffect(() => { setPage(0); }, [search, module, action, dateRange, dateFrom, dateTo]);

  const DATE_OPTS: Array<{ v: DateRange; l: string }> = [
    { v: "today",     l: "Hôm nay" },
    { v: "yesterday", l: "Hôm qua" },
    { v: "7d",        l: "7 ngày" },
    { v: "30d",       l: "30 ngày" },
    { v: "custom",    l: "Tùy chỉnh" },
  ];

  return (
    <>
      <ConfirmDialog
        open={deleteDialogType === "bulk" || deleteDialogType === "single"}
        onClose={() => setDeleteDialogType(null)}
        onConfirm={handleConfirmDelete}
        title={
          deleteDialogType === "bulk"
            ? `Xóa ${selectedIds.size} bản ghi?`
            : "Xóa bản ghi nhật ký?"
        }
        description={
          deleteDialogType === "bulk"
            ? `Bạn có chắc muốn xóa ${selectedIds.size} bản ghi nhật ký? Hành động này không thể hoàn tác.`
            : `Xóa bản ghi nhật ký "${deleteTargetName}"? Hành động này không thể hoàn tác.`
        }
        confirmLabel="Xóa"
        cancelLabel="Hủy"
        variant="danger"
        loading={deleting}
      />

      {selected && <DetailModal record={selected} onClose={() => setSelected(null)} />}

      {/* Custom date range picker modal */}
      {deleteDialogType === "date-range" && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm" onClick={(e) => { if (e.target === e.currentTarget) { setDeleteDialogType(null); setDeleteDateFrom(""); setDeleteDateTo(""); } }}>
          <div className="bg-surface-container-lowest border border-outline-variant rounded-2xl shadow-2xl w-full max-w-sm mx-4 animate-scale-in">
            <div className="flex items-center justify-between px-5 pt-5 pb-4 border-b border-outline-variant">
              <h2 className="text-title-lg font-semibold text-on-surface">Tùy chỉnh ngày xóa</h2>
              <IconButton
              label="Đóng"
              variant="ghost"
              size="sm"
              onClick={() => { setDeleteDialogType(null); setDeleteDateFrom(""); setDeleteDateTo(""); }}
              className="ml-auto text-on-surface-variant"
            >
              <span className="material-symbols-outlined text-[20px]" aria-hidden="true">close</span>
            </IconButton>
            </div>
            <div className="px-5 py-4 flex flex-col gap-3">
              <p className="text-body-sm text-on-surface-variant">Chọn ngày bắt đầu và kết thúc.</p>
              <div className="grid grid-cols-2 gap-3">
                <div className="flex flex-col gap-1.5">
                  <label className="text-[12px] font-semibold text-on-surface-variant" htmlFor="del-from">Từ ngày</label>
                  <input id="del-from" type="date" className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all" value={deleteDateFrom} onChange={(e) => setDeleteDateFrom(e.target.value)} />
                </div>
                <div className="flex flex-col gap-1.5">
                  <label className="text-[12px] font-semibold text-on-surface-variant" htmlFor="del-to">Đến ngày</label>
                  <input id="del-to" type="date" className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-primary/20 focus:border-primary transition-all" value={deleteDateTo} onChange={(e) => setDeleteDateTo(e.target.value)} />
                </div>
              </div>
              <div className="flex gap-2 pt-1">
                <Button
                  variant="secondary"
                  size="md"
                  fullWidth
                  onClick={() => { setDeleteDialogType(null); setDeleteDateFrom(""); setDeleteDateTo(""); }}
                >
                  Hủy
                </Button>
                <Button
                  variant="danger"
                  size="md"
                  fullWidth
                  disabled={!deleteDateFrom || !deleteDateTo || deleteDateFrom > deleteDateTo || deleting}
                  loading={deleting}
                  onClick={async () => {
                    if (!deleteDateFrom || !deleteDateTo || deleteDateFrom > deleteDateTo || deleting) return;
                    setDeleting(true);
                    try {
                      const count = await api.deleteAuditHistoryByDateRange(deleteDateFrom, deleteDateTo);
                      toast.success(`Đã xóa ${count} bản ghi.`);
                      setDeleteDialogType(null);
                      setDeleteDateFrom("");
                      setDeleteDateTo("");
                      await fetchData(page, pageSize);
                    } catch (err) {
                      toast.error(getErrorMessage(err, "Lỗi xóa."));
                    } finally {
                      setDeleting(false);
                    }
                  }}
                >
                  Xóa
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}

      {/* ── "Xóa tất cả" — typed-confirm modal ───────────────────────────────────────
          User must type the exact phrase to unlock the destructive action.
          Prevents accidental clicks and fat-finger confirmations. */}
      {deleteDialogType === "all" && (
        <div
          className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 backdrop-blur-sm"
          role="dialog" aria-modal="true" aria-labelledby="delete-all-title"
          onClick={(e) => {
            if (e.target === e.currentTarget && !deleting) {
              setDeleteDialogType(null);
              setDeleteAllConfirmText("");
            }
          }}
        >
          <div className="bg-surface-container-lowest border border-error/40 rounded-2xl shadow-2xl w-full max-w-md mx-4 animate-scale-in">
            <div className="flex items-start gap-3 px-5 pt-5 pb-4 border-b border-outline-variant">
              <div className="w-10 h-10 rounded-full bg-error-container flex items-center justify-center shrink-0">
                <span className="material-symbols-outlined text-error" style={{ fontVariationSettings: "'FILL' 1" }}>warning</span>
              </div>
              <div className="flex-1">
                <h2 id="delete-all-title" className="text-title-lg font-semibold text-on-surface">Xóa toàn bộ nhật ký?</h2>
                <p className="text-body-sm text-on-surface-variant mt-1">
                  Hành động này sẽ xóa vĩnh viễn{" "}
                  <strong className="font-semibold text-error tabular-nums">
                    {summary.total.toLocaleString("vi")}
                  </strong>{" "}
                  bản ghi trong toàn bộ bảng audit_history. Không thể hoàn tác.
                </p>
              </div>
              <IconButton
                label="Đóng"
                variant="ghost"
                size="sm"
                disabled={deleting}
                onClick={() => { if (!deleting) { setDeleteDialogType(null); setDeleteAllConfirmText(""); } }}
                className="shrink-0 text-on-surface-variant"
              >
                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">close</span>
              </IconButton>
            </div>
            <div className="px-5 py-4 flex flex-col gap-3">
              <div className="bg-error-container border border-error/20 rounded-lg p-3 flex items-start gap-2">
                <span className="material-symbols-outlined text-error text-[18px] mt-0.5">info</span>
                <p className="text-[13px] text-on-error-container leading-snug">
                  Để xác nhận, hãy gõ chính xác cụm từ{" "}
                  <code className="px-1.5 py-0.5 rounded bg-error/15 text-error font-mono font-bold text-[12px]">
                    {DELETE_ALL_CONFIRM_PHRASE}
                  </code>{" "}
                  vào ô bên dưới.
                </p>
              </div>

              <div className="flex flex-col gap-1.5">
                <label htmlFor="delete-all-confirm" className="text-[12px] font-semibold text-on-surface-variant">
                  Xác nhận xóa
                </label>
                <input
                  id="delete-all-confirm"
                  type="text"
                  autoComplete="off"
                  spellCheck={false}
                  value={deleteAllConfirmText}
                  onChange={(e) => setDeleteAllConfirmText(e.target.value)}
                  placeholder={DELETE_ALL_CONFIRM_PHRASE}
                  className="w-full h-10 px-3 rounded-lg border border-outline-variant bg-surface text-body-sm text-on-surface focus:outline-none focus:ring-2 focus:ring-error/30 focus:border-error transition-all font-mono"
                  disabled={deleting}
                />
                {deleteAllConfirmText && deleteAllConfirmText !== DELETE_ALL_CONFIRM_PHRASE && (
                  <p className="text-[11px] text-error" role="alert">
                    Cụm từ chưa khớp. Hãy gõ đúng: {DELETE_ALL_CONFIRM_PHRASE}
                  </p>
                )}
              </div>

              <div className="flex gap-2 pt-1">
                <Button
                  variant="secondary"
                  size="md"
                  fullWidth
                  disabled={deleting}
                  onClick={() => { setDeleteDialogType(null); setDeleteAllConfirmText(""); }}
                >
                  Hủy
                </Button>
                <Button
                  variant="danger"
                  size="md"
                  fullWidth
                  disabled={deleting || deleteAllConfirmText !== DELETE_ALL_CONFIRM_PHRASE || summary.total === 0}
                  loading={deleting}
                  onClick={() => { if (deleteAllConfirmText === DELETE_ALL_CONFIRM_PHRASE) handleConfirmDelete(); }}
                >
                  {deleting ? "Đang xóa…" : `Xóa vĩnh viễn ${summary.total.toLocaleString("vi")} bản ghi`}
                </Button>
              </div>
            </div>
          </div>
        </div>
      )}

      <div className="flex flex-col gap-3 pb-6">
        <BackButton href="/dashboard" variant="full" label="Quay lại" className="mb-2" />

        {/* KPI Cards */}
        <section className="grid grid-cols-2 gap-3 lg:grid-cols-4">
          {([
            { l: "Tổng sự kiện", v: summary.total, ic: "history", bg: "bg-surface-container-low" },
            { l: "Tạo mới",      v: summary.create,  ic: "add_circle", bg: "bg-secondary-container" },
            { l: "Cập nhật",     v: summary.update,  ic: "edit",       bg: "bg-primary-fixed" },
            { l: "Xóa",          v: summary.delete,   ic: "delete",     bg: "bg-error-container" },
          ] as const).map((s) => (
            <div
              className="group relative flex items-center gap-3 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm transition-all duration-200 hover:bg-surface-container-low hover:shadow-md"
              key={s.l}
            >
              <div className={`flex h-10 w-10 shrink-0 items-center justify-center rounded-xl ${s.bg} transition-transform duration-200 group-hover:scale-105`}>
                <span className={`material-symbols-outlined text-[20px] ${
                  s.ic === "add_circle" ? "text-secondary" :
                  s.ic === "edit" ? "text-primary" :
                  s.ic === "delete" ? "text-error" : "text-on-surface-variant"
                }`}>{s.ic}</span>
              </div>
              <div className="min-w-0">
                <p className="text-label-sm text-on-surface-variant leading-none">{s.l}</p>
                <p className="text-headline-lg font-bold text-on-surface leading-none mt-1">
                  {loading ? "—" : s.v.toLocaleString("vi")}
                </p>
              </div>
            </div>
          ))}
        </section>

        {/* ── TOOLBAR ROW 1: Search + Action chips + Count + Actions ── */}
        <section className="flex items-center gap-2 flex-wrap rounded-xl border border-outline-variant bg-surface-container-lowest px-3 py-2.5 shadow-sm">

          {/* Search */}
          <div className="relative shrink-0" style={{ minWidth: 200, width: 240, maxWidth: "100%" }}>
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[16px]">search</span>
            <input
              autoComplete="off"
              className="w-full rounded-lg border border-outline-variant bg-surface h-9 pl-9 pr-8 text-[13px] text-on-surface placeholder:text-outline focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all"
              placeholder="Người, module, ID…"
              value={search}
              onChange={(e) => onSearch(e.target.value)}
            />
            {search && (
              <IconButton
                  label="Xóa tìm kiếm"
                  variant="ghost"
                  size="sm"
                  onClick={() => onSearch("")}
                  className="absolute right-2 top-1/2 -translate-y-1/2 text-outline"
                >
                  <span className="material-symbols-outlined text-[12px]" aria-hidden="true">close</span>
                </IconButton>
            )}
          </div>

          {/* Action filter chips */}
          <div className="flex items-center gap-1 shrink-0">
            <button
              className={`rounded-full px-3 py-1 text-[12px] font-semibold transition-all shrink-0 ${
                action === "" ? "bg-primary text-on-primary shadow-sm" : "bg-surface text-on-surface-variant border border-outline-variant hover:bg-surface-container-low"
              }`}
              onClick={() => setAction("")} type="button"
            >
              Tất cả
            </button>
            {(["CREATE", "UPDATE", "DELETE"] as const).map((a) => {
              const st = getAction(a);
              return (
                <button
                  key={a}
                  className={`rounded-full px-3 py-1 text-[12px] font-semibold transition-all shrink-0 ${
                    action === a ? `${st.chipBg} shadow-sm` : "bg-surface text-on-surface-variant border border-outline-variant hover:bg-surface-container-low"
                  }`}
                  onClick={() => setAction(action === a ? "" : a)} type="button"
                >
                  {st.label}
                </button>
              );
            })}
          </div>

          <div className="flex-1 min-w-0" />

          <span className="text-[12px] text-on-surface-variant shrink-0 tabular-nums">
            {loading ? "…" : filtered.length.toLocaleString("vi") + " kết quả"}
          </span>

          <div className="w-px h-5 bg-outline-variant shrink-0" />

          <IconButton
            label={refreshing ? "Đang làm mới" : "Làm mới"}
            variant="secondary"
            size="sm"
            disabled={refreshing}
            onClick={() => fetchData(page, pageSize, true)}
            className="shrink-0 text-on-surface-variant"
          >
            <span className={`material-symbols-outlined text-[16px] ${refreshing ? "animate-spin" : ""}`} aria-hidden="true">sync</span>
          </IconButton>

          <Button
            variant="secondary"
            size="sm"
            onClick={exportJSON}
            icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">download</span>}
          >
            Xuất
          </Button>

          {selectedIds.size > 0 && (
            <>
              <div className="w-px h-5 bg-outline-variant shrink-0" />
              <span className="text-[12px] text-primary font-semibold shrink-0">
                {selectedIds.size} đã chọn
              </span>
              <Button
                variant="danger"
                size="sm"
                onClick={() => setDeleteDialogType("bulk")}
                icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">delete</span>}
              >
                Xóa ({selectedIds.size})
              </Button>
              <Button
                variant="ghost"
                size="sm"
                onClick={clearSelection}
                icon={<span className="material-symbols-outlined text-[13px]" aria-hidden="true">clear</span>}
                className="text-on-surface-variant"
              >
                Bỏ chọn
              </Button>
            </>
          )}

          {hasFilters && (
            <Button
                variant="ghost"
                size="sm"
                onClick={clearFilters}
                icon={<span className="material-symbols-outlined text-[13px]" aria-hidden="true">clear</span>}
                className="text-primary hover:bg-primary-fixed"
              >
                Xóa
              </Button>
          )}

          {selectedIds.size === 0 && (
            <>
              <div className="w-px h-5 bg-outline-variant shrink-0" />
              <div className="relative shrink-0">
                <Button
                  variant="danger"
                  size="sm"
                  onClick={() => setDeleteOpen((v) => !v)}
                  icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">delete_sweep</span>}
                  iconPosition="left"
                >
                  Xóa
                  <span className="material-symbols-outlined text-[12px] ml-0.5" aria-hidden="true">expand_more</span>
                </Button>

                {deleteOpen && (
                  <>
                    <div className="fixed inset-0 z-40" onClick={() => setDeleteOpen(false)} />
                    <div
                      className="absolute top-full mt-1 z-50 bg-surface-container-lowest border border-outline-variant rounded-xl shadow-2xl w-64 animate-scale-in overflow-hidden"
                      style={{ right: 0 }}
                    >
                      <div className="px-3 py-2 border-b border-outline-variant">
                        <p className="text-[11px] font-semibold text-on-surface-variant uppercase tracking-wide">Xóa nhật ký</p>
                      </div>
                      <div className="p-1.5 flex flex-col gap-0.5">
                        {([
                          { v: "today", l: "Hôm nay", icon: "today" },
                          { v: "7d", l: "7 ngày qua", icon: "view_week" },
                          { v: "30d", l: "30 ngày qua", icon: "calendar_month" },
                          { v: "custom", l: "Tùy chỉnh…", icon: "edit_calendar" },
                        ] as const).map((p) => (
                          <button
                            key={p.v}
                            type="button"
                            role="menuitem"
                            className="flex items-center gap-2 w-full rounded-lg px-2.5 py-2 text-[13px] font-medium text-on-surface hover:bg-surface-container-low transition-colors text-left"
                            onClick={() => {
                              if (p.v === "custom") {
                                setDeleteOpen(false);
                                setDeleteDialogType("date-range");
                                return;
                              }
                              const ranges: Record<string, { from: string; to: string }> = {
                                today: { from: todayStr, to: todayStr },
                                "7d": { from: subDateStr(6), to: todayStr },
                                "30d": { from: subDateStr(29), to: todayStr },
                              };
                              const { from, to } = ranges[p.v];
                              setDeleteDateFrom(from);
                              setDeleteDateTo(to);
                              setDeleteDialogType("date-range");
                              setDeleteOpen(false);
                            }}
                          >
                            <span className="material-symbols-outlined text-[18px] text-on-surface-variant" aria-hidden="true">{p.icon}</span>
                            {p.l}
                          </button>
                        ))}

                        {/* Separator + dangerous "Xóa tất cả" action — typed-confirm required */}
                        <div className="my-1 h-px bg-outline-variant" role="separator" />
                        <button
                          type="button"
                          role="menuitem"
                          className="flex items-center gap-2 w-full rounded-lg px-2.5 py-2 text-[13px] font-semibold text-error hover:bg-error-container/40 transition-colors text-left disabled:opacity-40 disabled:cursor-not-allowed"
                          disabled={summary.total === 0}
                          onClick={() => {
                            setDeleteOpen(false);
                            setDeleteAllConfirmText("");
                            setDeleteDialogType("all");
                          }}
                        >
                          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">delete_forever</span>
                          Xóa tất cả
                          <span className="ml-auto text-[11px] font-medium text-on-surface-variant tabular-nums">
                            {summary.total.toLocaleString("vi")}
                          </span>
                        </button>
                      </div>
                    </div>
                  </>
                )}
              </div>
            </>
          )}
        </section>

        {/* ── TOOLBAR ROW 2: Module + Date pills + Custom dates ── */}
        <section className="flex items-center gap-2 flex-wrap rounded-xl border border-outline-variant bg-surface-container-lowest px-3 py-2.5 shadow-sm">

          {/* Module */}
          <label htmlFor="audit-module-filter" className="text-[12px] font-medium text-on-surface-variant shrink-0">
            Module:
          </label>
          <select
            id="audit-module-filter"
            className="appearance-none rounded-lg border border-outline-variant bg-surface px-2.5 h-9 text-[12px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer pr-7 shrink-0 min-w-[140px]"
            value={module}
            onChange={(e) => { setModule(e.target.value); setPage(0); }}
          >
            <option value="">Tất cả module</option>
            {modules.map((m) => <option key={m} value={m}>{m}</option>)}
          </select>

          <div className="w-px h-5 bg-outline-variant shrink-0" />

          {/* Date pills */}
          <div className="flex items-center gap-1 shrink-0">
            {DATE_OPTS.map((o) => (
              <button
                key={o.v}
                className={`rounded-full px-3 py-1 text-[12px] font-semibold transition-all shrink-0 ${
                  dateRange === o.v
                    ? "bg-primary text-on-primary shadow-sm"
                    : "bg-surface text-on-surface-variant border border-outline-variant hover:bg-surface-container-low"
                }`}
                onClick={() => setDateRange(o.v)} type="button"
              >
                {o.l}
              </button>
            ))}
          </div>

          {dateRange === "custom" && (
            <div className="flex items-center gap-1.5 shrink-0">
              <label htmlFor="audit-date-from" className="sr-only">Từ ngày</label>
              <input
                id="audit-date-from"
                className="rounded-lg border border-outline-variant bg-surface px-2.5 h-9 text-[12px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                type="date" value={dateFrom} onChange={(e) => { setDateFrom(e.target.value); setPage(0); }}
              />
              <span className="text-[12px] text-outline">—</span>
              <label htmlFor="audit-date-to" className="sr-only">Đến ngày</label>
              <input
                id="audit-date-to"
                className="rounded-lg border border-outline-variant bg-surface px-2.5 h-9 text-[12px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                type="date" value={dateTo} onChange={(e) => { setDateTo(e.target.value); setPage(0); }}
              />
            </div>
          )}
        </section>

        {/* ── Activity Stream ── */}
        <section className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">

          {/* Column header */}
          <div className="hidden md:grid gap-3 px-4 py-2.5 bg-surface-container-low border-b border-outline-variant shrink-0"
            style={{ gridTemplateColumns: "36px 36px 1fr auto auto" }}>
            {/* Checkbox select all */}
            <div className="flex items-center justify-center">
              <input
                type="checkbox"
                className="h-4 w-4 rounded border-outline-variant accent-primary cursor-pointer"
                checked={selectedIds.size > 0 && selectedIds.size === (pageData?.content ?? []).length}
                onChange={() => selectedIds.size > 0 ? clearSelection() : selectAll()}
                aria-label="Chọn tất cả"
              />
            </div>
            <span className="text-[11px] font-semibold text-on-surface-variant uppercase tracking-wide text-center">HĐ</span>
            <span className="text-[11px] font-semibold text-on-surface-variant uppercase tracking-wide">Chi tiết sự kiện</span>
            <span className="text-[11px] font-semibold text-on-surface-variant uppercase tracking-wide text-right">Giờ</span>
            {/* Bulk delete */}
            <div className="w-20" />
          </div>

          {loading ? (
            <div className="divide-y divide-outline-variant">
              {Array.from({ length: 6 }).map((_, i) => (
                <div key={i} className="flex items-start gap-3 px-4 py-3">
                  <Skeleton className="h-7 w-7 rounded-lg shrink-0" />
                  <div className="flex flex-col min-w-0 flex-1 gap-2">
                    <div className="flex items-center gap-2">
                      <Skeleton className="h-3 w-16 rounded" />
                      <Skeleton className="h-3 w-24 rounded" />
                      <Skeleton className="h-3 w-12 rounded" />
                    </div>
                    <Skeleton className="h-3 w-full rounded" />
                  </div>
                  <Skeleton className="h-3 w-12 rounded shrink-0" />
                </div>
              ))}
            </div>
          ) : filtered.length === 0 ? (
            <EmptyState
              icon="history"
              title={hasFilters ? "Không có kết quả phù hợp" : "Chưa có nhật ký nào"}
              action={
                hasFilters ? (
                  <button
                    className="inline-flex items-center gap-1.5 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-1.5 text-[12px] font-medium text-on-surface shadow-sm transition-colors hover:bg-surface-container-low"
                    onClick={clearFilters} type="button"
                  >
                    <span className="material-symbols-outlined text-[14px]">clear</span>
                    Xóa bộ lọc
                  </button>
                ) : undefined
              }
            />
          ) : (
            <div>
              {pagedGroups.map(([dateKey, dayRecords]) => {
                const collapsed = collapsedGroups.has(dateKey);
                const today = isToday(dateKey);
                const yesterday = isYesterday(dateKey);

                return (
                  <div key={dateKey}>
                    {/* Date group header */}
                    <div
                      className={`flex items-center gap-2 px-4 py-2 border-b border-outline-variant ${
                        today ? "bg-primary-fixed" : "bg-surface-container-low"
                      }`}
                    >
                      <button
                        className="flex h-6 w-6 shrink-0 items-center justify-center rounded text-on-surface-variant hover:bg-surface-container transition-colors"
                        onClick={() => toggleGroup(dateKey)} type="button"
                        aria-label={collapsed ? "Mở rộng" : "Thu gọn"}
                        aria-expanded={!collapsed}
                      >
                        <span className={`material-symbols-outlined text-[16px] transition-transform ${collapsed ? "" : "rotate-90"}`}>chevron_right</span>
                      </button>

                      <span className={`material-symbols-outlined text-[16px] shrink-0 ${today ? "text-primary" : "text-on-surface-variant"}`}>calendar_today</span>

                      <span className={`text-[13px] font-semibold shrink-0 ${today ? "text-primary" : "text-on-surface"}`}>
                        {fmtDateShort(dateKey)}
                      </span>

                      {today && (
                        <span className="flex items-center gap-1 rounded-full bg-primary px-2.5 py-0.5 text-[10px] font-bold text-on-primary shrink-0">
                          <span className="w-1.5 h-1.5 rounded-full bg-on-primary shrink-0" />
                          Hôm nay
                        </span>
                      )}

                      {yesterday && !today && (
                        <span className="flex items-center gap-1 rounded-full bg-surface-container-high px-2.5 py-0.5 text-[10px] font-bold text-on-surface-variant shrink-0">
                          Hôm qua
                        </span>
                      )}

                      <span className={`rounded-full px-2.5 py-0.5 text-[10px] font-bold shrink-0 ${
                        collapsed
                          ? "bg-surface-container text-on-surface-variant"
                          : today
                          ? "bg-primary-container text-on-primary-container"
                          : "bg-surface-container text-on-surface-variant"
                      }`}>
                        {dayRecords.length} sự kiện
                      </span>

                      {collapsed && (
                        <span className="text-[11px] text-on-surface-variant shrink-0 truncate max-w-xs">
                          — {dayRecords.map((r) => r.tableName).filter((v, i, a) => a.indexOf(v) === i).join(", ")}
                        </span>
                      )}
                    </div>

                    {/* Records */}
                    {!collapsed && dayRecords.map((r) => {
                      const st = getAction(r.action);
                      const isSelected = selected?.id === r.id;

                      // Smart user display
                      const userDisplay = r.userName ?? (r.userId != null && r.userId > 0 ? `#${r.userId}` : null);

                      return (
                        <div
                          key={r.id}
                          className={`flex items-start gap-3 px-4 py-3 transition-colors border-b border-outline-variant/10 last:border-b-0 cursor-pointer group ${
                            isSelected
                              ? "bg-primary-fixed border-l-2 border-l-primary"
                              : "hover:bg-surface-container-low"
                          }`}
                          onClick={() => setSelected(r)}
                        >
                          {/* Checkbox — only visible on md+ */}
                          <div className="hidden md:flex items-center justify-center w-9 shrink-0 pt-1">
                            <input
                              type="checkbox"
                              className="h-4 w-4 rounded border-outline-variant accent-primary cursor-pointer"
                              checked={selectedIds.has(r.id)}
                              onChange={(e) => { e.stopPropagation(); toggleSelect(r.id); }}
                              aria-label={`Chọn bản ghi ${r.id}`}
                            />
                          </div>

                          {/* Icon */}
                          <div className="flex h-7 w-7 shrink-0 items-center justify-center rounded-lg">
                            <div className={`flex h-7 w-7 shrink-0 items-center justify-center rounded-lg ${st.iconBg}`}>
                              <span className="material-symbols-outlined text-[14px]">{st.icon}</span>
                            </div>
                          </div>

                          {/* Content */}
                          <div className="flex flex-col min-w-0 flex-1 gap-1">
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className={`text-[12px] font-bold shrink-0 ${st.chipColor}`}>{st.label}</span>
                              <span className="text-[13px] font-medium text-on-surface shrink-0">{r.tableName}</span>
                              <span className="text-[11px] text-on-surface-variant shrink-0">#{r.recordId}</span>
                              {r.oldData && r.newData && (
                                <span className="flex items-center gap-0.5 rounded bg-surface-container-low px-1.5 py-0.5 shrink-0">
                                  <span className="material-symbols-outlined text-[11px] text-secondary">find_replace</span>
                                  <span className="text-[10px] text-secondary font-medium">diff</span>
                                </span>
                              )}
                            </div>
                            <div className="flex items-center gap-2 flex-wrap">
                              {userDisplay ? (
                                <>
                                  <span className="text-[12px] text-on-surface-variant shrink-0">{userDisplay}</span>
                                  {r.ipAddress && (
                                    <>
                                      <span className="text-[10px] text-outline shrink-0">·</span>
                                      <span className="text-[12px] text-on-surface-variant shrink-0">{r.ipAddress}</span>
                                    </>
                                  )}
                                </>
                              ) : r.ipAddress ? (
                                <span className="text-[12px] text-on-surface-variant shrink-0">{r.ipAddress}</span>
                              ) : (
                                <span className="text-[12px] text-outline italic shrink-0">Tự động hệ thống</span>
                              )}
                            </div>
                          </div>

                          {/* Time */}
                          <span className="text-[12px] text-on-surface-variant tabular-nums shrink-0 pt-0.5">
                            {fmtTime(r.createdAt)}
                          </span>

                          {/* Delete button — visible on hover on md+ */}
                          <div className="hidden md:flex items-center gap-1 shrink-0 opacity-0 group-hover:opacity-100 transition-opacity">
                            <IconButton
                              label={`Xóa bản ghi ${r.id}`}
                              variant="ghost"
                              size="sm"
                              onClick={(e) => { e.stopPropagation(); requestDelete(r.id, `${r.tableName} #${r.recordId}`); }}
                              className="text-outline hover:text-error hover:bg-error-container"
                            >
                              <span className="material-symbols-outlined text-[14px]" aria-hidden="true">delete</span>
                            </IconButton>
                          </div>
                        </div>
                      );
                    })}
                  </div>
                );
              })}
            </div>
          )}
        </section>

        {/* ── Pagination ── */}
        {!loading && filtered.length > 0 && (
          <div className="flex items-center justify-between gap-3">
            <div className="flex items-center gap-2 text-[12px] text-on-surface-variant">
              <span>Hiển thị</span>
              <select
                className="appearance-none rounded-lg border border-outline-variant bg-surface-container-lowest px-2 h-8 text-[12px] text-on-surface cursor-pointer pr-6 focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all"
                value={pageSize}
                onChange={(e) => { setPageSize(Number(e.target.value)); setPage(0); }}
              >
                <option value={25}>25</option>
                <option value={50}>50</option>
                <option value={100}>100</option>
              </select>
              <span>/ trang · <strong className="font-semibold text-on-surface">{filtered.length.toLocaleString("vi")}</strong> sự kiện</span>
            </div>

            <div className="flex items-center gap-1">
              <IconButton
                label="Trang đầu"
                variant="ghost"
                size="sm"
                disabled={page <= 0}
                onClick={() => setPage(0)}
                className="border border-outline-variant text-on-surface-variant"
              >
                <span className="material-symbols-outlined text-[14px]" aria-hidden="true">keyboard_double_arrow_left</span>
              </IconButton>
              <IconButton
                label="Trang trước"
                variant="ghost"
                size="sm"
                disabled={page <= 0}
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                className="border border-outline-variant text-on-surface-variant"
              >
                <span className="material-symbols-outlined text-[14px]" aria-hidden="true">chevron_left</span>
              </IconButton>

              {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                let p: number;
                if (totalPages <= 5) p = i + 1;
                else if (page <= 2) p = i + 1;
                else if (page >= totalPages - 3) p = totalPages - 4 + i;
                else p = page - 1 + i;
                const isActive = p === page + 1;
                return (
                  <Button
                    key={p}
                    variant={isActive ? "primary" : "secondary"}
                    size="sm"
                    onClick={() => setPage(p - 1)}
                  >
                    {p}
                  </Button>
                );
              })}

              <IconButton
                label="Trang sau"
                variant="ghost"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage(page + 1)}
                className="border border-outline-variant text-on-surface-variant"
              >
                <span className="material-symbols-outlined text-[14px]" aria-hidden="true">chevron_right</span>
              </IconButton>
              <IconButton
                label="Trang cuối"
                variant="ghost"
                size="sm"
                disabled={page >= totalPages - 1}
                onClick={() => setPage(Math.max(0, totalPages - 1))}
                className="border border-outline-variant text-on-surface-variant"
              >
                <span className="material-symbols-outlined text-[14px]" aria-hidden="true">keyboard_double_arrow_right</span>
              </IconButton>
            </div>

            <p className="text-[12px] text-on-surface-variant">
              Trang <strong className="font-semibold text-on-surface">{page + 1}</strong> / {totalPages}
            </p>
          </div>
        )}
      </div>
    </>
  );
}
