package com.hospital.scheduler.service;

import com.hospital.scheduler.dto.response.ConflictCheckResponse;
import com.hospital.scheduler.entity.Schedule;
import com.hospital.scheduler.entity.ScheduleConflict;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.entity.Staff;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("ConflictBroadcastService Tests")
class ConflictBroadcastServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @InjectMocks
    private ConflictBroadcastService broadcastService;

    @Captor
    private ArgumentCaptor<Object> payloadCaptor;

    private Staff testStaff;
    private Schedule testSchedule;
    private SchedulePeriod testPeriod;
    private ScheduleConflict testConflict;

    @BeforeEach
    void setUp() {
        testPeriod = SchedulePeriod.builder()
                .id(1).periodName("Tháng 6/2026")
                .startDate(LocalDate.of(2026, 6, 1))
                .endDate(LocalDate.of(2026, 6, 30))
                .status(SchedulePeriod.PeriodStatus.DRAFT)
                .build();

        testStaff = Staff.builder()
                .id(1).username("nurse1").fullName("Nguyen Van A").isActive(true)
                .build();

        testSchedule = Schedule.builder()
                .id(100).period(testPeriod).workDate(LocalDate.of(2026, 6, 15))
                .staff(testStaff).shiftType(new com.hospital.scheduler.entity.ShiftType() {{
                    setId("L01");
                    setName("Lịch trực 24/24");
                }}).hasConflict(true)
                .build();

        testConflict = ScheduleConflict.builder()
                .id(1).schedule(testSchedule)
                .conflictType(ScheduleConflict.ConflictType.OTHER)
                .description("L01 và L02 không thể cùng ngày")
                .isResolved(false)
                .build();
    }

    @Test
    @DisplayName("broadcastConflict -> sends message to /topic/conflicts topic")
    void broadcastConflict_shouldSendToTopic() {
        ConflictCheckResponse.ConflictDetail detail = ConflictCheckResponse.ConflictDetail.builder()
                .scheduleId(100)
                .staffName("Nguyen Van A")
                .workDate(LocalDate.of(2026, 6, 15))
                .shiftTypeId("L01")
                .shiftTypeName("Lịch trực 24/24")
                .conflictReasons(List.of("L01 và L02 không thể cùng ngày"))
                .build();

        broadcastService.broadcastConflict(testConflict, detail);

        verify(messagingTemplate).convertAndSend(eq("/topic/conflicts"), payloadCaptor.capture(), anyMap());
        Object payload = payloadCaptor.getValue();
        assertThat(payload).isInstanceOf(java.util.Map.class);
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) payload;
        assertThat(map.get("eventType")).isEqualTo("CONFLICT_DETECTED");
        assertThat(map.get("conflictId")).isEqualTo(1);
        assertThat(map.get("scheduleId")).isEqualTo(100);
        assertThat(map.get("staffName")).isEqualTo("Nguyen Van A");
    }

    @Test
    @DisplayName("broadcastConflict with null detail -> still sends with basic fields")
    void broadcastConflict_nullDetail_shouldStillSend() {
        broadcastService.broadcastConflict(testConflict, null);

        verify(messagingTemplate).convertAndSend(eq("/topic/conflicts"), payloadCaptor.capture(), anyMap());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) payloadCaptor.getValue();
        assertThat(map.get("eventType")).isEqualTo("CONFLICT_DETECTED");
        assertThat(map.get("conflictId")).isEqualTo(1);
        assertThat(map.get("scheduleId")).isEqualTo(100);
    }

    @Test
    @DisplayName("broadcastConflictBatch -> sends batch with periodId and totalConflicts")
    void broadcastConflictBatch_shouldIncludePeriodAndCount() {
        List<ConflictCheckResponse.ConflictDetail> details = List.of(
                ConflictCheckResponse.ConflictDetail.builder()
                        .scheduleId(100).staffName("Nguyen Van A")
                        .workDate(LocalDate.of(2026, 6, 15))
                        .shiftTypeId("L01").shiftTypeName("Lịch trực 24/24")
                        .conflictReasons(List.of("Reason 1"))
                        .build(),
                ConflictCheckResponse.ConflictDetail.builder()
                        .scheduleId(101).staffName("Tran Thi B")
                        .workDate(LocalDate.of(2026, 6, 16))
                        .shiftTypeId("L02").shiftTypeName("Lịch thông tầm")
                        .conflictReasons(List.of("Reason 2"))
                        .build()
        );

        broadcastService.broadcastConflictBatch(details, 1);

        verify(messagingTemplate).convertAndSend(eq("/topic/conflicts"), payloadCaptor.capture(), anyMap());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) payloadCaptor.getValue();
        assertThat(map.get("eventType")).isEqualTo("CONFLICT_BATCH");
        assertThat(map.get("periodId")).isEqualTo(1);
        assertThat(map.get("totalConflicts")).isEqualTo(2);
    }

    @Test
    @DisplayName("broadcastConflictResolved -> sends resolved event")
    void broadcastConflictResolved_shouldSendResolvedEvent() {
        broadcastService.broadcastConflictResolved(1, 100);

        verify(messagingTemplate).convertAndSend(eq("/topic/conflicts"), payloadCaptor.capture(), anyMap());
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> map = (java.util.Map<String, Object>) payloadCaptor.getValue();
        assertThat(map.get("eventType")).isEqualTo("CONFLICT_RESOLVED");
        assertThat(map.get("conflictId")).isEqualTo(1);
        assertThat(map.get("scheduleId")).isEqualTo(100);
    }
}
