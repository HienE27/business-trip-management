import type { ScheduleModule } from "@/types/schedule";

type ScheduleModuleCardProps = {
  module: ScheduleModule;
};

export function ScheduleModuleCard({ module }: ScheduleModuleCardProps) {
  return (
    <article className="min-h-[184px] rounded-lg border border-[#dfe4ea] bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold leading-4 text-[#667085]">{module.code}</p>
          <h3 className="mt-1 text-sm font-semibold leading-5 text-[#111418]">{module.title}</h3>
        </div>
        <span className="rounded-lg border border-[#dfe4ea] bg-[#f8fafc] px-2 py-1 text-xs font-medium text-[#667085]">
          {module.priority}
        </span>
      </div>
      <p className="mt-3 min-h-10 text-sm leading-5 text-[#4b5565]">
        {module.description}
      </p>
      <div className="mt-4 h-2 rounded-full bg-[#edf1f5]">
        <div
          className="h-2 rounded-full bg-[#111418]"
          style={{ width: `${module.progress}%` }}
        />
      </div>
      <p className="mt-2 text-xs leading-4 text-[#667085]">{module.progress}% dữ liệu hợp lệ</p>
    </article>
  );
}
