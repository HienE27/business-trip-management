"use client";

import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useSearchParams } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { BackButton } from "@/components/ui/BackButton";
import { Button } from "@/components/ui/Button";
import { Badge } from "@/components/ui/Badge";

export interface ReplayEntry {
  iteration: number;
  elapsed: number;
  moveType: string;
  slotId: number;
  previousStaffId: number;
  newStaffId: number;
  hardDelta: number;
  coverageDelta: number;
  accepted: boolean;
  score?: {
    hard: number;
    coverage: number;
    gap: number;
    gini: number;
  };
}

export interface ReplayPayload {
  runId: string;
  size: number;
  entries: ReplayEntry[];
}

const PLAYBACK_SPEEDS = [1, 2, 5, 10] as const;
type PlaybackSpeed = (typeof PLAYBACK_SPEEDS)[number];

/**
 * Phase 2.5 — Replay page.
 *
 * <p>Fetches the move log for a run id and steps through it like a media
 * player. Undo/Redo move the cursor without re-fetching. Play auto-advances
 * the cursor at the chosen speed.
 */
export default function AutoSchedulingReplayPage() {
  const params = useSearchParams();
  const initialRunId = params?.get("runId") ?? null;
  const [runId, setRunId] = useState<string | null>(initialRunId);
  const [draft, setDraft] = useState(initialRunId ?? "");

  const [entries, setEntries] = useState<ReplayEntry[]>([]);
  const [cursor, setCursor] = useState(0);
  const [playing, setPlaying] = useState(false);
  const [speed, setSpeed] = useState<PlaybackSpeed>(2);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const playRef = useRef<ReturnType<typeof setInterval> | null>(null);

  const load = useCallback(async (id: string) => {
    setLoading(true);
    setError(null);
    try {
      const res = await api.get<{ data: ReplayPayload }>(`/scheduling/replay/${id}`);
      setEntries(res.data.entries);
      setCursor(0);
    } catch (err) {
      setError(getErrorMessage(err, "Tải dữ liệu replay thất bại"));
      setEntries([]);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (runId) void load(runId);
  }, [runId, load]);

  // Playback loop
  useEffect(() => {
    if (!playing) {
      if (playRef.current) clearInterval(playRef.current);
      playRef.current = null;
      return;
    }
    if (entries.length === 0) {
      setPlaying(false);
      return;
    }
    playRef.current = setInterval(() => {
      setCursor((c) => {
        if (c >= entries.length - 1) {
          setPlaying(false);
          return c;
        }
        return c + 1;
      });
    }, 1000 / speed);
    return () => {
      if (playRef.current) clearInterval(playRef.current);
    };
  }, [playing, speed, entries.length]);

  const step = useCallback(
    (delta: number) => {
      setCursor((c) => Math.max(0, Math.min(entries.length - 1, c + delta)));
    },
    [entries.length],
  );

  const undo = useCallback(() => step(-1), [step]);
  const redo = useCallback(() => step(1), [step]);
  const reset = useCallback(() => {
    setCursor(0);
    setPlaying(false);
  }, []);

  const current = entries[cursor];

  const stats = useMemo(() => {
    const accepted = entries.slice(0, cursor + 1).filter((e) => e.accepted).length;
    const rejected = cursor + 1 - accepted;
    const improving = entries
      .slice(0, cursor + 1)
      .filter((e) => e.accepted && (e.score?.hard ?? 1) === 0).length;
    return { accepted, rejected, improving };
  }, [entries, cursor]);

  return (
    <div className="mx-auto max-w-container-max space-y-4 p-margin-desktop">
      <div className="flex items-center justify-between gap-3">
        <div className="flex items-center gap-3">
          <BackButton />
          <h1 className="font-display-lg text-display-lg text-on-surface">
            Tái hiện tìm kiếm
          </h1>
        </div>
      </div>

      <div className="flex flex-wrap items-end gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
        <label className="flex flex-1 flex-col gap-1">
          <span className="font-label-md text-label-md text-on-surface">Run id</span>
          <input
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            placeholder="UUID của search run"
            className="h-10 rounded-lg border border-outline-variant bg-surface-container-lowest px-3 font-body-sm text-body-sm text-on-surface focus:border-primary focus:outline-none focus:ring-2 focus:ring-primary/20"
          />
        </label>
        <Button variant="primary" onClick={() => setRunId(draft.trim() || null)} disabled={!draft.trim()}>
          Tải log
        </Button>
      </div>

      {loading && (
        <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4">
          <p className="font-body-sm text-body-sm text-on-surface-variant">Đang tải…</p>
        </div>
      )}

      {error && (
        <div className="rounded-lg border border-error-container bg-error-container p-4">
          <p className="font-body-sm text-body-sm text-on-error-container">{error}</p>
        </div>
      )}

      {entries.length > 0 && (
        <>
          <div className="flex flex-wrap items-center gap-2 rounded-lg border border-outline-variant bg-surface-container-lowest p-3 shadow-sm">
            <Button variant="primary" size="sm" onClick={() => setPlaying((p) => !p)}>
              {playing ? "Tạm dừng" : "Phát"}
            </Button>
            <Button variant="secondary" size="sm" onClick={undo} disabled={cursor === 0}>
              ⏮ Bước trước
            </Button>
            <Button variant="secondary" size="sm" onClick={redo} disabled={cursor >= entries.length - 1}>
              Bước sau ⏭
            </Button>
            <Button variant="ghost" size="sm" onClick={reset}>
              ⏹ Đặt lại
            </Button>
            <div className="flex items-center gap-1">
              <span className="font-label-sm text-label-sm text-on-surface-variant">Tốc độ:</span>
              {PLAYBACK_SPEEDS.map((s) => (
                <button
                  key={s}
                  type="button"
                  onClick={() => setSpeed(s)}
                  className={
                    speed === s
                      ? "rounded-full bg-primary px-3 py-1 text-[12px] font-semibold text-on-primary"
                      : "rounded-full bg-surface-container-highest px-3 py-1 text-[12px] font-semibold text-on-surface-variant"
                  }
                >
                  {s}x
                </button>
              ))}
            </div>
            <div className="ml-auto flex items-center gap-2">
              <Badge tone="info">
                {cursor + 1}/{entries.length}
              </Badge>
              <Badge tone="success">Chấp nhận: {stats.accepted}</Badge>
              <Badge tone="warning">Từ chối: {stats.rejected}</Badge>
            </div>
          </div>

          <input
            type="range"
            min={0}
            max={Math.max(0, entries.length - 1)}
            value={cursor}
            onChange={(e) => setCursor(Number(e.target.value))}
            className="w-full"
          />

          <div className="grid grid-cols-1 gap-3 md:grid-cols-3">
            <Card label="Hard" value={current?.score?.hard ?? 0} tone="error" />
            <Card label="Coverage" value={`${((current?.score?.coverage ?? 0) * 100).toFixed(1)}%`} tone="secondary" />
            <Card label="Gini" value={(current?.score?.gini ?? 0).toFixed(3)} tone="primary" />
          </div>

          {current && (
            <div className="rounded-lg border border-outline-variant bg-surface-container-lowest p-4 shadow-sm">
              <h3 className="font-headline-md text-headline-md text-on-surface">
                Bước #{cursor + 1} · Iteration {current.iteration}
              </h3>
              <p className="font-body-sm text-body-sm text-on-surface-variant">
                {current.moveType} · slot {current.slotId} · {current.previousStaffId} → {current.newStaffId}
              </p>
              <div className="mt-2 flex items-center gap-2">
                <Badge tone={current.accepted ? "success" : "neutral"}>
                  {current.accepted ? "Chấp nhận" : "Từ chối"}
                </Badge>
                <Badge tone="info">Δ hard: {current.hardDelta}</Badge>
                <Badge tone="info">Δ coverage: {current.coverageDelta.toFixed(3)}</Badge>
              </div>
            </div>
          )}
        </>
      )}
    </div>
  );
}

function Card({
  label,
  value,
  tone,
}: {
  label: string;
  value: number | string;
  tone: "primary" | "secondary" | "tertiary" | "error";
}) {
  const map = {
    primary: "bg-primary-fixed text-on-primary-container",
    secondary: "bg-secondary-container text-on-secondary-container",
    tertiary: "bg-tertiary-container text-on-tertiary-container",
    error: "bg-error-container text-on-error-container",
  } as const;
  return (
    <div className={`rounded-lg px-4 py-3 ${map[tone]}`}>
      <p className="font-label-sm text-label-sm uppercase opacity-80">{label}</p>
      <p className="font-headline-md text-headline-md">{value}</p>
    </div>
  );
}
