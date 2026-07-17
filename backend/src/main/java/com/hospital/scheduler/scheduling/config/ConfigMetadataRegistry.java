package com.hospital.scheduler.scheduling.config;

import java.util.*;

/**
 * Central registry of all configuration field metadata.
 * Single source of truth for UI rendering AND backend validation.
 *
 * <p>To add a new config field:
 * <ol>
 *   <li>Add the field to {@link ConfigDomain}</li>
 *   <li>Add metadata entry to this registry</li>
 *   <li>Add mapper entry in {@link ConfigMapper}</li>
 *   <li>Done — UI and validation work automatically</li>
 * </ol>
 *
 * <p>Categories match {@link ConfigMetadata.ConfigCategory}.
 */
public final class ConfigMetadataRegistry {

    private ConfigMetadataRegistry() {}

    /** All metadata entries, keyed by field path. */
    private static final Map<String, ConfigMetadata> FIELDS = buildRegistry();

    /** Fields grouped by category. */
    private static final Map<ConfigMetadata.ConfigCategory, List<ConfigMetadata>> BY_CATEGORY = buildByCategory();

    /** All field paths. */
    public static Set<String> fieldPaths() {
        return Collections.unmodifiableSet(FIELDS.keySet());
    }

    /** Get metadata for a single field. */
    public static ConfigMetadata get(String fieldPath) {
        return FIELDS.get(fieldPath);
    }

    /** Get all metadata for a category. */
    public static List<ConfigMetadata> byCategory(ConfigMetadata.ConfigCategory category) {
        return BY_CATEGORY.getOrDefault(category, Collections.emptyList());
    }

    /** Get all metadata entries. */
    public static Collection<ConfigMetadata> all() {
        return FIELDS.values();
    }

    /** Get all categories that have fields. */
    public static List<ConfigMetadata.ConfigCategory> categories() {
        return Arrays.stream(ConfigMetadata.ConfigCategory.values())
                .filter(cat -> !BY_CATEGORY.getOrDefault(cat, Collections.emptyList()).isEmpty())
                .sorted(Comparator.comparingInt((ConfigMetadata.ConfigCategory cat) -> cat.sortOrder))
                .toList();
    }

    // ─── Factory helpers for Option arrays ───────────────────────────────────

    private static ConfigMetadata.Option[] shiftTypeOptions() {
        return new ConfigMetadata.Option[]{
                new ConfigMetadata.Option("L01", "Lịch trực 24/24"),
                new ConfigMetadata.Option("L02", "Lịch thông tầm"),
                new ConfigMetadata.Option("L03", "Phòng khám dịch vụ"),
                new ConfigMetadata.Option("L04", "Phòng khám chuyên gia")
        };
    }

    private static ConfigMetadata.Option[] holidayModeOptions() {
        return new ConfigMetadata.Option[]{
                new ConfigMetadata.Option("SKIP", "Bỏ qua ngày nghỉ"),
                new ConfigMetadata.Option("PARTIAL", "Phủ một phần")
        };
    }

    private static ConfigMetadata.Option[] acceptanceStrategyOptions() {
        return new ConfigMetadata.Option[]{
                new ConfigMetadata.Option("TABU", "Tabu Search"),
                new ConfigMetadata.Option("HILL_CLIMBING", "Hill Climbing"),
                new ConfigMetadata.Option("SIMULATED_ANNEALING", "Simulated Annealing"),
                new ConfigMetadata.Option("LATE_ACCEPTANCE", "Late Acceptance"),
                new ConfigMetadata.Option("GREAT_DELUGE", "Great Deluge")
        };
    }

    private static ConfigMetadata.Option[] balanceStrategyOptions() {
        return new ConfigMetadata.Option[]{
                new ConfigMetadata.Option("STRICT_MATCH_ONLY", "Đúng chuyên khoa"),
                new ConfigMetadata.Option("FAIR_DISTRIBUTE", "Phân phối công bằng"),
                new ConfigMetadata.Option("WEIGHTED_FAIR", "Công bằng có trọng số")
        };
    }

    // ─── Registry builder ───────────────────────────────────────────────────

