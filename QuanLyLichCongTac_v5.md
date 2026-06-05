<div align="center">

# TÀI LIỆU MÔ TẢ CHỨC NĂNG
## WEBSITE QUẢN LÝ LỊCH CÔNG TÁC

</div>

| Thuộc tính | Chi tiết |
| :--- | :--- |
| **Phiên bản** | 1.1 |
| **Ngày lập** | 05/2026 |
| **Người hướng dẫn** | ThS. Văn Minh Hoàng Quân |
| **Nhóm thực hiện** | Nhóm 4 |
| **Công nghệ đề xuất** | Web App |

---

## 1. TỔNG QUAN HỆ THỐNG

### 1.1. Mục tiêu
Xây dựng website quản lý lịch công tác cho phòng gồm 20 nhân sự.
Hệ thống hỗ trợ xếp lịch 4 loại, kiểm tra xung đột tự động và tự động phân công lịch theo thuật toán.

### 1.2. Các loại lịch trong hệ thống

| Mã | Tên chức năng | Mô tả chi tiết | Ưu tiên |
| :--- | :--- | :--- | :--- |
| **L01** | Lịch trực 24/24 | Nhân sự trực liên tục từ 7h30 ngày N đến 7h30 ngày N+1. Sau ngày trực, nhân sự được nghỉ bù ngày kế tiếp. Trường hợp trực Thứ 6 hoặc Thứ 7 thì nghỉ bù vào tuần sau, nhưng không được nghỉ bù vào Thứ 2 hoặc Thứ 6 của tuần sau. | Cốt lõi |
| **L02** | Lịch thông tầm | Nhân sự làm ca liên tục không nghỉ trưa trong ngày được chọn. Chỉ cần chọn ngày, không cần chọn giờ. | Cốt lõi |
| **L03** | Lịch phòng khám dịch vụ | Nhân sự phụ trách ca khám dịch vụ trong ngày được chọn. Chỉ cần chọn ngày, không cần chọn giờ. | Cốt lõi |
| **L04** | Lịch phòng khám chuyên gia | Chuyên gia phụ trách ca khám chuyên sâu trong ngày được chọn. Chỉ cần chọn ngày, không cần chọn giờ. | Cốt lõi |

### 1.3. Quy tắc chọn ngày

| Loại lịch | Quy tắc áp dụng |
| :--- | :--- |
| **Quy tắc nhập lịch chung cho toàn hệ thống** | Tất cả 4 loại lịch đều chỉ yêu cầu chọn NGÀY, không cần nhập giờ hoặc chọn ca. |
| **Lịch trực 24/24** | chọn ngày N => hệ thống tự hiểu ca trực từ 7h30 ngày N đến 7h30 ngày N+1. |
| **Lịch thông tầm, phòng khám dịch vụ, phòng khám chuyên gia** | chọn ngày N => ghi nhận lịch làm việc trong ngày N. |

### 1.4. Quy định nghỉ bù sau trực 24/24

| Quy tắc tính ngày nghỉ bù sau ca trực 24/24 |
| :--- |
| **QUY TẮC CHUNG:** Nhân sự sau khi trực 24/24 được nghỉ bù vào ngày kế tiếp (ngày N+1). |
| **TRƯỜNG HỢP ĐẶC BIỆT:** Nếu ngày trực (ngày N) rơi vào Thứ 6 hoặc Thứ 7, ngày nghỉ bù được dời sang tuần sau. |
| **NGOẠI LỆ của trường hợp đặc biệt:** Ngày nghỉ bù được dời sang tuần sau KHÔNG được rơi vào Thứ 2 hoặc Thứ 6 của tuần sau đó. |
| Hệ thống tự động tính và hiển thị ngày nghỉ bù khi quản lý xếp lịch trực. Ngày nghỉ bù được đánh dấu trên bảng lịch và không thể xếp lịch khác cho nhân sự đó. |

**Bảng tổng hợp quy tắc nghỉ bù**

| Ngày Trực | Ngày Nghỉ Bù Tương Ứng |
| :--- | :--- |
| Trực Thứ 2 | Nghỉ bù Thứ 3 (tuần này) |
| Trực Thứ 3 | Nghỉ bù Thứ 4 (tuần này) |
| Trực Thứ 4 | Nghỉ bù Thứ 5 (tuần này) |
| Trực Thứ 5 | Nghỉ bù Thứ 6 (tuần này) |
| Trực Thứ 6 | Nghỉ bù tuần sau, bỏ qua Thứ 2 và Thứ 6 => Nghỉ bù Thứ 3 tuần sau |
| Trực Thứ 7 | Nghỉ bù tuần sau, bỏ qua Thứ 2 và Thứ 6 => Nghỉ bù Thứ 3 tuần sau |
| Trực Chủ Nhật | Nghỉ bù Thứ 2 (tuần sau / ngay hôm sau) |

