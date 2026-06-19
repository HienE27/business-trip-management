package com.hospital.scheduler.dto.request;

import jakarta.validation.constraints.*;
import lombok.*;

import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BulkPeriodRequest {

    @NotEmpty(message = "Danh sách ID kỳ lịch không được để trống")
    @Size(max = 20, message = "Tối đa 20 kỳ lịch mỗi lần thực hiện")
    private List<@NotNull(message = "ID kỳ lịch không được null") Integer> periodIds;
}
