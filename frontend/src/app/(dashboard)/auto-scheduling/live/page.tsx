"use client";

import { useSearchParams } from "next/navigation";
import { useState } from "react";
import { LiveSearchChart } from "@/components/auto-scheduling/LiveSearchChart";
import { BackButton } from "@/components/ui/BackButton";
import { Button } from "@/components/ui/Button";

/**
 * Phase 2.2 — Live search telemetry page.
 *
 * <p>Mounts the {@link LiveSearchChart} for a run id. The run id is sourced
 * from the {@code ?runId=} query param so the existing auto-scheduling page
 * can deep-link to this view right after starting a search.
 */
export default function AutoSchedulingLivePage() {
  const params = useSearchParams();
  const initialRunId = params?.get("runId") ?? null;
  const [runId, setRunId] = useState<string | null>(initialRunId);
  const [draft, setDraft] = useState(initialRunId ?? "");

  return (
    <div className="mx-auto max-w-container-max space-y-4 p-margin-desktop">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <BackButton />
          <h1 className="font-display-lg text-display-lg text-on-surface">
            Tìm kiếm trực tiếp
          </h1>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
        <label className="flex flex-1 flex-col gap-1">
          <span className="font-label-md text-label-md text-on-surface">Run id</span>
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="UUID từ endpoint /preview"
            className="h-10 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 font-body-sm text-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
        </label>
        <Button
          variant="primary"
          onClick={() => setRunId(draft.trim() || null)}
          disabled={!draft.trim()}
        >
          Theo dõi
        </Button>
      </div>

      <LiveSearchChart runId={runId} />

      <p className="font-body-sm text-body-sm text-on-surface-variant">
        Biểu đồ được vẽ trực tiếp từ các sự kiện SSE do backend phát ra trong
        quá trình chạy LocalSearch. Đường đứt nét thể hiện điểm tốt nhất từng
        đạt được.
      </p>
    </div>
  );
}
