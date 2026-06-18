package com.hospital.scheduler.dto.response;

import lombok.*;

/**
 * Daily schedule counts for a single staff member. Used by the
 * week/month aggregation endpoint when a {@code staffId} filter is supplied.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StaffDailyCount {
    private Integer staffId;
    private String staffFullName;
    private long scheduleCount;
}
