"use client";

import { useCallback, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { AutoScheduleResult, TemplatePreviewItem } from "@/types/api";

export type AutoScheduleState = {
  previewResult: AutoScheduleResult | null;
  editedPreview: Array<{ workDate: string; shiftTypeId: string; staffId: number }>;
  removedShiftTypes: Set<string>;
  applying: boolean;
  running: boolean;
  message: string | null;
  algorithmType: "GREEDY" | "ROUND_ROBIN" | "BACKTRACKING";
};

export type AutoScheduleActions = {
  runPreview: (periodId: number | null, excludedStaffIds?: number[]) => Promise<void>;
  applyPreview: (
    periodId: number | null,
    edited: Array<{ workDate: string; shiftTypeId: string; staffId: number }>,
    onSuccess: () => void
  ) => Promise<void>;
  saveAsTemplate: (
    periodId: number | null,
    templateName: string,
    description?: string
  ) => Promise<void>;
  loadTemplate: (templateId: number, periodId: number | null) => Promise<void>;
  previewTemplate: (templateId: number, periodId: number | null) => Promise<TemplatePreviewItem[]>;
  applyTemplateWithEdits: (templateId: number, periodId: number, edits: TemplatePreviewItem[]) => Promise<void>;
  editStaff: (workDate: string, shiftTypeId: string, staffId: number) => void;
  editShiftType: (workDate: string, oldShiftTypeId: string, newShiftTypeId: string, staffId: number) => void;
  resetEdits: () => void;
  clearPreview: () => void;
  clearMessage: () => void;
  setMessage: (msg: string) => void;
  setAlgorithmType: (type: "GREEDY" | "ROUND_ROBIN" | "BACKTRACKING") => void;
};

export function useAutoSchedule(): [AutoScheduleState, AutoScheduleActions] {
  const [previewResult, setPreviewResult] = useState<AutoScheduleResult | null>(null);
  const [editedPreview, setEditedPreview] = useState<Array<{ workDate: string; shiftTypeId: string; staffId: number }>>([]);
  const [removedShiftTypes, setRemovedShiftTypes] = useState<Set<string>>(new Set());
  const [applying, setApplying] = useState(false);
  const [running, setRunning] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [algorithmType, setAlgorithmType] = useState<"GREEDY" | "ROUND_ROBIN" | "BACKTRACKING">("GREEDY");

  const runPreview = useCallback(async (periodId: number | null) => {
    if (!periodId) return;
    try {
      setRunning(true);
      setMessage(null);
      const result = await api.previewAutoSchedule({
        periodId,
        algorithmType,
        maxIterations: 1000,
      });
      setPreviewResult(result.data);
      setEditedPreview([]);
      setRemovedShiftTypes(new Set());
    } catch (error) {
      setMessage(getErrorMessage(error, "Không thể chạy auto schedule."));
    } finally {
      setRunning(false);
    }
  }, [algorithmType]);

  const applyPreview = useCallback(
    async (
      periodId: number | null,
      edited: Array<{ workDate: string; shiftTypeId: string; staffId: number }>,
      onSuccess: () => void
    ) => {
      if (!periodId || !previewResult) return;
      try {
        setApplying(true);
        setMessage(null);
        const schedules = edited.length > 0
          ? edited
          : previewResult.schedules.map((s) => ({ workDate: s.workDate, shiftTypeId: s.shiftTypeId, staffId: s.staffId }));
        await api.applyPreview({ periodId, algorithmType, schedules });
        setMessage("Đã áp dụng phương án phân công.");
        setPreviewResult(null);
        setEditedPreview([]);
        setRemovedShiftTypes(new Set());
        onSuccess();
      } catch (error) {
        setMessage(getErrorMessage(error, "Không thể áp dụng phương án."));
      } finally {
        setApplying(false);
      }
    },
    [previewResult, algorithmType]
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
    async (templateId: number, periodId: number | null, _edits: TemplatePreviewItem[]) => {
      if (!periodId) return;
      try {
        setMessage(null);
        const result = await api.applyTemplate(templateId, periodId);
        const count = result.data?.appliedCount ?? 0;
        setMessage("Đã áp dụng mẫu lịch — " + count + " ca được tạo.");
        if (typeof window !== "undefined") {
          window.dispatchEvent(new Event("schedules-changed"));
        }
      } catch (error) {
        setMessage(getErrorMessage(error, "Không thể áp dụng mẫu lịch."));
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

  const resetEdits = useCallback(() => {
    setEditedPreview([]);
    setRemovedShiftTypes(new Set());
    setMessage("Đã hủy thay đổi.");
  }, []);

  const clearPreview = useCallback(() => {
    setPreviewResult(null);
    setEditedPreview([]);
    setRemovedShiftTypes(new Set());
  }, []);

  const clearMessage = useCallback(() => setMessage(null), []);
  const setAlgoType = useCallback((type: "GREEDY" | "ROUND_ROBIN" | "BACKTRACKING") => {
    setAlgorithmType(type);
  }, []);

  return [
    { previewResult, editedPreview, removedShiftTypes, applying, running, message, algorithmType },
    { runPreview, applyPreview, saveAsTemplate, loadTemplate, previewTemplate, applyTemplateWithEdits, editStaff, editShiftType, resetEdits, clearPreview, clearMessage, setMessage: setMessage, setAlgorithmType: setAlgoType },
  ];
}
