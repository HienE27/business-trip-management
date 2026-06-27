"use client";

import { Suspense, useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import { useAuth } from "@/components/auth/AuthProvider";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { formatDate, formatDateFull } from "@/lib/date";
import { BackButton } from "@/components/ui/BackButton";
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

function isManagerLike(staff: Staff | null) {
  return Boolean(staff?.roles?.some((role) => role === "ADMIN" || role === "MANAGER"));
}

function SwapRequestsContent() {
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
  const ignoreRef = useRef(false);
  const [conflictWarning, setConflictWarning] = useState<{ periodId: number; totalConflicts: number } | null>(null);
  const [submitting, setSubmitting] = useState(false);
  const [form, setForm] = useState({
    requesterScheduleId: "",
    targetScheduleId: "",
    reason: "",
  });

  const globalQuery = searchParams.get("q") ?? "";
  const [searchKeyword, setSearchKeyword] = useState(globalQuery);

  useEffect(() => {
    setSearchKeyword(globalQuery);
  }, [globalQuery]);

  const [selectedExchange, setSelectedExchange] = useState<ScheduleExchangeResponse | null>(null);
  const [reviewNote, setReviewNote] = useState("");

  const fetchExchanges = useCallback(async () => {
    ignoreRef.current = false;
    try {
      setLoading(true);
      setMessage("");
      const meRes = await api.get<Staff>("/staff/me");
      if (ignoreRef.current) return;
      setCurrentUser(meRes);

      const managerView = isManagerLike(meRes);
      const exchangePath = managerView ? "/schedule-exchanges" : `/schedule-exchanges/user/${meRes.id}`;
      const exchangeRes = await api.get<ScheduleExchangeResponse[]>(exchangePath);
      if (ignoreRef.current) return;
      setExchanges(exchangeRes ?? []);

      if (!managerView) {
        const schedules = await api.get<Schedule[]>(`/schedules/staff/${meRes.id}`);
        if (ignoreRef.current) return;
        setMySchedules(schedules ?? []);

        const periodIds = Array.from(new Set((schedules ?? []).map((s) => s.periodId)));
        const relatedSchedules = await Promise.all(
          periodIds.map((periodId) => api.get<Schedule[]>(`/schedules/period/${periodId}`)),
        );
        if (ignoreRef.current) return;
        setAllSchedules(
          relatedSchedules
            .flatMap((items) => items ?? [])
            .filter((schedule) => schedule.staff.id !== meRes.id),
        );
      } else {
        if (ignoreRef.current) return;
        setMySchedules([]);
        setAllSchedules([]);
      }
    } catch (err) {
      if (ignoreRef.current) return;
      setExchanges([]);
      setCurrentUser(null);
      setMySchedules([]);
      setAllSchedules([]);
      setMessage(getErrorMessage(err, "Không thể tải danh sách yêu cầu đổi trực."));
    } finally {
      if (!ignoreRef.current) setLoading(false);
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

  const handleApprove = useCallback(async (id: number, periodId: number) => {
    const reviewerId = currentUser?.id;
    if (!reviewerId) return;
    ignoreRef.current = false;
    try {
      setProcessing(id);
      setConflictWarning(null);

      const conflictRes = await api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${periodId}`);
      if (conflictRes.hasConflicts && conflictRes.totalConflicts > 0) {
        setConflictWarning({ periodId, totalConflicts: conflictRes.totalConflicts });
        setMessage(
          `Phát hiện ${conflictRes.totalConflicts} xung đột lịch trong kỳ. Không thể duyệt đổi trực khi còn xung đột.`
        );
        setProcessing(null);
        return;
      }

      await api.approveExchange(id, reviewerId, reviewNote || undefined);
      if (ignoreRef.current) return;
      setMessage("Đã duyệt yêu cầu đổi trực.");
      setSelectedExchange(null);
      setReviewNote("");
      await fetchExchanges();
    } catch (err) {
      if (ignoreRef.current) return;
      setMessage(getErrorMessage(err, "Lỗi duyệt yêu cầu."));
    } finally {
      if (!ignoreRef.current) setProcessing(null);
    }
  }, [currentUser, reviewNote, fetchExchanges]);

  const handleReject = useCallback(async (id: number) => {
    const reviewerId = currentUser?.id;
    if (!reviewerId) return;
    ignoreRef.current = false;
    try {
      setProcessing(id);
      await api.rejectExchange(id, reviewerId, reviewNote || undefined);
      if (ignoreRef.current) return;
      setMessage("Đã từ chối yêu cầu đổi trực.");
      setSelectedExchange(null);
      setReviewNote("");
      setConflictWarning(null);
      await fetchExchanges();
    } catch (err) {
      if (ignoreRef.current) return;
      setMessage(getErrorMessage(err, "Lỗi từ chối yêu cầu đổi trực."));
    } finally {
      if (!ignoreRef.current) setProcessing(null);
    }
  }, [currentUser, reviewNote, fetchExchanges]);

  const handleCancel = useCallback(async (id: number) => {
    ignoreRef.current = false;
    try {
      setProcessing(id);
      await api.cancelExchange(id);
      if (ignoreRef.current) return;
      setMessage("Đã hủy yêu cầu đổi trực.");
      await fetchExchanges();
    } catch (err) {
      if (ignoreRef.current) return;
      setMessage(getErrorMessage(err, "Lỗi hủy yêu cầu đổi trực."));
    } finally {
      if (!ignoreRef.current) setProcessing(null);
    }
  }, [fetchExchanges]);

  const handleCreateRequest = useCallback(async () => {
    if (!currentUser || !selectedRequesterSchedule || !form.targetScheduleId) {
      setMessage("Vui lòng chọn đầy đủ ca trực của bạn và ca muốn đổi.");
      return;
    }
    ignoreRef.current = false;
    try {
      setSubmitting(true);
      const payload = {
        periodId: selectedRequesterSchedule.periodId,
        requesterScheduleId: selectedRequesterSchedule.id,
        targetScheduleId: Number(form.targetScheduleId),
        reason: form.reason.trim() || undefined,
      };

      await api.createExchange(currentUser.id, payload);
      if (ignoreRef.current) return;
      setMessage("Đã gửi yêu cầu đổi trực. Quản lý sẽ xem xét và phản hồi.");
      setForm({ requesterScheduleId: "", targetScheduleId: "", reason: "" });
      await fetchExchanges();
    } catch (err) {
      if (ignoreRef.current) return;
      setMessage(getErrorMessage(err, "Không thể gửi yêu cầu đổi trực."));
    } finally {
      setSubmitting(false);
    }
  }, [currentUser, selectedRequesterSchedule, form, fetchExchanges]);

  useEffect(() => { void fetchExchanges(); }, [fetchExchanges]);

  return (
    <>
      <div className="space-y-4">
        <BackButton href="/dashboard" variant="full" label="Quay lại" className="mb-2" />

        {/* Stats Row - KPI Cards */}
        <section className="grid grid-cols-2 gap-3 md:grid-cols-4">
          {[
            { label: "Tổng yêu cầu", value: stats.total, icon: "list_alt", accent: "border-l-primary" },
            { label: "Chờ duyệt", value: stats.pending, icon: "pending_actions", accent: "border-l-tertiary" },
            { label: "Đã duyệt", value: stats.approved, icon: "task_alt", accent: "border-l-secondary" },
            { label: "Từ chối", value: stats.rejected, icon: "cancel", accent: "border-l-error" },
          ].map((card) => (
            <div
              className={`group relative flex items-center gap-3 rounded-xl border-t-2 ${card.accent} border border-r border-b border-outline-variant bg-surface-container-lowest p-4 shadow-sm transition-all duration-200 hover:bg-surface-container-low hover:shadow-md`}
              key={card.label}
            >
              <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-surface-container-low transition-transform duration-200 group-hover:scale-105">
                <span className="material-symbols-outlined text-[20px] text-on-surface-variant">{card.icon}</span>
              </div>
              <div className="min-w-0">
                <p className="text-label-sm text-on-surface-variant truncate">{card.label}</p>
                <p className="text-headline-lg font-bold leading-none text-on-surface mt-0.5">{card.value}</p>
              </div>
            </div>
          ))}
        </section>

        {!managerMode && (
          <section className="grid gap-4 rounded-xl border border-outline-variant bg-surface-container-lowest p-4 shadow-sm lg:grid-cols-2">
            <div className="space-y-3">
              <h2 className="text-title-lg font-semibold text-on-surface">Gửi yêu cầu đổi trực</h2>
              <p className="text-label-md text-on-surface-variant">
                Hỗ trợ đổi ca cùng loại (L01↔L01, L02↔L02, L03↔L03, L04↔L04) trong cùng kỳ đã được công bố.
              </p>

              <div className="space-y-2.5">
                <div>
                  <label className="mb-2 block text-label-md font-medium text-on-surface" htmlFor="requesterScheduleId">
                    Ca trực của bạn
                  </label>
                  {mySchedules.length === 0 ? (
                    <div className="rounded-lg border border-tertiary/30 bg-tertiary-container/20 px-3 py-2 text-label-md text-on-surface">
                      <span className="material-symbols-outlined text-[20px] text-tertiary align-middle mr-1">info</span>
                      Bạn chưa có ca trực L01 nào trong kỳ đã công bố.
                    </div>
                  ) : (
                  <select
                    className="h-10 w-full rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary"
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
                  <label className="mb-1 block text-label-md font-medium text-on-surface" htmlFor="targetScheduleId">
                    Ca muốn đổi cùng
                  </label>
                  {!selectedRequesterSchedule ? (
                    <div className="h-10 rounded-lg border border-outline-variant bg-surface px-3 flex items-center text-label-md text-on-surface-variant">
                      Vui lòng chọn ca trực của bạn trước.
                    </div>
                  ) : candidateTargetSchedules.length === 0 ? (
                    <div className="rounded-lg border border-tertiary/30 bg-tertiary-container/20 px-3 py-2 text-label-md text-on-surface">
                      <span className="material-symbols-outlined text-[20px] text-tertiary align-middle mr-1">info</span>
                      Không có ca L01 nào của người khác trong cùng kỳ.
                    </div>
                  ) : (
                    <select
                      className="h-10 w-full rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary"
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
                  <label className="mb-1 block text-label-md font-medium text-on-surface" htmlFor="exchange-reason">
                    Lý do
                  </label>
                  <textarea
                    className="min-h-16 w-full rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary resize-none"
                    id="exchange-reason"
                    maxLength={500}
                    onChange={(event) => setForm((prev) => ({ ...prev, reason: event.target.value }))}
                    placeholder="Ví dụ: bận công việc gia đình..."
                    value={form.reason}
                  />
                </div>

                <button
                  className="h-10 inline-flex items-center justify-center gap-2 rounded-lg bg-primary px-4 text-label-md font-medium text-on-primary transition-colors hover:brightness-110 disabled:opacity-50 disabled:cursor-not-allowed"
                  disabled={submitting || !form.requesterScheduleId || !form.targetScheduleId}
                  onClick={handleCreateRequest}
                  type="button"
                >
                  {submitting ? "Đang gửi yêu cầu..." : "Gửi yêu cầu đổi trực"}
                </button>
              </div>
            </div>

            <div className="rounded-lg border border-outline-variant bg-surface p-3">
              <h3 className="text-label-md font-semibold text-on-surface">Lưu ý</h3>
              <ul className="mt-2 space-y-1.5 text-label-md text-on-surface-variant">
                <li className="flex items-start gap-1.5">
                  <span className="material-symbols-outlined text-[14px] text-outline shrink-0 mt-0.5">info</span>
                  Hỗ trợ đổi ca cùng loại (L01↔L01, L02↔L02, L03↔L03, L04↔L04) trong cùng kỳ đã được công bố.
                </li>
                <li className="flex items-start gap-1.5">
                  <span className="material-symbols-outlined text-[14px] text-outline shrink-0 mt-0.5">info</span>
                  Kỳ lịch phải ở trạng thái PUBLISHED.
                </li>
                <li className="flex items-start gap-1.5">
                  <span className="material-symbols-outlined text-[14px] text-outline shrink-0 mt-0.5">info</span>
                  Hệ thống tự kiểm tra nghỉ phép và ngày nghỉ bù.
                </li>
                <li className="flex items-start gap-1.5">
                  <span className="material-symbols-outlined text-[14px] text-outline shrink-0 mt-0.5">info</span>
                  Có thể hủy yêu cầu khi đang chờ duyệt.
                </li>
              </ul>
            </div>
          </section>
        )}

        {/* Filter bar */}
        <section className="flex items-center justify-between rounded-xl border border-outline-variant bg-surface-container-lowest p-3 shadow-sm">
          <div className="flex items-center gap-4">
            <div className="flex flex-col gap-1">
              <label className="font-label-sm text-label-sm text-on-surface-variant" htmlFor="status-filter">
                Trạng thái
              </label>
              <select
                className="h-10 w-full rounded-lg border border-outline-variant bg-surface px-3 py-2 text-label-md text-on-surface outline-none focus:border-primary focus:ring-2 focus:ring-primary cursor-pointer"
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
            className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 py-2 text-label-md font-medium text-on-surface shadow-sm transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
            onClick={() => void fetchExchanges()}
            type="button"
          >
            <span className="material-symbols-outlined text-[18px]">refresh</span>
            Làm mới
          </button>
        </section>

        {message && (
          <div className={`rounded-lg border px-4 py-3 text-sm ${
            message.includes("thành công") || message.includes("Đã duyệt") || message.includes("Đã gửi")
              ? "border-secondary/20 bg-secondary-container text-on-secondary-container"
              : "border-error/20 bg-error-container text-error"
          }`}>
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
              <table className="w-full border-collapse text-left" aria-label="Page Table">
                <thead>
                  <tr className="border-b border-outline-variant bg-surface-container-low">
                    <th scope="col" className="px-4 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Người yêu cầu</th>
                    <th scope="col" className="px-4 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Người đổi cùng</th>
                    <th scope="col" className="px-4 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Ca ban đầu</th>
                    <th scope="col" className="px-4 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Ca đề xuất</th>
                    <th scope="col" className="px-4 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Trạng thái</th>
                    <th scope="col" className="px-4 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant text-right">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/50">
                  {Array.from({ length: 5 }).map((_, i) => (
                    <tr key={i} className="hover:bg-surface-container-lowest h-10">
                      <td className="px-4 py-2"><Skeleton className="h-3 w-32 rounded" /></td>
                      <td className="px-4 py-2"><Skeleton className="h-3 w-32 rounded" /></td>
                      <td className="px-4 py-2"><Skeleton className="h-3 w-24 rounded" /></td>
                      <td className="px-4 py-2"><Skeleton className="h-3 w-24 rounded" /></td>
                      <td className="px-4 py-2"><Skeleton className="h-5 w-20 rounded-full" /></td>
                      <td className="px-4 py-2 text-right"><Skeleton className="h-7 w-7 rounded-lg ml-auto" /></td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : filtered.length === 0 ? (
              <EmptyState
                icon="swap_horiz"
                title="Chưa có yêu cầu đổi trực nào"
              />
            ) : (
              <table className="w-full border-collapse text-left" aria-label="Page Table">
                <thead>
                  <tr className="border-b border-outline-variant bg-surface-container-low">
                    <th scope="col" className="px-4 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Người yêu cầu</th>
                    <th scope="col" className="px-4 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Người đổi cùng</th>
                    <th scope="col" className="px-4 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Ca ban đầu</th>
                    <th scope="col" className="px-4 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Ca đề xuất</th>
                    <th scope="col" className="px-4 py-2.5 text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Trạng thái</th>
                    <th scope="col" className="px-4 py-2.5 text-right text-label-sm font-semibold uppercase tracking-wide text-on-surface-variant">Thao tác</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-outline-variant/50">
                  {filtered.map((req) => {
                    const canManage = managerMode && req.status === "PENDING";
                    return (
                      <tr className="group transition-colors hover:bg-surface-container-lowest h-10" key={req.id}>
                        <td className="px-4 py-2">
                          <span className="text-label-sm font-medium text-on-surface">{req.requester.fullName}</span>
                        </td>
                        <td className="px-4 py-2">
                          <span className="text-label-md text-on-surface">{req.target.fullName}</span>
                        </td>
                        <td className="px-4 py-2">
                          <div className={`flex items-center gap-1.5 border-l-4 ${getBorderColor(req.requesterSchedule.shiftType.id)} pl-1.5`}>
                            <div className="flex flex-col">
                              <span className={`text-label-md text-on-surface ${req.status === "APPROVED" ? "line-through" : ""}`}>
                                {formatDate(req.requesterSchedule.workDate)}
                              </span>
                              <span className="text-label-sm text-on-surface-variant">{req.requesterSchedule.shiftType.name}</span>
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-2">
                          <div className={`flex items-center gap-1.5 border-l-4 ${getBorderColor(req.targetSchedule.shiftType.id)} pl-1.5`}>
                            <div className="flex flex-col">
                              <span className="text-label-md text-on-surface">{formatDate(req.targetSchedule.workDate)}</span>
                              <span className="text-label-sm text-on-surface-variant">{req.targetSchedule.shiftType.name}</span>
                            </div>
                          </div>
                        </td>
                        <td className="px-4 py-2">
                          <span className={`inline-flex items-center rounded-full px-2 py-0.5 text-label-sm font-semibold ${getStatusBadge(req.status)}`}>
                            {getStatusLabel(req.status)}
                          </span>
                          {req.reason && (
                            <p className="mt-1 max-w-[180px] truncate text-label-sm text-on-surface-variant" title={req.reason}>
                              Lý do: {req.reason}
                            </p>
                          )}
                          {req.reviewNote && (
                            <p className="mt-0.5 max-w-[180px] truncate rounded-md bg-surface-container-low px-1.5 py-0.5 text-label-sm text-secondary" title={req.reviewNote}>
                              {req.reviewNote}
                            </p>
                          )}
                          {req.reviewedBy && (
                            <p className="mt-0.5 text-label-sm text-outline">
                              Bởi {req.reviewedBy.fullName}
                            </p>
                          )}
                        </td>
                        <td className="px-4 py-2 text-right">
                          {canManage ? (
                            <div className="flex justify-end">
                              <button
                                className="flex h-7 w-7 items-center justify-center rounded bg-secondary-container/20 text-secondary transition-colors hover:bg-secondary-container disabled:opacity-50"
                                disabled={processing !== null}
                                onClick={() => { setSelectedExchange(req); setReviewNote(req.reviewNote ?? ""); }}
                                title="Xem chi tiết & duyệt"
                                type="button"
                                aria-label="Xem chi tiết & duyệt"
                              >
                                <span className="material-symbols-outlined text-[16px]" aria-hidden="true">visibility</span>
                              </button>
                            </div>
                          ) : (
                            <span className="flex justify-end items-center gap-1">
                              {req.status === "PENDING" && !managerMode && (
                                <button
                                  className="flex h-7 w-7 items-center justify-center rounded border border-error/30 text-error transition-colors hover:bg-error-container disabled:opacity-50"
                                  disabled={processing !== null}
                                  onClick={() => handleCancel(req.id)}
                                  title="Hủy yêu cầu"
                                  type="button"
                                  aria-label="Hủy yêu cầu"
                                >
                                  <span className="material-symbols-outlined text-[16px]" aria-hidden="true">close</span>
                                </button>
                              )}
                              {req.status !== "PENDING" && (
                                <button
                                  className="flex h-7 w-7 items-center justify-center rounded text-outline transition-colors hover:bg-surface-container hover:text-primary"
                                  onClick={() => { setSelectedExchange(req); setReviewNote(req.reviewNote ?? ""); }}
                                  title="Xem chi tiết"
                                  type="button"
                                  aria-label="Xem chi tiết"
                                >
                                  <span className="material-symbols-outlined text-[16px]" aria-hidden="true">visibility</span>
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

          <div className="flex items-center justify-between border-t border-outline-variant/50 bg-surface-container-lowest px-4 py-2">
            <span className="text-label-sm text-on-surface-variant">
              Hiển thị {filtered.length} / {exchanges.length} yêu cầu
            </span>
            {!managerMode && authUser?.roles?.includes("STAFF") && (
              <span className="text-label-sm text-on-surface-variant">
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
          <div className="absolute inset-0 bg-black/40" onClick={() => { setSelectedExchange(null); setConflictWarning(null); }} aria-hidden="true" />
          <div className="relative w-full max-w-lg rounded-xl border border-outline-variant bg-surface-container-lowest shadow-2xl">
            <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
              <div className="flex items-center gap-3">
                  <div className="flex h-10 w-10 items-center justify-center rounded-full bg-primary-fixed">
                  <span className="material-symbols-outlined text-[20px] text-primary" style={{ fontVariationSettings: "'FILL' 1" }}>swap_horiz</span>
                </div>
                <div>
                  <h2 className="text-headline-lg font-semibold text-on-surface">Chi tiết đổi trực</h2>
                  <p className="text-label-md text-on-surface-variant">#{selectedExchange.id}</p>
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
              <div className="grid grid-cols-2 gap-4">
                <div className="rounded-lg border border-outline-variant bg-surface p-3">
                  <p className="text-label-sm text-on-surface-variant mb-1">Người yêu cầu</p>
                  <p className="text-label-md font-semibold text-on-surface">{selectedExchange.requester.fullName}</p>
                </div>
                <div className="rounded-lg border border-outline-variant bg-surface p-3">
                  <p className="text-label-sm text-on-surface-variant mb-1">Người đổi cùng</p>
                  <p className="text-label-md font-semibold text-on-surface">{selectedExchange.target.fullName}</p>
                </div>
              </div>

              <div className="grid grid-cols-2 gap-4">
                <div className={`rounded-lg border-l-4 border p-3 ${getBorderColor(selectedExchange.requesterSchedule?.shiftType?.id ?? "")} bg-surface`}>
                  <p className="text-label-sm text-on-surface-variant mb-1">Ca ban đầu</p>
                  <p className="text-body-sm font-medium text-on-surface">{formatDateFull(selectedExchange.requesterSchedule?.workDate ?? "")}</p>
                  <p className="text-label-md text-on-surface-variant">{selectedExchange.requesterSchedule?.shiftType?.name ?? "—"}</p>
                  {selectedExchange.status === "APPROVED" && (
                    <span className="mt-1.5 inline-flex items-center gap-1 text-label-sm text-secondary">
                      <span className="material-symbols-outlined text-[12px]">check</span> Đã đổi
                    </span>
                  )}
                </div>
                <div className={`rounded-lg border-l-4 border p-3 ${getBorderColor(selectedExchange.targetSchedule?.shiftType?.id ?? "")} bg-surface`}>
                  <p className="text-label-sm text-on-surface-variant mb-1">Ca đề xuất</p>
                  <p className="text-body-sm font-medium text-on-surface">{formatDateFull(selectedExchange.targetSchedule?.workDate ?? "")}</p>
                  <p className="text-label-md text-on-surface-variant">{selectedExchange.targetSchedule?.shiftType?.name ?? "—"}</p>
                </div>
              </div>

              <div className="space-y-3">
                <div className="flex items-center gap-3">
                  <p className="text-label-sm text-on-surface-variant">Trạng thái</p>
                  <span className={`inline-flex items-center rounded-full px-2.5 py-0.5 text-label-sm font-semibold ${getStatusBadge(selectedExchange.status)}`}>
                    {getStatusLabel(selectedExchange.status)}
                  </span>
                </div>

                {selectedExchange.reason && (
                  <div>
                    <p className="text-label-sm text-on-surface-variant mb-1">Lý do</p>
                    <p className="text-body-sm text-on-surface leading-relaxed">{selectedExchange.reason}</p>
                  </div>
                )}

                {selectedExchange.reviewNote && (
                  <div className="rounded-lg bg-surface-container-low p-3">
                    <p className="text-label-sm text-on-surface-variant mb-1">Phản hồi</p>
                    <p className="text-body-sm text-on-surface">{selectedExchange.reviewNote}</p>
                  </div>
                )}

                {managerMode && selectedExchange.status === "PENDING" && (
                  <div className="space-y-2">
                    <label className="text-label-md font-semibold text-on-surface" htmlFor="exchange-review-note">
                      Ghi chú duyệt <span className="text-outline font-normal">(tùy chọn)</span>
                    </label>
                    <textarea
                      className="w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2.5 text-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 resize-none"
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
                className="flex-1 rounded-lg border border-outline-variant px-4 py-2.5 text-label-md font-semibold text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                onClick={() => { setSelectedExchange(null); setConflictWarning(null); }}
                type="button"
              >
                Đóng
              </button>
              {managerMode && selectedExchange.status === "PENDING" && (
                <>
                  <button
                    className="flex items-center gap-2 rounded-lg border border-outline-variant bg-error-container px-4 py-2.5 text-label-md font-semibold text-error transition-colors hover:bg-error-container/80 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error/20"
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
                    className="flex items-center gap-2 rounded-lg bg-secondary px-4 py-2.5 text-label-md font-semibold text-on-secondary transition-colors hover:brightness-110 disabled:opacity-50 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-secondary/20"
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
    </>
  );
}

export default function SwapRequestsPage() {
  return (
    <Suspense fallback={
      <div className="flex items-center justify-center py-16">
        <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
      </div>
    }>
      <SwapRequestsContent />
    </Suspense>
  );
}
