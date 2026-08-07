"use client";

import { Suspense, useCallback, useEffect, useRef, useMemo, useState } from "react";
import dynamic from "next/dynamic";
import { useSearchParams } from "next/navigation";
import { Button, IconButton } from "@/components/ui";
import { Modal, ModalFooter } from "@/components/ui/Modal";
import { SectionCard } from "@/components/ui/SectionCard";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import { Pagination } from "@/components/ui/Pagination";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { formatDateRange, formatDateTime } from "@/lib/date";
import { useAuth } from "@/components/auth/AuthProvider";
import { usePermissions } from "@/hooks/usePermissions";
import { Permission } from "@/lib/permissions";
import { BackButton } from "@/components/ui/BackButton";

// Lazy-load the confirm dialog. The dialog is only visible after the
// user clicks "Hủy yêu cầu" — deferring it shaves a small chunk off
// the initial /leave-requests payload.
const ConfirmDialog = dynamic(
  () => import("@/components/ui/ConfirmDialog").then((m) => m.ConfirmDialog),
  { ssr: false },
);
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
  const { can } = usePermissions();
  const canApprove = can(Permission.LEAVE_APPROVE);
  const canCancelSelf = can(Permission.LEAVE_CANCEL_SELF);
  const hasApproveRole = user?.roles?.some((r) => r === "ADMIN" || r === "MANAGER") ?? false;
  const isManager = hasApproveRole || canApprove;

  const [requests, setRequests] = useState<LeaveRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [statusFilter, setStatusFilter] = useState<FilterStatus>("ALL");
  const toast = useToast();
  const ignoreRef = useRef(false);
  const abortControllerRef = useRef<AbortController | null>(null);

  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(10);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  // BUGFIX #6: debounce for server-side filter
  const [debouncedKeyword, setDebouncedKeyword] = useState("");
  const [statusCounts, setStatusCounts] = useState({
    total: 0,
    PENDING: 0,
    APPROVED: 0,
    REJECTED: 0,
    CANCELLED: 0,
  });

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
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [deleteTargetId, setDeleteTargetId] = useState<number | null>(null);

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
      // BUGFIX #6: server-side filter instead of client-side filter on page slice
      const pageResult = await api.getLeaveRequestsPageWithFilters(
        page, pageSize,
        statusFilter === "ALL" ? undefined : statusFilter,
        debouncedKeyword || undefined
      );
      if (controller.signal.aborted) return;
      setRequests(pageResult.content ?? []);
      setTotalPages(pageResult.totalPages ?? 0);
      setTotalElements(pageResult.totalElements ?? 0);
    } catch (err) {
      if (ignoreRef.current || controller.signal.aborted) return;
      safeToast("error", getErrorMessage(err, "Không thể tải danh sách yêu cầu nghỉ phép."));
      setRequests([]);
    } finally {
      if (!ignoreRef.current && !controller.signal.aborted) setLoading(false);
    }
  }, [page, pageSize, statusFilter, debouncedKeyword]);

  const fetchStatusCounts = useCallback(async () => {
    try {
      const res = await api.getLeaveRequestStatusCounts();
      const data = (res?.data ?? {}) as Record<string, number>;
      setStatusCounts({
        total: data.total ?? 0,
        PENDING: data.PENDING ?? 0,
        APPROVED: data.APPROVED ?? 0,
        REJECTED: data.REJECTED ?? 0,
        CANCELLED: data.CANCELLED ?? 0,
      });
    } catch {
      // Fall back to zeros — UI gracefully degrades to "0" cards.
    }
  }, []);

  useEffect(() => {
    ignoreRef.current = false;
    abortControllerRef.current?.abort();
    void fetchRequests();
    void fetchStatusCounts();
    return () => {
      ignoreRef.current = true;
      abortControllerRef.current?.abort();
    };
  }, [fetchRequests, fetchStatusCounts, statusFilter, debouncedKeyword]);

  // BUGFIX #6: removed client-side filter — server-side handles it.
  // visibleRequests filters only by manager permission on the frontend
  // (since that is app-level, not data-level filtering).
  const visibleRequests = useMemo(() => {
    return requests.filter((r) => {
      if (!isManager && r.staff?.id !== user?.userId) return false;
      return true;
    });
  }, [requests, isManager, user]);

  // BUGFIX #6: removed — now passed server-side via API params
  // Debounce search keyword → server-side filter (300ms)
  useEffect(() => {
    const t = setTimeout(() => setDebouncedKeyword(searchKeyword.trim()), 300);
    return () => clearTimeout(t);
  }, [searchKeyword]);

  const stats = useMemo(() => ({
    total: statusCounts.total,
    pending: statusCounts.PENDING,
    approved: statusCounts.APPROVED,
    rejected: statusCounts.REJECTED,
  }), [statusCounts.total, statusCounts.PENDING, statusCounts.APPROVED, statusCounts.REJECTED]);

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

  const confirmCancel = useCallback(async () => {
    if (deleteTargetId === null) return;
    try {
      await api.put(`/leave-requests/${deleteTargetId}/cancel`, {});
      toastRef.current.success("Đã hủy yêu cầu nghỉ phép.");
      await fetchRequests();
    } catch (err) {
      toastRef.current.error(getErrorMessage(err, "Lỗi hủy yêu cầu."));
    } finally {
      setConfirmOpen(false);
      setDeleteTargetId(null);
    }
  }, [fetchRequests]);

  const handleOpenDetail = useCallback((req: LeaveRequest) => {
    setDetailRequest(req);
    setReviewNote(req.reviewNote ?? "");
    setConflictWarning(null);
  }, []);

  return (
    <>
      <BackButton href="/dashboard" variant="full" label="Quay lại" className="mb-4" />

      {/* Conflict warning */}
      {conflictWarning && (
        <div className="rounded-lg border border-error/30 bg-error-container/40 px-4 py-3 text-sm text-error flex items-start gap-2">
          <span className="material-symbols-outlined text-[18px] shrink-0 mt-0.5">warning</span>
          <span>
            Phát hiện <strong>{conflictWarning.totalConflicts} xung đột</strong> trong kỳ lịch. Vui lòng giải quyết xung đột trước khi duyệt nghỉ phép.
          </span>
        </div>
      )}

      {/* Stats row - KPI Cards */}
      <section className="grid grid-cols-2 gap-3 md:grid-cols-4">
        {[
          { label: "Tổng yêu cầu", value: stats.total, icon: "event", accent: "border-l-outline" },
          { label: "Chờ duyệt", value: stats.pending, icon: "pending_actions", accent: "border-l-tertiary" },
          { label: "Đã duyệt", value: stats.approved, icon: "check_circle", accent: "border-l-secondary" },
          { label: "Từ chối", value: stats.rejected, icon: "cancel", accent: "border-l-error" },
        ].map((item) => (
          <article
            key={item.label}
            className={`group relative flex items-center gap-3 rounded-xl border-t-2 ${item.accent} border border-r border-b border-outline-variant bg-surface-container-lowest p-4 shadow-sm transition-all duration-200 hover:bg-surface-container-low hover:shadow-md`}
          >
            <div className="flex h-10 w-10 shrink-0 items-center justify-center rounded-xl bg-surface-container-low transition-transform duration-200 group-hover:scale-105">
              <span className="material-symbols-outlined text-[20px] text-on-surface-variant">{item.icon}</span>
            </div>
            <div className="min-w-0">
              <p className="text-label-sm text-on-surface-variant truncate">{item.label}</p>
              <p className="mt-0.5 text-headline-lg font-bold leading-none text-on-surface">{loading ? "—" : item.value}</p>
            </div>
          </article>
        ))}
      </section>

      <SectionCard
        title="Danh sách yêu cầu nghỉ phép"
        description="Tất cả yêu cầu nghỉ phép từ nhân sự trong hệ thống."
        action={
          <div className="flex items-center gap-3">
            <div className="relative">
              <label htmlFor="leave-status-filter" className="sr-only">Lọc theo trạng thái</label>
              <select
                id="leave-status-filter"
                className="h-10 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 text-label-md text-on-surface focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary/20 cursor-pointer appearance-none pr-8"
                value={statusFilter}
                onChange={(event) => { setStatusFilter(event.target.value as FilterStatus); setPage(0); }}
              >
                <option value="ALL">Tất cả trạng thái</option>
                <option value="PENDING">Chờ duyệt</option>
                <option value="APPROVED">Đã duyệt</option>
                <option value="REJECTED">Từ chối</option>
                <option value="CANCELLED">Đã hủy</option>
              </select>
              <span aria-hidden="true" className="material-symbols-outlined absolute right-2 top-1/2 -translate-y-1/2 text-outline pointer-events-none text-[20px]">expand_more</span>
            </div>
            <Button
              variant="primary"
              size="md"
              onClick={() => setShowCreateModal(true)}
              icon={<span className="material-symbols-outlined text-[18px]" aria-hidden="true">add</span>}
            >
              Tạo yêu cầu nghỉ phép
            </Button>
            <Button
              variant="secondary"
              size="md"
              onClick={() => void fetchRequests()}
              icon={<span className="material-symbols-outlined text-[18px]" aria-hidden="true">refresh</span>}
            >
              Làm mới
            </Button>
          </div>
        }
      >
        {loading ? (
          <div className="divide-y divide-outline-variant">
            {Array.from({ length: 5 }).map((_, i) => (
              <div key={i} className="grid gap-3 px-4 py-3 lg:grid-cols-[minmax(0,1fr)_240px] lg:items-start">
                <div className="space-y-2">
                  <Skeleton className="h-5 w-48 rounded" />
                  <Skeleton className="h-3 w-full rounded" />
                  <Skeleton className="h-3 w-2/3 rounded" />
                </div>
                <div className="flex gap-1.5 items-center">
                  <Skeleton className="h-7 w-16 rounded-lg" />
                  <Skeleton className="h-7 w-20 rounded-lg" />
                </div>
              </div>
            ))}
          </div>
        ) : visibleRequests.length === 0 ? (
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
            {visibleRequests.map((request) => (
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
                    <Button
                      variant="secondary"
                      size="sm"
                      fullWidth
                      onClick={() => handleOpenDetail(request)}
                    >
                      Chi tiết
                    </Button>
                    {request.status === "PENDING" && isManager && (
                      <Button
                        variant="primary"
                        size="sm"
                        fullWidth
                        onClick={() => handleOpenDetail(request)}
                      >
                        Duyệt / Từ chối
                      </Button>
                    )}
                    {request.status === "PENDING" && !isManager && request.staff?.id === user?.userId && (
                      <Button
                        variant="danger"
                        size="sm"
                        fullWidth
                        onClick={() => { setDeleteTargetId(request.id); setConfirmOpen(true); }}
                      >
                        Hủy
                      </Button>
                    )}
                  </div>
                </div>
              </article>
            ))}
          </div>
        )}

        {!loading && visibleRequests.length > 0 && (
          <Pagination
            currentPage={page + 1}
            totalPages={totalPages}
            totalItems={totalElements}
            pageSize={pageSize}
            onPageChange={(p) => setPage(p - 1)}
            onPageSizeChange={(s) => { setPageSize(s); setPage(0); }}
          />
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
              <IconButton
                label="Đóng"
                variant="ghost"
                size="sm"
                onClick={() => setDetailRequest(null)}
                className="text-on-surface-variant"
              >
                <span className="material-symbols-outlined text-[20px]" aria-hidden="true">close</span>
              </IconButton>
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
              <Button
                variant="secondary"
                size="md"
                fullWidth
                onClick={() => setDetailRequest(null)}
              >
                Đóng
              </Button>
              {canApprove && isManager && detailRequest.status === "PENDING" && (
                <>
                  <Button
                    variant="danger"
                    size="md"
                    fullWidth
                    disabled={processing}
                    loading={processing}
                    onClick={handleReject}
                    icon={!processing ? <span className="material-symbols-outlined text-[18px]" aria-hidden="true">close</span> : undefined}
                  >
                    Từ chối
                  </Button>
                  <Button
                    variant="secondary"
                    size="md"
                    fullWidth
                    disabled={processing}
                    loading={processing}
                    onClick={handleApprove}
                    icon={!processing ? <span className="material-symbols-outlined text-[18px]" aria-hidden="true">check</span> : undefined}
                    className="!bg-secondary !text-on-secondary hover:!opacity-90"
                  >
                    Duyệt
                  </Button>
                </>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Create Leave Request Modal */}
      {showCreateModal && (
        <Modal
          open={showCreateModal}
          onClose={() => setShowCreateModal(false)}
          title="Tạo yêu cầu nghỉ phép"
          description="Gửi yêu cầu nghỉ phép cho quản lý xét duyệt."
          size="md"
        >
          <div className="space-y-4">
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
          <ModalFooter>
            <Button
              variant="secondary"
              size="md"
              onClick={() => setShowCreateModal(false)}
            >
              Hủy
            </Button>
            <Button
              variant="primary"
              size="md"
              disabled={creating || !createStartDate || !createEndDate}
              loading={creating}
              onClick={() => void handleCreateLeaveRequest()}
              icon={!creating ? <span className="material-symbols-outlined text-[18px]" aria-hidden="true">send</span> : undefined}
            >
              Gửi yêu cầu
            </Button>
          </ModalFooter>
        </Modal>
      )}

      <ConfirmDialog
        open={confirmOpen}
        onClose={() => { setConfirmOpen(false); setDeleteTargetId(null); }}
        onConfirm={confirmCancel}
        title="Hủy yêu cầu nghỉ phép?"
        description="Bạn có chắc muốn hủy yêu cầu này?"
        confirmLabel="Hủy yêu cầu"
        variant="danger"
      />
    </>
  );
}

export default function LeaveRequestsPage() {
  // NOTE: We do an inline role check here instead of using a wrapper
  // component, because RoleGuard (the old wrapper) used to hard-code
  // DashboardShell, which would double-mount the shell on top of the
  // one already provided by the (dashboard) route-group layout.
  const { user } = useAuth();
  const roles = (user?.roles ?? []) as ("ADMIN" | "MANAGER" | "STAFF")[];
  const hasAccess = roles.some((r) => r === "ADMIN" || r === "MANAGER" || r === "STAFF");

  if (!hasAccess) {
    return (
      <>
        <EmptyState
          icon="lock"
          title="Bạn không có quyền truy cập trang này"
          description="Trang này chỉ dành cho Quản lý lịch, Trưởng phòng hoặc Nhân viên. Vui lòng liên hệ quản trị viên nếu cần."
        />
      </>
    );
  }

  return (
    <>
      <Suspense fallback={
        <div className="flex items-center justify-center py-16">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
        </div>
      }>
        <LeaveRequestsContent />
      </Suspense>
    </>
  );
}
