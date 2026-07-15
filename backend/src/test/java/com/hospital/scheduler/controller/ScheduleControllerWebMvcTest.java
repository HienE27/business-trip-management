package com.hospital.scheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.config.JacksonConfig;
import com.hospital.scheduler.config.PaginationConfig;
import com.hospital.scheduler.testsupport.MethodSecurityTestConfig;
import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.BulkL01Request;
import com.hospital.scheduler.dto.request.BulkScheduleRequest;
import com.hospital.scheduler.dto.request.OverrideConflictRequest;
import com.hospital.scheduler.dto.request.ScheduleRequest;
import com.hospital.scheduler.dto.response.BulkL01Response;
import com.hospital.scheduler.dto.response.BulkScheduleResponse;
import com.hospital.scheduler.dto.response.ConflictCheckResponse;
import com.hospital.scheduler.dto.response.ScheduleResponse;
import com.hospital.scheduler.exception.GlobalExceptionHandler;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.CompensationDayService;
import com.hospital.scheduler.service.ConflictDetectionService;
import com.hospital.scheduler.service.ScheduleDeleteService;
import com.hospital.scheduler.service.ScheduleService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link ScheduleController}.
 *
 * <p>Adds the {@link GlobalExceptionHandler} explicitly — {@code @WebMvcTest}
 * doesn't auto-import {@code @ControllerAdvice} beans from the main package
 * scan, so validation / access-denied / 404 mapping wouldn't fire without it.
 *
 * <p>{@link AuthContextService} is mocked because two endpoints
 * ({@code /staff/{id}}) call {@code @authContextService.isCurrentStaff(...)}
 * via SpEL; without the bean the slice context fails to start.
 *
 * <p>Skeleton: copy this file and add tests per endpoint. The two shown below
 * cover the canonical routing and the secured-with-permission case — both
 * regression-prone spots.
 */
