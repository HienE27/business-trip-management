"use client";

import { useCallback, useEffect, useRef, useState } from "react";
import { useParams, useRouter } from "next/navigation";
import { api } from "@/lib/api";
import { getErrorMessage } from "@/lib/errors";
import { BackButton } from "@/components/ui/BackButton";
import { Button } from "@/components/ui/Button";
import { Skeleton } from "@/components/ui/Skeleton";
import type {
  SandboxSession,
  SandboxReplayFrame,
  ReplayScoreSummary,
  SandboxSnapshot,
} from "@/types/api";
import { ReplayChart } from "./ReplayChart";
import { FrameDetails } from "./FrameDetails";
import { ReplayTimeline } from "./ReplayTimeline";
import { ReplayPlaybackControls } from "./ReplayPlaybackControls";
import { IterationSlider } from "./IterationSlider";

// Local type for replay response
interface SandboxReplayResponse {
  session: SandboxSession;
  snapshots: SandboxSnapshot[];
  decisions: Array<{ iteration: number; decision: string; score: number }>;
  frames?: SandboxReplayFrame[];
  scoreSummary?: ReplayScoreSummary;
}

/**
 * v11.1.6 Replay Debugger
 *
 * Interactive debugger for sandbox simulation:
 * - Frame-by-frame navigation
 * - Playback with speed control
 * - Move visualization
 * - Export functionality
 */