### 1.4. Ràng buộc nghiệp vụ cốt lõi

| Ràng buộc | Mô tả chi tiết |
| :--- | :--- |
| **Lịch trực 24/24 và Lịch thông tầm** | - Cùng một nhân sự, trong cùng một ngày: KHÔNG được đồng thời có lịch trực 24/24 và lịch thông tầm.<br>- Lý do: lịch trực 24/24 đã chiếm toàn bộ thời gian trong ngày (7h30 hôm đó đến 7h30 hôm sau).<br>- Hệ thống phải kiểm tra và từ chối lưu khi phát hiện xung đột, cả thủ công lẫn tự động. |
| **Ngày nghỉ bù sau trực 24/24** | - Cùng một nhân sự: KHÔNG được xếp bất kỳ loại lịch nào (thông tầm, phòng khám dịch vụ, phòng khám chuyên gia) vào ngày nghỉ bù của họ.<br>- Ngày nghỉ bù được hệ thống tự động tính khi xếp lịch trực 24/24 và hiển thị khoá trên bảng lịch tháng.<br>- Hệ thống kiểm tra trong bước kiểm tra xung đột hàng loạt của tất cả 3 module lịch còn lại (M03, M04, M05). |
| **Lịch phòng khám dịch vụ và Lịch phòng khám chuyên gia** | - Cùng một nhân sự, trong cùng một ngày: KHÔNG được đồng thời có lịch phòng khám dịch vụ và lịch phòng khám chuyên gia.<br>- Hệ thống phải kiểm tra và từ chối lưu khi phát hiện xung đột, cả thủ công lẫn tự động.<br>- Logic kiểm tra ràng buộc phải được tách thành hàm/service dùng chung cho toàn hệ thống. |

---

## 2. MÔ TẢ CHỨC NĂNG CHI TIẾT

### Module M01 — Quản lý nhân sự
Quản lý thông tin 20 nhân sự trong phòng, phân quyền hệ thống và duy trì danh sách nhân sự đang hoạt động.

**M01 — Danh sách chức năng**

| Mã | Tên chức năng | Mô tả chi tiết | Ưu tiên |
| :--- | :--- | :--- | :--- |
| **M01-F01** | Thêm nhân sự | Nhập: họ tên, mã nhân viên, chức vụ, chuyên khoa, SĐT, email. Hệ thống kiểm tra trùng mã NV trước khi lưu. | Trung bình |
| **M01-F02** | Sửa thông tin nhân sự | Chỉnh sửa thông tin nhân sự; lưu lịch sử thay đổi để tra cứu. | Trung bình |
| **M01-F03** | Ngừng hoạt động | Đánh dấu nhân sự nghỉ việc (soft delete); không xoá cứng để bảo toàn dữ liệu lịch sử. | Trung bình |
| **M01-F04** | Tìm kiếm & lọc | Tìm theo tên, mã NV, chức vụ, chuyên khoa, trạng thái (đang làm / nghỉ phép / nghỉ việc). | Trung bình |
| **M01-F05** | Phân quyền hệ thống | 3 vai trò: Quản lý lịch (toàn quyền), Trưởng phòng (xem + phê duyệt), Nhân viên (xem lịch cá nhân). | Trung bình |

**M01-F01 — Luồng xử lý: Thêm nhân sự**

| Bước | Tên bước | Mô tả xử lý | Kết quả / Output |
| :--- | :--- | :--- | :--- |
| **B1** | Mở form thêm nhân sự | Quản lý nhấn nút Thêm nhân sự; hệ thống hiển thị form nhập liệu trống. | Form nhập liệu hiển thị |
| **B2** | Nhập thông tin | Người dùng điền: họ tên, mã NV, chức vụ, chuyên khoa, SĐT, email, ngày vào làm. | Dữ liệu nhập trên form |
| **B3** | Kiểm tra validate | Hệ thống kiểm tra: bắt buộc điền đủ trường, mã NV không trùng, email đúng định dạng. | Thông báo lỗi nếu sai |
| **B4** | Lưu vào CSDL | Lưu bản ghi nhân sự mới với trạng thái Đang làm việc; ghi nhật ký thao tác. | Bản ghi nhân sự được tạo |
| **B5** | Thông báo kết quả | Hiển thị thông báo Thêm thành công; cập nhật danh sách nhân sự. | Danh sách cập nhật |

---

### Module M02 — Lịch trực 24/24
Quản lý xếp lịch trực 24/24 cho cả tháng trong một lần thao tác.
Ca trực từ 7h30 ngày N đến 7h30 ngày N+1.
Sau mỗi ngày trực, nhân sự được hệ thống tự động tính và ghi nhận ngày nghỉ bù theo quy định (xem mục 1.4).
Ràng buộc: không trùng lịch thông tầm cùng ngày.

