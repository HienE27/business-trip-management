import type { WorkflowStep } from "@/types/schedule";

type AutoSchedulingPanelProps = {
  steps: WorkflowStep[];
};

export function AutoSchedulingPanel({ steps }: AutoSchedulingPanelProps) {
  return (
    <section className="rounded-lg border border-slate-200 bg-[#15191f] p-4 text-white shadow-[0_1px_2px_rgba(15,23,42,0.08)]">
      <p className="text-xs font-medium uppercase text-white/50">M07</p>
      <h2 className="mt-3 text-lg font-semibold">Tự động sắp xếp lịch</h2>
      <p className="mt-2 text-sm leading-6 text-white/64">
        Round Robin phân bổ đều, sau đó quét ràng buộc trực 24/24, thông tầm,
        phòng khám và ngày nghỉ bù.
      </p>
      <div className="mt-4 space-y-2">
        {steps.map((step) => (
          <div className="flex items-center gap-3 rounded-md bg-white/6 p-2" key={step.step}>
            <span
              className={`grid size-7 place-items-center rounded-md text-xs font-semibold ${
                step.status === "Done"
                  ? "bg-emerald-400 text-slate-950"
                  : step.status === "Active"
                    ? "bg-white text-slate-950"
                    : "bg-white/10 text-white/50"
              }`}
            >
              {step.step}
            </span>
            <span className="text-sm">{step.title}</span>
          </div>
        ))}
      </div>
      <button className="mt-4 h-9 w-full rounded-md bg-white text-sm font-medium text-slate-950">
        Xem bản nháp
      </button>
    </section>
  );
}
