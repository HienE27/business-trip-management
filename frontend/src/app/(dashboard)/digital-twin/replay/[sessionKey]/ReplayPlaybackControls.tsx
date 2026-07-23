"use client";

import type { SandboxStatus } from "@/types/api";

interface ReplayPlaybackControlsProps {
  isPlaying: boolean;
  speed: number;
  currentIndex: number;
  totalFrames: number;
  onPlay: () => void;
  onPause: () => void;
  onPrevious: () => void;
  onNext: () => void;
  onFirst: () => void;
  onLast: () => void;
  onSpeedChange: (speed: number) => void;
  canPlay: boolean;
  canPrevious: boolean;
  canNext: boolean;
}

const SPEEDS = [0.25, 0.5, 1, 2, 4, 8] as const;

/**
 * Full playback controls for replay.
 * Includes: First, Previous, Play/Pause, Next, Last, Speed
 */
export function ReplayPlaybackControls({
  isPlaying,
  speed,
  currentIndex,
  totalFrames,
  onPlay,
  onPause,
  onPrevious,
  onNext,
  onFirst,
  onLast,
  onSpeedChange,
  canPlay,
  canPrevious,
  canNext,
}: ReplayPlaybackControlsProps) {
  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 space-y-4">
      <h3 className="font-title-lg text-title-lg text-on-surface">Playback</h3>

      {/* Progress */}
      <div className="text-center">
        <span className="font-headline-lg text-headline-lg text-on-surface">
          {currentIndex + 1}
        </span>
        <span className="text-on-surface-variant"> / </span>
        <span className="text-on-surface-variant">{totalFrames}</span>
      </div>

      {/* Transport controls */}
      <div className="flex items-center justify-center gap-2">
        {/* First */}
        <button
          onClick={onFirst}
          disabled={!canPrevious}
          className="w-10 h-10 rounded-lg flex items-center justify-center transition-colors disabled:opacity-30 hover:bg-surface-container-high"
          title="First frame"
        >
          <span className="material-symbols-outlined text-[20px]">first_page</span>
        </button>

        {/* Previous */}
        <button
          onClick={onPrevious}
          disabled={!canPrevious}
          className="w-10 h-10 rounded-lg flex items-center justify-center transition-colors disabled:opacity-30 hover:bg-surface-container-high"
          title="Previous frame"
        >
          <span className="material-symbols-outlined text-[20px]">chevron_left</span>
        </button>

        {/* Play/Pause */}
        <button
          onClick={isPlaying ? onPause : onPlay}
          disabled={!canPlay && !isPlaying}
          className={`
            w-14 h-14 rounded-full flex items-center justify-center transition-all
            ${isPlaying
              ? "bg-tertiary text-on-tertiary hover:bg-tertiary/80"
              : "bg-primary text-on-primary hover:bg-primary/80"
            }
            disabled:opacity-30
          `}
          title={isPlaying ? "Pause" : "Play"}
        >
          <span className="material-symbols-outlined text-[28px]">
            {isPlaying ? "pause" : "play_arrow"}
          </span>
        </button>

        {/* Next */}
        <button
          onClick={onNext}
          disabled={!canNext}
          className="w-10 h-10 rounded-lg flex items-center justify-center transition-colors disabled:opacity-30 hover:bg-surface-container-high"
          title="Next frame"
        >
          <span className="material-symbols-outlined text-[20px]">chevron_right</span>
        </button>

        {/* Last */}
        <button
          onClick={onLast}
          disabled={!canNext}
          className="w-10 h-10 rounded-lg flex items-center justify-center transition-colors disabled:opacity-30 hover:bg-surface-container-high"
          title="Last frame"
        >
          <span className="material-symbols-outlined text-[20px]">last_page</span>
        </button>
      </div>

      {/* Speed control */}
      <div className="space-y-2">
        <div className="text-label-sm text-on-surface-variant text-center">Speed</div>
        <div className="flex items-center justify-center gap-1">
          {SPEEDS.map((s) => (
            <button
              key={s}
              onClick={() => onSpeedChange(s)}
              className={`
                px-3 py-1.5 rounded-lg text-label-sm font-medium transition-colors
                ${speed === s
                  ? "bg-primary text-on-primary"
                  : "bg-surface-container-low text-on-surface-variant hover:bg-surface-container-high"
                }
              `}
            >
              {s}x
            </button>
          ))}
        </div>
      </div>

      {/* Keyboard shortcuts hint */}
      <div className="text-center text-label-xs text-on-surface-variant pt-2 border-t border-outline-variant">
        Phím tắt: Space (Play/Pause), ← → (Prev/Next)
      </div>
    </div>
  );
}