**M02 — Danh sách chức năng**

| Mã | Tên chức năng | Mô tả chi tiết | Ưu tiên |
| :--- | :--- | :--- | :--- |
| **M02-F01** | Xếp lịch trực 24/24 theo tháng | Quản lý chọn tháng và gán ngày trực cho từng nhân sự trên bảng lịch tháng. Hệ thống tự hiểu ca trực từ 7h30 ngày đó đến 7h30 ngày hôm sau. Có thể gán nhiều nhân sự cho cùng một ngày. | Cao |
| **M02-F02** | Kiểm tra xung đột hàng loạt | Sau khi quản lý hoàn tất xếp lịch tháng, hệ thống quét toàn bộ, phát hiện tất cả ngày có nhân sự vừa trực 24/24 vừa có lịch thông tầm. Hiển thị danh sách lỗi tổng hợp. | Cao |
| **M02-F03** | Chỉnh sửa lịch trong tháng | Quản lý sửa từng ô ngày trên bảng lịch tháng (đổi người, xoá ngày trực). Hệ thống kiểm tra ràng buộc ngay khi sửa từng ô. | Cao |
| **M02-F04** | Đăng ký đổi ngày trực | Sau khi lịch tháng đã công bố: nhân viên gửi yêu cầu đổi ngày trực; quản lý duyệt hoặc từ chối; hệ thống kiểm tra cho cả 2 nhân sự liên quan. | Trung bình |
| **M02-F05** | Thống kê số ngày trực | Báo cáo tổng số ngày trực của từng nhân sự trong tháng; cảnh báo nếu phân bổ lệch lớn. | Trung bình |
| **M02-F06** | Tự động tính ngày nghỉ bù | Sau khi quản lý gán ngày trực, hệ thống tự tính ngày nghỉ bù theo quy định: trực T6/T7 thì nghỉ bù tuần sau nhưng không vào T2 hoặc T6. Hiển thị và khoá ngày nghỉ bù trên bảng lịch. | Cao |
| **M02-F07** | Cảnh báo lịch trùng ngày nghỉ bù | Trong bước kiểm tra xung đột hàng loạt, hệ thống phát hiện nếu bất kỳ loại lịch nào (thông tầm, dịch vụ, chuyên gia) bị xếp trùng vào ngày nghỉ bù của nhân sự. Ô lỗi tô đỏ và ngăn lưu. | Cao |

**M02-F01 — Luồng xử lý: Xếp lịch trực 24/24 theo tháng**

| Bước | Tên bước | Mô tả xử lý | Kết quả / Output |
| :--- | :--- | :--- | :--- |
| **B1** | Chọn tháng xếp lịch | Quản lý vào màn hình Xếp lịch trực, chọn tháng/năm. Hệ thống hiển thị bảng lịch tháng dạng lưới (hàng = ngày, cột = nhân sự), tải danh sách nhân sự đang hoạt động. | Bảng lịch tháng hiển thị |
| **B2** | Đánh dấu ngày nghỉ / ngoại lệ | Quản lý đánh dấu ngày nghỉ phép, nghỉ lễ, ngoại lệ của từng nhân sự. Những ô này bị khoá, không thể xếp lịch trực. | Ngày ngoại lệ được ghi nhận |
| **B3** | Gán lịch trực cho từng ngày | Quản lý nhấn vào từng ô ngày và tick chọn nhân sự trực ngày đó. Hệ thống hiển thị nhãn 7h30 ngày N -> 7h30 ngày N+1 trên ô đã gán. | Ngày trực được gán |
| **B4** | Tự động tính ngày nghỉ bù | Ngay sau khi gán mỗi ngày trực, hệ thống tự tính ngày nghỉ bù theo quy định: (1) Trực T2-T5: nghỉ bù ngày hôm sau; (2) Trực T6 hoặc T7: dời sang tuần sau, bỏ qua T2 và T6, gán vào T3 tuần sau; (3) Trực CN: nghỉ bù T2 hôm sau. Ô nghỉ bù tự động bị khoá màu xám trên bảng, không thể xếp lịch khác cho nhân sự đó. | Ngày nghỉ bù được tính và hiển thị trên bảng |
| **B5** | Kiểm tra xung đột hàng loạt | Quản lý nhấn Kiểm tra xung đột. Hệ thống quét toàn bộ: (1) Vi phạm: cùng nhân sự cùng ngày vừa trực 24/24 vừa thông tầm; (2) Lịch khác bị xếp đè lên ngày nghỉ bù. | Kết quả kiểm tra toàn tháng |
| **B6a** | Có xung đột / lỗi | Hệ thống hiển thị bảng lỗi tổng hợp: loại lỗi, ngày lỗi, tên nhân sự. Ô lỗi tô đỏ. Quản lý phải sửa hết trước khi lưu. | Danh sách lỗi, chưa lưu |
| **B6b** | Không có xung đột | Toàn bộ lịch hợp lệ. Hệ thống hiển thị bản tóm tắt: số ngày trực, số ngày nghỉ bù của từng nhân sự trong tháng. | Xác nhận hợp lệ, bản tóm tắt hiển thị |
| **B7** | Lưu & công bố lịch tháng | Quản lý xem tóm tắt và nhấn Lưu & Công bố. Hệ thống lưu lịch trực và ngày nghỉ bù vào CSDL, ghi nhật ký, gửi thông báo đến từng nhân viên (kèm danh sách ngày trực và ngày nghỉ bù của họ). | Lịch tháng được lưu, thông báo toàn phòng |

