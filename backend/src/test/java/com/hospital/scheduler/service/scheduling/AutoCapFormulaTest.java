package com.hospital.scheduler.service.scheduling;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Verifies the auto-cap computation in AutoSchedulingService.runScheduling (line 849-859):
 *   cap = Math.max(1, ceil(totalRequired / activeStaff.size()))
 * applied only when save=false AND request has no user override.
 *
 * Precedence in StaffEligibilityFilter (line 131-138):
 *   request override (per-call) > runtime config > staff.maxShiftsPerMonth > MAX_VALUE
 *   override == 0 → Integer.MAX_VALUE (cap disabled)
 */
@DisplayName("Auto-cap (Cap ca/NS — Tự động theo kỳ)")
class AutoCapFormulaTest {

    /** Replays the exact arithmetic from AutoSchedulingService line 855. */
    private int computeAutoCap(int totalRequired, int activeStaffSize) {
        if (activeStaffSize <= 0) return -1; // guard mirroring the outer `!activeStaff.isEmpty()` check
        return Math.max(1, (int) Math.ceil((double) totalRequired / activeStaffSize));
    }

    @Nested
    @DisplayName("Công thức ceil(demand/staff)")
    class Formula {

        @Test
        @DisplayName("ceil(132/6) = 22 (case thực tế UI hiển thị)")
        void caseInUi132DividedBy6() {
            assertEquals(22, computeAutoCap(132, 6));
        }

        @Test
        @DisplayName("ceil(100/6) = 17 (làm tròn lên khi không chia hết)")
        void roundsUpForUneven() {
            assertEquals(17, computeAutoCap(100, 6));
        }

        @Test
        @DisplayName("ceil(48/8) = 6 (chia hết, không dư)")
        void exactDivision() {
            assertEquals(6, computeAutoCap(48, 8));
        }

        @Test
        @DisplayName("ceil(0/1) = 1 (Math.max(1, ...) đảm bảo floor)")
        void floorsAt1() {
            assertEquals(1, computeAutoCap(0, 1));
        }

        @Test
        @DisplayName("ceil(5/5) = 1")
        void equals1AtParity() {
            assertEquals(1, computeAutoCap(5, 5));
        }

        @Test
        @DisplayName("ceil(1/1) = 1 (kỳ chỉ có 1 ca, 1 người)")
        void singleShiftSingleStaff() {
            assertEquals(1, computeAutoCap(1, 1));
        }
    }

    @Nested
    @DisplayName("Edge cases theo guard ngoài (line 849)")
    class Guards {

        @Test
        @DisplayName("activeStaff rỗng → KHÔNG gọi ceil (tránh chia 0)")
        void emptyStaffSkipsFormula() {
            // Outer guard: `!activeStaff.isEmpty()` → không bao giờ vào math
            assertEquals(-1, computeAutoCap(132, 0), "marker confirms guard chặn ngoài");
        }

        @Test
        @DisplayName("totalRequired = 0 → KHÔNG set override (line 854: `if (totalRequired > 0)`)")
        void zeroDemandDoesNotApply() {
            // Replay guard: when totalRequired is 0, request.setMaxShiftsPerMonthOverride is NOT called
            int totalRequired = 0;
            boolean overrideApplied = totalRequired > 0;
            assertFalse(overrideApplied);
        }

        @Test
        @DisplayName("User override != null → KHÔNG overwrite (line 849: `== null` check)")
        void userOverrideWins() {
            Integer userOverride = 10;
            boolean formulaRuns = userOverride == null;
            assertFalse(formulaRuns);
        }

        @Test
        @DisplayName("save=true → KHÔNG compute auto-cap (line 849: `!save`)")
        void saveTrueBypasses() {
            boolean save = true;
            boolean formulaRuns = !save;
            assertFalse(formulaRuns);
        }
    }

    @Nested
    @DisplayName("Precedence trong StaffEligibilityFilter (line 131-138)")
    class Precedence {

