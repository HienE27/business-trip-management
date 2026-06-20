"use client";

import { RoleGuard, type UserRole } from "@/components/auth/RoleGuard";
import {
  ScheduleByTypePage,
  type ScheduleTypeConfig,
} from "@/components/monthly-schedule/ScheduleByTypePage";

type GuardedScheduleByTypePageProps = {
  config: ScheduleTypeConfig;
  allow?: UserRole[];
};

/**
 * Wraps ScheduleByTypePage with a RoleGuard so the 4 schedule-by-type
 * routes (/duty-24, /all-day, /service-clinic, /expert-clinic) get the
 * same deny-state UX as the rest of the dashboard.
 *
 * The component has its own `canManage` logic inside to hide manager-only
 * controls (the "Thêm ca" CTA, WorkflowStepper, edit affordances) from
 * STAFF users — but that doesn't prevent a STAFF user from navigating to
 * the URL and seeing the schedule read-only. This guard closes that gap.
 *
 * Default allow list: ADMIN + MANAGER. Pass a different list for STAFF-
 * accessible variants.
 */
export function GuardedScheduleByTypePage({
  config,
  allow = ["ADMIN", "MANAGER"],
}: GuardedScheduleByTypePageProps) {
  return (
    <RoleGuard
      activeSection={config.activeSection}
      title={config.title}
      description={config.description}
      allow={allow}
    >
      <ScheduleByTypePage config={config} />
    </RoleGuard>
  );
}