"use client";

import { useEffect, useMemo, useState } from "react";
import { useRouter } from "next/navigation";
import { AllocationStats } from "@/components/dashboard/AllocationStats";
import { ConflictPanel } from "@/components/dashboard/ConflictPanel";
import { ScheduleCalendarWidget } from "@/components/dashboard/ScheduleCalendarWidget";
import { MetricCard } from "@/components/dashboard/MetricCard";
import { SwapRequestsPanel } from "@/components/dashboard/SwapRequestsPanel";
import { DashboardShell } from "@/components/layout/DashboardShell";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type {
  DashboardData,
  Schedule,
  SchedulePeriod,
  ScheduleExchangeResponse,
  ConflictCheckResponse,
} from "@/types/api";
import type {
  Metric,
  ConflictItem,
  SwapRequest,
  AllocationStat,
  ScheduleTone,
} from "@/types/schedule";

export default function DashboardPage() {
  const router = useRouter();
  const [loading, setLoading] = useState(true);
  const [dashboardData, setDashboardData] = useState<DashboardData | null>(null);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [swapRequests, setSwapRequests] = useState<ScheduleExchangeResponse[]>([]);
  const [conflicts, setConflicts] = useState<ConflictCheckResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [warnings, setWarnings] = useState<string[]>([]);

  useEffect(() => {
    let active = true;

    const fetchAllData = async () => {
      setLoading(true);
      setError(null);
      setWarnings([]);

      const nextWarnings: string[] = [];

      const loadSection = async <T,>(label: string, request: Promise<T>) => {
        try {
          return await request;
        } catch (err) {
          const message = getErrorMessage(err, "Unknown error");
          nextWarnings.push(`${label}: ${message}`);
          return null;
        }
      };

      try {
        const [dashboardResult, periodsResult, swapsResult] = await Promise.all([
          loadSection("Dashboard", api.get<DashboardData>("/dashboard")),
          loadSection("Danh sách kỳ", api.get<SchedulePeriod[]>("/periods")),
          loadSection(
            "Yêu cầu đổi ca",
            api.get<ScheduleExchangeResponse[]>("/schedule-exchanges?status=PENDING")
          ),
        ]);

        if (!active) {
          return;
        }

        setDashboardData(dashboardResult);
        setSwapRequests(swapsResult ?? []);

        const periods = periodsResult ?? [];
        if (periods.length > 0) {
          const activePeriod =
            periods.find((p) => p.status === "PUBLISHED" || p.status === "DRAFT") ?? periods[0];

          const [schedulesResult, conflictsResult] = await Promise.all([
            loadSection("Lịch trong kỳ", api.get<Schedule[]>(`/schedules/period/${activePeriod.id}`)),
            loadSection(
              "Kiểm tra xung đột",
              api.get<ConflictCheckResponse>(`/schedules/conflicts/check/${activePeriod.id}`)
            ),
          ]);

          if (!active) {
            return;
          }

          setSchedules(schedulesResult ?? []);
          setConflicts(conflictsResult);
        } else {
          setSchedules([]);
          setConflicts(null);
        }

        setWarnings(nextWarnings);
        if (!dashboardResult && periods.length === 0) {
          setError("Không thể tải dữ liệu dashboard");
        }
      } finally {
        if (active) {
          setLoading(false);
        }
      }
    };

    void fetchAllData();

    return () => {
      active = false;
    };
  }, []);

  const metrics: Metric[] = useMemo(
    () =>
      dashboardData
        ? [
            {
              label: "Tổng nhân sự",
              value: String(dashboardData.summary?.activeStaff ?? 0),
              helper: `${dashboardData.summary?.totalStaff ?? 0} TV`,
              tone: "neutral" as ScheduleTone,
              icon: "group",
            },
            {
              label: "Trực 24/24",
              value: String(dashboardData.shiftStatistics?.L01Count ?? 0),
              tone: "duty24" as ScheduleTone,
              icon: "emergency",
            },
            {
              label: "Thông tầm",
              value: String(dashboardData.shiftStatistics?.L02Count ?? 0),
              tone: "allDay" as ScheduleTone,
              icon: "schedule",
            },
            {
              label: "Lịch dịch vụ",
              value: String(dashboardData.shiftStatistics?.L03Count ?? 0),
              tone: "serviceClinic" as ScheduleTone,
              icon: "medical_services",
            },
            {
              label: "Lịch chuyên gia",
              value: String(dashboardData.shiftStatistics?.L04Count ?? 0),
              tone: "expertClinic" as ScheduleTone,
              icon: "vaccines",
            },
            {
              label: "Xung đột",
              value: String(conflicts?.totalConflicts || 0),
              tone:
                (conflicts?.totalConflicts || 0) > 0
                  ? ("warning" as ScheduleTone)
                  : ("neutral" as ScheduleTone),
              icon: "warning",
            },
          ]
        : [],
    [conflicts?.totalConflicts, dashboardData]
  );

  const conflictItems: ConflictItem[] = (conflicts?.conflicts || []).map((c) => ({
    id: String(c.scheduleId),
    type: c.shiftTypeId,
    staffName: c.staffName,
    date: c.workDate,
    severity: c.conflictReasons.length > 0 ? "Cảnh báo" : ("Chặn lưu" as const),
    detail: c.conflictReasons.join("; ") || "Có xung đột lịch",
  }));

  const swapRequestItems: SwapRequest[] = swapRequests.map((req) => ({
    id: String(req.id),
    requester: req.requester?.fullName || "Nguoi yeu cau",
    requesterInitials: (
      req.requester?.fullName || "U"
    ).charAt(0).toUpperCase(),
    requesterAvatar: "",
    target: req.target?.fullName,
    shiftType: `${req.requesterSchedule?.shiftType?.name || "Lich"} - ${
      req.targetSchedule?.shiftType?.name || "Lich"
    }`,
    date: req.requesterSchedule?.workDate || "",
    reason: req.reason,
    type: "exchange" as const,
    status: "pending" as const,
  }));

  const stats = dashboardData?.shiftStatistics;
  const total =
    (stats?.L01Count ?? 0) +
    (stats?.L02Count ?? 0) +
    (stats?.L03Count ?? 0) +
    (stats?.L04Count ?? 0) || 1;

  const allocationStats: AllocationStat[] = [
    {
      department: "Trực 24/24",
      percentage: Math.round(((stats?.L01Count ?? 0) / total) * 100),
      color: "primary",
    },
    {
      department: "Thông tầm",
      percentage: Math.round(((stats?.L02Count ?? 0) / total) * 100),
      color: "secondary",
    },
    {
      department: "Dịch vụ",
      percentage: Math.round(((stats?.L03Count ?? 0) / total) * 100),
      color: "error",
    },
    {
      department: "Chuyên gia",
      percentage: Math.round(((stats?.L04Count ?? 0) / total) * 100),
      color: "secondary",
    },
  ];

  if (loading) {
    return (
      <DashboardShell
        activeCode="HOME"
        description="Thông tin điều phối nhân sự"
        title="Tổng quan"
      >
        <div className="flex flex-col items-center justify-center min-h-[400px] gap-4">
          <div className="size-8 animate-spin rounded-full border-2 border-primary border-t-transparent" />
          <p className="text-on-surface-variant text-label-md">
            Đang tải dữ liệu...
          </p>
        </div>
      </DashboardShell>
    );
  }

  if (error) {
    return (
      <DashboardShell
        activeCode="HOME"
        description="Thông tin điều phối nhân sự"
        title="Tổng quan"
      >
        <div className="flex flex-col items-center justify-center min-h-[400px] gap-4">
          <span className="material-symbols-outlined text-error text-[48px]">
            error
          </span>
          <p className="text-on-surface-variant text-label-md">{error}</p>
          <button
            className="px-4 py-2 bg-primary text-on-primary rounded-lg text-label-md flex items-center gap-2 hover:opacity-90 transition-colors"
            onClick={() => window.location.reload()}
            type="button"
          >
            Thử lại
          </button>
        </div>
      </DashboardShell>
    );
  }

  return (
    <DashboardShell
      activeCode="HOME"
      description="Thong tin dieu phoi nhan su"
      title="Tong quan"
      >
        {warnings.length > 0 && (
          <div className="rounded-xl border border-warning/30 bg-warning/10 px-4 py-3 text-sm text-on-surface">
            <p className="font-medium">Một số dữ liệu chưa tải được:</p>
            <p className="mt-1 text-on-surface-variant">{warnings.join(" • ")}</p>
          </div>
        )}

        {/* Page Header */}
      <div className="flex items-center justify-between">
        <div />
        <div className="flex gap-3">
          <button
            className="px-4 py-2 bg-surface-container-lowest border border-outline-variant rounded-lg text-label-md text-on-surface flex items-center gap-2 hover:bg-surface-container-low transition-colors shadow-[0_1px_2px_0_rgba(0,0,0,0.05)]"
            type="button"
            onClick={() => router.push("/reports")}
          >
            <span aria-hidden="true" className="material-symbols-outlined text-sm">download</span>
            Xuất báo cáo
          </button>
          <button
            className="px-4 py-2 bg-primary text-on-primary rounded-lg text-label-md flex items-center gap-2 hover:bg-primary/90 transition-colors shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)]"
            type="button"
            onClick={() => router.push("/duty-24")}
          >
            <span aria-hidden="true" className="material-symbols-outlined text-sm">add</span>
            Xếp lịch mới
          </button>
        </div>
      </div>

      {/* Metric Cards */}
      <div className="grid grid-cols-2 md:grid-cols-3 lg:grid-cols-6 gap-4">
        {metrics.map((metric) => (
          <MetricCard key={metric.label} metric={metric} />
        ))}
      </div>

      {/* Content Grid */}
      <div className="grid grid-cols-1 lg:grid-cols-12 gap-6 h-full min-h-[600px]">
        {/* Calendar */}
        <div className="col-span-1 lg:col-span-8 pb-20 lg:pb-24">
          <ScheduleCalendarWidget schedules={schedules} />
        </div>

        {/* Right Widgets */}
        <div className="col-span-1 lg:col-span-4 flex flex-col gap-6">
          <ConflictPanel conflicts={conflictItems} />
          <SwapRequestsPanel requests={swapRequestItems} />
          <AllocationStats stats={allocationStats} />
        </div>
      </div>
    </DashboardShell>
  );
}
