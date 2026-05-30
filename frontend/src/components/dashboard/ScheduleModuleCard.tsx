import type { ScheduleModule } from "@/types/schedule";

type ScheduleModuleCardProps = {
  module: ScheduleModule;
};

export function ScheduleModuleCard({ module }: ScheduleModuleCardProps) {
  return (
    <article className="rounded-lg border border-slate-200 bg-white p-4 shadow-[0_1px_2px_rgba(15,23,42,0.05)]">
      <div className="flex items-start justify-between gap-3">
        <div>
          <p className="text-xs font-semibold text-slate-500">{module.code}</p>
          <h3 className="mt-1 text-sm font-semibold">{module.title}</h3>
        </div>
        <span className="rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-xs font-medium text-slate-600">
          {module.priority}
        </span>
      </div>
      <p className="mt-3 min-h-10 text-sm leading-5 text-slate-600">
        {module.description}
      </p>
      <div className="mt-4 h-2 rounded-full bg-slate-100">
        <div
          className="h-2 rounded-full bg-slate-950"
          style={{ width: `${module.progress}%` }}
        />
      </div>
      <p className="mt-2 text-xs text-slate-500">{module.progress}% dữ liệu hợp lệ</p>
    </article>
  );
}
