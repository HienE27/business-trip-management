"use client";

import { useCallback, useMemo } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import type { MonthlyPanel, MonthlyScheduleQueryState, ScheduleTab } from "@/components/monthly-schedule/types";

function getTab(searchParams: URLSearchParams): ScheduleTab {
  const tab = searchParams.get("tab")?.toUpperCase();
  if (tab === "L01" || tab === "L02" || tab === "L03" || tab === "L04" || tab === "ALL") return tab;
  return "ALL";
}

function getPanel(searchParams: URLSearchParams): MonthlyPanel {
  const panel = searchParams.get("panel");
  if (panel === "conflicts" || panel === "summary") return panel;
  return "overview";
}

function getNumberParam(searchParams: URLSearchParams, key: string) {
  const raw = searchParams.get(key);
  if (!raw) return null;
  const value = Number.parseInt(raw, 10);
  return Number.isFinite(value) ? value : null;
}

function getPeriodId(searchParams: URLSearchParams): number | null {
  return getNumberParam(searchParams, "periodId");
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
    parsedScheduleId: getNumberParam(searchParams, "scheduleId"),
    parsedStaffId: getNumberParam(searchParams, "staffId"),
    parsedSpecialtyId: getNumberParam(searchParams, "specialtyId"),
    periodId: getPeriodId(searchParams),
  }), [searchParams]);

  const setQueryState = useCallback(
    (next: { tab?: ScheduleTab; panel?: MonthlyPanel; periodId?: number | null; staffId?: number | null }) => {
      const params = new URLSearchParams(searchParams.toString());
      if (next.tab) params.set("tab", next.tab);
      if (next.panel) params.set("panel", next.panel);
      if (next.periodId !== undefined) {
        if (next.periodId === null) {
          params.delete("periodId");
        } else {
          params.set("periodId", String(next.periodId));
        }
      }
      if (next.staffId !== undefined) {
        if (next.staffId === null) {
          params.delete("staffId");
        } else {
          params.set("staffId", String(next.staffId));
        }
      }
      router.replace(toMonthlyScheduleUrl(params), { scroll: false });
    },
    [router, searchParams],
  );

  const openScheduleDetail = useCallback(
    (scheduleId: number) => {
      const params = new URLSearchParams(searchParams.toString());
      params.set("scheduleId", String(scheduleId));
      router.push(toMonthlyScheduleUrl(params), { scroll: false });
    },
    [router, searchParams],
  );

  const closeScheduleDetail = useCallback(() => {
    const params = new URLSearchParams(searchParams.toString());
    params.delete("scheduleId");
    router.replace(toMonthlyScheduleUrl(params), { scroll: false });
  }, [router, searchParams]);

  return {
    ...queryState,
    setQueryState,
    openScheduleDetail,
    closeScheduleDetail,
  };
}
