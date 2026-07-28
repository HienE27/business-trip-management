package com.hospital.scheduler.calculator;

import com.hospital.scheduler.dto.ApiResponse;
import com.hospital.scheduler.dto.ConfigCalculatorRequest;
import com.hospital.scheduler.dto.ConfigCalculatorResponse;
import com.hospital.scheduler.exception.BadRequestException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Configuration Calculator — 3 chế độ phân tích capacity dựa trên thuật toán thật.
 *
 * <pre>
 *   POST /api/v1/config-calculator/analyze  (mode 1, 2, 3)
 * </pre>
 */
@RestController
@RequestMapping("/api/v1/config-calculator")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Config Calculator", description = "Tính toán capacity dựa trên thuật toán thật")
public class ConfigCalculatorController {

    private final ConfigCalculatorOrchestrator orchestrator;

    @PostMapping("/analyze")
    @Operation(summary = "Phân tích capacity", description =
            "Mode 1: Config + Algorithm → Capacity\n" +
            "Mode 2: Target + Algorithm → Config\n" +
            "Mode 3: Target → Config + Algorithm (tự động chọn thuật toán tốt nhất)")
    public ResponseEntity<ApiResponse<ConfigCalculatorResponse>> analyze(
            @RequestBody ConfigCalculatorRequest request) {

        if (request.getPeriodId() == null) {
            throw new BadRequestException("periodId là bắt buộc");
        }
        if (request.getMode() < 1 || request.getMode() > 3) {
            throw new BadRequestException("mode phải là 1, 2, hoặc 3");
        }

        log.info("ConfigCalculator mode={} periodId={} algorithm={} targets={}",
                request.getMode(), request.getPeriodId(),
                request.getAlgorithmType(), request.getTargetShifts());

        ConfigCalculatorResponse response = orchestrator.calculate(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }
}
