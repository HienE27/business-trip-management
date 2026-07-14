package com.hospital.scheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.config.JacksonConfig;
import com.hospital.scheduler.config.PaginationConfig;
import com.hospital.scheduler.testsupport.MethodSecurityTestConfig;
import com.hospital.scheduler.dto.request.LeaveRequestDTO;
import com.hospital.scheduler.dto.response.LeaveRequestResponse;
import com.hospital.scheduler.exception.GlobalExceptionHandler;
import com.hospital.scheduler.security.AuthContextService;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.LeaveRequestService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link LeaveRequestController}.
 * Skeleton — extend by endpoint. Three of the twelve endpoints shown below;
 * the rest are TODOs at the bottom.
 */
@WebMvcTest(controllers = LeaveRequestController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, PaginationConfig.class, MethodSecurityTestConfig.class})
@DisplayName("LeaveRequestController - HTTP wiring slice")
class LeaveRequestControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean LeaveRequestService leaveRequestService;
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

    private org.springframework.test.web.servlet.request.RequestPostProcessor asManager() {
        return user("alice").authorities(new SimpleGrantedAuthority(Permissions.LEAVE_VIEW),
                                          new SimpleGrantedAuthority(Permissions.LEAVE_APPROVE),
                                          new SimpleGrantedAuthority(Permissions.LEAVE_CREATE));
    }

    @Test
    @DisplayName("GET /leave-requests/status-counts: returns Map wrapped in ApiResponse")
    void statusCounts() throws Exception {
        when(leaveRequestService.getStatusCounts()).thenReturn(Map.of("PENDING", 3L, "APPROVED", 5L));

        mockMvc.perform(get("/api/v1/leave-requests/status-counts").with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.PENDING").value(3))
                .andExpect(jsonPath("$.data.APPROVED").value(5));
    }

    @Test
    @DisplayName("PUT /leave-requests/{id}/approve: 200, reviewer id forwarded to service")
    void approveRequest() throws Exception {
        LeaveRequestResponse resp = new LeaveRequestResponse();
        when(leaveRequestService.approveLeaveRequest(eq(11), eq(7), any())).thenReturn(resp);

        mockMvc.perform(put("/api/v1/leave-requests/11/approve")
                        .param("reviewerId", "7")
                        .param("reviewNote", "ok")
                        .with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("POST /leave-requests/staff/{id}: 201 CREATED")
    void createLeaveRequest() throws Exception {
        LeaveRequestDTO dto = new LeaveRequestDTO();
        dto.setStartDate(java.time.LocalDate.of(2026, 2, 1));
        dto.setEndDate(java.time.LocalDate.of(2026, 2, 3));
        dto.setReason("personal");

        when(leaveRequestService.createLeaveRequest(eq(7), any(LeaveRequestDTO.class)))
                .thenReturn(new LeaveRequestResponse());

        mockMvc.perform(post("/api/v1/leave-requests/staff/7")
                        .with(asManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isCreated());
    }

    // ── TODOs (ponytail) ────────────────────────────────────────────────
    //   PUT /leave-requests/{id}/reject  — same shape as approve
    //   PUT /leave-requests/{id}/cancel  — self-cancel path (LEAVE_CANCEL_SELF)
    //   GET /leave-requests/staff/{id}   — requireSelfOrManager interaction with AuthContextService
    //   GET /leave-requests/{id}/replacements
    //   Bean-validation on LeaveRequestDTO → 400 (start > end)
}