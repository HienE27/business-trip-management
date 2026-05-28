export const staffMembers = [
  {
    code: "NV001",
    name: "Nguyen Minh Anh",
    role: "Quản lý lịch",
    position: "Điều dưỡng trưởng",
    specialty: "Nội tổng hợp",
    phone: "0901 112 234",
    email: "minhanh@clinic.vn",
    status: "Đang làm",
  },
  {
    code: "NV002",
    name: "Tran Duc Huy",
    role: "Nhân viên",
    position: "Bác sĩ",
    specialty: "Ngoại",
    phone: "0901 223 345",
    email: "duchuy@clinic.vn",
    status: "Đang làm",
  },
  {
    code: "NV003",
    name: "Le Bao Chau",
    role: "Trưởng phòng",
    position: "Bác sĩ",
    specialty: "Nhi",
    phone: "0901 334 456",
    email: "baochau@clinic.vn",
    status: "Nghỉ phép",
  },
  {
    code: "NV004",
    name: "Pham Quoc Viet",
    role: "Nhân viên",
    position: "Kỹ thuật viên",
    specialty: "Chẩn đoán hình ảnh",
    phone: "0901 445 567",
    email: "quocviet@clinic.vn",
    status: "Đang làm",
  },
  {
    code: "NV005",
    name: "Do Lan Phuong",
    role: "Nhân viên",
    position: "Chuyên gia",
    specialty: "Mắt",
    phone: "0901 556 678",
    email: "lanphuong@clinic.vn",
    status: "Đang làm",
  },
];

export const staffSummary = [
  ["Tổng nhân sự", "20"],
  ["Đang làm", "18"],
  ["Nghỉ phép", "02"],
  ["Chuyên khoa", "07"],
];

export const ruleCards = [
  {
    title: "Trực 24/24",
    detail: "Chọn ngày N, hệ thống hiểu ca từ 7h30 ngày N đến 7h30 ngày N+1.",
  },
  {
    title: "Nghỉ bù",
    detail: "Trực T2-T5 nghỉ bù ngày kế tiếp; trực T6/T7 dời sang T3 tuần sau.",
  },
  {
    title: "Khóa ô",
    detail: "Ngày nghỉ bù bị khóa, không thể xếp thông tầm, dịch vụ hoặc chuyên gia.",
  },
];

export const dutyRows = [
  ["27/05", "Thứ 2", "Minh Anh, Duc Huy", "28/05", "Hợp lệ"],
  ["28/05", "Thứ 3", "Bao Chau", "29/05", "Hợp lệ"],
  ["30/05", "Thứ 5", "Quoc Viet", "31/05", "Hợp lệ"],
  ["31/05", "Thứ 6", "Lan Phuong", "03/06", "Cần kiểm tra"],
];

export const allDayRows = [
  ["27/05", "Duc Huy", "Đã kiểm tra trực 24/24", "Hợp lệ"],
  ["28/05", "Lan Phuong", "Không trùng nghỉ bù", "Hợp lệ"],
  ["30/05", "Minh Anh", "Không trùng trực 24/24", "Hợp lệ"],
  ["31/05", "Minh Anh", "Trùng trực 24/24", "Chặn lưu"],
];

export const clinicServiceRows = [
  ["27/05", "Bao Chau", "Nhi", "PK dịch vụ 01", "Hợp lệ"],
  ["28/05", "Quoc Viet", "Chẩn đoán hình ảnh", "PK dịch vụ 02", "Hợp lệ"],
  ["30/05", "Lan Phuong", "Mắt", "PK dịch vụ 03", "Hợp lệ"],
  ["31/05", "Bao Chau", "Nhi", "PK dịch vụ 01", "Cảnh báo chuyên gia"],
];

export const expertClinicRows = [
  ["27/05", "Lan Phuong", "Mắt", "Khám chuyên sâu", "Hợp lệ"],
  ["29/05", "Bao Chau", "Nhi", "Khám chuyên gia", "Cần đối chiếu"],
  ["30/05", "Duc Huy", "Ngoại", "Hội chẩn", "Hợp lệ"],
  ["31/05", "Tran Minh Khoa", "Răng hàm mặt", "Khám chuyên sâu", "Chờ phân công"],
];

export const autoSchedulingPreview = [
  ["Bước 1", "Đọc 20 nhân sự đang hoạt động", "Hoàn tất"],
  ["Bước 2", "Loại nhân sự nghỉ phép / ngoại lệ", "Hoàn tất"],
  ["Bước 3", "Round Robin trực 24/24", "Đang chạy"],
  ["Bước 4", "Greedy cho thông tầm và phòng khám", "Chờ"],
  ["Bước 5", "Quét ràng buộc toàn tháng", "Chờ"],
];

export const exceptionStaff = [
  ["Le Bao Chau", "Nghỉ phép", "27-29/05"],
  ["Tran Minh Khoa", "Không tham gia trực 24/24", "Cả tháng"],
  ["Do Lan Phuong", "Chỉ nhận lịch chuyên gia", "Thứ 3, Thứ 5"],
];
