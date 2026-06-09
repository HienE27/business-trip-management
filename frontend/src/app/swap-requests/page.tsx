"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { useAuth } from "@/components/auth/AuthProvider";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { Schedule, ScheduleExchangeCreate, ScheduleExchangeResponse, Staff } from "@/types/api";

type ExchangeStatus = "PENDING" | "APPROVED" | "REJECTED" | "CANCELLED";

function getStatusBadge(status: ExchangeStatus) {
  if (status === "PENDING") return "bg-tertiary-fixed text-on-tertiary-fixed";
  if (status === "APPROVED") return "bg-secondary-fixed text-on-secondary-fixed";
  if (status === "REJECTED") return "bg-error-container text-on-error-container";
  return "bg-surface-container-high text-on-surface-variant";
}

function getStatusLabel(status: ExchangeStatus) {
  if (status === "PENDING") return "Chờ duyệt";
  if (status === "APPROVED") return "Đã duyệt";
  if (status === "REJECTED") return "Từ chối";
  return "Đã hủy";
}

function getBorderColor(shiftTypeId: string) {
  if (shiftTypeId === "L01") return "border-error";
  if (shiftTypeId === "L02") return "border-primary";
  if (shiftTypeId === "L03") return "border-secondary";
  if (shiftTypeId === "L04") return "border-outline";
  return "border-outline-variant";
}

function formatDate(dateStr: string) {
  try {
    return new Date(dateStr).toLocaleDateString("vi-VN");
  } catch {
    return dateStr;
  }
}

function isManagerLike(staff: Staff | null) {
  return Boolean(staff?.roles?.some((role) => role === "ADMIN" || role === "MANAGER"));
}

