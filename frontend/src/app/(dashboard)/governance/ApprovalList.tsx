"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";
import { Skeleton } from "@/components/ui/Skeleton";
import { useToast } from "@/hooks/useToast";
import type { ApprovalRequest, ApprovalStatus } from "@/types/api";

/**
 * Approval request list component.
 */
export function ApprovalList() {
  const toast = useToast();
  const [requests, setRequests] = useState<ApprovalRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [processing, setProcessing] = useState<number | null>(null);
  // Generation counter: ignore stale list fetches so that an in-flight
  // load from before an approve/reject can't wipe out a fresher one.
  const latestListRequestRef = useRef(0);

  const loadRequests = useCallback(async () => {
    const requestId = ++latestListRequestRef.current;
    setLoading(true);
    setError(null);

    try {
      const data = await api.getPendingApprovals();
      if (requestId !== latestListRequestRef.current) return;
      setRequests((data as ApprovalRequest[]) || []);
    } catch (err) {
      if (requestId !== latestListRequestRef.current) return;
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      if (requestId === latestListRequestRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadRequests();
  }, [loadRequests]);

  const handleApprove = async (id: number) => {
    setProcessing(id);
    try {
      await api.approveRequest(id, "Approved from UI");
      await loadRequests();
      toast.success("Đã duyệt yêu cầu.");
    } catch (err) {
      toast.error(getErrorMessage(err, "Không thể duyệt yêu cầu."));
    } finally {
      setProcessing(null);
    }
  };

  const handleReject = async (id: number) => {
    const reason = prompt("Nhập lý do từ chối:");
    if (!reason) return;

    setProcessing(id);
    try {
      await api.rejectApproval(id, reason);
      await loadRequests();
      toast.success("Đã từ chối yêu cầu.");
    } catch (err) {
      toast.error(getErrorMessage(err, "Không thể từ chối yêu cầu."));
    } finally {
      setProcessing(null);
    }
  };

  const statusConfig: Record<ApprovalStatus, { label: string; tone: "success" | "warning" | "error" | "info" }> = {
    PENDING: { label: "Đang chờ", tone: "info" },
    DRAFT: { label: "Bản nháp", tone: "info" },
    SUBMITTED: { label: "Đã gửi", tone: "info" },
    UNDER_REVIEW: { label: "Đang xem xét", tone: "info" },
    APPROVED: { label: "Đã duyệt", tone: "success" },
    REJECTED: { label: "Từ chối", tone: "error" },
    CHANGES_REQUESTED: { label: "Yêu cầu sửa", tone: "warning" },
    CANCELLED: { label: "Đã hủy", tone: "warning" },
    EXPIRED: { label: "Hết hạn", tone: "error" },
    APPLIED: { label: "Đã áp dụng", tone: "success" },
  };

  const getPriorityColor = (priority: string | number | undefined) => {
    const p = Number(priority) || 0;
    if (p <= 1) return "error";
    if (p <= 3) return "warning";
    return "info";
  };

  const getPriorityLabel = (priority: string | number | undefined) => {
    const p = Number(priority) || 0;
    if (p === 1) return "Rất cao";
    if (p === 2) return "Cao";
    if (p === 3) return "Trung bình";
    return "Thấp";
  };

  if (loading) {
    return (
      <div className="space-y-4">
        {[1, 2, 3].map((i) => (
          <Skeleton key={i} className="h-32 rounded-xl" />
        ))}
      </div>
    );
  }

  if (error) {
    return (
      <div className="p-6 bg-error-container rounded-xl">
        <p className="text-error">{error}</p>
        <Button variant="ghost" className="mt-2" onClick={loadRequests}>
          Thử lại
        </Button>
      </div>
    );
  }

  return (
    <div className="space-y-4">
      <div className="flex items-center justify-between">
        <h3 className="font-title-lg text-title-lg text-on-surface">
          Pending Approvals ({requests.length})
        </h3>
        <Button variant="primary" size="sm">
          <span className="material-symbols-outlined text-[16px]">add</span>
          Tạo Request
        </Button>
      </div>

      {requests.length === 0 ? (
        <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-8 text-center">
          <span className="material-symbols-outlined text-[48px] text-on-surface-variant">verified_user</span>
          <h3 className="mt-4 font-title-lg text-title-lg text-on-surface">Không có request nào</h3>
          <p className="mt-2 text-body-md text-on-surface-variant">
            Tất cả các yêu cầu đã được xử lý
          </p>
        </div>
      ) : (
        <div className="space-y-3">
          {requests.map((request) => {
            const status = statusConfig[request.status];

            return (
              <div
                key={request.id}
                className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5"
              >
                <div className="flex items-start justify-between gap-4">
                  <div className="flex-1">
                    <div className="flex items-center gap-2 mb-2">
                      <h4 className="font-title-lg text-title-lg text-on-surface">
                        {request.title}
                      </h4>
                      <Badge tone={status.tone}>{status.label}</Badge>
                      <Badge tone={getPriorityColor(request.priority)}>
                        {getPriorityLabel(request.priority)}
                      </Badge>
                    </div>

                    {request.description && (
                      <p className="text-body-sm text-on-surface-variant mb-3">
                        {request.description}
                      </p>
                    )}

                    <div className="flex items-center gap-4 text-label-sm text-on-surface-variant">
                      <span>
                        <span className="material-symbols-outlined text-[14px] mr-1">person</span>
                        {request.submittedByName || "Unknown"}
                      </span>
                      <span>
                        <span className="material-symbols-outlined text-[14px] mr-1">category</span>
                        {request.entityType}
                      </span>
                      <span>
                        <span className="material-symbols-outlined text-[14px] mr-1">schedule</span>
                        {request.createdAt ? new Date(request.createdAt).toLocaleDateString("vi-VN") : "N/A"}
                      </span>
                      {request.dueDate && (
                        <span className="text-warning">
                          <span className="material-symbols-outlined text-[14px] mr-1">schedule</span>
                          Hạn: {request.dueDate ? new Date(request.dueDate).toLocaleDateString("vi-VN") : "N/A"}
                        </span>
                      )}
                    </div>
                  </div>

                  <div className="flex gap-2">
                    <Button
                      variant="primary"
                      size="sm"
                      onClick={() => handleApprove(request.id)}
                      disabled={processing === request.id}
                    >
                      <span className="material-symbols-outlined text-[16px]">check</span>
                      Duyệt
                    </Button>
                    <Button
                      variant="danger"
                      size="sm"
                      onClick={() => handleReject(request.id)}
                      disabled={processing === request.id}
                    >
                      <span className="material-symbols-outlined text-[16px]">close</span>
                      Từ chối
                    </Button>
                    <Button variant="ghost" size="sm">
                      <span className="material-symbols-outlined text-[16px]">visibility</span>
                    </Button>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
