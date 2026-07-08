package com.hospital.scheduler.algorithm.scoring;

import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests cho {@link StaffShiftTypeEligibility}.
 *
 * <p>Đảm bảo logic eligibility được áp dụng đúng cho từng shift type:
 * <ul>
 *   <li>L01/L02/L03 chỉ gán cho Ngoại / Nội (CORE specialties)</li>
 *   <li>L04 với specialty: chỉ staff cùng chuyên khoa</li>
 *   <li>L04 không có specialty: mặc định tất cả chuyên khoa eligible</li>
 * </ul>
 */
class StaffShiftTypeEligibilityTest {

    private static Specialty specialty(int id, String name) {
        return Specialty.builder().id(id).name(name).isActive(true).build();
    }

    private static Staff staff(int id, Specialty sp, Boolean isActive) {
        return Staff.builder()
            .id(id)
            .username("user" + id)
            .fullName("Staff " + id)
            .specialty(sp)
            .isActive(isActive)
            .staffRoles(new HashSet<>())
            .build();
    }

    @Test
    void doctor_isEligible_for_L01() {
        Staff doc = staff(1, specialty(1, "Ngoại"), true);
        assertThat(StaffShiftTypeEligibility.isEligible(doc, "L01", null)).isTrue();
    }

    @Test
    void nurse_isEligible_for_L01() {
        Staff nur = staff(2, specialty(2, "Nội"), true);
        assertThat(StaffShiftTypeEligibility.isEligible(nur, "L01", null)).isTrue();
    }

    @Test
    void pharmacist_isIneligible_for_L01() {
        Staff pha = staff(3, specialty(3, "Dược sĩ"), true);
        assertThat(StaffShiftTypeEligibility.isEligible(pha, "L01", null)).isFalse();
    }

    @Test
    void technician_isIneligible_for_L02() {
        Staff tech = staff(4, specialty(4, "Kỹ thuật viên"), true);
        assertThat(StaffShiftTypeEligibility.isEligible(tech, "L02", null)).isFalse();
    }

    @Test
    void pharmacist_isIneligible_for_L03() {
        Staff pha = staff(3, specialty(3, "Dược sĩ"), true);
        assertThat(StaffShiftTypeEligibility.isEligible(pha, "L03", null)).isFalse();
    }

    @Test
    void L04_withSpecialty_requiresExactSpecialty() {
        Staff doc = staff(1, specialty(1, "Ngoại"), true);
        Staff nur = staff(2, specialty(2, "Nội"), true);
        // L04 cho Ngoại
        assertThat(StaffShiftTypeEligibility.isEligible(doc, "L04", 1)).isTrue();
        assertThat(StaffShiftTypeEligibility.isEligible(nur, "L04", 1)).isFalse();
        // L04 cho Nội
        assertThat(StaffShiftTypeEligibility.isEligible(doc, "L04", 2)).isFalse();
        assertThat(StaffShiftTypeEligibility.isEligible(nur, "L04", 2)).isTrue();
    }

    @Test
    void L04_withoutSpecialty_acceptsDoctorOrNurse() {
        Staff doc = staff(1, specialty(1, "Ngoại"), true);
        Staff nur = staff(2, specialty(2, "Nội"), true);
        Staff pha = staff(3, specialty(3, "Dược sĩ"), true);
        assertThat(StaffShiftTypeEligibility.isEligible(doc, "L04", null)).isTrue();
        assertThat(StaffShiftTypeEligibility.isEligible(nur, "L04", null)).isTrue();
        assertThat(StaffShiftTypeEligibility.isEligible(pha, "L04", null)).isFalse();
    }

    @Test
    void inactiveStaff_neverEligible() {
        Staff doc = staff(1, specialty(1, "Ngoại"), false);
        assertThat(StaffShiftTypeEligibility.isEligible(doc, "L01", null)).isFalse();
        assertThat(StaffShiftTypeEligibility.isEligible(doc, "L04", 1)).isFalse();
    }

    @Test
    void staffWithoutSpecialty_isIneligible() {
        Staff generic = staff(5, null, true);
        assertThat(StaffShiftTypeEligibility.isEligible(generic, "L01", null)).isFalse();
        assertThat(StaffShiftTypeEligibility.isEligible(generic, "L04", null)).isFalse();
    }

    @Test
    void unknownShiftType_isIneligible() {
        Staff doc = staff(1, specialty(1, "Ngoại"), true);
        assertThat(StaffShiftTypeEligibility.isEligible(doc, "L99", null)).isFalse();
    }

    @Test
    void nullStaff_orNullShiftType_isIneligible() {
        assertThat(StaffShiftTypeEligibility.isEligible(null, "L01", null)).isFalse();
        Staff doc = staff(1, specialty(1, "Ngoại"), true);
        assertThat(StaffShiftTypeEligibility.isEligible(doc, null, null)).isFalse();
    }

    @Test
    void filterEligible_returnsOnlyEligible() {
        Staff doc = staff(1, specialty(1, "Ngoại"), true);
        Staff nur = staff(2, specialty(2, "Nội"), true);
        Staff pha = staff(3, specialty(3, "Dược sĩ"), true);
        Staff tech = staff(4, specialty(4, "Kỹ thuật viên"), true);
        List<Staff> pool = List.of(doc, nur, pha, tech);
        List<Staff> eligible = StaffShiftTypeEligibility.filterEligible(pool, "L01", null);
        assertThat(eligible).extracting(Staff::getId).containsExactly(1, 2);
    }

    @Test
    void eligibleStaffIdsForNonL04_excludesIneligibleSpecialties() {
        Staff doc = staff(1, specialty(1, "Ngoại"), true);
        Staff nur = staff(2, specialty(2, "Nội"), true);
        Staff pha = staff(3, specialty(3, "Dược sĩ"), true);
        Staff tech = staff(4, specialty(4, "Kỹ thuật viên"), true);
        Staff inactive = staff(5, specialty(1, "Ngoại"), false);
        List<Staff> pool = List.of(doc, nur, pha, tech, inactive);
        Set<Integer> ids = StaffShiftTypeEligibility.eligibleStaffIdsForNonL04(pool);
        assertThat(ids).containsExactlyInAnyOrder(1, 2);
    }

    @Test
    void getL04EligibilityBySpecialty_groupsCorrectly() {
        Staff doc1 = staff(1, specialty(1, "Ngoại"), true);
        Staff doc2 = staff(2, specialty(1, "Ngoại"), true);
        Staff nur1 = staff(3, specialty(2, "Nội"), true);
        Staff pha = staff(4, specialty(3, "Dược sĩ"), true);
        List<Staff> pool = List.of(doc1, doc2, nur1, pha);
        Map<Integer, Set<Integer>> map = StaffShiftTypeEligibility.getL04EligibilityBySpecialty(pool);
        assertThat(map.get(1)).containsExactlyInAnyOrder(1, 2);
        assertThat(map.get(2)).containsExactly(3);
        assertThat(map.size()).isEqualTo(2);  // Dược sĩ không eligible cho L04 (không thuộc ALL_ELIGIBLE)
    }
}