    private static Map<String, ConfigMetadata> buildRegistry() {
        Map<String, ConfigMetadata> m = new LinkedHashMap<>();

        // ═══════════════════════════════════════════════════════════════════════
        // GENERAL
        // ═══════════════════════════════════════════════════════════════════════
        put(m, ConfigMetadata.toggle(
                "enabled",
                "Bật tự động xếp lịch",
                "Kích hoạt chức năng tự động xếp lịch. Tắt để tạm dừng mà không xóa cấu hình.",
                ConfigMetadata.ConfigCategory.GENERAL,
                true
        ));

        put(m, ConfigMetadata.select(
                "holidayMode",
                "Chế độ ngày nghỉ lễ",
                "Cách xử lý khi gặp ngày nghỉ lễ trong kỳ xếp lịch.",
                ConfigMetadata.ConfigCategory.GENERAL,
                "SKIP",
                holidayModeOptions()
        ));

        put(m, ConfigMetadata.chipGroup(
                "removedShiftTypes",
                "Loại trừ ca xếp",
                "Những loại ca sẽ KHÔNG được tự động xếp.",
                ConfigMetadata.ConfigCategory.GENERAL,
                new String[]{},
                shiftTypeOptions()
        ));

        // ═══════════════════════════════════════════════════════════════════════
        // ALGORITHM
        // ═══════════════════════════════════════════════════════════════════════
        put(m, ConfigMetadata.integer(
                "algorithm.maxIterations",
                "Số lần lặp tối đa",
                "Số lần lặp tối đa trước khi dừng. Cao hơn = chất lượng hơn nhưng chậm hơn.",
                ConfigMetadata.ConfigCategory.ALGORITHM,
                500, 1, 10000
        ));

        put(m, ConfigMetadata.integer(
                "algorithm.neighborhoodSize",
                "Kích thước vùng lân cận",
                "Số ứng viên (move) được tạo mỗi lần lặp. Lớn hơn = tìm kiếm rộng hơn.",
                ConfigMetadata.ConfigCategory.ALGORITHM,
                10, 1, 100
        ));

        put(m, ConfigMetadata.integer(
                "algorithm.tabuTenureMin",
                "Tabu tối thiểu",
                "Giá trị tối thiểu của khoảng thời gian Tabu (sẽ chọn ngẫu nhiên trong khoảng này).",
                ConfigMetadata.ConfigCategory.ALGORITHM,
                5, 1, 100
        ));

        put(m, ConfigMetadata.integer(
                "algorithm.tabuTenureMax",
                "Tabu tối đa",
                "Giá trị tối đa của khoảng thời gian Tabu.",
                ConfigMetadata.ConfigCategory.ALGORITHM,
                10, 1, 100
        ));

        put(m, ConfigMetadata.integer(
                "algorithm.maxNoImproveIterations",
                "Dừng nếu không cải thiện",
                "Dừng thuật toán nếu không có cải thiện sau N lần lặp.",
                ConfigMetadata.ConfigCategory.ALGORITHM,
                50, 1, 1000
        ));

        put(m, ConfigMetadata.decimal(
                "algorithm.relativeImprovementThreshold",
                "Ngưỡng cải thiện tương đối",
                "Tiếp tục tìm kiếm nếu cải thiện ≥ ngưỡng này. Nhỏ hơn = tìm kiếm kỹ hơn.",
                ConfigMetadata.ConfigCategory.ALGORITHM,
                0.001, 0.0, 1.0, 0.001
        ));

        put(m, ConfigMetadata.integer(
                "algorithm.diversifyAfterIterations",
                "Đa dạng hóa sau",
                "Số lần lặp không cải thiện trước khi kích hoạt đa dạng hóa.",
                ConfigMetadata.ConfigCategory.ALGORITHM,
                20, 1, 1000
        ));

        // ═══════════════════════════════════════════════════════════════════════
        // ACCEPTANCE STRATEGY
        // ═══════════════════════════════════════════════════════════════════════
        put(m, ConfigMetadata.select(
                "acceptanceStrategy.kind",
                "Chiến lược chấp nhận",
                "Chiến lược quyết định có chấp nhận move không cải thiện hay không.",
                ConfigMetadata.ConfigCategory.ACCEPTANCE,
                "TABU",
                acceptanceStrategyOptions()
        ));

        put(m, ConfigMetadata.decimal(
                "acceptanceStrategy.saInitialTemperature",
                "SA: Nhiệt độ ban đầu",
                "Nhiệt độ ban đầu cho Simulated Annealing. Cao hơn = chấp nhận nhiều move tệ hơn.",
                ConfigMetadata.ConfigCategory.ACCEPTANCE,
                100.0, 0.1, 10000.0, 0.1
        ));

        put(m, ConfigMetadata.decimal(
                "acceptanceStrategy.saCoolingRate",
                "SA: Tốc độ làm nguội",
                "Hệ số nhân mỗi lần lặp (0.9995 = 0.05% giảm). Gần 1.0 = làm nguội chậm.",
                ConfigMetadata.ConfigCategory.ACCEPTANCE,
                0.9995, 0.9, 0.99999, 0.00001
        ));

        put(m, ConfigMetadata.decimal(
                "acceptanceStrategy.saTemperatureMin",
                "SA: Nhiệt độ tối thiểu",
                "Dừng SA khi nhiệt độ giảm xuống dưới ngưỡng này.",
                ConfigMetadata.ConfigCategory.ACCEPTANCE,
                0.01, 0.001, 100.0, 0.001
        ));

        put(m, ConfigMetadata.integer(
                "acceptanceStrategy.laMemorySize",
                "LA: Kích thước bộ nhớ",
                "Số lượng lời giải trước đó để so sánh trong Late Acceptance.",
                ConfigMetadata.ConfigCategory.ACCEPTANCE,
                10, 1, 100
        ));

        put(m, ConfigMetadata.decimal(
                "acceptanceStrategy.gdInitialLevel",
                "GD: Mức nước ban đầu",
                "Mức nước ban đầu (giới hạn trên của điểm số) cho Great Deluge.",
                ConfigMetadata.ConfigCategory.ACCEPTANCE,
                1000.0, 100.0, 100000.0, 1.0
        ));

        put(m, ConfigMetadata.decimal(
                "acceptanceStrategy.gdDecayRate",
                "GD: Tốc độ giảm",
                "Hệ số nhân mỗi lần lặp cho GD (0.999 = 0.1% giảm).",
                ConfigMetadata.ConfigCategory.ACCEPTANCE,
                0.999, 0.9, 0.99999, 0.0001
        ));

        put(m, ConfigMetadata.decimal(
                "acceptanceStrategy.gdMinLevel",
                "GD: Mức nước tối thiểu",
                "Dừng GD khi mức nước giảm xuống dưới ngưỡng này.",
                ConfigMetadata.ConfigCategory.ACCEPTANCE,
                0.0, 0.0, 1000.0, 1.0
        ));

        // ═══════════════════════════════════════════════════════════════════════
        // FAIRNESS
        // ═══════════════════════════════════════════════════════════════════════
        put(m, ConfigMetadata.percentage(
                "fairness.cvTarget",
                "Mục tiêu CV (hệ số biến thiên)",
                "Hệ số biến thiên mục tiêu cho phân phối ca trực. Thấp hơn = phân phối đều hơn.",
                ConfigMetadata.ConfigCategory.FAIRNESS,
                0.10, 0.0, 1.0
        ));

        put(m, ConfigMetadata.percentage(
                "fairness.cvWorst",
                "CV tồi tệ nhất có thể chấp nhận",
                "Nếu CV vượt quá ngưỡng này, thuật toán coi là vi phạm công bằng nghiêm trọng.",
                ConfigMetadata.ConfigCategory.FAIRNESS,
                0.50, 0.0, 1.0
        ));

        put(m, ConfigMetadata.decimal(
                "fairness.weekendWeight",
                "Trọng số cuối tuần",
                "Nhân trọng số này với penalty cuối tuần. Cao hơn = tránh xếp cuối tuần hơn.",
                ConfigMetadata.ConfigCategory.FAIRNESS,
                2.0, 0.0, 10.0, 0.1
        ));

        // ═══════════════════════════════════════════════════════════════════════
        // COVERAGE
        // ═══════════════════════════════════════════════════════════════════════
        // L01
        put(m, l01Bounds("minPerDay", "L01 - Tối thiểu/ngày",
                "Số ca trực 24/24 tối thiểu được phân mỗi ngày.", 1, 20));
        put(m, l01Bounds("maxPerDay", "L01 - Tối đa/ngày",
                "Số ca trực 24/24 tối đa được phân mỗi ngày.", 1, 50));
        put(m, l01Bounds("minPerWeek", "L01 - Tối thiểu/tuần",
                "Số ca trực 24/24 tối thiểu mỗi nhân sự mỗi tuần.", 1, 7));
        put(m, l01Bounds("maxPerWeek", "L01 - Tối đa/tuần",
                "Số ca trực 24/24 tối đa mỗi nhân sự mỗi tuần.", 1, 7));

        // L02
        put(m, l02Bounds("minPerDay", "L02 - Tối thiểu/ngày",
                "Số ca thông tầm tối thiểu được phân mỗi ngày.", 1, 20));
        put(m, l02Bounds("maxPerDay", "L02 - Tối đa/ngày",
                "Số ca thông tầm tối đa được phân mỗi ngày.", 1, 50));
        put(m, l02Bounds("minPerWeek", "L02 - Tối thiểu/tuần",
                "Số ca thông tầm tối thiểu mỗi nhân sự mỗi tuần.", 1, 7));
        put(m, l02Bounds("maxPerWeek", "L02 - Tối đa/tuần",
                "Số ca thông tầm tối đa mỗi nhân sự mỗi tuần.", 1, 7));

        // L03
        put(m, l03Bounds("minPerDay", "L03 - Tối thiểu/ngày",
                "Số ca dịch vụ tối thiểu được phân mỗi ngày.", 1, 20));
        put(m, l03Bounds("maxPerDay", "L03 - Tối đa/ngày",
                "Số ca dịch vụ tối đa được phân mỗi ngày.", 1, 50));
        put(m, l03Bounds("minPerWeek", "L03 - Tối thiểu/tuần",
                "Số ca dịch vụ tối thiểu mỗi nhân sự mỗi tuần.", 1, 7));
        put(m, l03Bounds("maxPerWeek", "L03 - Tối đa/tuần",
                "Số ca dịch vụ tối đa mỗi nhân sự mỗi tuần.", 1, 7));

        // L04
        put(m, l04Bounds("minPerDay", "L04 - Tối thiểu/ngày",
                "Số ca chuyên gia tối thiểu được phân mỗi ngày.", 1, 20));
        put(m, l04Bounds("maxPerDay", "L04 - Tối đa/ngày",
                "Số ca chuyên gia tối đa được phân mỗi ngày.", 1, 50));
        put(m, l04Bounds("minPerWeek", "L04 - Tối thiểu/tuần",
                "Số ca chuyên gia tối thiểu mỗi nhân sự mỗi tuần.", 1, 7));
        put(m, l04Bounds("maxPerWeek", "L04 - Tối đa/tuần",
                "Số ca chuyên gia tối đa mỗi nhân sự mỗi tuần.", 1, 7));

        // ═══════════════════════════════════════════════════════════════════════
        // L04 - Expert Clinic specific
        // ═══════════════════════════════════════════════════════════════════════
        put(m, ConfigMetadata.toggle(
                "l04.crossSpecialtyEnabled",
                "Bật cross-specialty L04",
                "Cho phép nhân sự khác chuyên khoa phục vụ ca chuyên gia khi thiếu.",
                ConfigMetadata.ConfigCategory.L04,
                false
        ));

        put(m, ConfigMetadata.percentage(
                "l04.crossSpecialtyRatio",
                "Tỷ lệ cross-specialty L04",
                "Tỷ lệ tối đa ca L04 có thể giao cho nhân sự không đúng chuyên khoa.",
                ConfigMetadata.ConfigCategory.L04,
                0.30, 0.0, 1.0
        ));

        put(m, ConfigMetadata.chipGroup(
                "l04.allowedSpecialties",
                "Chuyên khoa được phép L04",
                "Danh sách chuyên khoa được phép phục vụ ca L04 (không áp dụng cross-specialty).",
                ConfigMetadata.ConfigCategory.L04,
                new String[]{},
                specialtyOptions()
        ));

        put(m, ConfigMetadata.select(
                "l04.balanceStrategy",
                "Chiến lược cân bằng L04",
                "Cách phân phối ca L04 cho nhân sự cùng chuyên khoa.",
                ConfigMetadata.ConfigCategory.L04,
                "FAIR_DISTRIBUTE",
                balanceStrategyOptions()
        ));

        // ═══════════════════════════════════════════════════════════════════════
        // CONSTRAINTS
        // ═══════════════════════════════════════════════════════════════════════
        put(m, ConfigMetadata.integer(
                "constraints.overnightRecoveryHours",
                "Giờ hồi phục sau trực đêm",
                "Số giờ tối thiểu giữa 2 ca trực 24/24. An toàn lao động: ≥ 12h.",
                ConfigMetadata.ConfigCategory.CONSTRAINTS,
                24, 12, 72
        ));

        put(m, ConfigMetadata.percentage(
                "constraints.greedyCoverageThreshold",
                "Ngưỡng coverage cho Greedy",
                "Thuật toán Greedy dừng sớm khi đạt ngưỡng coverage này.",
                ConfigMetadata.ConfigCategory.CONSTRAINTS,
                0.85, 0.5, 1.0
        ));

        put(m, ConfigMetadata.integer(
                "constraints.minStaffPerShift",
                "Tối thiểu nhân sự/ca",
                "Số nhân sự tối thiểu mỗi ca (0 = không giới hạn, chỉ giám sát).",
                ConfigMetadata.ConfigCategory.CONSTRAINTS,
                0, 0, 100
        ));

        put(m, ConfigMetadata.integer(
                "constraints.maxStaffPerShift",
                "Tối đa nhân sự/ca",
                "Số nhân sự tối đa mỗi ca (0 = không giới hạn).",
                ConfigMetadata.ConfigCategory.CONSTRAINTS,
                0, 0, 100
        ));

        put(m, ConfigMetadata.integer(
                "constraints.minShiftsPerStaff",
                "Tối thiểu ca/nhân sự",
                "Số ca tối thiểu mỗi nhân sự/tháng (0 = không giới hạn, chỉ giám sát).",
                ConfigMetadata.ConfigCategory.CONSTRAINTS,
                0, 0, 100
        ));

        put(m, ConfigMetadata.integer(
                "constraints.maxShiftsPerStaff",
                "Tối đa ca/nhân sự",
                "Số ca tối đa mỗi nhân sự/tháng (0 = không giới hạn).",
                ConfigMetadata.ConfigCategory.CONSTRAINTS,
                0, 0, 100
        ));

        // ═══════════════════════════════════════════════════════════════════════
        // PERFORMANCE
        // ═══════════════════════════════════════════════════════════════════════
        put(m, ConfigMetadata.integer(
                "performance.timeLimitSeconds",
                "Giới hạn thời gian (giây)",
                "Dừng thuật toán sau N giây bất kể kết quả.",
                ConfigMetadata.ConfigCategory.PERFORMANCE,
                60, 1, 3600
        ));

        put(m, ConfigMetadata.integer(
                "performance.candidateListSize",
                "Kích thước danh sách ứng viên",
                "Số ứng viên được giữ lại sau khi đánh giá. Lớn hơn = chọn lọc kỹ hơn.",
                ConfigMetadata.ConfigCategory.PERFORMANCE,
                50, 1, 200
        ));

        return Collections.unmodifiableMap(m);
    }

