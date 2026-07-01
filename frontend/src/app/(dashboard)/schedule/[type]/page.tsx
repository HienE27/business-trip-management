import { GuardedScheduleByTypePage } from "@/components/monthly-schedule/GuardedScheduleByTypePage";
import {
  SCHEDULE_TYPE_CONFIG_MAP,
  type ScheduleRouteKey,
} from "@/components/monthly-schedule/schedule-type-config";

interface PageProps {
  params: Promise<{ type: string }>;
}

const VALID_TYPES: ScheduleRouteKey[] = [
  "duty-24",
  "all-day",
  "service-clinic",
  "expert-clinic",
];

export default async function ScheduleByTypePage({ params }: PageProps) {
  const { type } = await params;
  const routeKey = (VALID_TYPES.includes(type as ScheduleRouteKey)
    ? type
    : "duty-24") as ScheduleRouteKey;

  const config = SCHEDULE_TYPE_CONFIG_MAP[routeKey];

  return <GuardedScheduleByTypePage config={config} />;
}
