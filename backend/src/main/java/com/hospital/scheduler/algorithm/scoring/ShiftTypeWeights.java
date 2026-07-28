package com.hospital.scheduler.algorithm.scoring;

/**
 * Shift-type weights for fairness scoring.
 *
 * L01 is a 24h shift (7:30→7:30 next day) that earns a compensation day,
 * so it occupies 2 calendar days. L02/L03/L04 are single-day shifts.
 * Weighting the balance score by these values prevents unfair scheduling
 * where one staff gets many L01 (and thus fewer free days) while another
 * gets the same raw count of L02/L03 with more free days.
 */
public final class ShiftTypeWeights {

    public static final int L01 = 2;
    public static final int L02 = 1;
    public static final int L03 = 1;
    public static final int L04 = 1;

    private ShiftTypeWeights() {}

    /**
     * Return the weight for a shift type id.
     * Unknown types default to 1.
     */
    public static int of(String shiftTypeId) {
        if (shiftTypeId == null) return 1;
        return switch (shiftTypeId) {
            case "L01" -> L01;
            case "L02" -> L02;
            case "L03" -> L03;
            case "L04" -> L04;
            default -> 1;
        };
    }
}
