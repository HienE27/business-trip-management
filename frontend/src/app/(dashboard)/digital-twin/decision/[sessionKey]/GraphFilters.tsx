"use client";

import { Button } from "@/components/ui/Button";

interface GraphFiltersProps {
  filterStatus: "all" | "accepted" | "rejected";
  onFilterChange: (status: "all" | "accepted" | "rejected") => void;
  searchQuery: string;
  onSearchChange: (query: string) => void;
  nodeCount: number;
  totalCount: number;
}

/**
 * Filters and search for decision graph.
 */
export function GraphFilters({
  filterStatus,
  onFilterChange,
  searchQuery,
  onSearchChange,
  nodeCount,
  totalCount,
}: GraphFiltersProps) {
  return (
    <div className="flex items-center justify-between gap-4 bg-surface-container-lowest border border-outline-variant rounded-xl p-4">
      {/* Status filter */}
      <div className="flex items-center gap-2">
        <span className="text-label-sm text-on-surface-variant mr-2">Filter:</span>
        <Button
          variant={filterStatus === "all" ? "primary" : "ghost"}
          size="sm"
          onClick={() => onFilterChange("all")}
        >
          Tất cả
        </Button>
        <Button
          variant={filterStatus === "accepted" ? "primary" : "ghost"}
          size="sm"
          onClick={() => onFilterChange("accepted")}
        >
          <span className="material-symbols-outlined text-[14px] mr-1">check_circle</span>
          Accepted
        </Button>
        <Button
          variant={filterStatus === "rejected" ? "primary" : "ghost"}
          size="sm"
          onClick={() => onFilterChange("rejected")}
        >
          <span className="material-symbols-outlined text-[14px] mr-1">cancel</span>
          Rejected
        </Button>
      </div>

      {/* Search */}
      <div className="flex items-center gap-4">
        <div className="relative">
          <span className="material-symbols-outlined absolute left-3 top-1/2 -translate-y-1/2 text-outline text-[20px]">
            search
          </span>
          <input
            type="text"
            placeholder="Search staff, constraint..."
            value={searchQuery}
            onChange={(e) => onSearchChange(e.target.value)}
            className="pl-10 pr-4 py-2 bg-surface-container-low rounded-lg border border-transparent focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 w-64 text-body-sm text-on-surface transition-all"
          />
        </div>

        {/* Result count */}
        <span className="text-label-sm text-on-surface-variant">
          {nodeCount} / {totalCount} nodes
        </span>
      </div>
    </div>
  );
}
