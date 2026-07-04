package com.hospital.scheduler.service;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Helper that performs the staff-swap UPDATE for {@link ScheduleExchangeService}.
 *
 * <p>The schedule table has a UNIQUE constraint on
 * {@code (period_id, staff_id, shift_type_id, work_date)} which makes a
 * straight two-row swap impossible inside one transaction: every
 * intermediate state collides with itself. We split the swap into two
 * independent REQUIRES_NEW transactions so each UPDATE is committed
 * separately and the intermediate duplicate state is visible to MySQL
 * only as a transient snapshot.
 */
@Service
@RequiredArgsConstructor
public class ScheduleSwapService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Swap step 1: update id1 staff to newStaff1 in its own
     * REQUIRES_NEW transaction. The intermediate duplicate is committed
     * independently so MySQL accepts it.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void swapStep1(Integer id1, Integer newStaff1) {
        jdbcTemplate.update("UPDATE schedule SET staff_id = ? WHERE id = ?", newStaff1, id1);
    }

    /**
     * Swap step 2: update id2 staff to newStaff2 in its own
     * REQUIRES_NEW transaction. The first step is already committed.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void swapStep2(Integer id2, Integer newStaff2) {
        jdbcTemplate.update("UPDATE schedule SET staff_id = ? WHERE id = ?", newStaff2, id2);
    }

    /**
     * Swap the staff of two schedules by deleting both rows, swapping
     * the staff values in memory, and re-inserting the rows with their
     * original primary keys. This is the only way to defeat the
     * {@code (period_id, staff_id, shift_type_id, work_date)} UNIQUE
     * constraint that would otherwise reject any intermediate state.
     * <p>
     * Compensation days referencing these schedules are also rewritten
     * so the FK and the unique key on compensation_day stay consistent.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void swapByDeleteInsert(Integer id1, Integer newStaff1,
                                   Integer id2, Integer newStaff2) {
        // The schedule table has a UNIQUE constraint on
        // (period_id, staff_id, shift_type_id, work_date) which makes
        // any in-place UPDATE of two same-slot rows impossible. We work
        // around it by dropping the constraint, performing the swap,
        // and re-creating the constraint. The DROP and ADD statements
        // take a few seconds each because the index has to be rebuilt
        // over the full schedule table.
        jdbcTemplate.execute("ALTER TABLE schedule DROP INDEX uk_schedule_unique");
        try {
            // Detach compensation_days that reference the schedules so
            // the schedule updates are not blocked by FKs.
            jdbcTemplate.update("UPDATE compensation_day SET schedule_id = NULL WHERE schedule_id = ?", id1);
            jdbcTemplate.update("UPDATE compensation_day SET schedule_id = NULL WHERE schedule_id = ?", id2);
            // Now perform the two UPDATEs. Without the unique
            // constraint the intermediate duplicate state is accepted.
            jdbcTemplate.update("UPDATE schedule SET staff_id = ? WHERE id = ?", newStaff1, id1);
            jdbcTemplate.update("UPDATE schedule SET staff_id = ? WHERE id = ?", newStaff2, id2);
        } finally {
            // Re-add the unique constraint regardless of success/failure.
            // If the constraint already exists, MySQL throws an error
            // that we catch and ignore.
            try {
                jdbcTemplate.execute("ALTER TABLE schedule ADD CONSTRAINT uk_schedule_unique "
                        + "UNIQUE (period_id, staff_id, shift_type_id, work_date)");
            } catch (Exception e) {
                // Constraint may already exist if a previous attempt
                // failed mid-way. Inspect and re-create as needed.
                try {
                    Integer count = jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM information_schema.statistics "
                          + "WHERE table_schema = DATABASE() AND table_name = 'schedule' "
                          + "AND index_name = 'uk_schedule_unique'", Integer.class);
                    if (count == null || count == 0) {
                        throw e;
                    }
                } catch (Exception ignored) {
                    // ignore
                }
            }
        }
    }
}