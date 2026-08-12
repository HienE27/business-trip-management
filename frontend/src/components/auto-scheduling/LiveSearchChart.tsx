"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { Badge } from "@/components/ui/Badge";
import { Button } from "@/components/ui/Button";

export interface LiveSearchEvent {
  type: "ITERATION" | "MOVE_ACCEPTED" | "MOVE_REJECTED" | "TABU_HIT" | "SCORE_IMPROVED" | "DIVERSIFIED" | "connected" | "complete";
  iteration?: number;
  elapsed?: number;
  hardDelta?: number;
  coverageDelta?: number;
  currentHard?: number;
  currentCoverage?: number;
  bestHard?: number;
  bestCoverage?: number;
  runId?: string;
}

export interface LiveSearchChartProps {
  runId: string | null;
  /** Max points to keep in the rolling buffer. */
  bufferSize?: number;
}

/**
 * Phase 2.2 — Live search telemetry chart.
 *
 * <p>Opens an SSE connection to {@code /api/v1/scheduling/stream/{runId}} and
 * plots current vs best hard-violation count and accepted/rejected/tabu move
 * counters over iterations. Uses Canvas to avoid pulling in a heavyweight
 * charting library just for this widget.
 */
export function LiveSearchChart({ runId, bufferSize = 200 }: LiveSearchChartProps) {
  const canvasRef = useRef<HTMLCanvasElement>(null);
  const [events, setEvents] = useState<LiveSearchEvent[]>([]);
  const [connected, setConnected] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const countersRef = useRef({ accepted: 0, rejected: 0, tabu: 0, scoreImproved: 0 });

  // Reset state whenever the run id changes
  useEffect(() => {
    setEvents([]);
    countersRef.current = { accepted: 0, rejected: 0, tabu: 0, scoreImproved: 0 };
  }, [runId]);

  // Connect SSE
  useEffect(() => {
    if (!runId) return;
    if (typeof window === "undefined" || typeof EventSource === "undefined") return;

    const source = new EventSource(`/api/v1/scheduling/stream/${runId}`);
    setConnected(false);
    setError(null);

    source.addEventListener("connected", (ev) => {
      setConnected(true);
      try {
        const data = JSON.parse((ev as MessageEvent).data);
        setEvents((prev) => [
          ...prev,
          { type: "connected", runId: data.runId },
        ]);
      } catch {
        // ignore
      }
    });

    source.addEventListener("event", (ev) => {
      try {
        const parsed = JSON.parse((ev as MessageEvent).data) as LiveSearchEvent;
        if (parsed.type === "MOVE_ACCEPTED") countersRef.current.accepted++;
        else if (parsed.type === "MOVE_REJECTED") countersRef.current.rejected++;
        else if (parsed.type === "TABU_HIT") countersRef.current.tabu++;
        else if (parsed.type === "SCORE_IMPROVED") countersRef.current.scoreImproved++;
        setEvents((prev) => {
          const next = prev.length >= bufferSize ? prev.slice(-bufferSize + 1) : prev.slice();
          next.push(parsed);
          return next;
        });
      } catch {
        // ignore malformed events
      }
    });

    source.addEventListener("complete", () => {
      source.close();
      setConnected(false);
      setEvents((prev) => [...prev, { type: "complete" }]);
    });

    source.onerror = () => {
      setConnected(false);
      setError("Mất kết nối với máy chủ (SSE)");
    };

    return () => {
      source.close();
    };
  }, [runId, bufferSize]);

  // Redraw canvas whenever events change
  const draw = useCallback(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    const w = canvas.width;
    const h = canvas.height;
    ctx.clearRect(0, 0, w, h);

    // Background
    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, w, h);

    // Padding
    const pad = { l: 50, r: 12, t: 12, b: 28 };
    const plotW = w - pad.l - pad.r;
    const plotH = h - pad.t - pad.b;

    // Compute data points from events
    const iterEvents = events.filter(
      (e) =>
        (e.type === "ITERATION" || e.type === "SCORE_IMPROVED") &&
        typeof e.iteration === "number" &&
        typeof e.currentHard === "number",
    );
    if (iterEvents.length === 0) {
      ctx.fillStyle = "#737686";
      ctx.font = "13px Inter, sans-serif";
      ctx.textAlign = "center";
      ctx.fillText("Đang chờ sự kiện…", w / 2, h / 2);
      return;
    }

    const minIter = iterEvents[0].iteration ?? 0;
    const maxIter = iterEvents[iterEvents.length - 1].iteration ?? 1;
    const iterRange = Math.max(1, maxIter - minIter);

    // Y axis — hard violations (start at 0)
    const allHard = iterEvents.flatMap((e) =>
      [e.currentHard ?? 0, e.bestHard ?? 0].filter((v) => typeof v === "number"),
    );
    const maxHard = Math.max(1, ...allHard);

    // Axes
    ctx.strokeStyle = "#c3c6d7";
    ctx.lineWidth = 1;
    ctx.beginPath();
    ctx.moveTo(pad.l, pad.t);
    ctx.lineTo(pad.l, pad.t + plotH);
    ctx.lineTo(pad.l + plotW, pad.t + plotH);
    ctx.stroke();

    // Y labels (hard)
    ctx.fillStyle = "#434655";
    ctx.font = "11px Inter, sans-serif";
    ctx.textAlign = "right";
    for (let i = 0; i <= 4; i++) {
      const v = Math.round((maxHard * (4 - i)) / 4);
      const y = pad.t + (plotH * i) / 4;
      ctx.fillText(String(v), pad.l - 6, y + 3);
      ctx.strokeStyle = "#eceef0";
      ctx.beginPath();
      ctx.moveTo(pad.l, y);
      ctx.lineTo(pad.l + plotW, y);
      ctx.stroke();
    }

    // X labels (iteration)
    ctx.textAlign = "center";
    for (let i = 0; i <= 4; i++) {
      const iter = Math.round(minIter + (iterRange * i) / 4);
      const x = pad.l + (plotW * i) / 4;
      ctx.fillText(String(iter), x, pad.t + plotH + 16);
    }

    // Current hard line (red)
    ctx.strokeStyle = "#ba1a1a";
    ctx.lineWidth = 2;
    ctx.beginPath();
    iterEvents.forEach((e, idx) => {
      const x = pad.l + (((e.iteration ?? 0) - minIter) / iterRange) * plotW;
      const y = pad.t + (1 - (e.currentHard ?? 0) / maxHard) * plotH;
      if (idx === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();

    // Best hard line (green)
    ctx.strokeStyle = "#006e2d";
    ctx.lineWidth = 2;
    ctx.setLineDash([6, 3]);
    ctx.beginPath();
    iterEvents.forEach((e, idx) => {
      const x = pad.l + (((e.iteration ?? 0) - minIter) / iterRange) * plotW;
      const y = pad.t + (1 - (e.bestHard ?? 0) / maxHard) * plotH;
      if (idx === 0) ctx.moveTo(x, y);
      else ctx.lineTo(x, y);
    });
    ctx.stroke();
    ctx.setLineDash([]);

    // Legend
    ctx.font = "12px Inter, sans-serif";
    ctx.textAlign = "left";
    ctx.fillStyle = "#ba1a1a";
    ctx.fillRect(pad.l, 4, 12, 3);
    ctx.fillStyle = "#191c1e";
    ctx.fillText("Hiện tại (hard)", pad.l + 18, 12);
    ctx.fillStyle = "#006e2d";
    ctx.fillRect(pad.l + 120, 4, 12, 3);
    ctx.fillStyle = "#191c1e";
    ctx.fillText("Tốt nhất (hard)", pad.l + 138, 12);
  }, [events]);

  useEffect(() => {
    draw();
  }, [draw]);

  // Auto-size canvas to container width
  useEffect(() => {
    const canvas = canvasRef.current;
    if (!canvas) return;
    const handleResize = () => {
      const parent = canvas.parentElement;
      if (!parent) return;
      canvas.width = parent.clientWidth;
      canvas.height = 220;
      draw();
    };
    handleResize();
    window.addEventListener("resize", handleResize);
    return () => window.removeEventListener("resize", handleResize);
  }, [draw]);

  if (!runId) {
    return (
      <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
        <p className="font-body-sm text-body-sm text-on-surface-variant">
          Chưa có run id. Hãy bắt đầu chạy tự động xếp lịch để xem biểu đồ thời gian thực.
        </p>
      </div>
    );
  }

  const c = countersRef.current;
  const acceptedRate =
    c.accepted + c.rejected > 0
      ? Math.round((c.accepted / (c.accepted + c.rejected)) * 100)
      : 0;

  return (
    <div className="space-y-3 rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
      <div className="flex flex-wrap items-center justify-between gap-2">
        <div className="flex items-center gap-2">
          <h3 className="font-headline-md text-headline-md text-on-surface">
            Tìm kiếm trực tiếp
          </h3>
          <Badge tone={connected ? "success" : "neutral"}>
            {connected ? "Đang kết nối" : "Mất kết nối"}
          </Badge>
        </div>
        <p className="font-body-sm text-body-sm text-on-surface-variant">
          Run id: <code className="font-mono text-label-sm text-label-sm">{runId}</code>
        </p>
      </div>

      <div className="w-full overflow-hidden rounded-lg border border-outline-variant">
        <canvas ref={canvasRef} className="block h-[220px] w-full" />
      </div>

      <div className="grid grid-cols-2 gap-2 md:grid-cols-4">
        <KPI label="Chấp nhận" value={c.accepted} tone="secondary" />
        <KPI label="Từ chối" value={c.rejected} tone="neutral" />
        <KPI label="Tabu hit" value={c.tabu} tone="tertiary" />
        <KPI label="Tỉ lệ chấp nhận" value={`${acceptedRate}%`} tone="primary" />
      </div>

      {error && (
        <div className="rounded-lg border border-red-300 bg-red-100 text-red-800 p-3">
          <p className="font-body-sm text-body-sm text-red-800">{error}</p>
          <Button
            variant="secondary"
            size="sm"
            onClick={() => {
              setError(null);
              setEvents([]);
            }}
          >
            Đặt lại
          </Button>
        </div>
      )}
    </div>
  );
}

function KPI({
  label,
  value,
  tone,
}: {
  label: string;
  value: number | string;
  tone: "primary" | "secondary" | "tertiary" | "neutral";
}) {
  const map = {
    primary: "bg-blue-100 text-blue-800",
    secondary: "bg-emerald-100 text-emerald-800",
    tertiary: "bg-amber-100 text-amber-800",
    neutral: "bg-surface-container-highest text-on-surface-variant",
  } as const;
  return (
    <div className={`rounded-lg px-3 py-2 ${map[tone]}`}>
      <p className="font-label-sm text-label-sm uppercase opacity-80">{label}</p>
      <p className="font-headline-md text-headline-md">{value}</p>
    </div>
  );
}

export default LiveSearchChart;