"use client";

import { useMemo } from "react";
import type { ConflictCheckResponse, CompensationDay, Schedule, Staff } from "@/types/api";
import type { ScheduleTab } from "@/components/monthly-schedule/types";
import {
  buildCalendarAnnotations,
  buildConflictKeys,
  buildCoverageMap,
  buildOperationalKpis,
  buildWorkloadSnapshot,
} from "@/components/monthly-schedule/utils";

export function useMonthlyScheduleDerivedData(params: {
  selectedTab: ScheduleTab;
  schedules: Schedule[];
  activeStaff: Staff[];
  conflictData: ConflictCheckResponse | null;
  compensationDays: CompensationDay[];
  focusDate: string | null;
  pendingLeaveRequests?: number;
  pendingExchanges?: number;
}) {
  const { selectedTab, schedules, activeStaff, conflictData, compensationDays, focusDate, pendingLeaveRequests = 0, pendingExchanges = 0 } = params;
  const showAll = selectedTab === "ALL";

  const filteredSchedules = useMemo(
    () => (showAll ? schedules : schedules.filter((schedule) => schedule.shiftType.id === selectedTab)),
    [schedules, selectedTab, showAll],
  );

  const conflictList = useMemo(
    () => (showAll ? (conflictData?.conflicts ?? []) : (conflictData?.conflicts ?? []).filter((item) => item.shiftTypeId === selectedTab)),
    [conflictData, selectedTab, showAll],
  );

  const calendarAnnotations = useMemo(
    () => buildCalendarAnnotations(compensationDays, conflictList),
    [compensationDays, conflictList],
  );

  const computedCoverages = useMemo(
    () => buildCoverageMap(schedules, { shiftTypeId: selectedTab }),
    [schedules, selectedTab],
  );

  const coverageGapsByTab = useMemo(() => {
    const gaps: string[] = [];
    const seen = new Set<string>();
    for (const schedule of filteredSchedules) {
      const key = schedule.workDate.substring(0, 10);
      if (seen.has(key)) continue;
      seen.add(key);
      const d = new Date(key + "T00:00:00");
      gaps.push(d.toLocaleDateString("vi-VN", { weekday: "short", day: "numeric", month: "numeric" }));
    }
    gaps.sort();
    return gaps;
  }, [filteredSchedules]);

  const kpis = useMemo(
    () => buildOperationalKpis({ schedules: filteredSchedules, conflictList, activeStaff, pendingLeaveRequests, pendingExchanges }),
    [filteredSchedules, conflictList, activeStaff, pendingLeaveRequests, pendingExchanges],
  );

  const workloadSnapshot = useMemo(
    () => buildWorkloadSnapshot(filteredSchedules),
    [filteredSchedules],
  );

  const focusSchedules = useMemo(() => {
    if (!focusDate) return filteredSchedules.slice(0, 8);
    return filteredSchedules.filter((schedule) => schedule.workDate.startsWith(focusDate));
  }, [filteredSchedules, focusDate]);

  const conflictKeys = useMemo(
    () => buildConflictKeys(conflictList),
    [conflictList],
  );

  return {
    filteredSchedules,
    conflictList,
    calendarAnnotations,
    computedCoverages,
    coverageGapsByTab,
    kpis,
    workloadSnapshot,
    focusSchedules,
    conflictKeys,
  };
}
