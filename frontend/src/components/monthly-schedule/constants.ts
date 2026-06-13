import type { ScheduleTab, WorkflowDefinition } from "./types";

export const TAB_OPTIONS: { id: ScheduleTab; label: string; shortLabel: string; description: string }[] = [
  { id: "L01", label: "Trực 24/24", shortLabel: "24/24", description: "Ca trực xuyên ngày, có nghỉ bù và ràng buộc fatigue cao nhất." },
  { id: "L02", label: "Thông tầm", shortLabel: "TT", description: "Ca ngày liên tục, không nghỉ trưa và không được trùng với trực 24/24." },
  { id: "L03", label: "PK dịch vụ", shortLabel: "PKDV", description: "Lịch phòng khám dịch vụ theo ngày, ưu tiên theo năng lực chuyên môn." },
  { id: "L04", label: "PK chuyên gia", shortLabel: "PKCG", description: "Lịch phòng khám chuyên gia theo chuyên khoa, không được trùng với PKDV." },
];

export const WORKFLOW_STEPS: WorkflowDefinition[] = [
  { id: "auto-schedule", title: "Auto Schedule", description: "Tạo phương án phân công ban đầu cho kỳ lịch." },
  { id: "conflicts", title: "Conflict Check", description: "Quét xung đột và đánh dấu lịch cần xử lý trước khi công bố." },
  { id: "review", title: "Review", description: "Đối chiếu tải công việc, ngày nghỉ bù và mức độ phủ lịch." },
  { id: "publish", title: "Publish", description: "Khóa bản nháp, công bố kỳ lịch hợp lệ và chuyển trạng thái vận hành." },
  { id: "notify", title: "Notify", description: "Gửi thông báo cho nhân sự và đẩy dữ liệu sang các màn báo cáo." },
];

export const SHIFT_TYPE_LABELS: Record<ScheduleTab, string> = {
  L01: "Trực 24/24",
  L02: "Lịch thông tầm",
  L03: "Phòng khám dịch vụ",
  L04: "Phòng khám chuyên gia",
};

export const SHIFT_TYPE_COLORS: Record<ScheduleTab, string> = {
  L01: "bg-red-500",
  L02: "bg-blue-500",
  L03: "bg-green-500",
  L04: "bg-purple-500",
};

export const SHIFT_TYPE_BADGES: Record<ScheduleTab, string> = {
  L01: "bg-red-50 text-red-700 border-red-200",
  L02: "bg-blue-50 text-blue-700 border-blue-200",
  L03: "bg-green-50 text-green-700 border-green-200",
  L04: "bg-purple-50 text-purple-700 border-purple-200",
};

export const ALGORITHM_OPTIONS = [
  { value: "GREEDY", label: "GREEDY — Ưu tiên cân bằng tải" },
  { value: "ROUND_ROBIN", label: "ROUND_ROBIN — Xen kẽ luân phiên" },
  { value: "BACKTRACKING", label: "BACKTRACKING — Tìm kiếm tối ưu" },
] as const;

export const WEEKDAYS = ["Chủ Nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"];
