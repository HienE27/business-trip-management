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
 * <p>Theo tài liệu nghiệp vụ ({@code QuanLyLichCongTac_v5.md}), L01/L02/L03/L04
 * là 4 loại <b>ca trực</b>, phân biệt bởi thời gian/ca và chế độ nghỉ —
 * <b>không phải bởi chuyên khoa</b>. Bất kỳ nhân sự active thuộc
 * chuyên khoa nào trong hệ thống đều có thể được xếp bất kỳ loại ca nào,
 * miễn tuân thủ ràng buộc nghiệp vụ (L01↔L02, L03↔L04, nghỉ bù).
 *
 * <p>Điểm khác biệt duy nhất giữa các loại ca:
 * <ul>
 *   <li><b>L01/L02/L03</b>: Không có ràng buộc chuyên khoa — bất kỳ staff
 *       eligible nào đều được phép.</li>
 *   <li><b>L04 (PK Chuyên gia)</b>: Yêu cầu staff cùng chuyên khoa
 *       với ShiftRequirement (nếu requirement có specialty). Cross-specialty
 *       đã bị gỡ — L04 luôn strict-specialty.</li>
 * </ul>
 *
 * <p>Đây là baseline eligibility — chỉ kiểm tra active + có specialty.
 * Các ràng buộc nghiệp vụ (conflict, nghỉ bù, max shifts) được kiểm
 * tra riêng tại {@link com.hospital.scheduler.service.ConflictDetectionService}
 * và các constraint trong package {@code scheduling.constraint}.
 *
 * <h3>Dynamic specialty list</h3>
 * Danh sách eligible specialties được đọc động từ
 * {@link com.hospital.scheduler.service.HospitalSpecialtyRegistry}.
 * Khi bệnh viện thêm khoa mới, chỉ cần evict cache — không cần sửa code này.
 *
 * @see StaffEligibilityFilter
 * @see com.hospital.scheduler.service.HospitalSpecialtyRegistry
 * @see com.hospital.scheduler.service.ConflictDetectionService
 */
public final class StaffShiftTypeEligibility {

    private StaffShiftTypeEligibility() {}

    /**
     * Cung cấp danh sách tên specialties eligible cho L01/L02/L03/L04.
     *
     * <p>Default implementation: 6 khoa core (Ngoại, Nội, Sản, Nhi, Mắt, Răng).
     * Khi {@link HospitalSpecialtyRegistry} được khởi tạo, nó thay thế bằng
     * danh sách động từ database.
     *
     * <p>Đặt là {@code volatile} để đảm bảo visibility across threads
     * trong multi-threaded scheduling engine.
     */
    @FunctionalInterface
    public interface SpecialtyProvider {
        java.util.Set<String> getEligibleSpecialtyNames();
    }

    /**
     * Default provider: 6 khoa core.
     * Đảm bảo eligibility hoạt động ngay cả khi HospitalSpecialtyRegistry chưa được inject.
     */
    private static final SpecialtyProvider DEFAULT_PROVIDER = () -> Set.of(
        "Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng"
    );

    private static volatile SpecialtyProvider specialtyProvider = DEFAULT_PROVIDER;

    /**
     * Inject provider động (gọi bởi {@link HospitalSpecialtyRegistry} khi khởi tạo).
     * Sau khi gọi, mọi static method tự động dùng danh sách từ database.
     *
     * @param provider provider trả về Set<String> specialty names
     */
    public static void setSpecialtyProvider(SpecialtyProvider provider) {
        if (provider == null) {
            specialtyProvider = DEFAULT_PROVIDER;
        } else {
            specialtyProvider = provider;
        }
    }

    /**
     * Reset về default (6 khoa core). Hữu ích cho testing.
     */
    public static void resetToDefaultProvider() {
        specialtyProvider = DEFAULT_PROVIDER;
    }

    /**
     * Trả về Set<String> hiện tại của tất cả eligible specialty names.
     *
     * @return immutable view of current eligible specialties
     */
    public static Set<String> getAllEligibleSpecialtyNames() {
        return specialtyProvider.getEligibleSpecialtyNames();
    }

    /**
     * Kiểm tra xem specialty name có trong danh sách eligible không.
     *
     * @param specialtyName tên specialty (null → false)
     * @return true nếu eligible
     */
    public static boolean isEligibleSpecialty(String specialtyName) {
        if (specialtyName == null) return false;
        return specialtyProvider.getEligibleSpecialtyNames().contains(specialtyName);
    }

    // ── Backward-compatible constants ────────────────────────────────────────

    /**
     * Tất cả các chuyên khoa có thể gán ca trong hệ thống (default baseline).
     *
     * <p><b>Deprecated:</b> Dùng {@link #getAllEligibleSpecialtyNames()} thay vì hằng này
     * để nhận danh sách động từ database.
     *
     * @deprecated Use {@link #getAllEligibleSpecialtyNames()} for dynamic list
     */
    @Deprecated
    public static final Set<String> ALL_ELIGIBLE_SPECIALTIES = Set.of(
        "Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng"
    );

    /**
     * Alias cho backward compatibility.
     * @deprecated Use {@link #ALL_ELIGIBLE_SPECIALTIES} instead.
     */
    @Deprecated
    public static final Set<String> CORE_ELIGIBLE_SPECIALTIES = ALL_ELIGIBLE_SPECIALTIES;

