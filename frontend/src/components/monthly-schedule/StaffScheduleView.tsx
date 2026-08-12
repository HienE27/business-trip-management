"use client";

import { useEffect, useMemo, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { useToast } from "@/components/ui";
import { EmptyState } from "@/components/ui/EmptyState";
import { Skeleton } from "@/components/ui/Skeleton";
import { ScheduleMatrixView } from "@/components/dashboard/ScheduleMatrixView";
import type { ScheduleTypeConfig } from "@/components/monthly-schedule/ScheduleByTypePage";
import type { Schedule } from "@/types/api";

type StaffShort = {
  id: number;
  fullName: string;
};

/**
 * Read-only "Lịch cá nhân" matrix view cho STAFF (M01-F05 — "xem lịch cá
 * nhân"). Tái sử dụng {@link ScheduleMatrixView} để hiển thị đúng layout
 * matrix (hàng = ngày, cột = nhân sự) như các trang lịch theo kỳ mà STAFF
 * đã truy cập — chỉ khác 2 điểm:
 *
 * <ul>
 *   <li>Data lấy từ {@code /api/v1/schedules/me}, backend tự resolve
 *       staffId = currentUser nên không cần PERIOD_VIEW/STAFF_VIEW_ALL.</li>
 *   <li>{@code isReadOnly=true} → không có FAB, modal, nút sửa/xoá — chỉ xem.</li>
 *   <li>{@code staffList} chỉ gồm 1 nhân sự (chính user) → matrix hiển thị
 *       đúng "lịch của tôi" qua các ngày trong kỳ.</li>
 * </ul>
 *
 * <p>Dùng cho cả 5 route {@code /duty-24}, {@code /all-day}, {@code /service-clinic},
 * {@code /expert-clinic}, {@code /schedule-summary} khi role là STAFF.
 */
export function StaffScheduleView({ config }: { config: ScheduleTypeConfig }) {
  const toast = useToast();
  const [loading, setLoading] = useState(true);
  const [schedules, setSchedules] = useState<Schedule[]>([]);
  const [error, setError] = useState<string | null>(null);
  // Group theo kỳ để có thể hiển thị nhiều kỳ (cũ + mới).
  const [periodKey, setPeriodKey] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError(null);
    api
      .get<Schedule[]>("/schedules/me")
      .then((data) => {
        if (cancelled) return;
        const sorted = [...(data ?? [])].sort((a, b) =>
          a.workDate.localeCompare(b.workDate),
        );
        setSchedules(sorted);
      })
      .catch((err) => {
        if (cancelled) return;
        const msg = getErrorMessage(err, "Không tải được lịch cá nhân");
        setError(msg);
        toast.error(msg);
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [toast]);

  // Filter theo shiftType của trang hiện tại (L01/L02/L03/L04). Nếu ca
  // không có shiftType id (legacy) vẫn hiển thị để user thấy mọi lịch.
  const filteredSchedules = useMemo(() => {
    const target = config.shiftTypeId;
    if (!target) return schedules;
    return schedules.filter((s) => {
      const id = s.shiftType?.id ?? null;
      return id === null || id === target;
    });
  }, [schedules, config.shiftTypeId]);

  // Staff list — với STAFF chỉ có chính mình, derive từ schedules[0].staff
  // (toàn bộ schedules đều cùng user vì backend filter cứng bằng currentUser).
  const staffList = useMemo<StaffShort[]>(() => {
    const seen = new Map<number, StaffShort>();
    for (const s of filteredSchedules) {
      const staff = s.staff;
      if (staff?.id && !seen.has(staff.id)) {
        seen.set(staff.id, { id: staff.id, fullName: staff.fullName ?? "" });
      }
    }
    return Array.from(seen.values());
  }, [filteredSchedules]);

  // Default period — lấy kỳ có nhiều ca nhất (gần nhất). Với 1 user chỉ
  // thấy lịch cá nhân nên việc mặc định tháng của ca đầu tiên là đủ.
  const primaryPeriod = filteredSchedules[0]?.period ?? null;

  useEffect(() => {
    setPeriodKey(primaryPeriod?.id ? String(primaryPeriod.id) : null);
  }, [primaryPeriod?.id]);

  const initialYear =
    primaryPeriod?.startDate
      ? Number(primaryPeriod.startDate.substring(0, 4))
      : new Date().getFullYear();
  const initialMonth =
    primaryPeriod?.startDate
      ? Number(primaryPeriod.startDate.substring(5, 7)) - 1
      : new Date().getMonth();

  // Nếu có nhiều kỳ (user có lịch trải dài), cho phép đổi kỳ đang xem.
  const periodOptions = useMemo(() => {
    const seen = new Map<number, { id: number; label: string }>();
    for (const s of filteredSchedules) {
      const p = s.period;
      if (p && !seen.has(p.id)) {
        seen.set(p.id, {
          id: p.id,
          label: `${p.periodName} (${p.startDate} → ${p.endDate})`,
        });
      }
    }
    return Array.from(seen.values());
  }, [filteredSchedules]);

  // Filter theo periodKey đang chọn.
  const visibleSchedules = useMemo(() => {
    if (!periodKey) return filteredSchedules;
    return filteredSchedules.filter((s) => String(s.periodId) === periodKey);
  }, [filteredSchedules, periodKey]);

  const visibleStaffList = useMemo<StaffShort[]>(() => {
    if (!periodKey) return staffList;
    return Array.from(
      new Map(
        visibleSchedules
          .map((s) => s.staff)
          .filter((s): s is StaffShort => Boolean(s?.id))
          .map((s) => [s.id, { id: s.id, fullName: s.fullName ?? "" }]),
      ).values(),
    );
  }, [staffList, visibleSchedules, periodKey]);

  return (
    <div className="space-y-4">
      <div>
        <h1 className="text-title-lg font-bold text-on-surface">{config.title}</h1>
        <p className="mt-1 text-body-md text-on-surface-variant">
          {config.description}
        </p>
        <p className="mt-1 text-label-md text-on-surface-variant">
          Đang hiển thị lịch <b>cá nhân</b> của bạn ở chế độ chỉ-xem. Mọi thay
          đổi phải được quản lý lịch thực hiện.
        </p>
      </div>

      {loading ? (
        <div className="space-y-3">
          <Skeleton className="h-10 w-full" />
          <Skeleton className="h-72 w-full" />
        </div>
      ) : error ? (
        <EmptyState icon="error" title="Không tải được lịch" description={error} />
      ) : filteredSchedules.length === 0 ? (
        <EmptyState
          icon="event_busy"
          title="Chưa có ca nào được phân công"
          description="Liên hệ quản lý lịch nếu bạn nghĩ đây là sai sót."
        />
      ) : (
        <div className="rounded-lg border border-outline-variant bg-surface overflow-hidden">
          {/* Header bar với dropdown chọn kỳ (chỉ hiển thị khi có &gt;1 kỳ) */}
          {periodOptions.length > 1 && (
            <div className="flex items-center gap-2 px-4 py-2 border-b border-outline-variant bg-surface-container-low">
              <span className="material-symbols-outlined text-blue-800 text-[18px]">
                event_note
              </span>
              <label
                htmlFor="staff-period-select"
                className="text-label-md text-on-surface"
              >
                Kỳ lịch:
              </label>
              <select
                id="staff-period-select"
                value={periodKey ?? ""}
                onChange={(e) => setPeriodKey(e.target.value || null)}
                className="flex-1 max-w-md rounded-md border border-outline-variant bg-surface px-2 py-1 text-body-md"
              >
                {periodOptions.map((p) => (
                  <option key={p.id} value={p.id}>
                    {p.label}
                  </option>
                ))}
              </select>
              <span className="ml-auto text-label-md text-on-surface-variant">
                Tổng cộng: {visibleSchedules.length} ca
              </span>
            </div>
          )}

          <ScheduleMatrixView
            key={periodKey ?? "default"}
            schedules={visibleSchedules}
            staffList={visibleStaffList}
            initialYear={initialYear}
            initialMonth={initialMonth}
            periodId={
              periodKey ? Number(periodKey) : primaryPeriod?.id ?? null
            }
            periodStart={
              visibleSchedules[0]?.period?.startDate ?? primaryPeriod?.startDate
            }
            periodEnd={
              visibleSchedules[0]?.period?.endDate ?? primaryPeriod?.endDate
            }
            isReadOnly
            hideFilters
            selectedTab={config.shiftTypeId}
          />
        </div>
      )}
    </div>
  );
}