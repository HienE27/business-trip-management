"use client";

import { useCallback, useMemo } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import type { ScheduleTab } from "@/components/monthly-schedule/types";

/**
 * Filter state cho trang lịch: chuyển thành URL query params
 * để share/back/forward hoạt động nhất quán giữa dashboard và monthly-schedule.
 *
 * URL shape:
 *   ?tab=L02          → L01..L04 hoặc "ALL"
 *   ?staffId=42       → id nhân sự hoặc bỏ qua (= tất cả)
 *   ?date=2026-06-20  → focus date cho chip focus day
 *
 * Mặc định khi không có param:
 *   tab = "ALL", staffId = null, date = null
 */
export type ScheduleFilters = {
  selectedTab: ScheduleTab;
  selectedStaffId: number | null;
  selectedDate: string | null;
};

export type ScheduleFiltersActions = {
  setTab: (tab: ScheduleTab) => void;
  setStaffId: (id: number | null) => void;
  setDate: (date: string | null) => void;
  /** Set nhiều filter cùng lúc — dùng cho workflow cross-page. */
  applyFilters: (next: Partial<ScheduleFilters>) => void;
};

export type UseScheduleFiltersOptions = {
  /** Route path cần giữ khi cập nhật URL. Mặc định giữ nguyên (chỉ thay đổi query). */
  basePath?: string;
  /** Dùng `router.push` thay vì `router.replace`. Mặc định `false` (replace). */
  push?: boolean;
};

function isTab(v: string | null): v is ScheduleTab {
  return v === "L01" || v === "L02" || v === "L03" || v === "L04" || v === "ALL";
}

export function useScheduleFilters(
  options: UseScheduleFiltersOptions = {}
): ScheduleFilters & ScheduleFiltersActions {
  const { basePath, push = false } = options;
  const router = useRouter();
  const searchParams = useSearchParams();

  const selectedTab: ScheduleTab = useMemo(() => {
    const v = searchParams.get("tab");
    return isTab(v) ? v : "ALL";
  }, [searchParams]);

  const selectedStaffId: number | null = useMemo(() => {
    const v = searchParams.get("staffId");
    if (!v) return null;
    const n = Number.parseInt(v, 10);
    return Number.isFinite(n) ? n : null;
  }, [searchParams]);

  const selectedDate: string | null = useMemo(() => {
    const v = searchParams.get("date");
    return v && /^\d{4}-\d{2}-\d{2}$/.test(v) ? v : null;
  }, [searchParams]);

  const navigate = useCallback(
    (params: URLSearchParams) => {
      const query = params.toString();
      const path = basePath ?? (typeof window !== "undefined" ? window.location.pathname : "");
      const url = query ? `${path}?${query}` : path;
      if (push) router.push(url);
      else router.replace(url);
    },
    [basePath, push, router]
  );

  const setTab = useCallback(
    (tab: ScheduleTab) => {
      const params = new URLSearchParams(searchParams.toString());
      if (tab === "ALL") params.delete("tab");
      else params.set("tab", tab);
      navigate(params);
    },
    [navigate, searchParams]
  );

  const setStaffId = useCallback(
    (id: number | null) => {
      const params = new URLSearchParams(searchParams.toString());
      if (id === null) params.delete("staffId");
      else params.set("staffId", String(id));
      navigate(params);
    },
    [navigate, searchParams]
  );

  const setDate = useCallback(
    (date: string | null) => {
      const params = new URLSearchParams(searchParams.toString());
      if (!date) params.delete("date");
      else params.set("date", date);
      navigate(params);
    },
    [navigate, searchParams]
  );

  const applyFilters = useCallback(
    (next: Partial<ScheduleFilters>) => {
      const params = new URLSearchParams(searchParams.toString());
      if (next.selectedTab !== undefined) {
        if (next.selectedTab === "ALL") params.delete("tab");
        else params.set("tab", next.selectedTab);
      }
      if (next.selectedStaffId !== undefined) {
        if (next.selectedStaffId === null) params.delete("staffId");
        else params.set("staffId", String(next.selectedStaffId));
      }
      if (next.selectedDate !== undefined) {
        if (!next.selectedDate) params.delete("date");
        else params.set("date", next.selectedDate);
      }
      navigate(params);
    },
    [navigate, searchParams]
  );

  return { selectedTab, selectedStaffId, selectedDate, setTab, setStaffId, setDate, applyFilters };
}