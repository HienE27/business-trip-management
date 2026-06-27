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
  pendingLeaveRequests?: number;
}) {
  const { selectedTab, schedules, activeStaff, conflictData, compensationDays, requirements, focusDate, pendingLeaveRequests = 0 } = params;
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
    () => buildCoverageMap(requirements, { shiftTypeId: selectedTab }),
    [requirements, selectedTab],
  );

  const filteredRequirements = useMemo(
    () => (showAll ? requirements : requirements.filter((req) => req.shiftType.id === selectedTab)),
    [requirements, selectedTab, showAll],
  );

  const coverageGapsByTab = useMemo(() => {
    const gaps: string[] = [];
    const seen = new Set<string>();
    for (const req of filteredRequirements) {
      if (req.assignedStaffCount >= req.requiredStaffCount) continue;
      // Use substring instead of split for better performance
      const key = req.workDate.substring(0, 10);
      if (seen.has(key)) continue;
      seen.add(key);
      gaps.push(key);
    }
    gaps.sort();
    return gaps;
  }, [filteredRequirements]);

  const kpis = useMemo(
    () => buildOperationalKpis({ schedules: filteredSchedules, requirements: filteredRequirements, conflictList, activeStaff, pendingLeaveRequests }),
    [filteredSchedules, filteredRequirements, conflictList, activeStaff, pendingLeaveRequests],
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
