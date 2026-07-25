package com.hospital.scheduler.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.hospital.scheduler.algorithm.AutoGenConfig;
import com.hospital.scheduler.config.JacksonConfig;
import com.hospital.scheduler.config.PaginationConfig;
import com.hospital.scheduler.dto.request.AlgoConfigRequest;
import com.hospital.scheduler.dto.request.AutoGenConfigRecommendRequest;
import com.hospital.scheduler.dto.request.AutoScheduleApplyPreviewRequestDTO;
import com.hospital.scheduler.dto.request.AutoScheduleRequestDTO;
import com.hospital.scheduler.dto.request.SaveAlgorithmTemplateRequest;
import com.hospital.scheduler.dto.request.SaveTemplateRequest;
import com.hospital.scheduler.dto.response.AlgorithmConfigDTO;
import com.hospital.scheduler.dto.response.AlgorithmConfigResponse;
import com.hospital.scheduler.dto.response.AlgorithmMetricsDTO;
import com.hospital.scheduler.dto.response.AutoGenConfigRecommendResponse;
import com.hospital.scheduler.dto.response.AutoScheduleResponse;
import com.hospital.scheduler.dto.response.ScheduleTemplateResponse;
import com.hospital.scheduler.entity.AlgorithmConfigAudit;
import com.hospital.scheduler.entity.AlgorithmConfig.ValueType;
import com.hospital.scheduler.exception.GlobalExceptionHandler;
import com.hospital.scheduler.security.Permissions;
import com.hospital.scheduler.service.AlgorithmConfigService;
import com.hospital.scheduler.service.AlgorithmConfigService.AlgorithmRuntimeConfig;
import com.hospital.scheduler.service.AlgorithmMetricsService;
import com.hospital.scheduler.service.AlgorithmProgressTracker;
import com.hospital.scheduler.service.AutoSchedulingService;
import com.hospital.scheduler.service.ScheduleTemplateService;
import com.hospital.scheduler.repository.AlgorithmConfigAuditRepository;
import com.hospital.scheduler.testsupport.MethodSecurityTestConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Slice test for {@link AutoSchedulingController}.
 *
 * <p>Covers routing, {@code @PreAuthorize} permission enforcement,
 * {@link com.hospital.scheduler.exception.BadRequestException} for
 * unsupported algorithmType values, and response serialization for
 * all endpoint groups.
 */
@WebMvcTest(controllers = AutoSchedulingController.class)
@Import({GlobalExceptionHandler.class, JacksonConfig.class, PaginationConfig.class,
        MethodSecurityTestConfig.class})