**M02-F06 — Luồng xử lý: Tự động tính ngày nghỉ bù**

| Bước | Tên bước | Mô tả xử lý | Kết quả / Output |
| :--- | :--- | :--- | :--- |
| **B1** | Nhận ngày trực đầu vào | Hệ thống nhận ngày trực N vừa được quản lý gán cho nhân sự X. | Ngày N và thứ trong tuần xác định |
| **B2** | Xác định thứ trong tuần | Hệ thống tính thứ của ngày N: Thứ 2, 3, 4, 5, 6, 7 hay Chủ Nhật. | Thứ của ngày N |
| **B3a** | Trực T2, T3, T4, T5 | Ngày nghỉ bù = N+1 (ngày kế tiếp trong tuần đó). | Nghỉ bù ngày N+1 |
| **B3b** | Trực T6 hoặc T7 | Bù sang tuần sau. Tính từ T2 tuần sau, duyệt qua: bỏ T2 (không được nghỉ), bỏ T6 (không được nghỉ). Ngày nghỉ bù = T3 của tuần sau. | Nghỉ bù T3 tuần sau |
| **B3c** | Trực Chủ Nhật | Ngày nghỉ bù = T2 ngay hôm sau (đầu tuần kế tiếp). | Nghỉ bù T2 hôm sau |
| **B4** | Kiểm tra ngày nghỉ bù | Kiểm tra ngày nghỉ bù vừa tính có trùng ngày nghỉ lễ hoặc đã bị khoá không. Nếu có: cộng thêm 1 ngày, lặp lại bỏ qua T2 và T6 cho đến khi tìm được ngày hợp lệ. | Ngày nghỉ bù hợp lệ |
| **B5** | Ghi nhận và khoá ô | Hệ thống ghi ngày nghỉ bù vào CSDL tạm (bản nháp), tô màu xám ô ngày đó cho nhân sự X trên bảng lịch. Ô bị khoá, không thể xếp bất kỳ lịch nào cho nhân sự X vào ngày đó. | Ô nghỉ bù bị khoá trên bảng lịch |

**M02-F04 — Luồng xử lý: Đổi ngày trực (sau khi đã công bố lịch)**

| Bước | Tên bước | Mô tả xử lý | Kết quả / Output |
| :--- | :--- | :--- | :--- |
| **B1** | Nhân viên gửi yêu cầu | Nhân viên chọn ngày trực muốn đổi trong lịch tháng, nhập lý do, chọn người muốn đổi cùng (nếu có). | Yêu cầu đổi được tạo |
| **B2** | Thông báo quản lý | Hệ thống gửi thông báo cho quản lý; hiển thị trên dashboard duyệt yêu cầu. | Quản lý nhận thông báo |
| **B3** | Kiểm tra ràng buộc | Hệ thống mô phỏng lịch sau khi đổi: kiểm tra cả 2 nhân sự liên quan không vi phạm ở ngày mới. | Kết quả kiểm tra |
| **B4a** | Phê duyệt | Quản lý duyệt: hệ thống cập nhật lịch tháng, thông báo cả 2 nhân viên. | Lịch tháng được cập nhật |
| **B4b** | Từ chối | Quản lý từ chối kèm lý do; thông báo cho nhân viên yêu cầu. | Yêu cầu bị từ chối |

---

### Module M03 — Lịch thông tầm
Quản lý xếp lịch thông tầm cho cả tháng trong một lần thao tác.
Nhân sự làm ca liên tục không nghỉ trưa trong ngày được gán.
Quản lý chỉ cần chọn ngày, không chọn giờ. Ràng buộc: không trùng lịch trực 24/24 cùng ngày.

**M03 — Danh sách chức năng**

