"use client";

import { memo, useCallback, useMemo, useState } from "react";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { SectionCard } from "@/components/ui/SectionCard";
import { Button } from "@/components/ui/Button";
import { useToast } from "@/components/ui/ToastProvider";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { BulkScheduleResponse, BulkScheduleResultEntry, CompensationDay, Staff } from "@/types/api";

export interface BulkScheduleModalProps {
  open: boolean;
  onClose: () => void;
  onSuccess: () => void;
  periodId: number;
  shiftTypeId: string;
  /** Staff that already have a schedule on the given date — skip them */
  existingSchedules: Array<{ workDate: string; staffId: number }>;
  staffList: Staff[];
  selectedDates: string[]; // ISO yyyy-MM-dd
  submitting: boolean;
  onSubmittingChange?: (v: boolean) => void;
  /** Compensation days to validate against — staff on comp day are disabled */
  compensationDays?: CompensationDay[];
}

type Phase = "prepare" | "results";

interface DateAssignment {
  workDate: string; // ISO yyyy-MM-dd
  staffId: number | null;
}

const DATE_OPTIONS: Intl.DateTimeFormatOptions = {
  weekday: "short",
  day: "2-digit",
  month: "2-digit",
};

function formatDateLabel(dateStr: string): string {
  return new Date(dateStr + "T00:00:00").toLocaleDateString("vi-VN", DATE_OPTIONS);
}

function buildStaffNameMap(staffList: Staff[]): Map<number, string> {
  return new Map(staffList.map((s) => [s.id, s.fullName]));
}

function buildExistingSet(
  existingSchedules: Array<{ workDate: string; staffId: number }>
): Set<string> {
  return new Set(existingSchedules.map((e) => `${e.workDate}::${e.staffId}`));
}

