import type { ShiftDetail, ShiftStaff } from "@/data/shift-detail";

type ShiftDetailTableProps = {
  shift: ShiftDetail;
  className?: string;
};

const ROLE_BADGE_STYLES: Record<string, string> = {
  primary: "bg-tertiary-fixed text-on-tertiary-fixed font-label-sm border border-tertiary/20",
  secondary: "bg-secondary-fixed text-on-secondary-fixed font-label-sm border border-secondary/20",
  neutral: "bg-surface-container-high text-on-surface font-label-sm border border-outline-variant",
};

function StaffAvatar({ staff }: { staff: ShiftStaff }) {
  return (
    <div className="flex items-center gap-3">
      <div
        className={`w-8 h-8 rounded-full flex items-center justify-center font-label-md font-bold shrink-0 ${staff.avatarColor}`}
      >
        {staff.initials}
      </div>
      <div>
        <p className="font-medium text-on-surface group-hover:text-primary transition-colors">
          {staff.name}
        </p>
        <p className="text-on-surface-variant text-[12px]">{staff.department}</p>
      </div>
    </div>
  );
}

export function ShiftDetailTable({ shift, className = "" }: ShiftDetailTableProps) {
  return (
    <section
      className={`bg-surface-container-lowest rounded-lg border border-outline-variant shadow-[0_1px_3px_0_rgba(0,0,0,0.05)] overflow-hidden flex flex-col ${className}`}
    >
      {/* Header */}
      <div className="p-4 border-b border-outline-variant flex justify-between items-center bg-surface-bright">
        <h2 className="font-title-lg text-on-surface flex items-center gap-2">
          <span className="material-symbols-outlined text-primary">groups</span>
          Danh sách chi tiết nhân sự tham gia
        </h2>
        <span className="bg-primary-fixed text-on-primary-fixed-variant px-3 py-1 rounded-full font-label-sm font-medium">
          Tổng: {shift.staff.length} nhân sự
        </span>
      </div>

      {/* Table */}
      <div className="overflow-x-auto">
        <table className="w-full text-left border-collapse">
          <thead>
            <tr className="bg-slate-50 border-b border-outline-variant text-on-surface-variant font-label-sm uppercase tracking-wider">
              <th className="p-4 font-semibold w-12 text-center align-middle">STT</th>
              <th className="p-4 font-semibold align-middle">Họ và tên</th>
              <th className="p-4 font-semibold align-middle">Chức danh / Vai trò</th>
              <th className="p-4 font-semibold align-middle">Vị trí công tác cụ thể</th>
              <th className="p-4 font-semibold align-middle">Ghi chú chuyên môn</th>
            </tr>
          </thead>
          <tbody className="divide-y divide-outline-variant/50">
            {shift.staff.map((staff, index) => (
              <tr
                className="hover:bg-surface-container-low transition-colors group"
                key={staff.id}
              >
                <td className="p-4 text-center text-on-surface-variant align-middle">
                  {index + 1}
                </td>
                <td className="p-4 align-middle">
                  <StaffAvatar staff={staff} />
                </td>
                <td className="p-4 align-middle">
                  <span className={`inline-flex items-center px-2 py-1 rounded ${ROLE_BADGE_STYLES[staff.roleBadge]}`}>
                    {staff.role}
                  </span>
                </td>
                <td className="p-4 align-middle">
                  <span className="text-on-surface font-body-sm">{staff.position}</span>
                </td>
                <td className="p-4 align-middle">
                  {staff.note ? (
                    <span className="text-on-surface-variant italic">{staff.note}</span>
                  ) : (
                    <span className="text-on-surface-variant/40">—</span>
                  )}
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
    </section>
  );
}
