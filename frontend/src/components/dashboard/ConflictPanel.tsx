import type { ConflictItem } from "@/types/schedule";

type ConflictPanelProps = {
  conflicts: ConflictItem[];
};

export function ConflictPanel({ conflicts }: ConflictPanelProps) {
  return (
    <section className="rounded-lg border border-slate-200 bg-white shadow-[0_1px_2px_rgba(15,23,42,0.05)]">
      <div className="border-b border-slate-200 p-4">
        <h2 className="text-sm font-semibold">Cảnh báo xung đột</h2>
        <p className="text-xs text-slate-500">Logic dùng chung cho thủ công và tự động</p>
      </div>
      <div className="divide-y divide-slate-100">
        {conflicts.map((conflict) => (
          <div className="p-4" key={`${conflict.staff}-${conflict.date}-${conflict.type}`}>
            <div className="flex items-start justify-between gap-3">
              <div>
                <p className="text-sm font-medium">{conflict.type}</p>
                <p className="mt-1 text-xs text-slate-500">
                  {conflict.staff} · {conflict.date}
                </p>
              </div>
              <span
                className={`shrink-0 rounded-md border px-2 py-1 text-xs font-medium ${
                  conflict.severity === "Chặn lưu"
                    ? "border-rose-200 bg-rose-50 text-rose-700"
                    : "border-amber-200 bg-amber-50 text-amber-700"
                }`}
              >
                {conflict.severity}
              </span>
            </div>
          </div>
        ))}
      </div>
    </section>
  );
}
