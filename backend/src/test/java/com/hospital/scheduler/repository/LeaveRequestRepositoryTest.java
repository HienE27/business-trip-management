package com.hospital.scheduler.repository;

import com.hospital.scheduler.entity.LeaveRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@ActiveProfiles("test")
@SpringBootTest
class LeaveRequestRepositoryTest {

    @Autowired
    private LeaveRequestRepository leaveRequestRepository;

    @Test
    void shouldFindLeaveRequestsByStaffId() {
        assertThat(leaveRequestRepository.findByStaffId(1)).isNotNull();
    }

    @Test
    void shouldFindLeaveRequestsByStatus() {
        assertThat(leaveRequestRepository.findByStatus(LeaveRequest.LeaveStatus.PENDING)).isNotNull();
    }

    @Test
    void shouldFindPendingLeaveRequests() {
        assertThat(leaveRequestRepository.findByStatus(LeaveRequest.LeaveStatus.APPROVED)).isNotNull();
    }
}
