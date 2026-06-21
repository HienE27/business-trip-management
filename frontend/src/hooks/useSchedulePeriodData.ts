"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useAutoDismiss } from "@/hooks/useAutoDismiss";
import type {
  CompensationDay,
  ConflictCheckResponse,
  Schedule,
  SchedulePeriod,
  ShiftRequirement,
  Specialty,
  Staff,
} from "@/types/api";

/**
 * Hook nền tảng: fetch toàn bộ dữ liệu liên quan đến một kỳ lịch.
 *
 * Dùng chung cho:
 * - `/dashboard` (read-only KPI + lịch overview)
 * - `/monthly-schedule` (full editor — compose thêm `useScheduleWorkspace`)
 *
 * Cung cấp:
 * - `periods`, `selectedPeriodId`, `setSelectedPeriodId`
 * - `schedules`, `activeStaff`, `conflictData`, `compensationDays`,
 *   `requirements`, `specialties`
 * - `loading`, `refreshing`, `message`
 * - `refresh()`, `clearMessage()`
 *
 * Options:
 * - `conflictPollMs`: nếu > 0, poll conflict mỗi N ms (dashboard realtime).
 *   Mặc định `0` (không poll).
 * - `autoSelectPeriod`: tự chọn period DRAFT/PUBLISHED đầu tiên khi mount.
 *   Mặc định `true`.
 */
export type UseSchedulePeriodDataOptions = {
  conflictPollMs?: number;
  autoSelectPeriod?: boolean;
};

