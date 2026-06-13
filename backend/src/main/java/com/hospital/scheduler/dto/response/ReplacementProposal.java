package com.hospital.scheduler.dto.response;

import lombok.*;

import java.time.LocalDate;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReplacementProposal {

    private Integer scheduleId;
    private LocalDate workDate;
    private String shiftTypeId;
    private String shiftTypeName;

    private StaffCandidate primaryCandidate;
    private StaffCandidate secondaryCandidate;
    private StaffCandidate tertiaryCandidate;

    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class StaffCandidate {
        private Integer id;
        private String fullName;
        private String specialtyName;
        private String roleName;
        private int currentShiftCount;
    }
}
