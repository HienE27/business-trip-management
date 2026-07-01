import { GuardedScheduleByTypePage } from "@/components/monthly-schedule/GuardedScheduleByTypePage";
import { SCHEDULE_TYPE_CONFIG_MAP } from "@/components/monthly-schedule/schedule-type-config";

export default function ServiceClinicPage() {
  return <GuardedScheduleByTypePage config={SCHEDULE_TYPE_CONFIG_MAP["service-clinic"]} />;
}
