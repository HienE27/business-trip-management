package com.hospital.scheduler.dto.response;

import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AuditHistorySummaryResponse {
    /** Total records matching the active filter (or DB-wide when no date filter). */
    private long total;
    /** Records with action == "CREATE" (mapped from INSERT). */
    private long create;
    /** Records with action == "UPDATE". */
    private long update;
    /** Records with action == "DELETE". */
    private long delete;
}