| Mã | Tên chức năng | Mô tả chi tiết | Ưu tiên |
| :--- | :--- | :--- | :--- |
| **M03-F01** | Tạo lịch thông tầm | Chọn nhân sự và ngày làm thông tầm. Hệ thống ghi nhận lịch thông tầm cho ngày đó. Có thể thêm ghi chú. | Cao |
| **M03-F02** | Kiểm tra xung đột lịch trực 24/24 | Tự động ngăn lưu nếu nhân sự đã có lịch trực 24/24 trong cùng ngày được chọn. | Cao |
| **M03-F03** | Sửa / huỷ lịch thông tầm | Chỉnh sửa ngày hoặc huỷ lịch thông tầm đã xếp; ghi nhật ký thao tác. | Cao |
| **M03-F04** | Xem lịch theo tuần / tháng | Hiển thị dạng bảng lịch; màu phân biệt lịch thông tầm với các loại lịch khác; bộ lọc theo nhân sự. | Trung bình |

**M03-F01 — Luồng xử lý: Xếp lịch thông tầm theo tháng**

| Bước | Tên bước | Mô tả xử lý | Kết quả / Output |
| :--- | :--- | :--- | :--- |
| **B1** | Chọn tháng xếp lịch | Quản lý vào màn hình Xếp lịch thông tầm, chọn tháng/năm. Hệ thống hiển thị bảng lịch tháng, tải danh sách nhân sự và lịch trực 24/24 + ngày nghỉ bù tháng đó (nếu đã có) để quản lý dễ tránh xung đột. | Bảng lịch tháng, dữ liệu trực 24/24 và nghỉ bù hiển thị |
| **B2** | Gán lịch thông tầm cho từng ngày | Quản lý nhấn vào từng ô ngày và chọn nhân sự làm thông tầm ngày đó. Ô ngày đã có lịch trực 24/24 hoặc ngày nghỉ bù của cùng nhân sự được tô màu cảnh báo để quản lý dễ nhận biết. | Bảng lịch tháng dần được điền |
| **B3** | Kiểm tra xung đột hàng loạt | Quản lý nhấn Kiểm tra xung đột. Hệ thống quét toàn bộ bảng lịch tháng, phát hiện: (1) Vi phạm: nhân sự vừa có thông tầm vừa có trực 24/24 cùng ngày; (2) Lịch thông tầm xếp trùng ngày nghỉ bù của nhân sự. | Kết quả kiểm tra toàn tháng |
| **B4a** | Có xung đột | Hiển thị bảng lỗi tổng hợp: loại lỗi, ngày bị xung đột, tên nhân sự. Ô lỗi tô đỏ. Quản lý phải sửa hết trước khi lưu. | Danh sách lỗi, chưa lưu |
| **B4b** | Không có xung đột | Toàn bộ lịch hợp lệ, hiển thị nút Lưu & Công bố. | Xác nhận hợp lệ |
| **B5** | Lưu & công bố lịch tháng | Quản lý nhấn Lưu & Công bố. Hệ thống lưu vào CSDL, ghi nhật ký, gửi thông báo đến từng nhân viên. | Lịch tháng được lưu và thông báo |

---

### Module M04 — Lịch phòng khám dịch vụ
Quản lý xếp lịch phòng khám dịch vụ cho cả tháng trong một lần thao tác.
Nhân sự phụ trách ca khám dịch vụ trong ngày được gán. Quản lý chỉ cần chọn ngày.
Ràng buộc: không trùng lịch phòng khám chuyên gia cùng ngày.

**M04 — Danh sách chức năng**

| Mã | Tên chức năng | Mô tả chi tiết | Ưu tiên |
| :--- | :--- | :--- | :--- |
| **M04-F01** | Tạo lịch phòng khám dịch vụ | Chọn nhân sự và ngày phụ trách phòng khám dịch vụ. Hệ thống kiểm tra ràng buộc trước khi lưu. Có thể thêm ghi chú. | Cao |
| **M04-F02** | Kiểm tra xung đột lịch chuyên gia | Tự động ngăn lưu nếu nhân sự đã có lịch phòng khám chuyên gia trong cùng ngày được chọn. | Cao |
| **M04-F03** | Sửa / huỷ lịch dịch vụ | Chỉnh sửa ngày hoặc huỷ lịch phòng khám dịch vụ đã xếp; ghi lý do và nhật ký. | Cao |
| **M04-F04** | Xem lịch theo tuần / tháng | Hiển thị lịch phòng khám dịch vụ dạng bảng lịch; màu phân biệt; bộ lọc theo nhân sự. | Trung bình |
| **M04-F05** | Thống kê ca khám dịch vụ | Báo cáo số ngày trực ca khám dịch vụ theo tuần/tháng của từng nhân sự. | Thấp |

**M04-F01 — Luồng xử lý: Xếp lịch phòng khám dịch vụ theo tháng**

