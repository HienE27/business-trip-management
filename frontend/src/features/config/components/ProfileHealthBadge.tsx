"use client";

import type { ConfigProfile } from "@/types/api";
import type { ConfigDomain } from "@/features/config/types/ConfigMetadata";

interface ProfileHealthBadgeProps {
  profile: ConfigProfile;
  showLabel?: boolean;
  size?: "sm" | "md";
}

export type HealthStatus = "valid" | "warning" | "invalid";

function readConfig(profile: ConfigProfile): ConfigDomain | null {
  const configRecord = profile.config ?? profile.configJson;
  if (!configRecord || typeof configRecord !== "object") {
    return null;
  }
  // Cast the record — it contains all ConfigDomain fields
  return configRecord as unknown as ConfigDomain;
}

export function ProfileHealthBadge({
  profile,
  showLabel = true,
  size = "md",
}: ProfileHealthBadgeProps) {
  const status = getHealthStatus(profile);

  const config = size === "sm" ? "text-[10px] px-1.5 py-0.5" : "text-[11px] px-2 py-1";
  const iconSize = size === "sm" ? "text-[12px]" : "text-[14px]";

  return (
    <span
      className={`
        inline-flex items-center gap-1 font-semibold rounded-full border shrink-0
        ${config}
        ${status === "valid" ? "bg-emerald-100 text-emerald-800 border-secondary/20" : ""}
        ${status === "warning" ? "bg-amber-100 text-amber-800 border-amber-300" : ""}
        ${status === "invalid" ? "bg-red-100 text-red-800 border-error/20" : ""}
      `}
    >
      <span className={`material-symbols-outlined ${iconSize}`} style={{ fontVariationSettings: "'FILL' 1" }}>
        {status === "valid" ? "check_circle" : status === "warning" ? "warning" : "error"}
      </span>
      {showLabel && (
        <span>
          {status === "valid" ? "Hợp lệ" : status === "warning" ? "Cảnh báo" : "Không hợp lệ"}
        </span>
      )}
    </span>
  );
}

export function getHealthStatus(profile: ConfigProfile): HealthStatus {
  const config = readConfig(profile);
  if (!config) {
    return "invalid";
  }

  const maxIterations = Number(config.maxIterations) || 0;
  const cvTarget = Number(config.cvTarget) || 0;
  const cvWorst = Number(config.cvWorst) || 1;
  const tabuTenureMin = Number(config.tabuTenureMin) || 0;
  const tabuTenureMax = Number(config.tabuTenureMax) || 0;
  const timeLimitSeconds = Number(config.timeLimitSeconds) || 0;
  const weekendWeight = Number(config.weekendWeight) || 0;
  const neighborhoodSize = Number(config.neighborhoodSize) || 0;
  const candidateListSize = Number(config.candidateListSize) || 0;

  if (maxIterations <= 0) return "invalid";
  if (cvTarget > cvWorst) return "invalid";
  if (tabuTenureMin > tabuTenureMax) return "invalid";

  if (maxIterations < 100) return "warning";
  if (timeLimitSeconds < 30) return "warning";
  if (cvTarget < 0.05) return "warning";
  if (weekendWeight > 5) return "warning";
  if (neighborhoodSize > candidateListSize) return "warning";

  return "valid";
}

export function getHealthDescription(profile: ConfigProfile): string | null {
  const status = getHealthStatus(profile);

  if (status === "invalid") {
    const config = readConfig(profile);
    if (!config) return "Cấu hình bị trống";

    const maxIterations = Number(config.maxIterations) || 0;
    const cvTarget = Number(config.cvTarget) || 0;
    const cvWorst = Number(config.cvWorst) || 1;
    const tabuTenureMin = Number(config.tabuTenureMin) || 0;
    const tabuTenureMax = Number(config.tabuTenureMax) || 0;

    if (maxIterations <= 0) return "Số lần lặp phải lớn hơn 0";
    if (cvTarget > cvWorst) return "CV mục tiêu phải nhỏ hơn hoặc bằng CV tồi tệ nhất";
    if (tabuTenureMin > tabuTenureMax) return "Tabu tối thiểu phải nhỏ hơn hoặc bằng tabu tối đa";
    return "Cấu hình không hợp lệ";
  }

  if (status === "warning") {
    const config = readConfig(profile);
    if (!config) return null;

    const maxIterations = Number(config.maxIterations) || 0;
    const timeLimitSeconds = Number(config.timeLimitSeconds) || 0;
    const cvTarget = Number(config.cvTarget) || 0;
    const weekendWeight = Number(config.weekendWeight) || 0;
    const neighborhoodSize = Number(config.neighborhoodSize) || 0;
    const candidateListSize = Number(config.candidateListSize) || 0;

    if (maxIterations < 100) return "Số lần lặp thấp, có thể ảnh hưởng chất lượng";
    if (timeLimitSeconds < 30) return "Giới hạn thời gian ngắn";
    if (cvTarget < 0.05) return "CV mục tiêu rất thấp, có thể không đạt được";
    if (weekendWeight > 5) return "Trọng số cuối tuần cao";
    if (neighborhoodSize > candidateListSize) return "Kích thước vùng lân cận lớn hơn danh sách ứng viên";
  }

  return null;
}
