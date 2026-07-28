"use client";

import { useEffect, useState, useRef, useCallback } from "react";
import { api } from "@/lib/api-client";

export type AlgorithmProgressData = {
  status: "IDLE" | "RUNNING" | "COMPLETED" | "FAILED";
  step: string;
  percent: number;
  message: string;
  /** Server-side start timestamp (ISO). Frontend computes elapsed from this. */
  startedAt?: string;
  resultJson?: string; // Cached result from backend
};

/**
 * Hook real-time polling progress cho auto-scheduling algorithm.
 * - Polls every 1s khi running=true
 * - Auto stops khi status là COMPLETED/FAILED/IDLE
 * - Cleanup on unmount
 * - Returns resultJson when COMPLETED so parent can parse and use it
 */
export function useAlgorithmProgress(
  periodId: number | null, 
  running: boolean,
  onResult?: (resultJson: string) => void
) {
  const [progress, setProgress] = useState<AlgorithmProgressData>({
    status: "IDLE",
    step: "",
    percent: 0,
    message: "",
  });
  const intervalRef = useRef<NodeJS.Timeout | null>(null);

  const handleResult = useCallback((resultJson: string) => {
    if (onResult) {
      onResult(resultJson);
    }
  }, [onResult]);

  useEffect(() => {
    if (!running || !periodId) {
      setProgress({ status: "IDLE", step: "", percent: 0, message: "" });
      return;
    }

    const fetchProgress = async () => {
      try {
        const data = await api.getAlgorithmProgress(periodId);
        setProgress({
          status: data.status,
          step: data.step ?? "",
          percent: data.percent ?? 0,
          message: data.message ?? "",
          startedAt: data.startedAt,
          resultJson: data.resultJson,
        });
        
        // If resultJson is available, notify parent
        if (data.resultJson && data.status === "COMPLETED") {
          handleResult(data.resultJson);
        }
        
        // Auto-stop polling khi đã xong
        if (data.status === "COMPLETED" || data.status === "FAILED" || data.status === "IDLE") {
          if (intervalRef.current) {
            clearInterval(intervalRef.current);
            intervalRef.current = null;
          }
        }
      } catch {
        // Silent - polling sẽ retry
      }
    };

    // Fetch ngay lập tức
    void fetchProgress();
    // Sau đó poll mỗi 1s
    intervalRef.current = setInterval(fetchProgress, 1000);

    return () => {
      if (intervalRef.current) {
        clearInterval(intervalRef.current);
        intervalRef.current = null;
      }
    };
  }, [periodId, running, handleResult]);

  return progress;
}