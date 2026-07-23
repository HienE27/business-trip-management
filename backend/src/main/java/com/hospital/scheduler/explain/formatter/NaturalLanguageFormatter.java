package com.hospital.scheduler.explain.formatter;

import com.hospital.scheduler.explain.dto.AssignmentExplanation;
import com.hospital.scheduler.explain.dto.ReplayExplanation;
import com.hospital.scheduler.digital.sandbox.dto.ReplayFrame;
import com.hospital.scheduler.explain.dto.WhyNotExplanation;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Natural language formatter for explanations.
 *
 * <p>Transforms structured explanation data into human-readable Vietnamese text.
 */
@Component
public class NaturalLanguageFormatter {

    /**
     * Format assignment explanation as natural language.
     */
    public String formatAssignmentExplanation(
            String staffName,
            List<AssignmentExplanation.SelectionReason> reasons,
            AssignmentExplanation.ScoreBreakdown breakdown
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append(staffName).append(" được chọn vì:\n\n");

        if (reasons.isEmpty()) {
            sb.append("• Không có lý do cụ thể\n");
        } else {
            for (AssignmentExplanation.SelectionReason reason : reasons) {
                if (reason.isPositive()) {
                    sb.append("• ").append(reason.getReason());
                    if (reason.getDetail() != null && !reason.getDetail().isEmpty()) {
                        sb.append(": ").append(reason.getDetail());
                    }
                    sb.append("\n");
                }
            }
        }

        sb.append("\nĐóng góp điểm:\n");
        sb.append("• Coverage: ").append(String.format("+%.1f", breakdown.getCoverageScore())).append("\n");
        sb.append("• Fairness: ").append(String.format("+%.1f", breakdown.getFairnessScore())).append("\n");
        sb.append("• Preference: ").append(String.format("+%.1f", breakdown.getPreferenceScore())).append("\n");
        sb.append("• Tổng: ").append(String.format("+%.1f", breakdown.getNetScore())).append("\n");

        return sb.toString();
    }

    /**
     * Format "why not" explanation as natural language.
     */
    public String formatWhyNotExplanation(
            String staffName,
            List<WhyNotExplanation.RejectionReason> reasons,
            WhyNotExplanation.SelectedAlternative selected
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append(staffName).append(" không được chọn vì:\n\n");

        if (reasons.isEmpty()) {
            sb.append("• Không có lý do cụ thể\n");
        } else {
            for (WhyNotExplanation.RejectionReason reason : reasons) {
                if (reason.isBlocking()) {
                    sb.append("✗ ").append(reason.getConstraintName());
                    if (reason.getDetail() != null && !reason.getDetail().isEmpty()) {
                        sb.append(": ").append(reason.getDetail());
                    }
                    sb.append(" (Penalty: ").append(String.format("%.1f", reason.getPenalty())).append(")\n");
                } else {
                    sb.append("• ").append(reason.getConstraintName());
                    if (reason.getDetail() != null && !reason.getDetail().isEmpty()) {
                        sb.append(": ").append(reason.getDetail());
                    }
                    sb.append("\n");
                }
            }
        }

        if (selected != null) {
            sb.append("\n").append(selected.getStaffName()).append(" được chọn thay thế");
            if (selected.getScore() > 0) {
                sb.append(" với điểm ").append(String.format("%.1f", selected.getScore()));
            }
            sb.append(".\n");
        }

        return sb.toString();
    }

    /**
     * Format replay explanation as natural language.
     */
    public String formatReplayExplanation(ReplayFrame frame) {
        StringBuilder sb = new StringBuilder();

        sb.append("Iteration ").append(frame.getIteration()).append(": ");

        if (frame.isAccepted()) {
            sb.append("Move được chấp nhận\n\n");

            if (frame.getMoveType() != null) {
                sb.append("• Move type: ").append(frame.getMoveType()).append("\n");
            }

            if (frame.getStaff() != null) {
                sb.append("• Staff: ").append(frame.getStaff().getName()).append("\n");
            }

            if (frame.getScoreDelta() != 0) {
                sb.append("• Score delta: ").append(String.format("%+.1f", frame.getScoreDelta())).append("\n");
            }

            if (frame.getCoverageDelta() != 0) {
                sb.append("• Coverage delta: ").append(String.format("%+.1f%%", frame.getCoverageDelta())).append("\n");
            }

        } else {
            sb.append("Move bị từ chối\n\n");

            if (frame.getReason() != null && !frame.getReason().isEmpty()) {
                sb.append("• Lý do: ").append(frame.getReason()).append("\n");
            }

            // Check constraint deltas for violations
            if (frame.getConstraintDeltas() != null && !frame.getConstraintDeltas().isEmpty()) {
                var violated = frame.getConstraintDeltas().values().stream()
                        .filter(d -> d.getDelta() > 0)
                        .findFirst()
                        .orElse(null);
                if (violated != null) {
                    sb.append("• Constraint vi phạm: ").append(violated.getConstraintName()).append("\n");
                }
            }

            if (frame.getScoreDelta() != 0) {
                sb.append("• Score impact: ").append(String.format("%.1f", frame.getScoreDelta())).append("\n");
            }
        }

        return sb.toString();
    }

    /**
     * Format constraint contribution as natural language.
     */
    public String formatConstraintContribution(String constraintName, boolean satisfied, double contribution) {
        if (satisfied) {
            return constraintName + " được thỏa mãn (đóng góp " + String.format("+%.1f", contribution) + ")";
        } else {
            return constraintName + " bị vi phạm (phạt " + String.format("%.1f", contribution) + ")";
        }
    }

    /**
     * Format a summary of the scheduling decision.
     */
    public String formatSummary(String staffName, boolean selected, String reason) {
        if (selected) {
            return staffName + " được phân công: " + reason;
        } else {
            return staffName + " không được phân công: " + reason;
        }
    }
}
