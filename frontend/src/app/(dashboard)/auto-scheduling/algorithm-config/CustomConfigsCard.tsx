"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { Button, Pagination } from "@/components/ui";
import { EmptyState } from "@/components/ui/EmptyState";
import { api } from "@/lib/api";
import type { Page } from "@/types/api";
import { ConfigValueCell } from "./ConfigValueCell";
import { ConfigRowInline } from "./ConfigRowInline";
import type { ConfigEntry } from "./types";
import { LEGACY_AUTO_GEN_KEYS } from "./types";

type SortBy = "key" | "updatedAt";
type SortDir = "asc" | "desc";

const VALUE_TYPE_BADGE: Record<ConfigEntry["valueType"], string> = {
  NUMBER: "bg-primary-fixed text-primary",
  BOOLEAN: "bg-secondary-container text-secondary",
  JSON: "bg-tertiary-container text-tertiary",
  STRING: "bg-surface-container text-on-surface-variant",
};

export function CustomConfigsCard({ onCreate, refreshSignal }: { onCreate: () => void; refreshSignal?: number }) {
  const [configs, setConfigs] = useState<ConfigEntry[]>([]);
  const [loading, setLoading] = useState(true);
  const [keyword, setKeyword] = useState("");
  const [filterType, setFilterType] = useState<"ALL" | ConfigEntry["valueType"]>("ALL");
  const [sortBy, setSortBy] = useState<SortBy>("key");
  const [sortDir, setSortDir] = useState<SortDir>("asc");
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);

  const loadConfigs = useCallback(async () => {
    setLoading(true);
    try {
      const data = await api.getPage<ConfigEntry>("/auto-schedule/config/page", { page, size: pageSize });
      setConfigs(data.content ?? []);
      setTotalPages(data.totalPages ?? 0);
      setTotalElements(data.totalElements ?? 0);
    } catch {
      setConfigs([]);
    } finally {
      setLoading(false);
    }
  }, [page, pageSize]);

  useEffect(() => { void loadConfigs(); }, [loadConfigs, refreshSignal]);

  const filtered = useMemo(() => {
    const kw = keyword.toLowerCase().trim();
    return configs
      .filter(c => {
        if (LEGACY_AUTO_GEN_KEYS.has(c.paramKey)) return false;
        if (filterType !== "ALL" && c.valueType !== filterType) return false;
        if (!kw) return true;
        return c.paramKey.toLowerCase().includes(kw) || c.description.toLowerCase().includes(kw);
      })
      .sort((a, b) => {
        const dir = sortDir === "asc" ? 1 : -1;
        if (sortBy === "key") return dir * a.paramKey.localeCompare(b.paramKey);
        return dir * (new Date(a.updatedAt).getTime() - new Date(b.updatedAt).getTime());
      });
  }, [configs, keyword, filterType, sortBy, sortDir]);

  function toggleSort(column: SortBy) {
    if (sortBy === column) setSortDir(d => d === "asc" ? "desc" : "asc");
    else { setSortBy(column); setSortDir(column === "key" ? "asc" : "desc"); }
  }

  return (
    <div className="bg-surface-container-lowest rounded-2xl border border-outline-variant overflow-hidden">
      <div className="px-5 py-3 border-b border-outline-variant bg-surface-container-low flex items-center justify-between gap-3 flex-wrap">
        <div className="flex items-center gap-2">
          <p className="text-label-sm font-semibold text-on-surface">Cấu hình tùy chỉnh</p>
          <span className="text-[11px] text-on-surface-variant">{configs.length} thông số</span>
        </div>
        <div className="flex items-center gap-2">
          <SearchInput value={keyword} onChange={(v) => { setKeyword(v); setPage(0); }} />
          <TypeFilter value={filterType} onChange={(v) => { setFilterType(v); setPage(0); }} />
          <Button
            variant="primary"
            size="sm"
            onClick={onCreate}
            icon={<span className="material-symbols-outlined text-[14px]" aria-hidden="true">add</span>}
          >
            Thêm
          </Button>
        </div>
      </div>

      {loading ? <TableSkeleton /> : filtered.length === 0 ? (
        <EmptyState
          icon="tune"
          title={configs.length === 0 ? "Chưa có cấu hình tùy chỉnh" : "Không tìm thấy cấu hình phù hợp"}
          description={configs.length === 0 ? "Tạo cấu hình mới để tùy chỉnh thuật toán" : "Thử thay đổi bộ lọc tìm kiếm"}
          size="compact"
        />
      ) : (
        <>
        <div className="overflow-x-auto max-h-[480px] overflow-y-auto">
          <table className="w-full text-left">
            <thead className="sticky top-0 z-10">
              <tr className="bg-surface-container-low border-b border-outline-variant shadow-sm">
                <th scope="col" className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant w-8">
                  <span className="material-symbols-outlined text-[14px]" aria-hidden="true">key</span>
                </th>
                <th scope="col" className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant">
                  <SortHeader label="Thông số" active={sortBy === "key"} dir={sortDir} onClick={() => toggleSort("key")} />
                </th>
                <th scope="col" className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant w-20">Kiểu</th>
                <th scope="col" className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant">Giá trị</th>
                <th scope="col" className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant hidden lg:table-cell">Mô tả</th>
                <th scope="col" className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant w-24">
                  <SortHeader label="Cập nhật" active={sortBy === "updatedAt"} dir={sortDir} onClick={() => toggleSort("updatedAt")} />
                </th>
                <th scope="col" className="px-4 py-2.5 text-label-xs font-semibold text-on-surface-variant w-16 text-right">Hành động</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-outline-variant/50">
              {filtered.map(config => (
                <ConfigRow
                  key={config.paramKey}
                  config={config}
                  onSave={updated => setConfigs(prev => prev.map(c => c.paramKey === config.paramKey ? { ...c, ...updated } : c))}
                  onDelete={() => setConfigs(prev => prev.filter(c => c.paramKey !== config.paramKey))}
                />
              ))}
            </tbody>
          </table>
        </div>
        <Pagination
          currentPage={page + 1}
          totalPages={totalPages}
          totalItems={totalElements}
          pageSize={pageSize}
          onPageChange={(p) => setPage(p - 1)}
          onPageSizeChange={(s) => { setPageSize(s); setPage(0); }}
        />
        </>
      )}
    </div>
  );
}