    private static ConfigMetadata.Option[] specialtyOptions() {
        return new ConfigMetadata.Option[]{
                new ConfigMetadata.Option("1", "Ngoại"),
                new ConfigMetadata.Option("2", "Nội"),
                new ConfigMetadata.Option("3", "Sản"),
                new ConfigMetadata.Option("4", "Nhi"),
                new ConfigMetadata.Option("5", "Mắt"),
                new ConfigMetadata.Option("6", "Răng")
        };
    }

    // L01 coverage bounds
    private static ConfigMetadata l01Bounds(String suffix, String label, String desc, int min, int max) {
        return ConfigMetadata.integer("coverage.l01." + suffix, label, desc,
                ConfigMetadata.ConfigCategory.COVERAGE, 0, min, max);
    }

    private static ConfigMetadata l02Bounds(String suffix, String label, String desc, int min, int max) {
        return ConfigMetadata.integer("coverage.l02." + suffix, label, desc,
                ConfigMetadata.ConfigCategory.COVERAGE, 0, min, max);
    }

    private static ConfigMetadata l03Bounds(String suffix, String label, String desc, int min, int max) {
        return ConfigMetadata.integer("coverage.l03." + suffix, label, desc,
                ConfigMetadata.ConfigCategory.COVERAGE, 0, min, max);
    }

