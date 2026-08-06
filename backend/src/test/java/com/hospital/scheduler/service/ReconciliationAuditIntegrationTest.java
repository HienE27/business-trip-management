package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.request.AutoScheduleRequestDTO;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * RECONCILIATION AUDIT TEST — diagnostic only.
 *
 * <p>Calls {@code AutoSchedulingService.previewSchedule()} on period 2 with
 * {@code V10_LOCAL_SEARCH} to trigger the {@code logRequirementReconciliationAudit}
 * log added in the strict-cap refactor. Run this test, then read the log
 * line starting with {@code [RECON-AUDIT/before-dispatch]} to see:
 * <ul>
 *   <li>total rows + total requiredCount after adaptive L04</li>
 *   <li>per-shift sum (L01/L02/L03/L04)</li>
 *   <li>duplicate (date, shift, specialty) key count + samples</li>
 * </ul>
 *
 * <p>Disabled by default — enable manually when investigating overshoot.
 */
@SpringBootTest
@TestPropertySource(properties = {
        "spring.profiles.active=default",
        "logging.level.com.hospital.scheduler.service.AutoSchedulingService=INFO"
})
@DisplayName("RECON-AUDIT: log requirement reconciliation before dispatch")
class ReconciliationAuditIntegrationTest {

    @Autowired
    private AutoSchedulingService autoSchedulingService;

    @Test
    @DisplayName("Trigger V10 preview on period 2 and log requirement reconciliation")
    void triggerAuditLogForPeriod2() {
        AutoScheduleRequestDTO req = new AutoScheduleRequestDTO();
        req.setPeriodId(2);
        req.setAlgorithmType("V10_LOCAL_SEARCH");
        req.setSkipExisting(true);
        // Fire-and-forget: we only care about the log output, not the
        // result. A non-2xx response is acceptable — the audit log fires
        // BEFORE dispatch regardless of validity.
        try {
            autoSchedulingService.previewSchedule(req);
        } catch (Exception e) {
            // ignore — audit log already emitted
        }
    }
}
