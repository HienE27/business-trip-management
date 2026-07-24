"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { api } from "@/lib/api";
import { suggestReplacements } from "@/lib/api/autoScheduleApi";
import type { ReplacementSuggestion } from "@/types/api";

/**
 * useReplacementSuggestions — fetch-on-demand hook backing the M07-F08
 * "Đề xuất người thay thế" trigger inside ShiftDetailModal.
 *
 * Follows the same self-contained fetch pattern as useScheduleDetailModal:
 * the component never touches the API client directly; the hook keeps its
 * own loading / data / error state and re-fetches only when the trigger
 * flips to `true` (so mounting the modal alone does NOT fire a request).
 *
 * @param scheduleId  the persisted schedule id to find replacements for.
 *                    null disables the fetch entirely.
 * @param trigger     caller-controlled open flag. The hook fetches on the
 *                    rising edge of `scheduleId != null && trigger === true`
 *                    and stops fetching once it goes back to false.
 */
export function useReplacementSuggestions(
  scheduleId: number | null,
  trigger: boolean,
): {
  data: ReplacementSuggestion | null;
  loading: boolean;
  error: string | null;
  fetch: () => void;
  reset: () => void;
} {
  const [data, setData] = useState<ReplacementSuggestion | null>(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const ignore = useRef(false);
  // Bump counter to force a re-fetch from the imperative `fetch()` action.
  const [fetchTick, setFetchTick] = useState(0);

  useEffect(() => {
    if (scheduleId === null || !trigger) {
      // No active request — keep last successful data around so the modal
      // stays rendered while it animates closed, but stop loading state.
      setLoading(false);
      return;
    }

    ignore.current = false;
    setData(null);
    setError(null);
    setLoading(true);

    suggestReplacements(api, scheduleId)
      .then((res) => {
        if (ignore.current) return;
        setData(res);
      })
      .catch(() => {
        if (ignore.current) return;
        setError("Không thể tải đề xuất thay thế.");
      })
      .finally(() => {
        if (ignore.current) return;
        setLoading(false);
      });

    return () => {
      ignore.current = true;
    };
    // fetchTick is intentionally a dep so the imperative `fetch()` action
    // can force the effect to re-run without changing scheduleId/trigger.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scheduleId, trigger, fetchTick]);

  const fetch = useCallback(() => {
    setFetchTick((t) => t + 1);
  }, []);

  const reset = useCallback(() => {
    ignore.current = true;
    setData(null);
    setError(null);
    setLoading(false);
  }, []);

  return { data, loading, error, fetch, reset };
}
