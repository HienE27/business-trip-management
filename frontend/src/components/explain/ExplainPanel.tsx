"use client";

import { useCallback, useState } from "react";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import type {
  AssignmentExplanation,
  WhyNotExplanation,
  CandidateRankingExplanation,
  ReplayExplanation,
} from "@/types/api";
import { AssignmentExplainCard } from "./AssignmentExplainCard";
import { WhyNotExplainCard } from "./WhyNotExplainCard";
import { CandidateRankingCard } from "./CandidateRankingCard";
import { ReplayExplainCard } from "./ReplayExplainCard";

interface ExplainPanelProps {
  mode: "assignment" | "why-not" | "ranking" | "replay";
  slotId?: number;
  assignmentId?: number;
  staffId?: number;
  sessionKey?: string;
  iteration?: number;
  onClose?: () => void;
}

/**
 * Explain Panel - shows reasoning for scheduling decisions.
 *
 * Can display:
 * - Assignment explanation
 * - Why not explanation
 * - Candidate ranking
 * - Replay explanation
 */
export function ExplainPanel({
  mode,
  slotId,
  assignmentId,
  staffId,
  sessionKey,
  iteration,
  onClose,
}: ExplainPanelProps) {
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // Assignment explanation
  const [assignmentExplanation, setAssignmentExplanation] = useState<AssignmentExplanation | null>(null);

  // Why not explanation
  const [whyNotExplanation, setWhyNotExplanation] = useState<WhyNotExplanation | null>(null);

  // Candidate ranking
  const [candidateRanking, setCandidateRanking] = useState<CandidateRankingExplanation | null>(null);

  // Replay explanation
  const [replayExplanation, setReplayExplanation] = useState<ReplayExplanation | null>(null);

  // Load data based on mode
  const loadExplanation = useCallback(async () => {
    setLoading(true);
    setError(null);

    try {
      switch (mode) {
        case "assignment":
          if (assignmentId) {
            const data = await api.getAssignmentExplanation(assignmentId, { slotId, staffId });
            setAssignmentExplanation(data);
          }
          break;

        case "why-not":
          if (slotId && staffId) {
            const data = await api.getWhyNotExplanation(slotId, staffId, { sessionKey });
            setWhyNotExplanation(data);
          }
          break;

        case "ranking":
          if (slotId && sessionKey) {
            const data = await api.getCandidateRanking(slotId, sessionKey);
            setCandidateRanking(data);
          }
          break;

        case "replay":
          if (sessionKey && iteration !== undefined) {
            const data = await api.getReplayExplanation(sessionKey, iteration);
            setReplayExplanation(data);
          }
          break;
      }
    } catch (err) {
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setLoading(false);
    }
  }, [mode, assignmentId, slotId, staffId, sessionKey, iteration]);

  // Auto-load when props change
  if (!loading && !error && !assignmentExplanation && !whyNotExplanation && !candidateRanking && !replayExplanation) {
    loadExplanation();
  }

  const modeLabels: Record<string, string> = {
    assignment: "Giải thích phân công",
    "why-not": "Tại sao không được chọn",
    ranking: "Xếp hạng ứng viên",
    replay: "Giải thích Iteration",
  };

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl overflow-hidden">
      {/* Header */}
      <div className="flex items-center justify-between p-4 border-b border-outline-variant bg-surface-container-low">
        <div className="flex items-center gap-3">
          <span className="material-symbols-outlined text-primary">psychology</span>
          <h3 className="font-title-lg text-title-lg text-on-surface">{modeLabels[mode]}</h3>
        </div>
        {onClose && (
          <button
            onClick={onClose}
            className="p-2 rounded-lg hover:bg-surface-container-high transition-colors"
          >
            <span className="material-symbols-outlined text-[20px]">close</span>
          </button>
        )}
      </div>

      {/* Content */}
      <div className="p-4 max-h-[600px] overflow-y-auto">
        {loading && (
          <div className="flex items-center justify-center py-12">
            <div className="w-8 h-8 border-4 border-primary border-t-transparent rounded-full animate-spin" />
          </div>
        )}

        {error && (
          <div className="p-4 bg-error-container rounded-lg">
            <p className="text-error text-label-md">{error}</p>
            <button
              onClick={loadExplanation}
              className="mt-2 text-label-sm text-primary hover:underline"
            >
              Thử lại
            </button>
          </div>
        )}

        {!loading && !error && mode === "assignment" && assignmentExplanation && (
          <AssignmentExplainCard explanation={assignmentExplanation} />
        )}

        {!loading && !error && mode === "why-not" && whyNotExplanation && (
          <WhyNotExplainCard explanation={whyNotExplanation} />
        )}

        {!loading && !error && mode === "ranking" && candidateRanking && (
          <CandidateRankingCard ranking={candidateRanking} />
        )}

        {!loading && !error && mode === "replay" && replayExplanation && (
          <ReplayExplainCard explanation={replayExplanation} />
        )}
      </div>
    </div>
  );
}
