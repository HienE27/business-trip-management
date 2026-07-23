"use client";

import type { SandboxReplayFrame } from "@/types/api";

interface FrameDetailsProps {
  frame: SandboxReplayFrame | null;
}

/**
 * Frame details panel showing move information.
 */
export function FrameDetails({ frame }: FrameDetailsProps) {
  if (!frame) {
    return (
      <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5">
        <h3 className="font-title-lg text-title-lg text-on-surface mb-4">Frame Details</h3>
        <p className="text-on-surface-variant text-label-md">Chưa chọn frame</p>
      </div>
    );
  }

  const moveIcon = frame.accepted ? "check_circle" : "cancel";
  const moveColor = frame.accepted ? "text-secondary bg-secondary-container" : "text-error bg-error-container";

  return (
    <div className="bg-surface-container-lowest border border-outline-variant rounded-xl p-5 space-y-4">
      <h3 className="font-title-lg text-title-lg text-on-surface">Frame Details</h3>

      {/* Iteration & Status */}
      <div className="flex items-center justify-between">
        <div className="flex items-center gap-2">
          <span className="material-symbols-outlined text-primary bg-primary-fixed p-1.5 rounded-md text-[18px]">
            tag
          </span>
          <span className="text-label-md text-on-surface-variant">Iteration</span>
        </div>
        <span className="font-headline-lg text-headline-lg text-on-surface">
          {frame.iteration}
        </span>
      </div>

      {/* Move Type */}
      {frame.moveType && (
        <div className="flex items-center gap-2">
          <span className={`material-symbols-outlined ${moveColor} p-1.5 rounded-md text-[18px]`}
                style={{ fontVariationSettings: "'FILL' 1" }}>
            {moveIcon}
          </span>
          <div>
            <span className="text-label-md text-on-surface">{frame.moveType}</span>
            <span className={`ml-2 text-label-sm ${frame.accepted ? "text-secondary" : "text-error"}`}>
              {frame.accepted ? "Accepted" : "Rejected"}
            </span>
          </div>
        </div>
      )}

      {/* Staff */}
      {frame.staff && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Staff</div>
          <div className="flex items-center gap-2 bg-surface-container-low rounded-lg p-3">
            <span className="material-symbols-outlined text-primary bg-primary-fixed p-1 rounded-md text-[16px]">
              person
            </span>
            <div>
              <div className="text-body-md text-on-surface font-medium">{frame.staff.name}</div>
              <div className="text-label-xs text-on-surface-variant">{frame.staff.staffCode}</div>
            </div>
          </div>
        </div>
      )}

      {/* Swap/Change - Target Staff */}
      {frame.targetStaff && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Target Staff</div>
          <div className="flex items-center gap-2 bg-surface-container-low rounded-lg p-3">
            <span className="material-symbols-outlined text-secondary bg-secondary-container p-1 rounded-md text-[16px]">
              person
            </span>
            <div>
              <div className="text-body-md text-on-surface font-medium">
                {typeof frame.targetStaff === 'string' ? frame.targetStaff : (frame.targetStaff as { name?: string; staffCode?: string })?.name ?? ""}
              </div>
              {typeof frame.targetStaff === 'object' && (frame.targetStaff as { staffCode?: string })?.staffCode && (
                <div className="text-label-xs text-on-surface-variant">{(frame.targetStaff as { staffCode?: string }).staffCode}</div>
              )}
            </div>
          </div>
        </div>
      )}

      {/* Reason */}
      {frame.reason && (
        <div className="space-y-2">
          <div className="text-label-sm text-on-surface-variant">Reason</div>
          <div className="bg-surface-container-low rounded-lg p-3">
            <span className="text-body-md text-on-surface">{frame.reason}</span>
          </div>
        </div>
      )}

      {/* Score Delta */}
      {frame.scoreDelta !== 0 && (
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-secondary bg-secondary-container p-1.5 rounded-md text-[18px]">
              trending_up
            </span>
            <span className="text-label-md text-on-surface-variant">Score Delta</span>
          </div>
          <span className={`font-headline-lg text-headline-lg ${(frame.scoreDelta ?? 0) > 0 ? "text-secondary" : "text-error"}`}>
            {(frame.scoreDelta ?? 0) > 0 ? "+" : ""}{(frame.scoreDelta ?? 0).toFixed(1)}
          </span>
        </div>
      )}

      {/* Metrics */}
      <div className="pt-4 border-t border-outline-variant space-y-3">
        <div className="flex items-center justify-between">
          <span className="text-label-sm text-on-surface-variant">Coverage</span>
          <span className="text-body-md text-on-surface font-medium">{(frame.coverage ?? 0).toFixed(1)}%</span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-label-sm text-on-surface-variant">Fairness (CV)</span>
          <span className="text-body-md text-on-surface font-medium">{(frame.fairnessCv ?? 0).toFixed(3)}</span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-label-sm text-on-surface-variant">Hard Violations</span>
          <span className={`text-body-md font-medium ${(frame.hardViolations ?? 0) === 0 ? "text-secondary" : "text-error"}`}>
            {frame.hardViolations ?? 0}
          </span>
        </div>
        <div className="flex items-center justify-between">
          <span className="text-label-sm text-on-surface-variant">Soft Violations</span>
          <span className="text-body-md text-on-surface">{frame.softViolations ?? 0}</span>
        </div>
      </div>

      {/* Timestamp */}
      <div className="pt-4 border-t border-outline-variant">
        <div className="flex items-center justify-between">
          <span className="text-label-sm text-on-surface-variant">Timestamp</span>
          <span className="text-label-sm text-on-surface">
            {frame.timestamp ? new Date(frame.timestamp).toLocaleTimeString("vi-VN") : "—"}
          </span>
        </div>
        {(frame.durationMs ?? 0) > 0 && (
          <div className="flex items-center justify-between mt-1">
            <span className="text-label-sm text-on-surface-variant">Duration</span>
            <span className="text-label-sm text-on-surface">{frame.durationMs} ms</span>
          </div>
        )}
      </div>
    </div>
  );
}
