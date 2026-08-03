"use client";

import { useCallback, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type {
  AutoScheduleResult,
  AutoScheduleSummary,
  Schedule,
  TemplatePreviewItem,
} from "@/types/api";

type PreviewScheduleEdit = {
  workDate: string;
  shiftTypeId: string;
  staffId: number;
  // BUGFIX (was M07 #8): carry the backend requirement id through the
  // edit→apply round-trip so the resolver can disambiguate L04 slots with
  // multiple specialties.
  requirementId?: number | null;
  // BUGFIX (BUG#1): set by editShiftType on a type change so the apply step can
  // drop the base slot at (workDate, oldShiftTypeId) that this edit replaces —
  // even when the staff changed at the same time.
  oldShiftTypeId?: string;
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
	  skipExisting: boolean;
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
  editStaff: (workDate: string, shiftTypeId: string, staffId: number, requirementId?: number | null) => void;
  editShiftType: (workDate: string, oldShiftTypeId: string, newShiftTypeId: string, staffId: number, requirementId?: number | null) => void;
  removeShift: (workDate: string, shiftTypeId: string, staffId: number) => void;
  resetEdits: () => void;
  clearPreview: () => void;
  clearMessage: () => void;
  setMessage: (msg: string) => void;
  setAlgorithmType: (type: "GREEDY" | "FAIR_GREEDY" | "CSP_MRV_FC" | "V10_LOCAL_SEARCH") => void;
  setHolidayMode: (mode: "SKIP" | "PARTIAL" | null) => void;
	  setMaxShiftsPerMonthOverride: (cap: number | null) => void;
	  setSkipExisting: (skip: boolean) => void;
	};

function parseScheduleKey(key: string): PreviewScheduleEdit | null {
  const [workDate, shiftTypeId, staffIdRaw] = key.split("_");
  const staffId = Number(staffIdRaw);
  if (!workDate || !shiftTypeId || !Number.isFinite(staffId)) return null;
  return { workDate, shiftTypeId, staffId };
}

/**
 * After an apply-template (PATTERN or GENERATED) the backend has only inserted
 * ShiftRequirement rows (or, for GENERATED, copied Schedule rows). The hook
 * re-fetches `/schedules/period/{id}` so the matrix grid renders correctly,
 * but the response is projected into the AutoScheduleResult shape used by
 * AutoSchedulePanel.
 *
 * KPI fields are intentionally left as `null` because applying a template is
 * a prerequisite to scheduling, not scheduling itself. Coverage/balance/conflict
 * would be misleading here, so the panel renders them as "—" until the user
 * clicks "Chạy" and the scheduler computes the real numbers.
 */
async function buildPreviewResultFromPeriod(
  periodId: number,
  algorithmType: string,
): Promise<AutoScheduleResult> {
  const res = await api.getSchedulesByPeriod(periodId);
  const schedules: Schedule[] = Array.isArray(res.data) ? res.data : [];
  const summary: AutoScheduleSummary[] = schedules.map((s) => ({
    scheduleId: s.id,
    staffId: s.staff.id,
    staffName: s.staff.fullName,
    workDate: s.workDate,
    shiftTypeId: s.shiftType.id,
    shiftTypeName: s.shiftType.name,
    staffSpecialtyName: s.staff.specialtyName ?? null,
    requirementId: s.requirementId ?? null,
  }));
  return {
    success: true,
    message: "Đã tải lịch đã áp dụng từ mẫu",
    periodId,
    algorithmType,
    executionTimeMs: 0,
    coverageRate: null,
    balanceScore: null,
    conflictCount: null,
    totalSchedulesCreated: summary.length,
    status: "TEMPLATE_APPLIED",
    schedules: summary,
    executedAt: new Date().toISOString(),
  };
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
	  const [skipExisting, setSkipExisting] = useState(false);

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
        skipExisting,
      }, { timeout: 600000 }); // 10 minute ceiling for the CSP partial path

      // Preserve user edits across re-runs: merge edited items back into the fresh
      // result so they carry their requirementId (needed for L04 multi-specialty
      // disambiguation) and are visible in the matrix grid alongside the new result.
      const editedKeys = new Set(editedPreview.map((e) => `${e.workDate}_${e.shiftTypeId}_${e.staffId}`));
      const mergedSchedules = [
        ...result.data.schedules,
        ...editedPreview.map((e) => ({
          scheduleId: null,
          staffId: e.staffId,
          staffName: "",
          workDate: e.workDate,
          shiftTypeId: e.shiftTypeId,
          shiftTypeName: "",
          requirementId: e.requirementId ?? null,
        })),
      ];
      const merged = { ...result.data, schedules: mergedSchedules };
      setPreviewResult(merged);
      // DON'T clear editedPreview here — edits survive re-runs so the user can
      // compare results and still apply their changes.
      setRemovedShifts(new Set());
      setRemovedShiftTypes(new Set());
    } catch (error) {
      console.error("[AutoSchedule] Error:", error);
      setMessage(getErrorMessage(error, "Không thể chạy auto schedule."));
    } finally {
      setRunning(false);
    }
  }, [algorithmType, holidayMode, maxShiftsPerMonthOverride, skipExisting]);

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
        // BUGFIX (coverage drift): pass overwriteExisting=true so the backend
        // is allowed to delete the existing 805 schedules for this period before
        // inserting the new preview. Without this flag the backend now throws
        // BadRequestException to protect manual assignments — which is the
        // correct behaviour but breaks the apply flow. The ConfirmDialog already
        // shown by the page gives the user the same opt-in moment.
        // BUGFIX (BUG#4): add 120s timeout for apply so the dialog "Đang áp
        // dụng..." is guaranteed to unstick even when the backend is slow to
        // persist a large schedule set or the network is congested.
        await api.applyPreview({
          periodId,
          algorithmType,
          schedules,
          removedSchedules,
          overwriteExisting: true,
        }, { timeout: 120000 });

        // BUGFIX (coverage drift): re-read the live coverage from the DB so the
        // success toast / metrics we surface reflect what was actually persisted,
        // not the cached algorithm_metrics.coverage_rate which can disagree
        // when an apply was interrupted or when successive runs overwrite each
        // other.
        let liveCoverageNote = "";
        try {
          const live = await api.getLiveCoverage(periodId);
          // BUGFIX (UX): show per-shift-type breakdown so the user can see which
          // shift type is understaffed instead of staring at a misleading
          // single low percentage.
          const breakdown = Object.values(live.byShiftType ?? {})
            .sort((a, b) => a.shiftTypeId.localeCompare(b.shiftTypeId))
            .map(
              (s) =>
                `${s.shiftTypeId}: ${s.assignedCount}/${s.requiredCapacity} (${s.coverageRate.toFixed(0)}%)`
            )
            .join(" · ");
          liveCoverageNote = ` — đã lưu ${live.totalSchedules}/${live.totalRequiredCapacity} ca (${live.coverageRate.toFixed(2)}% theo DB). ${breakdown}`;
        } catch (e) {
          console.warn("[applyPreview] live coverage fetch failed:", e);
        }

        setMessage(`Đã áp dụng phương án phân công${liveCoverageNote}.`);
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
        // BUGFIX (UX): surface the real backend reason verbatim so the user
        // can see WHY apply fails (e.g. "Có nhiều requirement cho (date, L04) —
        // client phải gửi requirementId"). Generic fallback only kicks in
        // when the error has no message at all.
        const detail = getErrorMessage(error, "Không thể áp dụng phương án.");
        console.error("[applyPreview] backend said:", detail, error);
        setMessage(`Lỗi áp dụng: ${detail}`);
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
        // PATTERN templates persist slot rows as shift_requirement (no staff
        // yet); GENERATED templates persist fully-staffed Schedule rows. Show
        // the correct verb so the count matches what the user can actually
        // inspect on screen.
        const suffix = appliedCount > 0
            ? " — " + appliedCount + " yêu cầu nhân sự được tạo. Nhấn \"Chạy\" để phân công."
            : ".";
        setMessage("Đã áp dụng mẫu lịch" + suffix);
        // Surface the just-created schedules in the matrix grid so the user
        // can see them immediately instead of getting a "1039 ca được tạo"
        // toast over an empty state.
        try {
          const preview = await buildPreviewResultFromPeriod(periodId, "TEMPLATE");
          setPreviewResult(preview);
          setEditedPreview([]);
          setRemovedShifts(new Set());
          setRemovedShiftTypes(new Set());
        } catch (loadErr) {
          console.error("[loadTemplate] Failed to refresh schedules:", loadErr);
        }
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
        const suffix = count > 0
            ? " — " + count + " yêu cầu nhân sự được tạo. Nhấn \"Chạy\" để phân công."
            : ".";
        setMessage("Đã áp dụng mẫu lịch với chỉnh sửa" + suffix);
        // Re-fetch the period so the matrix grid renders the freshly created
        // schedules (PATTERN templates persist shift_requirement rows instead,
        // which the user can fill by pressing "Chạy").
        try {
          const preview = await buildPreviewResultFromPeriod(periodId, "TEMPLATE");
          setPreviewResult(preview);
          setEditedPreview([]);
          setRemovedShifts(new Set());
          setRemovedShiftTypes(new Set());
        } catch (loadErr) {
          console.error("[applyTemplateWithEdits] Failed to refresh schedules:", loadErr);
        }
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
    (workDate: string, shiftTypeId: string, staffId: number, requirementId?: number | null) => {
      setEditedPreview((prev) => {
        const existing = prev.findIndex(
          (e) => e.workDate === workDate && e.shiftTypeId === shiftTypeId
        );
        if (existing >= 0) {
          return prev.map((e, i) =>
            i === existing ? { ...e, staffId } : e
          );
        }
        return [...prev, { workDate, shiftTypeId, staffId, requirementId: requirementId ?? null }];
      });
    },
    []
  );

  /**
   * Change the shift type of an existing (date, staff) assignment.
   * Removes any entry keyed by (workDate, oldShiftTypeId) and adds one keyed by (workDate, newShiftTypeId).
   * If newShiftTypeId is empty, removes the entry entirely.
   * The requirementId is preserved from the original preview item so the apply step
   * can pin the right ShiftRequirement when multiple L04 slots exist on the same date.
   */
  const editShiftType = useCallback(
    (workDate: string, oldShiftTypeId: string, newShiftTypeId: string, staffId: number, requirementId?: number | null) => {
      // BUGFIX (BUG#1): staff-only change (same shift type) must keep the
      // (workDate, shiftTypeId) slot key and just swap the staff. The old code
      // filtered the entry out and skipped the re-add, silently dropping the
      // edit so apply used the base entry (old staff).
      if (newShiftTypeId && newShiftTypeId === oldShiftTypeId) {
        setEditedPreview((prev) => {
          const existing = prev.findIndex(
            (e) => e.workDate === workDate && e.shiftTypeId === newShiftTypeId
          );
          if (existing >= 0) {
            return prev.map((e, i) => (i === existing ? { ...e, staffId } : e));
          }
          return [...prev, { workDate, shiftTypeId: newShiftTypeId, staffId, requirementId: requirementId ?? null }];
        });
        return;
      }
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
        if (newShiftTypeId) {
          return [...filtered, { workDate, shiftTypeId: newShiftTypeId, staffId, oldShiftTypeId, requirementId: requirementId ?? null }];
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
    { previewResult, editedPreview, removedShifts, removedShiftTypes, applying, running, message, algorithmType, holidayMode, maxShiftsPerMonthOverride, skipExisting },
    { runPreview, applyPreview, saveAsTemplate, loadTemplate, previewTemplate, applyTemplateWithEdits, editStaff, editShiftType, removeShift, resetEdits, clearPreview, clearMessage, setMessage: setMessage, setAlgorithmType: setAlgoType, setHolidayMode: setHolidayMode, setMaxShiftsPerMonthOverride, setSkipExisting },
  ];
}
