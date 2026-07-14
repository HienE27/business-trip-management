package com.hospital.scheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.request.SchedulePeriodRequest;
import com.hospital.scheduler.dto.response.SchedulePeriodResponse;
import com.hospital.scheduler.config.JacksonConfig;
import com.hospital.scheduler.config.PaginationConfig;
import com.hospital.scheduler.testsupport.MethodSecurityTestConfig;
import com.hospital.scheduler.entity.SchedulePeriod;
import com.hospital.scheduler.exception.GlobalExceptionHandler;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.ConflictDetectionService;
import com.hospital.scheduler.service.SchedulePeriodService;
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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Slice test for {@link SchedulePeriodController}.
 * Skeleton — extend by endpoint.
 */
@WebMvcTest(controllers = SchedulePeriodController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, PaginationConfig.class})
@DisplayName("SchedulePeriodController - HTTP wiring slice")
class SchedulePeriodControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    @MockitoBean SchedulePeriodService periodService;
    @MockitoBean ConflictDetectionService conflictDetectionService;
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
        return user("alice").authorities(new SimpleGrantedAuthority(Permissions.PERIOD_VIEW),
                                          new SimpleGrantedAuthority(Permissions.PERIOD_CREATE),
                                          new SimpleGrantedAuthority(Permissions.PERIOD_PUBLISH),
                                          new SimpleGrantedAuthority(Permissions.PERIOD_DELETE));
    }

    @Test
    @DisplayName("GET /periods: returns list wrapped in ApiResponse")
    void getAllPeriods() throws Exception {
        when(periodService.getAllPeriods()).thenReturn(List.of(new SchedulePeriodResponse()));

        mockMvc.perform(get("/api/v1/periods").with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @DisplayName("POST /periods: 201 CREATED with body")
    void createPeriod() throws Exception {
        SchedulePeriodResponse resp = new SchedulePeriodResponse();
        when(periodService.createPeriod(any(SchedulePeriodRequest.class), eq(7)))
                .thenReturn(resp);

        SchedulePeriodRequest body = new SchedulePeriodRequest();
        body.setStartDate(java.time.LocalDate.of(2026, 1, 1));
        body.setEndDate(java.time.LocalDate.of(2026, 1, 31));
        body.setPeriodName("Kỳ 2026-01");

        mockMvc.perform(post("/api/v1/periods")
                        .param("generatedById", "7")
                        .with(asManager())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("DELETE /periods/{id}: 200, body null, service invoked")
    void deletePeriod() throws Exception {
        mockMvc.perform(delete("/api/v1/periods/123").with(asManager()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));

        org.mockito.Mockito.verify(periodService).deletePeriod(123);
    }

    // ── TODOs (ponytail) ────────────────────────────────────────────────
    //   POST /periods/{id}/publish       — happy path + already-published → 409
    //   GET  /periods/{id}/publish/dry-run
    //   POST /periods/{id}/archive
    //   Bean-validation on SchedulePeriodRequest → 400
}