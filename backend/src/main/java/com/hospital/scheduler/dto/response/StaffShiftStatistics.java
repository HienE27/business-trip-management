package com.hospital.scheduler.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Staff shift statistics for a schedule period")
public class StaffShiftStatistics {

    @Schema(description = "Staff ID", example = "1")
    private Integer staffId;

    @Schema(description = "Staff full name", example = "Nguyễn Văn A")
    private String staffName;

    @Schema(description = "Staff code", example = "NV001")
    private String staffCode;

    @Schema(description = "Specialty name", example = "Nội khoa")
    private String specialtyName;

    @Schema(description = "Total number of shifts assigned", example = "15")
    private int totalShifts;

    @Schema(description = "Number of L01 shifts (24/24 duty)", example = "5")
    @JsonProperty("L01Count")
    private int L01Count;

    @Schema(description = "Number of L02 shifts (thông tầm)", example = "4")
    @JsonProperty("L02Count")
    private int L02Count;

    @Schema(description = "Number of L03 shifts (phòng khám dịch vụ)", example = "3")
    @JsonProperty("L03Count")
    private int L03Count;

    @Schema(description = "Number of L04 shifts (phòng khám chuyên gia)", example = "3")
    @JsonProperty("L04Count")
    private int L04Count;

    @Schema(description = "Total hours worked (L01=24h, L02=8h, L03=4h, L04=4h)", example = "168.0")
    private BigDecimal totalHours;

    @Schema(description = "Workload percentage relative to average", example = "12.5")
    private BigDecimal workloadPercentage;
}