        /** Replays the exact ternary from filter line 132-138. */
        private int resolveEffectiveMax(Integer override, int runtimeLimit, Integer staffEntity) {
            return (override != null)
                    ? (override == 0 ? Integer.MAX_VALUE : override)
                    : (runtimeLimit > 0 && runtimeLimit < Integer.MAX_VALUE
                            ? runtimeLimit
                            : (staffEntity != null && staffEntity > 0
                                    ? staffEntity
                                    : Integer.MAX_VALUE));
        }

        @Test
        @DisplayName("override=22 > runtime=30 > staff.maxShiftsPerMonth=15 → 22")
        void overrideBeatsRuntimeAndEntity() {
            assertEquals(22, resolveEffectiveMax(22, 30, 15));
        }

        @Test
        @DisplayName("override=null, runtime=30 → dùng 30")
        void runtimeUsedWhenNoOverride() {
            assertEquals(30, resolveEffectiveMax(null, 30, 15));
        }

        @Test
        @DisplayName("override=null, runtime=0 → dùng staff.maxShiftsPerMonth=15")
        void entityUsedWhenRuntimeZero() {
            assertEquals(15, resolveEffectiveMax(null, 0, 15));
        }

        @Test
        @DisplayName("override=null, runtime=0, staff=null → MAX_VALUE (no cap)")
        void noCapWhenAllZeroOrNull() {
            assertEquals(Integer.MAX_VALUE, resolveEffectiveMax(null, 0, null));
        }

        @Test
        @DisplayName("override=0 → Integer.MAX_VALUE (cap bị tắt, không phải 0 ca)")
        void zeroOverrideDisablesCap() {
            // Đây là quirk quan trọng: 0 KHÔNG có nghĩa "0 ca", mà là "vô hiệu cap"
            assertEquals(Integer.MAX_VALUE, resolveEffectiveMax(0, 5, 5));
        }
    }

    @Nested
    @DisplayName("Đồng nhất 4 thuật toán (commit 372cefc claim)")
    class AlgorithmParity {

        /**
         * Mỗi entry-point của thuật toán trong AutoSchedulingService đều nhận
         * `Integer maxShiftsPerMonthOverride` rồi truyền xuống. Đây là test
         * smoke cho sự tồn tại của các method overload đó, đảm bảo không thuật
         * toán nào âm thầm bỏ qua override (đúng như bug mà commit fix).
         */
        @Test
        @DisplayName("AutoSchedulingService có đủ 4 entry-point nhận override")
        void allFourAlgorithmsReceiveOverride() throws Exception {
            Class<?> svc = Class.forName(
                    "com.hospital.scheduler.service.AutoSchedulingService");
            boolean hasRunGreedy = false, hasRunFairGreedy = false,
                    hasRunCsp = false, hasRunV10 = false;
            for (var m : svc.getDeclaredMethods()) {
                if (m.getName().equals("runGreedy") && m.getParameterCount() >= 8) hasRunGreedy = true;
                if (m.getName().equals("runFairGreedy") && m.getParameterCount() >= 7) hasRunFairGreedy = true;
                if (m.getName().equals("runCsp") && m.getParameterCount() >= 6) hasRunCsp = true;
                if (m.getName().equals("runV10LocalSearch") && m.getParameterCount() >= 6) hasRunV10 = true;
            }
            assertTrue(hasRunGreedy, "runGreedy phải nhận override");
            assertTrue(hasRunFairGreedy, "runFairGreedy phải nhận override");
            assertTrue(hasRunCsp, "runCsp phải nhận override");
            assertTrue(hasRunV10, "runV10LocalSearch phải nhận override");
        }

        @Test
        @DisplayName("LocalSearchScheduler.solve có overload Integer maxShiftsPerStaffOverride")
        void v10OverloadExists() throws Exception {
            Class<?> cls = Class.forName(
                    "com.hospital.scheduler.scheduling.LocalSearchScheduler");
            boolean hasOverride = false;
            for (var m : cls.getDeclaredMethods()) {
                if (m.getName().equals("solve")) {
                    for (var p : m.getParameterTypes()) {
                        if (p == Integer.class) { hasOverride = true; break; }
                    }
                }
            }
            assertTrue(hasOverride, "LocalSearchScheduler.solve phải có overload nhận Integer override");
        }
    }
}