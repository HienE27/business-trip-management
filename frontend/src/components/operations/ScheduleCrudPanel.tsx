"use client";

import { FormEvent, useCallback, useEffect, useMemo, useState } from "react";
import { SectionCard } from "@/components/ui/SectionCard";
import { StatusBadge } from "@/components/ui/StatusBadge";
import { api } from "@/lib/api";

// ── Types matching backend ScheduleResponse / ScheduleRequest ──
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

// ── Props ───────────────────────────────────────────────────
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
  // Mon=1..Fri=5: next day. Fri=5 -> +4 (Tue next week), Sat=6 -> +3 (Tue next week), Sun=0 -> +1 (Mon)
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
  // ── State ─────────────────────────────────────────────────
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

  // ── Fetch from API ────────────────────────────────────────
  const fetchSchedules = useCallback(async () => {
    try {
      setLoading(true);
      // Try to fetch schedules by period 1 (default)
      const data = await api.get<ScheduleResponseDTO[]>("/schedules/period/1");
      const filtered = (data ?? []).filter((s) => s.shiftType.id === shiftType);
      setApiRecords(filtered);
      setUseApi(true);

      // Also fetch staff for dropdown
      const staff = await api.get<StaffOption[]>("/staff/active");
      setStaffOptions(
        (staff ?? []).map((s: Record<string, unknown>) => ({
          id: (s as StaffOption).id,
          fullName: (s as StaffOption).fullName ?? (s as Record<string, unknown>).username as string,
        })),
      );
    } catch {
      // Backend not available — fallback to local mock data
      setUseApi(false);
    } finally {
      setLoading(false);
    }
  }, [shiftType]);

  useEffect(() => {
    // eslint-disable-next-line react-hooks/set-state-in-effect
    fetchSchedules();
  }, [fetchSchedules]);

  // ── Metrics ───────────────────────────────────────────────
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

  // ── Helpers ───────────────────────────────────────────────
  function showMessage(msg: string, type: "success" | "error" | "info" = "info") {
    setMessage(msg);
    setMessageType(type);
    if (type === "success") setTimeout(() => setMessage(""), 4000);
  }

  // ── API Submit ────────────────────────────────────────────
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

  // ── Local fallback submit ─────────────────────────────────
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

  // ── Delete ────────────────────────────────────────────────
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

  // ── Edit ──────────────────────────────────────────────────
  function editScheduleApi(record: ScheduleResponseDTO) {
    setForm({
      staffId: record.staff.id.toString(),
      date: record.workDate,
      note: "",
    });
    setEditingId(record.id);
    showMessage(`Đang sửa lịch ngày ${record.workDate}.`, "info");
  }

  function editScheduleLocal(record: ScheduleRecord) {
    setForm({ staffId: record.staff, date: record.date, note: record.note });
    setEditingId(parseInt(record.id) || 0);
    showMessage("Đang sửa lịch đã chọn.", "info");
  }

  // ── Render ────────────────────────────────────────────────
  return (
    <div className="grid gap-4 p-5 max-sm:p-3 xl:grid-cols-[minmax(0,1fr)_340px]">
      <div className="space-y-4">
        {/* Metrics */}
        <section className="grid gap-4 md:grid-cols-3">
          {metrics.map(([label, value]) => (
            <div
              className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]"
              key={label}
            >
              <p className="text-xs font-medium uppercase text-slate-500">{label}</p>
              <p className="mt-3 text-2xl font-semibold">{value}</p>
            </div>
          ))}
        </section>

        {/* Connection status */}
        <div className="flex items-center gap-2 text-xs">
          <span className={`inline-block size-2 rounded-full ${useApi ? "bg-emerald-500" : "bg-amber-500"}`} />
          <span className="text-slate-500">
            {useApi ? "Kết nối API backend thành công" : "Đang dùng dữ liệu mẫu (backend chưa sẵn sàng)"}
          </span>
        </div>

        {/* Table */}
        <SectionCard description={description} title={title}>
          <div className="overflow-x-auto">
            {loading ? (
              <div className="flex items-center justify-center py-12">
                <svg className="size-6 animate-spin text-slate-400" fill="none" viewBox="0 0 24 24">
                  <circle className="opacity-25" cx={12} cy={12} r={10} stroke="currentColor" strokeWidth={4} />
                  <path className="opacity-75" d="M4 12a8 8 0 018-8v4a4 4 0 00-4 4H4z" fill="currentColor" />
                </svg>
              </div>
            ) : useApi ? (
              /* ── API-connected table ── */
              <table className="min-w-full border-collapse text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                  <tr>
                    {["ID", "Ngày", "Nhân sự", "Loại lịch", "Xung đột", "Thao tác"].map((h) => (
                      <th className="border-b border-slate-200 px-4 py-3 font-semibold" key={h}>
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {apiRecords.length === 0 ? (
                    <tr>
                      <td className="px-4 py-8 text-center text-sm text-slate-400" colSpan={6}>
                        Chưa có lịch nào trong kỳ này
                      </td>
                    </tr>
                  ) : (
                    apiRecords.map((record) => (
                      <tr className="border-b border-slate-100 transition-colors hover:bg-slate-50/50" key={record.id}>
                        <td className="px-4 py-3 font-medium text-slate-500">{record.id}</td>
                        <td className="px-4 py-3 font-medium">{record.workDate}</td>
                        <td className="px-4 py-3">{record.staff.fullName}</td>
                        <td className="px-4 py-3">{record.shiftType.name}</td>
                        <td className="px-4 py-3">
                          <StatusBadge tone={record.hasConflict ? "warning" : "success"}>
                            {record.hasConflict ? "Xung đột" : "Hợp lệ"}
                          </StatusBadge>
                        </td>
                        <td className="px-4 py-3">
                          <div className="flex gap-2">
                            <button
                              className="h-8 rounded-md border border-slate-200 px-3 text-xs font-medium transition-colors hover:bg-slate-50"
                              onClick={() => editScheduleApi(record)}
                              type="button"
                            >
                              Sửa
                            </button>
                            <button
                              className="h-8 rounded-md border border-rose-200 px-3 text-xs font-medium text-rose-700 transition-colors hover:bg-rose-50"
                              onClick={() => deleteSchedule(record.id)}
                              type="button"
                            >
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
              /* ── Local fallback table ── */
              <table className="min-w-full border-collapse text-left text-sm">
                <thead className="bg-slate-50 text-xs uppercase text-slate-500">
                  <tr>
                    {["Ngày", "Nhân sự", "Loại lịch", "Chuyên khoa", locationLabel, "Nghỉ bù", "Trạng thái", "Thao tác"].map((h) => (
                      <th className="border-b border-slate-200 px-4 py-3 font-semibold" key={h}>
                        {h}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {localRecords.map((record) => (
                    <tr className="border-b border-slate-100" key={record.id}>
                      <td className="px-4 py-3 font-medium">{record.date}</td>
                      <td className="px-4 py-3">{record.staff}</td>
                      <td className="px-4 py-3">{labelsByShiftType[record.shiftType]}</td>
                      <td className="px-4 py-3">{record.specialty || "-"}</td>
                      <td className="px-4 py-3">{record.location || "-"}</td>
                      <td className="px-4 py-3">{record.compensationDate || "-"}</td>
                      <td className="px-4 py-3">
                        <StatusBadge tone={record.status === "Hợp lệ" ? "success" : "warning"}>
                          {record.status}
                        </StatusBadge>
                      </td>
                      <td className="px-4 py-3">
                        <div className="flex gap-2">
                          <button
                            className="h-8 rounded-md border border-slate-200 px-3 text-xs font-medium"
                            onClick={() => editScheduleLocal(record)}
                            type="button"
                          >
                            Sửa
                          </button>
                          <button
                            className="h-8 rounded-md border border-rose-200 px-3 text-xs font-medium text-rose-700"
                            onClick={() => deleteSchedule(record.id)}
                            type="button"
                          >
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

      {/* ── Form sidebar ────────────────────────────────────── */}
      <aside className="space-y-4">
        <SectionCard
          description={editingId !== null ? "Sửa lịch và kiểm tra ràng buộc phía backend" : "Chọn ngày và nhân sự để tạo lịch mới"}
          title={editingId !== null ? "Sửa lịch" : submitLabel}
        >
          <form className="space-y-3 p-4" onSubmit={useApi ? submitScheduleApi : submitScheduleLocal}>
            <label className="block">
              <span className="text-xs font-medium text-slate-500">Ngày</span>
              <input
                className="mt-1 h-9 w-full rounded-md border border-slate-200 px-3 text-sm outline-none focus:border-slate-400"
                id="schedule-date"
                onChange={(e) => setForm((f) => ({ ...f, date: e.target.value }))}
                type="date"
                value={form.date}
              />
            </label>

            {useApi && staffOptions.length > 0 ? (
              <label className="block">
                <span className="text-xs font-medium text-slate-500">Nhân sự</span>
                <select
                  className="mt-1 h-9 w-full rounded-md border border-slate-200 bg-white px-3 text-sm outline-none focus:border-slate-400"
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
                <span className="text-xs font-medium text-slate-500">Nhân sự</span>
                <input
                  className="mt-1 h-9 w-full rounded-md border border-slate-200 px-3 text-sm outline-none focus:border-slate-400"
                  id="schedule-staff"
                  onChange={(e) => setForm((f) => ({ ...f, staffId: e.target.value }))}
                  placeholder="Nhập tên nhân sự"
                  value={form.staffId}
                />
              </label>
            )}

            <label className="block">
              <span className="text-xs font-medium text-slate-500">Ghi chú</span>
              <input
                className="mt-1 h-9 w-full rounded-md border border-slate-200 px-3 text-sm outline-none focus:border-slate-400"
                id="schedule-note"
                onChange={(e) => setForm((f) => ({ ...f, note: e.target.value }))}
                value={form.note}
              />
            </label>

            <div className="rounded-md bg-slate-50 px-3 py-2 text-xs text-slate-500">
              Loại lịch: <span className="font-semibold text-slate-700">{labelsByShiftType[shiftType]}</span>
            </div>

            {shiftType === "L01" && form.date ? (
              <p className="rounded-md bg-indigo-50 px-3 py-2 text-sm text-indigo-700">
                Nghỉ bù dự kiến: <span className="font-semibold">{getCompensationDate(form.date)}</span>
              </p>
            ) : null}

            {message ? (
              <p
                className={`rounded-md border px-3 py-2 text-sm ${
                  messageType === "error"
                    ? "border-rose-200 bg-rose-50 text-rose-700"
                    : messageType === "success"
                      ? "border-emerald-200 bg-emerald-50 text-emerald-700"
                      : "border-slate-200 bg-slate-50 text-slate-600"
                }`}
              >
                {message}
              </p>
            ) : null}

            <div className="grid grid-cols-2 gap-2">
              <button
                className="h-9 rounded-md bg-slate-950 text-sm font-medium text-white transition-colors hover:bg-slate-800 disabled:cursor-not-allowed disabled:opacity-50"
                disabled={submitting}
                id="schedule-submit"
                type="submit"
              >
                {submitting ? "Đang lưu…" : editingId !== null ? "Cập nhật" : "Thêm mới"}
              </button>
              <button
                className="h-9 rounded-md border border-slate-200 text-sm font-medium transition-colors hover:bg-slate-50"
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
