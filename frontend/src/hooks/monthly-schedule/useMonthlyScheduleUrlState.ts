"use client";

import { useCallback, useMemo } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import type { MonthlyPanel, MonthlyScheduleQueryState, ScheduleTab, ViewMode } from "@/components/monthly-schedule/types";

function getTab(searchParams: URLSearchParams): ScheduleTab {
  const tab = searchParams.get("tab")?.toUpperCase();
  if (tab === "L01" || tab === "L02" || tab === "L03" || tab === "L04") return tab;
  return "L01";
}

function getPanel(searchParams: URLSearchParams): MonthlyPanel {
  const panel = searchParams.get("panel");
  if (panel === "conflicts" || panel === "summary") return panel;
  return "overview";
}

function getViewMode(searchParams: URLSearchParams): ViewMode {
  return searchParams.get("view") === "table" ? "table" : "calendar";
}

function getNumberParam(searchParams: URLSearchParams, key: string) {
  const raw = searchParams.get(key);
  if (!raw) return null;
  const value = Number.parseInt(raw, 10);
  return Number.isFinite(value) ? value : null;
}

function toMonthlyScheduleUrl(params: URLSearchParams) {
  const query = params.toString();
  return query ? `/monthly-schedule?${query}` : "/monthly-schedule";
}

export function useMonthlyScheduleUrlState() {
  const router = useRouter();
  const searchParams = useSearchParams();

  const queryState = useMemo<MonthlyScheduleQueryState>(() => ({
    selectedTab: getTab(searchParams),
    selectedPanel: getPanel(searchParams),
    viewMode: getViewMode(searchParams),
    parsedScheduleId: getNumberParam(searchParams, "scheduleId"),
    parsedStaffId: getNumberParam(searchParams, "staffId"),
    parsedSpecialtyId: getNumberParam(searchParams, "specialtyId"),
  }), [searchParams]);

  const setQueryState = useCallback(
    (next: { tab?: ScheduleTab; panel?: MonthlyPanel; view?: ViewMode }) => {
      const params = new URLSearchParams(searchParams.toString());
      if (next.tab) params.set("tab", next.tab);
      if (next.panel) params.set("panel", next.panel);
      if (next.view) params.set("view", next.view);
      router.replace(toMonthlyScheduleUrl(params));
    },
    [router, searchParams],
  );

  const openScheduleDetail = useCallback(
    (scheduleId: number) => {
      const params = new URLSearchParams(searchParams.toString());
      params.set("scheduleId", String(scheduleId));
      router.push(toMonthlyScheduleUrl(params));
    },
    [router, searchParams],
  );

  const closeScheduleDetail = useCallback(() => {
    const params = new URLSearchParams(searchParams.toString());
    params.delete("scheduleId");
    router.replace(toMonthlyScheduleUrl(params));
  }, [router, searchParams]);

  return {
    ...queryState,
    setQueryState,
    openScheduleDetail,
    closeScheduleDetail,
  };
}
