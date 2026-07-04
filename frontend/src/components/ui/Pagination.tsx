import { Button, IconButton } from "@/components/ui";

type PaginationProps = {
  currentPage: number;
  totalPages: number;
  totalItems: number;
  pageSize: number;
  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
};

const PAGE_SIZES = [10, 20, 50, 100];

export function Pagination({
  currentPage,
  totalPages,
  totalItems,
  pageSize,
  onPageChange,
  onPageSizeChange,
}: PaginationProps) {
  const startItem = Math.min((currentPage - 1) * pageSize + 1, totalItems);
  const endItem = Math.min(currentPage * pageSize, totalItems);

  const pages = buildPages(currentPage, totalPages);

  if (totalPages <= 1 && totalItems <= pageSize) return null;

  return (
    <div className="flex flex-col sm:flex-row items-center justify-between gap-4 px-4 py-3 border-t border-outline-variant bg-surface-container-lowest">
      {/* Left: page size */}
      <div className="flex items-center gap-2">
        <span className="text-label-sm text-on-surface-variant">Hàng mới trang:</span>
        <div className="relative">
          <select
            className="h-8 pl-2 pr-7 appearance-none rounded-lg border border-outline-variant bg-surface-container-low text-label-sm text-on-surface cursor-pointer focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
            value={pageSize}
            onChange={(e) => onPageSizeChange?.(Number(e.target.value))}
            aria-label="Hàng mới trang"
          >
            {PAGE_SIZES.map((s) => (
              <option key={s} value={s}>{s}</option>
            ))}
          </select>
          <span className="material-symbols-outlined pointer-events-none absolute right-1.5 top-1/2 -translate-y-1/2 text-[14px] text-on-surface-variant">expand_more</span>
        </div>
        <span className="text-label-sm text-on-surface-variant">
          {startItem}–{endItem} / {totalItems}
        </span>
      </div>

      {/* Right: page controls */}
      <div className="flex items-center gap-1">
        {/* Prev */}
        <IconButton
          label="Trang trước"
          variant="ghost"
          size="sm"
          onClick={() => onPageChange(currentPage - 1)}
          disabled={currentPage <= 1}
          className="text-on-surface"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">chevron_left</span>
        </IconButton>

        {/* Page numbers */}
        {pages.map((p, i) =>
          p === "..." ? (
            <span key={`ellipsis-${i}`} className="w-8 h-8 flex items-center justify-center text-on-surface-variant select-none" aria-hidden="true">
              …
            </span>
          ) : (
            <Button
              key={p}
              variant={p === currentPage ? "primary" : "ghost"}
              size="sm"
              onClick={() => onPageChange(p as number)}
              aria-label={`Trang ${p}`}
              aria-current={p === currentPage ? "page" : undefined}
              className={p === currentPage ? "" : "text-on-surface"}
            >
              {p}
            </Button>
          )
        )}

        {/* Next */}
        <IconButton
          label="Trang sau"
          variant="ghost"
          size="sm"
          onClick={() => onPageChange(currentPage + 1)}
          disabled={currentPage >= totalPages}
          className="text-on-surface"
        >
          <span className="material-symbols-outlined text-[18px]" aria-hidden="true">chevron_right</span>
        </IconButton>
      </div>
    </div>
  );
}

function buildPages(current: number, total: number): (number | "...")[] {
  if (total <= 7) return Array.from({ length: total }, (_, i) => i + 1);

  const pages: (number | "...")[] = [1];

  if (current > 3) pages.push("...");
  for (
    let i = Math.max(2, current - 1);
    i <= Math.min(total - 1, current + 1);
    i++
  ) {
    pages.push(i);
  }
  if (current < total - 2) pages.push("...");

  pages.push(total);

  return pages;
}