| Bước | Tên bước | Mô tả xử lý | Kết quả / Output |
| :--- | :--- | :--- | :--- |
| **B1** | Chọn tháng xếp lịch | Quản lý vào màn hình Xếp lịch phòng khám dịch vụ, chọn tháng/năm. Hệ thống tải lịch phòng khám chuyên gia và ngày nghỉ bù tháng đó (nếu đã có) để hỗ trợ tránh xung đột. | Bảng lịch tháng, dữ liệu chuyên gia và nghỉ bù hiển thị |
| **B2** | Gán nhân sự cho từng ngày | Quản lý chọn nhân sự phụ trách ca khám dịch vụ cho từng ngày. Ô ngày đã có lịch chuyên gia hoặc ngày nghỉ bù của cùng nhân sự được tô màu cảnh báo. | Bảng lịch tháng dần được điền |
| **B3** | Kiểm tra xung đột hàng loạt | Quản lý nhấn Kiểm tra xung đột. Hệ thống quét toàn bộ bảng, phát hiện: (1) Vi phạm: nhân sự vừa có lịch dịch vụ vừa có lịch chuyên gia cùng ngày; (2) Lịch dịch vụ xếp trùng ngày nghỉ bù của nhân sự. | Kết quả kiểm tra toàn tháng |
| **B4a** | Có xung đột | Hiển thị bảng lỗi tổng hợp: loại lỗi, ngày bị xung đột, tên nhân sự. Ô lỗi tô đỏ. Quản lý sửa trước khi lưu. | Danh sách lỗi, chưa lưu |
| **B4b** | Không có xung đột | Toàn bộ lịch hợp lệ, hiển thị nút Lưu & Công bố. | Xác nhận hợp lệ |
| **B5** | Lưu & công bố lịch tháng | Quản lý nhấn Lưu & Công bố. Hệ thống lưu vào CSDL, ghi nhật ký, gửi thông báo đến nhân viên phụ trách. | Lịch tháng được lưu và thông báo |

---

### Module M05 — Lịch phòng khám chuyên gia
Quản lý xếp lịch phòng khám chuyên gia cho cả tháng trong một lần thao tác.
Chuyên gia phụ trách ca khám chuyên sâu trong ngày được gán. Quản lý chỉ cần chọn ngày.
Ràng buộc: không trùng lịch phòng khám dịch vụ cùng ngày.

**M05 — Danh sách chức năng**

| Mã | Tên chức năng | Mô tả chi tiết | Ưu tiên |
| :--- | :--- | :--- | :--- |
| **M05-F01** | Tạo lịch phòng khám chuyên gia | Chọn chuyên gia và ngày phụ trách phòng khám chuyên gia. Hệ thống kiểm tra ràng buộc trước khi lưu. Có thể thêm ghi chú. | Cao |
| **M05-F02** | Kiểm tra xung đột lịch dịch vụ | Tự động ngăn lưu nếu chuyên gia đã có lịch phòng khám dịch vụ trong cùng ngày được chọn. | Cao |
| **M05-F03** | Sửa / huỷ lịch chuyên gia | Chỉnh sửa ngày hoặc huỷ lịch phòng khám chuyên gia đã xếp; ghi lý do và nhật ký. | Cao |
| **M05-F04** | Lọc lịch theo chuyên khoa | Xem lịch theo từng chuyên khoa: Ngoại, Nội, Sản, Nhi, Mắt, Răng, v.v. | Trung bình |
| **M05-F05** | Thống kê ca khám chuyên gia | Báo cáo số ngày trực ca khám chuyên gia theo tuần/tháng của từng chuyên gia. | Thấp |

**M05-F01 — Luồng xử lý: Xếp lịch phòng khám chuyên gia theo tháng**

| Bước | Tên bước | Mô tả xử lý | Kết quả / Output |
| :--- | :--- | :--- | :--- |
| **B1** | Chọn tháng và chuyên khoa | Quản lý chọn tháng/năm và chuyên khoa cần xếp lịch. Hệ thống lọc danh sách chuyên gia theo chuyên khoa, tải lịch phòng khám dịch vụ và ngày nghỉ bù tháng đó để hỗ trợ tránh xung đột. | Bảng lịch tháng, danh sách chuyên gia, dữ liệu dịch vụ và nghỉ bù hiển thị |
| **B2** | Gán chuyên gia cho từng ngày | Quản lý chọn chuyên gia phụ trách từng ngày. Ô ngày đã có lịch dịch vụ hoặc ngày nghỉ bù của cùng chuyên gia được tô màu cảnh báo. | Bảng lịch tháng dần được điền |
| **B3** | Kiểm tra xung đột hàng loạt | Quản lý nhấn Kiểm tra xung đột. Hệ thống quét toàn bộ bảng, phát hiện: (1) Vi phạm: chuyên gia vừa có lịch chuyên gia vừa có lịch dịch vụ cùng ngày; (2) Lịch chuyên gia xếp trùng ngày nghỉ bù của chuyên gia. | Kết quả kiểm tra toàn tháng |
| **B4a** | Có xung đột | Hiển thị bảng lỗi tổng hợp: loại lỗi, ngày bị xung đột, tên chuyên gia. Ô lỗi tô đỏ. Quản lý sửa trước khi lưu. | Danh sách lỗi, chưa lưu |
| **B4b** | Không có xung đột | Toàn bộ lịch hợp lệ, hiển thị nút Lưu & Công bố. | Xác nhận hợp lệ |
| **B5** | Lưu & công bố lịch tháng | Quản lý nhấn Lưu & Công bố. Hệ thống lưu vào CSDL, ghi nhật ký, gửi thông báo đến từng chuyên gia. | Lịch tháng được lưu và thông báo |