export type UseSchedulePeriodDataState = {
  periods: SchedulePeriod[];
  selectedPeriodId: number | null;
  selectedPeriod: SchedulePeriod | null;
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

export type UseSchedulePeriodDataActions = {
  setSelectedPeriodId: (id: number | null) => void;
  refresh: () => Promise<void>;
  setMessage: (message: string | null) => void;
  clearMessage: () => void;
};

export type UseSchedulePeriodDataReturn = UseSchedulePeriodDataState & UseSchedulePeriodDataActions;

const DEFAULT_OPTIONS: Required<UseSchedulePeriodDataOptions> = {
  conflictPollMs: 0,
  autoSelectPeriod: true,
};

export function useSchedulePeriodData(
  options: UseSchedulePeriodDataOptions = {}
): UseSchedulePeriodDataReturn {
  const { conflictPollMs, autoSelectPeriod } = { ...DEFAULT_OPTIONS, ...options };

  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [selectedPeriodId, setSelectedPeriodIdState] = useState<number | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [activeStaff, setActiveStaff] = useState<Staff[]>([]);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [compensationDays, setCompensationDays] = useState<CompensationDay[]>([]);
  const [requirements, setRequirements] = useState<ShiftRequirement[]>([]);
  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [message, setMessage] = useState<string | null>(null);

  // Auto-dismiss transient messages after 5s so they don't linger on screen
  // or survive a remount and confuse the user on re-entry.
  useAutoDismiss(message, () => setMessage(null));

  // Guard khi component unmount trong lúc đang fetch
  const aliveRef = useRef(true);
  useEffect(() => {
    aliveRef.current = true;
    return () => {
      aliveRef.current = false;
    };
  }, []);

  // Clear any stale banner when the user navigates to or away from this
  // page. Without this, a success/error message set during a prior visit
  // (or on a different page that shares the same hook) would still be
  // visible when the user returns.
  const pathname = usePathname();
  useEffect(() => {
    setMessage(null);
  }, [pathname]);

  const selectedPeriod = periods.find((p) => p.id === selectedPeriodId) ?? null;

  const loadPeriodData = useCallback(async (periodId: number | null, isRefresh = false) => {
    if (!periodId) {
      setSchedules([]);
      setConflictData(null);
      setCompensationDays([]);
      setRequirements([]);
      return;
    }

    if (isRefresh) setRefreshing(true);

    const fetchOne = async <T,>(fetcher: () => Promise<T>, fallback: T): Promise<T> => {
      try {
        const result = await fetcher();
        return (result ?? fallback) as T;
      } catch {
        return fallback;
      }
    };

    const [scheduleData, conflictResult, compDaysData, reqData] = await Promise.all([
      fetchOne<Schedule[]>(() => api.get<Schedule[]>(`/schedules/period/${periodId}`), []),
      fetchOne<ConflictCheckResponse | null>(
        () => api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`),
        null
      ),
      fetchOne<CompensationDay[]>(
        () => api.get<CompensationDay[]>(`/schedules/compensation-days/${periodId}`),
        []
      ),
      fetchOne<ShiftRequirement[]>(
        () => api.get<ShiftRequirement[]>(`/shift-requirements/period/${periodId}`),
        []
      ),
    ]);

    if (!aliveRef.current) return;

    setSchedules(scheduleData);
    setConflictData(conflictResult);
    setCompensationDays(compDaysData);
    setRequirements(reqData);
    if (isRefresh) setRefreshing(false);
  }, []);

  // Bootstrap: load periods + staff + auto-select active period
  useEffect(() => {
    let active = true;

    const bootstrap = async () => {
      setLoading(true);
      setMessage(null);
      try {
        const [periodData, staffData, specialtyData] = await Promise.all([
          api.get<SchedulePeriod[]>("/periods"),
          api.get<Staff[]>("/staff/active"),
          api.get<Specialty[]>("/specialties").catch(() => [] as Specialty[]),
        ]);

        if (!active) return;

        const nextPeriods = periodData ?? [];
        setPeriods(nextPeriods);
        setActiveStaff(staffData ?? []);
        setSpecialties(specialtyData ?? []);

        if (autoSelectPeriod) {
          const preferred =
            nextPeriods.find((p) => p.status === "DRAFT" || p.status === "PUBLISHED") ??
            nextPeriods[0] ??
            null;
          const nextPeriodId = preferred?.id ?? null;
          setSelectedPeriodIdState(nextPeriodId);
          await loadPeriodData(nextPeriodId, false);
        }
      } catch (error) {
        if (!active) return;
        setMessage(getErrorMessage(error, "Không thể tải dữ liệu kỳ lịch."));
      } finally {
        if (active) setLoading(false);
      }
    };

    void bootstrap();
    return () => {
      active = false;
    };
  }, [autoSelectPeriod, loadPeriodData]);

  const setSelectedPeriodId = useCallback(
    (id: number | null) => {
      setSelectedPeriodIdState(id);
      void loadPeriodData(id, false);
    },
    [loadPeriodData]
  );

  const refresh = useCallback(async () => {
    const periodId = selectedPeriodId; // capture at call time to avoid stale closure
    setRefreshing(true);
    setMessage(null);
    try {
      const [periodData] = await Promise.all([
        api.get<SchedulePeriod[]>("/periods").catch(() => null),
      ]);
      if (!aliveRef.current) return;
      if (periodData) setPeriods(periodData);
      await loadPeriodData(periodId, false);
    } catch (error) {
      if (!aliveRef.current) return;
      setMessage(getErrorMessage(error, "Không thể làm mới dữ liệu."));
    } finally {
      if (aliveRef.current) setRefreshing(false);
    }
  }, [loadPeriodData]); // selectedPeriodId intentionally excluded — captured via closure above

  // Optional conflict polling cho dashboard realtime
  useEffect(() => {
    if (!conflictPollMs || conflictPollMs <= 0 || !selectedPeriodId) return;
    const ignoreRef = { current: false };
    const interval = setInterval(async () => {
      if (ignoreRef.current) return;
      try {
        const data = await api.get<ConflictCheckResponse>(
          `/schedules/conflicts/check/${selectedPeriodId}`
        );
        if (!ignoreRef.current) setConflictData(data);
      } catch {
        // silently skip polling errors
      }
    }, conflictPollMs);
    return () => {
      ignoreRef.current = true;
      clearInterval(interval);
    };
  }, [conflictPollMs, selectedPeriodId]);

  const clearMessage = useCallback(() => setMessage(null), []);
  const setMessageFn = useCallback((next: string | null) => setMessage(next), []);

  return {
    periods,
    selectedPeriodId,
    selectedPeriod,
    schedules,
    activeStaff,
    conflictData,
    compensationDays,
    requirements,
    specialties,
    loading,
    refreshing,
    message,
    setSelectedPeriodId,
    refresh,
    setMessage: setMessageFn,
    clearMessage,
  };
}