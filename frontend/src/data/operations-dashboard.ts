export const swapRequests = [
  [
    "REQ-024",
    "Nguyen Minh Anh",
    "31/05/2026",
    "Tran Duc Huy",
    "03/06/2026",
    "Chờ duyệt",
  ],
  [
    "REQ-023",
    "Le Bao Chau",
    "29/05/2026",
    "Do Lan Phuong",
    "30/05/2026",
    "Hợp lệ",
  ],
  [
    "REQ-022",
    "Pham Quoc Viet",
    "28/05/2026",
    "Tran Minh Khoa",
    "31/05/2026",
    "Chặn lưu",
  ],
];

export const swapValidationSteps = [
  ["B1", "Kiểm tra người gửi có lịch trực ở ngày cũ", "Hoàn tất"],
  ["B2", "Mô phỏng lịch sau khi đổi cho cả hai nhân sự", "Hoàn tất"],
  ["B3", "Quét trùng thông tầm và ngày nghỉ bù", "Đang chạy"],
  ["B4", "Gửi kết quả cho quản lý duyệt", "Chờ"],
];

export const conflictRows = [
  [
    "CF-101",
    "Trực 24/24 trùng thông tầm",
    "Nguyen Minh Anh",
    "31/05/2026",
    "M02 / M03",
    "Chặn lưu",
  ],
  [
    "CF-102",
    "Xếp lịch vào ngày nghỉ bù",
    "Tran Duc Huy",
    "28/05/2026",
    "M02 / M04",
    "Chặn lưu",
  ],
  [
    "CF-103",
    "Dịch vụ trùng chuyên gia",
    "Le Bao Chau",
    "29/05/2026",
    "M04 / M05",
    "Cảnh báo",
  ],
  [
    "CF-104",
    "Ngoại lệ nghỉ phép",
    "Do Lan Phuong",
    "30/05/2026",
    "M07",
    "Cảnh báo",
  ],
];

export const conflictSummary = [
  ["Tổng lỗi", "04"],
  ["Chặn lưu", "02"],
  ["Cảnh báo", "02"],
  ["Đã sửa", "00"],
];

export const reportRows = [
  ["RP-0526-ALL", "Lịch toàn phòng tháng 05/2026", "Excel", "Sẵn sàng"],
  ["RP-0526-L01", "Lịch trực 24/24 và nghỉ bù", "PDF", "Sẵn sàng"],
  ["RP-0526-LOAD", "Cân bằng tải nhân sự", "Excel", "Đang chạy"],
  ["RP-0526-CONFLICT", "Danh sách xung đột", "PDF", "Chờ"],
];

export const exportFilters = [
  ["Kỳ báo cáo", "Tháng 05/2026"],
  ["Phạm vi", "Toàn phòng"],
  ["Loại lịch", "Tất cả 4 loại"],
  ["Định dạng", "Excel + PDF"],
];

export const auditRows = [
  ["18:42", "admin", "Cập nhật lịch trực 24/24", "M02", "Hợp lệ"],
  ["18:37", "manager01", "Duyệt yêu cầu đổi trực REQ-023", "M02-F04", "Hoàn tất"],
  ["18:22", "admin", "Chạy kiểm tra xung đột toàn tháng", "M06-F03", "Cảnh báo"],
  ["17:58", "scheduler", "Tạo bản nháp tự động bằng Round Robin", "M07", "Hoàn tất"],
  ["17:41", "admin", "Ngừng hoạt động nhân sự NV018", "M01", "Hợp lệ"],
];

export const roleMatrixRows = [
  ["Quản lý lịch", "Tạo / sửa / xóa lịch", "Duyệt đổi trực", "Xuất báo cáo", "Toàn quyền"],
  ["Trưởng phòng", "Xem toàn phòng", "Duyệt yêu cầu", "Xem báo cáo", "Giới hạn"],
  ["Nhân viên", "Xem lịch cá nhân", "Gửi yêu cầu đổi", "Không", "Cá nhân"],
];

export const permissionCards = [
  {
    title: "Quản lý lịch",
    detail: "Toàn quyền thao tác M01-M07, công bố lịch và xuất báo cáo.",
  },
  {
    title: "Trưởng phòng",
    detail: "Xem toàn phòng, phê duyệt đổi trực và theo dõi cảnh báo.",
  },
  {
    title: "Nhân viên",
    detail: "Xem lịch cá nhân, nhận thông báo và gửi yêu cầu đổi trực.",
  },
];
