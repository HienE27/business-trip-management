package com.hospital.scheduler.algorithm.scoring;

import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Xác định nhân sự <b>eligible</b> (đủ điều kiện) cho từng loại ca.
 *
 * <p>Trước đây {@link ScheduleQualityScorer} tính fairness trên tất cả
 * {@code activeStaff} — điều này gây CV cao giả vì nhân sự không cùng
 * chuyên khoa (ví dụ Dược sĩ/KTV không thể đảm nhận L01/L02/L03)
 * luôn có 0 ca và bị tính vào variance.
 *
 * <p>Utility này định nghĩa eligibility tập trung, dùng được cho cả:
 * <ul>
 *   <li>{@link ScheduleQualityScorer} (tính fairness trên eligible pool)</li>
 *   <li>{@code AutoSchedulingService.filterAndSortEligibleStaffBatch}
 *       (lọc staff ineligible ngay từ đầu, tránh phân bổ cho staff
 *       không đủ điều kiện khiến maxShifts bị "lãng phí")</li>
 * </ul>
 *
 * <h3>Quy tắc eligibility</h3>
 * <pre>
 *   L01 (Trực 24/24)     → Ngoại, Nội
 *   L02 (Thông tầm)      → Ngoại, Nội
 *   L03 (PK Dịch vụ)     → Ngoại, Nội
 *   L04 (PK Chuyên gia)  → Tất cả (Ngoại, Nội, Sản, Nhi, Mắt, Răng)
 *                          hoặc chỉ các specialty được cấu hình trong
 *                          AUTO_GEN_L04_ALLOWED_SPECIALTIES
 * </pre>
 */
public final class StaffShiftTypeEligibility {

    private StaffShiftTypeEligibility() {}

    /**
     * Các chuyên khoa CỐT LÕI được phép gán ca trực/thông tầm/phòng khám.
     * Đây là baseline - L01/L02/L03 yêu cầu staff thuộc các specialty này.
     */
    public static final Set<String> CORE_ELIGIBLE_SPECIALTIES = Set.of(
        "Ngoại",
        "Nội"
    );

    /**
     * Tất cả các chuyên khoa có thể gán ca L04 (PK Chuyên gia).
     * Bao gồm cả core + extended specialties.
     */
    public static final Set<String> ALL_ELIGIBLE_SPECIALTIES = Set.of(
        "Ngoại",
        "Nội",
        "Sản",
        "Nhi",
        "Mắt",
        "Răng"
    );

    /**
     * Backward compatibility - map cũ sang sets mới.
     * @deprecated Use {@link #CORE_ELIGIBLE_SPECIALTIES} instead.
     */
    @Deprecated
    public static final Set<String> ELIGIBLE_SPECIALTY_NAMES = CORE_ELIGIBLE_SPECIALTIES;

    /**
     * Kiểm tra staff có đủ điều kiện cho 1 shift type hay không.
     *
     * @param staff                Staff cần kiểm tra (null → false)
     * @param shiftTypeId          Loại ca (L01/L02/L03/L04)
     * @param requiredSpecialtyId  Specialty yêu cầu (chỉ áp dụng cho L04, có thể null)
     * @return true nếu staff có thể gán shift type này
     */
    public static boolean isEligible(Staff staff, String shiftTypeId, Integer requiredSpecialtyId) {
        return isEligible(staff, shiftTypeId, requiredSpecialtyId, null);
    }

    /**
     * Kiểm tra staff có đủ điều kiện cho 1 shift type hay không.
     *
     * @param staff                Staff cần kiểm tra (null → false)
     * @param shiftTypeId          Loại ca (L01/L02/L03/L04)
     * @param requiredSpecialtyId  Specialty yêu cầu (chỉ áp dụng cho L04, có thể null)
     * @param l04AllowedSpecialties Danh sách specialties được phép gán L04 (null/empty = tất cả)
     * @return true nếu staff có thể gán shift type này
     */
    public static boolean isEligible(Staff staff, String shiftTypeId, Integer requiredSpecialtyId, 
                                     java.util.List<String> l04AllowedSpecialties) {
        if (staff == null || shiftTypeId == null) return false;
        if (!Boolean.TRUE.equals(staff.getIsActive())) return false;

        Specialty sp = staff.getSpecialty();
        String spName = sp != null ? sp.getName() : null;

        switch (shiftTypeId) {
            case "L01":
            case "L02":
            case "L03":
                // L01/L02/L03: allowed specialties được truyền vào qua l04AllowedSpecialties param
                // (tên param lịch sử, hiện áp dụng cho cả 4 loại ca).
                // null/empty → fallback về CORE_ELIGIBLE_SPECIALTIES (giữ behavior mặc định).
                Set<String> coreAllowed = l04AllowedSpecialties != null && !l04AllowedSpecialties.isEmpty()
                    ? new java.util.HashSet<>(l04AllowedSpecialties)
                    : CORE_ELIGIBLE_SPECIALTIES;
                return spName != null && coreAllowed.contains(spName);

            case "L04":
                // L04: Kiểm tra với danh sách allowed specialties từ config
                // Nếu allowedSpecialties rỗng/null → tất cả đều được
                Set<String> allowed = l04AllowedSpecialties != null && !l04AllowedSpecialties.isEmpty()
                    ? new java.util.HashSet<>(l04AllowedSpecialties)
                    : ALL_ELIGIBLE_SPECIALTIES;

                if (spName != null && allowed.contains(spName)) {
                    // Nếu có requiredSpecialtyId, phải khớp
                    if (requiredSpecialtyId != null) {
                        return sp != null && requiredSpecialtyId.equals(sp.getId());
                    }
                    return true;
                }
                return false;

            default:
                return false;
        }
    }

