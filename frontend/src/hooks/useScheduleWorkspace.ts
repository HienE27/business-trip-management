"use client";

import { useCallback, useEffect, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type {
  Schedule,
  SchedulePeriod,
  ConflictCheckResponse,
  CompensationDay,
  ShiftRequirement,
  Specialty,
  Staff,
} from "@/types/api";

export type ScheduleWorkspaceState = {
  periods: SchedulePeriod[];
  selectedPeriodId: number | null;
  schedules: Schedule[];
  activeStaff: Staff[];
  conflictData: ConflictCheckResponse | null;
  compensationDays: CompensationDay[];
  requirements: ShiftRequirement[];
  specialties: Specialty[];
  loading: boolean;
  refreshing: boolean;
  message: string | null;
};

export type ScheduleWorkspaceActions = {
  setSelectedPeriodId: (id: number | null) => void;
  refreshWorkspace: () => Promise<void>;
  checkConflicts: () => Promise<void>;
  publishPeriod: () => Promise<void>;
  sendNotifications: () => Promise<void>;
  clearMessage: () => void;
};

export function useScheduleWorkspace(): [ScheduleWorkspaceState, ScheduleWorkspaceActions] {
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [activeStaff, setActiveStaff] = useState<Staff[]>([]);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [compensationDays, setCompensationDays] = useState<CompensationDay[]>([]);
  const [requirements, setRequirements] = useState<ShiftRequirement[]>([]);
  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  const selectedPeriod = periods.find((p) => p.id === selectedPeriodId) ?? null;

  const loadWorkspace = useCallback(async (periodId: number | null) => {
    if (!periodId) {
      setSchedules([]);
      setConflictData(null);
      setCompensationDays([]);
      setRequirements([]);
      setSpecialties([]);
      return;
    }

    let scheduleData: Schedule[] = [];
    let conflictResult: ConflictCheckResponse | null = null;
    let compDaysData: CompensationDay[] = [];
    let reqData: ShiftRequirement[] = [];
    let specialtyData: Specialty[] = [];

    try {
      scheduleData = await api.get<Schedule[]>(`/schedules/period/${periodId}`);
    } catch {
      // schedules will be empty array
    }
    try {
      conflictResult = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`);
    } catch {
      // conflict data will be null
    }
    try {
      compDaysData = await api.get<CompensationDay[]>(`/schedules/compensation-days/${periodId}`) ?? [];
    } catch {
      // compensation days will be empty
    }
    try {
      reqData = await api.get<ShiftRequirement[]>(`/shift-requirements/period/${periodId}`) ?? [];
    } catch {
      // requirements will be empty
    }
    try {
      specialtyData = await api.get<Specialty[]>("/specialties") ?? [];
    } catch {
      // specialties will be empty
    }

    setSchedules(scheduleData);
    setConflictData(conflictResult);
    setCompensationDays(compDaysData);
    setRequirements(reqData);
    setSpecialties(specialtyData);
  }, []);

  useEffect(() => {
    let active = true;

    const bootstrap = async () => {
      try {
        setLoading(true);
        setMessage(null);
        const [periodData, staffData] = await Promise.all([
          api.get<SchedulePeriod[]>("/periods"),
          api.get<Staff[]>("/staff/active"),
        ]);

        if (!active) return;

        const nextPeriods = periodData ?? [];
        setPeriods(nextPeriods);
        setActiveStaff(staffData ?? []);
        const preferred =
          nextPeriods.find((p) => p.status === "DRAFT" || p.status === "PUBLISHED") ??
          nextPeriods[0] ??
          null;
        const nextPeriodId = preferred?.id ?? null;
        setSelectedPeriodId(nextPeriodId);
        await loadWorkspace(nextPeriodId);
      } catch (error) {
        if (!active) return;
        setPeriods([]);
        setActiveStaff([]);
        setSchedules([]);
        setConflictData(null);
        setMessage(getErrorMessage(error, "Không thể tải workspace lập lịch tháng."));
      } finally {
        if (active) setLoading(false);
      }
    };

    void bootstrap();
    return () => {
      active = false;
    };
  }, [loadWorkspace]);

  const handleSetSelectedPeriodId = useCallback(
    (id: number | null) => {
      setSelectedPeriodId(id);
      void loadWorkspace(id);
    },
    [loadWorkspace]
  );

  const refreshWorkspace = useCallback(async () => {
    try {
      setRefreshing(true);
      setMessage(null);
      await loadWorkspace(selectedPeriodId);
    } catch (error) {
      setMessage(getErrorMessage(error, "Không thể làm mới dữ liệu kỳ lịch."));
    } finally {
      setRefreshing(false);
    }
  }, [loadWorkspace, selectedPeriodId]);

  const checkConflicts = useCallback(async () => {
    if (!selectedPeriodId) return;
    try {
      setMessage(null);
      const result = await api.get<ConflictCheckResponse>(
        `/schedules/conflicts/check/${selectedPeriodId}`
      );
      setConflictData(result);
      setMessage(
        result?.hasConflicts
          ? `Phát hiện ${result.totalConflicts} xung đột cần xử lý trước khi publish.`
          : "Không phát hiện xung đột trong kỳ lịch đang chọn."
      );
    } catch (error) {
      setMessage(getErrorMessage(error, "Không thể kiểm tra xung đột kỳ lịch."));
    }
  }, [selectedPeriodId]);

  const publishPeriod = useCallback(async () => {
    if (!selectedPeriodId) return;
    if (conflictData?.hasConflicts) {
      setMessage(`Không thể publish: còn ${conflictData.totalConflicts} xung đột chưa xử lý.`);
      return;
    }
    try {
      setMessage(null);
      await api.post(`/periods/${selectedPeriodId}/publish`, {});
      setMessage("Kỳ lịch đã được công bố. Thông báo chi tiết đã được gửi đến nhân sự.");
      const nextPeriods = await api.get<SchedulePeriod[]>("/periods");
      setPeriods(nextPeriods ?? []);
    } catch (error) {
      setMessage(getErrorMessage(error, "Không thể công bố kỳ lịch."));
    }
  }, [selectedPeriodId, conflictData]);

  const sendNotifications = useCallback(async () => {
    if (!selectedPeriodId || activeStaff.length === 0) return;
    try {
      setMessage(null);
      await Promise.all(
        activeStaff.map((staff) => {
          const staffSchedules = schedules.filter((s) => s.staff.id === staff.id);
          const staffCompDays = compensationDays.filter((cd) => cd.staffId === staff.id);
          const dutyList = staffSchedules
            .map((s) => `${new Date(s.workDate).toLocaleDateString("vi-VN")} – ${s.shiftType.name}`)
            .join("; ");
          const compList = staffCompDays
            .map((cd) => new Date(cd.compensationDate).toLocaleDateString("vi-VN"))
            .join(", ");
          return api.post("/notifications", {
            staffId: staff.id,
            title: `Thông báo lịch trực – ${selectedPeriod?.periodName ?? ""}`,
            message: `Lịch trực của bạn đã được công bố.\nDanh sách trực: ${dutyList || "không có"}\nNgày nghỉ bù: ${compList || "không có"}`,
          });
        })
      );
      setMessage(`Đã gửi thông báo đến ${activeStaff.length} nhân sự.`);
    } catch (error) {
      setMessage(getErrorMessage(error, "Không thể gửi thông báo."));
    }
  }, [selectedPeriodId, activeStaff, schedules, compensationDays, selectedPeriod]);

  const clearMessage = useCallback(() => setMessage(null), []);

  return [
    {
      periods,
      selectedPeriodId,
      schedules,
      activeStaff,
      conflictData,
      compensationDays,
      requirements,
      specialties,
      loading,
      refreshing,
      message,
    },
    {
      setSelectedPeriodId: handleSetSelectedPeriodId,
      refreshWorkspace,
      checkConflicts,
      publishPeriod,
      sendNotifications,
      clearMessage,
    },
  ];
}
