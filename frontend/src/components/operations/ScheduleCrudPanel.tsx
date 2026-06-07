"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { SectionCard } from "@/components/ui/SectionCard";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { api } from "@/lib/api";

type StaffSummary = {
  id: number;
  fullName: string;
};

type ShiftTypeSummary = {
  id: string;
  name: string;
  isOvernight: boolean;
};

type ScheduleResponseDTO = {
  id: number;
  periodId: number;
  workDate: string;
  staff: StaffSummary;
  shiftType: ShiftTypeSummary;
  requirementId: number | null;
  hasConflict: boolean;
  createdAt: string;
  updatedAt: string;
};

type StaffOption = {
  id: number;
  fullName: string;
};

export type ScheduleRecord = {
  id: string;
  date: string;
  staff: string;
  specialty: string;
  location: string;
  note: string;
  status: string;
  shiftType: "L01" | "L02" | "L03" | "L04";
  compensationDate?: string;
};

type ScheduleCrudPanelProps = {
  title: string;
  description: string;
  shiftType: ScheduleRecord["shiftType"];
  defaultRows: ScheduleRecord[];
  locationLabel: string;
  submitLabel: string;
};

const labelsByShiftType: Record<string, string> = {
  L01: "Lịch trực 24/24",
  L02: "Lịch thông tầm",
  L03: "Phòng khám dịch vụ",
  L04: "Phòng khám chuyên gia",
};

function getCompensationDate(dateValue: string) {
  if (!dateValue) return "";
  const date = new Date(`${dateValue}T00:00:00`);
  const day = date.getDay();
  const plusDays = day === 5 ? 4 : day === 6 ? 3 : day === 0 ? 1 : 1;
  date.setDate(date.getDate() + plusDays);
  return date.toISOString().slice(0, 10);
}

function createEmptyForm() {
  return { staffId: "", date: "", note: "" };
}

