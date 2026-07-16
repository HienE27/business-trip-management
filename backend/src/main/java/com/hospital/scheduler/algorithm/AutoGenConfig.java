package com.hospital.scheduler.algorithm;

import java.util.List;

/**
 * Cấu hình tự động sinh yêu cầu nhân sự khi mở kỳ lịch.
 *
 * <p><b>Nguyên tắc thiết kế (theo {@code QuanLyLichCongTac_v5.md}):</b>
 * L01/L02/L03/L04 là 4 loại <b>ca trực</b>, phân biệt bởi thời gian/ca
 * và chế độ nghỉ — <b>không phải bởi chuyên khoa</b>. Eligibility cho
 * L01/L02/L03 dùng {@link com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility#ALL_ELIGIBLE_SPECIALTIES}
 * (6 khoa: Ngoại, Nội, Sản, Nhi, Mắt, Răng). Không có cấu hình per-shift-type
 * specialties cho L01/L02/L03.
 *
 * <p>Chỉ có L04 (PK Chuyên gia) có cấu hình specialty:
 * {@code l04CrossSpecialty}, {@code l04CrossSpecialtyRatio},
 * {@code l04AllowedSpecialties}, {@code l04BalanceStrategy}.
 *
 * @see com.hospital.scheduler.algorithm.scoring.StaffShiftTypeEligibility
 */
