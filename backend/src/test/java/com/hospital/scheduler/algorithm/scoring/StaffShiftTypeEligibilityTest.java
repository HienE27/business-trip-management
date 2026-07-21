package com.hospital.scheduler.algorithm.scoring;

import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.entity.Staff;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests cho {@link StaffShiftTypeEligibility}.
 *
 * <p>Business rules (theo SPEC.md):
 * <ul>
 *   <li>L01/L02/L03: Mọi staff active thuộc 6 khoa (Ngoại, Nội, Sản, Nhi, Mắt, Răng) đều eligible</li>
 *   <li>L04 với specialty: chỉ staff cùng chuyên khoa mới eligible</li>
 *   <li>L04 không có specialty: mọi staff eligible cho L04 (6 khoa)</li>
 *   <li>Staff inactive: không bao giờ eligible</li>
 * </ul>
 *
 * <p><b>Regression test:</b> Đảm bảo 6 khoa (Ngoại, Nội, Sản, Nhi, Mắt, Răng)
 * đều eligible cho L01/L02/L03. Việc thêm/không thêm khoa mới không ảnh hưởng nghiệp vụ.
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

    // ── Regression test: 6 core specialties eligible for L01/L02/L03 ────────

    @Nested
    @DisplayName("Regression: All 6 core specialties eligible for L01/L02/L03")
    class AllSixSpecialtiesEligibleRegression {

        private final Specialty spNgoai = specialty(1, "Ngoại");
        private final Specialty spNoi = specialty(2, "Nội");
        private final Specialty spSan = specialty(3, "Sản");
        private final Specialty spNhi = specialty(4, "Nhi");
        private final Specialty spMat = specialty(5, "Mắt");
        private final Specialty spRang = specialty(6, "Răng");

        @Test
        @DisplayName("L01: all 6 specialties eligible (Ngoại, Nội, Sản, Nhi, Mắt, Răng)")
        void allSixSpecialtiesEligibleForL01() {
            for (Specialty sp : List.of(spNgoai, spNoi, spSan, spNhi, spMat, spRang)) {
                Staff s = staff(sp.getId(), sp, true);
                assertThat(StaffShiftTypeEligibility.isEligible(s, "L01", null))
                    .as("Staff with specialty '%s' should be eligible for L01", sp.getName())
                    .isTrue();
            }
        }

        @Test
        @DisplayName("L02: all 6 specialties eligible")
        void allSixSpecialtiesEligibleForL02() {
            for (Specialty sp : List.of(spNgoai, spNoi, spSan, spNhi, spMat, spRang)) {
                Staff s = staff(sp.getId(), sp, true);
                assertThat(StaffShiftTypeEligibility.isEligible(s, "L02", null))
                    .as("Staff with specialty '%s' should be eligible for L02", sp.getName())
                    .isTrue();
            }
        }

        @Test
        @DisplayName("L03: all 6 specialties eligible")
        void allSixSpecialtiesEligibleForL03() {
            for (Specialty sp : List.of(spNgoai, spNoi, spSan, spNhi, spMat, spRang)) {
                Staff s = staff(sp.getId(), sp, true);
                assertThat(StaffShiftTypeEligibility.isEligible(s, "L03", null))
                    .as("Staff with specialty '%s' should be eligible for L03", sp.getName())
                    .isTrue();
            }
        }

        @Test
        @DisplayName("Unknown specialty NOT eligible for L01/L02/L03")
        void unknownSpecialtyNotEligibleForNonL04() {
            Specialty unknown = specialty(99, "Tim mạch");
            Staff s = staff(99, unknown, true);
            assertThat(StaffShiftTypeEligibility.isEligible(s, "L01", null)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(s, "L02", null)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(s, "L03", null)).isFalse();
        }

        @Test
        @DisplayName("L04 with specialty requires exact specialty match")
        void L04_withSpecialty_requiresExactMatch() {
            Staff ngoai = staff(1, spNgoai, true);
            Staff noi = staff(2, spNoi, true);
            Staff san = staff(3, spSan, true);

            // L04 for Ngoại specialty (id=1)
            assertThat(StaffShiftTypeEligibility.isEligible(ngoai, "L04", 1)).isTrue();
            assertThat(StaffShiftTypeEligibility.isEligible(noi, "L04", 1)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(san, "L04", 1)).isFalse();

            // L04 for Nội specialty (id=2)
            assertThat(StaffShiftTypeEligibility.isEligible(ngoai, "L04", 2)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(noi, "L04", 2)).isTrue();
            assertThat(StaffShiftTypeEligibility.isEligible(san, "L04", 2)).isFalse();
        }

        @Test
        @DisplayName("L04 without specialty: all 6 core specialties eligible")
        void L04_withoutSpecialty_allSixEligible() {
            for (Specialty sp : List.of(spNgoai, spNoi, spSan, spNhi, spMat, spRang)) {
                Staff s = staff(sp.getId(), sp, true);
                assertThat(StaffShiftTypeEligibility.isEligible(s, "L04", null))
                    .as("Staff with specialty '%s' should be eligible for L04 (no specialty req)", sp.getName())
                    .isTrue();
            }
        }

        @Test
        @DisplayName("L04: unknown specialty NOT eligible (not in ALL_ELIGIBLE_SPECIALTIES)")
        void L04_unknownSpecialtyNotEligible() {
            Specialty unknown = specialty(99, "Tim mạch");
            Staff s = staff(99, unknown, true);
            assertThat(StaffShiftTypeEligibility.isEligible(s, "L04", null)).isFalse();
        }
    }

    // ── Edge cases ──────────────────────────────────────────────────────────

    @Nested
    @DisplayName("Edge cases: inactive, null specialty, unknown shift type")
    class EdgeCases {

        @Test
        @DisplayName("Inactive staff never eligible for any shift type")
        void inactiveStaff_neverEligible() {
            Staff inactive = staff(1, specialty(1, "Ngoại"), false);
            assertThat(StaffShiftTypeEligibility.isEligible(inactive, "L01", null)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(inactive, "L02", null)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(inactive, "L03", null)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(inactive, "L04", 1)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(inactive, "L04", null)).isFalse();
        }

        @Test
        @DisplayName("Staff without specialty is ineligible for all shift types")
        void staffWithoutSpecialty_isIneligible() {
            Staff generic = staff(5, null, true);
            assertThat(StaffShiftTypeEligibility.isEligible(generic, "L01", null)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(generic, "L02", null)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(generic, "L03", null)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(generic, "L04", null)).isFalse();
        }

        @Test
        @DisplayName("Unknown shift type is ineligible")
        void unknownShiftType_isIneligible() {
            Staff doc = staff(1, specialty(1, "Ngoại"), true);
            assertThat(StaffShiftTypeEligibility.isEligible(doc, "L99", null)).isFalse();
        }

        @Test
        @DisplayName("Null staff or null shift type is ineligible")
        void nullStaff_orNullShiftType_isIneligible() {
            assertThat(StaffShiftTypeEligibility.isEligible(null, "L01", null)).isFalse();
            Staff doc = staff(1, specialty(1, "Ngoại"), true);
            assertThat(StaffShiftTypeEligibility.isEligible(doc, null, null)).isFalse();
        }
    }

    // ── Batch/filter operations ──────────────────────────────────────────────

    @Nested
    @DisplayName("Batch operations: filterEligible, eligibleStaffIdsForNonL04, getL04EligibilityBySpecialty")
    class BatchOperations {

        @Test
        @DisplayName("filterEligible for L01 returns only staff from 6 core specialties")
        void filterEligible_returnsOnlyEligible() {
            Staff ngoai = staff(1, specialty(1, "Ngoại"), true);
            Staff noi = staff(2, specialty(2, "Nội"), true);
            Staff san = staff(3, specialty(3, "Sản"), true);
            Staff nhi = staff(4, specialty(4, "Nhi"), true);
            Staff mat = staff(5, specialty(5, "Mắt"), true);
            Staff rang = staff(6, specialty(6, "Răng"), true);
            Staff unknown = staff(99, specialty(99, "Tim mạch"), true);

            List<Staff> pool = List.of(ngoai, noi, san, nhi, mat, rang, unknown);
            List<Staff> eligible = StaffShiftTypeEligibility.filterEligible(pool, "L01", null);

            assertThat(eligible).extracting(Staff::getId)
                .containsExactlyInAnyOrder(1, 2, 3, 4, 5, 6);
            assertThat(eligible).doesNotContain(unknown);
        }

        @Test
        @DisplayName("eligibleStaffIdsForNonL04 returns IDs of all 6 core specialties")
        void eligibleStaffIdsForNonL04_excludesUnknownSpecialties() {
            Staff ngoai = staff(1, specialty(1, "Ngoại"), true);
            Staff noi = staff(2, specialty(2, "Nội"), true);
            Staff unknown = staff(99, specialty(99, "Tim mạch"), true);
            Staff inactive = staff(5, specialty(5, "Mắt"), false);

            List<Staff> pool = List.of(ngoai, noi, unknown, inactive);
            Set<Integer> ids = StaffShiftTypeEligibility.eligibleStaffIdsForNonL04(pool);

            assertThat(ids).containsExactlyInAnyOrder(1, 2);
        }

        @Test
        @DisplayName("getL04EligibilityBySpecialty groups staff by specialty correctly")
        void getL04EligibilityBySpecialty_groupsCorrectly() {
            Staff ngoai1 = staff(1, specialty(1, "Ngoại"), true);
            Staff ngoai2 = staff(2, specialty(1, "Ngoại"), true);
            Staff noi1 = staff(3, specialty(2, "Nội"), true);
            Staff unknown = staff(99, specialty(99, "Tim mạch"), true);

            List<Staff> pool = List.of(ngoai1, ngoai2, noi1, unknown);
            Map<Integer, Set<Integer>> map = StaffShiftTypeEligibility.getL04EligibilityBySpecialty(pool);

            assertThat(map.get(1)).containsExactlyInAnyOrder(1, 2);
            assertThat(map.get(2)).containsExactly(3);
            // Unknown specialty NOT included (not in ALL_ELIGIBLE_SPECIALTIES)
            assertThat(map).doesNotContainKey(99);
        }

        @Test
        @DisplayName("Sanity: ALL_ELIGIBLE_SPECIALTIES contains the expected 6 core specialties")
        void sanity_allEligibleSpecialties_containsSixCore() {
            Set<String> expected = Set.of("Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng");
            assertThat(StaffShiftTypeEligibility.ALL_ELIGIBLE_SPECIALTIES)
                .as("ALL_ELIGIBLE_SPECIALTIES must contain exactly 6 core specialties")
                .containsExactlyInAnyOrderElementsOf(expected);
        }
    }

    // ── Dynamic specialty provider ─────────────────────────────────────────────

    @Nested
    @DisplayName("Dynamic specialty provider: setSpecialtyProvider / getAllEligibleSpecialtyNames")
    class DynamicSpecialtyProvider {

        @AfterEach
        void tearDown() {
            // Always reset to default after each test
            StaffShiftTypeEligibility.resetToDefaultProvider();
        }

        @Test
        @DisplayName("getAllEligibleSpecialtyNames returns 6 core specialties by default")
        void default_returnsSixCoreSpecialties() {
            assertThat(StaffShiftTypeEligibility.getAllEligibleSpecialtyNames())
                .containsExactlyInAnyOrder("Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng");
        }

        @Test
        @DisplayName("setSpecialtyProvider: new specialty added to eligibility pool")
        void newSpecialtyBecomesEligible() {
            // Simulate hospital adds "Tim mạch" as a new eligible specialty
            StaffShiftTypeEligibility.setSpecialtyProvider(() -> Set.of(
                "Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng", "Tim mạch"
            ));

            Staff timMach = staff(1, specialty(99, "Tim mạch"), true);
            Staff noi = staff(2, specialty(2, "Nội"), true);

            assertThat(StaffShiftTypeEligibility.isEligible(timMach, "L01", null)).isTrue();
            assertThat(StaffShiftTypeEligibility.isEligible(noi, "L01", null)).isTrue();
        }

        @Test
        @DisplayName("setSpecialtyProvider: removing specialty from pool makes staff ineligible")
        void removingSpecialtyMakesIneligible() {
            // Simulate "Răng" no longer eligible (e.g., department closed)
            StaffShiftTypeEligibility.setSpecialtyProvider(() -> Set.of(
                "Ngoại", "Nội", "Sản", "Nhi", "Mắt"
            ));

            Staff rang = staff(1, specialty(6, "Răng"), true);
            Staff ngoai = staff(2, specialty(1, "Ngoại"), true);

            assertThat(StaffShiftTypeEligibility.isEligible(rang, "L01", null)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(ngoai, "L01", null)).isTrue();
        }

        @Test
        @DisplayName("setSpecialtyProvider: only eligible specialties affect eligibility")
        void onlyEligibleSpecialtiesMatter() {
            StaffShiftTypeEligibility.setSpecialtyProvider(() -> Set.of("Ngoại", "Nội"));

            Staff ngoai = staff(1, specialty(1, "Ngoại"), true);
            Staff san = staff(2, specialty(3, "Sản"), true);
            Staff noi = staff(3, specialty(2, "Nội"), true);

            assertThat(StaffShiftTypeEligibility.isEligible(ngoai, "L01", null)).isTrue();
            assertThat(StaffShiftTypeEligibility.isEligible(san, "L01", null)).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligible(noi, "L01", null)).isTrue();
        }

        @Test
        @DisplayName("isEligibleSpecialty respects dynamic provider")
        void isEligibleSpecialty_dynamic() {
            StaffShiftTypeEligibility.setSpecialtyProvider(() -> Set.of("Ngoại", "Tim mạch"));

            assertThat(StaffShiftTypeEligibility.isEligibleSpecialty("Ngoại")).isTrue();
            assertThat(StaffShiftTypeEligibility.isEligibleSpecialty("Tim mạch")).isTrue();
            assertThat(StaffShiftTypeEligibility.isEligibleSpecialty("Nội")).isFalse();
            assertThat(StaffShiftTypeEligibility.isEligibleSpecialty(null)).isFalse();
        }

        @Test
        @DisplayName("resetToDefaultProvider restores 6 core specialties")
        void resetToDefaultProvider() {
            StaffShiftTypeEligibility.setSpecialtyProvider(() -> Set.of("Chỉ một"));

            StaffShiftTypeEligibility.resetToDefaultProvider();

            assertThat(StaffShiftTypeEligibility.getAllEligibleSpecialtyNames())
                .containsExactlyInAnyOrder("Ngoại", "Nội", "Sản", "Nhi", "Mắt", "Răng");
        }
    }
}
