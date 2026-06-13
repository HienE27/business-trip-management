"use client";

import { useMemo } from "react";
import type { ConflictCheckResponse, CompensationDay, Schedule, ShiftRequirement, Staff } from "@/types/api";
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
  requirements: ShiftRequirement[];
  focusDate: string | null;
}) {
  const { selectedTab, schedules, activeStaff, conflictData, compensationDays, requirements, focusDate } = params;

  const filteredSchedules = useMemo(
    () => schedules.filter((schedule) => schedule.shiftType.id === selectedTab),
    [schedules, selectedTab],
  );

  const conflictList = useMemo(
    () => (conflictData?.conflicts ?? []).filter((item) => item.shiftTypeId === selectedTab),
    [conflictData, selectedTab],
  );

  const calendarAnnotations = useMemo(
    () => buildCalendarAnnotations(compensationDays, conflictList),
    [compensationDays, conflictList],
  );

  const computedCoverages = useMemo(
    () => buildCoverageMap(requirements),
    [requirements],
  );

  const kpis = useMemo(
    () => buildOperationalKpis({ schedules, requirements, conflictData, activeStaff }),
    [schedules, requirements, conflictData, activeStaff],
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
    kpis,
    workloadSnapshot,
    focusSchedules,
    conflictKeys,
  };
}