public record AutoGenConfig(
    boolean enabled,
    int l01MinPerDay, int l02MinPerDay, int l03MinPerDay, int l04MinPerDay,
    int l01MaxPerDay, int l02MaxPerDay, int l03MaxPerDay, int l04MaxPerDay,
    int l01MinPerWeek, int l02MinPerWeek, int l03MinPerWeek, int l04MinPerWeek,
    int l01MaxPerWeek, int l02MaxPerWeek, int l03MaxPerWeek, int l04MaxPerWeek,
    String holidayMode,  // "SKIP" or "PARTIAL"
    List<String> removedShiftTypes,  // e.g. ["L03", "L04"] to skip when generating

    // L04 cross-specialty (chỉ L04 có specialty config)
    boolean l04CrossSpecialty,
    float l04CrossSpecialtyRatio,
    List<String> l04AllowedSpecialties,  // null/empty = tất cả 6 khoa
    String l04BalanceStrategy   // "STRICT_MATCH_ONLY", "FAIR_DISTRIBUTE", "WEIGHTED_FAIR"
) {
    /**
     * Builder-style factory cho backward compatibility với code cũ.
     * L01/L02/L03 specialties bị bỏ qua — luôn dùng ALL_ELIGIBLE_SPECIALTIES.
     *
     * @deprecated Use {@link #builder()} for new code
     */
    @Deprecated
    public static AutoGenConfig withDefaults(
            boolean enabled,
            int l01MinPerDay, int l02MinPerDay, int l03MinPerDay, int l04MinPerDay,
            int l01MaxPerDay, int l02MaxPerDay, int l03MaxPerDay, int l04MaxPerDay,
            int l01MinPerWeek, int l02MinPerWeek, int l03MinPerWeek, int l04MinPerWeek,
            int l01MaxPerWeek, int l02MaxPerWeek, int l03MaxPerWeek, int l04MaxPerWeek,
            String holidayMode, List<String> removedShiftTypes,
            // L01/L02/L03 fields (ignored in new model)
            boolean l01CrossSpecialty, float l01CrossSpecialtyRatio,
            List<String> l01AllowedSpecialties, String l01BalanceStrategy,
            boolean l02CrossSpecialty, float l02CrossSpecialtyRatio,
            List<String> l02AllowedSpecialties, String l02BalanceStrategy,
            boolean l03CrossSpecialty, float l03CrossSpecialtyRatio,
            List<String> l03AllowedSpecialties, String l03BalanceStrategy,
            // L04 fields
            boolean l04CrossSpecialty, float l04CrossSpecialtyRatio,
            List<String> l04AllowedSpecialties, String l04BalanceStrategy) {
        return new AutoGenConfig(
                enabled,
                l01MinPerDay, l02MinPerDay, l03MinPerDay, l04MinPerDay,
                l01MaxPerDay, l02MaxPerDay, l03MaxPerDay, l04MaxPerDay,
                l01MinPerWeek, l02MinPerWeek, l03MinPerWeek, l04MinPerWeek,
                l01MaxPerWeek, l02MaxPerWeek, l03MaxPerWeek, l04MaxPerWeek,
                holidayMode, removedShiftTypes,
                l04CrossSpecialty, l04CrossSpecialtyRatio, l04AllowedSpecialties, l04BalanceStrategy);
    }

    /**
     * Builder for new code. L01/L02/L03 không có cấu hình specialty.
     */
    public static Builder builder() { return new Builder(); }

    public static class Builder {
        private boolean enabled = true;
        private int l01MinPerDay = 1, l02MinPerDay = 1, l03MinPerDay = 1, l04MinPerDay = 1;
        private int l01MaxPerDay = 0, l02MaxPerDay = 0, l03MaxPerDay = 0, l04MaxPerDay = 0;
        private int l01MinPerWeek = 1, l02MinPerWeek = 2, l03MinPerWeek = 1, l04MinPerWeek = 1;
        private int l01MaxPerWeek = 0, l02MaxPerWeek = 0, l03MaxPerWeek = 0, l04MaxPerWeek = 0;
        private String holidayMode = "SKIP";
        private List<String> removedShiftTypes = List.of();
        private boolean l04CrossSpecialty = false;
        private float l04CrossSpecialtyRatio = 0.5f;
        private List<String> l04AllowedSpecialties = List.of();
        private String l04BalanceStrategy = "FAIR_DISTRIBUTE";

        public Builder enabled(boolean v) { this.enabled = v; return this; }
        public Builder l01MinPerDay(int v) { this.l01MinPerDay = v; return this; }
        public Builder l02MinPerDay(int v) { this.l02MinPerDay = v; return this; }
        public Builder l03MinPerDay(int v) { this.l03MinPerDay = v; return this; }
        public Builder l04MinPerDay(int v) { this.l04MinPerDay = v; return this; }
        public Builder l01MaxPerDay(int v) { this.l01MaxPerDay = v; return this; }
        public Builder l02MaxPerDay(int v) { this.l02MaxPerDay = v; return this; }
        public Builder l03MaxPerDay(int v) { this.l03MaxPerDay = v; return this; }
        public Builder l04MaxPerDay(int v) { this.l04MaxPerDay = v; return this; }
        public Builder l01MinPerWeek(int v) { this.l01MinPerWeek = v; return this; }
        public Builder l02MinPerWeek(int v) { this.l02MinPerWeek = v; return this; }
        public Builder l03MinPerWeek(int v) { this.l03MinPerWeek = v; return this; }
        public Builder l04MinPerWeek(int v) { this.l04MinPerWeek = v; return this; }
        public Builder l01MaxPerWeek(int v) { this.l01MaxPerWeek = v; return this; }
        public Builder l02MaxPerWeek(int v) { this.l02MaxPerWeek = v; return this; }
        public Builder l03MaxPerWeek(int v) { this.l03MaxPerWeek = v; return this; }
        public Builder l04MaxPerWeek(int v) { this.l04MaxPerWeek = v; return this; }
        public Builder holidayMode(String v) { this.holidayMode = v; return this; }
        public Builder removedShiftTypes(List<String> v) { this.removedShiftTypes = v; return this; }
        public Builder l04CrossSpecialty(boolean v) { this.l04CrossSpecialty = v; return this; }
        public Builder l04CrossSpecialtyRatio(float v) { this.l04CrossSpecialtyRatio = v; return this; }
        public Builder l04AllowedSpecialties(List<String> v) { this.l04AllowedSpecialties = v; return this; }
        public Builder l04BalanceStrategy(String v) { this.l04BalanceStrategy = v; return this; }

        public AutoGenConfig build() {
            return new AutoGenConfig(
                    enabled,
                    l01MinPerDay, l02MinPerDay, l03MinPerDay, l04MinPerDay,
                    l01MaxPerDay, l02MaxPerDay, l03MaxPerDay, l04MaxPerDay,
                    l01MinPerWeek, l02MinPerWeek, l03MinPerWeek, l04MinPerWeek,
                    l01MaxPerWeek, l02MaxPerWeek, l03MaxPerWeek, l04MaxPerWeek,
                    holidayMode, removedShiftTypes,
                    l04CrossSpecialty, l04CrossSpecialtyRatio, l04AllowedSpecialties, l04BalanceStrategy);
        }
    }
}
