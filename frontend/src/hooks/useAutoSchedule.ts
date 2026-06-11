"use client";

import { useCallback, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { AutoScheduleResult } from "@/types/api";

export type AutoScheduleState = {
  previewResult: AutoScheduleResult | null;
  editedPreview: Array<{ workDate: string; shiftTypeId: string; staffId: number }>;
  applying: boolean;
  running: boolean;
  message: string | null;
  algorithmType: "GREEDY" | "ROUND_ROBIN" | "BACKTRACKING";
};

export type AutoScheduleActions = {
  runPreview: (periodId: number | null) => Promise<void>;
  applyPreview: (
    periodId: number | null,
    edited: Array<{ workDate: string; shiftTypeId: string; staffId: number }>,
    onSuccess: () => void
  ) => Promise<void>;
  editStaff: (workDate: string, shiftTypeId: string, staffId: number) => void;
  resetEdits: () => void;
  clearMessage: () => void;
  setAlgorithmType: (type: "GREEDY" | "ROUND_ROBIN" | "BACKTRACKING") => void;
};

export function useAutoSchedule(): [AutoScheduleState, AutoScheduleActions] {
  const [previewResult, setPreviewResult] = useState<AutoScheduleResult | null>(null);
  const [editedPreview, setEditedPreview] = useState<Array<{ workDate: string; shiftTypeId: string; staffId: number }>>([]);
  const [applying, setApplying] = useState(false);
  const [running, setRunning] = useState(false);
  const [message, setMessage] = useState<string | null>(null);
  const [algorithmType, setAlgorithmType] = useState<"GREEDY" | "ROUND_ROBIN" | "BACKTRACKING">("GREEDY");

  const runPreview = useCallback(async (periodId: number | null) => {
    if (!periodId) return;
    try {
      setRunning(true);
      setMessage(null);
      const result = await api.post<AutoScheduleResult>("/auto-schedule/preview", {
        periodId,
        algorithmType,
        maxIterations: 1000,
        excludedStaffIds: [],
      });
      setPreviewResult(result);
      setEditedPreview([]);
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
        await api.post("/auto-schedule/apply-preview", {
          periodId,
          algorithmType,
          schedules: edited.length > 0 ? edited : previewResult.schedules
            .filter((s) => s.scheduleId != null)
            .map((s) => ({ workDate: s.workDate, shiftTypeId: s.shiftTypeId, staffId: s.staffId })),
        });
        setMessage("Đã áp dụng phương án phân công.");
        setPreviewResult(null);
        setEditedPreview([]);
        onSuccess();
      } catch (error) {
        setMessage(getErrorMessage(error, "Không thể áp dụng phương án."));
      } finally {
        setApplying(false);
      }
    },
    [previewResult, algorithmType]
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

  const resetEdits = useCallback(() => {
    setEditedPreview([]);
    setMessage("Đã hủy thay đổi.");
  }, []);

  const clearMessage = useCallback(() => setMessage(null), []);
  const setAlgoType = useCallback((type: "GREEDY" | "ROUND_ROBIN" | "BACKTRACKING") => {
    setAlgorithmType(type);
  }, []);

  return [
    { previewResult, editedPreview, applying, running, message, algorithmType },
    { runPreview, applyPreview, editStaff, resetEdits, clearMessage, setAlgorithmType: setAlgoType },
  ];
}
