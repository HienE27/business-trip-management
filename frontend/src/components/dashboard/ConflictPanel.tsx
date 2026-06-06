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
        {conflicts.length === 0 ? (
          <div className="p-6 text-center">
            <div className="mx-auto flex size-12 items-center justify-center rounded-full bg-emerald-50 text-emerald-600">
              <svg className="size-6" fill="none" viewBox="0 0 24 24" strokeWidth="2" stroke="currentColor">
                <path strokeLinecap="round" strokeLinejoin="round" d="M9 12l2 2 4-4m6 2a9 9 0 11-18 0 9 9 0 0118 0z" />
              </svg>
            </div>
            <h3 className="mt-2 text-sm font-semibold text-slate-900">Hệ thống an toàn</h3>
            <p className="mt-1 text-xs text-slate-500">Không phát hiện xung đột lịch trực nào trong kỳ này.</p>
          </div>
        ) : (
          conflicts.map((conflict, index) => (
            <div className="p-4" key={`${conflict.staff}-${conflict.date}-${conflict.type}-${index}`}>
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
          ))
        )}
      </div>
    </section>
  );
}