    /**
     * Alias cho backward compatibility.
     * @deprecated Use {@link #ALL_ELIGIBLE_SPECIALTIES} instead.
     */
    @Deprecated
    public static final Set<String> ELIGIBLE_SPECIALTY_NAMES = ALL_ELIGIBLE_SPECIALTIES;

    /**
     * Kiểm tra staff có đủ điều kiện cho 1 shift type hay không.
     *
     * <p>Quy tắc:
     * <ul>
     *   <li>L01/L02/L03: Staff active + có specialty ∈ ALL_ELIGIBLE_SPECIALTIES</li>
     *   <li>L04: Staff active + có specialty ∈ ALL_ELIGIBLE_SPECIALTIES
     *       + khớp requiredSpecialtyId (nếu có)</li>
     * </ul>
     *
     * @param staff                Staff cần kiểm tra (null → false)
     * @param shiftTypeId          Loại ca (L01/L02/L03/L04)
     * @param requiredSpecialtyId  Specialty yêu cầu (chỉ áp dụng cho L04, có thể null)
     * @return true nếu staff có thể gán shift type này
     */
    public static boolean isEligible(Staff staff, String shiftTypeId, Integer requiredSpecialtyId) {
        if (staff == null || shiftTypeId == null) return false;
        if (!Boolean.TRUE.equals(staff.getIsActive())) return false;

        Specialty sp = staff.getSpecialty();
        String spName = sp != null ? sp.getName() : null;
        if (spName == null) return false;

        boolean inEligiblePool = isEligibleSpecialty(spName);
        if (!inEligiblePool) return false;

        switch (shiftTypeId) {
            case "L01":
            case "L02":
            case "L03":
                // L01/L02/L03: không ràng buộc chuyên khoa
                return true;

            case "L04":
                // L04: nếu có requiredSpecialtyId, phải khớp
                if (requiredSpecialtyId != null) {
                    return requiredSpecialtyId.equals(sp.getId());
                }
                return true;

            default:
                return false;
        }
    }

    /**
     * Lọc danh sách nhân sự theo eligibility cho 1 shift type.
     *
     * @param staffList            Pool nhân sự nguồn
     * @param shiftTypeId          Loại ca
     * @param requiredSpecialtyId  Specialty yêu cầu (chỉ L04, có thể null)
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
     * Lấy set staff IDs eligible cho L01/L02/L03.
     *
     * @return Set các staffId active thuộc ALL_ELIGIBLE_SPECIALTIES.
     */
    public static Set<Integer> eligibleStaffIdsForNonL04(List<Staff> staffList) {
        if (staffList == null) return Set.of();
        return staffList.stream()
            .filter(s -> s != null && Boolean.TRUE.equals(s.getIsActive())
                && s.getSpecialty() != null
                && isEligibleSpecialty(s.getSpecialty().getName()))
            .map(Staff::getId)
            .collect(Collectors.toSet());
    }

    /**
     * Lấy set staff IDs eligible cho L04.
     *
     * @return Set các staffId active thuộc ALL_ELIGIBLE_SPECIALTIES.
     */
    public static Set<Integer> eligibleStaffIdsForL04(List<Staff> staffList) {
        if (staffList == null) return Set.of();
        return staffList.stream()
            .filter(s -> s != null && Boolean.TRUE.equals(s.getIsActive())
                && s.getSpecialty() != null
                && isEligibleSpecialty(s.getSpecialty().getName()))
            .map(Staff::getId)
            .collect(Collectors.toSet());
    }

    /**
     * Lấy L04 eligibility per specialty.
     *
     * @return Map&lt;specialtyId, Set&lt;staffId&gt;&gt; cho L04.
     */
    public static Map<Integer, Set<Integer>> getL04EligibilityBySpecialty(List<Staff> staffList) {
        if (staffList == null) return Map.of();
        Map<Integer, Set<Integer>> result = new HashMap<>();
        for (Staff s : staffList) {
            if (s == null || !Boolean.TRUE.equals(s.getIsActive()) || s.getSpecialty() == null) continue;
            if (!isEligibleSpecialty(s.getSpecialty().getName())) continue;
            result
                .computeIfAbsent(s.getSpecialty().getId(), k -> new HashSet<>())
                .add(s.getId());
        }
        return result;
    }

    /**
     * Lấy tất cả eligible staff IDs (cho L01-L04).
     *
     * @return Set các staffId thuộc ALL_ELIGIBLE_SPECIALTIES và active.
     */
    public static Set<Integer> getAllEligibleStaffIds(List<Staff> staffList) {
        if (staffList == null) return Set.of();
        return staffList.stream()
            .filter(s -> s != null && Boolean.TRUE.equals(s.getIsActive())
                && s.getSpecialty() != null
                && isEligibleSpecialty(s.getSpecialty().getName()))
            .map(Staff::getId)
            .collect(Collectors.toSet());
    }

    /**
     * Kiểm tra staff có eligible cho BẤT KỲ ca nào không.
     *
     * @return true nếu staff thuộc ALL_ELIGIBLE_SPECIALTIES.
     */
    public static boolean isEligibleForAnyShift(Staff staff) {
        if (staff == null) return false;
        if (!Boolean.TRUE.equals(staff.getIsActive())) return false;
        String spName = staff.getSpecialty() != null ? staff.getSpecialty().getName() : null;
        return spName != null && isEligibleSpecialty(spName);
    }
}