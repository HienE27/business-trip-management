package com.hospital.scheduler.explain.service;

import com.hospital.scheduler.explain.dto.*;
import com.hospital.scheduler.explain.formatter.NaturalLanguageFormatter;
import com.hospital.scheduler.digital.sandbox.decision.DecisionGraph;
import com.hospital.scheduler.digital.sandbox.decision.DecisionGraphBuilder;
import com.hospital.scheduler.digital.sandbox.decision.DecisionNode;
import com.hospital.scheduler.digital.sandbox.decision.DecisionStatus;
import com.hospital.scheduler.digital.sandbox.dto.ReplayFrame;
import com.hospital.scheduler.digital.sandbox.dto.ReplayResponse;
import com.hospital.scheduler.digital.sandbox.service.SandboxReplayService;
import com.hospital.scheduler.scheduling.score.ScoreSnapshot;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Central service for generating explanations.
 *
 * <p>Provides reasoning for:
 * <ul>
 *   <li>Why a staff member was assigned to a slot</li>
 *   <li>Why a candidate was not selected</li>
 *   <li>Candidate ranking for a slot</li>
 *   <li>Replay iteration decisions</li>
 *   <li>Constraint contributions</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExplainService {

    private final SandboxReplayService replayService;
    private final DecisionGraphBuilder graphBuilder;
    private final NaturalLanguageFormatter naturalLanguageFormatter;

    /**
     * Explain why a staff member was assigned to a slot.
     */
    public AssignmentExplanation explainAssignment(
            Integer assignmentId,
            Integer slotId,
            Integer staffId,
            String staffName,
            ScoreSnapshot score
    ) {
        log.debug("Explaining assignment: slot={}, staff={}", slotId, staffId);

        // Build score breakdown (simulated - in real implementation, this would come from constraint evaluation)
        double fairnessScore = score.getCvTotal();
        double preferenceScore = score.getGini();
        double totalScore = score.getCoverage() + fairnessScore + preferenceScore;
        
        AssignmentExplanation.ScoreBreakdown breakdown = AssignmentExplanation.ScoreBreakdown.builder()
                .coverageScore(score.getCoverage() * 0.6)
                .fairnessScore(fairnessScore * 0.3)
                .preferenceScore(preferenceScore * 0.1)
                .recoveryScore(0)
                .weekendScore(0)
                .otherScore(0)
                .totalPenalty(0)
                .netScore(totalScore)
                .build();

        // Build constraint results (simulated)
        List<AssignmentExplanation.ConstraintResult> hardConstraints = buildHardConstraintResults(staffId, slotId);
        List<AssignmentExplanation.ConstraintResult> softConstraints = buildSoftConstraintResults(staffId, slotId);

        // Build selection reasons
        List<AssignmentExplanation.SelectionReason> reasons = buildSelectionReasons(hardConstraints, softConstraints);

        // Build natural language explanation
        String naturalExplanation = naturalLanguageFormatter.formatAssignmentExplanation(
                staffName, reasons, breakdown
        );

        return AssignmentExplanation.builder()
                .assignmentId(assignmentId)
                .slotId(slotId)
                .staffId(staffId)
                .staffName(staffName)
                .totalScore(totalScore)
                .allHardConstraintsSatisfied(hardConstraints.stream().allMatch(AssignmentExplanation.ConstraintResult::isSatisfied))
                .scoreBreakdown(breakdown)
                .hardConstraints(hardConstraints)
                .softConstraints(softConstraints)
                .selectionReasons(reasons)
                .naturalLanguageExplanation(naturalExplanation)
                .explainedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Explain why a candidate was not selected.
     */
    public WhyNotExplanation explainWhyNot(
            Integer slotId,
            Integer staffId,
            String staffName,
            DecisionGraph graph
    ) {
        log.debug("Explaining why not: slot={}, staff={}", slotId, staffId);

        // Defensive null check: when no decision graph is provided (e.g. no
        // sandbox session yet), return a stub explanation rather than NPE.
        // Callers should normally supply sessionKey for full graph-based reasoning.
        if (graph == null || graph.getNodes() == null) {
            return WhyNotExplanation.builder()
                    .slotId(slotId)
                    .staffId(staffId)
                    .staffName(staffName)
                    .rejected(false)
                    .rejectionReasons(Collections.emptyList())
                    .explainedAt(LocalDateTime.now())
                    .naturalLanguageExplanation(
                            "Không có decision graph cho slot/staff này. "
                            + "Vui lòng cung cấp sessionKey (sandbox) hoặc chạy auto-schedule "
                            + "để có decision graph phục vụ explain.")
                    .build();
        }

        // Find the node for this candidate
        DecisionNode node = graph.getNodes().stream()
                .filter(n -> Objects.equals(n.getSlotId(), slotId) && Objects.equals(n.getCandidateStaffId(), staffId))
                .findFirst()
                .orElse(null);

        if (node == null) {
            return WhyNotExplanation.builder()
                    .slotId(slotId)
                    .staffId(staffId)
                    .staffName(staffName)
                    .rejected(false)
                    .rejectionReasons(Collections.emptyList())
                    .explainedAt(LocalDateTime.now())
                    .naturalLanguageExplanation("Candidate was not evaluated.")
                    .build();
        }

        // Build rejection reasons
        List<WhyNotExplanation.RejectionReason> reasons = new ArrayList<>();

        if (node.getViolatedConstraint() != null) {
            reasons.add(WhyNotExplanation.RejectionReason.builder()
                    .constraintId(node.getViolatedConstraint())
                    .constraintName(getConstraintName(node.getViolatedConstraint()))
                    .reasonType(node.getStatus() == DecisionStatus.REJECTED_HARD ? "HARD" : "SOFT")
                    .detail(node.getRejectionReason() != null ? node.getRejectionReason() : "Constraint violated")
                    .penalty(Math.abs(node.getScoreDelta()))
                    .isBlocking(true)
                    .build());
        }

        // Build constraint chain
        List<WhyNotExplanation.ConstraintChainNode> chain = buildConstraintChain(node);

        // Find selected alternative
        WhyNotExplanation.SelectedAlternative selected = findSelectedAlternative(graph, slotId);

        // Build natural language explanation
        String naturalExplanation = naturalLanguageFormatter.formatWhyNotExplanation(
                staffName, reasons, selected
        );

        return WhyNotExplanation.builder()
                .slotId(slotId)
                .staffId(staffId)
                .staffName(staffName)
                .rejected(true)
                .rejectionReasons(reasons)
                .primaryRejectionConstraint(node.getViolatedConstraint())
                .constraintChain(chain)
                .scoreImpact(node.getScoreDelta())
                .rank(calculateRank(graph, slotId, staffId))
                .selectedAlternative(selected)
                .naturalLanguageExplanation(naturalExplanation)
                .explainedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Get candidate ranking for a slot.
     */
    public CandidateRankingExplanation explainCandidateRanking(Integer slotId, DecisionGraph graph) {
        log.debug("Explaining candidate ranking for slot: {}", slotId);

        // Defensive: empty graph → empty ranking rather than NPE.
        if (graph == null || graph.getNodes() == null) {
            return CandidateRankingExplanation.builder()
                    .slotId(slotId)
                    .totalCandidates(0)
                    .acceptedCount(0)
                    .rejectedCount(0)
                    .rankings(Collections.emptyList())
                    .summary(CandidateRankingExplanation.RankingSummary.builder()
                            .highestScore(0).lowestScore(0).averageScore(0)
                            .averageBranchingFactor(0).mostCommonRejectionConstraint(null)
                            .build())
                    .explainedAt(LocalDateTime.now())
                    .build();
        }

        // Get all nodes for this slot
        List<DecisionNode> slotNodes = graph.getNodes().stream()
                .filter(n -> Objects.equals(n.getSlotId(), slotId))
                .sorted(Comparator.comparingDouble(DecisionNode::getScoreDelta).reversed())
                .collect(Collectors.toList());

        // Build rankings
        List<CandidateRankingExplanation.CandidateRank> rankings = new ArrayList<>();
        int rank = 1;

        for (DecisionNode node : slotNodes) {
            if (node.getCandidateStaffId() == null) continue;

            CandidateRankingExplanation.CandidateRank candidateRank = CandidateRankingExplanation.CandidateRank.builder()
                    .rank(rank++)
                    .staffId(node.getCandidateStaffId())
                    .staffName(node.getCandidateStaffName())
                    .score(node.getScoreDelta())
                    .selected(node.getStatus() == DecisionStatus.ACCEPTED)
                    .rejected(node.getStatus().name().startsWith("REJECTED"))
                    .rejectionReason(node.getRejectionReason())
                    .primaryConstraint(node.getViolatedConstraint())
                    .strengths(buildStrengths(node))
                    .weaknesses(buildWeaknesses(node))
                    .build();

            rankings.add(candidateRank);
        }

        // Build summary
        double avgScore = rankings.stream()
                .mapToDouble(CandidateRankingExplanation.CandidateRank::getScore)
                .average().orElse(0);

        String mostCommonConstraint = rankings.stream()
                .filter(r -> r.getPrimaryConstraint() != null)
                .collect(Collectors.groupingBy(CandidateRankingExplanation.CandidateRank::getPrimaryConstraint, Collectors.counting()))
                .entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .map(Map.Entry::getKey)
                .orElse(null);

        CandidateRankingExplanation.RankingSummary summary = CandidateRankingExplanation.RankingSummary.builder()
                .highestScore(rankings.stream().mapToDouble(CandidateRankingExplanation.CandidateRank::getScore).max().orElse(0))
                .lowestScore(rankings.stream().mapToDouble(CandidateRankingExplanation.CandidateRank::getScore).min().orElse(0))
                .averageScore(avgScore)
                .averageBranchingFactor(rankings.size() / (double) Math.max(1, (int) rankings.stream().filter(CandidateRankingExplanation.CandidateRank::isSelected).count()))
                .mostCommonRejectionConstraint(mostCommonConstraint)
                .build();

        return CandidateRankingExplanation.builder()
                .slotId(slotId)
                .totalCandidates(rankings.size())
                .acceptedCount((int) rankings.stream().filter(CandidateRankingExplanation.CandidateRank::isSelected).count())
                .rejectedCount((int) rankings.stream().filter(CandidateRankingExplanation.CandidateRank::isRejected).count())
                .rankings(rankings)
                .summary(summary)
                .explainedAt(LocalDateTime.now())
                .build();
    }

    /**
     * Explain a replay iteration.
     */
    public ReplayExplanation explainReplayIteration(String sessionKey, int iteration) {
        log.debug("Explaining replay iteration: session={}, iteration={}", sessionKey, iteration);

        ReplayResponse replay = replayService.loadReplay(sessionKey);

        ReplayFrame frame = replay.getFrames().stream()
                .filter(f -> f.getIteration() == iteration)
                .findFirst()
                .orElse(null);

        if (frame == null) {
            return ReplayExplanation.builder()
                    .sessionKey(sessionKey)
                    .iteration(iteration)
                    .explainedAt(LocalDateTime.now())
                    .naturalLanguageExplanation("Frame not found.")
                    .build();
        }

        // Build score breakdown
        ReplayExplanation.ScoreBreakdown breakdown = ReplayExplanation.ScoreBreakdown.builder()
                .coverageDelta(frame.getCoverageDelta())
                .fairnessDelta(frame.getFairnessCv())
                .preferenceDelta(0)
                .recoveryDelta(0)
                .weekendDelta(0)
                .otherDelta(0)
                .totalDelta(frame.getScoreDelta())
                .build();

        // Build constraint changes
        List<ReplayExplanation.ConstraintChange> changes = buildConstraintChanges(frame);

        // Build natural language explanation
        String naturalExplanation = naturalLanguageFormatter.formatReplayExplanation(frame);

        return ReplayExplanation.builder()
                .sessionKey(sessionKey)
                .iteration(iteration)
                .moveType(frame.getMoveType())
                .accepted(frame.isAccepted())
                .staffId(frame.getStaff() != null ? frame.getStaff().getId() : null)
                .staffName(frame.getStaff() != null ? frame.getStaff().getName() : null)
                .targetStaffId(frame.getTargetStaff() != null ? frame.getTargetStaff().getId() : null)
                .targetStaffName(frame.getTargetStaff() != null ? frame.getTargetStaff().getName() : null)
                .scoreBreakdown(breakdown)
                .constraintChanges(changes)
                .acceptanceReason(frame.isAccepted() ? "Move improved score" : null)
                .rejectionReason(!frame.isAccepted() ? frame.getReason() : null)
                .naturalLanguageExplanation(naturalExplanation)
                .explainedAt(LocalDateTime.now())
                .build();
    }

    // ─── Helper Methods ───────────────────────────────────────────────────────

    private List<AssignmentExplanation.ConstraintResult> buildHardConstraintResults(Integer staffId, Integer slotId) {
        // Simulated - in real implementation, this would evaluate actual constraints
        return List.of(
                AssignmentExplanation.ConstraintResult.builder()
                        .constraintId("BR03")
                        .constraintName("Overnight Recovery")
                        .satisfied(true)
                        .detail("12h recovery satisfied")
                        .contribution(5)
                        .reason("Previous shift ended 14h ago")
                        .build(),
                AssignmentExplanation.ConstraintResult.builder()
                        .constraintId("BR05")
                        .constraintName("Leave Conflict")
                        .satisfied(true)
                        .detail("No leave on work date")
                        .contribution(3)
                        .reason("Staff is available")
                        .build()
        );
    }

    private List<AssignmentExplanation.ConstraintResult> buildSoftConstraintResults(Integer staffId, Integer slotId) {
        return List.of(
                AssignmentExplanation.ConstraintResult.builder()
                        .constraintId("BR08")
                        .constraintName("Weekend Balance")
                        .satisfied(true)
                        .detail("Weekend shifts balanced")
                        .contribution(2)
                        .reason("CV improved")
                        .build(),
                AssignmentExplanation.ConstraintResult.builder()
                        .constraintId("BR11")
                        .constraintName("Preference")
                        .satisfied(true)
                        .detail("Staff prefers morning shifts")
                        .contribution(1)
                        .reason("Slot is morning")
                        .build()
        );
    }

    private List<AssignmentExplanation.SelectionReason> buildSelectionReasons(
            List<AssignmentExplanation.ConstraintResult> hardConstraints,
            List<AssignmentExplanation.ConstraintResult> softConstraints
    ) {
        List<AssignmentExplanation.SelectionReason> reasons = new ArrayList<>();

        // Add satisfied hard constraints as reasons
        for (AssignmentExplanation.ConstraintResult c : hardConstraints) {
            if (c.isSatisfied()) {
                reasons.add(AssignmentExplanation.SelectionReason.builder()
                        .reason(c.getConstraintName() + " satisfied")
                        .detail(c.getDetail())
                        .weight(c.getContribution())
                        .positive(true)
                        .build());
            }
        }

        // Add satisfied soft constraints
        for (AssignmentExplanation.ConstraintResult c : softConstraints) {
            if (c.isSatisfied()) {
                reasons.add(AssignmentExplanation.SelectionReason.builder()
                        .reason(c.getConstraintName() + " improved")
                        .detail(c.getDetail())
                        .weight(c.getContribution())
                        .positive(true)
                        .build());
            }
        }

        return reasons;
    }

    private List<WhyNotExplanation.ConstraintChainNode> buildConstraintChain(DecisionNode node) {
        List<WhyNotExplanation.ConstraintChainNode> chain = new ArrayList<>();

        chain.add(WhyNotExplanation.ConstraintChainNode.builder()
                .description("Candidate evaluated")
                .detail(node.getCandidateStaffName() + " was considered")
                .satisfied(false)
                .build());

        if (node.getViolatedConstraint() != null) {
            chain.add(WhyNotExplanation.ConstraintChainNode.builder()
                    .description("Constraint violated: " + node.getViolatedConstraint())
                    .detail(getConstraintDescription(node.getViolatedConstraint()))
                    .satisfied(false)
                    .build());
        }

        chain.add(WhyNotExplanation.ConstraintChainNode.builder()
                .description("Candidate rejected")
                .detail("Score impact: " + node.getScoreDelta())
                .satisfied(false)
                .build());

        return chain;
    }

    private WhyNotExplanation.SelectedAlternative findSelectedAlternative(DecisionGraph graph, Integer slotId) {
        return graph.getNodes().stream()
                .filter(n -> Objects.equals(n.getSlotId(), slotId) && n.getStatus() == DecisionStatus.ACCEPTED)
                .findFirst()
                .map(n -> WhyNotExplanation.SelectedAlternative.builder()
                        .staffId(n.getCandidateStaffId())
                        .staffName(n.getCandidateStaffName())
                        .score(n.getScoreDelta())
                        .selectionReason("Best score among candidates")
                        .build())
                .orElse(null);
    }

    private int calculateRank(DecisionGraph graph, Integer slotId, Integer staffId) {
        List<DecisionNode> sortedNodes = graph.getNodes().stream()
                .filter(n -> Objects.equals(n.getSlotId(), slotId) && n.getCandidateStaffId() != null)
                .sorted(Comparator.comparingDouble(DecisionNode::getScoreDelta).reversed())
                .collect(Collectors.toList());

        for (int i = 0; i < sortedNodes.size(); i++) {
            if (Objects.equals(sortedNodes.get(i).getCandidateStaffId(), staffId)) {
                return i + 1;
            }
        }
        return -1;
    }

    private List<String> buildStrengths(DecisionNode node) {
        List<String> strengths = new ArrayList<>();
        if (node.getScoreDelta() > 0) {
            strengths.add("Positive score impact");
        }
        if (node.getStatus() == DecisionStatus.ACCEPTED) {
            strengths.add("Selected by algorithm");
        }
        return strengths;
    }

    private List<String> buildWeaknesses(DecisionNode node) {
        List<String> weaknesses = new ArrayList<>();
        if (node.getViolatedConstraint() != null) {
            weaknesses.add("Violated: " + node.getViolatedConstraint());
        }
        if (node.getScoreDelta() < 0) {
            weaknesses.add("Negative score impact");
        }
        return weaknesses;
    }

    private List<ReplayExplanation.ConstraintChange> buildConstraintChanges(ReplayFrame frame) {
        // Simulated - in real implementation, this would track actual changes
        return Collections.emptyList();
    }

    private String getConstraintName(String constraintId) {
        return switch (constraintId) {
            case "BR01" -> "Minimum Staff";
            case "BR02" -> "Maximum Staff";
            case "BR03" -> "Overnight Recovery";
            case "BR04" -> "Duplicate Shift";
            case "BR05" -> "Leave Conflict";
            case "BR06" -> "Skill Requirement";
            case "BR07" -> "Specialty Match";
            case "BR08" -> "Weekend Balance";
            case "BR09" -> "Coverage Requirement";
            case "BR10" -> "Fairness Balance";
            default -> constraintId;
        };
    }

    private String getConstraintDescription(String constraintId) {
        return switch (constraintId) {
            case "BR03" -> "Staff needs minimum 12h rest between shifts";
            case "BR04" -> "Staff cannot be assigned to overlapping shifts";
            case "BR05" -> "Staff cannot work on leave days";
            case "BR08" -> "Weekend shifts should be distributed fairly";
            default -> "Constraint rule " + constraintId;
        };
    }
}
