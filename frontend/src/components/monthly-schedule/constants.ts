import type { ScheduleTab, WorkflowDefinition } from "./types";
export { SHIFT_COLORS, SHIFT_TYPE_BADGES } from "@/lib/shift-colors";
export type { ShiftColorSet } from "@/lib/shift-colors";

export const TAB_OPTIONS: { id: ScheduleTab; label: string; shortLabel: string; description: string }[] = [
  { id: "L01", label: "Trực 24/24", shortLabel: "24/24", description: "Ca trực xuyên ngày, có nghỉ bù và ràng buộc fatigue cao nhất." },
  { id: "L02", label: "Thông tầm", shortLabel: "TT", description: "Ca ngày liên tục, không nghỉ trưa và không được trùng với trực 24/24." },
  { id: "L03", label: "PK dịch vụ", shortLabel: "PKDV", description: "Lịch phòng khám dịch vụ theo ngày, ưu tiên theo năng lực chuyên môn." },
  { id: "L04", label: "PK chuyên gia", shortLabel: "PKCG", description: "Lịch phòng khám chuyên gia theo chuyên khoa, không được trùng với PKDV." },
];

export const WORKFLOW_STEPS: WorkflowDefinition[] = [
  { id: "auto-schedule", title: "Tự động xếp lịch", description: "Tạo phương án phân công ban đầu cho kỳ lịch." },
  { id: "conflicts", title: "Kiểm tra xung đột", description: "Quét xung đột và đánh dấu lịch cần xử lý trước khi công bố." },
  { id: "review", title: "Đánh giá", description: "Đối chiếu tải công việc, ngày nghỉ bù và mức độ phủ lịch." },
  { id: "publish", title: "Công bố", description: "Khóa bản nháp, công bố kỳ lịch hợp lệ và chuyển trạng thái vận hành." },
  { id: "notify", title: "Thông báo", description: "Gửi thông báo cho nhân sự và đẩy dữ liệu sang các màn báo cáo." },
];

export const SHIFT_TYPE_LABELS: Record<ScheduleTab, string> = {
  L01: "Trực 24/24",
  L02: "Thông tầm",
  L03: "Phòng khám DV",
  L04: "Phòng khám CG",
  ALL: "Tất cả",
};

export const ALGORITHM_OPTIONS = [
  { value: "GREEDY", label: "GREEDY — Ưu tiên cân bằng tải" },
  { value: "ROUND_ROBIN", label: "ROUND_ROBIN — Xen kẽ luân phiên" },
  { value: "BACKTRACKING", label: "BACKTRACKING — Tìm kiếm tối ưu" },
  { value: "CSP_MRV_FC", label: "CSP-MRV-FC — Constraint Satisfaction (khuyến nghị)" },
  { value: "GENETIC", label: "GENETIC — Thuật toán di truyền" },
] as const;

export const WEEKDAYS = ["Chủ Nhật", "Thứ 2", "Thứ 3", "Thứ 4", "Thứ 5", "Thứ 6", "Thứ 7"];
