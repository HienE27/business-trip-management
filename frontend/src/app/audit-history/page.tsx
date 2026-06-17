"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { EmptyState } from "@/components/ui/EmptyState";
import { api } from "@/lib/api";
import type { AuditHistory } from "@/types/api";

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
  const today = new Date();
  const to = today.toISOString().split("T")[0];
  switch (range) {
    case "today":    return { from: to, to };
    case "yesterday": { const y = new Date(today); y.setDate(y.getDate() - 1); return { from: y.toISOString().split("T")[0], to: y.toISOString().split("T")[0] }; }
    case "7d":  { const s = new Date(today); s.setDate(s.getDate() - 7);  return { from: s.toISOString().split("T")[0], to }; }
    case "30d": { const s = new Date(today); s.setDate(s.getDate() - 30); return { from: s.toISOString().split("T")[0], to }; }
    default: return {};
  }
}

function isToday(dateKey: string) {
  return dateKey === new Date().toISOString().split("T")[0];
}

function isYesterday(dateKey: string) {
  const y = new Date(); y.setDate(y.getDate() - 1);
  return dateKey === y.toISOString().split("T")[0];
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
      <table className="w-full">
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
            <div key={k} className="grid grid-cols-2 rounded-lg overflow-hidden border border-outline-variant/20 text-[12px]">
              <div className="bg-surface-container-low border-r border-outline-variant/20 px-3 py-2">
                <p className="text-[10px] text-error/40 font-medium mb-0.5 leading-none">{prettyKey(k)}</p>
                <p className="text-on-surface font-medium leading-snug mt-0.5">{fmtVal(m1[k])}</p>
              </div>
              <div className="bg-surface-container-lowest px-3 py-2">
                <p className="text-[10px] text-secondary/40 font-medium mb-0.5 leading-none">{prettyKey(k)}</p>
                <p className="text-on-surface font-medium leading-snug mt-0.5">{fmtVal(m2[k])}</p>
              </div>
            </div>
          ))}
        </>
      )}
      {added.length > 0 && (
        <>
          <p className="text-[11px] font-semibold text-secondary mt-3 mb-1.5">{added.length} mới thêm</p>
          {added.map((k) => (
            <div key={k} className="flex items-center gap-3 px-3 py-1.5 rounded-lg bg-secondary-container/20 border border-secondary/10 text-[12px]">
              <span className="material-symbols-outlined text-[13px] text-secondary shrink-0">add</span>
              <span className="text-secondary/70 font-medium w-36 shrink-0">{prettyKey(k)}</span>
              <span className="text-on-surface font-medium">{fmtVal(m2[k])}</span>
            </div>
          ))}
        </>
      )}
      {removed.length > 0 && (
        <>
          <p className="text-[11px] font-semibold text-error mt-3 mb-1.5">{removed.length} đã xóa</p>
          {removed.map((k) => (
            <div key={k} className="flex items-center gap-3 px-3 py-1.5 rounded-lg bg-error-container/20 border border-error/10 text-[12px]">
              <span className="material-symbols-outlined text-[13px] text-error shrink-0">remove</span>
              <span className="text-error/70 font-medium w-36 shrink-0">{prettyKey(k)}</span>
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
          <button
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg hover:bg-surface-container-low transition-colors"
            onClick={onClose} type="button"
          >
            <span className="material-symbols-outlined text-[16px] text-on-surface-variant">close</span>
          </button>
        </div>

        {/* Meta row */}
        <div className="flex items-center gap-4 px-4 py-2.5 border-b border-outline-variant/20 bg-surface shrink-0 text-[12px]">
          <span className="text-on-surface-variant">
            Người thực hiện:{" "}
            <strong className="font-semibold text-on-surface">
              {record.userName ?? (record.userId > 0 ? `#${record.userId}` : <span className="text-outline italic">—</span>)}
            </strong>
          </span>
          {record.ipAddress && (
            <span className="text-on-surface-variant">
              IP: <strong className="font-semibold text-on-surface">{record.ipAddress}</strong>
            </span>
          )}
        </div>

        {/* Tabs */}
        <div className="flex items-end gap-0.5 px-4 pt-3 bg-surface border-b border-outline-variant/20 shrink-0">
          {TABS.map((t) => {
            const disabled = noData && (t.id === "old" || t.id === "new" || t.id === "raw");
            return (
              <button
                key={t.id}
                className={`px-3 py-1.5 text-[12px] font-medium rounded-t-lg border border-transparent transition-all ${
                  tab === t.id
                    ? "bg-surface-container-lowest border-outline-variant text-on-surface"
                    : disabled
                    ? "text-outline/30 cursor-not-allowed"
                    : "text-on-surface-variant hover:bg-surface-container-low hover:text-on-surface"
                }`}
                onClick={() => !disabled && setDetailTab(t.id)} type="button"
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
              : <p className="text-[13px] text-outline italic py-4">Không có dữ liệu cũ.</p>
          )}
          {tab === "new" && (
            record.newData
              ? <SyntaxHighlight json={record.newData} />
              : <p className="text-[13px] text-outline italic py-4">Không có dữ liệu mới.</p>
          )}
          {tab === "raw" && (
            <div className="space-y-4">
              {record.oldData && (
                <div>
                  <p className="text-[11px] font-semibold text-error/50 uppercase tracking-wide mb-2">Dữ liệu cũ</p>
                  <SyntaxHighlight json={record.oldData} />
                </div>
              )}
              {record.newData && (
                <div>
                  <p className="text-[11px] font-semibold text-secondary/50 uppercase tracking-wide mb-2">Dữ liệu mới</p>
                  <SyntaxHighlight json={record.newData} />
                </div>
              )}
              {noData && <p className="text-[13px] text-outline italic">Không có dữ liệu chi tiết.</p>}
            </div>
          )}
        </div>
      </div>
    </div>
  );
}

// ─── Main page ─────────────────────────────────────────────────────────────────

export default function AuditHistoryPage() {
  const [records, setRecords] = useState<AuditHistory[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [search, setSearch] = useState("");
  const [module, setModule] = useState("");
  const [action, setAction] = useState<ActionFilter>("");
  const [dateRange, setDateRange] = useState<DateRange>("30d");
  const [dateFrom, setDateFrom] = useState("");
  const [dateTo, setDateTo] = useState("");
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(50);
  const [selected, setSelected] = useState<AuditHistory | null>(null);
  const [collapsedGroups, setCollapsedGroups] = useState<Set<string>>(new Set());
  const searchRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  const fetchData = useCallback(async (refresh = false) => {
    if (refresh) setRefreshing(true); else setLoading(true);
    try {
      const res = await api.get<AuditHistory[]>("/audit-history");
      if (res) setRecords(res);
    } catch { /* silent */ }
    finally { setLoading(false); setRefreshing(false); }
  }, []);

  useEffect(() => { fetchData(); }, [fetchData]);

  const df = useMemo(() =>
    dateRange === "custom" ? { from: dateFrom, to: dateTo } : getDateRange(dateRange),
  [dateRange, dateFrom, dateTo]);

  const filtered = useMemo(() => {
    const kw = search.trim().toLowerCase();
    return records.filter((r) => {
      if (kw) {
        const match = (r.userName ?? "").toLowerCase().includes(kw)
          || String(r.userId).includes(kw)
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

  const pagedGroups = useMemo(() => {
    const flat = grouped.flatMap(([, g]) => g);
    const start = (page - 1) * pageSize;
    const slice = flat.slice(start, start + pageSize);
    const ids = new Set(slice.map((r) => r.id));
    return grouped
      .map(([dk, recs]) => [dk, recs.filter((r) => ids.has(r.id))] as [string, AuditHistory[]])
      .filter(([, recs]) => recs.length > 0);
  }, [grouped, page, pageSize]);

  const totalPages = useMemo(() => Math.max(1, Math.ceil(filtered.length / pageSize)), [filtered.length, pageSize]);

  const summary = useMemo(() => ({
    total:  filtered.length,
    create: filtered.filter((r) => r.action === "CREATE").length,
    update: filtered.filter((r) => r.action === "UPDATE").length,
    delete: filtered.filter((r) => r.action === "DELETE").length,
  }), [filtered]);

  const modules = useMemo(() => Array.from(new Set(records.map((r) => r.tableName))), [records]);
  const hasFilters = !!(search || module || action || dateRange !== "30d");

  function clearFilters() {
    setSearch(""); setModule(""); setAction(""); setDateRange("30d");
    setDateFrom(""); setDateTo(""); setPage(1);
  }

  function onSearch(val: string) {
    setSearch(val);
    if (searchRef.current) clearTimeout(searchRef.current);
    searchRef.current = setTimeout(() => setPage(1), 300);
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

  useEffect(() => { setPage(1); }, [search, module, action, dateRange, dateFrom, dateTo]);

  const DATE_OPTS: Array<{ v: DateRange; l: string }> = [
    { v: "today",     l: "Hôm nay" },
    { v: "yesterday", l: "Hôm qua" },
    { v: "7d",        l: "7 ngày" },
    { v: "30d",       l: "30 ngày" },
    { v: "custom",    l: "Tùy chỉnh" },
  ];

  return (
    <DashboardShell activeSection="audit-history" description="Theo dõi lịch sử thay đổi toàn hệ thống." title="Nhật ký thao tác">

      {selected && <DetailModal record={selected} onClose={() => setSelected(null)} />}

      <div className="flex flex-col gap-3 pb-6">

        {/* KPI Cards */}
        <section className="grid grid-cols-2 gap-1.5 lg:grid-cols-4">
          {([
            { l: "Tổng sự kiện", v: summary.total,  ic: "history",    bg: "bg-surface-container-low",  co: "text-on-surface-variant" },
            { l: "Tạo mới",      v: summary.create,  ic: "add_circle", bg: "bg-secondary-container",    co: "text-secondary"          },
            { l: "Cập nhật",     v: summary.update,  ic: "edit",       bg: "bg-primary-fixed",          co: "text-primary"           },
            { l: "Xóa",          v: summary.delete,   ic: "delete",     bg: "bg-error-container",        co: "text-error"             },
          ] as const).map((s) => (
            <div
              className="flex items-center gap-3 rounded-xl border border-outline-variant bg-surface-container-lowest px-4 py-3 shadow-sm"
              key={s.l}
            >
              <div className={`flex h-9 w-9 shrink-0 items-center justify-center rounded-lg ${s.bg}`}>
                <span className={`material-symbols-outlined text-[18px] ${s.co}`}>{s.ic}</span>
              </div>
              <div>
                <p className="text-[12px] text-on-surface-variant leading-none">{s.l}</p>
                <p className="text-[20px] font-bold text-on-surface leading-none mt-1">
                  {loading ? "—" : s.v.toLocaleString("vi")}
                </p>
              </div>
            </div>
          ))}
        </section>

        {/* ── TOOLBAR ROW 1: Search + Action chips + Count + Actions ── */}
        <section className="flex items-center gap-2 flex-wrap rounded-xl border border-outline-variant bg-surface-container-lowest px-3 py-2 shadow-sm">

          {/* Search */}
          <div className="relative shrink-0" style={{ minWidth: 180, width: 220, maxWidth: "100%" }}>
            <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[14px]">search</span>
            <input
              autoComplete="off"
              className="w-full rounded-lg border border-outline-variant bg-surface h-8 pl-9 pr-7 text-[12px] text-on-surface placeholder:text-outline focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 transition-all"
              placeholder="Người, module, ID…"
              value={search}
              onChange={(e) => onSearch(e.target.value)}
            />
            {search && (
              <button className="absolute right-2 top-1/2 -translate-y-1/2 p-0.5 rounded hover:bg-surface-container-low" onClick={() => onSearch("")} type="button">
                <span className="material-symbols-outlined text-[11px] text-outline">close</span>
              </button>
            )}
          </div>

          {/* Action filter chips */}
          <div className="flex items-center gap-0.5 shrink-0">
            <button
              className={`rounded-full px-2.5 py-0.5 text-[11px] font-semibold transition-all shrink-0 ${
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
                  className={`rounded-full px-2.5 py-0.5 text-[11px] font-semibold transition-all shrink-0 ${
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

          <span className="text-[12px] text-outline shrink-0 tabular-nums">
            {loading ? "…" : filtered.length.toLocaleString("vi") + " kết quả"}
          </span>

          <div className="w-px h-4 bg-outline-variant shrink-0" />

          <button
            className="flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border border-outline-variant bg-surface hover:bg-surface-container-low transition-colors"
            onClick={() => fetchData(true)} type="button"
          >
            <span className={`material-symbols-outlined text-[14px] text-on-surface-variant ${refreshing ? "animate-spin" : ""}`}>sync</span>
          </button>

          <button
            className="flex h-8 items-center gap-1 rounded-lg border border-outline-variant bg-surface px-2.5 text-[12px] font-medium text-on-surface hover:bg-surface-container-low transition-colors shrink-0"
            onClick={exportJSON} type="button"
          >
            <span className="material-symbols-outlined text-[13px]">download</span>
            Xuất
          </button>

          {hasFilters && (
            <button
              className="flex h-8 items-center gap-1 rounded-lg px-2 text-[12px] font-medium text-primary hover:bg-primary-fixed transition-colors shrink-0"
              onClick={clearFilters} type="button"
            >
              <span className="material-symbols-outlined text-[12px]">clear</span>
              Xóa
            </button>
          )}
        </section>

        {/* ── TOOLBAR ROW 2: Module + Date pills + Custom dates ── */}
        <section className="flex items-center gap-2 flex-wrap rounded-xl border border-outline-variant bg-surface-container-lowest px-3 py-2 shadow-sm">

          {/* Module */}
          <select
            className="appearance-none rounded-lg border border-outline-variant bg-surface px-2 h-8 text-[12px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20 cursor-pointer pr-6 shrink-0 min-w-[120px]"
            value={module}
            onChange={(e) => { setModule(e.target.value); setPage(1); }}
          >
            <option value="">Tất cả module</option>
            {modules.map((m) => <option key={m} value={m}>{m}</option>)}
          </select>

          {/* Date pills */}
          <div className="flex items-center gap-0.5 shrink-0">
            {DATE_OPTS.map((o) => (
              <button
                key={o.v}
                className={`rounded-full px-2.5 py-0.5 text-[11px] font-semibold transition-all shrink-0 ${
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
            <div className="flex items-center gap-1 shrink-0">
              <input
                className="rounded-lg border border-outline-variant bg-surface px-2 h-7 text-[11px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                type="date" value={dateFrom} onChange={(e) => { setDateFrom(e.target.value); setPage(1); }}
              />
              <span className="text-[11px] text-outline">—</span>
              <input
                className="rounded-lg border border-outline-variant bg-surface px-2 h-7 text-[11px] text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
                type="date" value={dateTo} onChange={(e) => { setDateTo(e.target.value); setPage(1); }}
              />
            </div>
          )}
        </section>

        {/* ── Activity Stream ── */}
        <section className="rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">

          {/* Column header */}
          <div className="hidden md:grid grid-cols-[32px_1fr_auto] gap-2 px-4 py-2 bg-surface border-b border-outline-variant shrink-0">
            <span className="text-[10px] font-semibold text-on-surface-variant uppercase tracking-wide text-center">HĐ</span>
            <span className="text-[10px] font-semibold text-on-surface-variant uppercase tracking-wide">Chi tiết sự kiện</span>
            <span className="text-[10px] font-semibold text-on-surface-variant uppercase tracking-wide text-right">Giờ</span>
          </div>

          {loading ? (
            <div className="flex flex-col items-center justify-center gap-2 py-12">
              <div className="size-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
              <p className="text-[12px] text-outline">Đang tải…</p>
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
                      className={`flex items-center gap-2 px-4 py-2 border-b border-outline-variant/20 ${
                        today ? "bg-primary/5" : yesterday ? "bg-surface-container-low" : "bg-surface-container-low"
                      }`}
                    >
                      <button
                        className="flex h-5 w-5 shrink-0 items-center justify-center rounded text-on-surface-variant hover:bg-surface-container-high transition-colors"
                        onClick={() => toggleGroup(dateKey)} type="button"
                      >
                        <span className={`material-symbols-outlined text-[14px] transition-transform ${collapsed ? "" : "rotate-90"}`}>chevron_right</span>
                      </button>

                      <span className={`material-symbols-outlined text-[14px] shrink-0 ${today ? "text-primary" : "text-on-surface-variant"}`}>calendar_today</span>

                      <span className={`text-[12px] font-semibold shrink-0 ${today ? "text-primary" : "text-on-surface"}`}>
                        {fmtDateShort(dateKey)}
                      </span>

                      {today && (
                        <span className="flex items-center gap-1 rounded-full bg-primary/10 px-2 py-0.5 text-[10px] font-bold text-primary shrink-0">
                          <span className="w-1.5 h-1.5 rounded-full bg-primary shrink-0" />
                          Hôm nay
                        </span>
                      )}

                      {yesterday && !today && (
                        <span className="flex items-center gap-1 rounded-full bg-surface-container-high px-2 py-0.5 text-[10px] font-bold text-on-surface-variant shrink-0">
                          Hôm qua
                        </span>
                      )}

                      <span className={`rounded-full px-2 py-0.5 text-[10px] font-bold shrink-0 ${
                        collapsed ? "bg-surface-container-high text-on-surface-variant"
                        : today ? "bg-primary/10 text-primary"
                        : "bg-surface-container-high text-on-surface-variant"
                      }`}>
                        {dayRecords.length} sự kiện
                      </span>

                      {collapsed && (
                        <span className="text-[11px] text-outline shrink-0 truncate max-w-xs">
                          — {dayRecords.map((r) => r.tableName).filter((v, i, a) => a.indexOf(v) === i).join(", ")}
                        </span>
                      )}
                    </div>

                    {/* Records */}
                    {!collapsed && dayRecords.map((r) => {
                      const st = getAction(r.action);
                      const isSelected = selected?.id === r.id;

                      // Smart user display
                      const userDisplay = r.userName ?? (r.userId > 0 ? `#${r.userId}` : null);

                      return (
                        <div
                          key={r.id}
                          className={`flex items-start gap-3 px-4 py-2.5 transition-colors border-b border-outline-variant/10 last:border-b-0 cursor-pointer ${
                            isSelected
                              ? "bg-primary/5 border-l-2 border-l-primary"
                              : "hover:bg-surface-container-low"
                          }`}
                          onClick={() => setSelected(r)}
                        >
                          {/* Icon */}
                          <div className="flex h-6 w-8 shrink-0 items-center justify-center pt-0.5">
                            <div className={`flex h-6 w-6 shrink-0 items-center justify-center rounded ${st.iconBg}`}>
                              <span className="material-symbols-outlined text-[12px]">{st.icon}</span>
                            </div>
                          </div>

                          {/* Content — 2 lines */}
                          <div className="flex flex-col min-w-0 flex-1 gap-0.5">
                            {/* Line 1: Action + Table + ID + diff badge */}
                            <div className="flex items-center gap-2 flex-wrap">
                              <span className={`text-[11px] font-bold shrink-0 ${st.chipColor}`}>{st.label}</span>
                              <span className="text-[12px] font-medium text-on-surface shrink-0">{r.tableName}</span>
                              <span className="text-[11px] text-outline shrink-0">#{r.recordId}</span>
                              {r.oldData && r.newData && (
                                <span className="flex items-center gap-0.5 rounded bg-surface-container-low px-1.5 py-0.5 shrink-0">
                                  <span className="material-symbols-outlined text-[10px] text-secondary">find_replace</span>
                                  <span className="text-[10px] text-secondary font-medium">diff</span>
                                </span>
                              )}
                            </div>

                            {/* Line 2: User + IP + meta */}
                            <div className="flex items-center gap-2 flex-wrap">
                              {userDisplay ? (
                                <>
                                  <span className="text-[11px] text-on-surface-variant shrink-0">{userDisplay}</span>
                                  {r.ipAddress && (
                                    <>
                                      <span className="text-[10px] text-outline shrink-0">·</span>
                                      <span className="text-[11px] text-outline/60 shrink-0">{r.ipAddress}</span>
                                    </>
                                  )}
                                </>
                              ) : r.ipAddress ? (
                                <span className="text-[11px] text-outline/60 shrink-0">{r.ipAddress}</span>
                              ) : (
                                <span className="text-[11px] text-outline/40 italic shrink-0">Tự động hệ thống</span>
                              )}
                            </div>
                          </div>

                          {/* Time — right aligned */}
                          <span className="text-[11px] text-outline tabular-nums shrink-0 pt-0.5">
                            {fmtTime(r.createdAt)}
                          </span>
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
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-1.5 text-[12px] text-outline">
              <span>Hiển thị</span>
              <select
                className="appearance-none rounded-lg border border-outline-variant bg-surface px-1.5 h-6 text-[12px] text-on-surface cursor-pointer pr-5 focus:border-primary focus:outline-none"
                value={pageSize}
                onChange={(e) => { setPageSize(Number(e.target.value)); setPage(1); }}
              >
                <option value={25}>25</option>
                <option value={50}>50</option>
                <option value={100}>100</option>
              </select>
              <span>/ trang · <strong className="font-semibold text-on-surface">{filtered.length.toLocaleString("vi")}</strong> sự kiện</span>
            </div>

            <div className="flex items-center gap-0.5">
              <button className="flex h-6 w-6 items-center justify-center rounded border border-outline-variant bg-surface hover:bg-surface-container-low disabled:opacity-30 transition-colors" disabled={page <= 1} onClick={() => setPage(1)} type="button">
                <span className="material-symbols-outlined text-[11px] text-on-surface-variant">keyboard_double_arrow_left</span>
              </button>
              <button className="flex h-6 w-6 items-center justify-center rounded border border-outline-variant bg-surface hover:bg-surface-container-low disabled:opacity-30 transition-colors" disabled={page <= 1} onClick={() => setPage((p) => p - 1)} type="button">
                <span className="material-symbols-outlined text-[11px] text-on-surface-variant">chevron_left</span>
              </button>

              {Array.from({ length: Math.min(5, totalPages) }, (_, i) => {
                let p: number;
                if (totalPages <= 5) p = i + 1;
                else if (page <= 3) p = i + 1;
                else if (page >= totalPages - 2) p = totalPages - 4 + i;
                else p = page - 2 + i;
                return (
                  <button
                    key={p}
                    className={`flex h-6 min-w-[24px] items-center justify-center rounded px-1 text-[11px] font-medium transition-colors ${
                      p === page
                        ? "bg-primary text-on-primary shadow-sm"
                        : "border border-outline-variant bg-surface hover:bg-surface-container-low text-on-surface"
                    }`}
                    onClick={() => setPage(p)} type="button"
                  >
                    {p}
                  </button>
                );
              })}

              <button className="flex h-6 w-6 items-center justify-center rounded border border-outline-variant bg-surface hover:bg-surface-container-low disabled:opacity-30 transition-colors" disabled={page >= totalPages} onClick={() => setPage((p) => p + 1)} type="button">
                <span className="material-symbols-outlined text-[11px] text-on-surface-variant">chevron_right</span>
              </button>
              <button className="flex h-6 w-6 items-center justify-center rounded border border-outline-variant bg-surface hover:bg-surface-container-low disabled:opacity-30 transition-colors" disabled={page >= totalPages} onClick={() => setPage(totalPages)} type="button">
                <span className="material-symbols-outlined text-[11px] text-on-surface-variant">keyboard_double_arrow_right</span>
              </button>
            </div>

            <p className="text-[12px] text-outline">
              Trang <strong className="font-semibold text-on-surface">{page}</strong> / {totalPages}
            </p>
          </div>
        )}
      </div>
    </DashboardShell>
  );
}