export function ScheduleCrudPanel({
  title,
  description,
  shiftType,
  defaultRows,
  locationLabel,
  submitLabel,
}: ScheduleCrudPanelProps) {
  const [apiRecords, setApiRecords] = useState<ScheduleResponseDTO[]>([]);
  const [localRecords, setLocalRecords] = useState<ScheduleRecord[]>(defaultRows);
  const [staffOptions, setStaffOptions] = useState<StaffOption[]>([]);
  const [form, setForm] = useState(createEmptyForm());
  const [editingId, setEditingId] = useState<number | null>(null);
  const [message, setMessage] = useState("");
  const [messageType, setMessageType] = useState<"success" | "error" | "info">("info");
  const [loading, setLoading] = useState(true);
  const [submitting, setSubmitting] = useState(false);
  const [useApi, setUseApi] = useState(false);

  const fetchSchedules = useCallback(async () => {
    try {
      setLoading(true);
      const data = await api.get<ScheduleResponseDTO[]>("/schedules/period/1");
      const filtered = (data ?? []).filter((s) => s.shiftType.id === shiftType);
      setApiRecords(filtered);
      setUseApi(true);
      const staff = await api.get<StaffOption[]>("/staff/active");
      setStaffOptions(
        (staff ?? []).map((s: Record<string, unknown>) => ({
          id: (s as StaffOption).id,
          fullName:
            (s as StaffOption).fullName ??
            ((s as Record<string, unknown>).username as string),
        })),
      );
    } catch {
      setUseApi(false);
    } finally {
      setLoading(false);
    }
  }, [shiftType]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchSchedules();
  }, [fetchSchedules]);

  const metrics = useMemo(() => {
    if (useApi) {
      return [
        ["Tổng lịch", apiRecords.length.toString().padStart(2, "0")],
        ["Hợp lệ", apiRecords.filter((r) => !r.hasConflict).length.toString().padStart(2, "0")],
        ["Xung đột", apiRecords.filter((r) => r.hasConflict).length.toString().padStart(2, "0")],
      ];
    }
    return [
      ["Tổng lịch", localRecords.length.toString().padStart(2, "0")],
      ["Hợp lệ", localRecords.filter((r) => r.status === "Hợp lệ").length.toString().padStart(2, "0")],
      ["Cảnh báo", localRecords.filter((r) => r.status !== "Hợp lệ").length.toString().padStart(2, "0")],
    ];
  }, [useApi, apiRecords, localRecords]);

  function showMessage(msg: string, type: "success" | "error" | "info" = "info") {
    setMessage(msg);
    setMessageType(type);
    if (type === "success") setTimeout(() => setMessage(""), 4000);
  }

  async function submitScheduleApi(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.date || !form.staffId) {
      showMessage("Cần chọn ngày và nhân sự.", "error");
      return;
    }
    try {
      setSubmitting(true);
      const body = {
        periodId: 1,
        workDate: form.date,
        staffId: parseInt(form.staffId),
        shiftTypeId: shiftType,
      };
      if (editingId !== null) {
        await api.put(`/schedules/${editingId}`, body);
        showMessage("Đã cập nhật lịch.", "success");
      } else {
        await api.post("/schedules", body);
        showMessage("Đã thêm lịch mới.", "success");
      }
      setForm(createEmptyForm());
      setEditingId(null);
      await fetchSchedules();
    } catch (err) {
      showMessage(err instanceof Error ? err.message : "Lỗi lưu lịch", "error");
    } finally {
      setSubmitting(false);
    }
  }

  function submitScheduleLocal(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (!form.date || !form.staffId.trim()) {
      showMessage("Cần chọn ngày và nhập nhân sự.", "error");
      return;
    }
    const newRecord: ScheduleRecord = {
      id: editingId?.toString() ?? crypto.randomUUID(),
      date: form.date,
      staff: form.staffId,
      specialty: "",
      location: "",
      note: form.note,
      status: "Hợp lệ",
      shiftType,
      compensationDate: shiftType === "L01" ? getCompensationDate(form.date) : undefined,
    };
    if (editingId !== null) {
      setLocalRecords((c) => c.map((r) => (r.id === editingId.toString() ? newRecord : r)));
      showMessage("Đã cập nhật lịch.", "success");
    } else {
      setLocalRecords((c) => [newRecord, ...c]);
      showMessage("Đã thêm lịch mới.", "success");
    }
    setForm(createEmptyForm());
    setEditingId(null);
  }

  async function deleteSchedule(id: number | string) {
    if (!confirm("Bạn có chắc muốn xóa lịch này?")) return;
    if (useApi) {
      try {
        await api.delete(`/schedules/${id}`);
        showMessage("Đã xóa lịch.", "success");
        if (editingId === id) {
          setEditingId(null);
          setForm(createEmptyForm());
        }
        await fetchSchedules();
      } catch (err) {
        showMessage(err instanceof Error ? err.message : "Lỗi xóa lịch", "error");
      }
    } else {
      setLocalRecords((c) => c.filter((r) => r.id !== id.toString()));
      showMessage("Đã xóa lịch khỏi danh sách local.", "success");
    }
  }

  function editScheduleApi(record: ScheduleResponseDTO) {
    setForm({ staffId: record.staff.id.toString(), date: record.workDate, note: "" });
    setEditingId(record.id);
    showMessage(`Đang sửa lịch ngày ${record.workDate}.`, "info");
  }

  function editScheduleLocal(record: ScheduleRecord) {
    setForm({ staffId: record.staff, date: record.date, note: record.note });
    setEditingId(parseInt(record.id) || 0);
    showMessage("Đang sửa lịch đã chọn.", "info");
  }

  const tableHeaders = ["ID", "Ngày", "Nhân sự", "Loại lịch", "Xung đột", "Thao tác"];
  const localHeaders = ["Ngày", "Nhân sự", "Loại lịch", "Chuyên khoa", locationLabel, "Nghỉ bù", "Trạng thái", "Thao tác"];

  return (
    <div className="grid gap-6 xl:grid-cols-[minmax(0,1fr)_340px]">
      <div className="space-y-6">
        <section className="grid gap-4 md:grid-cols-3">
          {metrics.map(([label, value]) => (
            <div
              className="rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm transition-colors hover:bg-surface-container-low"
              key={label}
            >
              <p className="text-[11px] font-semibold uppercase tracking-[0.08em] text-on-surface-variant">{label}</p>
              <p className="mt-3 text-[28px] font-bold leading-9 text-on-surface">{value}</p>
            </div>
          ))}
        </section>

        <div className="flex items-center gap-2 rounded-lg border border-outline-variant/60 bg-surface-container-low px-4 py-2 text-sm text-on-surface-variant">
          <span
            className={`inline-block size-2 rounded-full ${useApi ? "bg-secondary" : "bg-tertiary"}`}
          />
          {useApi
            ? "Kết nối API backend thành công"
            : "Đang dùng dữ liệu mẫu (backend chưa sẵn sàng)"}
        </div>

        <SectionCard description={description} title={title}>
          <div className="overflow-x-auto">
            {loading ? (
              <div className="flex items-center justify-center py-16">
                <svg className="size-6 animate-spin text-outline" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx="12" cy="12" r="10" stroke="currentColor" strokeWidth="4" />
                  <path className="opacity-75" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" fill="currentColor" />
                </svg>
              </div>
            ) : useApi ? (
              <table className="min-w-full border-collapse text-left text-sm text-on-surface">
                <thead className="bg-surface-container-low">
                  <tr className="border-b border-outline-variant/80">
                    {tableHeaders.map((h) => (
                      <th
                        className="px-5 py-3 text-[11px] font-semibold uppercase tracking-[0.08em] text-on-surface-variant"
                        key={h}
                        scope="col"
                      >
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {apiRecords.length === 0 ? (
                    <tr>
                      <td className="px-5 py-10 text-center text-sm text-on-surface-variant" colSpan={6}>
                        Chưa có lịch nào trong kỳ này.
                      </td>
                    </tr>
                  ) : (
                    apiRecords.map((record) => (
                      <tr
                        className="border-b border-outline-variant/60 transition-colors hover:bg-surface-container-low/60 last:border-0"
                        key={record.id}
                      >
                        <td className="px-5 py-4 font-medium text-on-surface-variant">{record.id}</td>
                        <td className="px-5 py-4 font-medium">{record.workDate}</td>
                        <td className="px-5 py-4">{record.staff.fullName}</td>
                        <td className="px-5 py-4">{record.shiftType.name}</td>
                        <td className="px-5 py-4">
                          <StatusBadge tone={record.hasConflict ? "warning" : "success"}>
                            {record.hasConflict ? "Xung đột" : "Hợp lệ"}
                          </StatusBadge>
                        </td>
                        <td className="px-5 py-4">
                          <div className="flex items-center gap-2">
                            <button
                              className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-xs font-semibold text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
                              onClick={() => editScheduleApi(record)}
                              type="button"
                            >
                              <span aria-hidden="true" className="material-symbols-outlined text-[16px]">edit</span>
                              Sửa
                            </button>
                            <button
                              className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-error/20 bg-error-container px-3 text-xs font-semibold text-on-error-container transition-colors hover:brightness-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error/30"
                              onClick={() => deleteSchedule(record.id)}
                              type="button"
                            >
                              <span aria-hidden="true" className="material-symbols-outlined text-[16px]">delete</span>
                              Xóa
                            </button>
                          </div>
                        </td>
                      </tr>
                    ))
                  )}
                </tbody>
              </table>
            ) : (
              <table className="min-w-full border-collapse text-left text-sm text-on-surface">
                <thead className="bg-surface-container-low">
                  <tr className="border-b border-outline-variant/80">
                    {localHeaders.map((h) => (
                      <th
                        className="px-5 py-3 text-[11px] font-semibold uppercase tracking-[0.08em] text-on-surface-variant"
                        key={h}
                        scope="col"
                      >
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {localRecords.map((record) => (
                    <tr
                      className="border-b border-outline-variant/60 transition-colors hover:bg-surface-container-low/60 last:border-0"
                      key={record.id}
                    >
                      <td className="px-5 py-4 font-medium">{record.date}</td>
                      <td className="px-5 py-4">{record.staff}</td>
                      <td className="px-5 py-4">{labelsByShiftType[record.shiftType]}</td>
                      <td className="px-5 py-4">{record.specialty || "-"}</td>
                      <td className="px-5 py-4">{record.location || "-"}</td>
                      <td className="px-5 py-4">{record.compensationDate || "-"}</td>
                      <td className="px-5 py-4">
                        <StatusBadge tone={record.status === "Hợp lệ" ? "success" : "warning"}>
                          {record.status}
                        </StatusBadge>
                      </td>
                      <td className="px-5 py-4">
                        <div className="flex items-center gap-2">
                          <button
                            className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-xs font-semibold text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
                            onClick={() => editScheduleLocal(record)}
                            type="button"
                          >
                            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">edit</span>
                            Sửa
                          </button>
                          <button
                            className="inline-flex h-8 items-center gap-1.5 rounded-lg border border-error/20 bg-error-container px-3 text-xs font-semibold text-on-error-container transition-colors hover:brightness-90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error/30"
                            onClick={() => deleteSchedule(record.id)}
                            type="button"
                          >
                            <span aria-hidden="true" className="material-symbols-outlined text-[16px]">delete</span>
                            Xóa
                          </button>
                        </div>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            )}
          </div>
        </SectionCard>
      </div>

      <aside className="space-y-6">
        <SectionCard
          description={editingId !== null ? "Sửa lịch và kiểm tra ràng buộc phía backend" : "Chọn ngày và nhân sự để tạo lịch mới"}
          title={editingId !== null ? "Sửa lịch" : submitLabel}
        >
          <form className="space-y-4 px-5 py-4" onSubmit={useApi ? submitScheduleApi : submitScheduleLocal}>
            <label className="block">
              <span className="text-xs font-semibold uppercase tracking-[0.06em] text-on-surface-variant">Ngày</span>
              <input
                className="mt-2 h-10 w-full rounded-xl border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface outline-none transition-colors focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
                id="schedule-date"
                onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))}
                type="date"
                value={form.date}
              />
            </label>

            {useApi && staffOptions.length > 0 ? (
              <label className="block">
                <span className="text-xs font-semibold uppercase tracking-[0.06em] text-on-surface-variant">Nhân sự</span>
                <select
                  className="mt-2 h-10 w-full appearance-none rounded-xl border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface outline-none transition-colors focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
                  id="schedule-staff"
                  onChange={(e) => setForm((f) => ({ ...f, staffId: e.target.value }))}
                  value={form.staffId}
                >
                  <option value="">-- Chọn nhân sự --</option>
                  {staffOptions.map((s) => (
                    <option key={s.id} value={s.id}>
                      {s.fullName}
                    </option>
                  ))}
                </select>
              </label>
            ) : (
              <label className="block">
                <span className="text-xs font-semibold uppercase tracking-[0.06em] text-on-surface-variant">Nhân sự</span>
                <input
                  className="mt-2 h-10 w-full rounded-xl border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface outline-none transition-colors placeholder:text-on-surface-variant/60 focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
                  id="schedule-staff"
                  onChange={(e) => setForm((f) => ({ ...f, staffId: e.target.value }))}
                  placeholder="Nhập tên nhân sự"
                  value={form.staffId}
                />
              </label>
            )}

            <label className="block">
              <span className="text-xs font-semibold uppercase tracking-[0.06em] text-on-surface-variant">Ghi chú</span>
              <input
                className="mt-2 h-10 w-full rounded-xl border border-outline-variant bg-surface-container-lowest px-3 text-sm text-on-surface outline-none transition-colors placeholder:text-on-surface-variant/60 focus-visible:border-primary focus-visible:ring-2 focus-visible:ring-primary/20"
                id="schedule-note"
                onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))}
                value={form.note}
              />
            </label>

            <div className="rounded-xl border border-outline-variant/60 bg-surface-container-low px-3.5 py-2.5 text-sm text-on-surface-variant">
              Loại lịch: <span className="font-semibold text-on-surface">{labelsByShiftType[shiftType]}</span>
            </div>

            {shiftType === "L01" && form.date ? (
              <div className="rounded-xl border border-primary/15 bg-primary-fixed/60 px-3.5 py-2.5 text-sm text-on-primary-fixed-variant">
                Nghỉ bù dự kiến: <span className="font-semibold">{getCompensationDate(form.date)}</span>
              </div>
            ) : null}

            {message ? (
              <p
                className={`rounded-xl border px-3.5 py-2.5 text-sm ${
                  messageType === "error"
                    ? "border-error/20 bg-error-container text-on-error-container"
                    : messageType === "success"
                      ? "border-secondary/15 bg-secondary-container/70 text-on-secondary-container"
                      : "border-outline-variant bg-surface-container-low text-on-surface"
                }`}
              >
                {message}
              </p>
            ) : null}

            <div className="grid grid-cols-2 gap-3">
              <button
                className="inline-flex h-10 items-center justify-center gap-2 rounded-xl bg-primary px-4 text-sm font-semibold text-on-primary shadow-sm transition-colors hover:brightness-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30 disabled:cursor-not-allowed disabled:opacity-50"
                disabled={submitting}
                id="schedule-submit"
                type="submit"
              >
                {submitting ? "Đang lưu…" : editingId !== null ? "Cập nhật" : "Thêm mới"}
              </button>
              <button
                className="inline-flex h-10 items-center justify-center gap-2 rounded-xl border border-outline-variant bg-surface-container-lowest px-4 text-sm font-semibold text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/30"
                id="schedule-reset"
                onClick={() => {
                  setEditingId(null);
                  setForm(createEmptyForm());
                  setMessage("");
                }}
                type="button"
              >
                Làm mới
              </button>
            </div>
          </form>
        </SectionCard>
      </aside>
    </div>
  );
}
