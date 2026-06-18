"use client";

import { PAGE_SIZE, type SortKey, type SortDir } from "./constants";

export type ScheduleTablePaginationProps = {
  totalItems: number;
  page: number;
  totalPages: number;
  onPageChange: (page: number) => void;
};

export function ScheduleTablePagination({ totalItems, page, totalPages, onPageChange }: ScheduleTablePaginationProps) {
  if (totalPages <= 1) return null;
  const start = (page - 1) * PAGE_SIZE + 1;
  const end = Math.min(page * PAGE_SIZE, totalItems);

  return (
    <div className="px-4 py-3 border-t border-outline-variant bg-surface-container-low flex items-center justify-between gap-3 flex-wrap shrink-0">
      <span className="text-label-sm text-on-surface-variant">
        Hiển {start}–{end} / {totalItems} lịch
      </span>
      <div className="flex items-center gap-1">
        <button type="button" onClick={() => onPageChange(1)} disabled={page === 1} className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant transition-colors disabled:opacity-30 disabled:cursor-not-allowed" aria-label="Trang đầu">
          <span className="material-symbols-outlined text-[18px]">first_page</span>
        </button>
        <button type="button" onClick={() => onPageChange(Math.max(1, page - 1))} disabled={page === 1} className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant transition-colors disabled:opacity-30 disabled:cursor-not-allowed" aria-label="Trang trước">
          <span className="material-symbols-outlined text-[18px]">chevron_left</span>
        </button>
        {Array.from({ length: totalPages }, (_, i) => {
          let p: number;
          if (totalPages <= 7) {
            p = i + 1;
          } else if (page <= 4) {
            p = i + 1;
          } else if (page >= totalPages - 3) {
            p = totalPages - 6 + i;
          } else {
            p = page - 3 + i;
          }
          return (
            <button
              key={p}
              type="button"
              onClick={() => onPageChange(p)}
              aria-label={`Trang ${p}${page === p ? " (đang xem)" : ""}`}
              aria-current={page === p ? "page" : undefined}
              className={`min-w-8 h-8 px-2 rounded-lg text-label-sm font-medium transition-colors focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary ${
                page === p ? "bg-primary text-on-primary" : "hover:bg-surface-container-high text-on-surface"
              }`}
            >
              {p}
            </button>
          );
        })}
        <button type="button" onClick={() => onPageChange(Math.min(totalPages, page + 1))} disabled={page === totalPages} className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant transition-colors disabled:opacity-30 disabled:cursor-not-allowed" aria-label="Trang sau">
          <span className="material-symbols-outlined text-[18px]">chevron_right</span>
        </button>
        <button type="button" onClick={() => onPageChange(totalPages)} disabled={page === totalPages} className="p-1.5 rounded-lg hover:bg-surface-container-high text-on-surface-variant transition-colors disabled:opacity-30 disabled:cursor-not-allowed" aria-label="Trang cuối">
          <span className="material-symbols-outlined text-[18px]">last_page</span>
        </button>
      </div>
      {totalPages > 10 && <PaginationJumpInput totalPages={totalPages} currentPage={page} onJump={onPageChange} />}
    </div>
  );
}

function PaginationJumpInput({ totalPages, currentPage, onJump }: { totalPages: number; currentPage: number; onJump: (page: number) => void }) {
  return (
    <form
      onSubmit={(e) => {
        e.preventDefault();
        const input = e.currentTarget.querySelector<HTMLInputElement>("input");
        if (!input) return;
        const v = Math.max(1, Math.min(totalPages, Number(input.value) || 1));
        onJump(v);
        input.value = String(v);
        input.blur();
      }}
      className="flex items-center gap-1.5 text-label-sm text-on-surface-variant"
      role="group"
      aria-label="Nhảy tới trang"
    >
      <label htmlFor="pagination-jump" className="hidden sm:inline">Tới trang</label>
      <input
        id="pagination-jump"
        type="number"
        min={1}
        max={totalPages}
        defaultValue={currentPage}
        key={currentPage}
        className="w-14 h-8 px-2 text-center rounded-lg border border-outline-variant bg-surface text-label-sm text-on-surface focus:border-primary focus:ring-2 focus:ring-primary/20 focus:outline-none tabular-nums"
        aria-label={`Nhập số trang từ 1 đến ${totalPages}`}
      />
      <span className="text-on-surface-variant tabular-nums">/ {totalPages}</span>
    </form>
  );
}

export type SortHeaderProps = {
  sortKey: SortKey;
  sortDir: SortDir;
  onSort: (key: SortKey) => void;
};

export const TABLE_HEADERS = [
  { key: "workDate" as SortKey | null, label: "Ngày", className: "w-24" },
  { key: null, label: "Thứ", className: "w-16" },
  { key: "shiftType" as SortKey | null, label: "Loại ca", className: "w-28" },
  { key: "staffName" as SortKey | null, label: "Nhân sự", className: "w-44" },
  { key: null, label: "Xung đột", className: "w-20" },
  { key: null, label: "Ghi chú", className: "" },
  { key: null, label: "Hành động", className: "w-28" },
];

export function ScheduleTableHeader({ sortKey, sortDir, onSort }: SortHeaderProps) {
  return (
    <thead className="sticky top-0 z-10 bg-surface-container-low">
      <tr>
        {TABLE_HEADERS.map(({ key, label, className }) => {
          const isSortable = !!key;
          const ariaSort = !isSortable
            ? undefined
            : sortKey === key
            ? sortDir === "asc" ? "ascending" : "descending"
            : "none";
          const sortKeyTyped = key as SortKey | null;
          return (
            <th
              key={label}
              scope="col"
              aria-sort={ariaSort}
              className={`px-3 py-3.5 text-left text-label-sm font-bold text-on-surface-variant uppercase tracking-wider whitespace-nowrap border-b-2 border-outline-variant ${className} ${
                isSortable ? "cursor-pointer hover:bg-surface-container-high group select-none focus-within:bg-surface-container-high" : ""
              }`}
            >
              {isSortable && sortKeyTyped ? (
                <button
                  type="button"
                  onClick={() => onSort(sortKeyTyped)}
                  onKeyDown={(e) => {
                    if (e.key === "Enter" || e.key === " ") {
                      e.preventDefault();
                      onSort(sortKeyTyped);
                    }
                  }}
                  className="flex items-center gap-1 bg-transparent p-0 text-label-sm font-semibold text-on-surface-variant focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary rounded"
                >
                  {label}
                  {sortKey === sortKeyTyped ? (
                    <span aria-hidden="true" className="material-symbols-outlined text-[18px]">{sortDir === "asc" ? "expand_less" : "expand_more"}</span>
                  ) : (
                    <span aria-hidden="true" className="material-symbols-outlined text-[18px] opacity-0 group-hover:opacity-40">unfold_more</span>
                  )}
                  <span className="sr-only">
                    {sortKey === sortKeyTyped
                      ? `Đang sắp xếp ${sortDir === "asc" ? "tăng dần" : "giảm dần"}`
                      : "Sắp xếp tăng dần"}
                  </span>
                </button>
              ) : (
                <div className="flex items-center gap-1">{label}</div>
              )}
            </th>
          );
        })}
      </tr>
    </thead>
  );
}
