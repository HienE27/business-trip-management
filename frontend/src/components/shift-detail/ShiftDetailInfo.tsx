import type { ShiftDetail } from "@/data/shift-detail";

type ShiftDetailInfoProps = {
  shift: ShiftDetail;
  className?: string;
};

const STATUS_STYLES: Record<string, string> = {
  approved: "bg-surface-container-high text-on-surface border border-outline-variant",
  pending: "bg-tertiary-container text-on-tertiary-container border border-tertiary/20",
  draft: "bg-surface-container-low text-on-surface-variant border border-outline-variant",
};

const INFO_ICONS: Record<string, string> = {
  department: "apartment",
  date: "calendar_today",
  shiftType: "schedule",
  status: "check_circle",
};

export function ShiftDetailInfo({ shift, className = "" }: ShiftDetailInfoProps) {
  return (
    <section
      className={`bg-surface-container-lowest rounded-lg border border-outline-variant shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] p-6 ${className}`}
    >
      <h2 className="font-title-lg text-title-lg text-on-surface mb-6 border-b border-outline-variant pb-4">
        Thông tin chung
      </h2>
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        {/* Khoa công tác */}
        <div className="flex flex-col gap-1">
          <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">
            Khoa công tác
          </span>
          <div className="flex items-center gap-2 text-on-surface font-body-md font-medium">
            <span className="material-symbols-outlined text-primary text-[20px]">apartment</span>
            {shift.departmentFull}
          </div>
        </div>

        {/* Ngày trực */}
        <div className="flex flex-col gap-1">
          <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">
            Ngày trực
          </span>
          <div className="flex items-center gap-2 text-on-surface font-body-md font-medium">
            <span className="material-symbols-outlined text-primary text-[20px]">calendar_today</span>
            {shift.weekday}, {shift.date}
          </div>
        </div>

        {/* Loại ca */}
        <div className="flex flex-col gap-1">
          <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">
            Loại ca
          </span>
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center px-2.5 py-0.5 rounded font-label-sm bg-primary text-on-primary">
              {shift.shiftType}
            </span>
          </div>
        </div>

        {/* Trạng thái */}
        <div className="flex flex-col gap-1">
          <span className="font-label-sm text-label-sm text-on-surface-variant uppercase tracking-wider">
            Trạng thái
          </span>
          <div className="flex items-center gap-2">
            <span className={`inline-flex items-center px-2.5 py-0.5 rounded font-label-sm ${STATUS_STYLES[shift.status]}`}>
              {shift.status === "approved" ? "Đã duyệt" : shift.status === "pending" ? "Chờ duyệt" : "Bản nháp"}
            </span>
          </div>
        </div>
      </div>
    </section>
  );
}
