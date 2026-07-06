"use client";

import { useCallback } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { ConflictCheckResponse } from "@/types/api";
import {
  useSchedulePeriodData,
  type UseSchedulePeriodDataReturn,
} from "./useSchedulePeriodData";

/**
 * Wrapper cho `/monthly-schedule`: thêm các action nghiệp vụ
 * (checkConflicts, publishPeriod, sendNotifications) lên trên
 * `useSchedulePeriodData`.
 *
 * Dashboard KHÔNG cần các action này → dùng thẳng `useSchedulePeriodData`.
 */
export type ScheduleWorkspaceState = Pick<
  UseSchedulePeriodDataReturn,
  | "periods"
  | "selectedPeriodId"
  | "schedules"
  | "activeStaff"
  | "conflictData"
  | "compensationDays"
  | "specialties"
  | "loading"
  | "refreshing"
  | "message"
>;

export type ScheduleWorkspaceActions = Pick<
  UseSchedulePeriodDataReturn,
  "setSelectedPeriodId" | "refresh" | "setMessage" | "clearMessage"
> & {
  refreshWorkspace: () => Promise<void>;
  checkConflicts: () => Promise<void>;
  publishPeriod: () => Promise<void>;
  sendNotifications: () => Promise<void>;
};

export function useScheduleWorkspace(): [ScheduleWorkspaceState, ScheduleWorkspaceActions] {
  const data = useSchedulePeriodData();

  const checkConflicts = useCallback(async () => {
    if (!data.selectedPeriodId) return;
    try {
      data.clearMessage();
      const result = await api.get<ConflictCheckResponse>(
        `/schedules/conflicts/check/${data.selectedPeriodId}`
      );
      data.setMessage(
        result?.hasConflicts
          ? `Phát hiện ${result.totalConflicts} xung đột cần xử lý trước khi publish.`
          : "Không phát hiện xung đột trong kỳ lịch đang chọn."
      );
    } catch (error) {
      data.setMessage(getErrorMessage(error, "Không thể kiểm tra xung đột kỳ lịch."));
    }
  }, [data]);

  const publishPeriod = useCallback(async () => {
    if (!data.selectedPeriodId) return;
    if (data.conflictData?.hasConflicts) {
      data.setMessage(`Không thể publish: còn ${data.conflictData.totalConflicts} xung đột chưa xử lý.`);
      return;
    }
    try {
      data.clearMessage();
      await api.post(`/periods/${data.selectedPeriodId}/publish`, {});
      data.setMessage("Kỳ lịch đã được công bố. Thông báo chi tiết đã được gửi đến nhân sự.");
      await data.refresh();
    } catch (error) {
      data.setMessage(getErrorMessage(error, "Không thể công bố kỳ lịch."));
    }
  }, [data]);

  const sendNotifications = useCallback(async () => {
    if (!data.selectedPeriodId || data.activeStaff.length === 0) return;
    try {
      data.clearMessage();
      const periodName = data.periods.find((p) => p.id === data.selectedPeriodId)?.periodName ?? "";

      // Pre-build lookups for O(n) instead of O(n²) - performance optimization
      const scheduleByStaff = new Map<number, typeof data.schedules>();
      const compDaysByStaff = new Map<number, typeof data.compensationDays>();
      for (const s of data.schedules) {
        if (!scheduleByStaff.has(s.staff.id)) scheduleByStaff.set(s.staff.id, []);
        scheduleByStaff.get(s.staff.id)!.push(s);
      }
      for (const cd of data.compensationDays) {
        if (!compDaysByStaff.has(cd.staffId)) compDaysByStaff.set(cd.staffId, []);
        compDaysByStaff.get(cd.staffId)!.push(cd);
      }

      await Promise.all(
        data.activeStaff.map((staff) => {
          const staffSchedules = scheduleByStaff.get(staff.id) ?? [];
          const staffCompDays = compDaysByStaff.get(staff.id) ?? [];
          // Use simple string formatting instead of Date parsing for better performance
          const dutyList = staffSchedules
            .map((s) => `${s.workDate.split("T")[0].split("-").reverse().join("/")} – ${s.shiftType.name}`)
            .join("; ");
          const compList = staffCompDays
            .map((cd) => cd.compensationDate.split("T")[0].split("-").reverse().join("/"))
            .join(", ");
          return api.post("/notifications", {
            staffId: staff.id,
            title: `Thông báo lịch trực – ${periodName}`,
            message: `Lịch trực của bạn đã được công bố.\nDanh sách trực: ${dutyList || "không có"}\nNgày nghỉ bù: ${compList || "không có"}`,
          });
        })
      );
      data.setMessage(`Đã gửi thông báo đến ${data.activeStaff.length} nhân sự.`);
    } catch (error) {
      data.setMessage(getErrorMessage(error, "Không thể gửi thông báo."));
    }
  }, [data]);

  const state: ScheduleWorkspaceState = {
    periods: data.periods,
    selectedPeriodId: data.selectedPeriodId,
    schedules: data.schedules,
    activeStaff: data.activeStaff,
    conflictData: data.conflictData,
    compensationDays: data.compensationDays,
    specialties: data.specialties,
    loading: data.loading,
    refreshing: data.refreshing,
    message: data.message,
  };

  const actions: ScheduleWorkspaceActions = {
    setSelectedPeriodId: data.setSelectedPeriodId,
    refresh: data.refresh,
    refreshWorkspace: data.refresh,
    setMessage: data.setMessage,
    clearMessage: data.clearMessage,
    checkConflicts,
    publishPeriod,
    sendNotifications,
  };

  return [state, actions];
}