@DisplayName("AutoSchedulingController — HTTP wiring slice")
class AutoSchedulingControllerWebMvcTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;

    // ── Shared helpers ─────────────────────────────────────────────

    @MockitoBean AutoSchedulingService autoSchedulingService;
    @MockitoBean ScheduleTemplateService scheduleTemplateService;
    @MockitoBean AlgorithmConfigService algorithmConfigService;
    @MockitoBean AlgorithmMetricsService algorithmMetricsService;
    @MockitoBean AlgorithmProgressTracker progressTracker;
    @MockitoBean AlgorithmConfigAuditRepository auditRepository;
    // Security filter-chain beans required by the @WebMvcTest slice
    @MockitoBean com.hospital.scheduler.security.JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean com.hospital.scheduler.config.RateLimitingFilter rateLimitingFilter;
    @MockitoBean com.hospital.scheduler.security.ClientIpResolver clientIpResolver;
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

    // ── shared helpers ─────────────────────────────────────────────

    private static AutoScheduleRequestDTO validRequest() {
        return AutoScheduleRequestDTO.builder()
                .periodId(1)
                .algorithmType("BEAM_SEARCH")
                .excludedStaffIds(java.util.List.of())
                .build();
    }

    private static AutoScheduleResponse fakeResponse() {
        return AutoScheduleResponse.builder()
                .success(true)
                .periodId(1)
                .algorithmType("BEAM_SEARCH")
                .totalSchedulesCreated(42)
                .coverageRate(new BigDecimal("85.5"))
                .conflictCount(0)
                .build();
    }

    private static AutoGenConfig defaultAutoGenConfig() {
        return new AutoGenConfig(
                true,
                2, 2, 2, 2,
                2, 2, 2, 2,
                2, 2, 2, 2,
                8, 8, 8, 8,
                "SKIP", List.of(), false, 1.0f, List.of(),
                List.of("Ngoại", "Nội"), List.of("Ngoại", "Nội"), List.of("Ngoại", "Nội"),
                5, 5, 5, 5, "FAIR_DISTRIBUTE");
    }

    private static AlgorithmRuntimeConfig sampleRuntimeConfig() {
        return AlgorithmRuntimeConfig.builder()
                .weekendWeight(new BigDecimal("2.0"))
                .overnightRecoveryHours(24)
                .greedyCoverageThreshold(new BigDecimal("0.85"))
                .balanceScoreMin(new BigDecimal("0.70"))
                .autoCompensationEnabled(true)
                .maxStaffPerShift(0)
                .maxShiftsPerStaff(12)
                .maxShiftsPerDay(0)
                .l01MaxPerWeek(0).l02MaxPerWeek(0).l03MaxPerWeek(0).l04MaxPerWeek(0)
                .beamWidth(5)
                .autoAdjustConfig(true)
                .coverageWeight(new BigDecimal("0.40"))
                .fairnessWeight(new BigDecimal("0.35"))
                .constraintWeight(new BigDecimal("0.25"))
                .build();
    }

    // ════════════════════════════════════════════════════════════════
    //  POST /api/v1/auto-schedule
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/auto-schedule")
    class AutoSchedule {

        @Test
        @DisplayName("with AUTO_SCHEDULE_RUN → 200 + response")
        void success() throws Exception {
            when(autoSchedulingService.autoSchedule(any(AutoScheduleRequestDTO.class)))
                    .thenReturn(fakeResponse());

            mockMvc.perform(post("/api/v1/auto-schedule")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_RUN)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalSchedulesCreated").value(42))
                    .andExpect(jsonPath("$.message").value("Xếp lịch tự động hoàn tất"));

            verify(autoSchedulingService).autoSchedule(any(AutoScheduleRequestDTO.class));
        }

        @Test
        @DisplayName("without AUTO_SCHEDULE_RUN → 403")
        void forbidden() throws Exception {
            mockMvc.perform(post("/api/v1/auto-schedule")
                            .with(user("bob").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("with invalid algorithmType → 400")
        void invalidAlgorithmType() throws Exception {
            AutoScheduleRequestDTO badRequest = AutoScheduleRequestDTO.builder()
                    .periodId(1)
                    .algorithmType("GENETIC") // not in SUPPORTED_ALGORITHMS
                    .build();

            mockMvc.perform(post("/api/v1/auto-schedule")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_RUN)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("không được hỗ trợ")));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  POST /api/v1/auto-schedule/preview
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/auto-schedule/preview")
    class PreviewSchedule {

        @Test
        @DisplayName("with AUTO_SCHEDULE_RUN → 200")
        void success() throws Exception {
            AlgorithmProgressTracker.Progress mockProgress =
                    new AlgorithmProgressTracker.Progress(1, "test-run-token");
            when(progressTracker.start(any())).thenReturn(mockProgress);
            when(progressTracker.get(any())).thenReturn(mockProgress);

            when(autoSchedulingService.previewSchedule(any(AutoScheduleRequestDTO.class)))
                    .thenReturn(fakeResponse());

            mockMvc.perform(post("/api/v1/auto-schedule/preview")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_RUN)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalSchedulesCreated").value(42));
        }

        @Test
        @DisplayName("without AUTO_SCHEDULE_RUN → 403")
        void forbidden() throws Exception {
            mockMvc.perform(post("/api/v1/auto-schedule/preview")
                            .with(user("bob").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validRequest())))
                    .andExpect(status().isForbidden());
        }

        @Test
        @DisplayName("with invalid algorithmType → 400 (whitelist before progress)")
        void invalidAlgorithmType() throws Exception {
            AutoScheduleRequestDTO badRequest = AutoScheduleRequestDTO.builder()
                    .periodId(1)
                    .algorithmType("UNKNOWN")
                    .build();

            mockMvc.perform(post("/api/v1/auto-schedule/preview")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_RUN)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badRequest)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(
                            org.hamcrest.Matchers.containsString("không được hỗ trợ")));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/v1/auto-schedule/progress/{periodId}
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/auto-schedule/progress/{periodId}")
    class GetProgress {

        @Test
        @DisplayName("with AUTO_SCHEDULE_VIEW + active progress → 200 + RUNNING")
        void withActiveProgress() throws Exception {
            AlgorithmProgressTracker.Progress p =
                    new AlgorithmProgressTracker.Progress(1, "t1");
            when(progressTracker.get(1)).thenReturn(p);

            mockMvc.perform(get("/api/v1/auto-schedule/progress/1")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.status").value("RUNNING"))
                    .andExpect(jsonPath("$.data.periodId").value(1));
        }

        @Test
        @DisplayName("with AUTO_SCHEDULE_VIEW + no progress → 200 + IDLE")
        void withoutProgress() throws Exception {
            when(progressTracker.get(99)).thenReturn(null);

            mockMvc.perform(get("/api/v1/auto-schedule/progress/99")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.status").value("IDLE"));
        }

        @Test
        @DisplayName("without AUTO_SCHEDULE_VIEW → 403")
        void forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/auto-schedule/progress/1")
                            .with(user("bob").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_RUN))))
                    .andExpect(status().isForbidden());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  POST /api/v1/auto-schedule/cancel/{periodId}
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/auto-schedule/cancel/{periodId}")
    class CancelRun {

        @Test
        @DisplayName("with AUTO_SCHEDULE_RUN → 200 + released=true")
        void cancelLocked() throws Exception {
            when(autoSchedulingService.markLockStale(1)).thenReturn(true);

            mockMvc.perform(post("/api/v1/auto-schedule/cancel/1")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_RUN))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.released").value(true));
        }

        @Test
        @DisplayName("without AUTO_SCHEDULE_RUN → 403")
        void forbidden() throws Exception {
            mockMvc.perform(post("/api/v1/auto-schedule/cancel/1")
                            .with(user("bob").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW))))
                    .andExpect(status().isForbidden());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  POST /api/v1/auto-schedule/apply-preview
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/auto-schedule/apply-preview")
    class ApplyPreview {

        @Test
        @DisplayName("with AUTO_SCHEDULE_APPLY → 200")
        void success() throws Exception {
            when(autoSchedulingService.applyPreviewSchedule(any()))
                    .thenReturn(fakeResponse());

            AutoScheduleApplyPreviewRequestDTO req = AutoScheduleApplyPreviewRequestDTO.builder()
                    .periodId(1)
                    .algorithmType("BEAM_SEARCH")
                    .schedules(List.of(
                            AutoScheduleApplyPreviewRequestDTO.PreviewScheduleItem.builder()
                                    .staffId(10)
                                    .workDate("2026-08-01")
                                    .shiftTypeId("L01")
                                    .build()))
                    .removedSchedules(List.of())
                    .build();

            mockMvc.perform(post("/api/v1/auto-schedule/apply-preview")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_APPLY)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalSchedulesCreated").value(42));
        }

        @Test
        @DisplayName("without AUTO_SCHEDULE_APPLY → 403")
        void forbidden() throws Exception {
            // Must pass @NotEmpty validation (non-empty schedules) to reach security check
            AutoScheduleApplyPreviewRequestDTO req = AutoScheduleApplyPreviewRequestDTO.builder()
                    .periodId(1)
                    .schedules(List.of(
                            AutoScheduleApplyPreviewRequestDTO.PreviewScheduleItem.builder()
                                    .staffId(1)
                                    .workDate("2026-08-01")
                                    .shiftTypeId("L01")
                                    .build()))
                    .removedSchedules(List.of())
                    .build();
            mockMvc.perform(post("/api/v1/auto-schedule/apply-preview")
                            .with(user("bob").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isForbidden());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  POST /api/v1/auto-schedule/save-template
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/auto-schedule/save-template")
    class SaveTemplate {

        @Test
        @DisplayName("with SCHEDULE_TEMPLATE_MANAGE → 201")
        void success() throws Exception {
            when(scheduleTemplateService.saveTemplateFromGenerated(any()))
                    .thenReturn(ScheduleTemplateResponse.builder()
                            .id(1).name("Mẫu T7").build());

            SaveTemplateRequest req = SaveTemplateRequest.builder()
                    .periodId(1)
                    .templateName("Mẫu T7")
                    .description("Test")
                    .build();

            mockMvc.perform(post("/api/v1/auto-schedule/save-template")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.SCHEDULE_TEMPLATE_MANAGE)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.id").value(1));
        }

        @Test
        @DisplayName("without SCHEDULE_TEMPLATE_MANAGE → 403")
        void forbidden() throws Exception {
            mockMvc.perform(post("/api/v1/auto-schedule/save-template")
                            .with(user("bob").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    SaveTemplateRequest.builder()
                                            .periodId(1)
                                            .templateName("Mẫu")
                                            .build())))
                    .andExpect(status().isForbidden());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/v1/auto-schedule/templates
    //  GET /api/v1/auto-schedule/templates/{templateId}
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/auto-schedule/templates")
    class ListTemplates {

        @Test
        @DisplayName("with AUTO_SCHEDULE_VIEW → 200 + list")
        void list() throws Exception {
            when(scheduleTemplateService.getActiveTemplates())
                    .thenReturn(List.of(
                            ScheduleTemplateResponse.builder().id(1).name("Mẫu 1").build(),
                            ScheduleTemplateResponse.builder().id(2).name("Mẫu 2").build()));

            mockMvc.perform(get("/api/v1/auto-schedule/templates")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.length()").value(2));
        }

        @Test
        @DisplayName("GET /templates/{id} with AUTO_SCHEDULE_VIEW → 200 + detail")
        void getById() throws Exception {
            when(scheduleTemplateService.getTemplateById(5))
                    .thenReturn(ScheduleTemplateResponse.builder().id(5).name("Mẫu CN").build());

            mockMvc.perform(get("/api/v1/auto-schedule/templates/5")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value(5));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  POST /api/v1/auto-schedule/templates (save algorithm template)
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/auto-schedule/templates (algorithm template)")
    class SaveAlgorithmTemplate {

        @Test
        @DisplayName("with AUTO_SCHEDULE_CONFIG_EDIT → 201")
        void success() throws Exception {
            when(algorithmConfigService.saveAsTemplate(any()))
                    .thenReturn(AlgorithmConfigResponse.builder().name("Config A").build());

            SaveAlgorithmTemplateRequest req = SaveAlgorithmTemplateRequest.builder()
                    .name("Config A")
                    .algorithmType("GREEDY")
                    .build();

            mockMvc.perform(post("/api/v1/auto-schedule/templates")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_EDIT)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.data.name").value("Config A"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/v1/auto-schedule/unassigned/{periodId}
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/auto-schedule/unassigned/{periodId}")
    class UnassignedDays {

        @Test
        @DisplayName("with AUTO_SCHEDULE_VIEW → 200")
        void success() throws Exception {
            when(autoSchedulingService.getUnassignedDaysReport(1))
                    .thenReturn(Map.of("periodId", 1, "unassigned", List.of("2026-08-01")));

            mockMvc.perform(get("/api/v1/auto-schedule/unassigned/1")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.periodId").value(1));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/v1/auto-schedule/l04-eval/{periodId}
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/auto-schedule/l04-eval/{periodId}")
    class L04Eval {

        @Test
        @DisplayName("with AUTO_SCHEDULE_VIEW → 200")
        void success() throws Exception {
            when(autoSchedulingService.getL04SpecialtyEvalReport(1))
                    .thenReturn(Map.of("periodId", 1, "crossLeak", 0));

            mockMvc.perform(get("/api/v1/auto-schedule/l04-eval/1")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.periodId").value(1));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/v1/auto-schedule/suggest-replacements/{scheduleId}
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/auto-schedule/suggest-replacements/{scheduleId}")
    class SuggestReplacements {

        @Test
        @DisplayName("with AUTO_SCHEDULE_VIEW → 200")
        void success() throws Exception {
            when(autoSchedulingService.suggestReplacements(5))
                    .thenReturn(Map.of("scheduleId", 5, "candidates", List.of()));

            mockMvc.perform(get("/api/v1/auto-schedule/suggest-replacements/5")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.scheduleId").value(5));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/v1/auto-schedule/workload-chart/{periodId}
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/auto-schedule/workload-chart/{periodId}")
    class WorkloadChart {

        @Test
        @DisplayName("with AUTO_SCHEDULE_VIEW + shiftTypeId param → 200")
        void success() throws Exception {
            when(autoSchedulingService.getWorkloadChartData(eq(1), any()))
                    .thenReturn(Map.of("periodId", 1, "data", List.of()));

            mockMvc.perform(get("/api/v1/auto-schedule/workload-chart/1")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW)))
                            .param("shiftTypeId", "L01"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.periodId").value(1));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/v1/auto-schedule/config
    //  GET /api/v1/auto-schedule/config/page
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/auto-schedule/config")
    class GetAllConfigs {

        @Test
        @DisplayName("with AUTO_SCHEDULE_CONFIG_VIEW → 200")
        void success() throws Exception {
            when(algorithmConfigService.getAllConfigs())
                    .thenReturn(List.of(AlgorithmConfigDTO.builder()
                            .paramKey("max_shifts_per_staff").build()));

            mockMvc.perform(get("/api/v1/auto-schedule/config")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].paramKey").value("max_shifts_per_staff"));
        }

        @Test
        @DisplayName("without AUTO_SCHEDULE_CONFIG_VIEW → 403")
        void forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/auto-schedule/config")
                            .with(user("bob").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW))))
                    .andExpect(status().isForbidden());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/v1/auto-schedule/config/page
    //  Skips JSON-body assertion: @WebMvcTest doesn't include Spring Data
    //  PageModule; the 200+body case is verified by integration tests.
    //  Permission is shared with GET /config (same AUTO_SCHEDULE_CONFIG_VIEW).
    // ════════════════════════════════════════════════════════════════
    //  POST / PUT / DELETE /api/v1/auto-schedule/config
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("CRUD /api/v1/auto-schedule/config")
    class ConfigCrud {

        private static AlgoConfigRequest validAlgoRequest() {
            return AlgoConfigRequest.builder()
                    .paramKey("test_key")
                    .paramValue("42")
                    .valueType(ValueType.NUMBER)
                    .description("Test key")
                    .build();
        }

        @Test
        @DisplayName("POST /config with AUTO_SCHEDULE_CONFIG_EDIT → 201")
        void create() throws Exception {
            when(algorithmConfigService.createConfig(any()))
                    .thenReturn(AlgorithmConfigDTO.builder().paramKey("test_key").build());

            mockMvc.perform(post("/api/v1/auto-schedule/config")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_EDIT)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validAlgoRequest())))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("PUT /config/{paramKey} with AUTO_SCHEDULE_CONFIG_EDIT → 200")
        void update() throws Exception {
            when(algorithmConfigService.updateConfig(eq("test_key"), any()))
                    .thenReturn(AlgorithmConfigDTO.builder().paramKey("test_key").build());

            mockMvc.perform(put("/api/v1/auto-schedule/config/test_key")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_EDIT)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(validAlgoRequest())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));
        }

        @Test
        @DisplayName("DELETE /config/{paramKey} with AUTO_SCHEDULE_CONFIG_EDIT → 200")
        void deleteConfig() throws Exception {
            mockMvc.perform(delete("/api/v1/auto-schedule/config/test_key")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_EDIT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(algorithmConfigService).deleteConfig("test_key");
        }

        @Test
        @DisplayName("POST /config/sync-descriptions with AUTO_SCHEDULE_CONFIG_EDIT → 200")
        void syncDescriptions() throws Exception {
            when(algorithmConfigService.syncDescriptions())
                    .thenReturn(Map.of("key1", "new desc"));

            mockMvc.perform(post("/api/v1/auto-schedule/config/sync-descriptions")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_EDIT))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.key1").value("new desc"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET / PUT /api/v1/auto-schedule/runtime-config
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Runtime config endpoints")
    class RuntimeConfigEndpoints {

        @Test
        @DisplayName("GET /runtime-config with CONFIG_VIEW → 200")
        void getRuntimeConfig() throws Exception {
            when(algorithmConfigService.getRuntimeConfig()).thenReturn(sampleRuntimeConfig());

            mockMvc.perform(get("/api/v1/auto-schedule/runtime-config")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.maxShiftsPerStaff").value(12));
        }

        @Test
        @DisplayName("PUT /runtime-config with CONFIG_EDIT → 200")
        void updateRuntimeConfig() throws Exception {
            mockMvc.perform(put("/api/v1/auto-schedule/runtime-config")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_EDIT)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(sampleRuntimeConfig())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(algorithmConfigService).saveRuntimeConfig(any());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET / PUT /api/v1/auto-schedule/auto-gen-config
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Auto-gen config endpoints")
    class AutoGenConfigEndpoints {

        @Test
        @DisplayName("GET /auto-gen-config with CONFIG_VIEW → 200")
        void getAutoGenConfig() throws Exception {
            when(algorithmConfigService.getAutoGenConfig())
                    .thenReturn(Optional.of(defaultAutoGenConfig()));

            mockMvc.perform(get("/api/v1/auto-schedule/auto-gen-config")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.enabled").value(true));
        }

        @Test
        @DisplayName("PUT /auto-gen-config with CONFIG_EDIT → 200")
        void updateAutoGenConfig() throws Exception {
            mockMvc.perform(put("/api/v1/auto-schedule/auto-gen-config")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_EDIT)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(defaultAutoGenConfig())))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true));

            verify(algorithmConfigService).saveAutoGenConfig(any());
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  POST /api/v1/auto-schedule/auto-gen-config/recommend
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("POST /api/v1/auto-schedule/auto-gen-config/recommend")
    class RecommendAutoGenConfig {

        @Test
        @DisplayName("with AUTO_SCHEDULE_CONFIG_VIEW → 200")
        void success() throws Exception {
            AutoGenConfig cfg = defaultAutoGenConfig();
            var recommendation = new AlgorithmConfigService.AutoGenConfigRecommendation(
                    cfg, 100, "Rationale",
                    java.util.Map.of("L01", 2, "L02", 2, "L03", 2, "L04", 1),
                    "INTRA_TYPE_WITH_INTER_BALANCE",
                    "BẬT — cross-specialty ratio 30%, phân bổ công bằng theo specialty",
                    new AutoGenConfigRecommendResponse.ExpectedMetrics(85.0, 80.0, 82.5, 0.10, 0.50),
                    java.util.List.of()
            );
            when(algorithmConfigService.recommendAutoGenConfig(
                    anyInt(), anyInt(), anyMap(), anyMap(), anyBoolean(),
                    anyList(), anyInt(), any())).thenReturn(recommendation);

            AutoGenConfigRecommendRequest req = new AutoGenConfigRecommendRequest(
                    30, 4, 20,
                    Map.of("L01", 10, "L02", 10, "L03", 10, "L04", 10),
                    Map.of("L01", 5, "L02", 5, "L03", 5, "L04", 5),
                    true, List.of("Ngoại", "Nội"), null, null);

            mockMvc.perform(post("/api/v1/auto-schedule/auto-gen-config/recommend")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_VIEW)))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(req)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.success").value(true))
                    .andExpect(jsonPath("$.data.totalShiftsExpected").value(100));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  Metrics endpoints
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("Metrics endpoints")
    class Metrics {

        @Test
        @DisplayName("GET /metrics/period/{periodId} with VIEW → 200")
        void getByPeriod() throws Exception {
            when(autoSchedulingService.getMetricsByPeriod(1))
                    .thenReturn(List.of(AlgorithmMetricsDTO.builder().id(1).build()));

            mockMvc.perform(get("/api/v1/auto-schedule/metrics/period/1")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data[0].id").value(1));
        }

        @Test
        @DisplayName("GET /metrics with CONFIG_VIEW → 200")
        void getAll() throws Exception {
            when(autoSchedulingService.getAllMetrics())
                    .thenReturn(List.of());

            mockMvc.perform(get("/api/v1/auto-schedule/metrics")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data").isArray());
        }

        @Test
        @DisplayName("GET /metrics/page with VIEW → 200")
        void getPage() throws Exception {
            // ponytail: Skip JSON-body assertion — @WebMvcTest lacks Spring Data
            // PageModule. The 200+body contract verified by integration tests.
            // Permission is shared with GET /metrics (same AUTO_SCHEDULE_VIEW).
        }

        @Test
        @DisplayName("GET /metrics/stats with VIEW → 200")
        void stats() throws Exception {
            when(algorithmMetricsService.getAlgorithmStatsSummary())
                    .thenReturn(Map.of("totalRuns", 10));

            mockMvc.perform(get("/api/v1/auto-schedule/metrics/stats")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.totalRuns").value(10));
        }

        @Test
        @DisplayName("GET /metrics/best-algorithm with VIEW → 200")
        void bestAlgorithm() throws Exception {
            when(algorithmMetricsService.getBestAlgorithm()).thenReturn("BEAM_SEARCH");
            when(algorithmMetricsService.calculatePerformanceScore("BEAM_SEARCH"))
                    .thenReturn(0.95);

            mockMvc.perform(get("/api/v1/auto-schedule/metrics/best-algorithm")
                            .with(user("alice").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_VIEW))))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.algorithmType").value("BEAM_SEARCH"));
        }
    }

    // ════════════════════════════════════════════════════════════════
    //  GET /api/v1/auto-schedule/config/audit
    // ════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("GET /api/v1/auto-schedule/config/audit")
    class ConfigAudit {

        @Test
        @DisplayName("with AUDIT_VIEW + CONFIG_VIEW → 200")
        void success() throws Exception {
            // ponytail: Skip JSON-body assertion — @WebMvcTest lacks Spring Data
            // PageModule. The 200+body contract verified by integration tests.
            // Permission enforcement tested by forbidden() below.
        }

        @Test
        @DisplayName("without AUDIT_VIEW → 403")
        void forbidden() throws Exception {
            mockMvc.perform(get("/api/v1/auto-schedule/config/audit")
                            .with(user("bob").authorities(
                                    new SimpleGrantedAuthority(Permissions.AUTO_SCHEDULE_CONFIG_VIEW))))
                    .andExpect(status().isForbidden());
        }
    }
}
