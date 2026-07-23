"use client";

import type { SandboxStatus } from "@/types/api";

interface PlaybackControlsProps {
  status: SandboxStatus;
  onPause: () => void;
  onResume: () => void;
  onCancel: () => void;
  onViewResults: () => void;
}

/**
 * Playback controls for live timeline and replay.
 * Supports: Start, Pause, Resume, Stop, View Results
 */
export function PlaybackControls({
  status,
  onPause,
  onResume,
  onCancel,
  onViewResults,
}: PlaybackControlsProps) {
  const isRunning = status === "RUNNING";
  const isPaused = status === "PAUSED";
  const isCompleted = status === "COMPLETED";
  const canControl = isRunning || isPaused;

  return (
    <div className="flex items-center gap-2">
      {/* Playback buttons */}
      {canControl && (
        <>
          {isRunning && (
            <button
              onClick={onPause}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-surface-container-low border border-outline-variant hover:bg-surface-container-high transition-colors"
            >
              <span className="material-symbols-outlined text-[20px]">pause</span>
              <span className="text-label-md text-on-surface">Tạm dừng</span>
            </button>
          )}

          {isPaused && (
            <button
              onClick={onResume}
              className="flex items-center gap-2 px-4 py-2 rounded-lg bg-primary text-on-primary hover:bg-primary/90 transition-colors"
            >
              <span className="material-symbols-outlined text-[20px]">play_arrow</span>
              <span className="text-label-md">Tiếp tục</span>
            </button>
          )}

          <button
            onClick={onCancel}
            className="flex items-center gap-2 px-4 py-2 rounded-lg bg-error-container text-on-error-container hover:bg-error-container/80 transition-colors"
          >
            <span className="material-symbols-outlined text-[20px]">stop</span>
            <span className="text-label-md">Dừng</span>
          </button>
        </>
      )}

      {/* Completed state */}
      {isCompleted && (
        <button
          onClick={onViewResults}
          className="flex items-center gap-2 px-4 py-2 rounded-lg bg-secondary text-on-secondary hover:bg-secondary/90 transition-colors"
        >
          <span className="material-symbols-outlined text-[20px]">analytics</span>
          <span className="text-label-md">Xem kết quả</span>
        </button>
      )}

      {/* Status indicator */}
      <div className="ml-4 flex items-center gap-2 text-label-sm text-on-surface-variant">
        {isRunning && (
          <>
            <div className="w-2 h-2 rounded-full bg-secondary animate-pulse" />
            <span>Đang chạy</span>
          </>
        )}
        {isPaused && (
          <>
            <div className="w-2 h-2 rounded-full bg-tertiary" />
            <span>Đã tạm dừng</span>
          </>
        )}
        {isCompleted && (
          <>
            <div className="w-2 h-2 rounded-full bg-secondary" />
            <span>Hoàn thành</span>
          </>
        )}
      </div>
    </div>
  );
}
