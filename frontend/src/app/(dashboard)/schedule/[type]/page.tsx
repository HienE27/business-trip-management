import { notFound } from "next/navigation";
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

  // BUGFIX (was FE#15): the previous implementation silently remapped
  // any unknown type segment (e.g. /schedule/foo) to "duty-24". That
  // hid typos from the user and made /schedule/<anything> always render
  // the L01 duty roster, confusing anybody debugging a stale link.
  // Now we 404 loudly for unknown segments so the user gets a clear
  // signal and isn't accidentally reading the wrong schedule.
  if (!VALID_TYPES.includes(type as ScheduleRouteKey)) {
    notFound();
  }

  const routeKey = type as ScheduleRouteKey;
  const config = SCHEDULE_TYPE_CONFIG_MAP[routeKey];

  return <GuardedScheduleByTypePage config={config} />;
}
