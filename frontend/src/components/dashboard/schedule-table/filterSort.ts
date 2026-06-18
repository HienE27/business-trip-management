import type { Schedule } from "@/types/api";
import { PAGE_SIZE, type FilterConflict, type SortDir, type SortKey } from "./constants";

export type TableFilters = {
  search: string;
  filterType: string;
  filterStaff: string;
  filterConflict: FilterConflict;
  dateFrom: string;
  dateTo: string;
  sortKey: SortKey;
  sortDir: SortDir;
};

export const EMPTY_FILTERS: TableFilters = {
  search: "",
  filterType: "all",
  filterStaff: "all",
  filterConflict: "all",
  dateFrom: "",
  dateTo: "",
  sortKey: "workDate",
  sortDir: "asc",
};

export function filterSchedules(schedules: Schedule[], f: TableFilters): Schedule[] {
  let result: Schedule[] = schedules;

  if (f.search.trim()) {
    const q = f.search.toLowerCase();
    result = result.filter(
      (s) =>
        s.staff.fullName.toLowerCase().includes(q) ||
        s.shiftType.name.toLowerCase().includes(q) ||
        String(s.id).includes(q),
    );
  }

  if (f.filterType !== "all") result = result.filter((s) => s.shiftType.id === f.filterType);
  if (f.filterStaff !== "all") result = result.filter((s) => s.staff.id === Number(f.filterStaff));
  if (f.filterConflict === "conflict") result = result.filter((s) => s.hasConflict);
  if (f.filterConflict === "clean") result = result.filter((s) => !s.hasConflict);
  if (f.dateFrom) result = result.filter((s) => s.workDate >= f.dateFrom);
  if (f.dateTo) result = result.filter((s) => s.workDate <= f.dateTo);

  return result;
}

export function sortSchedules(schedules: Schedule[], sortKey: SortKey, sortDir: SortDir): Schedule[] {
  return [...schedules].sort((a, b) => {
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
}

export function paginate<T>(items: T[], page: number, pageSize: number = PAGE_SIZE): {
  pageData: T[];
  safePage: number;
  totalPages: number;
} {
  const totalPages = Math.max(1, Math.ceil(items.length / pageSize));
  const safePage = Math.min(Math.max(1, page), totalPages);
  const pageData = items.slice((safePage - 1) * pageSize, safePage * pageSize);
  return { pageData, safePage, totalPages };
}

export function applyTablePipeline(schedules: Schedule[], f: TableFilters, page: number) {
  const filtered = filterSchedules(schedules, f);
  const sorted = sortSchedules(filtered, f.sortKey, f.sortDir);
  const paged = paginate(sorted, page);
  return { ...paged, totalFiltered: filtered.length };
}

export function getUniqueStaff(schedules: Schedule[]): Array<[number, string]> {
  const map = new Map<number, string>();
  schedules.forEach((s) => map.set(s.staff.id, s.staff.fullName));
  return Array.from(map.entries()).sort((a, b) => a[1].localeCompare(b[1]));
}

export function countActiveFilters(f: TableFilters): number {
  return (
    (f.search ? 1 : 0) +
    (f.filterType !== "all" ? 1 : 0) +
    (f.filterStaff !== "all" ? 1 : 0) +
    (f.filterConflict !== "all" ? 1 : 0) +
    (f.dateFrom ? 1 : 0) +
    (f.dateTo ? 1 : 0)
  );
}

export function getInitials(fullName: string): string {
  const parts = fullName.trim().split(/\s+/);
  return parts[parts.length - 1]?.slice(0, 2).toUpperCase() ?? "";
}

export function isSameDay(d: Date, ref?: Date): boolean {
  const now = ref ?? new Date();
  return d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth() && d.getDate() === now.getDate();
}