---

### Module M06 — Tổng hợp & Hiển thị lịch
Dashboard lịch công tác toàn phòng. Tổng hợp 4 loại lịch theo ngày/tuần/tháng, cảnh báo xung đột, xuất báo cáo và lưu nhật ký thao tác.

**M06 — Danh sách chức năng**

| Mã | Tên chức năng | Mô tả chi tiết | Ưu tiên |
| :--- | :--- | :--- | :--- |
| **M06-F01** | Xem lịch theo ngày / tuần / tháng | Lưới lịch hiển thị 4 loại lịch với màu phân biệt. Mỗi ô hiển thị tên nhân sự. Nhấn vào ô để xem chi tiết hoặc chỉnh sửa nhanh. | Cao |
| **M06-F02** | Xem lịch theo nhân sự | Chọn 1 nhân sự để xem toàn bộ lịch của người đó trong khoảng thời gian tuỳ chọn. | Cao |
| **M06-F03** | Cảnh báo xung đột thời gian thực | Thông báo tức thời (badge đỏ + tuỳ chọn email) khi phát hiện vi phạm ràng buộc. | Cao |
| **M06-F04** | Xuất báo cáo lịch | Xuất file Excel / PDF lịch công tác theo tháng; theo từng loại lịch hoặc toàn phòng. | Trung bình |
| **M06-F05** | Nhật ký thao tác | Ghi lại toàn bộ hành động: ai tạo/sửa/xoá lịch nào, loại lịch gì, vào lúc mấy giờ. | Thấp |

**M06-F01 — Luồng xử lý: Xem lịch tổng hợp tháng**

| Bước | Tên bước | Mô tả xử lý | Kết quả / Output |
| :--- | :--- | :--- | :--- |
| **B1** | Truy cập dashboard lịch | Người dùng đăng nhập, vào trang Lịch công tác. Hệ thống tải lịch mặc định theo tháng hiện tại (chế độ xem tháng là mặc định vì quản lý làm việc theo tháng). | Dashboard lịch tháng hiển thị |
| **B2** | Chọn tháng / chế độ xem | Chọn tháng/năm cần xem. Chọn thêm chế độ: Toàn phòng (xem tất cả nhân sự) hoặc Theo nhân sự (chọn 1 người). Lọc thêm theo loại lịch nếu cần. | Bộ lọc được áp dụng |
| **B3** | Tải dữ liệu lịch tháng | Hệ thống truy vấn CSDL, lấy toàn bộ 4 loại lịch của tháng được chọn cho toàn phòng hoặc nhân sự cụ thể. | Dữ liệu lịch tháng trả về |
| **B4** | Render bảng lịch tháng | Vẽ bảng lịch tháng dạng lưới (hàng = ngày, cột = nhân sự). Mỗi loại lịch có màu riêng. Ngày nào chưa xếp đủ lịch được đánh dấu để nhắc nhở. | Bảng lịch tháng hiển thị trực quan |
| **B5** | Xem chi tiết hoặc điều chỉnh | Nhấn vào ô lịch: xem chi tiết và cho phép chỉnh sửa nhanh. Nhấn nút Chỉnh sửa lịch tháng: mở lại màn hình xếp lịch tương ứng với tháng đó. | Chi tiết lịch hoặc màn hình chỉnh sửa |

---

### Module M07 — Tự động sắp xếp lịch
Module tự động phân công lịch cho toàn bộ nhân sự theo thuật toán, đảm bảo đầy đủ ràng buộc nghiệp vụ và phân bổ công bằng.
Đầu vào và đầu ra đều theo đơn vị NGÀY, không theo giờ.

**M07 — Danh sách chức năng**