export default function ReplayPage() {
  const params = useParams();
  const router = useRouter();
  const sessionKey = params.sessionKey as string;

  const [session, setSession] = useState<SandboxSession | null>(null);
  const [replay, setReplay] = useState<SandboxReplayResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  // Playback state
  const [currentFrame, setCurrentFrame] = useState<SandboxReplayFrame | null>(null);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [isPlaying, setIsPlaying] = useState(false);
  const [playbackSpeed, setPlaybackSpeed] = useState(1);

  const playIntervalRef = useRef<ReturnType<typeof setInterval> | null>(null);

  // Load replay data
  const loadReplay = useCallback(async () => {
    if (!sessionKey) return;

    setLoading(true);
    setError(null);

    try {
      const [sessionData, replayData] = await Promise.all([
        api.getSandboxByKey(sessionKey),
        api.getReplay(sessionKey),
      ]);

      setSession(sessionData as SandboxSession | null);
      setReplay(replayData as SandboxReplayResponse | null);

      // Set initial frame from snapshots
      const snapshots = (replayData as SandboxReplayResponse)?.snapshots ?? [];
      if (snapshots.length > 0) {
        const snap = snapshots[0];
        setCurrentFrame({
          iteration: snap.iterations ?? 0,
          score: snap.coverageRate ?? 0,
          timestamp: snap.createdAt,
          accepted: true,
        });
        setCurrentIndex(0);
      }
    } catch (err) {
      setError(getErrorMessage(err, "Có lỗi xảy ra"));
    } finally {
      setLoading(false);
    }
  }, [sessionKey]);

  useEffect(() => {
    loadReplay();
  }, [loadReplay]);

  // Playback control
  useEffect(() => {
    const snapshots = replay?.snapshots;
    if (isPlaying && snapshots && snapshots.length > 0) {
      const interval = 1000 / playbackSpeed;
      playIntervalRef.current = setInterval(() => {
        setCurrentIndex((prev) => {
          if (prev >= snapshots.length - 1) {
            setIsPlaying(false);
            return prev;
          }
          const next = prev + 1;
          const snap = snapshots[next];
          setCurrentFrame({
            iteration: snap.iterations ?? next,
            score: snap.coverageRate ?? 0,
            timestamp: snap.createdAt,
            accepted: true,
          });
          return next;
        });
      }, interval);

      return () => {
        if (playIntervalRef.current) {
          clearInterval(playIntervalRef.current);
        }
      };
    }
  }, [isPlaying, playbackSpeed, replay]);

  // Playback actions
  const handlePlay = () => setIsPlaying(true);
  const handlePause = () => setIsPlaying(false);

  const handlePrevious = () => {
    const snapshots = replay?.snapshots;
    if (!snapshots || currentIndex <= 0) return;
    const prev = currentIndex - 1;
    setCurrentIndex(prev);
    const snap = snapshots[prev];
    setCurrentFrame({
      iteration: snap.iterations ?? prev,
      score: snap.coverageRate ?? 0,
      timestamp: snap.createdAt,
      accepted: true,
    });
  };

  const handleNext = () => {
    const snapshots = replay?.snapshots;
    if (!snapshots || currentIndex >= snapshots.length - 1) return;
    const next = currentIndex + 1;
    setCurrentIndex(next);
    const snap = snapshots[next];
    setCurrentFrame({
      iteration: snap.iterations ?? next,
      score: snap.coverageRate ?? 0,
      timestamp: snap.createdAt,
      accepted: true,
    });
  };

  const handleFirst = () => {
    const snapshots = replay?.snapshots;
    if (!snapshots) return;
    setCurrentIndex(0);
    const snap = snapshots[0];
    setCurrentFrame({
      iteration: snap.iterations ?? 0,
      score: snap.coverageRate ?? 0,
      timestamp: snap.createdAt,
      accepted: true,
    });
  };

  const handleLast = () => {
    const snapshots = replay?.snapshots;
    if (!snapshots) return;
    const last = snapshots.length - 1;
    setCurrentIndex(last);
    const snap = snapshots[last];
    setCurrentFrame({
      iteration: snap.iterations ?? last,
      score: snap.coverageRate ?? 0,
      timestamp: snap.createdAt,
      accepted: true,
    });
  };

  const handleSeek = (index: number) => {
    const snapshots = replay?.snapshots;
    if (!snapshots || index < 0 || index >= snapshots.length) return;
    setCurrentIndex(index);
    const snap = snapshots[index];
    setCurrentFrame({
      iteration: snap.iterations ?? index,
      score: snap.coverageRate ?? 0,
      timestamp: snap.createdAt,
      accepted: true,
    });
  };

  // Export
  const handleExportJson = async () => {
    window.open(`/api/v1/sandbox/${sessionKey}/replay/export/json`, "_blank");
  };

  const handleExportCsv = async () => {
    window.open(`/api/v1/sandbox/${sessionKey}/replay/export/csv`, "_blank");
  };

  if (loading) {
    return (
      <div className="p-margin-desktop space-y-6">
        <Skeleton className="h-12 w-64" />
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          <div className="lg:col-span-2 space-y-4">
            <Skeleton className="h-64 rounded-xl" />
            <Skeleton className="h-48 rounded-xl" />
          </div>
          <Skeleton className="h-96 rounded-xl" />
        </div>
      </div>
    );
  }

  if (error || !session || !replay) {
    return (
      <div className="p-margin-desktop">
        <BackButton href="/digital-twin/compare" />
        <div className="mt-6 p-6 bg-error-container rounded-lg text-center">
          <p className="text-on-error-container">Không tìm thấy replay</p>
          {error && <p className="text-label-sm mt-2 text-on-error-container/70">{error}</p>}
        </div>
      </div>
    );
  }

  const frames = replay.frames ?? [];
  const totalFrames = frames.length;
  const maxIteration = totalFrames > 0 ? frames[totalFrames - 1].iteration : 0;

  return (
    <div className="p-margin-desktop space-y-6">
      {/* Header */}
      <div className="flex items-start justify-between gap-4">
        <div>
          <div className="flex items-center gap-3 mb-2">
            <BackButton href="/digital-twin/compare" />
            <h1 className="font-display-lg text-display-lg text-on-surface">Replay Debugger</h1>
          </div>
          <p className="text-body-sm text-on-surface-variant">
            {session.name} • {totalFrames} frames
          </p>
        </div>

        <div className="flex gap-2">
          <Button variant="ghost" onClick={handleExportJson}>
            <span className="material-symbols-outlined text-[18px]">download</span>
            JSON
          </Button>
          <Button variant="ghost" onClick={handleExportCsv}>
            <span className="material-symbols-outlined text-[18px]">table_chart</span>
            CSV
          </Button>
        </div>
      </div>

      {/* Iteration Slider */}
      <IterationSlider
        currentIndex={currentIndex}
        totalFrames={totalFrames}
        onSeek={handleSeek}
      />

      {/* Main Content */}
      <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
        {/* Left Column: Chart + Timeline */}
        <div className="lg:col-span-2 space-y-6">
          {/* Replay Chart */}
          <ReplayChart
            summary={replay.scoreSummary}
            currentIndex={currentIndex}
            onSeek={handleSeek}
          />

          {/* Timeline */}
          <ReplayTimeline
            frames={frames}
            currentIndex={currentIndex}
            onSelect={handleSeek}
          />
        </div>

        {/* Right Column: Frame Details */}
        <div className="space-y-6">
          {/* Playback Controls */}
          <ReplayPlaybackControls
            isPlaying={isPlaying}
            speed={playbackSpeed}
            currentIndex={currentIndex}
            totalFrames={totalFrames}
            onPlay={handlePlay}
            onPause={handlePause}
            onPrevious={handlePrevious}
            onNext={handleNext}
            onFirst={handleFirst}
            onLast={handleLast}
            onSpeedChange={setPlaybackSpeed}
            canPlay={currentIndex < totalFrames - 1}
            canPrevious={currentIndex > 0}
            canNext={currentIndex < totalFrames - 1}
          />

          {/* Frame Details */}
          <FrameDetails frame={currentFrame} />
        </div>
      </div>
    </div>
  );
}
