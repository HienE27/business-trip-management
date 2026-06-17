"use client";

import { Suspense, useCallback, useEffect, useRef, useMemo, useState } from "react";
import { useSearchParams } from "next/navigation";
import { WorkflowShell } from "@/components/layout/WorkflowShell";
import { SectionCard } from "@/components/ui/SectionCard";
import { EmptyState } from "@/components/ui/EmptyState";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { formatDateRange, formatDateTime } from "@/lib/date";
import { useAuth } from "@/components/auth/AuthProvider";
import { useToast } from "@/hooks/useToast";
import type { LeaveRequest, ConflictCheckResponse, SchedulePeriod } from "@/types/api";

type FilterStatus = LeaveRequest["status"] | "ALL";

const STATUS_LABEL: Record<LeaveRequest["status"], string> = {
  PENDING: "Chờ duyệt",
  APPROVED: "Đã duyệt",
  REJECTED: "Từ chối",
  CANCELLED: "Đã hủy",
};

const STATUS_CLASS: Record<LeaveRequest["status"], string> = {
  PENDING: "bg-tertiary-fixed/40 text-tertiary border border-tertiary/20",
  APPROVED: "bg-secondary-container text-on-secondary-container border border-secondary/20",
  REJECTED: "bg-error-container text-on-error-container border border-error/20",
  CANCELLED: "bg-surface-container-high text-on-surface-variant border border-outline-variant",
};

function getStaffDisplayName(req: LeaveRequest) {
  return req.staff?.fullName ?? req.staffName ?? `Nhân sự #${req.staffId ?? "?"}`;
}

