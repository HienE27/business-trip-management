"use client";

import { useCallback, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { AutoScheduleResult, TemplatePreviewItem } from "@/types/api";

type PreviewScheduleEdit = {
  workDate: string;
  shiftTypeId: string;
  staffId: number;
  // BUGFIX (was M07 #8): carry the backend requirement id through the
  // edit→apply round-trip so the resolver can disambiguate L04 slots with
  // multiple specialties.
  requirementId?: number | null;
};

export type AutoScheduleState = {
  previewResult: AutoScheduleResult | null;
  editedPreview: PreviewScheduleEdit[];
  removedShifts: Set<string>;
  removedShiftTypes: Set<string>;
  applying: boolean;
  running: boolean;
  message: string | null;
  algorithmType: "GREEDY" | "FAIR_GREEDY" | "CSP_MRV_FC" | "V10_LOCAL_SEARCH";
  holidayMode: "SKIP" | "PARTIAL" | null;
  /** Runtime override for max_shifts_per_month. null = use DB cap per staff. */
  maxShiftsPerMonthOverride: number | null;
};

export type AutoScheduleActions = {
  runPreview: (periodId: number | null, excludedStaffIds?: number[]) => Promise<void>;
  applyPreview: (
    periodId: number | null,
    edited: PreviewScheduleEdit[],
    onSuccess: () => void
  ) => Promise<void>;
  saveAsTemplate: (
    periodId: number | null,
    templateName: string,
    description?: string
  ) => Promise<void>;
  loadTemplate: (templateId: number, periodId: number | null) => Promise<void>;
  previewTemplate: (templateId: number, periodId: number | null) => Promise<TemplatePreviewItem[]>;
  applyTemplateWithEdits: (templateId: number, periodId: number | null, edits: { slotId: number; assignedStaffId: number }[]) => Promise<void>;
  editStaff: (workDate: string, shiftTypeId: string, staffId: number) => void;
  editShiftType: (workDate: string, oldShiftTypeId: string, newShiftTypeId: string, staffId: number) => void;
  removeShift: (workDate: string, shiftTypeId: string, staffId: number) => void;
  resetEdits: () => void;
  clearPreview: () => void;
  clearMessage: () => void;
  setMessage: (msg: string) => void;
  setAlgorithmType: (type: "GREEDY" | "FAIR_GREEDY" | "CSP_MRV_FC" | "V10_LOCAL_SEARCH") => void;
  setHolidayMode: (mode: "SKIP" | "PARTIAL" | null) => void;
  setMaxShiftsPerMonthOverride: (cap: number | null) => void;
};

function parseScheduleKey(key: string): PreviewScheduleEdit | null {
  const [workDate, shiftTypeId, staffIdRaw] = key.split("_");
  const staffId = Number(staffIdRaw);
  if (!workDate || !shiftTypeId || !Number.isFinite(staffId)) return null;
  return { workDate, shiftTypeId, staffId };
}

export function useAutoSchedule(): [AutoScheduleState, AutoScheduleActions] {
  const [previewResult, setPreviewResult] = useState<AutoScheduleResult | null>(null);
  const [editedPreview, setEditedPreview] = useState<PreviewScheduleEdit[]>([]);
  const [removedShifts, setRemovedShifts] = useState<Set<string>>(new Set());
  const [removedShiftTypes, setRemovedShiftTypes] = useState<Set<string>>(new Set());
  const [applying, setApplying] = useState(false);
  const [running, setRunning] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [algorithmType, setAlgorithmType] = useState<"GREEDY" | "FAIR_GREEDY" | "CSP_MRV_FC" | "V10_LOCAL_SEARCH">("V10_LOCAL_SEARCH");
  const [holidayMode, setHolidayMode] = useState<"SKIP" | "PARTIAL" | null>(null);
  const [maxShiftsPerMonthOverride, setMaxShiftsPerMonthOverride] = useState<number | null>(null);

  const runPreview = useCallback(async (periodId: number | null, excludedStaffIds?: number[]) => {
    if (!periodId) return;
    try {
      setRunning(true);
      setMessage(null);

      const result = await api.previewAutoSchedule({
        periodId,
        algorithmType,
        excludedStaffIds: excludedStaffIds && excludedStaffIds.length > 0 ? excludedStaffIds : undefined,
        holidayMode: holidayMode ?? undefined,
        maxShiftsPerMonthOverride,
      }, { timeout: 600000 }); // 10 minute ceiling for the CSP partial path

      setPreviewResult(result.data);
      setEditedPreview([]);
      setRemovedShifts(new Set());
      setRemovedShiftTypes(new Set());
    } catch (error) {
      console.error("[AutoSchedule] Error:", error);
      setMessage(getErrorMessage(error, "Không thể chạy auto schedule."));
    } finally {
      setRunning(false);
    }
  }, [algorithmType, holidayMode, maxShiftsPerMonthOverride]);

  const applyPreview = useCallback(
    async (
      periodId: number | null,
      edited: PreviewScheduleEdit[],
      onSuccess: () => void
    ) => {
      if (!periodId) return;
      try {
        setApplying(true);
        setMessage(null);
        const removedSchedules = [...removedShifts, ...removedShiftTypes]
          .map(parseScheduleKey)
          .filter((item): item is PreviewScheduleEdit => item !== null);
        const schedules = edited.length > 0
          ? edited
          : (previewResult?.schedules ?? []).map((s) => ({
              workDate: s.workDate,
              shiftTypeId: s.shiftTypeId,
              staffId: s.staffId,
              // BUGFIX (was M07 #8): forward the requirementId emitted by the
              // backend so the resolver picks the right (date, shiftType)
              // requirement even when L04 has multiple specialties.
              requirementId: s.requirementId ?? null,
            }));
        await api.applyPreview({ periodId, algorithmType, schedules, removedSchedules });
        setMessage("Đã áp dụng phương án phân công.");
        setPreviewResult(null);
        setEditedPreview([]);
        setRemovedShifts(new Set());
        setRemovedShiftTypes(new Set());
        // Dispatch schedules-changed to notify other components (e.g., monthly-schedule page)
        if (typeof window !== "undefined") {
          window.dispatchEvent(new Event("schedules-changed"));
        }
        onSuccess();
      } catch (error) {
        console.error("[applyPreview] Error:", error);
        setMessage(getErrorMessage(error, "Không thể áp dụng phương án."));
      } finally {
        setApplying(false);
      }
    },
    [previewResult, removedShifts, removedShiftTypes, algorithmType]
  );

  const saveAsTemplate = useCallback(
    async (periodId: number | null, templateName: string, description?: string) => {
      if (!periodId || !previewResult) return;
      try {
        setMessage(null);
        const scheduleIds = previewResult.schedules.map((s) => s.scheduleId).filter(Boolean) as number[];
        await api.saveScheduleTemplate({ periodId, templateName, description: description ?? "", algorithmType, scheduleIds });
        setMessage("Đã lưu mẫu lịch '" + templateName + "' thành công.");
      } catch (error) {
        setMessage(getErrorMessage(error, "Không thể lưu mẫu lịch."));
      }
    },
    [previewResult, algorithmType]
  );

  const loadTemplate = useCallback(
    async (templateId: number, periodId: number | null) => {
      if (!periodId) return;
      try {
        setMessage(null);
        const result = await api.applyTemplate(templateId, periodId);
        const appliedCount = result.data?.appliedCount ?? 0;
        setMessage("Đã áp dụng mẫu lịch — " + appliedCount + " ca được tạo.");
        if (typeof window !== "undefined") {
          window.dispatchEvent(new Event("schedules-changed"));
        }
      } catch (error) {
        setMessage(getErrorMessage(error, "Không thể áp dụng mẫu lịch."));
      }
    },
    []
  );

  const previewTemplate = useCallback(
    async (templateId: number, periodId: number | null) => {
      if (!periodId) return [];
      try {
        setMessage(null);
        const data = await api.previewTemplate(templateId, periodId);
        return data.data ?? [];
      } catch (error) {
        setMessage(getErrorMessage(error, "Không thể xem trước mẫu lịch."));
        return [];
      }
    },
    []
  );

  const applyTemplateWithEdits = useCallback(
    async (templateId: number, periodId: number | null, edits: { slotId: number; assignedStaffId: number }[]) => {
      if (!periodId) return;
      try {
        setApplying(true);
        setMessage(null);
        const result = await api.applyTemplateWithEdits(templateId, periodId, edits);
        const count = result.data?.appliedCount ?? 0;
        setMessage("Đã áp dụng mẫu lịch với chỉnh sửa — " + count + " ca được tạo.");
        if (typeof window !== "undefined") {
          window.dispatchEvent(new Event("schedules-changed"));
        }
      } catch (error) {
        setMessage(getErrorMessage(error, "Không thể áp dụng mẫu lịch với chỉnh sửa."));
      } finally {
        setApplying(false);
      }
    },
    []
  );

  const editStaff = useCallback(
    (workDate: string, shiftTypeId: string, staffId: number) => {
      setEditedPreview((prev) => {
        const existing = prev.findIndex(
          (e) => e.workDate === workDate && e.shiftTypeId === shiftTypeId
        );
        if (existing >= 0) {
          return prev.map((e, i) =>
            i === existing ? { ...e, staffId } : e
          );
        }
        return [...prev, { workDate, shiftTypeId, staffId }];
      });
    },
    []
  );

  /**
   * Change the shift type of an existing (date, staff) assignment.
   * Removes any entry keyed by (workDate, oldShiftTypeId) and adds one keyed by (workDate, newShiftTypeId).
   * If newShiftTypeId is empty, removes the entry entirely.
   */
  const editShiftType = useCallback(
    (workDate: string, oldShiftTypeId: string, newShiftTypeId: string, staffId: number) => {
      const removeKey = `${workDate}_${oldShiftTypeId}_${staffId}`;
      setRemovedShiftTypes((prev) => {
        const next = new Set(prev);
        next.add(removeKey);
        return next;
      });
      setEditedPreview((prev) => {
        const filtered = prev.filter(
          (e) => !(e.workDate === workDate && e.shiftTypeId === oldShiftTypeId)
        );
        if (newShiftTypeId && newShiftTypeId !== oldShiftTypeId) {
          return [...filtered, { workDate, shiftTypeId: newShiftTypeId, staffId }];
        }
        return filtered;
      });
    },
    []
  );

  const removeShift = useCallback((workDate: string, shiftTypeId: string, staffId: number) => {
    setRemovedShifts((prev) => {
      const next = new Set(prev);
      next.add(`${workDate}_${shiftTypeId}_${staffId}`);
      return next;
    });
  }, []);

  const resetEdits = useCallback(() => {
    setEditedPreview([]);
    setRemovedShifts(new Set());
    setRemovedShiftTypes(new Set());
    setMessage("Đã hủy thay đổi.");
  }, []);

  const clearPreview = useCallback(() => {
    setPreviewResult(null);
    setEditedPreview([]);
    setRemovedShifts(new Set());
    setRemovedShiftTypes(new Set());
  }, []);

  const clearMessage = useCallback(() => setMessage(null), []);
  const setAlgoType = useCallback((type: "GREEDY" | "FAIR_GREEDY" | "CSP_MRV_FC" | "V10_LOCAL_SEARCH") => {
    setAlgorithmType(type);
  }, [setAlgorithmType]);

  return [
    { previewResult, editedPreview, removedShifts, removedShiftTypes, applying, running, message, algorithmType, holidayMode, maxShiftsPerMonthOverride },
    { runPreview, applyPreview, saveAsTemplate, loadTemplate, previewTemplate, applyTemplateWithEdits, editStaff, editShiftType, removeShift, resetEdits, clearPreview, clearMessage, setMessage: setMessage, setAlgorithmType: setAlgoType, setHolidayMode: setHolidayMode, setMaxShiftsPerMonthOverride },
  ];
}