@WebMvcTest(controllers = ScheduleController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, PaginationConfig.class, MethodSecurityTestConfig.class})
@DisplayName("ScheduleController - HTTP wiring slice")
class ScheduleControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean ScheduleService scheduleService;
    @MockitoBean ScheduleDeleteService scheduleDeleteService;
    @MockitoBean ConflictDetectionService conflictDetectionService;
    @MockitoBean CompensationDayService compensationDayService;
    @MockitoBean AuthContextService authContextService;
    @MockitoBean com.hospital.scheduler.config.RateLimitingFilter rateLimitingFilter;
    @MockitoBean com.hospital.scheduler.security.ClientIpResolver clientIpResolver;
    @MockitoBean com.hospital.scheduler.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean com.hospital.scheduler.security.JwtService jwtService;

    @org.junit.jupiter.api.BeforeEach
    void passThroughSecurityChain() throws Exception {
        org.mockito.Mockito.doAnswer(inv -> {
            ((jakarta.servlet.FilterChain) inv.getArgument(2))
                    .doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(jwtAuthenticationFilter).doFilter(any(), any(), any());
        org.mockito.Mockito.doAnswer(inv -> {
            ((jakarta.servlet.FilterChain) inv.getArgument(2))
                    .doFilter(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(rateLimitingFilter).doFilter(any(), any(), any());
    }

    @Test
    @DisplayName("GET /schedules/conflicts/check/{periodId}: authed user → 200 + body")
    void conflictCheck_pathVar() throws Exception {
        when(scheduleService.checkConflictsInPeriod(42))
                .thenReturn(new ConflictCheckResponse());

        mockMvc.perform(get("/api/v1/schedules/conflicts/check/42")
                        .with(user("alice").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        com.hospital.scheduler.security.Permissions.SCHEDULE_VIEW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(scheduleService).checkConflictsInPeriod(42);
    }

    @Test
    @DisplayName("GET /schedules/conflicts/check?periodId=N: query alias works")
    void conflictCheck_queryAlias() throws Exception {
        when(scheduleService.checkConflictsInPeriod(99)).thenReturn(new ConflictCheckResponse());

        mockMvc.perform(get("/api/v1/schedules/conflicts/check")
                        .param("periodId", "99")
                        .with(user("alice").authorities(
                                new org.springframework.security.core.authority.SimpleGrantedAuthority(
                                        com.hospital.scheduler.security.Permissions.SCHEDULE_VIEW))))
                .andExpect(status().isOk());

        verify(scheduleService).checkConflictsInPeriod(99);
    }

    @Test
    @DisplayName("GET /schedules/conflicts/check/{id} without permission → 403")
    void conflictCheck_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/schedules/conflicts/check/42")
                        .with(user("bob"))) // no SCHEDULE_VIEW authority
                .andExpect(status().isForbidden());
    }

    // ── TODOs (ponytail): add the rest as needed ─────────────────────────
    //   Edge: bean-validation on ScheduleRequest → 400 via GlobalExceptionHandler
    // ── END REMOVED ──────────────────────────────────────────────────────

    // ════════════════════════════════════════════════════════════════════
    //  POST /schedules  — create
    // ════════════════════════════════════════════════════════════════════

    private static ScheduleRequest validRequest() {
        return ScheduleRequest.builder()
                .periodId(1)
                .workDate(LocalDate.of(2026, 6, 1))
                .staffId(42)
                .shiftTypeId("L01")
                .build();
    }

    private static ScheduleResponse fakeResponse(int id) {
        return ScheduleResponse.builder()
                .id(id)
                .periodId(1)
                .workDate(LocalDate.of(2026, 6, 1))
                .hasConflict(false)
                .build();
    }

    @Test
    @DisplayName("POST /schedules: 201 CREATED, calls service and returns wrapped body")
    void createSchedule_success() throws Exception {
        when(scheduleService.createSchedule(any(ScheduleRequest.class)))
                .thenReturn(fakeResponse(7));

        mockMvc.perform(post("/api/v1/schedules")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_CREATE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(7))
                .andExpect(jsonPath("$.message").value("Tạo lịch thành công"));

        verify(scheduleService).createSchedule(any(ScheduleRequest.class));
    }

    @Test
    @DisplayName("POST /schedules without SCHEDULE_CREATE → 403")
    void createSchedule_forbidden() throws Exception {
        mockMvc.perform(post("/api/v1/schedules")
                        .with(user("bob").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_VIEW)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    // ════════════════════════════════════════════════════════════════════
    //  PUT /schedules/{id}  — update
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /schedules/{id}: 200 OK, returns updated schedule")
    void updateSchedule_success() throws Exception {
        when(scheduleService.updateSchedule(eq(5), any(ScheduleRequest.class)))
                .thenReturn(fakeResponse(5));

        mockMvc.perform(put("/api/v1/schedules/5")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_UPDATE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(5))
                .andExpect(jsonPath("$.message").value("Cập nhật lịch thành công"));

        verify(scheduleService).updateSchedule(eq(5), any(ScheduleRequest.class));
    }

    @Test
    @DisplayName("PUT /schedules/{id} without SCHEDULE_UPDATE → 403")
    void updateSchedule_forbidden() throws Exception {
        mockMvc.perform(put("/api/v1/schedules/5")
                        .with(user("bob").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_VIEW)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validRequest())))
                .andExpect(status().isForbidden());
    }

    // ════════════════════════════════════════════════════════════════════
    //  POST /schedules/bulk  — bulkCreateSchedules
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /schedules/bulk: 200 OK, delegates to service with shiftTypeId param")
    void bulkCreateSchedules_success() throws Exception {
        BulkScheduleRequest bulkReq = BulkScheduleRequest.builder()
                .periodId(1)
                .entries(List.of(
                        BulkScheduleRequest.BulkScheduleEntry.builder()
                                .workDate(LocalDate.of(2026, 6, 1)).staffId(1).build(),
                        BulkScheduleRequest.BulkScheduleEntry.builder()
                                .workDate(LocalDate.of(2026, 6, 2)).staffId(2).build()))
                .build();
        BulkScheduleResponse bulkResp = BulkScheduleResponse.builder()
                .successCount(2).totalRequested(2).build();

        when(scheduleService.bulkCreateSchedules(any(BulkScheduleRequest.class), eq("L02")))
                .thenReturn(bulkResp);

        mockMvc.perform(post("/api/v1/schedules/bulk")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_CREATE)))
                        .param("shiftTypeId", "L02")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(bulkReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.successCount").value(2))
                .andExpect(jsonPath("$.data.totalRequested").value(2));
    }

    // ════════════════════════════════════════════════════════════════════
    //  POST /schedules/bulk-l01  — createBulkL01
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("POST /schedules/bulk-l01: 200 OK")
    void bulkCreateL01_success() throws Exception {
        BulkL01Request req = BulkL01Request.builder()
                .periodId(1)
                .entries(List.of(
                        BulkL01Request.L01Entry.builder()
                                .workDate(LocalDate.of(2026, 6, 1)).staffId(1).build()))
                .build();
        BulkL01Response resp = BulkL01Response.builder()
                .successCount(1).totalCount(1).build();

        when(scheduleService.createBulkL01(any(BulkL01Request.class))).thenReturn(resp);

        mockMvc.perform(post("/api/v1/schedules/bulk-l01")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_CREATE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.successCount").value(1));
    }

    // ════════════════════════════════════════════════════════════════════
    //  DELETE /schedules/{id}  — delete
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("DELETE /schedules/{id}: 200 OK")
    void deleteSchedule_success() throws Exception {
        mockMvc.perform(delete("/api/v1/schedules/5")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_DELETE))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        verify(scheduleDeleteService).deleteSchedule(5);
    }

    @Test
    @DisplayName("DELETE /schedules/{id} without SCHEDULE_DELETE → 403")
    void deleteSchedule_forbidden() throws Exception {
        mockMvc.perform(delete("/api/v1/schedules/5")
                        .with(user("bob").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_VIEW))))
                .andExpect(status().isForbidden());
    }

    // ════════════════════════════════════════════════════════════════════
    //  GET /schedules/period/{periodId}  — list-by-period
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /schedules/period/{id}: 200 OK, empty list")
    void getSchedulesByPeriod_empty() throws Exception {
        when(scheduleService.getSchedulesByPeriod(3)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/schedules/period/3")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_VIEW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty());

        verify(scheduleService).getSchedulesByPeriod(3);
    }

    @Test
    @DisplayName("GET /schedules/period/{id}: 200 OK, populated list")
    void getSchedulesByPeriod_populated() throws Exception {
        when(scheduleService.getSchedulesByPeriod(3)).thenReturn(List.of(
                fakeResponse(1), fakeResponse(2)));

        mockMvc.perform(get("/api/v1/schedules/period/3")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_VIEW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    // ════════════════════════════════════════════════════════════════════
    //  GET /schedules/{id}  — get by id
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /schedules/{id}: 200 OK")
    void getScheduleById_success() throws Exception {
        when(scheduleService.getScheduleById(7)).thenReturn(fakeResponse(7));

        mockMvc.perform(get("/api/v1/schedules/7")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_VIEW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(7));
    }

    // ════════════════════════════════════════════════════════════════════
    //  PUT /schedules/{id}/override  — override conflict
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("PUT /schedules/{id}/override: 200 OK")
    void overrideConflict_success() throws Exception {
        when(scheduleService.overrideConflict(eq(5), anyString())).thenReturn(fakeResponse(5));

        OverrideConflictRequest body = OverrideConflictRequest.builder()
                .reason("Cần thiết do thiếu nhân sự").build();

        mockMvc.perform(put("/api/v1/schedules/5/override")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_UPDATE)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("Đã ghi nhận override xung đột"));
    }

    // ════════════════════════════════════════════════════════════════════
    //  GET /schedules/compensation-days/{periodId}
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /schedules/compensation-days/{id}: 200 OK")
    void getCompensationDays_success() throws Exception {
        when(compensationDayService.getCompensationDaysByPeriod(3)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/schedules/compensation-days/3")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_VIEW))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ════════════════════════════════════════════════════════════════════
    //  GET /schedules/expert-clinic
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /schedules/expert-clinic: 200 OK")
    void getExpertClinicSchedules_success() throws Exception {
        when(scheduleService.getExpertClinicSchedules(1, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/schedules/expert-clinic")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_VIEW)))
                        .param("periodId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ════════════════════════════════════════════════════════════════════
    //  GET /schedules/expert-clinic/weekly
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /schedules/expert-clinic/weekly: 200 OK")
    void getExpertClinicWeeklyView_success() throws Exception {
        when(scheduleService.getExpertClinicWeeklyView(eq(1), any(), any()))
                .thenReturn(new com.hospital.scheduler.dto.response.ExpertClinicWeeklyResponse());

        mockMvc.perform(get("/api/v1/schedules/expert-clinic/weekly")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_VIEW)))
                        .param("periodId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    // ════════════════════════════════════════════════════════════════════
    //  GET /schedules/replacements/{periodId}
    // ════════════════════════════════════════════════════════════════════

    @Test
    @DisplayName("GET /schedules/replacements/{id}: 200 OK")
    void findReplacements_success() throws Exception {
        when(scheduleService.findReplacements(eq(1), any(), anyString(), anyInt(), anyInt()))
                .thenReturn(List.of());

        mockMvc.perform(get("/api/v1/schedules/replacements/1")
                        .with(user("alice").authorities(
                                new SimpleGrantedAuthority(Permissions.SCHEDULE_UPDATE)))
                        .param("workDate", "2026-06-01")
                        .param("shiftTypeId", "L01")
                        .param("originalStaffId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }
}