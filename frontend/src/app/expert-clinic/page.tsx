"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ConflictInspector } from "@/components/schedule-summary/ConflictInspector";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { ConflictCheckResponse, ConflictDetail, Schedule, SchedulePeriod, ShiftType, Specialty, Staff } from "@/types/api";

type ConflictState = {
  schedule: Schedule;
  detail: ConflictDetail;
};

type MessageState = { type: "success" | "error"; text: string };

export default function ExpertClinicPage() {
  const [loading, setLoading] = useState(true);
  const [periods, setPeriods] = useState<SchedulePeriod[]>([]);
  const [staffList, setStaffList] = useState<Staff[]>([]);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [shiftTypes, setShiftTypes] = useState<ShiftType[]>([]);
  const [specialties, setSpecialties] = useState<Specialty[]>([]);
  const [conflictData, setConflictData] = useState<ConflictCheckResponse | null>(null);
  const [selectedSpecialty, setSelectedSpecialty] = useState<number | null>(null);
  const [activePeriodId, setActivePeriodId] = useState<number | null>(null);

  const [formPeriodId, setFormPeriodId] = useState<number | null>(null);
  const [formStaffId, setFormStaffId] = useState<number | null>(null);
  const [formWorkDate, setFormWorkDate] = useState("");
  const [formNotes, setFormNotes] = useState("");
  const [editingId, setEditingId] = useState<number | null>(null);
  const [selectedConflict, setSelectedConflict] = useState<ConflictState | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [exporting, setExporting] = useState(false);
  const [printing, setPrinting] = useState(false);
  const [checkingConflicts, setCheckingConflicts] = useState(false);
  const [publishing, setPublishing] = useState(false);
  const [submitMsg, setSubmitMsg] = useState<MessageState | null>(null);

  const fetchSchedules = useCallback(async (periodId: number) => {
    try {
      const [scheduleRes, conflictRes] = await Promise.all([
        api.get<Schedule[]>(`/schedules/period/${periodId}`),
        api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`),
      ]);
      setSchedules((scheduleRes ?? []).filter((schedule) => schedule.shiftType.id === "L04"));
      setConflictData(conflictRes);
      return conflictRes;
    } catch (err) {
      setSchedules([]);
      setConflictData(null);
      setSubmitMsg({
        type: "error",
        text: getErrorMessage(err, "Không thể tải lịch chuyên gia."),
      });
      return null;
    }
  }, []);

  const refreshPeriods = useCallback(async () => {
    const periodsRes = await api.get<SchedulePeriod[]>("/periods");
    const periodsData = periodsRes ?? [];
    setPeriods(periodsData);
    return periodsData;
  }, []);

  const handleCheckConflicts = useCallback(async () => {
    if (!activePeriodId) {
      setSubmitMsg({ type: "error", text: "Chưa chọn kỳ lịch để kiểm tra xung đột." });
      return null;
    }

    try {
      setCheckingConflicts(true);
      setSubmitMsg(null);
      const conflictRes = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${activePeriodId}`);
      setConflictData(conflictRes);
      setSubmitMsg({
        type: conflictRes?.hasConflicts ? "error" : "success",
        text: conflictRes?.hasConflicts
          ? `Phát hiện ${conflictRes.totalConflicts} xung đột cần xử lý trước khi công bố.`
          : "Không phát hiện xung đột. Kỳ lịch sẵn sàng để công bố.",
      });
      return conflictRes;
    } catch (err) {
      setSubmitMsg({
        type: "error",
        text: getErrorMessage(err, "Không thể kiểm tra xung đột kỳ lịch."),
      });
      return null;
    } finally {
      setCheckingConflicts(false);
    }
  }, [activePeriodId]);

  const handlePublishPeriod = useCallback(async () => {
    if (!activePeriodId) {
      setSubmitMsg({ type: "error", text: "Chưa chọn kỳ lịch để công bố." });
      return;
    }

    const currentPeriod = periods.find((period) => period.id === activePeriodId);
    if (currentPeriod?.status !== "DRAFT") {
      setSubmitMsg({ type: "error", text: "Chỉ có thể công bố kỳ lịch đang ở trạng thái DRAFT." });
      return;
    }

    const confirmed = window.confirm("Công bố kỳ lịch này? Sau khi công bố, bạn sẽ không thể chỉnh sửa lịch trong kỳ.");
    if (!confirmed) {
      return;
    }

    try {
      setPublishing(true);
      setSubmitMsg(null);
      const latestConflict = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${activePeriodId}`);
      setConflictData(latestConflict);

      if (latestConflict?.hasConflicts) {
        setSubmitMsg({
          type: "error",
          text: `Kỳ lịch còn ${latestConflict.totalConflicts} xung đột. Vui lòng xử lý trước khi công bố.`,
        });
        return;
      }

      await api.post(`/periods/${activePeriodId}/publish`, {});
      const nextPeriods = await refreshPeriods();
      const refreshedActivePeriod = nextPeriods.find((period) => period.id === activePeriodId);
      if (refreshedActivePeriod) {
        setFormPeriodId(refreshedActivePeriod.id);
      }
      await fetchSchedules(activePeriodId);
      setSubmitMsg({ type: "success", text: "Đã lưu và công bố kỳ lịch thành công." });
    } catch (err) {
      setSubmitMsg({
        type: "error",
        text: getErrorMessage(err, "Không thể công bố kỳ lịch phòng khám chuyên gia."),
      });
    } finally {
      setPublishing(false);
    }
  }, [activePeriodId, fetchSchedules, periods, refreshPeriods]);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setSubmitMsg(null);
    if (!formPeriodId || !formStaffId || !formWorkDate) {
      setSubmitMsg({ type: "error", text: "Vui lòng điền đầy đủ thông tin bắt buộc." });
      return;
    }

    if (!isEditablePeriod) {
      setSubmitMsg({ type: "error", text: "Chỉ có thể thêm hoặc chỉnh sửa lịch khi kỳ lịch ở trạng thái DRAFT." });
      return;
    }

    if (formWorkDate && compensationDateSet.has(formWorkDate)) {
      setSubmitMsg({ type: "error", text: "Ngày này là ngày nghỉ bù từ lịch trực 24/24, không thể gán lịch phòng khám chuyên gia." });
      return;
    }

    try {
      setSubmitting(true);
      if (editingId) {
        await api.put(`/schedules/${editingId}`, {
          periodId: formPeriodId,
          staffId: formStaffId,
          workDate: formWorkDate,
          shiftTypeId: "L04",
          notes: formNotes,
        });
        setSubmitMsg({ type: "success", text: "Cập nhật lịch chuyên gia thành công!" });
      } else {
        await api.post("/schedules", {
          periodId: formPeriodId,
          staffId: formStaffId,
          workDate: formWorkDate,
          shiftTypeId: "L04",
          notes: formNotes,
        });
        setSubmitMsg({ type: "success", text: "Thêm lịch chuyên gia thành công!" });
      }
      setEditingId(null);
      setFormStaffId(null);
      setFormWorkDate("");
      setFormNotes("");
      if (formPeriodId) {
        await fetchSchedules(formPeriodId);
      }
    } catch (err) {
      setSubmitMsg({
        type: "error",
        text: getErrorMessage(err, "Không thể lưu lịch. Kiểm tra xung đột L03/L04 hoặc ngày nghỉ bù."),
      });
    } finally {
      setSubmitting(false);
    }
  }

  async function handleDelete(id: number) {
    if (!confirm("Bạn có chắc muốn xóa lịch này?")) return;
    try {
      await api.delete(`/schedules/${id}`);
      if (activePeriodId) {
        await fetchSchedules(activePeriodId);
      }
      setSubmitMsg({ type: "success", text: "Đã xóa lịch chuyên gia." });
    } catch (err) {
      setSubmitMsg({
        type: "error",
        text: getErrorMessage(err, "Không thể xóa lịch."),
      });
    }
  }

  async function handleExportExcel() {
    if (!activePeriodId) {
      setSubmitMsg({ type: "error", text: "Chưa chọn kỳ lịch để xuất Excel." });
      return;
    }

    try {
      setExporting(true);
      setSubmitMsg(null);
      const response = await fetch(
        `${process.env.NEXT_PUBLIC_API_URL || "http://localhost:8080/api/v1"}/dashboard/export/schedule/${activePeriodId}`,
        { credentials: "include" },
      );

      if (!response.ok) {
        throw new Error(`HTTP ${response.status}`);
      }

      const blob = await response.blob();
      const url = URL.createObjectURL(blob);
      const anchor = document.createElement("a");
      anchor.href = url;
      anchor.download = `lich-phong-kham-chuyen-gia-${activePeriodId}.xlsx`;
      anchor.click();
      URL.revokeObjectURL(url);
      setSubmitMsg({ type: "success", text: "Đã xuất Excel kỳ lịch hiện tại." });
    } catch (err) {
      setSubmitMsg({
        type: "error",
        text: getErrorMessage(err, "Không thể xuất Excel lịch phòng khám chuyên gia."),
      });
    } finally {
      setExporting(false);
    }
  }

  async function handlePrint() {
    try {
      setPrinting(true);
      setSubmitMsg(null);
      window.print();
    } catch (err) {
      setSubmitMsg({
        type: "error",
        text: getErrorMessage(err, "Không thể mở chế độ in lịch phòng khám chuyên gia."),
      });
    } finally {
      setPrinting(false);
    }
  }

  useEffect(() => {
    const fetchData = async () => {
      try {
        setLoading(true);
        setSubmitMsg(null);
        const [periodsRes, staffRes, shiftTypesRes, specialtiesRes] = await Promise.all([
          api.get<SchedulePeriod[]>("/periods"),
          api.get<Staff[]>("/staff/active"),
          api.get<ShiftType[]>("/shift-types"),
          api.get<Specialty[]>("/specialties"),
        ]);

        const periodsData = periodsRes ?? [];
        setPeriods(periodsData);
        setStaffList(staffRes ?? []);
        setShiftTypes(shiftTypesRes ?? []);
        setSpecialties(specialtiesRes ?? []);

        const preferredPeriod =
          periodsData.find((period) => period.status === "DRAFT") ??
          periodsData.find((period) => period.status === "PUBLISHED") ??
          periodsData[0];

        if (preferredPeriod) {
          setActivePeriodId(preferredPeriod.id);
          setFormPeriodId(preferredPeriod.id);
          await fetchSchedules(preferredPeriod.id);
        } else {
          setActivePeriodId(null);
          setFormPeriodId(null);
          setSchedules([]);
          setConflictData(null);
        }
      } catch (err) {
        setPeriods([]);
        setStaffList([]);
        setShiftTypes([]);
        setSpecialties([]);
        setSchedules([]);
        setConflictData(null);
        setSubmitMsg({
          type: "error",
          text: getErrorMessage(err, "Không thể tải dữ liệu lịch chuyên gia."),
        });
      } finally {
        setLoading(false);
      }
    };

    void fetchData();
  }, [fetchSchedules]);

  useEffect(() => {
    if (!formWorkDate) return;
    const isCompensation = schedules.some((s) => {
      if (!s.compensationDate) return false;
      const dateKey = s.compensationDate.split("T")[0] ?? s.compensationDate;
      return dateKey === formWorkDate;
    });
    if (isCompensation) {
      setFormWorkDate("");
    }
  }, [formWorkDate, schedules]);

  const l04Schedules = schedules;
  const compensationDateSet = useMemo(() => {
    const dates = new Set<string>();
    l04Schedules.forEach((schedule) => {
      if (schedule.compensationDate) {
        dates.add(schedule.compensationDate.split("T")[0] ?? schedule.compensationDate);
      }
    });
    return dates;
  }, [l04Schedules]);
  const blockedCompensationDates = useMemo(
    () => Array.from(compensationDateSet).sort((left, right) => new Date(left).getTime() - new Date(right).getTime()),
    [compensationDateSet],
  );
  const isBlockedCompensationDate = formWorkDate ? compensationDateSet.has(formWorkDate) : false;

  const filteredSchedules =
    selectedSpecialty === null
      ? l04Schedules
      : l04Schedules.filter((schedule) => {
          const staffMember = staffList.find((staff) => staff.id === schedule.staff.id);
          return staffMember?.specialty?.id === selectedSpecialty;
        });

  const conflictMap = useMemo(() => {
    const entries = (conflictData?.conflicts ?? []).map((detail) => [detail.scheduleId, detail] as const);
    return new Map<number, ConflictDetail>(entries);
  }, [conflictData]);

  const filteredConflictSchedules = useMemo(
    () => filteredSchedules.filter((schedule) => conflictMap.has(schedule.id)),
    [filteredSchedules, conflictMap],
  );

  const expertCount = new Set(l04Schedules.map((schedule) => schedule.staff.id)).size;
  const shiftCount = l04Schedules.length;
  const specialtyCount = new Set(
    l04Schedules.map((schedule) => staffList.find((staff) => staff.id === schedule.staff.id)?.specialty?.id).filter(Boolean),
  ).size;
  const conflictCount = l04Schedules.filter((schedule) => conflictMap.has(schedule.id)).length;

  const specialtyButtons = [{ id: null, name: "Tất cả" }, ...specialties.filter((specialty) => specialty.isActive)];
  const selectedShiftType = shiftTypes.find((shiftType) => shiftType.id === "L04");
  const activePeriod = periods.find((period) => period.id === activePeriodId) ?? null;
  const isEditablePeriod = activePeriod?.status === "DRAFT";
  const canPublishPeriod = Boolean(activePeriodId) && isEditablePeriod && !loading && !submitting && !checkingConflicts && !publishing && !conflictData?.hasConflicts;

  if (loading) {
    return (
      <DashboardShell
        activeCode="M05"
        description="Lọc chuyên khoa, gán chuyên gia khám chuyên sâu và tránh trùng lịch dịch vụ."
        title="Lịch phòng khám chuyên gia"
      >
        <div className="flex h-96 items-center justify-center">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      </DashboardShell>
    );
  }

  return (
    <DashboardShell
      activeCode="M05"
      description="Lọc chuyên khoa, gán chuyên gia khám chuyên sâu và tránh trùng lịch dịch vụ."
      title="Lịch phòng khám chuyên gia"
    >
      <div className="space-y-6">
        <div className="flex flex-col justify-between gap-4 sm:flex-row sm:items-center">
          <div>
            <h1 className="text-headline-md text-on-surface">Lịch phòng khám chuyên gia</h1>
            <p className="mt-1 text-label-md text-on-surface-variant">
              Lọc chuyên khoa, gán chuyên gia khám chuyên sâu và tránh trùng lịch dịch vụ.
            </p>
            <p className="mt-2 text-sm text-on-surface-variant">
              Trạng thái kỳ lịch: <span className="font-semibold text-on-surface">{activePeriod?.status ?? "—"}</span>
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
              disabled={exporting || loading || !activePeriodId}
              className="flex items-center gap-2 rounded-lg bg-primary px-4 py-2 text-label-md text-on-primary transition-colors hover:bg-primary/90 shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)] disabled:opacity-50"
            >
              <span className="material-symbols-outlined text-[16px]">download</span>
              {exporting ? "Đang xuất..." : "Xuất Excel"}
            </button>
          </div>
        </div>

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
                disabled={!activePeriodId || checkingConflicts || publishing}
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

        <div className="grid gap-6 xl:grid-cols-[1fr_340px]">
          <div className="space-y-6">
          <section className="grid gap-4 md:grid-cols-4">
            <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low">
              <p className="flex items-center gap-2 text-label-sm uppercase tracking-wider text-on-surface-variant">
                <span className="material-symbols-outlined text-[16px] text-primary">person</span>
                Chuyên gia
              </p>
              <p className="mt-3 font-display-lg font-bold text-on-surface">{expertCount}</p>
            </div>
            <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low">
              <p className="flex items-center gap-2 text-label-sm uppercase tracking-wider text-on-surface-variant">
                <span className="material-symbols-outlined text-[16px] text-primary">calendar_month</span>
                Ca chuyên sâu
              </p>
              <p className="mt-3 font-display-lg font-bold text-on-surface">{shiftCount}</p>
            </div>
            <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low">
              <p className="flex items-center gap-2 text-label-sm uppercase tracking-wider text-on-surface-variant">
                <span className="material-symbols-outlined text-[16px] text-tertiary">medical_information</span>
                Chuyên khoa
              </p>
              <p className="mt-3 font-display-lg font-bold text-on-surface">{specialtyCount}</p>
            </div>
            <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low">
              <p className="flex items-center gap-2 text-label-sm uppercase tracking-wider text-on-surface-variant">
                <span className="material-symbols-outlined text-[16px] text-error">warning</span>
                Xung đột
              </p>
              <p className={`mt-3 font-display-lg font-bold ${conflictCount > 0 ? "text-error" : "text-on-surface"}`}>{conflictCount}</p>
            </div>
          </section>

          <div className="rounded-lg border border-outline-variant bg-surface-container-lowest shadow-sm overflow-hidden">
            <div className="flex flex-wrap items-center gap-2 border-b border-outline-variant p-4">
              <span className="mr-2 text-label-sm text-on-surface-variant">Lọc theo chuyên khoa:</span>
              {specialtyButtons.map((specialty) => (
                <button
                  className={`rounded-lg px-3 py-1.5 text-label-sm transition-colors ${
                    selectedSpecialty === specialty.id
                      ? "bg-primary text-on-primary"
                      : "border border-outline-variant text-on-surface-variant hover:bg-surface-container-low"
                  }`}
                  key={specialty.id ?? "all"}
                  onClick={() => setSelectedSpecialty(specialty.id)}
                  type="button"
                >
                  {specialty.name}
                </button>
              ))}
            </div>

            {filteredSchedules.length === 0 ? (
              <div className="flex flex-col items-center justify-center py-12 text-center">
                <span className="material-symbols-outlined text-[48px] text-outline">event_busy</span>
                <p className="mt-4 font-label-md text-on-surface-variant">Chưa có lịch phòng khám chuyên gia</p>
              </div>
            ) : (
              <div className="overflow-x-auto">
                <table className="w-full min-w-[720px] border-collapse text-left">
                  <thead>
                    <tr className="border-b border-outline-variant bg-surface-container-low">
                      {["Ngày", "Chuyên gia", "Chuyên khoa", "Trạng thái", ""].map((header) => (
                        <th key={header} className="px-5 py-3 font-label-sm text-label-sm font-bold uppercase tracking-wider text-on-surface-variant">
                          {header}
                        </th>
                      ))}
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-outline-variant">
                    {filteredSchedules.map((schedule) => {
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
                                  setFormPeriodId(schedule.periodId);
                                  setFormStaffId(schedule.staff.id);
                                  setFormWorkDate(schedule.workDate.split("T")[0]);
                                  setFormNotes(schedule.notes ?? "");
                                }}
                                title="Sửa"
                                type="button"
                              >
                                <span className="material-symbols-outlined text-[18px]">edit</span>
                              </button>
                              <button
                                className="flex h-8 w-8 items-center justify-center rounded-lg bg-surface text-on-surface-variant transition-colors hover:bg-error-container hover:text-error disabled:cursor-not-allowed disabled:opacity-40"
                                disabled={!isEditablePeriod}
                                onClick={() => handleDelete(schedule.id)}
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
          </div>
        </div>

        <aside className="space-y-6">
          <SectionCard description={editingId ? "M05-F03" : "M05-F01"} title={editingId ? "Sửa lịch chuyên gia" : "Thêm lịch chuyên gia"}>
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
                    value={formPeriodId ?? ""}
                    onChange={(event) => setFormPeriodId(Number(event.target.value) || null)}
                  >
                    <option value="">Chọn đợt lịch</option>
                    {periods.map((period) => (
                      <option key={period.id} value={period.id}>{period.periodName}</option>
                    ))}
                  </select>
                  <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[18px] text-outline">expand_more</span>
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
                  value={formWorkDate}
                  onChange={(event) => setFormWorkDate(event.target.value)}
                />
                {formWorkDate && compensationDateSet.has(formWorkDate) ? (
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
                    value={formStaffId ?? ""}
                    onChange={(event) => setFormStaffId(Number(event.target.value) || null)}
                  >
                    <option value="">Chọn nhân sự</option>
                    {staffList.map((staff) => (
                      <option key={staff.id} value={staff.id}>{staff.fullName}</option>
                    ))}
                  </select>
                  <span className="material-symbols-outlined pointer-events-none absolute right-3 top-1/2 -translate-y-1/2 text-[18px] text-outline">expand_more</span>
                </div>
              </div>
              <div className="rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 text-body-sm text-on-surface-variant">
                Loại lịch: <span className="font-semibold text-on-surface">{selectedShiftType?.name ?? "Phòng khám chuyên gia"}</span>
              </div>
              <div className="space-y-1">
                <label className="text-label-sm uppercase tracking-wider text-on-surface-variant">Ghi chú</label>
                <textarea
                  className="w-full resize-none rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2 text-body-md text-on-surface outline-none transition-colors focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
                  disabled={!isEditablePeriod}
                  rows={3}
                  value={formNotes}
                  onChange={(event) => setFormNotes(event.target.value)}
                />
              </div>
              {submitMsg && (
                <div className={`rounded-lg p-3 text-sm ${submitMsg.type === "success" ? "bg-secondary-container text-on-secondary-container" : "bg-error-container text-on-error-container"}`}>
                  {submitMsg.text}
                </div>
              )}
              <div className="flex gap-2">
                {editingId && (
                  <button
                    className="flex-1 h-10 rounded-lg border border-outline-variant px-4 text-label-md text-on-surface transition-colors hover:bg-surface-container-low disabled:opacity-50"
                    type="button"
                    onClick={() => {
                      setEditingId(null);
                      setFormStaffId(null);
                      setFormWorkDate("");
                      setFormNotes("");
                      setSubmitMsg(null);
                    }}
                    disabled={submitting || !isEditablePeriod}
                  >
                    Hủy
                  </button>
                )}
                <button
                  className="flex-1 h-10 rounded-lg bg-primary px-4 text-label-md text-on-primary shadow-sm transition-colors hover:opacity-90 disabled:opacity-50"
                  type="submit"
                  disabled={submitting || !isEditablePeriod || isBlockedCompensationDate}
                >
                  {submitting ? "Đang xử lý..." : editingId ? "Cập nhật" : "Thêm mới"}
                </button>
              </div>
            </form>
          </SectionCard>

          <ConflictInspector
            title="Cảnh báo trực tiếp"
            description="M05-F02 · xung đột thật theo kỳ"
            conflicts={filteredConflictSchedules
              .map((schedule) => conflictMap.get(schedule.id))
              .filter((detail): detail is ConflictDetail => Boolean(detail))}
            emptyLabel="Không có xung đột ở lịch chuyên gia."
            selectedConflict={selectedConflict?.detail ?? null}
            onSelect={(detail) => {
              const schedule = filteredSchedules.find((item) => item.id === detail.scheduleId);
              if (!schedule) {
                return;
              }
              setSelectedConflict({ schedule, detail });
            }}
            onClose={() => setSelectedConflict(null)}
          />

          {blockedCompensationDates.length > 0 ? (
            <SectionCard title={`${blockedCompensationDates.length} ngày nghỉ bù bị khóa`} description="Không được gán lịch chuyên gia vào các ngày này">
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

          <section className="rounded-lg border border-outline-variant bg-surface-container-lowest p-5 shadow-sm">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-[20px] text-tertiary">rule</span>
              <p className="text-label-sm font-semibold uppercase tracking-wider text-tertiary">Ràng buộc</p>
            </div>
            <ul className="mt-3 space-y-2 font-body-sm text-on-surface-variant">
              <li className="flex items-start gap-2"><span className="mt-1 shrink-0 text-tertiary">•</span>Chuyên gia (`L04`) không được trùng ngày với dịch vụ (`L03`).</li>
              <li className="flex items-start gap-2"><span className="mt-1 shrink-0 text-tertiary">•</span>Không xếp vào ngày nghỉ bù của nhân sự.</li>
              <li className="flex items-start gap-2"><span className="mt-1 shrink-0 text-tertiary">•</span>Chỉ 1 ca `L04` mỗi nhân sự mỗi ngày.</li>
            </ul>
          </section>
        </aside>
      </div>
    </div>

    </DashboardShell>
  );
}
