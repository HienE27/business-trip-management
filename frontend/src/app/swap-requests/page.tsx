"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { useAuth } from "@/components/auth/AuthProvider";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type { Schedule, ScheduleExchangeResponse, Staff, ConflictCheckResponse } from "@/types/api";

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

function formatDateFull(dateStr: string) {
  try {
    return new Date(dateStr).toLocaleDateString("vi-VN", {
      weekday: "long",
      year: "numeric",
      month: "long",
      day: "numeric",
    });
  } catch {
    return dateStr;
  }
}

function isManagerLike(staff: Staff | null) {
  return Boolean(staff?.roles?.some((role) => role === "ADMIN" || role === "MANAGER"));
}

export default function SwapRequestsPage() {
  const searchParams = useSearchParams();
  const { user: authUser } = useAuth();
  const [exchanges, setExchanges] = useState<ScheduleExchangeResponse[]>([]);
  const [currentUser, setCurrentUser] = useState<Staff | null>(null);
  const [mySchedules, setMySchedules] = useState<Schedule[]>([]);
  const [allSchedules, setAllSchedules] = useState<Schedule[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState("");
  const [message, setMessage] = useState("");
  const [processing, setProcessing] = useState<number | null>(null);
  const [conflictWarning, setConflictWarning] = useState<{ periodId: number; totalConflicts: number } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    requesterScheduleId: "",
    targetScheduleId: "",
    reason: "",
  });

  // Sync global search ?q= into local statusFilter for keyword-based filtering
  const globalQuery = searchParams.get("q") ?? "";
  const [searchKeyword, setSearchKeyword] = useState(globalQuery);

  useEffect(() => {
    setSearchKeyword(globalQuery);
  }, [globalQuery]);

  // Detail modal state
  const [selectedExchange, setSelectedExchange] = useState<ScheduleExchangeResponse | null>(null);
  const [reviewNote, setReviewNote] = useState("");

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

  const managerMode = Boolean(authUser?.roles?.some((role: string) => role === "ADMIN" || role === "MANAGER")) || isManagerLike(currentUser);

  const filtered = useMemo(() => {
    return exchanges.filter((exchange) => {
      if (statusFilter && exchange.status !== statusFilter) return false;
      if (searchKeyword.trim()) {
        const kw = searchKeyword.toLowerCase();
        const matchRequester = exchange.requester?.fullName?.toLowerCase().includes(kw);
        const matchTarget = exchange.target?.fullName?.toLowerCase().includes(kw);
        const matchReason = exchange.reason?.toLowerCase().includes(kw);
        if (!matchRequester && !matchTarget && !matchReason) return false;
      }
      return true;
    });
  }, [exchanges, statusFilter, searchKeyword]);

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
    if (!selectedRequesterSchedule) return [];
    return allSchedules.filter(
      (schedule) =>
        schedule.periodId === selectedRequesterSchedule.periodId &&
        schedule.id !== selectedRequesterSchedule.id,
    );
  }, [allSchedules, selectedRequesterSchedule]);

  async function handleApprove(id: number, periodId: number) {
    const reviewerId = currentUser?.id;
    if (!reviewerId) return;
    try {
      setProcessing(id);
      setConflictWarning(null);

      const conflictRes = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`);
      if (conflictRes.hasConflicts && conflictRes.totalConflicts > 0) {
        setConflictWarning({ periodId, totalConflicts: conflictRes.totalConflicts });
        setMessage(
          `Phát hiện ${conflictRes.totalConflicts} xung đột lịch trong kỳ. Không thể duyệt đổi trực khi còn xung đột.`
        );
        return;
      }

      await api.approveExchange(id, reviewerId, reviewNote || undefined);
      setMessage("Đã duyệt yêu cầu đổi trực.");
      setSelectedExchange(null);
      setReviewNote("");
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
      await api.rejectExchange(id, reviewerId, reviewNote || undefined);
      setMessage("Đã từ chối yêu cầu đổi trực.");
      setSelectedExchange(null);
      setReviewNote("");
      setConflictWarning(null);
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
      await api.cancelExchange(id);
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
      const payload = {
        periodId: selectedRequesterSchedule.periodId,
        requesterScheduleId: selectedRequesterSchedule.id,
        targetScheduleId: Number(form.targetScheduleId),
        reason: form.reason.trim() || undefined,
      };

      await api.createExchange(currentUser.id, payload);
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
      activeSection="shift-swaps"
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
            { label: "Tổng yêu cầu", value: String(stats.total).padStart(2, "0"), icon: "list_alt", colorClass: "text-on-surface-variant" },
            { label: "Chờ duyệt", value: String(stats.pending).padStart(2, "0"), icon: "pending_actions", colorClass: "text-tertiary" },
            { label: "Đã duyệt", value: String(stats.approved).padStart(2, "0"), icon: "task_alt", colorClass: "text-secondary" },
            { label: "Từ chối", value: String(stats.rejected).padStart(2, "0"), icon: "cancel", colorClass: "text-error" },
          ].map((card) => (
            <div
              className="flex items-center gap-3 rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm"
              key={card.label}
            >
              <span className={`material-symbols-outlined ${card.colorClass}`}>{card.icon}</span>
              <div>
                <p className="text-label-sm text-on-surface-variant">{card.label}</p>
                <p className="mt-1 text-display-lg text-on-surface font-bold">{card.value}</p>
              </div>
            </div>
          ))}
        </div>

        {!managerMode && (
          <section className="grid gap-6 rounded-xl border border-outline-variant bg-surface-container-lowest p-5 shadow-sm lg:grid-cols-2">
            <div className="space-y-4">
              <h2 className="text-[18px] font-semibold text-on-surface">Gửi yêu cầu đổi trực</h2>
              <p className="text-sm text-on-surface-variant">
                Chỉ hỗ trợ đổi giữa các ca L01 trong cùng kỳ đã được công bố.
              </p>

              <div className="space-y-3">
                <div>
                  <label className="mb-1.5 block text-[13px] font-medium text-on-surface" htmlFor="requesterScheduleId">
                    Ca trực của bạn
                  </label>
                  {mySchedules.length === 0 ? (
                    <div className="rounded-lg border border-tertiary/30 bg-tertiary-container/20 px-3 py-2.5 text-[13px] text-on-surface">
                      <span className="material-symbols-outlined text-[16px] text-tertiary align-middle mr-1.5">info</span>
                      Bạn chưa có ca trực L01 nào trong kỳ đã công bố.
                    </div>
                  ) : (
                  <select
                    className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5 text-body-sm text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary"
                    id="requesterScheduleId"
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
                  )}
                </div>

                <div>
                  <label className="mb-1.5 block text-[13px] font-medium text-on-surface" htmlFor="targetScheduleId">
                    Ca muốn đổi cùng
                  </label>
                  {!selectedRequesterSchedule ? (
                    <div className="rounded-lg border border-outline-variant bg-surface px-3 py-2.5 text-[13px] text-on-surface-variant">
                      Vui lòng chọn ca trực của bạn trước.
                    </div>
                  ) : candidateTargetSchedules.length === 0 ? (
                    <div className="rounded-lg border border-tertiary/30 bg-tertiary-container/20 px-3 py-2.5 text-[13px] text-on-surface">
                      <span className="material-symbols-outlined text-[16px] text-tertiary align-middle mr-1.5">info</span>
                      Không có ca L01 nào của người khác trong cùng kỳ.
                    </div>
                  ) : (
                    <select
                      className="w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5 text-body-sm text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary"
                      id="targetScheduleId"
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
                  )}
                </div>

                <div>
                  <label className="mb-1.5 block text-[13px] font-medium text-on-surface" htmlFor="exchange-reason">
                    Lý do
                  </label>
                  <textarea
                    className="min-h-20 w-full rounded-lg border border-outline-variant bg-surface px-3 py-2.5 text-body-sm text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary resize-none"
                    id="exchange-reason"
                    maxLength={500}
                    onChange={(event) => setForm((prev) => ({ ...prev, reason: event.target.value }))}
                    placeholder="Ví dụ: bận công việc gia đình, cần đổi ngày trực..."
                    value={form.reason}
                  />
                </div>

                <button
                  className="inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-[14px] font-semibold text-on-primary transition-colors hover:brightness-110 disabled:opacity-50 disabled:cursor-not-allowed"
                  disabled={submitting || !form.requesterScheduleId || !form.targetScheduleId}
                  onClick={handleCreateRequest}
                  type="button"
                >
                  {submitting ? "Đang gửi..." : "Gửi yêu cầu đổi trực"}
                </button>
              </div>
            </div>

            <div className="rounded-xl border border-outline-variant bg-surface p-4">
              <h3 className="text-[14px] font-semibold text-on-surface">Lưu ý</h3>
              <ul className="mt-3 space-y-2 text-[13px] text-on-surface-variant">
                <li className="flex items-start gap-2">
                  <span className="material-symbols-outlined text-[16px] text-outline shrink-0 mt-0.5">info</span>
                  Chỉ đổi được giữa 2 lịch trực L01 trong cùng kỳ.
                </li>
                <li className="flex items-start gap-2">
                  <span className="material-symbols-outlined text-[16px] text-outline shrink-0 mt-0.5">info</span>
                  Kỳ lịch phải ở trạng thái PUBLISHED.
                </li>
                <li className="flex items-start gap-2">
                  <span className="material-symbols-outlined text-[16px] text-outline shrink-0 mt-0.5">info</span>
                  Hệ thống tự kiểm tra nghỉ phép và ngày nghỉ bù trước khi duyệt.
                </li>
                <li className="flex items-start gap-2">
                  <span className="material-symbols-outlined text-[16px] text-outline shrink-0 mt-0.5">info</span>
                  Bạn có thể hủy yêu cầu khi trạng thái vẫn là chờ duyệt.
                </li>
              </ul>
            </div>
          </section>
        )}

        {/* Filter bar */}
        <section className="flex items-center justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
          <div className="flex items-center gap-4">
            <div className="flex flex-col gap-1">
              <label className="font-label-sm text-label-sm text-on-surface-variant" htmlFor="status-filter">
                Trạng thái
              </label>
              <select
                className="rounded-lg border border-outline-variant bg-surface px-3 py-2 font-body-sm text-body-sm text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary cursor-pointer"
                id="status-filter"
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
          <button
            className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-[13px] font-medium text-on-surface shadow-sm transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
            onClick={() => void fetchExchanges()}
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">refresh</span>
            Làm mới
          </button>
        </section>

        {message && (
          <div className="rounded-lg border border-primary/20 bg-primary-container/30 px-4 py-3 text-sm text-on-surface">
            {message}
          </div>
        )}

        {conflictWarning && (
          <div className="rounded-lg border border-error/30 bg-error-container/40 px-4 py-3 text-sm text-error flex items-start gap-2">
            <span className="material-symbols-outlined text-[18px] shrink-0 mt-0.5">warning</span>
            <span>
              Phát hiện <strong>{conflictWarning.totalConflicts} xung đột</strong> trong kỳ lịch. Vui lòng giải quyết xung đột trước khi duyệt đổi trực.
            </span>
          </div>
        )}

        {/* Table */}
        <div className="overflow-hidden rounded-xl border border-outline-variant bg-surface-container-lowest shadow-sm">
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
                  <tr className="border-b border-outline-variant bg-surface-container-low">
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant">Người yêu cầu</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant">Người đổi cùng</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant">Ca ban đầu</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant">Ca đề xuất</th>
                    <th className="px-5 py-3 font-label-sm text-label-sm text-on-surface-variant">Trạng thái</th>
                    <th className="px-5 py-3 text-right font-label-sm text-label-sm text-on-surface-variant">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/50">
                  {filtered.map((req) => {
                    const canManage = managerMode && req.status === "PENDING";
                    const canCancel = !managerMode && req.status === "PENDING" && req.requester.id === currentUser?.id;
                    return (
                      <tr className="group transition-colors hover:bg-surface-container-lowest" key={req.id}>
                        <td className="px-5 py-4">
                          <span className="font-body-sm font-medium text-on-surface">{req.requester.fullName}</span>
                        </td>
                        <td className="px-5 py-4">
                          <span className="font-body-sm text-on-surface">{req.target.fullName}</span>
                        </td>
                        <td className="px-5 py-4">
                          <div className={`flex items-center gap-2 border-l-4 ${getBorderColor(req.requesterSchedule.shiftType.id)} pl-2`}>
                            <div className="flex flex-col">
                              <span className={`font-body-sm text-on-surface ${req.status === "APPROVED" ? "line-through" : ""}`}>
                                {formatDate(req.requesterSchedule.workDate)}
                              </span>
                              <span className="font-label-sm text-label-sm text-on-surface-variant">{req.requesterSchedule.shiftType.name}</span>
                            </div>
                          </div>
                        </td>
                        <td className="px-5 py-4">
                          <div className={`flex items-center gap-2 border-l-4 ${getBorderColor(req.targetSchedule.shiftType.id)} pl-2`}>
                            <div className="flex flex-col">
                              <span className="font-body-sm text-on-surface">{formatDate(req.targetSchedule.workDate)}</span>
                              <span className="font-label-sm text-label-sm text-on-surface-variant">{req.targetSchedule.shiftType.name}</span>
                            </div>
                          </div>
                        </td>
                        <td className="px-5 py-4">
                          <span className={`inline-flex items-center rounded-full px-2.5 py-1 text-xs font-semibold ${getStatusBadge(req.status)}`}>
                            {getStatusLabel(req.status)}
                          </span>
                          {req.reason && (
                            <p className="mt-1.5 max-w-[220px] truncate text-[11px] text-on-surface-variant" title={req.reason}>
                              Lý do: {req.reason}
                            </p>
                          )}
                          {req.reviewNote && (
                            <p className="mt-1 max-w-[220px] truncate rounded-lg bg-surface-container-low px-2 py-1 text-[11px] text-secondary" title={req.reviewNote}>
                              Phản hồi: {req.reviewNote}
                            </p>
                          )}
                          {req.reviewedBy && (
                            <p className="mt-1 text-[11px] text-outline">
                              Bởi {req.reviewedBy.fullName}
                            </p>
                          )}
                        </td>
                        <td className="px-5 py-4 text-right">
                          {canManage ? (
                            <div className="flex justify-end gap-2 opacity-0 group-hover:opacity-100 transition-opacity">
                              <button
                                className="flex h-8 w-8 items-center justify-center rounded bg-secondary-container/20 text-secondary transition-colors hover:bg-secondary-container disabled:opacity-50"
                                disabled={processing !== null}
                                onClick={() => { setSelectedExchange(req); setReviewNote(req.reviewNote ?? ""); }}
                                title="Xem chi tiết &amp; duyệt"
                                type="button"
                              >
                                <span className="material-symbols-outlined text-[18px]">visibility</span>
                              </button>
                            </div>
                          ) : (
                            <span className="font-label-sm text-label-sm text-on-surface-variant">
                              {req.status === "PENDING" && !managerMode && (
                                <button
                                  className="rounded-lg border border-outline-variant px-3 py-1.5 text-[13px] text-error transition-colors hover:bg-error-container"
                                  disabled={processing !== null}
                                  onClick={() => handleCancel(req.id)}
                                  type="button"
                                >
                                  Hủy
                                </button>
                              )}
                              {req.status !== "PENDING" && (
                                <button
                                  className="flex h-8 w-8 items-center justify-center rounded text-outline transition-colors hover:bg-surface-container hover:text-primary"
                                  onClick={() => { setSelectedExchange(req); setReviewNote(req.reviewNote ?? ""); }}
                                  title="Xem chi tiết"
                                  type="button"
                                >
                                  <span className="material-symbols-outlined text-[18px]">visibility</span>
                                </button>
                              )}
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
              Hiển thị {filtered.length} / {exchanges.length} yêu cầu
            </span>
            {!managerMode && authUser?.roles?.includes("STAFF") && (
              <span className="text-[12px] text-on-surface-variant">
                Chỉ thấy các yêu cầu liên quan tới bạn.
              </span>
            )}
          </div>
        </div>
      </div>

      {/* Detail / Review Modal */}
      {selectedExchange && (
        <div
          aria-label="Chi tiết yêu cầu đổi trực"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          role="dialog"
        >
          <div className="absolute inset-0 bg-black/40" onClick={() => { setSelectedExchange(null); setConflictWarning(null); }} />
          <div className="relative w-full max-w-lg rounded-xl border border-outline-variant bg-surface-container-lowest shadow-2xl">
            <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary-fixed">
                  <span className="material-symbols-outlined text-[20px] text-primary">swap_horiz</span>
                </div>
                <div>
                  <h2 className="text-[18px] font-semibold text-on-surface">Chi tiết đổi trực</h2>
                  <p className="text-[12px] text-on-surface-variant">#{selectedExchange.id}</p>
                </div>
              </div>
              <button
                className="flex h-9 w-9 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors"
                onClick={() => setSelectedExchange(null)}
                title="Đóng"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            <div className="px-6 py-5 space-y-4">
              {/* People */}
              <div className="grid grid-cols-2 gap-4">
                <div className="rounded-lg border border-outline-variant bg-surface p-3">
                  <p className="text-label-sm text-on-surface-variant mb-1">Người yêu cầu</p>
                  <p className="text-[14px] font-semibold text-on-surface">{selectedExchange.requester.fullName}</p>
                </div>
                <div className="rounded-lg border border-outline-variant bg-surface p-3">
                  <p className="text-label-sm text-on-surface-variant mb-1">Người đổi cùng</p>
                  <p className="text-[14px] font-semibold text-on-surface">{selectedExchange.target.fullName}</p>
                </div>
              </div>

              {/* Schedule comparison */}
              <div className="grid grid-cols-2 gap-4">
                <div className={`rounded-lg border-l-4 border p-3 ${getBorderColor(selectedExchange.requesterSchedule.shiftType.id)} bg-surface`}>
                  <p className="text-label-sm text-on-surface-variant mb-1">Ca ban đầu</p>
                  <p className="text-[13px] font-medium text-on-surface">{formatDateFull(selectedExchange.requesterSchedule.workDate)}</p>
                  <p className="text-[12px] text-on-surface-variant">{selectedExchange.requesterSchedule.shiftType.name}</p>
                  {selectedExchange.status === "APPROVED" && (
                    <span className="mt-1.5 inline-flex items-center gap-1 text-[11px] text-secondary">
                      <span className="material-symbols-outlined text-[12px]">check</span> Đã đổi
                    </span>
                  )}
                </div>
                <div className={`rounded-lg border-l-4 border p-3 ${getBorderColor(selectedExchange.targetSchedule.shiftType.id)} bg-surface`}>
                  <p className="text-label-sm text-on-surface-variant mb-1">Ca đề xuất</p>
                  <p className="text-[13px] font-medium text-on-surface">{formatDateFull(selectedExchange.targetSchedule.workDate)}</p>
                  <p className="text-[12px] text-on-surface-variant">{selectedExchange.targetSchedule.shiftType.name}</p>
                </div>
              </div>

              {/* Status + reason */}
              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <p className="text-label-sm text-on-surface-variant">Trạng thái</p>
                  <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${getStatusBadge(selectedExchange.status)}`}>
                    {getStatusLabel(selectedExchange.status)}
                  </span>
                </div>

                {selectedExchange.reason && (
                  <div>
                    <p className="text-label-sm text-on-surface-variant mb-1">Lý do</p>
                    <p className="text-[13px] text-on-surface leading-relaxed">{selectedExchange.reason}</p>
                  </div>
                )}

                {selectedExchange.reviewNote && (
                  <div className="rounded-lg bg-surface-container-low p-3">
                    <p className="text-label-sm text-on-surface-variant mb-1">Phản hồi</p>
                    <p className="text-[13px] text-on-surface">{selectedExchange.reviewNote}</p>
                  </div>
                )}

                {managerMode && selectedExchange.status === "PENDING" && (
                  <div className="space-y-2">
                    <label className="text-[13px] font-semibold text-on-surface" htmlFor="exchange-review-note">
                      Ghi chú duyệt <span className="text-outline font-normal">(tùy chọn)</span>
                    </label>
                    <textarea
                      className="w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2.5 text-[13px] text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 resize-none"
                      id="exchange-review-note"
                      rows={3}
                      placeholder="Nhập ghi chú phê duyệt hoặc lý do từ chối..."
                      value={reviewNote}
                      onChange={(e) => setReviewNote(e.target.value)}
                    />
                  </div>
                )}
              </div>
            </div>

            <div className="flex items-center gap-3 px-6 py-4 border-t border-outline-variant">
              <button
                className="flex-1 rounded-lg border border-outline-variant px-4 py-2.5 text-[14px] font-semibold text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                onClick={() => { setSelectedExchange(null); setConflictWarning(null); }}
                type="button"
              >
                Đóng
              </button>
              {managerMode && selectedExchange.status === "PENDING" && (
                <>
                  <button
                    className="flex items-center gap-2 rounded-lg border border-outline-variant bg-error-container px-4 py-2.5 text-[14px] font-semibold text-error transition-colors hover:bg-error-container/80 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error/20"
                    disabled={processing !== null}
                    onClick={() => handleReject(selectedExchange.id)}
                    type="button"
                  >
                    {processing !== null ? (
                      <div className="size-4 animate-spin rounded-full border-2 border-error border-t-transparent" />
                    ) : (
                      <span className="material-symbols-outlined text-[18px]">close</span>
                    )}
                    Từ chối
                  </button>
                  <button
                    className="flex items-center gap-2 rounded-lg bg-secondary px-4 py-2.5 text-[14px] font-semibold text-on-secondary transition-colors hover:brightness-110 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-secondary/20"
                    disabled={processing !== null}
                    onClick={() => handleApprove(selectedExchange.id, selectedExchange.periodId)}
                    type="button"
                  >
                    {processing !== null ? (
                      <div className="size-4 animate-spin rounded-full border-2 border-on-secondary border-t-transparent" />
                    ) : (
                      <span className="material-symbols-outlined text-[18px]">check</span>
                    )}
                    Duyệt
                  </button>
                </>
              )}
            </div>
          </div>
        </div>
      )}
    </DashboardShell>
  );
}
