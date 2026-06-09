'use client';

import { useCallback, useEffect, useMemo, useState } from "react";
import { ConflictInspector } from "@/components/schedule-summary/ConflictInspector";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { ConflictCheckResponse, ConflictDetail, Schedule, SchedulePeriod, Specialty, Staff } from "@/types/api";

type ConflictState = {
  schedule: Schedule;
  detail: ConflictDetail;
};

type MessageState = { type: "success" | "error"; text: string };

export default function ServiceClinicPage() {
  const [loading, setLoading] = useState(true);
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [staffList, setStaffList] = useState<Staff[]>([]);
  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);

  const [selectedPeriodId, setSelectedPeriodId] = useState<number | null>(null);
  const [selectedStaffId, setSelectedStaffId] = useState<number | null>(null);
  const [selectedSpecialtyId, setSelectedSpecialtyId] = useState<number | null>(null);
  const [workDate, setWorkDate] = useState("");
  const [notes, setNotes] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [selectedConflict, setSelectedConflict] = useState<ConflictState | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [printing, setPrinting] = useState(false);
  const [checkingConflicts, setCheckingConflicts] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [submitMessage, setSubmitMessage] = useState<MessageState | null>(null);

  const fetchInitialData = useCallback(async () => {
    try {
      setLoading(true);
      setSubmitMessage(null);
      const [periodsRes, staffRes, specialtiesRes] = await Promise.all([
        api.get<SchedulePeriod[]>("/periods"),
        api.get<Staff[]>("/staff/active"),
        api.get<Specialty[]>("/specialties"),
      ]);

        const nextPeriods = periodsRes ?? [];
        setPeriods(nextPeriods);
        setStaffList(staffRes ?? []);
        setSpecialties(specialtiesRes ?? []);

        if (nextPeriods.length > 0) {
          const preferredPeriod =
            nextPeriods.find((period) => period.status === "DRAFT") ??
            nextPeriods.find((period) => period.status === "PUBLISHED") ??
            nextPeriods[0];
          setSelectedPeriodId(preferredPeriod.id);
        }
    } catch (err) {
      setPeriods([]);
      setStaffList([]);
      setSpecialties([]);
      setSubmitMessage({
        type: "error",
        text: getErrorMessage(err, "Không thể tải dữ liệu ban đầu."),
      });
    } finally {
      setLoading(false);
    }
  }, []);

  const fetchSchedules = useCallback(async (periodId: number) => {
    try {
      setLoading(true);
      const [scheduleRes, conflictRes] = await Promise.all([
        api.get<Schedule[]>(`/schedules/period/${periodId}`),
        api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`),
      ]);
      const l03Schedules = (scheduleRes ?? []).filter((schedule) => schedule.shiftType.id === "L03");
      setSchedules(l03Schedules);
      setConflictData(conflictRes);
      return conflictRes;
    } catch (err) {
      setSchedules([]);
      setConflictData(null);
      setSubmitMessage({
        type: "error",
        text: getErrorMessage(err, "Không thể tải danh sách lịch trực."),
      });
      return null;
    } finally {
      setLoading(false);
    }
  }, []);

  const refreshPeriods = useCallback(async () => {
    const periodsRes = await api.get<SchedulePeriod[]>("/periods");
    const nextPeriods = periodsRes ?? [];
    setPeriods(nextPeriods);
    return nextPeriods;
  }, []);

  const handleCheckConflicts = useCallback(async () => {
    if (!selectedPeriodId) {
      setSubmitMessage({ type: "error", text: "Chưa chọn kỳ lịch để kiểm tra xung đột." });
      return null;
    }

    try {
      setCheckingConflicts(true);
      setSubmitMessage(null);
      const conflictRes = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`);
      setConflictData(conflictRes);
      setSubmitMessage({
        type: conflictRes?.hasConflicts ? "error" : "success",
        text: conflictRes?.hasConflicts
          ? `Phát hiện ${conflictRes.totalConflicts} xung đột cần xử lý trước khi công bố.`
          : "Không phát hiện xung đột. Kỳ lịch sẵn sàng để công bố.",
      });
      return conflictRes;
    } catch (err) {
      setSubmitMessage({
        type: "error",
        text: getErrorMessage(err, "Không thể kiểm tra xung đột kỳ lịch."),
      });
      return null;
    } finally {
      setCheckingConflicts(false);
    }
  }, [selectedPeriodId]);

  const handlePublishPeriod = useCallback(async () => {
    if (!selectedPeriodId) {
      setSubmitMessage({ type: "error", text: "Chưa chọn kỳ lịch để công bố." });
      return;
    }

    const currentPeriod = periods.find((period) => period.id === selectedPeriodId);

    if (currentPeriod?.status !== "DRAFT") {
      setSubmitMessage({ type: "error", text: "Chỉ có thể công bố kỳ lịch đang ở trạng thái DRAFT." });
      return;
    }

    const confirmed = window.confirm("Công bố kỳ lịch này? Sau khi công bố, bạn sẽ không thể chỉnh sửa lịch trong kỳ.");
    if (!confirmed) {
      return;
    }

    try {
      setPublishing(true);
      setSubmitMessage(null);
      const latestConflict = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${selectedPeriodId}`);
      setConflictData(latestConflict);

      if (latestConflict?.hasConflicts) {
        setSubmitMessage({
          type: "error",
          text: `Kỳ lịch còn ${latestConflict.totalConflicts} xung đột. Vui lòng xử lý trước khi công bố.`,
        });
        return;
      }

      await api.post(`/periods/${selectedPeriodId}/publish`, {});
      await refreshPeriods();
      await fetchSchedules(selectedPeriodId);
      setSubmitMessage({ type: "success", text: "Đã lưu và công bố kỳ lịch thành công." });
    } catch (err) {
      setSubmitMessage({
        type: "error",
        text: getErrorMessage(err, "Không thể công bố kỳ lịch phòng khám dịch vụ."),
      });
    } finally {
      setPublishing(false);
    }
  }, [fetchSchedules, periods, refreshPeriods, selectedPeriodId]);

  useEffect(() => {
    void fetchInitialData();
  }, [fetchInitialData]);

  useEffect(() => {
    if (selectedPeriodId) {
      void fetchSchedules(selectedPeriodId);
    } else {
      setSchedules([]);
      setConflictData(null);
      setLoading(false);
    }
  }, [selectedPeriodId, fetchSchedules]);

  useEffect(() => {
    if (!workDate) return;
    const isCompensation = schedules.some((s) => {
      if (!s.compensationDate) return false;
      const dateKey = s.compensationDate.split("T")[0] ?? s.compensationDate;
      return dateKey === workDate;
    });
    if (isCompensation) {
      setWorkDate("");
    }
  }, [workDate, schedules]);

  const conflictMap = useMemo(() => {
    const entries = (conflictData?.conflicts ?? []).map((detail) => [detail.scheduleId, detail] as const);
    return new Map<number, ConflictDetail>(entries);
  }, [conflictData]);

  const l03Schedules = schedules;
  const compensationDateSet = useMemo(() => {
    const dates = new Set<string>();
    l03Schedules.forEach((schedule) => {
      if (schedule.compensationDate) {
        dates.add(schedule.compensationDate.split("T")[0] ?? schedule.compensationDate);
      }
    });
    return dates;
  }, [l03Schedules]);
  const blockedCompensationDates = useMemo(
    () => Array.from(compensationDateSet).sort((left, right) => new Date(left).getTime() - new Date(right).getTime()),
    [compensationDateSet],
  );
  const isBlockedCompensationDate = workDate ? compensationDateSet.has(workDate) : false;
  const l03ConflictSchedules = useMemo(
    () => l03Schedules.filter((schedule) => conflictMap.has(schedule.id)),
    [l03Schedules, conflictMap],
  );

  const totalShifts = l03Schedules.length;
  const conflictCount = l03ConflictSchedules.length;
  const assignedPercentage = totalShifts > 0 ? Math.round(((totalShifts - conflictCount) / totalShifts) * 100) : 0;
  const activeRooms = new Set(l03Schedules.map((schedule) => schedule.staff.id)).size;
  const selectedPeriod = periods.find((period) => period.id === selectedPeriodId);
  const isEditablePeriod = selectedPeriod?.status === "DRAFT";
  const canPublishPeriod = Boolean(selectedPeriodId) && isEditablePeriod && !loading && !submitting && !checkingConflicts && !publishing && !conflictData?.hasConflicts;
  const selectedStaff = staffList.find((staff) => staff.id === selectedStaffId);

  const handleSubmit = async (event: React.FormEvent) => {
    event.preventDefault();
    setSubmitMessage(null);

    if (!selectedPeriodId || !selectedStaffId || !workDate) {
      setSubmitMessage({ type: "error", text: "Vui lòng điền đầy đủ thông tin bắt buộc." });
      return;
    }

    if (!isEditablePeriod) {
      setSubmitMessage({ type: "error", text: "Chỉ có thể thêm hoặc chỉnh sửa lịch khi kỳ lịch ở trạng thái DRAFT." });
      return;
    }

    if (workDate && compensationDateSet.has(workDate)) {
      setSubmitMessage({ type: "error", text: "Ngày này là ngày nghỉ bù từ lịch trực 24/24, không thể gán lịch phòng khám dịch vụ." });
      return;
    }

    try {
      setSubmitting(true);
      if (editingId) {
        await api.put(`/schedules/${editingId}`, {
          periodId: selectedPeriodId,
          staffId: selectedStaffId,
          workDate,
          shiftTypeId: "L03",
          notes,
        });
        setSubmitMessage({ type: "success", text: "Cập nhật lịch khám dịch vụ thành công!" });
      } else {
        await api.post("/schedules", {
          periodId: selectedPeriodId,
          staffId: selectedStaffId,
          workDate,
          shiftTypeId: "L03",
          notes,
        });
        setSubmitMessage({ type: "success", text: "Thêm ca khám dịch vụ thành công!" });
      }

      setEditingId(null);
      setSelectedStaffId(null);
      setSelectedSpecialtyId(null);
      setWorkDate("");
      setNotes("");

      await fetchSchedules(selectedPeriodId);
    } catch (err) {
      setSubmitMessage({
        type: "error",
        text: getErrorMessage(err, "Không thể lưu lịch. Kiểm tra xung đột L03/L04 hoặc ngày nghỉ bù."),
      });
    } finally {
      setSubmitting(false);
    }
  };

  async function handleExportExcel() {
    if (!selectedPeriodId) {
      setSubmitMessage({ type: "error", text: "Chưa chọn kỳ lịch để xuất Excel." });
      return;
    }

    try {
      setExporting(true);
      setSubmitMessage(null);
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1"}/dashboard/export/schedule/${selectedPeriodId}`,
        { credentials: "include" },
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `lich-phong-kham-dich-vu-${selectedPeriodId}.xlsx`;
      anchor.click();
      URL.revokeObjectURL(url);
      setSubmitMessage({ type: "success", text: "Đã xuất Excel kỳ lịch hiện tại." });
    } catch (err) {
      setSubmitMessage({
        type: "error",
        text: getErrorMessage(err, "Không thể xuất Excel lịch phòng khám dịch vụ."),
      });
    } finally {
      setExporting(false);
    }
  }

  async function handlePrint() {
    try {
      setPrinting(true);
      setSubmitMessage(null);
      window.print();
    } catch (err) {
      setSubmitMessage({
        type: "error",
        text: getErrorMessage(err, "Không thể mở chế độ in lịch phòng khám dịch vụ."),
      });
    } finally {
      setPrinting(false);
    }
  }

  return (
    <DashboardShell
      activeCode="M04"
      description="Gán nhân sự phụ trách phòng khám dịch vụ theo ngày và kiểm tra trùng lịch chuyên gia."
      title="Lịch phòng khám dịch vụ"
    >
      <div className="space-y-6">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <h1 className="text-headline-md text-on-surface">Lịch phòng khám dịch vụ</h1>
            <p className="mt-1 text-label-md text-on-surface-variant">
              Gán nhân sự phụ trách phòng khám dịch vụ theo ngày và kiểm tra trùng lịch chuyên gia.
            </p>
          </div>
          <div className="flex items-center gap-3">
            <button
              type="button"
              onClick={handlePrint}
              disabled={printing || loading}
              className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-label-md text-primary transition-colors hover:bg-surface-container-low shadow-[0_1px_2px_0_rgba(0,0,0,0.05)] disabled:opacity-50"
            >
              <span className="material-symbols-outlined text-[16px]">print</span>
              {printing ? "Đang mở in..." : "In lịch"}
            </button>
            <button
              type="button"
              onClick={handleExportExcel}
              disabled={exporting || loading || !selectedPeriodId}
              className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary transition-colors hover:bg-primary/90 shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] disabled:opacity-50"
            >
              <span className="material-symbols-outlined text-[16px]">download</span>
              {exporting ? "Đang xuất..." : "Xuất Excel"}
            </button>
          </div>
        </div>
        <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
          <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low">
            <p className="text-label-sm uppercase tracking-wider text-on-surface-variant">Tổng ca khám dịch vụ</p>
            <p className="mt-3 font-display-lg font-bold text-on-surface">{loading ? "—" : totalShifts}</p>
            <p className="mt-1 font-body-sm text-on-surface-variant">{selectedPeriod?.periodName ?? "Chưa chọn đợt"}</p>
            <p className="mt-2 font-body-sm text-on-surface-variant">
              Trạng thái kỳ lịch: <span className="font-semibold text-on-surface">{selectedPeriod?.status ?? "—"}</span>
            </p>
          </div>

          <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low">
            <p className="text-label-sm uppercase tracking-wider text-on-surface-variant">Đã gán hợp lệ</p>
            <p className="mt-3 font-display-lg font-bold text-on-surface">{loading ? "—" : `${assignedPercentage}%`}</p>
            <p className="mt-1 font-body-sm text-on-surface-variant">{totalShifts > 0 ? `${totalShifts - conflictCount} ca không xung đột` : "Chưa có ca nào"}</p>
          </div>

          <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low">
            <p className="text-label-sm uppercase tracking-wider text-on-surface-variant">Cảnh báo</p>
            <p className={`mt-3 font-display-lg font-bold ${conflictCount > 0 ? "text-error" : "text-on-surface"}`}>
              {loading ? "—" : conflictCount}
            </p>
            <p className="mt-1 font-body-sm text-on-surface-variant">{conflictCount > 0 ? "Trùng lịch chuyên gia hoặc vi phạm rule" : "Không có xung đột"}</p>
          </div>

          <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low">
            <p className="text-label-sm uppercase tracking-wider text-on-surface-variant">Nhân sự tham gia</p>
            <p className="mt-3 font-display-lg font-bold text-on-surface">{loading ? "—" : activeRooms}</p>
            <p className="mt-1 font-body-sm text-on-surface-variant">Số người đang được phân công</p>
          </div>
        </section>

        {isEditablePeriod && (
          <div className="rounded-xl border border-primary/30 bg-primary/5 px-5 py-3 shadow-sm backdrop-blur-sm">
            <div className="flex flex-col items-start justify-between gap-3 sm:flex-row sm:items-center">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-primary text-[20px]">info</span>
                <span className="text-label-md text-on-surface">
                  {conflictData?.hasConflicts
                    ? `${conflictData.totalConflicts} xung đột cần xử lý trước khi công bố.`
                    : "Không phát hiện xung đột. Kỳ lịch sẵn sàng để công bố."}
                </span>
              </div>
              <div className="flex gap-2">
                <button
                  type="button"
                  onClick={handleCheckConflicts}
                  disabled={!selectedPeriodId || checkingConflicts || publishing}
                  className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface px-4 py-2 text-label-md text-on-surface shadow-[0_1px_2px_0_rgba(0,0,0,0.05)] hover:bg-surface-container-low disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <span className="material-symbols-outlined text-[16px]">sync</span>
                  {checkingConflicts ? "Đang kiểm tra..." : "Kiểm tra xung đột"}
                </button>
                <button
                  type="button"
                  onClick={handlePublishPeriod}
                  disabled={!canPublishPeriod}
                  className="flex items-center gap-2 rounded-lg bg-primary px-5 py-2 text-label-md text-on-primary shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] hover:bg-primary/90 disabled:cursor-not-allowed disabled:opacity-50"
                >
                  <span className="material-symbols-outlined text-[16px]">publish</span>
                  {publishing ? "Đang công bố..." : "Lưu & Công bố"}
                </button>
              </div>
            </div>
          </div>
        )}

        <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
          <SectionCard
            description="M04-F01 / M04-F03 — Thêm, sửa, xóa lịch phòng khám dịch vụ"
            title="Bảng phân công phòng khám dịch vụ"
          >
            {loading ? (
              <div className="flex items-center justify-center py-16">
                <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
              </div>
            ) : l03Schedules.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-center">
                <span className="material-symbols-outlined text-[48px] text-outline">event_busy</span>
                <p className="mt-4 font-label-md text-on-surface-variant">Chưa có lịch phòng khám dịch vụ</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] border-collapse text-left">
                  <thead>
                    <tr className="border-b border-outline-variant bg-surface-container-low">
                      {[
                        "Ngày",
                        "Nhân sự",
                        "Chuyên khoa",
                        "Trạng thái",
                        "",
                      ].map((header) => (
                        <th key={header} className="px-5 py-3 font-label-sm text-label-sm font-bold uppercase tracking-wider text-on-surface-variant">
                          {header}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant">
                    {l03Schedules.map((schedule) => {
                      const staffMember = staffList.find((staff) => staff.id === schedule.staff.id);
                      const detail = conflictMap.get(schedule.id);
                      const formattedDate = new Date(schedule.workDate).toLocaleDateString("vi-VN", {
                        weekday: "short",
                        day: "2-digit",
                        month: "2-digit",
                        year: "numeric",
                      });

                      return (
                        <tr key={schedule.id} className="group transition-colors hover:bg-surface-container-low">
                          <td className="px-5 py-3 font-body-md text-on-surface">{formattedDate}</td>
                          <td className="px-5 py-3 font-body-md text-on-surface">{schedule.staff.fullName}</td>
                          <td className="px-5 py-3 font-body-md text-on-surface-variant">{staffMember?.specialty?.name ?? "—"}</td>
                          <td className="px-5 py-3">
                            {detail ? (
                              <button
                                type="button"
                                onClick={() => setSelectedConflict({ schedule, detail })}
                                className="inline-flex items-center gap-1.5 rounded-full border border-error/20 bg-error-container px-2.5 py-1 text-[11px] font-semibold text-on-error-container"
                              >
                                Xung đột
                              </button>
                            ) : (
                              <span className="inline-flex items-center gap-1.5 rounded-full border border-secondary/20 bg-secondary-container px-2.5 py-1 text-[11px] font-semibold text-on-secondary-container">
                                Bình thường
                              </span>
                            )}
                          </td>
                          <td className="px-5 py-3 text-right">
                            <div className="flex justify-end gap-1 opacity-0 transition-opacity group-hover:opacity-100">
                              <button
                                className="flex h-8 w-8 items-center justify-center rounded-lg bg-surface text-on-surface-variant transition-colors hover:bg-primary-container hover:text-primary disabled:cursor-not-allowed disabled:opacity-40"
                                disabled={!isEditablePeriod}
                                onClick={() => {
                                  setEditingId(schedule.id);
                                  setSelectedPeriodId(schedule.periodId);
                                  setSelectedStaffId(schedule.staff.id);
                                  setSelectedSpecialtyId(staffMember?.specialty?.id ?? null);
                                  setWorkDate(schedule.workDate.split("T")[0]);
                                  setNotes(schedule.notes ?? "");
                                }}
                                title="Sửa"
                                type="button"
                              >
                                <span className="material-symbols-outlined text-[18px]">edit</span>
                              </button>
                              <button
                                className="flex h-8 w-8 items-center justify-center rounded-lg bg-surface text-on-surface-variant transition-colors hover:bg-error-container hover:text-error disabled:cursor-not-allowed disabled:opacity-40"
                                disabled={!isEditablePeriod}
                                onClick={async () => {
                                  if (!confirm("Bạn có chắc muốn xóa lịch này?")) return;
                                  try {
                                    await api.delete(`/schedules/${schedule.id}`);
                                    if (selectedPeriodId) {
                                      await fetchSchedules(selectedPeriodId);
                                    }
                                    if (editingId === schedule.id) {
                                      setEditingId(null);
                                      setSelectedStaffId(null);
                                      setSelectedSpecialtyId(null);
                                      setWorkDate("");
                                      setNotes("");
                                    }
                                    setSubmitMessage({ type: "success", text: "Đã xóa lịch khám dịch vụ." });
                                  } catch (err) {
                                    setSubmitMessage({
                                      type: "error",
                                      text: getErrorMessage(err, "Không thể xóa lịch."),
                                    });
                                  }
                                }}
                                title="Xóa"
                                type="button"
                              >
                                <span className="material-symbols-outlined text-[18px]">delete</span>
                              </button>
                            </div>
                          </td>
                        </tr>
                      );
                    })}
                  </tbody>
                </table>
              </div>
            )}
          </SectionCard>

          <aside className="space-y-6">
            <SectionCard description={editingId ? "M04-F03" : "M04-F01"} title={editingId ? "Sửa lịch khám dịch vụ" : "Tạo lịch khám dịch vụ"}>
              <form className="space-y-4 px-5 py-4" onSubmit={handleSubmit}>
                {!isEditablePeriod ? (
                  <div className="rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 text-sm text-on-surface-variant">
                    Kỳ lịch hiện tại không còn ở trạng thái `DRAFT`, nên chỉ có thể xem và kiểm tra xung đột.
                  </div>
                ) : null}
                <div className="space-y-1">
                  <label className="text-label-sm uppercase tracking-wider text-on-surface-variant">
                    Đợt lịch trực <span className="text-error">*</span>
                  </label>
                  <div className="relative">
                    <select
                      className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-low px-3 pr-10 text-body-md text-on-surface outline-none transition-colors focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
                      disabled={!isEditablePeriod}
                      value={selectedPeriodId ?? ""}
                      onChange={(event) => setSelectedPeriodId(Number(event.target.value) || null)}
                    >
                      <option value="">Chọn đợt lịch trực</option>
                      {periods.map((period) => (
                        <option key={period.id} value={period.id}>
                          {period.periodName}
                        </option>
                      ))}
                    </select>
                    <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant">
                      <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                      </svg>
                    </span>
                  </div>
                </div>

                <div className="space-y-1">
                  <label className="text-label-sm uppercase tracking-wider text-on-surface-variant">
                    Ngày làm việc <span className="text-error">*</span>
                  </label>
                  <input
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 text-body-md text-on-surface outline-none transition-colors focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
                    disabled={!isEditablePeriod}
                    type="date"
                    value={workDate}
                    onChange={(event) => setWorkDate(event.target.value)}
                  />
                  {workDate && compensationDateSet.has(workDate) ? (
                    <p className="mt-1 text-sm text-error">Ngày này là ngày nghỉ bù từ lịch trực 24/24 và đang bị khóa.</p>
                  ) : null}
                </div>

                <div className="space-y-1">
                  <label className="text-label-sm uppercase tracking-wider text-on-surface-variant">
                    Nhân sự <span className="text-error">*</span>
                  </label>
                  <div className="relative">
                    <select
                      className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-low px-3 pr-10 text-body-md text-on-surface outline-none transition-colors focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
                      disabled={!isEditablePeriod}
                      value={selectedStaffId ?? ""}
                      onChange={(event) => setSelectedStaffId(Number(event.target.value) || null)}
                    >
                      <option value="">Chọn nhân sự</option>
                      {staffList.map((staff) => (
                        <option key={staff.id} value={staff.id}>
                          {staff.fullName}
                        </option>
                      ))}
                    </select>
                    <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant">
                      <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                      </svg>
                    </span>
                  </div>
                </div>

                <div className="space-y-1">
                  <label className="text-label-sm uppercase tracking-wider text-on-surface-variant">Chuyên khoa</label>
                  <div className="relative">
                    <select
                      className="h-10 w-full appearance-none rounded-lg border border-outline-variant bg-surface-container-low px-3 pr-10 text-body-md text-on-surface outline-none transition-colors focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
                      disabled={!isEditablePeriod}
                      value={selectedSpecialtyId ?? ""}
                      onChange={(event) => setSelectedSpecialtyId(Number(event.target.value) || null)}
                    >
                      <option value="">Chọn chuyên khoa</option>
                      {specialties.map((specialty) => (
                        <option key={specialty.id} value={specialty.id}>
                          {specialty.name}
                        </option>
                      ))}
                    </select>
                    <span className="pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-on-surface-variant">
                      <svg className="h-4 w-4" fill="none" stroke="currentColor" viewBox="0 0 24 24">
                        <path strokeLinecap="round" strokeLinejoin="round" strokeWidth={2} d="M19 9l-7 7-7-7" />
                      </svg>
                    </span>
                  </div>
                </div>

                <div className="space-y-1">
                  <label className="text-label-sm uppercase tracking-wider text-on-surface-variant">Ghi chú</label>
                  <textarea
                    className="w-full resize-none rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 text-body-md text-on-surface outline-none transition-colors focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
                    disabled={!isEditablePeriod}
                    rows={3}
                    placeholder="Nhập ghi chú (nếu có)"
                    value={notes}
                    onChange={(event) => setNotes(event.target.value)}
                  />
                </div>

                {submitMessage && (
                  <div
                    className={`rounded-lg p-3 text-sm font-medium ${
                      submitMessage.type === "success"
                        ? "bg-secondary-container text-on-secondary-container"
                        : "bg-error-container text-on-error-container"
                    }`}
                  >
                    {submitMessage.text}
                  </div>
                )}

                <div className="flex gap-2">
                  {editingId && (
                    <button
                      type="button"
                      className="inline-flex h-10 flex-1 items-center justify-center rounded-lg border border-outline-variant bg-surface-container-low px-4 text-label-md text-on-surface shadow-sm transition-colors hover:bg-surface-container disabled:opacity-50"
                      onClick={() => {
                        setEditingId(null);
                        setSelectedStaffId(null);
                        setSelectedSpecialtyId(null);
                        setWorkDate("");
                        setNotes("");
                        setSubmitMessage(null);
                      }}
                      disabled={submitting || !isEditablePeriod}
                    >
                      Hủy sửa
                    </button>
                  )}
                  <button
                    className="inline-flex h-10 w-full items-center justify-center rounded-lg bg-primary px-4 text-label-md text-on-primary shadow-sm transition-colors hover:opacity-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20 disabled:opacity-50"
                    type="submit"
                    disabled={submitting || !isEditablePeriod || isBlockedCompensationDate}
                  >
                    {submitting ? (
                      <>
                        <div className="mr-2 size-4 animate-spin rounded-full border-2 border-on-primary border-t-transparent" />
                        Đang xử lý...
                      </>
                    ) : editingId ? (
                      "Lưu cập nhật"
                    ) : selectedStaff ? (
                      `Thêm ca cho ${selectedStaff.fullName}`
                    ) : (
                      "Thêm ca khám dịch vụ"
                    )}
                  </button>
                </div>
              </form>
            </SectionCard>

          <ConflictInspector
            title="Cảnh báo trực tiếp"
            description="M04-F02 · xung đột thật theo kỳ"
            conflicts={l03ConflictSchedules
              .map((schedule) => conflictMap.get(schedule.id))
              .filter((detail): detail is ConflictDetail => Boolean(detail))}
            emptyLabel="Không có xung đột ở lịch khám dịch vụ."
            selectedConflict={selectedConflict?.detail ?? null}
            onSelect={(detail) => {
              const schedule = l03Schedules.find((item) => item.id === detail.scheduleId);
              if (!schedule) {
                return;
              }
              setSelectedConflict({ schedule, detail });
            }}
            onClose={() => setSelectedConflict(null)}
          />

          {blockedCompensationDates.length > 0 ? (
            <SectionCard title={`${blockedCompensationDates.length} ngày nghỉ bù bị khóa`} description="Không được gán lịch dịch vụ vào các ngày này">
              <div className="flex flex-wrap gap-2 px-5 py-4">
                {blockedCompensationDates.map((day) => (
                  <span
                    key={day}
                    className="inline-flex items-center gap-1.5 rounded-full bg-surface-container-high px-3 py-1 text-label-sm text-on-surface-variant"
                  >
                    <span className="material-symbols-outlined text-[14px]">event_busy</span>
                    {new Date(day).toLocaleDateString("vi-VN")}
                  </span>
                ))}
              </div>
            </SectionCard>
          ) : null}

            <section className="rounded-lg border border-tertiary-container bg-tertiary-fixed/30 p-5 shadow-sm">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[20px] text-tertiary">warning</span>
                <p className="text-label-sm uppercase tracking-wider text-tertiary">Kiểm tra trước lưu</p>
              </div>
              <ul className="mt-3 space-y-2 font-body-sm text-on-surface">
                <li className="flex items-start gap-2">
                  <span className="mt-1 text-tertiary">•</span>
                  <span>Dịch vụ (`L03`) không được trùng với Chuyên gia (`L04`) cùng ngày.</span>
                </li>
                <li className="flex items-start gap-2">
                  <span className="mt-1 text-tertiary">•</span>
                  <span>Không được xếp vào ngày nghỉ bù của nhân sự.</span>
                </li>
                <li className="flex items-start gap-2">
                  <span className="mt-1 text-tertiary">•</span>
                  <span>Mỗi nhân sự chỉ được gán 1 ca `L03` mỗi ngày.</span>
                </li>
              </ul>
            </section>
          </aside>
        </div>
      </div>

    </DashboardShell>
  );
}