| Mã | Tên chức năng | Mô tả chi tiết | Ưu tiên |
| :--- | :--- | :--- | :--- |
| **M07-F01** | Cấu hình tham số đầu vào | Nhập: tháng cần xếp lịch, danh sách nhân sự ngoại lệ (nghỉ phép, không tham gia tự động). Hệ thống tự phân bổ đều số ngày cho 20 nhân sự, không giới hạn cố định. | Cao |
| **M07-F02** | Tự động xếp lịch trực 24/24 | Hệ thống tự chọn ngày và phân công nhân sự trực 24/24. Đảm bảo: số ngày trực đều nhau, luân phiên công bằng, không vi phạm ràng buộc. | Cao |
| **M07-F03** | Tự động xếp lịch thông tầm | Hệ thống tự chọn ngày và phân công nhân sự làm thông tầm. Không vi phạm ràng buộc. Tuân thủ giới hạn số ngày thông tầm/tháng đã cấu hình. | Cao |
| **M07-F04** | Tự động xếp lịch phòng khám dịch vụ | Tự phân công nhân sự phụ trách phòng khám dịch vụ theo từng ngày. Không vi phạm ràng buộc. | Cao |
| **M07-F05** | Tự động xếp lịch phòng khám chuyên gia | Tự gán chuyên gia phù hợp chuyên khoa vào từng ngày. Không vi phạm ràng buộc. | Cao |
| **M07-F06** | Báo cáo ngày chưa phân công được | Liệt kê các ngày chưa đủ nhân sự hợp lệ để phân công; quản lý xử lý thủ công phần còn lại. | Cao |
| **M07-F07** | Xem trước lịch trước khi xác nhận | Hiển thị bản nháp toàn bộ lịch đã sắp xếp. Quản lý có thể chỉnh sửa thủ công từng ngày trước khi bấm Xác nhận & Áp dụng. | Cao |
| **M07-F08** | Sắp xếp lại khi có thay đổi đột xuất | Khi nhân sự xin nghỉ đột xuất, hệ thống tự đề xuất người thay thế hợp lệ (không xung đột, đúng chuyên khoa). | Trung bình |
| **M07-F09** | Thống kê cân bằng tải | Biểu đồ số ngày trực / số ngày làm của từng nhân sự trong tháng để quản lý xem xét mức độ phân bổ. | Trung bình |
| **M07-F10** | Lưu & tái sử dụng mẫu lịch | Lưu cấu hình lịch thành template để dùng lại tháng sau; chỉnh sửa trước khi áp dụng. | Thấp |

**M07 — Luồng xử lý tổng thể: Tự động sắp xếp lịch**

| Bước | Tên bước | Mô tả xử lý | Kết quả / Output |
| :--- | :--- | :--- | :--- |
| **B1** | Cấu hình tham số | Quản lý chọn tháng cần xếp lịch và đánh dấu nhân sự ngoại lệ (nghỉ phép, không tham gia tự động). Hệ thống sẽ phân bổ đều số ngày cho 20 nhân sự. | Tham số được lưu |
| **B2** | Đọc dữ liệu hiện có | Hệ thống đọc: danh sách nhân sự đang hoạt động, lịch nghỉ phép đã đăng ký, lịch đã có sẵn (nếu có). | Dữ liệu đầu vào sẵn sàng |
| **B3** | Chạy thuật toán | Thực thi thuật toán theo thứ tự ưu tiên: trực 24/24 -> thông tầm -> phòng khám dịch vụ -> phòng khám chuyên gia. Mỗi bước kiểm tra ràng buộc trước khi gán. | Bản phân công nháp được tạo |
| **B4** | Quét toàn bộ ràng buộc | Duyệt lại toàn bộ bản phân công: kiểm tra (trực 24/24 vs thông tầm) và (dịch vụ vs chuyên gia). Lập danh sách vi phạm nếu có. | Danh sách vi phạm (nếu có) |
| **B5** | Hiển thị bản nháp | Hiển thị lịch dạng bảng theo tháng. Đánh dấu ngày chưa phân công được. Quản lý chỉnh sửa thủ công nếu cần. | Bản nháp trên giao diện |
| **B6** | Quản lý xác nhận | Quản lý kiểm tra bản nháp và nhấn Xác nhận & Áp dụng. | Lệnh xác nhận |
| **B7** | Lưu chính thức & thông báo | Ghi toàn bộ lịch vào CSDL; gửi thông báo đến từng nhân viên; ghi nhật ký. | Lịch được áp dụng, nhân viên được thông báo |

| Thông tin về Thuật Toán | Ghi chú & Đề xuất |
| :--- | :--- |
| **Gợi ý thuật toán cho sinh viên** | - Round Robin: Luân phiên xoay vòng theo thứ tự danh sách nhân sự. Dễ nhất, đảm bảo phân bổ đều.<br>- Greedy: Mỗi ngày chọn nhân sự có ít ngày công nhất và không vi phạm ràng buộc. Phù hợp nhóm trung bình.<br>- Backtracking: Thử từng phương án, quay lui nếu vi phạm ràng buộc. Chính xác hơn, phù hợp nhóm khá.<br>- Lưu ý: Logic kiểm tra ràng buộc phải được tách thành hàm/service riêng để dùng chung cho cả thủ công và tự động |