export default function SwapRequestsPage() {
  const { user } = useAuth();
  const [exchanges, setExchanges] = useState<ScheduleExchangeResponse[]>([]);
  const [currentUser, setCurrentUser] = useState<Staff | null>(null);
  const [mySchedules, setMySchedules] = useState<Schedule[]>([]);
  const [allSchedules, setAllSchedules] = useState<Schedule[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState("");
  const [message, setMessage] = useState("");
  const [processing, setProcessing] = useState<number | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    requesterScheduleId: "",
    targetScheduleId: "",
    reason: "",
  });

  const fetchExchanges = useCallback(async () => {
    try {
      setLoading(true);
      setMessage("");
      const meRes = await api.get<Staff>("/staff/me");
      setCurrentUser(meRes);

      const managerView = isManagerLike(meRes);
      const exchangePath = managerView ? "/schedule-exchanges" : `/schedule-exchanges/user/${meRes.id}`;
      const exchangeRes = await api.get<ScheduleExchangeResponse[]>(exchangePath);
      setExchanges(exchangeRes ?? []);

      if (!managerView) {
        const schedules = await api.get<Schedule[]>(`/schedules/staff/${meRes.id}`);
        const ownL01 = (schedules ?? []).filter((schedule) => schedule.shiftType.id === "L01");
        setMySchedules(ownL01);

        const periodIds = Array.from(new Set(ownL01.map((schedule) => schedule.periodId)));
        const relatedSchedules = await Promise.all(
          periodIds.map((periodId) => api.get<Schedule[]>(`/schedules/period/${periodId}`)),
        );
        setAllSchedules(
          relatedSchedules
            .flatMap((items) => items ?? [])
            .filter((schedule) => schedule.shiftType.id === "L01" && schedule.staff.id !== meRes.id),
        );
      } else {
        setMySchedules([]);
        setAllSchedules([]);
      }
    } catch (err) {
      setExchanges([]);
      setCurrentUser(null);
      setMySchedules([]);
      setAllSchedules([]);
      setMessage(getErrorMessage(err, "Không thể tải danh sách yêu cầu đổi trực."));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    void fetchExchanges();
  }, [fetchExchanges]);

  const managerMode = isManagerLike(currentUser);

  const filtered = useMemo(() => {
    return exchanges.filter((exchange) => !statusFilter || exchange.status === statusFilter);
  }, [exchanges, statusFilter]);

  const stats = useMemo(() => {
    const total = exchanges.length;
    const pending = exchanges.filter((exchange) => exchange.status === "PENDING").length;
    const approved = exchanges.filter((exchange) => exchange.status === "APPROVED").length;
    const rejected = exchanges.filter((exchange) => exchange.status === "REJECTED").length;
    return { total, pending, approved, rejected };
  }, [exchanges]);

  const selectedRequesterSchedule = useMemo(
    () => mySchedules.find((schedule) => String(schedule.id) === form.requesterScheduleId) ?? null,
    [form.requesterScheduleId, mySchedules],
  );

  const candidateTargetSchedules = useMemo(() => {
    if (!selectedRequesterSchedule) {
      return [];
    }
    return allSchedules.filter(
      (schedule) =>
        schedule.periodId === selectedRequesterSchedule.periodId &&
        schedule.id !== selectedRequesterSchedule.id,
    );
  }, [allSchedules, selectedRequesterSchedule]);

  async function handleApprove(id: number) {
    const reviewerId = currentUser?.id;
    if (!reviewerId) return;
    try {
      setProcessing(id);
      await api.put(`/schedule-exchanges/${id}/approve?reviewerId=${reviewerId}`, {});
      setMessage("Đã duyệt yêu cầu đổi trực.");
      await fetchExchanges();
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi duyệt yêu cầu."));
    } finally {
      setProcessing(null);
    }
  }

  async function handleReject(id: number) {
    const reviewerId = currentUser?.id;
    if (!reviewerId) return;
    try {
      setProcessing(id);
      await api.put(`/schedule-exchanges/${id}/reject?reviewerId=${reviewerId}`, {});
      setMessage("Đã từ chối yêu cầu đổi trực.");
      await fetchExchanges();
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi từ chối yêu cầu."));
    } finally {
      setProcessing(null);
    }
  }

  async function handleCancel(id: number) {
    try {
      setProcessing(id);
      await api.put(`/schedule-exchanges/${id}/cancel`, {});
      setMessage("Đã hủy yêu cầu đổi trực.");
      await fetchExchanges();
    } catch (err) {
      setMessage(getErrorMessage(err, "Lỗi hủy yêu cầu đổi trực."));
    } finally {
      setProcessing(null);
    }
  }

  async function handleCreateRequest() {
    if (!currentUser || !selectedRequesterSchedule || !form.targetScheduleId) {
      setMessage("Vui lòng chọn đầy đủ ca trực của bạn và ca muốn đổi.");
      return;
    }

    try {
      setSubmitting(true);
      const payload: ScheduleExchangeCreate & { periodId: number } = {
        targetStaffId:
          candidateTargetSchedules.find((schedule) => String(schedule.id) === form.targetScheduleId)?.staff.id ?? 0,
        requesterScheduleId: selectedRequesterSchedule.id,
        targetScheduleId: Number(form.targetScheduleId),
        reason: form.reason.trim() || undefined,
        periodId: selectedRequesterSchedule.periodId,
      };

      await api.post(`/schedule-exchanges/requester/${currentUser.id}`, payload);
      setMessage("Đã gửi yêu cầu đổi trực. Quản lý sẽ xem xét và phản hồi.");
      setForm({ requesterScheduleId: "", targetScheduleId: "", reason: "" });
      await fetchExchanges();
    } catch (err) {
      setMessage(getErrorMessage(err, "Không thể gửi yêu cầu đổi trực."));
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <DashboardShell
      activeCode="M02-SWAP"
      description={
        managerMode
          ? "Quản lý và xét duyệt các đề xuất thay đổi lịch trực từ nhân sự các khoa phòng."
          : "Gửi yêu cầu đổi lịch trực 24/24 của bạn và theo dõi trạng thái xử lý."
      }
      title={managerMode ? "Phê duyệt Yêu cầu Đổi trực" : "Yêu cầu Đổi trực"}
    >
      <div className="space-y-6">
        <div className="grid grid-cols-1 gap-4 md:grid-cols-4">
          {[
            { label: "Tổng yêu cầu", value: String(stats.total).padStart(2, "0"), icon: "list_alt", accent: "primary-fixed/50", colorClass: "text-on-surface-variant" },
            { label: "Chờ duyệt", value: String(stats.pending).padStart(2, "0"), icon: "pending_actions", accent: "tertiary-fixed/50", colorClass: "text-tertiary" },
            { label: "Đã duyệt", value: String(stats.approved).padStart(2, "0"), icon: "task_alt", accent: "secondary-fixed/50", colorClass: "text-secondary" },
            { label: "Từ chối", value: String(stats.rejected).padStart(2, "0"), icon: "cancel", accent: "error-container/50", colorClass: "text-error" },
          ].map((card) => (
            <div
              className="relative overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-[0_1px_3px_0_rgba(0,0,0,0.1)]"
              key={card.label}
            >
              <div className={`absolute right-0 top-0 h-24 w-24 rounded-bl-full bg-${card.accent} blur-xl -z-10`} />
              <div className={`flex items-center gap-3 ${card.colorClass}`}>
                <span className="material-symbols-outlined">{card.icon}</span>
                <span className="font-label-md uppercase tracking-wider">{card.label}</span>
              </div>
              <div className="mt-3 font-display-lg text-display-lg text-on-surface">{card.value}</div>
            </div>
          ))}
        </div>

        {!managerMode && (
          <div className="grid gap-6 rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-[0_1px_3px_0_rgba(0,0,0,0.1)] lg:grid-cols-2">
            <div className="space-y-4">
              <div>
                <h2 className="text-lg font-semibold text-on-surface">Gửi yêu cầu đổi trực</h2>
                <p className="mt-1 text-sm text-on-surface-variant">
                  Chỉ hỗ trợ đổi giữa các ca `L01` trong cùng kỳ đã được công bố.
                </p>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium text-on-surface" htmlFor="requesterScheduleId">
                  Ca trực của bạn
                </label>
                <select
                  id="requesterScheduleId"
                  className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary"
                  onChange={(event) =>
                    setForm((prev) => ({ ...prev, requesterScheduleId: event.target.value, targetScheduleId: "" }))
                  }
                  value={form.requesterScheduleId}
                >
                  <option value="">Chọn ca trực của bạn</option>
                  {mySchedules.map((schedule) => (
                    <option key={schedule.id} value={schedule.id}>
                      {formatDate(schedule.workDate)} — {schedule.period?.periodName ?? `Kỳ #${schedule.periodId}`}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium text-on-surface" htmlFor="targetScheduleId">
                  Ca muốn đổi cùng
                </label>
                <select
                  id="targetScheduleId"
                  className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary"
                  onChange={(event) => setForm((prev) => ({ ...prev, targetScheduleId: event.target.value }))}
                  value={form.targetScheduleId}
                >
                  <option value="">Chọn ca trực của người khác</option>
                  {candidateTargetSchedules.map((schedule) => (
                    <option key={schedule.id} value={schedule.id}>
                      {schedule.staff.fullName} — {formatDate(schedule.workDate)}
                    </option>
                  ))}
                </select>
              </div>

              <div className="space-y-2">
                <label className="text-sm font-medium text-on-surface" htmlFor="reason">
                  Lý do
                </label>
                <textarea
                  id="reason"
                  className="min-h-24 w-full rounded-lg border border-outline-variant bg-surface px-3 py-2 text-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary"
                  maxLength={500}
                  onChange={(event) => setForm((prev) => ({ ...prev, reason: event.target.value }))}
                  placeholder="Ví dụ: bận công việc gia đình, cần đổi ngày trực..."
                  value={form.reason}
                />
              </div>

              <button
                className="inline-flex items-center justify-center rounded-lg bg-primary px-4 py-2 text-sm font-medium text-on-primary disabled:cursor-not-allowed disabled:opacity-60"
                disabled={submitting || !form.requesterScheduleId || !form.targetScheduleId}
                onClick={handleCreateRequest}
                type="button"
              >
                {submitting ? "Đang gửi..." : "Gửi yêu cầu đổi trực"}
              </button>
            </div>

            <div className="rounded-xl border border-outline-variant bg-surface p-4">
              <h3 className="text-sm font-semibold text-on-surface">Lưu ý</h3>
              <ul className="mt-3 space-y-2 text-sm text-on-surface-variant">
                <li>- Chỉ đổi được giữa 2 lịch trực `L01` trong cùng kỳ.</li>
                <li>- Kỳ lịch phải ở trạng thái `PUBLISHED`.</li>
                <li>- Hệ thống sẽ tự kiểm tra nghỉ phép và ngày nghỉ bù trước khi duyệt.</li>
                <li>- Bạn có thể hủy yêu cầu khi trạng thái vẫn là chờ duyệt.</li>
              </ul>
            </div>
          </div>
        )}

        <div className="flex items-center justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-[0_1px_3px_0_rgba(0,0,0,0.1)]">
          <div className="flex items-center gap-4">
            <div className="flex flex-col gap-1">
              <label className="font-label-sm text-label-sm text-on-surface-variant" htmlFor="status-filter">
                Trạng thái
              </label>
              <select
                id="status-filter"
                className="rounded-lg border border-outline-variant bg-surface px-3 py-2 font-body-sm text-body-sm outline-none focus:border-primary focus:ring-2 focus:ring-primary"
                onChange={(event) => setStatusFilter(event.target.value)}
                value={statusFilter}
              >
                <option value="">Tất cả trạng thái</option>
                <option value="PENDING">Chờ duyệt</option>
                <option value="APPROVED">Đã duyệt</option>
                <option value="REJECTED">Từ chối</option>
                <option value="CANCELLED">Đã hủy</option>
              </select>
            </div>
          </div>
        </div>

        {message && (
          <div className="rounded-lg border border-primary/20 bg-primary-container/30 px-4 py-3 text-sm text-on-surface">
            {message}
          </div>
        )}

        <div className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-[0_1px_3px_0_rgba(0,0,0,0.1)]">
          <div className="overflow-x-auto">
            {loading ? (
              <div className="flex items-center justify-center py-16">
                <div className="size-6 animate-spin rounded-full border-2 border-primary border-t-transparent" />
              </div>
            ) : filtered.length === 0 ? (
              <div className="py-16 text-center">
                <span className="material-symbols-outlined text-5xl text-outline">swap_horiz</span>
                <p className="mt-4 text-on-surface-variant">Chưa có yêu cầu đổi trực nào.</p>
              </div>
            ) : (
              <table className="w-full border-collapse text-left">
                <thead>
                  <tr className="border-b border-outline-variant bg-[#f8fafc]">
                    <th className="px-5 py-3 font-label-sm text-label-sm uppercase tracking-wider text-on-surface-variant">Người yêu cầu</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm uppercase tracking-wider text-on-surface-variant">Người đổi cùng</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm uppercase tracking-wider text-on-surface-variant">Ca ban đầu</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm uppercase tracking-wider text-on-surface-variant">Ca đề xuất</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm uppercase tracking-wider text-on-surface-variant">Trạng thái</th>
                    <th className="px-5 py-3 text-right font-label-sm text-label-sm uppercase tracking-wider text-on-surface-variant">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/50">
                  {filtered.map((req) => {
                    const canManage = managerMode && req.status === "PENDING";
                    const canCancel = !managerMode && req.status === "PENDING" && req.requester.id === currentUser?.id;
                    return (
                      <tr className="group transition-colors hover:bg-[#f1f5f9]" key={req.id}>
                        <td className="px-5 py-2">
                          <div className="flex flex-col">
                            <span className="font-body-sm font-medium text-on-surface">{req.requester.fullName}</span>
                          </div>
                        </td>
                        <td className="px-5 py-2">
                          <div className="flex flex-col">
                            <span className="font-body-sm text-on-surface">{req.target.fullName}</span>
                          </div>
                        </td>
                        <td className="px-5 py-2">
                          <div className={`flex items-center gap-2 border-l-4 ${getBorderColor(req.requesterSchedule.shiftType.id)} pl-2`}>
                            <span className="material-symbols-outlined text-[16px] text-outline">calendar_today</span>
                            <div className="flex flex-col">
                              <span className={`font-body-sm text-on-surface ${req.status === "APPROVED" ? "line-through" : ""}`}>
                                {formatDate(req.requesterSchedule.workDate)}
                              </span>
                              <span className="font-label-sm text-label-sm text-on-surface-variant">{req.requesterSchedule.shiftType.name}</span>
                            </div>
                          </div>
                        </td>
                        <td className="px-5 py-2">
                          <div className={`flex items-center gap-2 border-l-4 ${getBorderColor(req.targetSchedule.shiftType.id)} pl-2`}>
                            <span className="material-symbols-outlined text-[16px] text-outline">event</span>
                            <div className="flex flex-col">
                              <span className="font-body-sm text-on-surface">{formatDate(req.targetSchedule.workDate)}</span>
                              <span className="font-label-sm text-label-sm text-on-surface-variant">{req.targetSchedule.shiftType.name}</span>
                            </div>
                          </div>
                        </td>
                        <td className="px-5 py-2">
                          <span className={`inline-flex items-center rounded-full px-2 py-1 text-xs font-medium ${getStatusBadge(req.status)}`}>
                            {getStatusLabel(req.status)}
                          </span>
                          {req.reason && <p className="mt-1 max-w-[240px] text-xs text-on-surface-variant">Lý do: {req.reason}</p>}
                          {req.reviewNote && (
                            <p className="mt-1 max-w-[240px] text-xs text-on-surface-variant">Phản hồi: {req.reviewNote}</p>
                          )}
                        </td>
                        <td className="px-5 py-2 text-right">
                          {canManage ? (
                            <div className="flex justify-end gap-2 opacity-0 transition-opacity group-hover:opacity-100">
                              <button
                                className="flex h-8 w-8 items-center justify-center rounded bg-secondary-container/20 text-secondary transition-colors hover:bg-secondary-container"
                                disabled={processing !== null}
                                onClick={() => handleApprove(req.id)}
                                title="Phê duyệt"
                                type="button"
                              >
                                <span className="material-symbols-outlined text-[18px]">check</span>
                              </button>
                              <button
                                className="flex h-8 w-8 items-center justify-center rounded bg-error-container/20 text-error transition-colors hover:bg-error-container"
                                disabled={processing !== null}
                                onClick={() => handleReject(req.id)}
                                title="Từ chối"
                                type="button"
                              >
                                <span className="material-symbols-outlined text-[18px]">close</span>
                              </button>
                            </div>
                          ) : canCancel ? (
                            <button
                              className="rounded-lg border border-outline-variant px-3 py-1.5 text-sm text-on-surface transition-colors hover:bg-surface"
                              disabled={processing !== null}
                              onClick={() => handleCancel(req.id)}
                              type="button"
                            >
                              Hủy yêu cầu
                            </button>
                          ) : (
                            <span className="font-label-sm text-label-sm text-on-surface-variant">
                              {req.reviewedBy ? `Bởi ${req.reviewedBy.fullName}` : ""}
                            </span>
                          )}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
            )}
          </div>

          <div className="flex items-center justify-between border-t border-outline-variant/50 bg-surface-container-lowest px-5 py-3">
            <span className="font-body-sm text-body-sm text-on-surface-variant">
              Hiển thị {filtered.length} của {exchanges.length} yêu cầu
            </span>
            {!managerMode && user?.roles?.includes("STAFF") && (
              <span className="text-xs text-on-surface-variant">Bạn chỉ thấy các yêu cầu liên quan tới mình.</span>
            )}
          </div>
        </div>
      </div>
    </DashboardShell>
  );
}
