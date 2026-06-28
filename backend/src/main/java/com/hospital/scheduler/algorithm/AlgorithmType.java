package com.hospital.scheduler.algorithm;

/**
 * Các loại thuật toán xếp lịch.
 */
public enum AlgorithmType {
    /**
     * CSP Scheduler với MRV + Forward Checking
     * - Constraint Satisfaction Problem formulation
     * - MRV (Minimum Remaining Values) heuristic
     * - Forward Checking cho constraint propagation
     * - Backtracking search
     *
     * Constraints:
     * - C1: DIRECT_24H exclusive per day (only 1 per day)
     * - C2: REST day after DIRECT_24H blocks all shifts
     * - C3: THONG_TAM cannot overlap DIRECT_24H or REST
     * - C4: DICH_VU and CHUYEN_GIA cannot overlap same staff
     */
    CSP_MRV_FC("CSP-MRV-FC", "CSP Scheduler với MRV + Forward Checking - Tối ưu bằng constraint propagation"),
    
    /**
     * Genetic Algorithm - Tìm nghiệm tối ưu bằng tiến hóa quần thể.
     * - Population-based search
     * - Tournament selection
     * - Order crossover (OX)
     * - Constraint-aware fitness evaluation
     * - Multi-objective: conflicts + balance + coverage
     */
    GENETIC("GENETIC", "Thuật toán di truyền - Tìm nghiệm tối ưu bằng tiến hóa quần thể");

    private final String code;
    private final String description;

    AlgorithmType(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * Parse từ string (case-insensitive).
     * Mặc định luôn trả về CSP_MRV_FC.
     */
    public static AlgorithmType fromString(String value) {
        if (value == null) {
            return CSP_MRV_FC;
        }
        String upper = value.toUpperCase();
        for (AlgorithmType type : values()) {
            if (type.name().equals(upper) || type.code.equalsIgnoreCase(upper)) {
                return type;
            }
        }
        return CSP_MRV_FC;
    }
}