function LeaveRequestsContent() {
  const searchParams = useSearchParams();
  const { user } = useAuth();
  const isManager = user?.roles?.some((r) => r === "ADMIN" || r === "MANAGER") ?? false;

  const [requests, setRequests] = useState<LeaveRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<FilterStatus>("ALL");
  const toast = useToast();
  const ignoreRef = useRef(false);
  const abortControllerRef = useRef<AbortController | null>(null);

  // Keep toast ref in sync — avoids stale closure while keeping deps clean
  const toastRef = useRef(toast);
  useEffect(() => { toastRef.current = toast; });

  // Sync global search ?q= URL param
  const globalQuery = searchParams.get("q") ?? "";
  const [searchKeyword, setSearchKeyword] = useState(globalQuery);

  useEffect(() => {
    setSearchKeyword(globalQuery);
  }, [globalQuery]);

  // Modal state
  const [detailRequest, setDetailRequest] = useState<LeaveRequest | null>(null);
  const [reviewNote, setReviewNote] = useState("");
  const [processing, setProcessing] = useState(false);
  const [conflictWarning, setConflictWarning] = useState<{ periodId: number; totalConflicts: number } | null>(null);

  // Create leave request modal state
  const [showCreateModal, setShowCreateModal] = useState(false);
  const [createStartDate, setCreateStartDate] = useState("");
  const [createEndDate, setCreateEndDate] = useState("");
  const [createReason, setCreateReason] = useState("");
  const [creating, setCreating] = useState(false);

  // Debounce same-message toasts to prevent duplicates (React Strict Mode double-invoke)
  const lastToastRef = useRef<{ msg: string; time: number } | null>(null);
  function safeToast(method: "success" | "error" | "info", message: string) {
    const now = Date.now();
    if (lastToastRef.current?.msg === message && now - lastToastRef.current.time < 500) return;
    lastToastRef.current = { msg: message, time: now };
    toastRef.current[method](message);
  }

  const fetchRequests = useCallback(async () => {
    // Cancel any in-flight request
    abortControllerRef.current?.abort();
    const controller = new AbortController();
    abortControllerRef.current = controller;

    try {
      setLoading(true);
      const data = await api.get<LeaveRequest[]>(
        "/leave-requests",
        undefined,
        { signal: controller.signal }
      );
      if (ignoreRef.current || controller.signal.aborted) return;
      setRequests(data ?? []);
    } catch (err) {
      if (ignoreRef.current || controller.signal.aborted) return;
      safeToast("error", getErrorMessage(err, "Không thể tải danh sách yêu cầu nghỉ phép."));
      setRequests([]);
    } finally {
      if (!ignoreRef.current && !controller.signal.aborted) setLoading(false);
    }
  }, []);

  useEffect(() => {
    ignoreRef.current = false;
    abortControllerRef.current?.abort();
    void fetchRequests();
    return () => {
      ignoreRef.current = true;
      abortControllerRef.current?.abort();
    };
  }, [fetchRequests]);

  const filteredRequests = useMemo(() => {
    const visible = requests.filter((r) => {
      if (!isManager && r.staff?.id !== user?.userId) return false;
      if (statusFilter !== "ALL" && r.status !== statusFilter) return false;
      if (searchKeyword.trim()) {
        const kw = searchKeyword.toLowerCase();
        const matchName = getStaffDisplayName(r).toLowerCase().includes(kw);
        const matchReason = r.reason?.toLowerCase().includes(kw);
        if (!matchName && !matchReason) return false;
      }
      return true;
    });
    return visible;
  }, [requests, statusFilter, searchKeyword, isManager, user]);

  const stats = useMemo(() => ({
    total: requests.length,
    pending: requests.filter((r) => r.status === "PENDING").length,
    approved: requests.filter((r) => r.status === "APPROVED").length,
    rejected: requests.filter((r) => r.status === "REJECTED").length,
  }), [requests]);

  const handleApprove = useCallback(async () => {
    if (!detailRequest || !user?.userId) return;
    ignoreRef.current = false;
    try {
      setProcessing(true);
      setConflictWarning(null);

      // Resolve the period covering the leave request dates
      const periodsRes = await api.get<SchedulePeriod[]>("/periods/status/PUBLISHED");
      const coveringPeriod = (periodsRes ?? []).find((p) => {
        const reqStart = new Date(detailRequest.startDate);
        const reqEnd = new Date(detailRequest.endDate);
        const periodStart = new Date(p.startDate);
        const periodEnd = new Date(p.endDate);
        return reqStart <= periodEnd && reqEnd >= periodStart;
      });

      if (coveringPeriod) {
        const conflictRes = await api.get<ConflictCheckResponse>(
          `/schedules/conflicts/check/${coveringPeriod.id}`,
        );
        if (conflictRes.hasConflicts && conflictRes.totalConflicts > 0) {
          setConflictWarning({ periodId: coveringPeriod.id, totalConflicts: conflictRes.totalConflicts });
          toastRef.current.error(
            `Phát hiện ${conflictRes.totalConflicts} xung đột lịch trong kỳ "${coveringPeriod.periodName}". Vui lòng giải quyết xung đột trước khi duyệt nghỉ phép.`,
          );
          setProcessing(false);
          return;
        }
      }

      await api.put(`/leave-requests/${detailRequest.id}/approve?reviewerId=${user.userId}&reviewNote=${encodeURIComponent(reviewNote)}`, {});
      if (ignoreRef.current) return;
      toastRef.current.success(`Đã duyệt yêu cầu của ${getStaffDisplayName(detailRequest)}.`);
      setDetailRequest(null);
      setReviewNote("");
      await fetchRequests();
    } catch (err) {
      if (ignoreRef.current) return;
      toastRef.current.error(getErrorMessage(err, "Lỗi duyệt yêu cầu."));
    } finally {
      if (!ignoreRef.current) setProcessing(false);
    }
  }, [detailRequest, reviewNote, user, fetchRequests]);

  const handleReject = useCallback(async () => {
    if (!detailRequest || !user?.userId) return;
    ignoreRef.current = false;
    try {
      setProcessing(true);
      await api.put(`/leave-requests/${detailRequest.id}/reject?reviewerId=${user.userId}&reviewNote=${encodeURIComponent(reviewNote)}`, {});
      if (ignoreRef.current) return;
      toastRef.current.success(`Đã từ chối yêu cầu của ${getStaffDisplayName(detailRequest)}.`);
      setDetailRequest(null);
      setReviewNote("");
      setConflictWarning(null);
      await fetchRequests();
    } catch (err) {
      if (ignoreRef.current) return;
      toastRef.current.error(getErrorMessage(err, "Lỗi từ chối yêu cầu."));
    } finally {
      if (!ignoreRef.current) setProcessing(false);
    }
  }, [detailRequest, reviewNote, user, fetchRequests]);

  const handleCreateLeaveRequest = useCallback(async () => {
    if (!user?.userId || !createStartDate || !createEndDate) return;
    if (new Date(createEndDate) < new Date(createStartDate)) {
      toastRef.current.error("Ngày kết thúc phải lớn hơn hoặc bằng ngày bắt đầu.");
      return;
    }
    setCreating(true);
    try {
      await api.post(`/leave-requests/staff/${user.userId}`, {
        startDate: createStartDate,
        endDate: createEndDate,
        reason: createReason || null,
      });
      toastRef.current.success("Đã gửi yêu cầu nghỉ phép thành công.");
      setShowCreateModal(false);
      setCreateStartDate("");
      setCreateEndDate("");
      setCreateReason("");
      await fetchRequests();
    } catch (err) {
      toastRef.current.error(getErrorMessage(err, "Không thể gửi yêu cầu nghỉ phép."));
    } finally {
      setCreating(false);
    }
  }, [user, createStartDate, createEndDate, createReason, fetchRequests]);

  const handleCancel = useCallback(async (id: number) => {
    if (!confirm("Bạn có chắc muốn hủy yêu cầu này?")) return;
    try {
      await api.put(`/leave-requests/${id}/cancel`, {});
      toastRef.current.success("Đã hủy yêu cầu nghỉ phép.");
      await fetchRequests();
    } catch (err) {
      toastRef.current.error(getErrorMessage(err, "Lỗi hủy yêu cầu."));
    }
  }, [fetchRequests]);

  const handleOpenDetail = useCallback((req: LeaveRequest) => {
    setDetailRequest(req);
    setReviewNote(req.reviewNote ?? "");
    setConflictWarning(null);
  }, []);

  return (
    <WorkflowShell
      section="leave-requests"
      title="Yêu cầu nghỉ phép"
      description="Theo dõi yêu cầu nghỉ phép từ nhân sự, phê duyệt và cân đối lịch trực."
    >
      {/* Conflict warning */}
      {conflictWarning && (
        <div className="rounded-lg border border-error/30 bg-error-container/40 px-4 py-3 text-sm text-error flex items-start gap-2">
          <span className="material-symbols-outlined text-[18px] shrink-0 mt-0.5">warning</span>
          <span>
            Phát hiện <strong>{conflictWarning.totalConflicts} xung đột</strong> trong kỳ lịch. Vui lòng giải quyết xung đột trước khi duyệt nghỉ phép.
          </span>
        </div>
      )}

      {/* Stats row */}
      <section className="grid gap-3 md:grid-cols-4">
        {[
          { label: "Tổng yêu cầu", value: stats.total, accent: "border-l-outline" },
          { label: "Chờ duyệt", value: stats.pending, accent: "border-l-tertiary" },
          { label: "Đã duyệt", value: stats.approved, accent: "border-l-secondary" },
          { label: "Từ chối", value: stats.rejected, accent: "border-l-error" },
        ].map((item) => (
          <article
            key={item.label}
            className={`rounded-lg border border-t-2 border-r border-b border-outline-variant bg-surface-container-lowest p-3 shadow-sm hover:bg-surface-container-low transition-colors ${item.accent}`}
          >
            <p className="text-[11px] font-medium text-on-surface-variant">{item.label}</p>
            <p className="mt-1 text-[22px] font-bold leading-none text-on-surface">{loading ? "\u2014" : item.value}</p>
          </article>
        ))}
      </section>

      <SectionCard
        title="Danh sách yêu cầu nghỉ phép"
        description="Tất cả yêu cầu nghỉ phép từ nhân sự trong hệ thống."
        action={
          <div className="flex items-center gap-3">
            <div className="relative">
              <select
                className="h-10 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-label-md text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 cursor-pointer appearance-none pr-8"
                value={statusFilter}
                onChange={(event) => setStatusFilter(event.target.value as FilterStatus)}
              >
                <option value="ALL">Tất cả trạng thái</option>
                <option value="PENDING">Chờ duyệt</option>
                <option value="APPROVED">Đã duyệt</option>
                <option value="REJECTED">Từ chối</option>
                <option value="CANCELLED">Đã hủy</option>
              </select>
              <span className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">expand_more</span>
            </div>
            <button
              type="button"
              onClick={() => setShowCreateModal(true)}
              className="flex items-center gap-2 rounded-lg bg-primary px-4 h-10 text-label-md font-medium text-on-primary shadow-sm transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
            >
              <span className="material-symbols-outlined text-[18px]">add</span>
              Tạo yêu cầu nghỉ phép
            </button>
            <button
              className="flex items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest px-4 h-10 text-label-md text-on-surface shadow-sm transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
              onClick={() => void fetchRequests()}
            >
              <span className="material-symbols-outlined text-[18px]">refresh</span>
              Làm mới
            </button>
          </div>
        }
      >
        {loading ? (
          <div className="flex items-center justify-center py-16">
            <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          </div>
        ) : filteredRequests.length === 0 ? (
          <EmptyState
            icon="event_busy"
            title="Không có yêu cầu phù hợp"
            description={
              statusFilter === "ALL"
                ? "Chưa có yêu cầu nghỉ phép nào trong hệ thống."
                : "Không có yêu cầu nào với trạng thái đã chọn."
            }
          />
        ) : (
          <div className="divide-y divide-outline-variant">
            {filteredRequests.map((request) => (
              <article
                key={request.id}
                className="grid gap-3 px-4 py-3 lg:grid-cols-[minmax(0,1fr)_240px] lg:items-start"
              >
                <div>
                  <div className="flex flex-wrap items-center gap-2">
                    <h3 className="text-title-lg font-semibold text-on-surface leading-tight">
                      {getStaffDisplayName(request)}
                    </h3>
                    <span className={`rounded-full px-2 py-0.5 text-[10px] font-semibold ${STATUS_CLASS[request.status]}`}>
                      {STATUS_LABEL[request.status]}
                    </span>
                  </div>
                  <p className="mt-1 text-[12px] leading-5 text-on-surface-variant">
                    {request.reason ?? "Không có lý do bổ sung."}
                  </p>
                  {request.reviewNote ? (
                    <div className="mt-1.5 rounded-lg bg-surface-container-low px-2.5 py-1.5">
                      <p className="text-[11px] text-on-surface-variant">Ghi chú: {request.reviewNote}</p>
                    </div>
                  ) : null}
                  <p className="mt-1 text-[11px] text-outline">
                    {formatDateTime(request.createdAt)}
                    {request.reviewedBy && (
                      <span> · Duyệt bởi {request.reviewedBy.fullName}</span>
                    )}
                  </p>
                </div>
                <div className="space-y-1.5 rounded-lg border border-outline-variant bg-surface p-3 text-[12px] text-on-surface">
                  <div className="flex justify-between gap-3">
                    <span className="text-on-surface-variant">Khoảng nghỉ</span>
                    <span className="font-medium">{formatDateRange(request.startDate, request.endDate)}</span>
                  </div>
                  <div className="flex gap-1.5 pt-1">
                    <button
                      className="flex-1 rounded-lg border border-outline-variant bg-surface-container-lowest px-2.5 py-1.5 text-[12px] text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                      onClick={() => handleOpenDetail(request)}
                      type="button"
                    >
                      Chi tiết
                    </button>
                    {request.status === "PENDING" && isManager && (
                      <button
                        className="flex-1 rounded-lg bg-primary px-2.5 py-1.5 text-[12px] text-on-primary transition-colors hover:brightness-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                        onClick={() => handleOpenDetail(request)}
                        type="button"
                      >
                        Duyệt / Từ chối
                      </button>
                    )}
                    {request.status === "PENDING" && !isManager && request.staff?.id === user?.userId && (
                      <button
                        className="flex-1 rounded-lg border border-outline-variant bg-surface-container-lowest px-2.5 py-1.5 text-[12px] text-error transition-colors hover:bg-error-container focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error/20"
                        onClick={() => handleCancel(request.id)}
                        type="button"
                      >
                        Hủy
                      </button>
                    )}
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}
      </SectionCard>

      {/* Detail / Review Modal */}
      {detailRequest && (
        <div
          aria-label="Chi tiết yêu cầu nghỉ phép"
          aria-modal="true"
          className="fixed inset-0 z-50 flex items-center justify-center p-4"
          role="dialog"
        >
          <div
            className="absolute inset-0 bg-black/40"
            onClick={() => setDetailRequest(null)}
            aria-hidden="true"
          />
          <div className="relative w-full max-w-lg rounded-xl border border-outline-variant bg-surface-container-lowest shadow-2xl">
            <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
              <div className="flex items-center gap-3">
                <div className={`flex h-10 w-10 items-center justify-center rounded-full ${
                  detailRequest.status === "PENDING" ? "bg-tertiary-fixed" : "bg-surface-container"
                }`}>
                  <span className="material-symbols-outlined text-[20px] text-on-surface-variant">event_busy</span>
                </div>
                <div>
                  <h2 className="text-[18px] font-semibold text-on-surface">Chi tiết yêu cầu</h2>
                  <p className="text-[12px] text-on-surface-variant">
                    {getStaffDisplayName(detailRequest)}
                  </p>
                </div>
              </div>
              <button
                className="flex h-9 w-9 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors"
                onClick={() => setDetailRequest(null)}
                title="Đóng"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            <div className="px-6 py-5 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <div>
                  <p className="text-label-sm text-on-surface-variant">Ngày bắt đầu</p>
                  <p className="mt-1 text-body-sm font-medium text-on-surface">
                    {new Date(detailRequest.startDate).toLocaleDateString("vi-VN", { weekday: "long", year: "numeric", month: "long", day: "numeric" })}
                  </p>
                </div>
                <div>
                  <p className="text-label-sm text-on-surface-variant">Ngày kết thúc</p>
                  <p className="mt-1 text-body-sm font-medium text-on-surface">
                    {new Date(detailRequest.endDate).toLocaleDateString("vi-VN", { weekday: "long", year: "numeric", month: "long", day: "numeric" })}
                  </p>
                </div>
              </div>

              <div>
                <p className="text-label-sm text-on-surface-variant">Lý do</p>
                <p className="mt-1 text-body-sm text-on-surface leading-relaxed">
                  {detailRequest.reason ?? "Không có lý do bổ sung."}
                </p>
              </div>

              <div className="flex items-center gap-3">
                <p className="text-label-sm text-on-surface-variant">Trạng thái</p>
                <span className={`rounded-full px-3 py-1 text-[11px] font-semibold ${STATUS_CLASS[detailRequest.status]}`}>
                  {STATUS_LABEL[detailRequest.status]}
                </span>
              </div>

              {isManager && detailRequest.status === "PENDING" && (
                <div className="space-y-2">
                  <label className="text-[13px] font-semibold text-on-surface" htmlFor="review-note">
                    Ghi chú duyệt <span className="text-outline font-normal">(tùy chọn)</span>
                  </label>
                  <textarea
                    className="w-full rounded-lg border border-outline-variant bg-surface-container-low px-3 py-2.5 text-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 resize-none"
                    id="review-note"
                    rows={3}
                    placeholder="Nhập ghi chú phê duyệt hoặc lý do từ chối..."
                    value={reviewNote}
                    onChange={(e) => setReviewNote(e.target.value)}
                  />
                </div>
              )}
            </div>

            <div className="flex items-center gap-3 px-6 py-4 border-t border-outline-variant">
              <button
                className="flex-1 rounded-lg border border-outline-variant px-4 py-2.5 text-label-md font-semibold text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                onClick={() => setDetailRequest(null)}
                type="button"
              >
                Đóng
              </button>
              {isManager && detailRequest.status === "PENDING" && (
                <>
                  <button
                    className="flex items-center gap-2 rounded-lg border border-outline-variant bg-error-container px-4 py-2.5 text-label-md font-semibold text-error transition-colors hover:bg-error-container/80 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-error/20 disabled:opacity-50"
                    disabled={processing}
                    onClick={handleReject}
                    type="button"
                  >
                    {processing ? (
                      <div className="size-4 animate-spin rounded-full border-2 border-error border-t-transparent" />
                    ) : (
                      <span className="material-symbols-outlined text-[18px]">close</span>
                    )}
                    Từ chối
                  </button>
                  <button
                    className="flex items-center gap-2 rounded-lg bg-secondary px-4 py-2.5 text-label-md font-semibold text-on-secondary transition-colors hover:brightness-110 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-secondary/20 disabled:opacity-50"
                    disabled={processing}
                    onClick={handleApprove}
                    type="button"
                  >
                    {processing ? (
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

      {/* Create Leave Request Modal */}
      {showCreateModal && (
        <div
          aria-label="Tạo yêu cầu nghỉ phép"
          role="dialog"
          className="fixed inset-0 z-50 flex items-center justify-center"
        >
          <div className="absolute inset-0 bg-black/40" onClick={() => setShowCreateModal(false)} aria-hidden="true" />
          <div className="relative w-full max-w-md rounded-xl border border-outline-variant bg-surface-container-lowest shadow-2xl">
            {/* Header */}
            <div className="flex items-center justify-between px-6 py-4 border-b border-outline-variant">
              <div className="flex items-center gap-3">
                <div className="flex h-10 w-10 items-center justify-center rounded-full bg-tertiary-fixed">
                  <span className="material-symbols-outlined text-[20px] text-tertiary">event_busy</span>
                </div>
                <div>
                  <h2 className="text-[18px] font-semibold text-on-surface">Tạo yêu cầu nghỉ phép</h2>
                  <p className="text-[12px] text-on-surface-variant">Gửi yêu cầu nghỉ phép cho quản lý xét duyệt.</p>
                </div>
              </div>
              <button
                className="flex h-9 w-9 items-center justify-center rounded-full text-on-surface-variant hover:bg-surface-container hover:text-on-surface transition-colors"
                onClick={() => setShowCreateModal(false)}
                title="Đóng"
                type="button"
              >
                <span className="material-symbols-outlined text-[20px]">close</span>
              </button>
            </div>

            {/* Form */}
            <div className="p-6 space-y-4">
              <div className="grid grid-cols-2 gap-4">
                <label className="flex flex-col gap-1.5">
                  <span className="text-[13px] font-semibold text-on-surface">
                    Ngày bắt đầu <span className="text-error">*</span>
                  </span>
                  <input
                    type="date"
                    className="h-10 rounded-lg border border-outline-variant bg-surface px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    value={createStartDate}
                    onChange={(e) => setCreateStartDate(e.target.value)}
                    required
                  />
                </label>
                <label className="flex flex-col gap-1.5">
                  <span className="text-[13px] font-semibold text-on-surface">
                    Ngày kết thúc <span className="text-error">*</span>
                  </span>
                  <input
                    type="date"
                    className="h-10 rounded-lg border border-outline-variant bg-surface px-3 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                    value={createEndDate}
                    onChange={(e) => setCreateEndDate(e.target.value)}
                    required
                  />
                </label>
              </div>
              <label className="flex flex-col gap-1.5">
                <span className="text-[13px] font-semibold text-on-surface">Lý do</span>
                <textarea
                  className="w-full resize-none rounded-lg border border-outline-variant bg-surface px-3 py-2 text-body-sm text-on-surface transition-all focus-visible:border-primary focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                  rows={3}
                  placeholder="Nhập lý do nghỉ phép (không bắt buộc)..."
                  value={createReason}
                  onChange={(e) => setCreateReason(e.target.value)}
                  maxLength={500}
                />
                <p className="text-[11px] text-outline text-right">{createReason.length}/500</p>
              </label>
            </div>

            {/* Footer */}
            <div className="flex items-center gap-3 px-6 py-4 border-t border-outline-variant">
              <button
                className="flex-1 rounded-lg border border-outline-variant px-4 py-2.5 text-label-md font-semibold text-on-surface transition-colors hover:bg-surface-container-low focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary/20"
                onClick={() => setShowCreateModal(false)}
                type="button"
              >
                Hủy
              </button>
              <button
                className="flex-1 flex items-center justify-center gap-2 rounded-lg bg-primary px-4 py-2.5 text-label-md font-medium text-on-primary transition-colors hover:bg-primary/90 focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-primary disabled:opacity-60"
                disabled={creating || !createStartDate || !createEndDate}
                onClick={() => void handleCreateLeaveRequest()}
                type="button"
              >
                {creating ? (
                  <>
                    <div className="size-4 animate-spin rounded-full border-2 border-on-primary border-t-transparent" />
                    Đang gửi...
                  </>
                ) : (
                  <>
                    <span className="material-symbols-outlined text-[18px]">send</span>
                    Gửi yêu cầu
                  </>
                )}
              </button>
            </div>
          </div>
        </div>
      )}
    </WorkflowShell>
  );
}

export default function LeaveRequestsPage() {
  return (
    <Suspense fallback={
      <WorkflowShell section="leave-requests" title="Yêu cầu nghỉ phép" description="Theo dõi yêu cầu nghỉ phép từ nhân sự, phê duyệt và cân đối lịch trực.">
        <div className="flex items-center justify-center py-16">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      </WorkflowShell>
    }>
      <LeaveRequestsContent />
    </Suspense>
  );
}
