export type ShiftStaff = {
  id: string;
  name: string;
  initials: string;
  role: string;
  roleBadge: string;
  department: string;
  departmentFull: string;
  position: string;
  note?: string;
  avatarColor: string;
};

export type ShiftDetail = {
  id: string;
  code: string;
  department: string;
  departmentFull: string;
  date: string;
  weekday: string;
  shiftType: string;
  status: "approved" | "pending" | "draft";
  staff: ShiftStaff[];
};

export const shiftDetail: ShiftDetail = {
  id: "CT-20231024-HSCC",
  code: "CT-20231024-HSCC",
  department: "ICU",
  departmentFull: "Hồi sức cấp cứu (ICU)",
  date: "24/10/2023",
  weekday: "Thứ 3",
  shiftType: "Trực 24/24",
  status: "approved",
  staff: [
    {
      id: "S-001",
      name: "BS. Nguyễn Văn A",
      initials: "A",
      role: "Trưởng ca / BS CKI",
      roleBadge: "primary",
      department: "Khoa HSCC",
      departmentFull: "Hồi sức cấp cứu",
      position: "Phụ trách chung, Hội chẩn ca khó",
      note: "Theo dõi đặc biệt BN giường 01 (Post-op)",
      avatarColor: "bg-primary-fixed-dim text-on-primary-fixed",
    },
    {
      id: "S-002",
      name: "BS. Trần Thị B",
      initials: "B",
      role: "Bác sĩ điều trị",
      roleBadge: "neutral",
      department: "Khoa HSCC",
      departmentFull: "Hồi sức cấp cứu",
      position: "Khu vực B (Giường 06-10)",
      avatarColor: "bg-surface-variant text-on-surface-variant",
    },
    {
      id: "S-003",
      name: "ĐD. Lê Văn C",
      initials: "C",
      role: "Điều dưỡng trưởng ca",
      roleBadge: "secondary",
      department: "Khoa HSCC",
      departmentFull: "Hồi sức cấp cứu",
      position: "Quản lý thuốc, Hành chính ca",
      note: "Bàn giao tủ thuốc trực đầy đủ",
      avatarColor: "bg-secondary-fixed-dim text-on-secondary-fixed-variant",
    },
    {
      id: "S-004",
      name: "ĐD. Phạm Thị D",
      initials: "D",
      role: "Điều dưỡng viên",
      roleBadge: "neutral",
      department: "Khoa HSCC",
      departmentFull: "Hồi sức cấp cứu",
      position: "Chăm sóc Khu vực A (Giường 01-05)",
      note: "Thực hiện y lệnh truyền máu giường 02",
      avatarColor: "bg-surface-variant text-on-surface-variant",
    },
    {
      id: "S-005",
      name: "ĐD. Hoàng Văn E",
      initials: "E",
      role: "Điều dưỡng viên",
      roleBadge: "neutral",
      department: "Khoa HSCC",
      departmentFull: "Hồi sức cấp cứu",
      position: "Chăm sóc Khu vực B (Giường 06-10)",
      avatarColor: "bg-surface-variant text-on-surface-variant",
    },
  ],
};