    private static ConfigMetadata l04Bounds(String suffix, String label, String desc, int min, int max) {
        return ConfigMetadata.integer("coverage.l04." + suffix, label, desc,
                ConfigMetadata.ConfigCategory.COVERAGE, 0, min, max);
    }

    private static void put(Map<String, ConfigMetadata> m, ConfigMetadata meta) {
        m.put(meta.fieldPath(), meta);
    }

    private static Map<ConfigMetadata.ConfigCategory, List<ConfigMetadata>> buildByCategory() {
        Map<ConfigMetadata.ConfigCategory, List<ConfigMetadata>> result = new EnumMap<>(ConfigMetadata.ConfigCategory.class);
        for (ConfigMetadata.ConfigCategory cat : ConfigMetadata.ConfigCategory.values()) {
            result.put(cat, new ArrayList<>());
        }
        for (ConfigMetadata meta : FIELDS.values()) {
            result.get(meta.category()).add(meta);
        }
        // Make all lists unmodifiable
        Map<ConfigMetadata.ConfigCategory, List<ConfigMetadata>> unmod = new EnumMap<>(ConfigMetadata.ConfigCategory.class);
        for (Map.Entry<ConfigMetadata.ConfigCategory, List<ConfigMetadata>> e : result.entrySet()) {
            unmod.put(e.getKey(), Collections.unmodifiableList(e.getValue()));
        }
        return Collections.unmodifiableMap(unmod);
    }
}
