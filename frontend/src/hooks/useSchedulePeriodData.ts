"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { usePathname } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useAutoDismiss } from "@/hooks/useAutoDismiss";
import { queryCache, invalidateEndpoint } from "@/lib/queryCache";
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

  // Guard for aborting async work when component unmounts
  const aliveRef = useRef(true);

  // Keep loadPeriodData stable via ref to avoid stale closures
  const loadPeriodDataRef = useRef<typeof loadPeriodData | null>(null);

  const selectedPeriod = useMemo(
    () => periods.find((p) => p.id === selectedPeriodId) ?? null,
    [periods, selectedPeriodId]
  );

  const loadPeriodData = useCallback(async (periodId: number | null, isRefresh = false) => {
    if (!periodId) {
      setSchedules([]);
      setConflictData(null);
      setCompensationDays([]);
      setRequirements([]);
      return;
    }

    if (isRefresh) setRefreshing(true);

    // OPTIMIZATION: All data fetches run in PARALLEL - not sequentially
    const fetchOne = async <T,>(fetcher: () => Promise<T>, fallback: T): Promise<T> => {
      try {
        const result = await fetcher();
        return (result ?? fallback) as T;
      } catch {
        return fallback;
      }
    };

    // Fetch all data in parallel for maximum performance
    // Note: schedule and requirement APIs return paginated responses with Page object structure
    // We need to extract the content array from the response
    const [scheduleResult, conflictResult, compDaysData, reqData] = await Promise.all([
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

    // Extract schedules from paginated response if needed
    const scheduleData = Array.isArray(scheduleResult)
      ? scheduleResult
      : (scheduleResult && typeof scheduleResult === 'object' && 'content' in scheduleResult)
        ? (scheduleResult as { content?: Schedule[] }).content ?? []
        : [];

    // Extract requirements from paginated response if needed
    const reqResultData = Array.isArray(reqData)
      ? reqData
      : (reqData && typeof reqData === 'object' && 'content' in reqData)
        ? (reqData as { content?: ShiftRequirement[] }).content ?? []
        : [];

    // Batch all state updates into ONE render
    setSchedules(scheduleData);
    setConflictData(conflictResult);
    setCompensationDays(compDaysData);
    setRequirements(reqResultData);
    if (isRefresh) setRefreshing(false);
  }, []);

  // Keep ref in sync with loadPeriodData callback
  useEffect(() => {
    loadPeriodDataRef.current = loadPeriodData;
  }, [loadPeriodData]);

  // Auto-dismiss transient messages after 5s
  useAutoDismiss(message, () => setMessage(null));

  // Reset aliveRef and message on mount/unmount
  useEffect(() => {
    aliveRef.current = true;
    return () => {
      aliveRef.current = false;
    };
  }, []);

  // Clear stale banner on pathname change
  const pathname = usePathname();
  useEffect(() => {
    setMessage(null);
  }, [pathname]);

  // Listen for schedules-changed events to auto-refresh
  useEffect(() => {
    const handleSchedulesChanged = () => {
      invalidateEndpoint("/schedules");
      invalidateEndpoint(`/schedules/period/${selectedPeriodId}`);
      invalidateEndpoint(`/schedules/conflicts/check/${selectedPeriodId}`);
      invalidateEndpoint(`/schedules/compensation-days/${selectedPeriodId}`);
      if (selectedPeriodId && loadPeriodDataRef.current) {
        void loadPeriodDataRef.current(selectedPeriodId, true);
      }
    };
    if (typeof window !== "undefined") {
      window.addEventListener("schedules-changed", handleSchedulesChanged);
    }
    return () => {
      if (typeof window !== "undefined") {
        window.removeEventListener("schedules-changed", handleSchedulesChanged);
      }
    };
  }, [selectedPeriodId, invalidateEndpoint]);

  // Bootstrap: load periods + staff + auto-select active period
  useEffect(() => {
    let active = true;

    const bootstrap = async () => {
      setLoading(true);
      setMessage(null);
      try {
        // Use queryCache to deduplicate: if another component is already
        // fetching these, this call returns the same Promise.
        const [periodData, staffData, specialtyData] = await Promise.all([
          queryCache("/periods", () => api.get<SchedulePeriod[]>("/periods")),
          queryCache("/staff/active", () => api.get<Staff[]>("/staff/active")),
          queryCache("/specialties", () =>
            api.get<Specialty[]>("/specialties").catch(() => [] as Specialty[])
          ),
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
    // Capture periodId at call time to avoid stale closure issues
    const periodId = selectedPeriodId;
    setRefreshing(true);
    setMessage(null);
    try {
      // Fetch periods + schedule data in parallel for performance
      const [periodData, scheduleResult, conflictResult, compDaysData, reqData] = await Promise.all([
        api.get<SchedulePeriod[]>("/periods").catch(() => null),
        periodId ? api.get<Schedule[]>(`/schedules/period/${periodId}`).catch(() => null) : Promise.resolve(null),
        periodId ? api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`).catch(() => null) : Promise.resolve(null),
        periodId ? api.get<CompensationDay[]>(`/schedules/compensation-days/${periodId}`).catch(() => []) : Promise.resolve([]),
        periodId ? api.get<ShiftRequirement[]>(`/shift-requirements/period/${periodId}`).catch(() => []) : Promise.resolve([]),
      ]);

      if (!aliveRef.current) return;

      // Update periods if fetched successfully
      if (periodData) setPeriods(periodData);

      // Only update schedule data if periodId exists
      if (periodId) {
        // Extract schedules from paginated response if needed
        const scheduleData = Array.isArray(scheduleResult)
          ? scheduleResult
          : (scheduleResult && typeof scheduleResult === 'object' && 'content' in scheduleResult)
            ? (scheduleResult as { content?: Schedule[] }).content ?? []
            : [];

        // Extract requirements from paginated response if needed
        const reqResultData = Array.isArray(reqData)
          ? reqData
          : (reqData && typeof reqData === 'object' && 'content' in reqData)
            ? (reqData as { content?: ShiftRequirement[] }).content ?? []
            : [];

        setSchedules(scheduleData);
        setConflictData(conflictResult);
        setCompensationDays(compDaysData ?? []);
        setRequirements(reqResultData);
      }
    } catch (error) {
      if (!aliveRef.current) return;
      setMessage(getErrorMessage(error, "Không thể làm mới dữ liệu."));
    } finally {
      if (aliveRef.current) setRefreshing(false);
    }
  }, []);

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