    /**
     * Lọc danh sách nhân sự theo eligibility cho 1 shift type.
     *
     * @param staffList            Pool nhân sự nguồn (có thể chứa ineligible staff)
     * @param shiftTypeId          Loại ca
     * @param requiredSpecialtyId  Specialty yêu cầu (chỉ áp dụng cho L04, có thể null)
     * @return Subset chỉ chứa staff eligible (giữ thứ tự input)
     */
    public static List<Staff> filterEligible(
            List<Staff> staffList,
            String shiftTypeId,
            Integer requiredSpecialtyId) {
        if (staffList == null || staffList.isEmpty()) return List.of();
        return staffList.stream()
            .filter(s -> isEligible(s, shiftTypeId, requiredSpecialtyId))
            .collect(Collectors.toList());
    }

    /**
     * Lấy set staff IDs eligible cho L01/L02/L03 (không cần specialty).
     *
     * @return Set các staffId thuộc Bác sĩ / Điều dưỡng và active.
     */
    public static Set<Integer> eligibleStaffIdsForNonL04(List<Staff> staffList) {
        if (staffList == null) return Set.of();
        return staffList.stream()
            .filter(s -> s != null && Boolean.TRUE.equals(s.getIsActive())
                && s.getSpecialty() != null
                && CORE_ELIGIBLE_SPECIALTIES.contains(s.getSpecialty().getName()))
            .map(Staff::getId)
            .collect(Collectors.toSet());
    }

    /**
     * Lấy set staff IDs eligible cho L04 (bao gồm KTV có specialty khớp).
     *
     * @return Set các staffId thuộc Bác sĩ / Điều dưỡng / KTV và active.
     */
    public static Set<Integer> eligibleStaffIdsForL04(List<Staff> staffList) {
        if (staffList == null) return Set.of();
        return staffList.stream()
            .filter(s -> s != null && Boolean.TRUE.equals(s.getIsActive())
                && s.getSpecialty() != null
                && ALL_ELIGIBLE_SPECIALTIES.contains(s.getSpecialty().getName()))
            .map(Staff::getId)
            .collect(Collectors.toSet());
    }

    /**
     * Lấy L04 eligibility per specialty.
     * Trả về Map&lt;specialtyId, Set&lt;staffId&gt;&gt;.
     * <p>
     * BAO GỒM: Tất cả specialties trong ALL_ELIGIBLE_SPECIALTIES.
     */
    public static Map<Integer, Set<Integer>> getL04EligibilityBySpecialty(List<Staff> staffList) {
        if (staffList == null) return Map.of();
        Map<Integer, Set<Integer>> result = new HashMap<>();
        for (Staff s : staffList) {
            if (s == null || !Boolean.TRUE.equals(s.getIsActive()) || s.getSpecialty() == null) continue;
            // L04 gán cho tất cả eligible specialties
            if (!ALL_ELIGIBLE_SPECIALTIES.contains(s.getSpecialty().getName())) continue;
            result
                .computeIfAbsent(s.getSpecialty().getId(), k -> new HashSet<>())
                .add(s.getId());
        }
        return result;
    }

    /**
     * Lấy L04 eligibility per specialty với config động.
     * Chỉ bao gồm các specialties trong danh sách allowed.
     */
    public static Map<Integer, Set<Integer>> getL04EligibilityBySpecialty(List<Staff> staffList, 
                                                                         java.util.List<String> allowedSpecialties) {
        if (staffList == null) return Map.of();
        Set<String> allowed = allowedSpecialties != null && !allowedSpecialties.isEmpty()
            ? new HashSet<>(allowedSpecialties)
            : ALL_ELIGIBLE_SPECIALTIES;

        Map<Integer, Set<Integer>> result = new HashMap<>();
        for (Staff s : staffList) {
            if (s == null || !Boolean.TRUE.equals(s.getIsActive()) || s.getSpecialty() == null) continue;
            if (!allowed.contains(s.getSpecialty().getName())) continue;
            result
                .computeIfAbsent(s.getSpecialty().getId(), k -> new HashSet<>())
                .add(s.getId());
        }
        return result;
    }

    /**
     * Lấy tất cả eligible staff IDs (cho L01-L04).
     * @return Set các staffId thuộc ALL_ELIGIBLE_SPECIALTIES và active.
     */
    public static Set<Integer> getAllEligibleStaffIds(List<Staff> staffList) {
        if (staffList == null) return Set.of();
        return staffList.stream()
            .filter(s -> s != null && Boolean.TRUE.equals(s.getIsActive())
                && s.getSpecialty() != null
                && ALL_ELIGIBLE_SPECIALTIES.contains(s.getSpecialty().getName()))
            .map(Staff::getId)
            .collect(Collectors.toSet());
    }

    /**
     * Kiểm tra staff có eligible cho BẤT KỲ ca nào không.
     * @return true nếu staff thuộc ALL_ELIGIBLE_SPECIALTIES.
     */
    public static boolean isEligibleForAnyShift(Staff staff) {
        if (staff == null) return false;
        if (!Boolean.TRUE.equals(staff.getIsActive())) return false;
        String spName = staff.getSpecialty() != null ? staff.getSpecialty().getName() : null;
        return spName != null && ALL_ELIGIBLE_SPECIALTIES.contains(spName);
    }
}