export const BulkScheduleModal = memo(function BulkScheduleModal({
  open,
  onClose,
  onSuccess,
  periodId,
  shiftTypeId,
  existingSchedules,
  staffList,
  selectedDates,
  submitting,
  onSubmittingChange,
  compensationDays,
}: BulkScheduleModalProps) {
  const { error: toastError, success: toastSuccess } = useToast();
  const [phase, setPhase] = useState<Phase>("prepare");
  const [assignments, setAssignments] = useState<DateAssignment[]>(() =>
    selectedDates.map((d) => ({ workDate: d, staffId: null }))
  );
  const [result, setResult] = useState<BulkScheduleResponse | null>(null);
  const [submitError, setSubmitError] = useState<string | null>(null);

  const staffNameMap = useMemo(() => buildStaffNameMap(staffList), [staffList]);
  const existingSet = useMemo(() => buildExistingSet(existingSchedules), [existingSchedules]);
  const compDaySet = useMemo(() => {
    const s = new Set<string>();
    if (!compensationDays) return s;
    for (const cd of compensationDays) {
      s.add(`${cd.staffId}::${cd.compensationDate.split("T")[0]}`);
    }
    return s;
  }, [compensationDays]);

  const handleAssignmentChange = useCallback(
    (index: number, staffId: number | null) => {
      setAssignments((prev) => {
        const next = [...prev];
        next[index] = { ...next[index], staffId };
        return next;
      });
    },
    []
  );

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();

    const entries = assignments
      .filter((a) => a.staffId !== null)
      .map((a) => ({
        workDate: a.workDate,
        staffId: a.staffId as number,
      }));

    if (entries.length === 0) {
      setSubmitError("Vui lòng chọn ít nhất một nhân sự cho một ngày.");
      return;
    }

    onSubmittingChange?.(true);
    setSubmitError(null);

    try {
      const response = await api.bulkCreateSchedules(
        { periodId, entries },
        shiftTypeId
      );
      setResult(response);
      setPhase("results");

      if (response.successCount > 0) {
        toastSuccess(`Đã tạo ${response.successCount} lịch thành công.`);
      }
      if (response.failureCount > 0) {
        toastError(`${response.failureCount} lịch thất bại. Xem chi tiết bên dưới.`, 6000);
      }
    } catch (err) {
      const msg = getErrorMessage(err, "Không thể tạo lịch hàng loạt. Vui lòng thử lại.");
      setSubmitError(msg);
      toastError(msg);
    } finally {
      onSubmittingChange?.(false);
    }
  };

  const handleClose = () => {
    setPhase("prepare");
    setAssignments(selectedDates.map((d) => ({ workDate: d, staffId: null })));
    setResult(null);
    setSubmitError(null);
    onClose();
  };

  const handleDone = () => {
    if (result?.successCount && result.successCount > 0) {
      onSuccess();
    }
    handleClose();
  };

  const handleRetry = () => {
    setPhase("prepare");
    setResult(null);
    setSubmitError(null);
  };

  const totalSelected = assignments.length;
  const totalAssigned = assignments.filter((a) => a.staffId !== null).length;

  return (
    <Modal
      open={open}
      onClose={handleClose}
      title={phase === "prepare" ? "Gán lịch hàng loạt" : "Kết quả gán lịch"}
      description={
        phase === "prepare"
          ? `Đang gán cho ${totalSelected} ngày · ${totalAssigned} đã chọn nhân sự`
          : undefined
      }
      size="xl"
    >
      {phase === "prepare" ? (
        <form onSubmit={handleSubmit} className="space-y-4">
          {selectedDates.length === 0 ? (
            <div className="flex flex-col items-center justify-center py-16 gap-4">
              <span
                aria-hidden="true"
                className="material-symbols-outlined text-5xl text-outline"
              >
                event_busy
              </span>
              <p className="text-label-md text-on-surface-variant">
                Chưa chọn ngày nào để gán lịch.
              </p>
            </div>
          ) : (
            <SectionCard
              title={
                <span className="text-label-md">
                  Bảng gán nhân sự — {selectedDates.length} ngày
                </span>
              }
              description={
                <span className="text-label-sm text-on-surface-variant">
                  Chọn nhân sự cho từng ngày. Nhân sự đã có lịch trong ngày sẽ bị bỏ qua.
                </span>
              }
            >
              <div className="overflow-x-auto">
                <table className="w-full text-left border-collapse" aria-label="Bulkschedulemodal Table">
                  <thead>
                    <tr className="bg-surface-container-low border-b border-outline-variant">
                      <th scope="col" className="py-2.5 px-4 text-label-sm text-on-surface-variant uppercase w-40">
                        Ngày
                      </th>
                      <th scope="col" className="py-2.5 px-4 text-label-sm text-on-surface-variant uppercase">
                        Nhân sự
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant">
                    {assignments.map((assignment, idx) => {
                      const isSkipped = assignment.staffId
                        ? existingSet.has(
                            `${assignment.workDate}::${assignment.staffId}`
                          )
                        : false;
                      const currentIsCompDay = assignment.staffId
                        ? compDaySet.has(`${assignment.staffId}::${assignment.workDate}`)
                        : false;

                      return (
                        <tr
                          key={assignment.workDate}
                          className="hover:bg-surface-container-lowest transition-colors h-12"
                        >
                          <td className="py-2 px-4">
                            <span className="text-label-md font-semibold text-on-surface">
                              {formatDateLabel(assignment.workDate)}
                            </span>
                          </td>
                          <td className="py-2 px-4">
                            <div className="relative max-w-xs">
                              <select
                                value={assignment.staffId ?? ""}
                                onChange={(e) =>
                                  handleAssignmentChange(
                                    idx,
                                    e.target.value ? Number(e.target.value) : null
                                  )
                                }
                                disabled={submitting}
                                className={[
                                  "w-full h-9 appearance-none rounded-lg border pl-3 pr-8",
                                  "text-label-md text-on-surface outline-none transition-colors",
                                  "focus:ring-1 focus:ring-primary/20 focus:border-primary",
                                  isSkipped
                                    ? "border-tertiary/40 bg-tertiary/5 cursor-not-allowed opacity-70"
                                    : "border-outline-variant bg-surface-container-lowest cursor-pointer",
                                ].join(" ")}
                              >
                                <option value="">— Chưa chọn —</option>
                                {staffList.map((s) => {
                                  const alreadyScheduled = existingSet.has(
                                    `${assignment.workDate}::${s.id}`
                                  );
                                  const isCompDay = compDaySet.has(
                                    `${s.id}::${assignment.workDate}`
                                  );
                                  const isDisabled = alreadyScheduled || isCompDay;
                                  return (
                                    <option
                                      key={s.id}
                                      value={s.id}
                                      disabled={isDisabled}
                                    >
                                      {alreadyScheduled
                                        ? `${s.fullName} (đã có lịch)`
                                        : isCompDay
                                        ? `${s.fullName} (nghỉ bù)`
                                        : s.fullName}
                                    </option>
                                  );
                                })}
                              </select>
                              <span
                                aria-hidden="true"
                                className="material-symbols-outlined pointer-events-none absolute right-2 top-1/2 -translate-y-1/2 text-outline text-[18px]"
                              >
                                expand_more
                              </span>
                            </div>
                            {isSkipped && (
                              <p className="mt-1 flex items-center gap-1 text-label-sm text-tertiary">
                                <span
                                  aria-hidden="true"
                                  className="material-symbols-outlined text-[14px]"
                                >
                                  warning
                                </span>
                                Nhân sự đã có lịch — sẽ bị bỏ qua
                              </p>
                            )}
                            {currentIsCompDay && (
                              <p className="mt-1 flex items-center gap-1 text-label-sm text-error">
                                <span
                                  aria-hidden="true"
                                  className="material-symbols-outlined text-[14px]"
                                >
                                  hotel
                                </span>
                                Nhân sự đang nghỉ bù ngày này — không thể gán
                              </p>
                            )}
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            </SectionCard>
          )}

          {submitError && (
            <div
              className="flex items-center gap-2 rounded-lg border border-error/20 bg-error-container px-4 py-2.5 text-label-sm text-on-error-container"
              role="alert"
            >
              <span aria-hidden="true" className="material-symbols-outlined text-[16px]">
                error
              </span>
              {submitError}
            </div>
          )}

          <ModalFooter>
            <Button type="button" variant="secondary" onClick={handleClose} disabled={submitting}>
              Hủy
            </Button>
            <Button
              type="submit"
              variant="primary"
              loading={submitting}
              disabled={selectedDates.length === 0 || totalAssigned === 0}
              icon={
                <span aria-hidden="true" className="material-symbols-outlined">
                  playlist_add
                </span>
              }
            >
              Gán {totalAssigned > 0 ? `${totalAssigned} ` : ""}lịch
            </Button>
          </ModalFooter>
        </form>
      ) : (
        <div className="space-y-4">
          {/* Dual-stat tiles */}
          {result && (
            <div className="grid grid-cols-2 gap-3">
              <div className="rounded-xl border border-secondary/30 bg-secondary-container p-4">
                <div className="flex items-center gap-2 mb-1">
                  <span
                    aria-hidden="true"
                    className="material-symbols-outlined text-[18px] text-secondary"
                    style={{ fontVariationSettings: "'FILL' 1" }}
                  >
                    check_circle
                  </span>
                  <span className="text-label-sm font-medium text-on-secondary-container">
                    Thành công
                  </span>
                </div>
                <p className="text-headline-md font-bold text-on-secondary-container">
                  {result.successCount}
                </p>
                <p className="text-label-sm text-on-secondary-container/70">
                  trên {result.totalRequested} yêu cầu
                </p>
              </div>

              <div className="rounded-xl border border-error/30 bg-error-container p-4">
                <div className="flex items-center gap-2 mb-1">
                  <span
                    aria-hidden="true"
                    className="material-symbols-outlined text-[18px] text-error"
                    style={{ fontVariationSettings: "'FILL' 1" }}
                  >
                    error
                  </span>
                  <span className="text-label-sm font-medium text-on-error-container">
                    Thất bại
                  </span>
                </div>
                <p className="text-headline-md font-bold text-on-error-container">
                  {result.failureCount}
                </p>
                <p className="text-label-sm text-on-error-container/70">
                  cần xem xét lại
                </p>
              </div>
            </div>
          )}

          {/* Scrollable result list */}
          {result && result.results.length > 0 && (
            <SectionCard
              title={<span className="text-label-md">Chi tiết kết quả</span>}
            >
              <div className="max-h-72 overflow-y-auto">
                <table className="w-full text-left border-collapse" aria-label="Bulkschedulemodal Table">
                  <thead className="sticky top-0 bg-surface-container-low z-10">
                    <tr className="border-b border-outline-variant">
                      <th scope="col" className="py-2.5 px-4 text-label-sm text-on-surface-variant uppercase w-40">
                        Ngày
                      </th>
                      <th scope="col" className="py-2.5 px-4 text-label-sm text-on-surface-variant uppercase">
                        Nhân sự
                      </th>
                      <th scope="col" className="py-2.5 px-4 text-label-sm text-on-surface-variant uppercase w-28 text-center">
                        Trạng thái
                      </th>
                      <th scope="col" className="py-2.5 px-4 text-label-sm text-on-surface-variant uppercase">
                        Ghi chú lỗi
                      </th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant">
                    {result.results.map((r: BulkScheduleResultEntry, idx: number) => (
                      <tr
                        key={idx}
                        className="hover:bg-surface-container-lowest transition-colors h-12"
                      >
                        <td className="py-2 px-4 text-label-md text-on-surface">
                          {formatDateLabel(r.workDate)}
                        </td>
                        <td className="py-2 px-4 text-label-md text-on-surface">
                          {r.staffName ?? staffNameMap.get(r.staffId) ?? `ID ${r.staffId}`}
                        </td>
                        <td className="py-2 px-4 text-center">
                          {r.scheduleId !== null ? (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-secondary-container text-on-secondary-container text-label-sm font-semibold">
                              <span
                                aria-hidden="true"
                                className="material-symbols-outlined text-[14px] text-secondary"
                                style={{ fontVariationSettings: "'FILL' 1" }}
                              >
                                check_circle
                              </span>
                              OK
                            </span>
                          ) : (
                            <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-error-container text-on-error-container text-label-sm font-semibold">
                              <span
                                aria-hidden="true"
                                className="material-symbols-outlined text-[14px] text-error"
                                style={{ fontVariationSettings: "'FILL' 1" }}
                              >
                                error
                              </span>
                              Lỗi
                            </span>
                          )}
                        </td>
                        <td className="py-2 px-4 text-label-sm text-on-surface-variant">
                          {r.error ?? "—"}
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            </SectionCard>
          )}

          <ModalFooter>
            <Button type="button" variant="secondary" onClick={handleRetry}>
              Gán lại
            </Button>
            <Button
              type="button"
              variant="primary"
              onClick={handleDone}
              icon={
                <span aria-hidden="true" className="material-symbols-outlined">
                  check
                </span>
              }
            >
              Xong
            </Button>
          </ModalFooter>
        </div>
      )}
    </Modal>
  );
});
