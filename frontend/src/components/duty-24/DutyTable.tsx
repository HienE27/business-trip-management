type StatusBadgeTone = "success" | "warning" | "danger" | "neutral" | "info";

type DutyRow = {
  id: number;
  date: string;
  weekday: string;
  staff: string;
  specialty?: string;
  role?: string;
  compDay: string;
  status: string;
  statusTone: StatusBadgeTone;
  detailHref?: string;
};

type DutyTableProps = {
  rows: DutyRow[];
  showStaffInfo?: boolean;
  onRowClick?: (row: DutyRow) => void;
};

function resolveTone(status: string): StatusBadgeTone {
  if (["Hoàn tất", "Hợp lệ", "Đang làm", "Đã phê duyệt", "Hoàn thành"].includes(status)) return "success";
  if (["Chấn lưu", "Cần kiểm tra", "Quá tải", "Chặn lưu"].includes(status)) return "danger";
  if (["Cảnh báo", "Cân đối chiêu", "Đang chạy", "Nghỉ phép", "Cảnh báo nhẹ", "Đang chờ"].includes(status)) return "warning";
  if (["Chờ", "Chờ phân công", "Chờ duyệt", "Bản nháp", "Đang xử lý"].includes(status)) return "neutral";
  return "info";
}

const BADGE_STYLES: Record<StatusBadgeTone, string> = {
  success: "bg-secondary-fixed text-on-secondary-fixed border border-secondary/20",
  warning: "bg-tertiary-fixed text-on-tertiary-fixed border border-tertiary/20",
  danger: "bg-error-container text-on-error-container border border-error/20",
  neutral: "bg-surface-container-high text-on-surface-variant border border-outline/10",
  info: "bg-primary-fixed text-on-primary-fixed-variant border border-primary/20",
};

const AVATAR_STYLES: string[] = [
  "bg-primary-fixed-dim text-on-primary-fixed",
  "bg-secondary-fixed-dim text-on-secondary-fixed-variant",
  "bg-tertiary-fixed-dim text-on-tertiary-fixed",
  "bg-surface-variant text-on-surface-variant",
];

function getAvatarStyle(name: string): string {
  return AVATAR_STYLES[name.charCodeAt(0) % AVATAR_STYLES.length];
}

const HEADERS = ["Ngày trực", "Thứ", "Nhân sự trực", "Nghỉ bù", "Trạng thái", "Thao tác"];

export function DutyTable({ rows, showStaffInfo = false, onRowClick }: DutyTableProps) {
  return (
    <div className="overflow-x-auto">
      <table className="w-full min-w-[940px] border-collapse text-left">
        <thead>
          <tr className="border-b border-outline-variant text-label-sm uppercase tracking-wider text-on-surface-variant">
            {HEADERS.map((h) => (
              <th className="p-4 font-semibold" key={h}>{h}</th>
            ))}
          </tr>
        </thead>
        <tbody className="divide-y divide-outline-variant/50">
          {rows.length === 0 ? (
            <tr>
              <td className="px-5 py-10 text-center text-on-surface-variant" colSpan={HEADERS.length}>
                Chưa có dữ liệu để hiển thị.
              </td>
            </tr>
          ) : (
            rows.map((row, ri) => (
              <tr
                className={`group h-12 transition-colors hover:bg-surface-container-low ${onRowClick ? "cursor-pointer" : ""}`}
                key={ri}
                onClick={() => onRowClick?.(row)}
              >
                <td className="px-4 py-3 text-label-md font-medium text-on-surface">{row.date}</td>
                <td className="px-4 py-3 text-label-md text-on-surface">{row.weekday}</td>
                <td className="px-4 py-3">
                  <div className="flex items-center gap-3">
                    <div className={`flex h-8 w-8 shrink-0 items-center justify-center rounded-full text-label-md font-bold ${getAvatarStyle(row.staff)}`}>
                      {row.staff.charAt(0)}
                    </div>
                    <div>
                      <p className="text-label-md text-on-surface transition-colors group-hover:text-primary">{row.staff}</p>
                      {showStaffInfo && row.specialty && (
                        <p className="text-[12px] text-on-surface-variant">{row.specialty}</p>
                      )}
                    </div>
                  </div>
                </td>
                <td className="px-4 py-3 text-label-md text-on-surface">{row.compDay}</td>
                <td className="px-4 py-3">
                  <span className={`inline-flex items-center gap-1.5 rounded-full px-2.5 py-0.5 text-label-sm font-medium ${BADGE_STYLES[resolveTone(row.status)]}`}>
                    {row.status}
                  </span>
                </td>
                <td className="px-4 py-3">
                  <button
                    className="inline-flex items-center gap-1.5 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 py-1.5 text-label-sm text-on-surface transition-colors hover:bg-surface-container-low hover:text-primary"
                    onClick={(event) => {
                      event.stopPropagation();
                      onRowClick?.(row);
                    }}
                    type="button"
                  >
                    <span className="material-symbols-outlined text-[16px]">open_in_new</span>
                    Xem chi tiết
                  </button>
                </td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}
