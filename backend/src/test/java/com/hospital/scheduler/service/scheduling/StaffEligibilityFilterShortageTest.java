package com.hospital.scheduler.service.scheduling;

import com.hospital.scheduler.entity.ShiftRequirement;
import com.hospital.scheduler.entity.ShiftType;
import com.hospital.scheduler.entity.Specialty;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.ConflictDetectionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for {@link StaffEligibilityFilter#shouldPreferCrossSpecialty(ShiftRequirement, int, int, float)}
 * applying A + Shortage Logic.
 *
 * <p>Nguyên tắc:
 * <ul>
 *   <li>strict ≥ required → KHÔNG cross (giữ fairness)</li>
 *   <li>strict < required → cross nếu shortage ≥ (1 - ratio)</li>
 * </ul>
 *
 * <p>Bảng truth (ratio = 0.5):
 * <ul>
 *   <li>strict=3, req=3 → 0% shortage → KHÔNG cross</li>
 *   <li>strict=2, req=3 → 33% shortage → KHÔNG cross</li>
 *   <li>strict=1, req=3 → 67% shortage → CÓ cross</li>
 *   <li>strict=0, req=3 → 100% shortage → CÓ cross</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("StaffEligibilityFilter - A + Shortage Logic")
class StaffEligibilityFilterShortageTest {

    @Mock
    private ConflictDetectionService conflictDetectionService;

    @Mock
    private AlgorithmConfigService algorithmConfigService;

    @InjectMocks
    private StaffEligibilityFilter filter;

    private Specialty ngoai;
    private Specialty noi;
    private ShiftType l04;

    @BeforeEach
    void setUp() {
        ngoai = buildSpecialty(1, "Ngoại");
        noi = buildSpecialty(2, "Nội");
        l04 = buildShiftType(ConflictDetectionService.SHIFT_TYPE_L04);
    }

    // ─── Strict đủ → KHÔNG cross ─────────────────────────────────────────

    @Test
    @DisplayName("strict = required → KHÔNG dùng cross (ratio=0.5)")
    void strictEqualsRequired_noCross() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        assertFalse(filter.shouldPreferCrossSpecialty(req, 3, 3, 0.5f));
    }

    @Test
    @DisplayName("strict > required → KHÔNG dùng cross (ratio=1.0)")
    void strictExceedsRequired_noCross() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        assertFalse(filter.shouldPreferCrossSpecialty(req, 5, 3, 1.0f));
        assertFalse(filter.shouldPreferCrossSpecialty(req, 10, 3, 1.0f));
    }

    // ─── ratio = 0.5 (default) ───────────────────────────────────────────

    @Test
    @DisplayName("ratio=0.5, shortage=33% < 50% → KHÔNG cross")
    void ratioHalf_shortage33_noCross() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        // strict=2, required=3 → shortage = (3-2)/3 = 33.33%
        // threshold = 1 - 0.5 = 0.5 (50%)
        // 33% < 50% → false
        assertFalse(filter.shouldPreferCrossSpecialty(req, 2, 3, 0.5f));
    }

    @Test
    @DisplayName("ratio=0.5, shortage=67% ≥ 50% → CÓ cross")
    void ratioHalf_shortage67_useCross() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        // strict=1, required=3 → shortage = (3-1)/3 = 66.67%
        // threshold = 0.5
        // 67% ≥ 50% → true
        assertTrue(filter.shouldPreferCrossSpecialty(req, 1, 3, 0.5f));
    }

    @Test
    @DisplayName("ratio=0.5, shortage=100% → CÓ cross (boundary full shortage)")
    void ratioHalf_fullShortage_useCross() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        assertTrue(filter.shouldPreferCrossSpecialty(req, 0, 3, 0.5f));
    }

    // ─── ratio = 0.0 (không bao giờ cross) ───────────────────────────────

    @Test
    @DisplayName("ratio=0.0, dù thiếu hết vẫn KHÔNG cross")
    void ratioZero_neverCross() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        assertFalse(filter.shouldPreferCrossSpecialty(req, 2, 3, 0.0f));
        assertFalse(filter.shouldPreferCrossSpecialty(req, 1, 3, 0.0f));
        assertFalse(filter.shouldPreferCrossSpecialty(req, 0, 3, 0.0f));
        assertFalse(filter.shouldPreferCrossSpecialty(req, 0, 1, 0.0f));
    }

    // ─── ratio = 1.0 (cross khi thiếu bất kỳ) ────────────────────────────

    @Test
    @DisplayName("ratio=1.0, strict=required → KHÔNG cross (không thiếu)")
    void ratioOne_strictEnough_noCross() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        assertFalse(filter.shouldPreferCrossSpecialty(req, 3, 3, 1.0f));
    }

    @Test
    @DisplayName("ratio=1.0, thiếu dù chỉ 1 → CÓ cross")
    void ratioOne_anyShortage_useCross() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        // thiếu 1
        assertTrue(filter.shouldPreferCrossSpecialty(req, 2, 3, 1.0f));
        // thiếu 2
        assertTrue(filter.shouldPreferCrossSpecialty(req, 1, 3, 1.0f));
        // thiếu hết
        assertTrue(filter.shouldPreferCrossSpecialty(req, 0, 3, 1.0f));
    }

    // ─── ratio = 0.3 (strict - shortage phải ≥ 70%) ──────────────────────

    @Test
    @DisplayName("ratio=0.3, shortage=67% < 70% → KHÔNG cross")
    void ratioThreeTenths_shortage67_noCross() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        // shortage = 67%, threshold = 70%
        assertFalse(filter.shouldPreferCrossSpecialty(req, 1, 3, 0.3f));
    }

    @Test
    @DisplayName("ratio=0.3, shortage=100% ≥ 70% → CÓ cross")
    void ratioThreeTenths_fullShortage_useCross() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        assertTrue(filter.shouldPreferCrossSpecialty(req, 0, 3, 0.3f));
    }

    // ─── Guard clauses ──────────────────────────────────────────────────

    @Test
    @DisplayName("non-L04 (L01/L02/L03) → luôn false dù thiếu")
    void nonL04Type_alwaysFalse() {
        ShiftRequirement l01Req = buildReq(buildShiftType("L01"), ngoai, 3);
        ShiftRequirement l02Req = buildReq(buildShiftType("L02"), ngoai, 3);
        ShiftRequirement l03Req = buildReq(buildShiftType("L03"), ngoai, 3);

        assertFalse(filter.shouldPreferCrossSpecialty(l01Req, 0, 3, 1.0f));
        assertFalse(filter.shouldPreferCrossSpecialty(l02Req, 0, 3, 1.0f));
        assertFalse(filter.shouldPreferCrossSpecialty(l03Req, 0, 3, 1.0f));
    }

    @Test
    @DisplayName("L04 không có specialty → luôn false")
    void l04WithoutSpecialty_alwaysFalse() {
        ShiftRequirement req = buildReq(l04, null, 3);
        assertFalse(filter.shouldPreferCrossSpecialty(req, 0, 3, 1.0f));
        assertFalse(filter.shouldPreferCrossSpecialty(req, 0, 3, 0.5f));
    }

    @Test
    @DisplayName("required <= 0 → luôn false (edge case)")
    void requiredZeroOrNegative_alwaysFalse() {
        ShiftRequirement req = buildL04Req(ngoai, 0);
        assertFalse(filter.shouldPreferCrossSpecialty(req, 0, 0, 1.0f));

        ShiftRequirement reqNeg = buildL04Req(ngoai, -1);
        assertFalse(filter.shouldPreferCrossSpecialty(reqNeg, 0, -1, 1.0f));
    }

    @Test
    @DisplayName("req == null → luôn false (null safety)")
    void nullReq_alwaysFalse() {
        // Test the ShiftRequirement overload with null req
        assertFalse(filter.shouldPreferCrossSpecialty((ShiftRequirement) null, 0, 3, 1.0f));
    }

    // ─── Determinism ────────────────────────────────────────────────────

    @Test
    @DisplayName("Deterministic: cùng input → cùng output (không random)")
    void deterministic_sameInputSameOutput() {
        ShiftRequirement req = buildL04Req(ngoai, 3);

        for (int i = 0; i < 100; i++) {
            boolean result = filter.shouldPreferCrossSpecialty(req, 1, 3, 0.5f);
            assertTrue(result, "Iteration " + i + " should be deterministic and true");
        }
    }

    @Test
    @DisplayName("Strict >= required ưu tiên hơn shortage ratio (ưu tiên fairness)")
    void strictPriority_beatsShortageLogic() {
        // strict = 5, required = 3, ratio = 1.0 → KHÔNG cross dù ratio bảo cross
        ShiftRequirement req = buildL04Req(ngoai, 3);
        assertFalse(filter.shouldPreferCrossSpecialty(req, 5, 3, 1.0f));
        assertFalse(filter.shouldPreferCrossSpecialty(req, 4, 3, 1.0f));
        assertFalse(filter.shouldPreferCrossSpecialty(req, 3, 3, 1.0f));
    }

    // ─── Clamp ratio out-of-range ────────────────────────────────────────

    @Test
    @DisplayName("ratio < 0 → coi như 0 (không bao giờ cross)")
    void negativeRatio_treatedAsZero() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        // ratio âm được guard explicit → luôn false dù thiếu nặng
        assertFalse(filter.shouldPreferCrossSpecialty(req, 0, 3, -0.5f));
        assertFalse(filter.shouldPreferCrossSpecialty(req, 1, 3, -0.5f));
        // ratio = 0 cũng guard → false
        assertFalse(filter.shouldPreferCrossSpecialty(req, 0, 3, 0.0f));
    }

    @Test
    @DisplayName("ratio > 1.0 → clamp về 1.0 (cross khi thiếu bất kỳ)")
    void ratioOverOne_treatedAsOne() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        assertTrue(filter.shouldPreferCrossSpecialty(req, 2, 3, 1.5f));
        assertTrue(filter.shouldPreferCrossSpecialty(req, 0, 3, 2.0f));
    }

    // ─── Multi-specialty sanity ──────────────────────────────────────────

    @Test
    @DisplayName("Áp dụng độc lập cho từng specialty (không cross-talk)")
    void perRequirementIndependentDecision() {
        ShiftRequirement ngoaiReq = buildL04Req(ngoai, 3);
        ShiftRequirement noiReq = buildL04Req(noi, 3);

        // Ngoại thiếu nặng → cross
        assertTrue(filter.shouldPreferCrossSpecialty(ngoaiReq, 0, 3, 0.5f));
        // Nội thiếu ít → không cross
        assertFalse(filter.shouldPreferCrossSpecialty(noiReq, 2, 3, 0.5f));
    }

    // ─── NaN / Inifinity edge cases ──────────────────────────────────────

    @Test
    @DisplayName("ratio = NaN → return false (NaN comparison trong IEEE 754)")
    void nanRatio_treatedAsZero() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        // NaN so sánh luôn false trong IEEE 754:
        // - NaN <= 0.0f = false → skip guard
        // - 1 - NaN = NaN, clamp(NaN, 0, 1) = NaN
        // - shortage >= NaN = false
        // → returns false (silently fall-through to "no cross")
        assertFalse(filter.shouldPreferCrossSpecialty(req, 0, 3, Float.NaN),
                "NaN ratio phải được treat an toàn - return false");
        assertFalse(filter.shouldPreferCrossSpecialty(req, 1, 3, Float.NaN));
        assertFalse(filter.shouldPreferCrossSpecialty(req, 0, 1, Float.NaN));
    }

    @Test
    @DisplayName("ratio = +Infinity → threshold = 0 → cross khi thiếu")
    void positiveInfinityRatio_useCrossOnAnyShortage() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        // 1 - Infinity = -Infinity → clamp(-Infinity, 0, 1) = 0
        // → shortage >= 0 = always true khi có shortage
        assertTrue(filter.shouldPreferCrossSpecialty(req, 0, 3, Float.POSITIVE_INFINITY));
        assertTrue(filter.shouldPreferCrossSpecialty(req, 2, 3, Float.POSITIVE_INFINITY));
        assertFalse(filter.shouldPreferCrossSpecialty(req, 3, 3, Float.POSITIVE_INFINITY),
                "strict = required → guard kích hoạt trước → false");
    }

    @Test
    @DisplayName("ratio = -Infinity → guard ratio<=0 kích hoạt → false")
    void negativeInfinityRatio_treatedAsZero() {
        ShiftRequirement req = buildL04Req(ngoai, 3);
        // -Infinity <= 0.0f = true → guard kích hoạt → false
        assertFalse(filter.shouldPreferCrossSpecialty(req, 0, 3, Float.NEGATIVE_INFINITY));
    }

    // ─── required = Integer.MAX_VALUE edge case ──────────────────────────

    @Test
    @DisplayName("required = Integer.MAX_VALUE → không overflow do dùng double")
    void requiredIntegerMax_noOverflow() {
        ShiftRequirement req = buildL04Req(ngoai, Integer.MAX_VALUE);
        // shortage = (MAX - 0) / MAX = 1.0 (Double precision, không overflow)
        assertTrue(filter.shouldPreferCrossSpecialty(req, 0, Integer.MAX_VALUE, 0.5f));
        // strict = MAX → shortage = 0 → guard strict>=required kích hoạt trước
        assertFalse(filter.shouldPreferCrossSpecialty(req, Integer.MAX_VALUE, Integer.MAX_VALUE, 0.5f));
    }

    @Test
    @DisplayName("required nhỏ (1, 2) → cross checks vẫn hợp lệ")
    void smallRequired_oneAndTwo() {
        ShiftRequirement req1 = buildL04Req(ngoai, 1);
        ShiftRequirement req2 = buildL04Req(ngoai, 2);

        // required=1, strict=0 → shortage=100% → cross
        assertTrue(filter.shouldPreferCrossSpecialty(req1, 0, 1, 0.5f));
        // required=1, strict=1 → guard strict>=required
        assertFalse(filter.shouldPreferCrossSpecialty(req1, 1, 1, 0.5f));

        // required=2, strict=0 → shortage=100% → cross
        assertTrue(filter.shouldPreferCrossSpecialty(req2, 0, 2, 0.5f));
        // required=2, strict=1 → shortage=50% (=threshold) → use cross
        assertTrue(filter.shouldPreferCrossSpecialty(req2, 1, 2, 0.5f),
                "shortage=50%, threshold=50%, 50%>=50%=true");
    }

    // ─── Test fixtures ───────────────────────────────────────────────────

    private ShiftType buildShiftType(String id) {
        ShiftType st = new ShiftType();
        st.setId(id);
        st.setName(id);
        return st;
    }

    private Specialty buildSpecialty(int id, String name) {
        Specialty sp = new Specialty();
        sp.setId(id);
        sp.setName(name);
        return sp;
    }

    private ShiftRequirement buildL04Req(Specialty specialty, int requiredCount) {
        return buildReq(l04, specialty, requiredCount);
    }

    private ShiftRequirement buildReq(ShiftType shiftType, Specialty specialty, int requiredCount) {
        ShiftRequirement req = new ShiftRequirement();
        req.setShiftType(shiftType);
        req.setSpecialty(specialty);
        req.setRequiredStaffCount(requiredCount);
        req.setWorkDate(LocalDate.of(2026, 1, 1));
        return req;
    }
}
