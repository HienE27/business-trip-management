import type { RuntimeConfig } from "./types";
import { AUTO_GEN_OVERRIDE_KEYS } from "./types";

/**
 * Merge runtime config + auto-gen config. Chỉ các keys trong
 * AUTO_GEN_OVERRIDE_KEYS mới được auto-gen ghi đè; các keys khác giữ từ
 * runtime config để tránh mất thông số khi backend chưa sync schema.
 */
export function mergeRuntimeAndAutoGen(
  runtime: RuntimeConfig,
  autoGen: RuntimeConfig,
): RuntimeConfig {
  const merged: RuntimeConfig = { ...runtime };
  for (const key of Object.keys(autoGen) as (keyof RuntimeConfig)[]) {
    const k = key as string;
    if (AUTO_GEN_OVERRIDE_KEYS.has(k) && autoGen[key] !== undefined) {
      (merged as Record<string, unknown>)[k] = autoGen[key];
    }
  }
  return merged;
}