function ConfigRow({ config, onSave, onDelete }: { config: ConfigEntry; onSave: (u: { paramValue?: string; description?: string }) => void; onDelete: () => void }) {
  return (
    <tr className="hover:bg-surface-container-low transition-colors group">
      <td className="px-4 py-3">
        <span className="material-symbols-outlined text-[14px] text-outline" aria-hidden="true">settings</span>
      </td>
      <td className="px-4 py-3">
        <code className="font-mono text-[11px] font-semibold text-primary bg-primary-fixed/20 px-1.5 py-0.5 rounded">{config.paramKey}</code>
      </td>
      <td className="px-4 py-3">
        <span className={`inline-flex px-1.5 py-0.5 rounded text-label-xs font-semibold uppercase ${VALUE_TYPE_BADGE[config.valueType]}`}>
          {config.valueType}
        </span>
      </td>
      <td className="px-4 py-3"><ConfigValueCell config={config} /></td>
      <td className="px-4 py-3 hidden lg:table-cell">
        <p className="text-[11px] text-on-surface-variant line-clamp-1" title={config.description}>{config.description || "—"}</p>
      </td>
      <td className="px-4 py-3">
        <p className="text-[11px] text-outline">{config.updatedBy || "—"}</p>
        <p className="text-[10px] text-outline/60">{new Date(config.updatedAt).toLocaleDateString("vi-VN")}</p>
      </td>
      <td className="px-4 py-3">
        <div className="flex items-center justify-end gap-1 opacity-0 group-hover:opacity-100 transition-opacity">
          <ConfigRowInline config={config} onSave={onSave} onDelete={onDelete} />
        </div>
      </td>
    </tr>
  );
}

function SortHeader({ label, active, dir, onClick }: { label: string; active: boolean; dir: SortDir; onClick: () => void }) {
  return (
    <button
      type="button"
      onClick={onClick}
      className="flex items-center gap-1 hover:text-primary transition-colors uppercase cursor-pointer"
    >
      {label}
      <span className="material-symbols-outlined text-[12px]" aria-hidden="true">
        {active ? (dir === "asc" ? "arrow_upward" : "arrow_downward") : "unfold_more"}
      </span>
    </button>
  );
}

function SearchInput({ value, onChange }: { value: string; onChange: (v: string) => void }) {
  return (
    <div className="relative">
      <span className="material-symbols-outlined absolute left-2.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[14px]" aria-hidden="true">search</span>
      <input
        className="h-8 pl-8 pr-3 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface w-40 focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 transition-all"
        placeholder="Tìm..."
        value={value}
        onChange={e => onChange(e.target.value)}
      />
    </div>
  );
}

function TypeFilter({ value, onChange }: { value: "ALL" | ConfigEntry["valueType"]; onChange: (v: "ALL" | ConfigEntry["valueType"]) => void }) {
  return (
    <div className="relative">
      <select
        className="h-8 pl-2.5 pr-7 rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface appearance-none focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 cursor-pointer transition-all"
        value={value}
        onChange={e => onChange(e.target.value as typeof value)}
      >
        <option value="ALL">Tất cả</option>
        <option value="STRING">STRING</option>
        <option value="NUMBER">NUMBER</option>
        <option value="BOOLEAN">BOOLEAN</option>
        <option value="JSON">JSON</option>
      </select>
      <span className="material-symbols-outlined absolute right-1.5 top-1/2 -translate-y-1/2 text-on-surface-variant text-[14px] pointer-events-none" aria-hidden="true">expand_more</span>
    </div>
  );
}

function TableSkeleton() {
  return (
    <div className="p-5 space-y-2">
      {[1, 2, 3].map(i => <div key={i} className="h-10 bg-surface-container-low rounded-lg animate-pulse" />)}
    </div>
  );
}