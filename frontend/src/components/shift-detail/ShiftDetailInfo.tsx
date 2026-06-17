import type { ShiftDetailViewModel } from "@/types/shift-detail";

type ShiftDetailInfoProps = {
  shift: ShiftDetailViewModel;
  className?: string;
};

const STATUS_STYLES: Record<string, string> = {
  approved: "bg-secondary-container text-secondary border border-secondary/20",
  pending: "bg-tertiary-fixed text-on-tertiary border border-tertiary/20",
  draft: "bg-surface-container-low text-on-surface-variant border border-outline-variant",
};

export function ShiftDetailInfo({ shift, className = "" }: ShiftDetailInfoProps) {
  return (
    <section
      className={`bg-surface-container-lowest rounded-lg border border-outline-variant shadow-sm p-6 ${className}`}
    >
      <h2 className="mb-6 border-b border-outline-variant pb-4 text-title-lg text-on-surface">
        Thông tin chung
      </h2>

      <div className="grid grid-cols-1 gap-6 md:grid-cols-2 lg:grid-cols-3">
        <div className="flex flex-col gap-1">
          <span className="text-label-sm text-on-surface-variant">
            Kỳ lịch
          </span>
          <div className="flex items-center gap-2 text-label-md font-medium text-on-surface">
            <span className="material-symbols-outlined text-[20px] text-primary">calendar_month</span>
            <div>
              <p>{shift.periodName ?? "—"}</p>
              <p className="text-label-md font-normal text-on-surface-variant">{shift.periodRange ?? "—"}</p>
            </div>
          </div>
        </div>

        <div className="flex flex-col gap-1">
          <span className="text-label-sm text-on-surface-variant">
            Chuyên khoa
          </span>
          <div className="flex items-center gap-2 text-label-md font-medium text-on-surface">
            <span className="material-symbols-outlined text-[20px] text-primary">apartment</span>
            {shift.specialtyName ?? shift.departmentFull}
          </div>
        </div>

        <div className="flex flex-col gap-1">
          <span className="text-label-sm text-on-surface-variant">
            Ngày trực
          </span>
          <div className="flex items-center gap-2 text-label-md font-medium text-on-surface">
            <span className="material-symbols-outlined text-[20px] text-primary">calendar_today</span>
            {shift.weekday}, {shift.date}
          </div>
        </div>

        <div className="flex flex-col gap-1">
          <span className="text-label-sm text-on-surface-variant">
            Loại ca
          </span>
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center rounded bg-primary px-2.5 py-0.5 text-label-sm text-on-primary">
              {shift.shiftType}
            </span>
            <span className="text-label-sm text-on-surface-variant">{shift.shiftTime}</span>
          </div>
        </div>

        <div className="flex flex-col gap-1">
          <span className="text-label-sm text-on-surface-variant">
            Nghỉ bù
          </span>
          <div className="flex items-center gap-2 text-label-md font-medium text-on-surface">
            <span className="material-symbols-outlined text-[20px] text-primary">event_available</span>
            {shift.compensationDate ?? "Không áp dụng"}
          </div>
        </div>

        <div className="flex flex-col gap-1">
          <span className="text-label-sm text-on-surface-variant">
            Trạng thái
          </span>
          <div className="flex items-center gap-2">
            <span className={`inline-flex items-center rounded px-2.5 py-0.5 text-label-sm ${STATUS_STYLES[shift.status]}`}>
              {shift.status === "approved" ? "Đã duyệt" : shift.status === "pending" ? "Chờ xử lý" : "Bản nháp"}
            </span>
          </div>
        </div>
      </div>

      {(shift.roles.length > 0 || shift.notes || shift.conflictReasons.length > 0) && (
        <div className="mt-6 space-y-4 border-t border-outline-variant pt-5">
          {shift.roles.length > 0 && (
            <div>
              <p className="text-label-sm text-on-surface-variant">Vai trò hệ thống</p>
              <div className="mt-2 flex flex-wrap gap-2">
                {shift.roles.map((role) => (
                  <span
                    key={role}
                    className="inline-flex items-center rounded-full bg-secondary-fixed px-3 py-1 text-label-sm text-on-secondary-fixed"
                  >
                    {role}
                  </span>
                ))}
              </div>
            </div>
          )}

          {shift.notes && (
            <div>
              <p className="text-label-sm text-on-surface-variant">Ghi chú</p>
              <p className="mt-2 text-label-md leading-relaxed text-on-surface">{shift.notes}</p>
            </div>
          )}

          {shift.conflictReasons.length > 0 && (
            <div className="rounded-lg border border-error-container bg-error-container/10 p-4">
              <p className="text-label-sm font-semibold text-error">Cảnh báo xung đột</p>
              <ul className="mt-2 list-disc space-y-1 pl-5 text-label-md text-on-surface-variant">
                {shift.conflictReasons.map((reason) => (
                  <li key={reason}>{reason}</li>
                ))}
              </ul>
            </div>
          )}
        </div>
      )}
    </section>
  );
}
