"use client";

import { useMemo, useRef, useEffect } from "react";
import type { SandboxReplayFrame } from "@/types/api";

interface ReplayTimelineProps {
  frames: SandboxReplayFrame[];
  currentIndex: number;
  onSelect: (index: number) => void;
}

/**
 * Timeline showing all frames with current position.
 * Click to seek to specific frame.
 */
export function ReplayTimeline({ frames, currentIndex, onSelect }: ReplayTimelineProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  // Auto-scroll to current frame
  useEffect(() => {
    if (containerRef.current) {
      const currentEl = containerRef.current.querySelector(`[data-index="${currentIndex}"]`);
      if (currentEl) {
        currentEl.scrollIntoView({ behavior: "smooth", block: "center" });
      }
    }
  }, [currentIndex]);

  // Group frames by iteration range
  const groupedFrames = useMemo(() => {
    const groups: { label: string; startIdx: number; endIdx: number; frames: SandboxReplayFrame[] }[] = [];
    const groupSize = 10;

    for (let i = 0; i < frames.length; i += groupSize) {
      const end = Math.min(i + groupSize, frames.length);
      groups.push({
        label: `Iteration ${frames[i].iteration} - ${frames[end - 1].iteration}`,
        startIdx: i,
        endIdx: end - 1,
        frames: frames.slice(i, end),
      });
    }

    return groups;
  }, [frames]);

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
      <h3 className="font-title-lg text-title-lg text-on-surface mb-4">Timeline</h3>

      <div ref={containerRef} className="h-48 overflow-y-auto space-y-1 pr-2">
        {groupedFrames.map((group) => (
          <div key={group.startIdx} className="space-y-1">
            {/* Group header */}
            <div className="text-label-xs text-on-surface-variant px-2 py-1 bg-surface-container-low rounded">
              {group.label}
            </div>

            {/* Frames in group */}
            <div className="flex flex-wrap gap-1 px-2">
              {group.frames.map((frame, idx) => {
                const actualIdx = group.startIdx + idx;
                const isActive = actualIdx === currentIndex;
                const isPassed = actualIdx < currentIndex;
                const isAccepted = frame.accepted;

                return (
                  <button
                    key={actualIdx}
                    data-index={actualIdx}
                    onClick={() => onSelect(actualIdx)}
                    className={`
                      w-8 h-8 rounded text-label-xs font-mono transition-all
                      ${isActive
                        ? "bg-primary text-on-primary ring-2 ring-primary ring-offset-2"
                        : isPassed
                        ? isAccepted
                          ? "bg-secondary-container text-on-secondary-container hover:bg-secondary"
                          : "bg-error-container text-on-error-container hover:bg-error"
                        : "bg-surface-container-low text-on-surface-variant hover:bg-surface-container-high"
                      }
                    `}
                  >
                    {frame.iteration % 10 === 0 ? frame.iteration : "."}
                  </button>
                );
              })}
            </div>
          </div>
        ))}

        {frames.length === 0 && (
          <div className="flex items-center justify-center h-full">
            <p className="text-on-surface-variant text-label-md">Không có frames</p>
          </div>
        )}
      </div>
    </div>
  );
}
