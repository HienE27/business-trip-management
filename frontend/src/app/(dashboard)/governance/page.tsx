"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import { Badge } from "@/components/ui/Badge";
import { AuditTimeline } from "./AuditTimeline";
import { VersionHistory } from "./VersionHistory";
import { ApprovalList } from "./ApprovalList";
import type { AuditSummary, ApprovalRequest } from "@/types/api";

type TabType = "dashboard" | "versions" | "audit" | "approvals" | "policies";

/**
 * v11.2 Governance Dashboard Page
 *
 * Enterprise governance layer for configuration management:
 * - Config versioning with diff and rollback
 * - Complete audit trail
 * - Multi-level approval workflow
 * - Policy engine
 */
export default function GovernancePage() {
  const [activeTab, setActiveTab] = useState<TabType>("dashboard");
  const [auditSummary, setAuditSummary] = useState<AuditSummary | null>(null);
  const [pendingApprovals, setPendingApprovals] = useState<ApprovalRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  // Discard stale dashboard loads (e.g. when the user navigates back fast).
  const latestLoadRequestRef = useRef(0);

  // Load data
  const loadData = useCallback(async () => {
    const requestId = ++latestLoadRequestRef.current;
    setLoading(true);
    setError(null);

    try {
      const [summary, approvals] = await Promise.all([
        api.getAuditSummary(),
        api.getPendingApprovals(),
      ]);
      if (requestId !== latestLoadRequestRef.current) return;
      setAuditSummary(summary as AuditSummary);
      setPendingApprovals((approvals as ApprovalRequest[]) || []);
    } catch (err) {
      if (requestId !== latestLoadRequestRef.current) return;
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      if (requestId === latestLoadRequestRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadData();
  }, [loadData]);

  const tabs: { id: TabType; label: string; icon: string; badge?: number }[] = [
    { id: "dashboard", label: "Dashboard", icon: "dashboard" },
    { id: "versions", label: "Config Versions", icon: "history" },
    { id: "audit", label: "Audit Trail", icon: "fact_check" },
    { id: "approvals", label: "Approvals", icon: "verified_user", badge: pendingApprovals.length },
    { id: "policies", label: "Policies", icon: "policy" },
  ];

  return (
    <div className="p-margin-desktop space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <h1 className="font-display-lg text-display-lg text-on-surface mb-2">Governance</h1>
          <p className="text-body-md text-on-surface-variant">
            Enterprise Administration Layer - Quản trị & Compliance
          </p>
        </div>
      </div>

      {/* Tabs */}
      <div className="flex items-center gap-2 border-b border-outline-variant">
        {tabs.map((tab) => (
          <button
            key={tab.id}
            onClick={() => setActiveTab(tab.id)}
            className={`flex items-center gap-2 px-4 py-3 text-label-md transition-colors relative ${
              activeTab === tab.id
                ? "text-primary font-medium"
                : "text-on-surface-variant hover:text-on-surface"
            }`}
          >
            <span className="material-symbols-outlined text-[20px]">{tab.icon}</span>
            {tab.label}
            {tab.badge !== undefined && tab.badge > 0 && (
              <span className="absolute -top-1 -right-1 w-5 h-5 bg-error text-white text-[10px] font-bold rounded-full flex items-center justify-center">
                {tab.badge}
              </span>
            )}
            {activeTab === tab.id && (
              <div className="absolute bottom-0 left-0 right-0 h-0.5 bg-primary" />
            )}
          </button>
        ))}
      </div>

      {/* Content */}
      {loading ? (
        <div className="grid grid-cols-1 md:grid-cols-4 gap-4">
          {[1, 2, 3, 4].map((i) => (
            <Skeleton key={i} className="h-32 rounded-xl" />
          ))}
        </div>
      ) : error ? (
        <div className="p-6 bg-error-container rounded-xl">
          <p className="text-error">{error}</p>
          <Button variant="ghost" className="mt-2" onClick={loadData}>
            Thử lại
          </Button>
        </div>
      ) : (
        <>
          {/* Dashboard Tab */}
          {activeTab === "dashboard" && auditSummary && (
            <div className="space-y-6">
              {/* KPI Cards */}
              <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-4">
                <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-label-sm text-on-surface-variant">Audit Events</span>
                    <span className="material-symbols-outlined text-primary bg-primary-fixed p-1.5 rounded-lg text-[20px]">fact_check</span>
                  </div>
                  <div className="text-display-lg text-on-surface font-bold">
                    {(auditSummary?.totalEvents ?? 0).toLocaleString()}
                  </div>
                  <div className="text-label-sm text-secondary mt-1">
                    {auditSummary?.todayEvents ?? 0} hôm nay
                  </div>
                </div>

                <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-label-sm text-on-surface-variant">Pending Approvals</span>
                    <span className="material-symbols-outlined text-warning bg-warning-container p-1.5 rounded-lg text-[20px]">verified_user</span>
                  </div>
                  <div className="text-display-lg text-on-surface font-bold">
                    {pendingApprovals.length}
                  </div>
                  <div className="text-label-sm text-on-surface-variant mt-1">
                    Chờ duyệt
                  </div>
                </div>

                <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-label-sm text-on-surface-variant">This Week</span>
                    <span className="material-symbols-outlined text-secondary bg-secondary-container p-1.5 rounded-lg text-[20px]">date_range</span>
                  </div>
                  <div className="text-display-lg text-on-surface font-bold">
                    {(auditSummary?.weekEvents ?? 0).toLocaleString()}
                  </div>
                  <div className="text-label-sm text-on-surface-variant mt-1">
                    Events
                  </div>
                </div>

                <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
                  <div className="flex items-center justify-between mb-3">
                    <span className="text-label-sm text-on-surface-variant">This Month</span>
                    <span className="material-symbols-outlined text-tertiary bg-tertiary-fixed p-1.5 rounded-lg text-[20px]">calendar_month</span>
                  </div>
                  <div className="text-display-lg text-on-surface font-bold">
                    {(auditSummary?.monthEvents ?? 0).toLocaleString()}
                  </div>
                  <div className="text-label-sm text-on-surface-variant mt-1">
                    Events
                  </div>
                </div>
              </div>

              {/* Quick Actions */}
              <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
                <button
                  onClick={() => setActiveTab("versions")}
                  className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 text-left hover:bg-surface-container-low transition-colors"
                >
                  <div className="flex items-center gap-3 mb-3">
                    <span className="material-symbols-outlined text-primary">history</span>
                    <h3 className="font-title-lg text-title-lg text-on-surface">Config Versions</h3>
                  </div>
                  <p className="text-label-sm text-on-surface-variant">
                    Xem lịch sử cấu hình, diff, và rollback
                  </p>
                </button>

                <button
                  onClick={() => setActiveTab("audit")}
                  className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 text-left hover:bg-surface-container-low transition-colors"
                >
                  <div className="flex items-center gap-3 mb-3">
                    <span className="material-symbols-outlined text-secondary">fact_check</span>
                    <h3 className="font-title-lg text-title-lg text-on-surface">Audit Trail</h3>
                  </div>
                  <p className="text-label-sm text-on-surface-variant">
                    Theo dõi mọi thay đổi trong hệ thống
                  </p>
                </button>

                <button
                  onClick={() => setActiveTab("approvals")}
                  className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 text-left hover:bg-surface-container-low transition-colors"
                >
                  <div className="flex items-center gap-3 mb-3">
                    <span className="material-symbols-outlined text-tertiary">verified_user</span>
                    <h3 className="font-title-lg text-title-lg text-on-surface">Approvals</h3>
                  </div>
                  <p className="text-label-sm text-on-surface-variant">
                    Quản lý phê duyệt đa cấp
                  </p>
                </button>
              </div>

              {/* Recent Activity */}
              <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
                <h3 className="font-title-lg text-title-lg text-on-surface mb-4">Recent Activity</h3>
                <AuditTimeline limit={5} />
              </div>
            </div>
          )}

          {/* Versions Tab */}
          {activeTab === "versions" && <VersionHistory />}

          {/* Audit Tab */}
          {activeTab === "audit" && <AuditTimeline limit={50} showSearch />}

          {/* Approvals Tab */}
          {activeTab === "approvals" && <ApprovalList />}

          {/* Policies Tab */}
          {activeTab === "policies" && (
            <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
              <div className="flex items-center justify-between mb-4">
                <div className="flex items-center gap-3">
                  <span className="material-symbols-outlined text-primary">policy</span>
                  <h3 className="font-title-lg text-title-lg text-on-surface">Policies</h3>
                </div>
                <Button variant="primary" size="sm">
                  <span className="material-symbols-outlined text-[16px]">add</span>
                  Tạo Policy
                </Button>
              </div>
              <p className="text-body-md text-on-surface-variant">
                Policy Engine cho phép tạo các quy tắc tùy chỉnh mà không cần thay đổi code.
              </p>
              <div className="mt-4 p-8 bg-surface-container-low rounded-xl text-center">
                <span className="material-symbols-outlined text-[48px] text-on-surface-variant">science</span>
                <p className="mt-2 text-label-md text-on-surface-variant">
                  Policy list sẽ hiển thị ở đây
                </p>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}
