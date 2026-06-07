import { DashboardShell } from "@/components/layout/DashboardShell";
import { ShiftDetailInfo } from "@/components/shift-detail/ShiftDetailInfo";
import { ShiftDetailTable } from "@/components/shift-detail/ShiftDetailTable";
import { shiftDetail } from "@/data/shift-detail";

export default function ShiftDetailPage() {
  return (
    <DashboardShell
      activeCode="M02"
      description={`Mã ca: ${shiftDetail.code}`}
      title="Chi tiết ca trực"
    >
      {/* Page Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div />
        <div className="flex items-center gap-3">
          <button
            className="px-4 py-2 bg-surface-container-lowest border border-outline-variant rounded-lg font-label-md text-label-md text-on-surface flex items-center gap-2 hover:bg-surface-container-low transition-colors shadow-[0_1px_2px_0_rgba(0,0,0,0.05)]"
            type="button"
          >
            <span className="material-symbols-outlined text-sm">edit</span>
            Chỉnh sửa
          </button>
          <button
            className="px-4 py-2 bg-primary text-on-primary rounded-lg font-label-md text-label-md flex items-center gap-2 hover:bg-primary/90 transition-colors shadow-[0_1px_3px_0_rgba(0,0,0,0.1),0_1px_2px_-1px_rgba(0,0,0,0.1)]"
            type="button"
          >
            <span className="material-symbols-outlined text-sm">print</span>
            In chi tiết ca trực
          </button>
        </div>
      </div>

      {/* Info Cards */}
      <ShiftDetailInfo shift={shiftDetail} />

      {/* Staff Table */}
      <ShiftDetailTable shift={shiftDetail} />
    </DashboardShell>
  );
}
